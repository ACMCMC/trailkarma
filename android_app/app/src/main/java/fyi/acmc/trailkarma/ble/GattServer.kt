package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import fyi.acmc.trailkarma.db.TrailReportDao
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

private const val TAG = "TrailKarma/GattServer"

// ──────────────────────────────────────────────────────────────────────────────
// Shared UUIDs (must match GattClient on the other phone)
// ──────────────────────────────────────────────────────────────────────────────
val GATT_SERVICE_UUID: UUID  = UUID.fromString("0000FE2D-0000-1000-8000-00805F9B34FB")

/** READ — returns JSON array of report UUIDs this phone knows about */
val MANIFEST_CHAR_UUID: UUID = UUID.fromString("0000FE2E-0000-1000-8000-00805F9B34FB")

/** WRITE (client writes a reportId) + NOTIFY (server streams back the report JSON) */
val REPORT_CHAR_UUID: UUID   = UUID.fromString("0000FE2F-0000-1000-8000-00805F9B34FB")

val CCCD_UUID: UUID          = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

/** Maximum bytes that fit in a single BLE notification (ATT_MTU 23 - 3 = 20 safe default) */
private const val MAX_NOTIFY_CHUNK = 512 // post-MTU-exchange, 512 is the BLE max

@SuppressLint("MissingPermission")
class GattServer(
    private val context: Context,
    private val reportDao: TrailReportDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    // Cache: reportId -> full report JSON (populated on demand)
    private val reportCache = mutableMapOf<String, String>()

    fun start() {
        gattServer = btManager.openGattServer(context, serverCallback)
        gattServer?.addService(buildService())
        Log.d(TAG, "GATT server started")
    }

    fun stop() {
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT server stopped")
    }

    // ── Service definition ────────────────────────────────────────────────────

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(GATT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // MANIFEST characteristic — client READs this to get the list of report IDs we have
        val manifestChar = BluetoothGattCharacteristic(
            MANIFEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // REPORT characteristic — client WRITEs a reportId, we NOTIFY back the report JSON
        val reportChar = BluetoothGattCharacteristic(
            REPORT_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        // CCCD descriptor — required for notifications
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        reportChar.addDescriptor(cccd)

        service.addCharacteristic(manifestChar)
        service.addCharacteristic(reportChar)
        return service
    }

    // ── GATT callbacks ────────────────────────────────────────────────────────

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val state = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
            Log.d(TAG, "Peer $state: ${device.address}")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                MANIFEST_CHAR_UUID -> {
                    // Return JSON array of all report IDs we have, in one read.
                    // For very large sets (1000s of IDs) this would need pagination — fine for hackathon.
                    scope.launch {
                        val ids = reportDao.getIds()
                        val json = "[${ids.joinToString(",") { "\"$it\"" }}]"
                        val bytes = json.toByteArray(Charsets.UTF_8)
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes)
                        Log.d(TAG, "Served manifest (${ids.size} reports) to ${device.address}")
                    }
                }
                else -> gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (characteristic.uuid != REPORT_CHAR_UUID) {
                if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }

            val requestedId = String(value, Charsets.UTF_8)
            Log.d(TAG, "Peer ${device.address} requested report: $requestedId")

            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)

            // Stream the report back via notification
            scope.launch {
                val json = buildReportJson(requestedId)
                if (json == null) {
                    Log.w(TAG, "Report $requestedId not found locally")
                    return@launch
                }
                notifyReport(device, json)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            // Client enabling/disabling notifications — always ACK
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun buildReportJson(reportId: String): String? {
        // Use cache to avoid repeated DB reads for the same report in one session
        reportCache[reportId]?.let { return it }

        val report = reportDao.getById(reportId) ?: return null
        val json = JSONObject().apply {
            put("report_id",    report.reportId)
            put("user_id",      report.userId)
            put("type",         report.type.name)
            put("title",        report.title)
            put("description",  report.description)
            put("lat",          report.lat)
            put("lng",          report.lng)
            put("h3_cell",      report.h3Cell ?: JSONObject.NULL)
            put("timestamp",    report.timestamp)
            put("species_name", report.speciesName ?: JSONObject.NULL)
            put("confidence",   report.confidence ?: JSONObject.NULL)
            put("source",       report.source.name)
        }.toString()
        reportCache[reportId] = json
        return json
    }

    @SuppressLint("MissingPermission")
    private fun notifyReport(device: BluetoothDevice, json: String) {
        val reportChar = gattServer
            ?.getService(GATT_SERVICE_UUID)
            ?.getCharacteristic(REPORT_CHAR_UUID) ?: return

        // Chunk into MAX_NOTIFY_CHUNK byte pieces if needed; prefix with [seq/total] header
        val bytes = json.toByteArray(Charsets.UTF_8)
        val chunkSize = MAX_NOTIFY_CHUNK - 4 // 4 bytes for simple [seq:total] framing
        val chunks = bytes.toList().chunked(chunkSize)

        for ((i, chunk) in chunks.withIndex()) {
            // Simple framing: 2-byte seq, 2-byte total, then data
            val frame = ByteArray(4 + chunk.size)
            frame[0] = (i shr 8).toByte()
            frame[1] = (i and 0xFF).toByte()
            frame[2] = (chunks.size shr 8).toByte()
            frame[3] = (chunks.size and 0xFF).toByte()
            chunk.toByteArray().copyInto(frame, 4)

            reportChar.value = frame
            gattServer?.notifyCharacteristicChanged(device, reportChar, false)
        }
        Log.d(TAG, "Notified report to ${device.address} in ${chunks.size} chunk(s)")
    }
}
