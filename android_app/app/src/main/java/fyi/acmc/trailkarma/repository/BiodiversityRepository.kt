package fyi.acmc.trailkarma.repository

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import fyi.acmc.trailkarma.db.BiodiversityContributionDao
import fyi.acmc.trailkarma.db.KarmaEventDao
import fyi.acmc.trailkarma.db.RelayPacketDao
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.models.KarmaEvent
import fyi.acmc.trailkarma.models.KarmaStatus
import fyi.acmc.trailkarma.models.PhotoSyncState
import fyi.acmc.trailkarma.models.RelayPacket
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

data class RelayableBiodiversityPayload(
    val observation_id: String,
    val lat: Double,
    val lon: Double,
    val timestamp: String,
    val finalLabel: String,
    val taxonomicLevel: String,
    val confidenceBand: String,
    val verificationStatus: String
)

class BiodiversityRepository(
    private val contributionDao: BiodiversityContributionDao,
    private val relayPacketDao: RelayPacketDao,
    private val karmaEventDao: KarmaEventDao
) {
    private val moshi = Moshi.Builder().build()
    private val relayAdapter: JsonAdapter<RelayableBiodiversityPayload> =
        moshi.adapter(RelayableBiodiversityPayload::class.java)
    private val topKAdapter: JsonAdapter<List<Map<String, Any?>>> =
        moshi.adapter(
            Types.newParameterizedType(
                List::class.java,
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )
        )

    val savedContributions: Flow<List<BiodiversityContribution>> = contributionDao.getSaved()

    suspend fun insert(contribution: BiodiversityContribution) = contributionDao.insert(contribution)

    fun observeByObservationId(observationId: String): Flow<BiodiversityContribution?> =
        contributionDao.observeByObservationId(observationId)

    suspend fun getByObservationId(observationId: String): BiodiversityContribution? =
        contributionDao.getByObservationId(observationId)

    suspend fun getPendingLocalInference(): List<BiodiversityContribution> =
        contributionDao.getPendingLocalInference()

    suspend fun getPendingCloudSync(): List<BiodiversityContribution> =
        contributionDao.getPendingCloudSync()

    suspend fun getPendingPhotoUploads(): List<BiodiversityContribution> =
        contributionDao.getPendingPhotoUploads()

    suspend fun markLocalInferenceRunning(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(inferenceState = InferenceState.CLASSIFYING_LOCAL))
    }

    suspend fun markLocalInferenceFailed(observationId: String, errorMessage: String? = null) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(
            item.copy(
                inferenceState = InferenceState.FAILED_LOCAL,
                explanation = errorMessage ?: item.explanation
            )
        )
    }

    suspend fun markCloudSyncQueued(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(cloudSyncState = CloudSyncState.SYNC_QUEUED))
    }

    suspend fun markCloudSyncRunning(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(cloudSyncState = CloudSyncState.SYNCING))
    }

    suspend fun markCloudSyncFailed(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(cloudSyncState = CloudSyncState.SYNC_FAILED))
    }

    suspend fun markCloudSynced(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(cloudSyncState = CloudSyncState.SYNCED, synced = true))
    }

    suspend fun attachLocalPhoto(observationId: String, photoPath: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(photoUri = photoPath, photoSyncState = PhotoSyncState.LOCAL_ONLY))
    }

    suspend fun markPhotoUploading(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(photoSyncState = PhotoSyncState.UPLOADING))
    }

    suspend fun markPhotoSynced(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(photoSyncState = PhotoSyncState.SYNCED))
    }

    suspend fun markPhotoFailed(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(photoSyncState = PhotoSyncState.FAILED))
    }

    suspend fun applyClassification(
        observationId: String,
        topK: List<Map<String, Any?>>,
        finalLabel: String,
        finalTaxonomicLevel: String,
        confidence: Float,
        confidenceBand: String,
        explanation: String,
        safeForRewarding: Boolean,
        modelMetadataJson: String?,
        classificationSource: String,
        localModelVersion: String?
    ) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(
            item.copy(
                topKJson = topKAdapter.toJson(topK),
                finalLabel = finalLabel,
                finalTaxonomicLevel = finalTaxonomicLevel,
                confidence = confidence,
                confidenceBand = confidenceBand,
                explanation = explanation,
                relayable = true,
                safeForRewarding = safeForRewarding,
                karmaStatus = if (safeForRewarding) KarmaStatus.pending else KarmaStatus.none,
                inferenceState = InferenceState.CLASSIFIED_LOCAL,
                cloudSyncState = CloudSyncState.SYNC_QUEUED,
                modelMetadataJson = modelMetadataJson,
                classificationSource = classificationSource,
                localModelVersion = localModelVersion
            )
        )
    }

    suspend fun saveContribution(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(item.copy(savedLocally = true))

        if (item.safeForRewarding && karmaEventDao.findByObservationId(observationId) == null) {
            karmaEventDao.insert(
                KarmaEvent(
                    id = UUID.randomUUID().toString(),
                    observationId = observationId,
                    createdAt = Instant.now().toString()
                )
            )
        }

        if (item.relayable && item.finalLabel != null && item.finalTaxonomicLevel != null && item.confidenceBand != null) {
            val packetId = "bio-${item.observationId}"
            if (relayPacketDao.exists(packetId) == 0) {
                relayPacketDao.insert(
                    RelayPacket(
                        packetId = packetId,
                        payloadJson = relayAdapter.toJson(
                            RelayableBiodiversityPayload(
                                observation_id = item.observationId,
                                lat = item.lat,
                                lon = item.lon,
                                timestamp = item.createdAt,
                                finalLabel = item.finalLabel,
                                taxonomicLevel = item.finalTaxonomicLevel,
                                confidenceBand = item.confidenceBand,
                                verificationStatus = item.verificationStatus
                            )
                        ),
                        receivedAt = Instant.now().toString(),
                        senderDevice = "local-device"
                    )
                )
            }
        }
    }
}
