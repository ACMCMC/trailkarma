package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.models.RelayPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("0000FE2D-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
class BleRepository(private val context: Context, private val dao: RelayPacketDao) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    val nearbyDevices = MutableStateFlow<Set<String>>(emptySet())
    val eventLog = MutableStateFlow<List<String>>(emptyList())

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    fun startAdvertising(payloadString: String) {
        advertiser = btAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            log("BLE Advertising not supported on this device")
            return
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
            
        // Service data is limited to ~20 bytes, so we take a substring if needed
        val payloadBytes = payloadString.take(20).toByteArray(Charsets.UTF_8)
            
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), payloadBytes)
            .build()
            
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                log("Broadcasting beacon: $payloadString")
            }
            override fun onStartFailure(errorCode: Int) {
                log("Advertising failed: error $errorCode")
            }
        }
        
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        log("Advertising stopped")
    }

    fun startScan() {
        scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            log("BLE Scanning not supported")
            return
        }
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                val rssi = result.rssi
                val serviceData = result.scanRecord?.serviceData?.get(ParcelUuid(SERVICE_UUID))
                
                nearbyDevices.value = nearbyDevices.value + address
                
                if (serviceData != null) {
                    val payloadStr = String(serviceData, Charsets.UTF_8)
                    log("Contact Trace! Device: $address | RSSI: $rssi | Payload: $payloadStr")
                    
                    // Create a unique hash for this encounter so we don't spam the DB
                    val packetId = UUID.nameUUIDFromBytes((address + payloadStr + Instant.now().epochSecond / 60).toByteArray()).toString()
                    val encounterJson = "{\"type\":\"encounter\", \"remote_device\":\"$address\", \"rssi\":$rssi, \"payload\":\"$payloadStr\"}"
                    
                    receivePacket(packetId, encounterJson, address, 1)
                } else {
                    log("Found generic TrailKarma device: $address")
                }
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback!!)
        log("Scanning for nearby hikers...")
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        log("Scan stopped")
    }

    fun receivePacket(packetId: String, payloadJson: String, senderDevice: String, ttl: Int) {
        if (ttl <= 0) { log("Dropped expired packet $packetId"); return }
        scope.launch {
            if (dao.exists(packetId) > 0) { return@launch }
            dao.insert(RelayPacket(
                packetId = packetId,
                payloadJson = payloadJson,
                receivedAt = Instant.now().toString(),
                senderDevice = senderDevice
            ))
            log("Logged encounter to secure offline vault.")
        }
    }

    private fun log(msg: String) {
        eventLog.value = (listOf(msg) + eventLog.value).take(50)
    }
}
