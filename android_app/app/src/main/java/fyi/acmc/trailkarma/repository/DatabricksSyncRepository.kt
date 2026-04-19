package fyi.acmc.trailkarma.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fyi.acmc.trailkarma.api.DatabricksApiClient
import fyi.acmc.trailkarma.api.DatabricksSyncRequest
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.network.NetworkUtil
import okhttp3.OkHttpClient

class DatabricksSyncRepository(context: Context, private val db: AppDatabase) {
    private val prefs: SharedPreferences = context.getSharedPreferences("databricks", Context.MODE_PRIVATE)
    private val networkUtil = NetworkUtil(context)

    private val warehouseId: String
        get() = prefs.getString("warehouse_id", "") ?: ""

    private val databricksUrl: String
        get() = prefs.getString("databricks_url", "") ?: ""

    private val databricksToken: String
        get() = prefs.getString("databricks_token", "") ?: ""

    fun setDatabricksConfig(url: String, token: String, warehouse: String) {
        prefs.edit().apply {
            putString("databricks_url", url)
            putString("databricks_token", token)
            putString("warehouse_id", warehouse)
            apply()
        }
    }

    suspend fun syncReports(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        val api = DatabricksApiClient.create(databricksUrl, databricksToken)
        val reports = db.trailReportDao().getUnsynced()

        Log.d("DatabricksSync", "Pushing ${reports.size} unsynced reports to Databricks")

        var success = true
        for (report in reports) {
            val species = if (report.type.name == "species") "'${report.speciesName}'" else "NULL"
            val conf = if (report.type.name == "species") report.confidence else null
            val confVal = if (conf != null) conf.toString() else "NULL"

            // MERGE INTO = Delta Lake's "insert if not exists" — deduplicates by report_id UUID
            // h3_cell is computed server-side by Databricks — no JNI library needed on Android
            val sql = """
                MERGE INTO workspace.trailkarma.trail_reports AS target
                USING (SELECT
                    '${report.reportId}'  AS report_id,
                    '${report.userId}'    AS user_id,
                    '${report.type.name}' AS type,
                    '${report.title}'     AS title,
                    '${report.description}' AS description,
                    ${report.lat}         AS lat,
                    ${report.lng}         AS lng,
                    h3_longlatash3(${report.lng}, ${report.lat}, 9) AS h3_cell,
                    '${report.timestamp}' AS timestamp,
                    $species              AS species_name,
                    $confVal              AS confidence,
                    '${report.source.name}' AS source
                ) AS source ON target.report_id = source.report_id
                WHEN NOT MATCHED THEN INSERT (report_id, user_id, type, title, description, lat, lng, h3_cell, timestamp, species_name, confidence, source)
                VALUES (source.report_id, source.user_id, source.type, source.title, source.description, source.lat, source.lng, source.h3_cell, source.timestamp, source.species_name, source.confidence, source.source)
            """.trimIndent()

            try {
                val request = DatabricksSyncRequest(warehouseId, sql)
                val response = api.executeSql(request)

                if (response.status.state == "SUCCEEDED") {
                    Log.d("DatabricksSync", "✓ Uploaded report: ${report.title}")
                    db.trailReportDao().markSynced(report.reportId)
                } else {
                    val errorMsg = response.status.error?.message ?: "no error detail"
                    Log.e("DatabricksSync", "✗ Failed to upload: ${report.title} [${response.status.state}] $errorMsg")
                    success = false
                }
            } catch (e: Exception) {
                when (e) {
                    is kotlinx.coroutines.JobCancellationException -> Log.e("DatabricksSync", "✗ Sync cancelled: ${report.title}")
                    else -> Log.e("DatabricksSync", "✗ Error uploading: ${report.title}", e)
                }
                success = false
            }
        }

        return success
    }

