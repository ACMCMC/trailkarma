package fyi.acmc.trailkarma.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val displayName: String,
    val realName: String? = null,
    val phoneNumber: String = "",
    val defaultRelayPhoneNumber: String = "",
    val shareLocationByDefault: Boolean = true,
    val shareRealNameByDefault: Boolean = false,
    val shareCallbackNumberByDefault: Boolean = true,
    val walletPublicKey: String = "",
    val solanaRegistered: Boolean = false,
    val lastWalletSyncAt: String? = null
)

@Entity(tableName = "trusted_contacts")
data class TrustedContact(
    @PrimaryKey val contactId: String = UUID.randomUUID().toString(),
    val userId: String,
    val displayName: String,
    val phoneNumber: String,
    val relationshipLabel: String? = null,
    val isDefault: Boolean = false,
    val createdAt: String
)

@Entity(tableName = "location_updates")
data class LocationUpdate(
    // UUID so any device can dedup this record — same ping seen by two phones = same row
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val timestamp: String,
    val lat: Double,
    val lng: Double,
    val h3Cell: String? = null,   // H3 res-9 cell, e.g. "89283082837ffff"
    val synced: Boolean = false
)

enum class ReportType { hazard, water, species }
enum class ReportSource { self, relayed }
enum class InferenceState { PENDING_LOCAL, CLASSIFYING_LOCAL, CLASSIFIED_LOCAL, FAILED_LOCAL }
enum class CloudSyncState { NOT_SYNCED, SYNC_QUEUED, SYNCING, SYNCED, SYNC_FAILED }
enum class PhotoSyncState { NONE, LOCAL_ONLY, UPLOADING, SYNCED, FAILED }
enum class KarmaStatus { none, pending, awarded }

@Entity(tableName = "trail_reports")
data class TrailReport(
    @PrimaryKey val reportId: String,
    val userId: String,
    val type: ReportType,
    val title: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val h3Cell: String? = null,   // H3 res-9 cell, e.g. "89283082837ffff"
    val timestamp: String,
    val speciesName: String? = null,
    val confidence: Float? = null,
    val source: ReportSource = ReportSource.self,
    val synced: Boolean = false,
    val verificationStatus: String = "pending",
    val verificationTier: String? = null,
    val rewardClaimed: Boolean = false,
    val rewardTxSignature: String? = null,
    val metadataHash: String? = null,
    val photoUri: String? = null,
    val audioUri: String? = null,
    val highConfidenceBonus: Boolean = false,
    val lastUpdatedAt: String? = null  // tracks when this record was last changed (locally or from cloud)
)

@Entity(tableName = "biodiversity_contributions")
data class BiodiversityContribution(
    @PrimaryKey val id: String,
    val type: String = "biodiversity_audio_detection",
    val observationId: String,
    val userId: String,
    val observerDisplayName: String? = null,
    val observerWalletPublicKey: String? = null,
    val createdAt: String,
    val claimedLabel: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val locationSource: String = "missing",
    val audioUri: String? = null,
    val photoUri: String? = null,
    val topKJson: String? = null,
    val finalLabel: String? = null,
    val finalTaxonomicLevel: String? = null,
    val confidence: Float? = null,
    val confidenceBand: String? = null,
    val explanation: String? = null,
    val verificationStatus: String = "provisional",
    val relayable: Boolean = false,
    val contributionType: String = "biodiversity",
    val karmaStatus: KarmaStatus = KarmaStatus.none,
    val inferenceState: InferenceState = InferenceState.PENDING_LOCAL,
    val cloudSyncState: CloudSyncState = CloudSyncState.NOT_SYNCED,
    val photoSyncState: PhotoSyncState = PhotoSyncState.NONE,
    val safeForRewarding: Boolean = false,
    val savedLocally: Boolean = false,
    val synced: Boolean = false,
    val modelMetadataJson: String? = null,
    val classificationSource: String? = null,
    val localModelVersion: String? = null,
    val verificationTxSignature: String? = null,
    val verifiedAt: String? = null,
    val collectibleStatus: String = "none",
    val collectibleId: String? = null,
    val collectibleName: String? = null,
    val collectibleImageUri: String? = null,
    val rewardPointsAwarded: Int = 0,
    val dataShareStatus: String = "local_only",
    val sharedWithOrgAt: String? = null
)

@Entity(tableName = "karma_events")
data class KarmaEvent(
    @PrimaryKey val id: String,
    val observationId: String,
    val userId: String,
    val createdAt: String,
    val status: String = "pending",
    val reason: String = "biodiversity_reward_pending",
    val walletPublicKey: String? = null,
    val collectibleStatus: String = "pending_verification",
    val collectibleId: String? = null,
    val verificationTxSignature: String? = null,
    val verifiedAt: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "relay_packets")
data class RelayPacket(
    @PrimaryKey val packetId: String,
    val payloadJson: String,
    val receivedAt: String,
    val senderDevice: String,
    val hopCount: Int = 0,        // loop guard: stop re-advertising after N hops
    val uploaded: Boolean = false
)

@Entity(tableName = "relay_job_intents")
data class RelayJobIntent(
    @PrimaryKey val jobId: String,
    val userId: String,
    val senderWallet: String,
    val relayType: String = "voice_outbound",
    val recipientName: String = "",
    val recipientPhoneNumber: String = "",
    val destinationHash: String,
    val payloadHash: String,
    val messageBody: String = "",
    val contextSummary: String = "",
    val contextJson: String = "{}",
    val expiryTs: Long,
    val rewardAmount: Int,
    val nonce: Long,
    val encryptedBlob: String? = null, // ECIES-encrypted payload for backend
    val signedMessageBase64: String,
    val signatureBase64: String,
    val source: String = "self",
    val status: String = "pending",
    val proofRef: String? = null,
    val openedTxSignature: String? = null,
    val fulfilledTxSignature: String? = null,
    val callSid: String? = null,
    val conversationId: String? = null,
    val transcriptSummary: String? = null,
    val replyJobId: String? = null,
    val createdAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "relay_inbox_messages")
data class RelayInboxMessage(
    @PrimaryKey val replyId: String,
    val originalJobId: String,
    val userId: String,
    val senderLabel: String,
    val senderPhoneNumber: String,
    val messageSummary: String,
    val messageBody: String,
    val contextJson: String = "{}",
    val createdAt: String,
    val status: String = "pending",
    val acknowledged: Boolean = false
)

@Entity(tableName = "trails")
data class Trail(
    @PrimaryKey val trailId: String,
    val name: String,
    val description: String?,
    val totalLengthMiles: Double?,
    val region: String?,
    val geometryJson: String?
)
