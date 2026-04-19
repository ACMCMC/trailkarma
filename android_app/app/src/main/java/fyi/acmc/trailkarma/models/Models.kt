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
    val highConfidenceBonus: Boolean = false
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
