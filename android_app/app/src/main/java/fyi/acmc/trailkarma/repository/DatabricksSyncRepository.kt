package fyi.acmc.trailkarma.repository

import android.content.Context
import android.content.SharedPreferences
import fyi.acmc.trailkarma.api.DatabricksApi
import fyi.acmc.trailkarma.api.DatabricksSyncRequest
import fyi.acmc.trailkarma.api.DatabricksApiClient
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.network.NetworkUtil
import kotlinx.coroutines.flow.first

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

        var success = true
        for (report in reports) {
            val species = if (report.type.name == "species") "'${report.speciesName}'" else "NULL"
            val conf = if (report.type.name == "species") report.confidence else null
            val confVal = if (conf != null) conf.toString() else "NULL"

            val sql = """
                INSERT INTO trailkarma.trail_reports
                (report_id, type, title, description, lat, lng, timestamp, species_name, confidence, source, synced)
                VALUES ('${report.reportId}', '${report.type.name}', '${report.title}',
                '${report.description}', ${report.lat}, ${report.lng}, '${report.timestamp}',
                $species, $confVal, '${report.source.name}', true)
            """.trimIndent()

            try {
                val request = DatabricksSyncRequest(warehouseId, sql)
                val response = api.executeSql(request)

                if (response.status.state == "SUCCEEDED") {
                    db.trailReportDao().markSynced(report.reportId)
                } else {
                    success = false
                }
            } catch (e: Exception) {
                success = false
            }
        }

        return success
    }

    suspend fun syncLocations(): Boolean {
        if (!networkUtil.isOnlineNow() || warehouseId.isEmpty()) return false

        val api = DatabricksApiClient.create(databricksUrl, databricksToken)
        val locations = db.locationUpdateDao().getUnsynced()

        var success = true
        for (location in locations) {
            val sql = """
                INSERT INTO trailkarma.location_updates (id, timestamp, lat, lng, synced)
                VALUES ('${location.id}', '${location.timestamp}', ${location.lat}, ${location.lng}, true)
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
}
