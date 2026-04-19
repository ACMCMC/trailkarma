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

            val sql = """
                INSERT INTO workspace.trailkarma.trail_reports
                (report_id, user_id, type, title, description, lat, lng, timestamp, species_name, confidence, source, synced)
                VALUES ('${report.reportId}', '${report.userId}', '${report.type.name}', '${report.title}',
                '${report.description}', ${report.lat}, ${report.lng}, '${report.timestamp}',
                $species, $confVal, '${report.source.name}', true)
            """.trimIndent()

            try {
                val request = DatabricksSyncRequest(warehouseId, sql)
                val response = api.executeSql(request)

                if (response.status.state == "SUCCEEDED") {
                    Log.d("DatabricksSync", "✓ Uploaded report: ${report.title}")
                    db.trailReportDao().markSynced(report.reportId)
                } else {
                    Log.e("DatabricksSync", "✗ Failed to upload: ${report.title}")
                    success = false
                }
            } catch (e: Exception) {
                Log.e("DatabricksSync", "✗ Error uploading: ${report.title}", e)
                success = false
            }
        }

        return success
    }

    suspend fun pullReportsFromCloud(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        try {
            val api = DatabricksApiClient.create(databricksUrl, databricksToken)
            val selectSql = "SELECT report_id, user_id, type, title, description, lat, lng, timestamp, species_name, confidence, source FROM workspace.trailkarma.trail_reports ORDER BY timestamp DESC"
            val request = DatabricksSyncRequest(warehouseId, selectSql)
            val response = api.executeSql(request)

            if (response.status.state != "SUCCEEDED") {
                Log.e("DatabricksSync", "✗ Pull failed: ${response.status.state}")
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

                    val reportId = row.getOrNull(0) as? String ?: continue
                    val userId = row.getOrNull(1) as? String ?: continue
                    val typeStr = row.getOrNull(2) as? String ?: "hazard"
                    val type = try { ReportType.valueOf(typeStr) } catch (e: Exception) { ReportType.hazard }
                    val title = row.getOrNull(3)?.toString() ?: ""
                    val description = row.getOrNull(4)?.toString() ?: ""
                    val lat = row.getOrNull(5)?.toString()?.toDoubleOrNull() ?: 0.0
                    val lng = row.getOrNull(6)?.toString()?.toDoubleOrNull() ?: 0.0
                    val timestamp = row.getOrNull(7)?.toString() ?: ""
                    val speciesName = row.getOrNull(8)?.toString()
                    val confidence = row.getOrNull(9)?.toString()?.toFloatOrNull()
                    val sourceStr = row.getOrNull(10)?.toString() ?: "self"
                    val source = try { ReportSource.valueOf(sourceStr) } catch (e: Exception) { ReportSource.self }

                    val report = TrailReport(
                        reportId = reportId,
                        userId = userId,
                        type = type,
                        title = title,
                        description = description,
                        lat = lat,
                        lng = lng,
                        timestamp = timestamp,
                        speciesName = speciesName,
                        confidence = confidence,
                        source = source,
                        synced = true
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
            val sql = """
                INSERT INTO workspace.trailkarma.location_updates (id, user_id, timestamp, lat, lng, synced)
                VALUES ('${location.id}', '${location.userId}', '${location.timestamp}', ${location.lat}, ${location.lng}, true)
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
            val rows = response.result?.data_array ?: return true

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
}
