package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val syncRepo = DatabricksSyncRepository(applicationContext, db)

        // Only sync if online and Databricks is configured
        if (!syncRepo.isOnline()) return Result.retry()

        return try {
            // Push local changes to cloud
            syncRepo.syncReports()
            syncRepo.syncLocations()
            syncRepo.syncRelayPackets() // upload BLE encounter + relay data

            // Pull all data from cloud to local
            syncRepo.pullReportsFromCloud()
            Result.success()
        } catch (e: CancellationException) {
            android.util.Log.w("SyncWorker", "Sync cancelled, will retry", e)
            Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
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
