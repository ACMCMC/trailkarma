package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.repository.RewardsRepository

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val syncRepo = DatabricksSyncRepository(applicationContext, db)
        val rewardsRepo = RewardsRepository(applicationContext, db)

        if (!syncRepo.isOnline()) return Result.retry()

        if (syncRepo.isConfigured()) {
            runCatching {
                syncRepo.syncReports()
                syncRepo.syncLocations()
                syncRepo.pullReportsFromCloud()
            }.getOrElse {
                return Result.retry()
            }
        }

        runCatching {
            rewardsRepo.syncCurrentUserRegistration()
            rewardsRepo.claimRewardsForPendingReports()
            rewardsRepo.openPendingRelayJobs()
        }.getOrElse {
            return Result.retry()
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
