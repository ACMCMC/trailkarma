package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val syncRepo = DatabricksSyncRepository(applicationContext, db)

        // Only sync if online and Databricks is configured
        if (!syncRepo.isOnline()) return Result.retry()

        runCatching {
            // Push local changes to cloud
            syncRepo.syncReports()
            syncRepo.syncLocations()
            syncRepo.syncRelayPackets() // upload BLE encounter + relay data

            // Pull all data from cloud to local
            syncRepo.pullReportsFromCloud()
        }.getOrNull() ?: return Result.retry()

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
