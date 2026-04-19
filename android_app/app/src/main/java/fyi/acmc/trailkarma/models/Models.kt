package fyi.acmc.trailkarma.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val displayName: String,
    val walletPublicKey: String = "",
    val solanaRegistered: Boolean = false,
    val lastWalletSyncAt: String? = null
)

@Entity(tableName = "location_updates")
data class LocationUpdate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val timestamp: String,
    val lat: Double,
    val lng: Double,
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
    val uploaded: Boolean = false
)

@Entity(tableName = "relay_job_intents")
data class RelayJobIntent(
    @PrimaryKey val jobId: String,
    val userId: String,
    val senderWallet: String,
    val destinationHash: String,
    val payloadHash: String,
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
    val createdAt: String,
    val synced: Boolean = false
)
