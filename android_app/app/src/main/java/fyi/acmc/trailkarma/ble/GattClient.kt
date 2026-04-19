package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.db.TrailReportDao
import fyi.acmc.trailkarma.models.RelayPacket
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

private const val TAG = "TrailKarma/GattClient"
private const val CONNECT_TIMEOUT_MS = 15_000L
private const val MAX_HOP_COUNT = 5 // stop re-advertising packets after this many hops

/**
 * Initiated when BleRepository discovers a new peer beacon.
 *
 * Protocol:
 *   1. Connect to peer's GATT server
 *   2. Discover services
 *   3. READ MANIFEST_CHAR → get peer's list of report IDs
 *   4. Diff against local DB → find IDs we don't have
 *   5. For each missing ID: WRITE it to REPORT_CHAR → receive report JSON via NOTIFY
 *   6. INSERT received reports into local Room DB (INSERT OR IGNORE — additive model)
 *   7. Log the exchange as a RelayPacket
 *   8. Disconnect
 */
@SuppressLint("MissingPermission")
class GattClient(
    private val context: Context,
    private val reportDao: TrailReportDao,
    private val relayPacketDao: RelayPacketDao,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reassembly buffer: accumulates chunked notification frames for one report at a time
    private val reassemblyBuffer = mutableMapOf<String, Array<ByteArray?>>()

    // Deferred that resolves when manifest read completes
    private var manifestDeferred: CompletableDeferred<String?>? = null

    // Queue of reportIds to request; each has its own deferred
    private val pendingReports = ArrayDeque<Pair<String, CompletableDeferred<String?>>>()
    private var activeReportDeferred: CompletableDeferred<String?>? = null

    // Active reassembly state for the current notification stream
    private var reassemblyChunks: Array<ByteArray?>? = null
    private var reassemblyTotal: Int = 0
    private var reassemblyReceived: Int = 0

    fun syncWithPeer(device: BluetoothDevice) {
        scope.launch {
            try {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    connectAndSync(device)
                }
            } catch (e: TimeoutCancellationException) {
                onLog("BLE sync timed out with ${device.address}")
            } catch (e: Exception) {
                onLog("BLE sync error with ${device.address}: ${e.message}")
            }
        }
    }

    private suspend fun connectAndSync(device: BluetoothDevice) = suspendCancellableCoroutine<Unit> { cont ->
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onLog("Connected to ${device.address}, discovering services…")
                    g.requestMtu(517) // request maximum MTU
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onLog("Disconnected from ${device.address}")
                    if (cont.isActive) cont.resume(Unit) {}
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onLog("Service discovery failed: $status")
                    g.disconnect()
                    return
                }
                val service = g.getService(GATT_SERVICE_UUID)
                if (service == null) {
                    onLog("${device.address} has no TrailKarma GATT service — skipping")
                    g.disconnect()
                    return
                }

                // Enable notifications on REPORT_CHAR before we start requesting reports
                val reportChar = service.getCharacteristic(REPORT_CHAR_UUID)
                val cccd = reportChar?.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    g.setCharacteristicNotification(reportChar, true)
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                } else {
                    readManifest(g)
                }
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                // Notifications enabled — now read the manifest
                readManifest(g)
            }

            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (characteristic.uuid != MANIFEST_CHAR_UUID) return
                val json = if (status == BluetoothGatt.GATT_SUCCESS)
                    String(characteristic.value ?: ByteArray(0), Charsets.UTF_8)
                else null
                manifestDeferred?.complete(json)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid != REPORT_CHAR_UUID) return
                val frame = characteristic.value ?: return
                if (frame.size < 4) return

                val seq   = (frame[0].toInt() and 0xFF shl 8) or (frame[1].toInt() and 0xFF)
                val total = (frame[2].toInt() and 0xFF shl 8) or (frame[3].toInt() and 0xFF)
                val data  = frame.copyOfRange(4, frame.size)

                // Initialise reassembly array on first chunk
                if (seq == 0 || reassemblyChunks == null || reassemblyTotal != total) {
                    reassemblyChunks = arrayOfNulls(total)
                    reassemblyTotal = total
                    reassemblyReceived = 0
                }
                reassemblyChunks!![seq] = data
                reassemblyReceived++

                if (reassemblyReceived == total) {
                    // All chunks received — reassemble and resolve
                    val fullBytes = reassemblyChunks!!.flatMap { it!!.toList() }.toByteArray()
                    val fullJson = String(fullBytes, Charsets.UTF_8)
                    reassemblyChunks = null
                    activeReportDeferred?.complete(fullJson)
                }
            }

            private fun readManifest(g: BluetoothGatt) {
                val manifestChar = g.getService(GATT_SERVICE_UUID)?.getCharacteristic(MANIFEST_CHAR_UUID) ?: return
                manifestDeferred = CompletableDeferred()
                g.readCharacteristic(manifestChar)

                scope.launch {
                    val manifestJson = manifestDeferred?.await() ?: run {
                        g.disconnect(); return@launch
                    }

                    // Parse manifest and diff against our local DB
                    val peerIds = try {
                        val arr = JSONArray(manifestJson)
                        (0 until arr.length()).map { arr.getString(it) }.toSet()
                    } catch (e: Exception) {
                        onLog("Bad manifest JSON from ${device.address}"); g.disconnect(); return@launch
                    }

                    val localIds = reportDao.getIds().toSet()
                    val missing = peerIds - localIds

                    onLog("Peer ${device.address}: ${peerIds.size} reports, ${missing.size} new to us")

                    if (missing.isEmpty()) { g.disconnect(); return@launch }

                    // Pull each missing report one at a time (WRITE → NOTIFY)
                    val reportChar = g.getService(GATT_SERVICE_UUID)?.getCharacteristic(REPORT_CHAR_UUID) ?: run {
                        g.disconnect(); return@launch
                    }

                    var pulledCount = 0
                    for (reportId in missing) {
                        val deferred = CompletableDeferred<String?>()
                        activeReportDeferred = deferred

                        reportChar.value = reportId.toByteArray(Charsets.UTF_8)
                        val wrote = g.writeCharacteristic(reportChar)
                        if (!wrote) continue

                        val reportJson = withTimeoutOrNull(8_000) { deferred.await() } ?: continue

                        insertRelayedReport(reportJson, device.address)
                        pulledCount++
                    }

                    onLog("✓ Pulled $pulledCount new reports from ${device.address}")

                    // Log the encounter as a RelayPacket
                    val packetId = UUID.randomUUID().toString()
                    val encounterJson = """{"type":"sync","remote":"${device.address}","pulled":$pulledCount}"""
                    relayPacketDao.insert(
                        RelayPacket(
                            packetId     = packetId,
                            payloadJson  = encounterJson,
                            receivedAt   = Instant.now().toString(),
                            senderDevice = device.address,
                            hopCount     = 0
                        )
                    )

                    g.disconnect()
                }
            }
        }

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        cont.invokeOnCancellation { gatt?.disconnect() }
    }

    /** Parse a report JSON received over GATT and insert into Room (additive — IGNORE on dup). */
    private suspend fun insertRelayedReport(json: String, senderDevice: String) {
        try {
            val obj = JSONObject(json)
            val report = TrailReport(
                reportId    = obj.getString("report_id"),
                userId      = obj.getString("user_id"),
                type        = try { ReportType.valueOf(obj.getString("type")) } catch (e: Exception) { ReportType.hazard },
                title       = obj.getString("title"),
                description = obj.optString("description", ""),
                lat         = obj.getDouble("lat"),
                lng         = obj.getDouble("lng"),
                h3Cell      = obj.optString("h3_cell").takeIf { it.isNotEmpty() && it != "null" },
                timestamp   = obj.getString("timestamp"),
                speciesName = obj.optString("species_name").takeIf { it.isNotEmpty() && it != "null" },
                confidence  = obj.optDouble("confidence").takeIf { !it.isNaN() }?.toFloat(),
                source      = ReportSource.relayed,
                synced      = false // will be uploaded to cloud on next sync
            )
            reportDao.insert(report) // OnConflictStrategy.IGNORE — safe to call even if we already have it
        } catch (e: Exception) {
            onLog("Failed to parse relayed report from $senderDevice: ${e.message}")
        }
    }
}
