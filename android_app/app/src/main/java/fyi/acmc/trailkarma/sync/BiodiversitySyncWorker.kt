package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fyi.acmc.trailkarma.api.BiodiversityApiClient
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.repository.BiodiversityRepository
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
        val api = BiodiversityApiClient.create()

        try {
            repo.getPendingCloudSync().forEach { item ->
                uploadAudio(item, repo, api)
            }

            repo.getPendingPhotoUploads().forEach { item ->
                uploadPhoto(item, repo, api)
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private suspend fun uploadAudio(
        item: BiodiversityContribution,
        repo: BiodiversityRepository,
        api: fyi.acmc.trailkarma.api.BiodiversityApi
    ) {
        val audioFile = File(item.audioUri)
        if (!audioFile.exists()) {
            repo.markCloudSyncFailed(item.observationId)
            return
        }

        val finalLabel = item.finalLabel ?: return
        val finalTaxonomicLevel = item.finalTaxonomicLevel ?: return
        val confidence = item.confidence ?: return
        val confidenceBand = item.confidenceBand ?: return
        val explanation = item.explanation ?: return
        val topKJson = item.topKJson ?: "[]"

        repo.markCloudSyncRunning(item.observationId)
        try {
            val response = api.syncAudioObservation(
                audio = MultipartBody.Part.createFormData(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                ),
                lat = item.lat.toString().toRequestBody("text/plain".toMediaType()),
                lon = item.lon.toString().toRequestBody("text/plain".toMediaType()),
                timestamp = item.createdAt.toRequestBody("text/plain".toMediaType()),
                observationId = item.observationId.toRequestBody("text/plain".toMediaType()),
                finalLabel = finalLabel.toRequestBody("text/plain".toMediaType()),
                finalTaxonomicLevel = finalTaxonomicLevel.toRequestBody("text/plain".toMediaType()),
                confidence = confidence.toString().toRequestBody("text/plain".toMediaType()),
                confidenceBand = confidenceBand.toRequestBody("text/plain".toMediaType()),
                explanation = explanation.toRequestBody("text/plain".toMediaType()),
                safeForRewarding = item.safeForRewarding.toString().toRequestBody("text/plain".toMediaType()),
                topKJson = topKJson.toRequestBody("application/json".toMediaType()),
                modelMetadataJson = (item.modelMetadataJson ?: "{}").toRequestBody("application/json".toMediaType()),
                classificationSource = (item.classificationSource ?: "local_android").toRequestBody("text/plain".toMediaType()),
                localModelVersion = (item.localModelVersion ?: "unknown").toRequestBody("text/plain".toMediaType())
            )
            if (response.success) {
                repo.markCloudSynced(item.observationId)
            } else {
                repo.markCloudSyncFailed(item.observationId)
            }
        } catch (_: Exception) {
            repo.markCloudSyncFailed(item.observationId)
        }
    }

    private suspend fun uploadPhoto(
        item: BiodiversityContribution,
        repo: BiodiversityRepository,
        api: fyi.acmc.trailkarma.api.BiodiversityApi
    ) {
        val photoPath = item.photoUri ?: return
        val photoFile = File(photoPath)
        if (!photoFile.exists()) {
            repo.markPhotoFailed(item.observationId)
            return
        }

        repo.markPhotoUploading(item.observationId)
        try {
            val response = api.linkPhoto(
                observationId = item.observationId.toRequestBody("text/plain".toMediaType()),
                photo = MultipartBody.Part.createFormData(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaType())
                )
            )
            if (response.success) {
                repo.markPhotoSynced(item.observationId)
            } else {
                repo.markPhotoFailed(item.observationId)
            }
        } catch (_: Exception) {
            repo.markPhotoFailed(item.observationId)
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