    suspend fun pullReportsFromCloud(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        try {
            val api = DatabricksApiClient.create(databricksUrl, databricksToken)
            // h3_cell intentionally excluded — it's a server-side analytics column.
            // Android stores lat/lng; Databricks computes h3_cell during MERGE INTO.
            val selectSql = """
                SELECT report_id, user_id, type, title, description, lat, lng,
                       timestamp, species_name, confidence, source
                FROM workspace.trailkarma.trail_reports
                ORDER BY timestamp DESC
                LIMIT 500
            """.trimIndent()
            val request = DatabricksSyncRequest(warehouseId, selectSql)
            val response = api.executeSql(request)

            if (response.status.state != "SUCCEEDED") {
                val reason = response.status.error?.message ?: "no error detail"
                Log.e("DatabricksSync", "✗ Pull reports failed [${response.status.state}]: $reason")
                return false
            }

            val result = response.result
            val rows = result?.data_array
            if (rows.isNullOrEmpty()) {
                Log.d("DatabricksSync", "No reports in cloud")
                return true
            }

            Log.d("DatabricksSync", "Pulling ${rows.size} reports from Databricks")

            var pulledCount = 0
            for (row in rows) {
                try {
                    if (row.size < 11) continue

                    val reportId     = row.getOrNull(0) as? String ?: continue
                    val userId       = row.getOrNull(1) as? String ?: continue
                    val typeStr      = row.getOrNull(2) as? String ?: "hazard"
                    val type         = try { ReportType.valueOf(typeStr) } catch (e: Exception) { ReportType.hazard }
                    val title        = row.getOrNull(3)?.toString() ?: ""
                    val description  = row.getOrNull(4)?.toString() ?: ""
                    val lat          = row.getOrNull(5)?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng          = row.getOrNull(6)?.toString()?.toDoubleOrNull() ?: 0.0
                    val timestamp    = row.getOrNull(7)?.toString() ?: ""
                    val speciesName  = row.getOrNull(8)?.toString()
                    val confidence   = row.getOrNull(9)?.toString()?.toFloatOrNull()
                    val sourceStr    = row.getOrNull(10)?.toString() ?: "self"
                    val source       = try { ReportSource.valueOf(sourceStr) } catch (e: Exception) { ReportSource.self }

                    val report = TrailReport(
                        reportId    = reportId,
                        userId      = userId,
                        type        = type,
                        title       = title,
                        description = description,
                        lat         = lat,
                        lng         = lng,
                        h3Cell      = null, // populated server-side; not in SELECT
                        timestamp   = timestamp,
                        speciesName = speciesName,
                        confidence  = confidence,
                        source      = source,
                        synced      = true
                    )

                    db.trailReportDao().insert(report)
                    Log.d("DatabricksSync", "✓ Synced: $title")
                    pulledCount++
                } catch (e: Exception) {
                    Log.w("DatabricksSync", "Failed to parse row", e)
                }
            }

            Log.d("DatabricksSync", "✓ Successfully synced $pulledCount reports from cloud")
            return true
        } catch (e: Exception) {
            Log.e("DatabricksSync", "✗ Error pulling from cloud", e)
            return false
        }
    }

    suspend fun syncLocations(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        val api = DatabricksApiClient.create(databricksUrl, databricksToken)
        val locations = db.locationUpdateDao().getUnsynced()

        var success = true
        for (location in locations) {
            // MERGE INTO = Delta Lake's "insert if not exists" — deduplicates by location UUID
            // h3_cell computed server-side so no JNI needed on Android
            val sql = """
                MERGE INTO workspace.trailkarma.location_updates AS target
                USING (SELECT
                    '${location.id}'        AS id,
                    '${location.userId}'    AS user_id,
                    '${location.timestamp}' AS timestamp,
                    ${location.lat}         AS lat,
                    ${location.lng}         AS lng,
                    h3_longlatash3(${location.lng}, ${location.lat}, 9) AS h3_cell
                ) AS source ON target.id = source.id
                WHEN NOT MATCHED THEN INSERT (id, user_id, timestamp, lat, lng, h3_cell)
                VALUES (source.id, source.user_id, source.timestamp, source.lat, source.lng, source.h3_cell)
            """.trimIndent()

            try {
                val request = DatabricksSyncRequest(warehouseId, sql)
                val response = api.executeSql(request)

                if (response.status.state == "SUCCEEDED") {
                    db.locationUpdateDao().markSynced(location.id)
                } else {
                    success = false
                }
            } catch (e: Exception) {
                success = false
            }
        }

        return success
    }

