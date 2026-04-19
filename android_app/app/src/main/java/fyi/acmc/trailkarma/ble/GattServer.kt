package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.db.TrailReportDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private const val TAG = "TrailKarma/GattServer"
private const val MAX_NOTIFY_CHUNK = 512

val GATT_SERVICE_UUID: UUID = UUID.fromString("0000FE2D-0000-1000-8000-00805F9B34FB")
val MANIFEST_CHAR_UUID: UUID = UUID.fromString("0000FE2E-0000-1000-8000-00805F9B34FB")
val REPORT_CHAR_UUID: UUID = UUID.fromString("0000FE2F-0000-1000-8000-00805F9B34FB")
val PACKET_MANIFEST_CHAR_UUID: UUID = UUID.fromString("0000FE30-0000-1000-8000-00805F9B34FB")
val PACKET_CHAR_UUID: UUID = UUID.fromString("0000FE31-0000-1000-8000-00805F9B34FB")
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
class GattServer(
    private val context: Context,
    private val reportDao: TrailReportDao,
    private val relayPacketDao: RelayPacketDao,
    private val onPeerServed: (String) -> Unit = {}
) {
    private data class PendingNotification(
        val device: BluetoothDevice,
        val characteristicUuid: UUID,
        val frame: ByteArray
    )

    private val scope = CoroutineScope(Dispatchers.IO)
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val notificationLock = Any()
    private val pendingNotifications = ArrayDeque<PendingNotification>()
    private var notificationInFlight: PendingNotification? = null
    private val peersServedThisConnection = mutableSetOf<String>()

    private val reportCache = mutableMapOf<String, String>()
    private val packetCache = mutableMapOf<String, String>()

    fun start() {
        gattServer = btManager.openGattServer(context, serverCallback)
        gattServer?.addService(buildService())
        Log.d(TAG, "GATT server started")
    }

    fun stop() {
        synchronized(notificationLock) {
            pendingNotifications.clear()
            notificationInFlight = null
        }
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT server stopped")
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(GATT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val manifestChar = BluetoothGattCharacteristic(
            MANIFEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        manifestChar.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))

        val reportChar = BluetoothGattCharacteristic(
            REPORT_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        reportChar.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))

        val packetManifestChar = BluetoothGattCharacteristic(
            PACKET_MANIFEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        packetManifestChar.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))

        val packetChar = BluetoothGattCharacteristic(
            PACKET_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        packetChar.addDescriptor(BluetoothGattDescriptor(CCCD_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))

        service.addCharacteristic(manifestChar)
        service.addCharacteristic(reportChar)
        service.addCharacteristic(packetManifestChar)
        service.addCharacteristic(packetChar)
        return service
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val state = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
            Log.d(TAG, "Peer $state: ${device.address}")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val servedPeer = synchronized(notificationLock) {
                    peersServedThisConnection.remove(device.address)
                }
                synchronized(notificationLock) {
                    pendingNotifications.removeAll { it.device.address == device.address }
                    if (notificationInFlight?.device?.address == device.address) {
                        notificationInFlight = null
                    }
                }
                drainNotificationQueue()
                if (servedPeer) {
                    onPeerServed(device.address)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val request = String(value, Charsets.UTF_8)
            Log.d(TAG, "✍️ onCharacteristicWriteRequest: uuid=${characteristic.uuid}, request=$request, responseNeeded=$responseNeeded")
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            when (characteristic.uuid) {
                MANIFEST_CHAR_UUID -> scope.launch {
                    markPeerServed(device.address)
                    Log.d(TAG, "📋 Building report manifest...")
                    val ids = manifestIdsForRequest(request, reportDao::getIds, reportDao::getIdsSince)
                    val json = ids.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    Log.d(TAG, "📢 Sending manifest notification: ${json.length} bytes, ${ids.size} IDs")
                    notifyJson(device, MANIFEST_CHAR_UUID, json)
                    Log.d(TAG, "✓ Manifest notification sent")
                }

                PACKET_MANIFEST_CHAR_UUID -> scope.launch {
                    markPeerServed(device.address)
                    val ids = manifestIdsForRequest(request, relayPacketDao::getIds, relayPacketDao::getIdsSince)
                    val json = ids.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    notifyJson(device, PACKET_MANIFEST_CHAR_UUID, json)
                }

                REPORT_CHAR_UUID -> scope.launch {
                    markPeerServed(device.address)
                    val json = buildReportJson(request)
                    if (json != null) notifyJson(device, REPORT_CHAR_UUID, json)
                }

                PACKET_CHAR_UUID -> scope.launch {
                    markPeerServed(device.address)
                    val json = buildPacketJson(request)
                    if (json != null) notifyJson(device, PACKET_CHAR_UUID, json)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Notification failed for ${device.address} with status=$status")
            }
            synchronized(notificationLock) {
                if (notificationInFlight?.device?.address == device.address) {
                    notificationInFlight = null
                }
            }
            drainNotificationQueue()
        }
    }

    private suspend fun buildReportJson(reportId: String): String? {
        reportCache[reportId]?.let { return it }
        val report = reportDao.getById(reportId) ?: return null
        val json = JSONObject()
            .put("report_id", report.reportId)
            .put("user_id", report.userId)
            .put("type", report.type.name)
            .put("title", report.title)
            .put("description", report.description)
            .put("lat", report.lat)
            .put("lng", report.lng)
            .put("h3_cell", report.h3Cell ?: JSONObject.NULL)
            .put("timestamp", report.timestamp)
            .put("species_name", report.speciesName ?: JSONObject.NULL)
            .put("confidence", report.confidence ?: JSONObject.NULL)
            .put("source", report.source.name)
            .toString()
        reportCache[reportId] = json
        return json
    }

    private suspend fun buildPacketJson(packetId: String): String? {
        packetCache[packetId]?.let { return it }
        val packet = relayPacketDao.getById(packetId) ?: return null
        val json = JSONObject()
            .put("packet_id", packet.packetId)
            .put("payload_json", packet.payloadJson)
            .put("received_at", packet.receivedAt)
            .put("sender_device", packet.senderDevice)
            .put("hop_count", packet.hopCount)
            .toString()
        packetCache[packetId] = json
        return json
    }

    private fun respondJson(device: BluetoothDevice, requestId: Int, offset: Int, json: String) {
        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, json.toByteArray(Charsets.UTF_8))
    }

    private fun notifyJson(device: BluetoothDevice, characteristicUuid: UUID, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val chunkSize = MAX_NOTIFY_CHUNK - 4
        val chunks = bytes.toList().chunked(chunkSize)
        Log.d(TAG, "🔔 Notifying ${chunks.size} chunks (total ${bytes.size} bytes) to ${device.address}")

        synchronized(notificationLock) {
            for ((index, chunk) in chunks.withIndex()) {
                val frame = ByteArray(4 + chunk.size)
                frame[0] = (index shr 8).toByte()
                frame[1] = (index and 0xFF).toByte()
                frame[2] = (chunks.size shr 8).toByte()
                frame[3] = (chunks.size and 0xFF).toByte()
                chunk.toByteArray().copyInto(frame, 4)
                pendingNotifications.addLast(
                    PendingNotification(
                        device = device,
                        characteristicUuid = characteristicUuid,
                        frame = frame
                    )
                )
            }
        }
        drainNotificationQueue()
    }

    private fun drainNotificationQueue() {
        val next = synchronized(notificationLock) {
            if (notificationInFlight != null) {
                return
            }
            pendingNotifications.removeFirstOrNull()?.also {
                notificationInFlight = it
            }
        } ?: return

        val characteristic = gattServer?.getService(GATT_SERVICE_UUID)?.getCharacteristic(next.characteristicUuid)
        if (characteristic == null) {
            Log.e(TAG, "✗ Characteristic ${next.characteristicUuid} not found for notification")
            synchronized(notificationLock) {
                if (notificationInFlight == next) {
                    notificationInFlight = null
                }
            }
            drainNotificationQueue()
            return
        }

        characteristic.value = next.frame
        Log.d(TAG, "  sending chunk ${next.frame.size} bytes to ${next.device.address}")
        val started = gattServer?.notifyCharacteristicChanged(next.device, characteristic, false) ?: false
        if (!started) {
            Log.e(TAG, "✗ notifyCharacteristicChanged returned false for ${next.device.address}")
            synchronized(notificationLock) {
                if (notificationInFlight == next) {
                    notificationInFlight = null
                }
            }
            drainNotificationQueue()
            return
        }

        Log.d(TAG, "✓ Notification enqueued for ${next.device.address}")
    }

    private fun markPeerServed(address: String) {
        synchronized(notificationLock) {
            peersServedThisConnection.add(address)
        }
    }

    private suspend fun manifestIdsForRequest(
        request: String,
        allIds: suspend () -> List<String>,
        idsSince: suspend (String) -> List<String>
    ): List<String> {
        val prefix = "manifest_since:"
        val since = request.substringAfter(prefix, "").takeIf { request.startsWith(prefix) && it.isNotBlank() }
        return if (since != null) idsSince(since) else allIds()
    }
}
