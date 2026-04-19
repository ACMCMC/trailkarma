package fyi.acmc.trailkarma.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.inference.LocalBiodiversityInferenceEngine
import fyi.acmc.trailkarma.repository.BiodiversityRepository
import java.io.File

class BiodiversityLocalInferenceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val repo = BiodiversityRepository(
            db.biodiversityContributionDao(),
            db.relayPacketDao(),
            db.karmaEventDao()
        )
        val engine = LocalBiodiversityInferenceEngine(applicationContext)
        val moshi = Moshi.Builder().build()
        val metadataAdapter = moshi.adapter<Map<String, Any?>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

        val explicitObservationId = inputData.getString(KEY_OBSERVATION_ID)
        val items = if (explicitObservationId != null) {
            listOfNotNull(repo.getByObservationId(explicitObservationId))
        } else {
            repo.getPendingLocalInference()
        }

        items.forEach { item ->
            val audioPath = item.audioUri
            if (audioPath.isNullOrBlank() || !File(audioPath).exists()) {
                repo.markLocalInferenceFailed(item.observationId, "Audio clip is missing from local storage.")
                return@forEach
            }

            repo.markLocalInferenceRunning(item.observationId)
            try {
                val result = engine.infer(
                    audioFile = File(audioPath),
                    observationId = item.observationId,
                    lat = item.lat,
                    lon = item.lon,
                    timestamp = item.createdAt
                )
                val topK = result.topCandidates.map { candidate ->
                    mapOf(
                        "label" to candidate.label,
                        "scientific_name" to candidate.scientificName,
                        "taxonomic_level" to candidate.taxonomicLevel,
                        "score" to candidate.score,
                        "genus" to candidate.genus,
                        "family" to candidate.family
                    )
                }
                repo.applyClassification(
                    observationId = item.observationId,
                    topK = topK,
                    finalLabel = result.decision.finalLabel,
                    finalTaxonomicLevel = result.decision.finalTaxonomicLevel,
                    confidence = result.rawConfidence,
                    confidenceBand = result.decision.confidenceBand,
                    explanation = result.decision.explanation,
                    safeForRewarding = result.decision.safeForRewarding,
                    modelMetadataJson = metadataAdapter.toJson(result.modelMetadata),
                    classificationSource = result.modelMetadata["provider"] as? String ?: "local_android",
                    localModelVersion = result.modelMetadata["model_version"] as? String
                )
                BiodiversitySyncWorker.schedule(applicationContext)
            } catch (t: Throwable) {
                repo.markLocalInferenceFailed(
                    item.observationId,
                    t.message ?: "Local biodiversity inference failed."
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_PREFIX = "biodiversity-local-inference-"
        private const val KEY_OBSERVATION_ID = "observation_id"

        fun schedule(context: Context, observationId: String) {
            val request = OneTimeWorkRequestBuilder<BiodiversityLocalInferenceWorker>()
                .setInputData(Data.Builder().putString(KEY_OBSERVATION_ID, observationId).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX$observationId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePending(context: Context) {
            val request = OneTimeWorkRequestBuilder<BiodiversityLocalInferenceWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${UNIQUE_PREFIX}pending",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
