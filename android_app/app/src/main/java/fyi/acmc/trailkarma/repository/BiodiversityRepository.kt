package fyi.acmc.trailkarma.repository

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
import kotlin.math.absoluteValue

data class RelayableBiodiversityPayload(
    val observation_id: String,
    val lat: Double,
    val lon: Double,
    val location_accuracy_meters: Float? = null,
    val location_source: String? = null,
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
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
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
        contributionDao.update(
            item.copy(
                cloudSyncState = CloudSyncState.SYNCED,
                synced = true,
                dataShareStatus = if (item.lat == null || item.lon == null) {
                    "mirrored_cloud_missing_location"
                } else {
                    "mirrored_cloud"
                }
            )
        )
        autoVerifyCollectibleCandidate(observationId)
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
                relayable = item.lat != null && item.lon != null,
                safeForRewarding = safeForRewarding,
                karmaStatus = if (safeForRewarding) KarmaStatus.pending else KarmaStatus.none,
                inferenceState = InferenceState.CLASSIFIED_LOCAL,
                cloudSyncState = CloudSyncState.SYNC_QUEUED,
                modelMetadataJson = modelMetadataJson,
                classificationSource = classificationSource,
                localModelVersion = localModelVersion,
                collectibleStatus = if (safeForRewarding) "pending_verification" else "not_eligible",
                dataShareStatus = when {
                    item.lat == null || item.lon == null -> "location_missing"
                    else -> "classification_ready"
                }
            )
        )
    }

    suspend fun saveContribution(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(
            item.copy(
                savedLocally = true,
                dataShareStatus = if (item.cloudSyncState == CloudSyncState.SYNCED) {
                    "mirrored_cloud"
                } else if (item.lat == null || item.lon == null) {
                    "location_missing"
                } else {
                    "ready_local"
                }
            )
        )

        if (item.safeForRewarding && karmaEventDao.findByObservationId(observationId) == null) {
            karmaEventDao.insert(
                KarmaEvent(
                    id = UUID.randomUUID().toString(),
                    observationId = observationId,
                    userId = item.userId,
                    createdAt = Instant.now().toString(),
                    walletPublicKey = item.observerWalletPublicKey,
                    collectibleStatus = item.collectibleStatus
                )
            )
        }

        if (item.relayable && item.lat != null && item.lon != null && item.finalLabel != null && item.finalTaxonomicLevel != null && item.confidenceBand != null) {
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
                                location_accuracy_meters = item.locationAccuracyMeters,
                                location_source = item.locationSource,
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

    suspend fun markCollectibleVerified(
        observationId: String,
        collectibleId: String,
        collectibleName: String,
        verificationTxSignature: String,
        collectibleImageUri: String? = null,
        verifiedAt: String = Instant.now().toString()
    ) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        contributionDao.update(
            item.copy(
                verificationStatus = "verified",
                karmaStatus = KarmaStatus.awarded,
                verificationTxSignature = verificationTxSignature,
                verifiedAt = verifiedAt,
                collectibleStatus = "verified",
                collectibleId = collectibleId,
                collectibleName = collectibleName,
                collectibleImageUri = collectibleImageUri
            )
        )

        karmaEventDao.findByObservationId(observationId)?.let { event ->
            karmaEventDao.insert(
                event.copy(
                    status = "verified",
                    collectibleStatus = "verified",
                    collectibleId = collectibleId,
                    verificationTxSignature = verificationTxSignature,
                    verifiedAt = verifiedAt,
                    synced = false
                )
            )
        }
    }

    suspend fun autoVerifyCollectibleCandidate(observationId: String) {
        val item = contributionDao.getByObservationId(observationId) ?: return
        if (!item.safeForRewarding) return

        val verifiedAt = item.verifiedAt ?: Instant.now().toString()
        val verificationTxSignature = item.verificationTxSignature
            ?: "demo-verify-${observationId.take(8)}"

        if (item.finalTaxonomicLevel == "species" && !item.finalLabel.isNullOrBlank()) {
            val normalizedLabel = item.finalLabel.trim()
            val existing = contributionDao.findVerifiedSpeciesCollectibleByLabel(normalizedLabel, observationId)
            if (existing == null) {
                markCollectibleVerified(
                    observationId = observationId,
                    collectibleId = item.collectibleId ?: "species:${slugify(normalizedLabel)}",
                    collectibleName = item.collectibleName ?: normalizedLabel,
                    verificationTxSignature = verificationTxSignature,
                    collectibleImageUri = item.collectibleImageUri ?: buildCollectibleGradient(normalizedLabel),
                    verifiedAt = verifiedAt
                )
            } else {
                contributionDao.update(
                    item.copy(
                        verificationStatus = "verified",
                        karmaStatus = KarmaStatus.awarded,
                        verificationTxSignature = verificationTxSignature,
                        verifiedAt = verifiedAt,
                        collectibleStatus = "duplicate_species",
                        collectibleId = existing.collectibleId,
                        collectibleName = existing.collectibleName ?: normalizedLabel,
                        collectibleImageUri = existing.collectibleImageUri
                    )
                )
                karmaEventDao.findByObservationId(observationId)?.let { event ->
                    karmaEventDao.insert(
                        event.copy(
                            status = "verified",
                            collectibleStatus = "duplicate_species",
                            collectibleId = existing.collectibleId,
                            verificationTxSignature = verificationTxSignature,
                            verifiedAt = verifiedAt,
                            synced = false
                        )
                    )
                }
            }
            return
        }

        contributionDao.update(
            item.copy(
                verificationStatus = "verified",
                karmaStatus = KarmaStatus.awarded,
                verificationTxSignature = verificationTxSignature,
                verifiedAt = verifiedAt,
                collectibleStatus = if (item.collectibleStatus == "not_eligible") "not_eligible" else "verified_no_collectible"
            )
        )
        karmaEventDao.findByObservationId(observationId)?.let { event ->
            karmaEventDao.insert(
                event.copy(
                    status = "verified",
                    collectibleStatus = "verified_no_collectible",
                    verificationTxSignature = verificationTxSignature,
                    verifiedAt = verifiedAt,
                    synced = false
                )
            )
        }
    }

    private fun slugify(value: String): String =
        value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { UUID.randomUUID().toString() }

    private fun buildCollectibleGradient(label: String): String {
        val hash = label.hashCode().absoluteValue
        val hue = (hash % 360).toFloat()
        val accent = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.52f, 0.88f))
        val accentSoft = android.graphics.Color.HSVToColor(floatArrayOf((hue + 26f) % 360f, 0.38f, 0.96f))
        return "gradient:${String.format("#%06X", 0xFFFFFF and accent)}:${String.format("#%06X", 0xFFFFFF and accentSoft)}"
    }
}
