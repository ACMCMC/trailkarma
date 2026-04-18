package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.*
import fyi.acmc.trailkarma.BuildConfig
import fyi.acmc.trailkarma.api.ApiClient
import fyi.acmc.trailkarma.api.LocationSyncRequest
import fyi.acmc.trailkarma.api.ReportSyncRequest
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.ReportSource

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val api = ApiClient.create(BuildConfig.API_BASE_URL)
        val userId = db.userDao().getFirst()?.userId ?: return Result.success()

        db.trailReportDao().getUnsynced().forEach { report ->
            runCatching {
                api.syncReport(ReportSyncRequest(
                    reportId = report.reportId, type = report.type.name,
                    title = report.title, description = report.description,
                    lat = report.lat, lng = report.lng, timestamp = report.timestamp,
                    source = report.source.name, speciesName = report.speciesName
                ))
            }.onSuccess { db.trailReportDao().markSynced(report.reportId) }
        }

        db.locationUpdateDao().getUnsynced().forEach { loc ->
            runCatching {
                api.syncLocation(LocationSyncRequest(userId, loc.lat, loc.lng, loc.timestamp))
            }.onSuccess { db.locationUpdateDao().markSynced(loc.id) }
        }

        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync", ExistingWorkPolicy.KEEP, request)
        }
    }
}