    fun isOnline(): Boolean = networkUtil.isOnlineNow()

    suspend fun pullTrailsFromCloud(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        return try {
            val api = DatabricksApiClient.create(databricksUrl, databricksToken)
            val request = DatabricksSyncRequest(
                warehouse_id = warehouseId,
                statement = "SELECT trail_id, name, description, total_length_miles, region, geometry_json FROM workspace.trailkarma.trails"
            )

            val response = api.executeSql(request)
            
            if (response.status.state != "SUCCEEDED") {
                Log.e("DatabricksSync", "✗ Trails pull failed: ${response.status.state}")
                if (response.status.state == "FAILED") {
                    Log.e("DatabricksSync", "✗ Error: ${response.status.error?.message}")
                }
                return false
            }

            val rows = response.result?.data_array
            if (rows == null) {
                Log.e("DatabricksSync", "✗ Trails pull succeeded but data_array was null")
                return false
            }

            val trails = mutableListOf<fyi.acmc.trailkarma.models.Trail>()
            for (row in rows) {
                if (row.size < 6) continue
                val trailId = row.getOrNull(0)?.toString() ?: continue
                val name = row.getOrNull(1)?.toString() ?: continue
                val description = row.getOrNull(2)?.toString()
                val length = row.getOrNull(3)?.toString()?.toDoubleOrNull()
                val region = row.getOrNull(4)?.toString()
                val geometry = row.getOrNull(5)?.toString()

                trails.add(fyi.acmc.trailkarma.models.Trail(trailId, name, description, length, region, geometry))
            }

            if (trails.isNotEmpty()) {
                db.trailDao().insertAll(trails)
                Log.d("DatabricksSync", "✓ Pulled ${trails.size} trails from Databricks")
            }

            true
        } catch (e: Exception) {
            Log.e("DatabricksSync", "✗ Error pulling trails from cloud", e)
            false
        }
    }

    suspend fun syncRelayPackets(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        val api = DatabricksApiClient.create(databricksUrl, databricksToken)
        val packets = db.relayPacketDao().getPending()
        Log.d("DatabricksSync", "Uploading ${packets.size} relay packets to Databricks")

        val ids = mutableListOf<String>()
        for (packet in packets) {
            val safePayload = packet.payloadJson.replace("'", "''")
            val sql = """
                MERGE INTO workspace.trailkarma.relay_packets AS target
                USING (SELECT
                    '${packet.packetId}'     AS packet_id,
                    '$safePayload'           AS payload_json,
                    '${packet.receivedAt}'   AS received_at,
                    '${packet.senderDevice}' AS sender_device,
                    ${packet.hopCount}       AS hop_count
                ) AS source ON target.packet_id = source.packet_id
                WHEN NOT MATCHED THEN INSERT (packet_id, payload_json, received_at, sender_device, hop_count)
                VALUES (source.packet_id, source.payload_json, source.received_at, source.sender_device, source.hop_count)
            """.trimIndent()
            try {
                val response = api.executeSql(DatabricksSyncRequest(warehouseId, sql))
                if (response.status.state == "SUCCEEDED") ids.add(packet.packetId)
            } catch (e: Exception) {
                Log.e("DatabricksSync", "✗ relay packet upload error", e)
            }
        }

        if (ids.isNotEmpty()) db.relayPacketDao().markSynced(ids)
        Log.d("DatabricksSync", "✓ Uploaded ${ids.size}/${packets.size} relay packets")
        return ids.size == packets.size
    }
}
