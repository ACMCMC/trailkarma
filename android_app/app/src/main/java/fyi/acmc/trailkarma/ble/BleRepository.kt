package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
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

private const val TAG = "TrailKarma/BLE"

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
        Log.d(TAG, "startAdvertising: hikerId=$hikerId btAdapter=${btAdapter != null}")
        advertiser = btAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            val msg = "BLE advertising not supported on this device (adapter=${btAdapter != null}, isEnabled=${btAdapter?.isEnabled})"
            log(msg); Log.w(TAG, msg)
            return
        }

        val hikerIdBytes = hikerId.toByteArray(Charsets.UTF_8).take(19)
        val payload = ByteArray(1 + hikerIdBytes.size)
        payload[0] = PAYLOAD_VERSION
        hikerIdBytes.forEachIndexed { i, b -> payload[1 + i] = b }
        Log.d(TAG, "advertising payload: version=${payload[0]}, hikerId bytes=${hikerIdBytes.size}")

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
                val msg = "Beacon active — broadcasting as hiker: $hikerId"
                log(msg); Log.i(TAG, msg)
            }
            override fun onStartFailure(errorCode: Int) {
                val reason = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE  -> "data too large"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR  -> "internal error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
                    else -> "unknown ($errorCode)"
                }
                val msg = "Advertising failed: $reason"
                log(msg); Log.e(TAG, msg)
            }
        }
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        log("Advertising stopped"); Log.d(TAG, "Advertising stopped")
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
        Log.d(TAG, "startScan: btAdapter=${btAdapter != null}, isEnabled=${btAdapter?.isEnabled}")
        scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            val msg = "BLE scanning not supported (adapter=${btAdapter != null}, isEnabled=${btAdapter?.isEnabled})"
            log(msg); Log.w(TAG, msg)
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device   = result.device
                val address  = device.address
                val rssi     = result.rssi
                val rawData  = result.scanRecord?.serviceData?.get(ParcelUuid(GATT_SERVICE_UUID))

                Log.d(TAG, "onScanResult: address=$address rssi=$rssi hasServiceData=${rawData != null} serviceDataLen=${rawData?.size ?: 0}")
                nearbyDevices.value = nearbyDevices.value + address

                if (rawData != null && rawData.isNotEmpty()) {
                    val version = rawData[0]
                    val hikerId = if (rawData.size > 1) String(rawData.copyOfRange(1, rawData.size), Charsets.UTF_8) else "unknown"

                    val msg = "📡 Hiker found: $hikerId @ $address (RSSI: $rssi dBm)"
                    log(msg); Log.i(TAG, msg)

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

                    if (address !in syncedDevices) {
                        syncedDevices.add(address)
                        val msg2 = "🔗 Initiating report sync with $hikerId ($address)…"
                        log(msg2); Log.i(TAG, msg2)
                        gattClient.syncWithPeer(device)
                    } else {
                        Log.d(TAG, "Already synced with $address this session, skipping GATT connect")
                    }
                } else {
                    val msg = "Found TrailKarma device $address with no service data (rawData=${rawData?.let { it.size.toString() + " bytes" } ?: "null"})"
                    log(msg); Log.w(TAG, msg)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val reason = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED          -> "already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app registration failed"
                    SCAN_FAILED_FEATURE_UNSUPPORTED      -> "feature unsupported"
                    SCAN_FAILED_INTERNAL_ERROR           -> "internal error"
                    else -> "unknown ($errorCode)"
                }
                val msg = "BLE scan failed: $reason"
                log(msg); Log.e(TAG, msg)
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GATT_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(listOf(filter), settings, scanCallback!!)
        val msg = "Scanning for nearby hikers… (filter UUID: $GATT_SERVICE_UUID)"
        log(msg); Log.i(TAG, msg)
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        syncedDevices.clear()
        log("Scan stopped"); Log.d(TAG, "Scan stopped")
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private fun log(msg: String) {
        Log.d(TAG, msg)
        eventLog.value = (listOf(msg) + eventLog.value).take(100)
    }
}
