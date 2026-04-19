package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.repository.BiodiversityRepository
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class BiodiversitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val repo = BiodiversityRepository(
            db.biodiversityContributionDao(),
            db.relayPacketDao(),
            db.karmaEventDao()
        )
        val databricksRepo = DatabricksSyncRepository(applicationContext, db)

        if (!databricksRepo.isConfigured() || !databricksRepo.isOnline()) {
            return Result.success()
        }

        try {
            repo.getPendingCloudSync().forEach { item ->
                syncToDatabricks(item, repo, databricksRepo)
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private suspend fun syncToDatabricks(
        item: BiodiversityContribution,
        repo: BiodiversityRepository,
        databricksRepo: DatabricksSyncRepository
    ) {
        repo.markCloudSyncRunning(item.observationId)
        val success = databricksRepo.mirrorBiodiversityContribution(item)
        if (success) {
            repo.markCloudSynced(item.observationId)
        } else {
            repo.markCloudSyncFailed(item.observationId)
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<BiodiversitySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "biodiversity-sync",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

private fun String.toPlainTextPart() = toRequestBody("text/plain".toMediaType())
