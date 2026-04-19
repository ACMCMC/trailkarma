package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import fyi.acmc.trailkarma.db.RelayInboxMessageDao
import fyi.acmc.trailkarma.db.RelayJobIntentDao
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.db.TrailReportDao
import fyi.acmc.trailkarma.models.RelayInboxMessage
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.RelayPacket
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val MAX_HOP_COUNT = 5

@SuppressLint("MissingPermission")
class GattClient(
    private val context: Context,
    private val reportDao: TrailReportDao,
    private val relayPacketDao: RelayPacketDao,
    private val relayJobIntentDao: RelayJobIntentDao,
    private val relayInboxMessageDao: RelayInboxMessageDao,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var manifestDeferred: CompletableDeferred<String?>? = null
    private var payloadDeferred: CompletableDeferred<String?>? = null
    private var activeTransferUuid: UUID? = null
    private var reassemblyChunks: Array<ByteArray?>? = null
    private var reassemblyTotal: Int = 0
    private var reassemblyReceived: Int = 0
    private var reportNotificationsReady = false

    fun syncWithPeer(device: BluetoothDevice) {
        scope.launch {
            try {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    connectAndSync(device)
                }
            } catch (error: Exception) {
                onLog("BLE sync error with ${device.address}: ${error.message}")
            }
        }
    }

    private suspend fun connectAndSync(device: BluetoothDevice) = suspendCancellableCoroutine<Unit> { continuation ->
        var gatt: BluetoothGatt? = null
        reportNotificationsReady = false

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(517)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val service = g.getService(GATT_SERVICE_UUID) ?: run {
                    g.disconnect()
                    return
                }
                val reportChar = service.getCharacteristic(REPORT_CHAR_UUID)
                val descriptor = reportChar?.getDescriptor(CCCD_UUID)
                if (reportChar == null || descriptor == null) {
                    g.disconnect()
                    return
                }

                g.setCharacteristicNotification(reportChar, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (!reportNotificationsReady && descriptor.characteristic.uuid == REPORT_CHAR_UUID) {
                    reportNotificationsReady = true
                    val packetChar = g.getService(GATT_SERVICE_UUID)?.getCharacteristic(PACKET_CHAR_UUID)
                    val packetDescriptor = packetChar?.getDescriptor(CCCD_UUID)
                    if (packetChar != null && packetDescriptor != null) {
                        g.setCharacteristicNotification(packetChar, true)
                        packetDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(packetDescriptor)
                        return
                    }
                }

                scope.launch {
                    syncReports(g, device.address)
                    syncPackets(g, device.address)
                    g.disconnect()
                }
            }

            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val json = if (status == BluetoothGatt.GATT_SUCCESS) {
                    String(characteristic.value ?: ByteArray(0), Charsets.UTF_8)
                } else {
                    null
                }
                manifestDeferred?.complete(json)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid != activeTransferUuid) return
                val frame = characteristic.value ?: return
                if (frame.size < 4) return

                val seq = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
                val total = ((frame[2].toInt() and 0xFF) shl 8) or (frame[3].toInt() and 0xFF)
                val data = frame.copyOfRange(4, frame.size)

                if (seq == 0 || reassemblyChunks == null || reassemblyTotal != total) {
                    reassemblyChunks = arrayOfNulls(total)
                    reassemblyTotal = total
                    reassemblyReceived = 0
                }
                reassemblyChunks!![seq] = data
                reassemblyReceived++

                if (reassemblyReceived == total) {
                    val fullBytes = reassemblyChunks!!.flatMap { it!!.toList() }.toByteArray()
                    payloadDeferred?.complete(String(fullBytes, Charsets.UTF_8))
                    reassemblyChunks = null
                    reassemblyTotal = 0
                    reassemblyReceived = 0
                }
            }
        }

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        continuation.invokeOnCancellation { gatt?.disconnect() }
    }

    private suspend fun syncReports(gatt: BluetoothGatt, senderDevice: String) {
        onLog("🔄 Reading peer report manifest...")
        val peerIds = readIdManifest(gatt, MANIFEST_CHAR_UUID) ?: run {
            onLog("✗ Could not read peer report manifest from $senderDevice (timeout/error)")
            return
        }
        onLog("✓ Got peer manifest: ${peerIds.size} reports")

        val localIds = reportDao.getIds().toSet()
        val missing = peerIds - localIds
        onLog("📊 Report sync: peer=${peerIds.size}, local=${localIds.size}, missing=${missing.size}")

        if (missing.isEmpty()) {
            onLog("✓ All reports synced")
            return
        }

        onLog("📥 Requesting ${missing.size} missing reports...")
        var pulledCount = 0
        var failedCount = 0
        for (reportId in missing) {
            onLog("  → requesting $reportId...")
            val reportJson = requestPayload(gatt, REPORT_CHAR_UUID, reportId) ?: run {
                failedCount++
                onLog("  ✗ timeout getting $reportId")
                continue
            }
            onLog("  ✓ got $reportId (${reportJson.length} bytes)")
            insertRelayedReport(reportJson)
            pulledCount++
        }

        onLog("✓ Synced $pulledCount/${ missing.size} reports (${failedCount} timeouts)")
    }

    private suspend fun syncPackets(gatt: BluetoothGatt, senderDevice: String) {
        val peerIds = readIdManifest(gatt, PACKET_MANIFEST_CHAR_UUID) ?: return
        val localIds = relayPacketDao.getIds().toSet()
        val missing = peerIds - localIds
        if (missing.isEmpty()) return

        var pulledCount = 0
        for (packetId in missing) {
            val packetJson = requestPayload(gatt, PACKET_CHAR_UUID, packetId) ?: continue
            insertRelayedPacket(packetJson, senderDevice)
            pulledCount++
        }

        onLog("Pulled $pulledCount relay packets from $senderDevice")
    }

    private suspend fun readIdManifest(gatt: BluetoothGatt, characteristicUuid: UUID): Set<String>? {
        val characteristic = gatt.getService(GATT_SERVICE_UUID)?.getCharacteristic(characteristicUuid) ?: run {
            onLog("✗ Characteristic $characteristicUuid not found")
            return null
        }
        manifestDeferred = CompletableDeferred()
        val wrote = gatt.readCharacteristic(characteristic)
        onLog("  read request sent: $wrote")
        val json = withTimeoutOrNull(8_000) { manifestDeferred?.await() } ?: run {
            onLog("✗ Manifest read timeout")
            return null
        }
        onLog("  manifest received: ${json?.length} bytes")
        return try {
            val array = JSONArray(json)
            val ids = (0 until array.length()).map { array.getString(it) }.toSet()
            onLog("  parsed ${ids.size} IDs")
            ids
        } catch (e: Exception) {
            onLog("✗ Failed to parse manifest: ${e.message}")
            null
        }
    }

    private suspend fun requestPayload(gatt: BluetoothGatt, characteristicUuid: UUID, id: String): String? {
        val characteristic = gatt.getService(GATT_SERVICE_UUID)?.getCharacteristic(characteristicUuid) ?: run {
            onLog("✗ Characteristic not found for payload")
            return null
        }
        activeTransferUuid = characteristicUuid
        payloadDeferred = CompletableDeferred()
        characteristic.value = id.toByteArray(Charsets.UTF_8)
        val wrote = gatt.writeCharacteristic(characteristic)
        if (!wrote) {
            onLog("✗ Write characteristic failed for $id")
            return null
        }
        val payload = withTimeoutOrNull(8_000) { payloadDeferred?.await() } ?: run {
            onLog("✗ Payload timeout for $id")
            return null
        }
        return payload
    }

    private suspend fun insertRelayedReport(json: String) {
        try {
            val obj = JSONObject(json)
            val reportId = obj.getString("report_id")
            val title = obj.getString("title")
            val confidence = if (obj.isNull("confidence")) null else obj.optDouble("confidence").toFloat()
            reportDao.insert(
                TrailReport(
                    reportId = reportId,
                    userId = obj.getString("user_id"),
                    type = try {
                        ReportType.valueOf(obj.getString("type"))
                    } catch (_: Exception) {
                        ReportType.hazard
                    },
                    title = title,
                    description = obj.optString("description", ""),
                    lat = obj.getDouble("lat"),
                    lng = obj.getDouble("lng"),
                    h3Cell = obj.optString("h3_cell").takeIf { it.isNotBlank() && it != "null" },
                    timestamp = obj.getString("timestamp"),
                    speciesName = obj.optString("species_name").takeIf { it.isNotBlank() && it != "null" },
                    confidence = confidence,
                    source = ReportSource.relayed,
                    synced = false
                )
            )
            onLog("✓ Received via BLE: $title ($reportId)")
        } catch (error: Exception) {
            onLog("✗ Failed to parse relayed report: ${error.message}")
        }
    }

    private suspend fun insertRelayedPacket(json: String, senderDevice: String) {
        try {
            val wrapper = JSONObject(json)
            val packetId = wrapper.getString("packet_id")
            val payloadJson = wrapper.getString("payload_json")
            val hopCount = wrapper.optInt("hop_count", 0)
            if (hopCount >= MAX_HOP_COUNT) return

            relayPacketDao.insert(
                RelayPacket(
                    packetId = packetId,
                    payloadJson = payloadJson,
                    receivedAt = wrapper.optString("received_at", Instant.now().toString()),
                    senderDevice = senderDevice,
                    hopCount = hopCount + 1
                )
            )

            val payload = JSONObject(payloadJson)
            when (payload.optString("type")) {
                "voice_relay_intent" -> relayJobIntentDao.insert(
                    RelayJobIntent(
                        jobId = payload.getString("job_id"),
                        userId = payload.getString("user_id"),
                        senderWallet = payload.getString("sender_wallet"),
                        relayType = payload.optString("relay_type", "voice_outbound"),
                        recipientName = payload.optString("recipient_name"),
                        recipientPhoneNumber = payload.optString("recipient_phone_number"),
                        destinationHash = payload.getString("destination_hash"),
                        payloadHash = payload.getString("payload_hash"),
                        messageBody = payload.optString("message_body"),
                        contextSummary = payload.optString("context_summary"),
                        contextJson = payload.optJSONObject("context_json")?.toString() ?: "{}",
                        expiryTs = payload.getLong("expiry_ts"),
                        rewardAmount = payload.getInt("reward_amount"),
                        nonce = payload.getLong("nonce"),
                        signedMessageBase64 = payload.getString("signed_message_base64"),
                        signatureBase64 = payload.getString("signature_base64"),
                        source = "mesh",
                        status = "queued_offline",
                        createdAt = Instant.now().toString()
                    )
                )

                "relay_reply" -> relayInboxMessageDao.insert(
                    RelayInboxMessage(
                        replyId = payload.getString("reply_id"),
                        originalJobId = payload.getString("original_job_id"),
                        userId = payload.getString("user_id"),
                        senderLabel = payload.optString("sender_label", "Relay reply"),
                        senderPhoneNumber = payload.optString("sender_phone_number"),
                        messageSummary = payload.optString("message_summary"),
                        messageBody = payload.optString("message_body"),
                        contextJson = payload.optJSONObject("context_json")?.toString() ?: "{}",
                        createdAt = payload.optString("created_at", Instant.now().toString()),
                        status = payload.optString("status", "pending")
                    )
                )
            }
        } catch (error: Exception) {
            onLog("Failed to parse relayed packet: ${error.message}")
        }
    }
}
