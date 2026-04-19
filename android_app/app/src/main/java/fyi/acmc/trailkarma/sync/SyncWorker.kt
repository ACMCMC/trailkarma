package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.repository.RewardsRepository
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val syncRepo = DatabricksSyncRepository(applicationContext, db)
        val rewardsRepo = RewardsRepository(applicationContext, db)

        if (!syncRepo.isOnline()) return Result.retry()

        return try {
            if (syncRepo.isConfigured()) {
                syncRepo.syncReports()
                syncRepo.syncLocations()
                syncRepo.syncRelayPackets()
                syncRepo.pullReportsFromCloud()
                syncRepo.pullTrailsFromCloud()
            }

            rewardsRepo.syncCurrentUserRegistration()
            rewardsRepo.claimRewardsForPendingReports()
            rewardsRepo.openPendingRelayJobs()
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
