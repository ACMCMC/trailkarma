package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import fyi.acmc.trailkarma.db.RelayInboxMessageDao
import fyi.acmc.trailkarma.db.RelayJobIntentDao
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.db.TrailReportDao
import fyi.acmc.trailkarma.models.RelayPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

// UUIDs defined in GattServer.kt — GATT_SERVICE_UUID is the scan filter anchor

// Maximum BLE advertisement service-data payload: 20 bytes.
// We pack: version(1) | hikerId(up to 19 chars, UTF-8, null-trimmed)
private const val PAYLOAD_VERSION: Byte = 0x01
private const val MAX_HOP_BROADCAST = 5   // don't re-advertise relay packets beyond this hop

@SuppressLint("MissingPermission")
class BleRepository(
    private val context: Context,
    private val relayPacketDao: RelayPacketDao,
    private val trailReportDao: TrailReportDao,
    private val relayJobIntentDao: RelayJobIntentDao,
    private val relayInboxMessageDao: RelayInboxMessageDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    val nearbyDevices = MutableStateFlow<Set<String>>(emptySet())
    val eventLog = MutableStateFlow<List<String>>(emptyList())

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    // Track devices we've already initiated a GATT sync with this session
    // so we don't spam-connect to the same phone every scan cycle.
    private val syncedDevices = mutableSetOf<String>()

    private val gattClient by lazy {
        GattClient(
            context = context,
            reportDao = trailReportDao,
            relayPacketDao = relayPacketDao,
            relayJobIntentDao = relayJobIntentDao,
            relayInboxMessageDao = relayInboxMessageDao,
            onLog = ::log
        )
    }

    // ── Advertising ───────────────────────────────────────────────────────────

    /**
     * Broadcast a structured beacon:
     *   byte[0]   = PAYLOAD_VERSION (0x01)
     *   byte[1..] = hikerId UTF-8 (up to 19 bytes)
     *
     * Peers parse this to know "a TrailKarma hiker is nearby" and initiate a GATT sync.
     */
    fun startAdvertising(hikerId: String) {
        advertiser = btAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            log("BLE advertising not supported on this device")
            return
        }

        val hikerIdBytes = hikerId.toByteArray(Charsets.UTF_8).take(19)
        val payload = ByteArray(1 + hikerIdBytes.size)
        payload[0] = PAYLOAD_VERSION
        hikerIdBytes.forEachIndexed { i, b -> payload[1 + i] = b }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)   // must be true so peers can open a GATT connection
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(GATT_SERVICE_UUID))
            .addServiceData(ParcelUuid(GATT_SERVICE_UUID), payload)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                log("Beacon active — broadcasting as hiker: $hikerId")
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

    // ── Scanning ──────────────────────────────────────────────────────────────

    /**
     * Scan for other TrailKarma beacons.
     * On discovery:
     *   1. Parse the structured payload to extract peer's hikerId
     *   2. Log the encounter as a RelayPacket
     *   3. If not yet synced this session → initiate GATT client sync
     */
    fun startScan() {
        scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            log("BLE scanning not supported")
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device   = result.device
                val address  = device.address
                val rssi     = result.rssi
                val rawData  = result.scanRecord?.serviceData?.get(ParcelUuid(GATT_SERVICE_UUID))

                nearbyDevices.value = nearbyDevices.value + address

                if (rawData != null && rawData.isNotEmpty()) {
                    val version  = rawData[0]
                    val hikerId  = if (rawData.size > 1) String(rawData.copyOfRange(1, rawData.size), Charsets.UTF_8) else "unknown"

                    log("📡 Hiker found: $hikerId @ $address (RSSI: $rssi dBm)")

                    // Log encounter
                    scope.launch {
                        val packetId = UUID.nameUUIDFromBytes(
                            (address + Instant.now().epochSecond / 60).toByteArray()
                        ).toString()
                        val encounterJson = """{"type":"encounter","remote":"$address","hiker_id":"$hikerId","rssi":$rssi}"""
                        relayPacketDao.insert(
                            RelayPacket(
                                packetId     = packetId,
                                payloadJson  = encounterJson,
                                receivedAt   = Instant.now().toString(),
                                senderDevice = address,
                                hopCount     = 0
                            )
                        )
                    }

                    // Initiate GATT sync if we haven't spoken to this device yet this session
                    if (address !in syncedDevices) {
                        syncedDevices.add(address)
                        log("🔗 Initiating report sync with $hikerId ($address)…")
                        gattClient.syncWithPeer(device)
                    }
                } else {
                    log("Found generic TrailKarma device: $address")
                }
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GATT_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback!!)
        log("Scanning for nearby hikers…")
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        syncedDevices.clear()
        log("Scan stopped")
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private fun log(msg: String) {
        eventLog.value = (listOf(msg) + eventLog.value).take(100)
    }
}
