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

    fun startScan() {
        scanner = btAdapter?.bluetoothLeScanner ?: return
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.device.address
                nearbyDevices.value = nearbyDevices.value + name
                log("Found: $name")
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback!!)
        log("Scanning started")
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        log("Scan stopped")
    }

    fun receivePacket(packetId: String, payloadJson: String, senderDevice: String, ttl: Int) {
        if (ttl <= 0) { log("Dropped expired packet $packetId"); return }
        scope.launch {
            if (dao.exists(packetId) > 0) { log("Dup dropped: $packetId"); return@launch }
            dao.insert(RelayPacket(
                packetId = packetId,
                payloadJson = payloadJson,
                receivedAt = Instant.now().toString(),
                senderDevice = senderDevice
            ))
            log("Stored relayed packet $packetId")
        }
    }

    private fun log(msg: String) {
        eventLog.value = (listOf(msg) + eventLog.value).take(50)
    }
}
