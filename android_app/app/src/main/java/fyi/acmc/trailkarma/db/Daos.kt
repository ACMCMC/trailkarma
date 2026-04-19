package fyi.acmc.trailkarma.db

import androidx.room.*
import fyi.acmc.trailkarma.models.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirst(): User?

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): User?

    @Query("UPDATE users SET solanaRegistered = :registered, lastWalletSyncAt = :timestamp, walletPublicKey = :walletPublicKey WHERE userId = :userId")
    suspend fun updateWalletRegistration(userId: String, walletPublicKey: String, registered: Boolean, timestamp: String)

    @Query("UPDATE users SET displayName = :displayName WHERE userId = :userId")
    suspend fun updateDisplayName(userId: String, displayName: String)
}

@Dao
interface TrustedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: TrustedContact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<TrustedContact>)

    @Query("SELECT * FROM trusted_contacts WHERE userId = :userId ORDER BY isDefault DESC, displayName ASC")
    fun getForUser(userId: String): Flow<List<TrustedContact>>

    @Query("DELETE FROM trusted_contacts WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface TrailReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: TrailReport)

    @Query("SELECT COUNT(*) FROM trail_reports WHERE reportId = :reportId")
    suspend fun exists(reportId: String): Int

    @Query("SELECT * FROM trail_reports ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TrailReport>>

    @Query("SELECT * FROM trail_reports WHERE synced = 0")
    suspend fun getUnsynced(): List<TrailReport>

    @Query("SELECT * FROM trail_reports WHERE rewardClaimed = 0 AND verificationStatus != 'rejected' ORDER BY timestamp ASC")
    suspend fun getPendingRewardClaims(): List<TrailReport>

    @Query("UPDATE trail_reports SET synced = 1 WHERE reportId = :id")
    suspend fun markSynced(id: String)

    // Used by BLE set-diff: "what IDs do I already have?"
    @Query("SELECT reportId FROM trail_reports")
    suspend fun getIds(): List<String>

    // Used by GattServer: look up a single report to stream to a peer
    @Query("SELECT * FROM trail_reports WHERE reportId = :id LIMIT 1")
    suspend fun getById(id: String): TrailReport?

    @Query("UPDATE trail_reports SET rewardClaimed = 1, rewardTxSignature = :txSignature, verificationStatus = 'claimed', verificationTier = :verificationTier WHERE reportId = :id")
    suspend fun markRewardClaimed(id: String, txSignature: String, verificationTier: String)

    @Query("UPDATE trail_reports SET verificationStatus = 'rejected' WHERE reportId = :id")
    suspend fun markRewardRejected(id: String)
}

@Dao
interface BiodiversityContributionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contribution: BiodiversityContribution)

    @Update
    suspend fun update(contribution: BiodiversityContribution)

    @Query("SELECT * FROM biodiversity_contributions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BiodiversityContribution>>

    @Query("SELECT * FROM biodiversity_contributions WHERE savedLocally = 1 ORDER BY createdAt DESC")
    fun getSaved(): Flow<List<BiodiversityContribution>>

    @Query("SELECT * FROM biodiversity_contributions WHERE observationId = :observationId LIMIT 1")
    fun observeByObservationId(observationId: String): Flow<BiodiversityContribution?>

    @Query("SELECT * FROM biodiversity_contributions WHERE observationId = :observationId LIMIT 1")
    suspend fun getByObservationId(observationId: String): BiodiversityContribution?

    @Query("SELECT * FROM biodiversity_contributions WHERE inferenceState IN ('PENDING_LOCAL', 'FAILED_LOCAL') ORDER BY createdAt ASC")
    suspend fun getPendingLocalInference(): List<BiodiversityContribution>

    @Query("SELECT * FROM biodiversity_contributions WHERE cloudSyncState IN ('SYNC_QUEUED', 'SYNC_FAILED') AND finalLabel IS NOT NULL ORDER BY createdAt ASC")
    suspend fun getPendingCloudSync(): List<BiodiversityContribution>

    @Query("SELECT * FROM biodiversity_contributions WHERE photoUri IS NOT NULL AND photoSyncState IN ('LOCAL_ONLY', 'FAILED') AND finalLabel IS NOT NULL")
    suspend fun getPendingPhotoUploads(): List<BiodiversityContribution>

    @Query("""
        SELECT * FROM biodiversity_contributions
        WHERE finalTaxonomicLevel = 'species'
          AND verificationStatus = 'verified'
          AND collectibleStatus = 'verified'
        ORDER BY verifiedAt DESC, createdAt DESC
    """)
    suspend fun getVerifiedSpeciesCollectibles(): List<BiodiversityContribution>

    @Query("""
        SELECT * FROM biodiversity_contributions
        WHERE finalTaxonomicLevel = 'species'
          AND finalLabel = :label
          AND verificationStatus = 'verified'
          AND collectibleStatus = 'verified'
          AND observationId != :observationId
        ORDER BY verifiedAt DESC, createdAt DESC
        LIMIT 1
    """)
    suspend fun findVerifiedSpeciesCollectibleByLabel(label: String, observationId: String): BiodiversityContribution?
}

@Dao
interface LocationUpdateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // UUID PK — duplicate pings are silently dropped
    suspend fun insert(update: LocationUpdate)

    @Query("SELECT * FROM location_updates WHERE synced = 0")
    suspend fun getUnsynced(): List<LocationUpdate>

    @Query("UPDATE location_updates SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String) // String UUID now

    @Query("SELECT * FROM location_updates ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LocationUpdate>>

    @Query("SELECT * FROM location_updates ORDER BY rowid DESC LIMIT 1")
    fun getLatest(): Flow<LocationUpdate?>
}

@Dao
interface RelayPacketDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // packetId UUID — dupes silently dropped
    suspend fun insert(packet: RelayPacket)

    @Query("SELECT * FROM relay_packets WHERE uploaded = 0")
    suspend fun getPending(): List<RelayPacket>

    @Query("UPDATE relay_packets SET uploaded = 1 WHERE packetId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE relay_packets SET uploaded = 1 WHERE packetId = :id")
    suspend fun markUploaded(id: String)

    // Used by BLE set-diff
    @Query("SELECT packetId FROM relay_packets")
    suspend fun getIds(): List<String>

    @Query("SELECT COUNT(*) FROM relay_packets WHERE packetId = :id")
    suspend fun exists(id: String): Int

    @Query("SELECT * FROM relay_packets WHERE packetId = :id LIMIT 1")
    suspend fun getById(id: String): RelayPacket?
}

@Dao
interface RelayJobIntentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: RelayJobIntent)

    @Query("SELECT * FROM relay_job_intents ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RelayJobIntent>>

    @Query("SELECT * FROM relay_job_intents WHERE source = 'self' AND status = 'pending'")
    suspend fun getPendingToOpen(): List<RelayJobIntent>

    @Query("SELECT * FROM relay_job_intents WHERE relayType IN ('voice_outbound', 'voice_reply') AND status NOT IN ('fulfilled', 'failed') ORDER BY createdAt ASC")
    suspend fun getVoiceJobsToSync(): List<RelayJobIntent>

    @Query("UPDATE relay_job_intents SET status = :status, openedTxSignature = :txSignature, synced = 1 WHERE jobId = :jobId")
    suspend fun markOpened(jobId: String, status: String, txSignature: String)

    @Query("UPDATE relay_job_intents SET status = 'fulfilled', fulfilledTxSignature = :txSignature, proofRef = :proofRef, synced = 1 WHERE jobId = :jobId")
    suspend fun markFulfilled(jobId: String, proofRef: String, txSignature: String)

    @Query("UPDATE relay_job_intents SET status = :status, openedTxSignature = :openedTxSignature, fulfilledTxSignature = :fulfilledTxSignature, callSid = :callSid, conversationId = :conversationId, transcriptSummary = :transcriptSummary, replyJobId = :replyJobId, synced = 1 WHERE jobId = :jobId")
    suspend fun updateVoiceRelayStatus(
        jobId: String,
        status: String,
        openedTxSignature: String?,
        fulfilledTxSignature: String?,
        callSid: String?,
        conversationId: String?,
        transcriptSummary: String?,
        replyJobId: String?
    )
}

@Dao
interface RelayInboxMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: RelayInboxMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<RelayInboxMessage>)

    @Query("SELECT * FROM relay_inbox_messages WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<RelayInboxMessage>>

    @Query("SELECT * FROM relay_inbox_messages WHERE userId = :userId AND acknowledged = 0 ORDER BY createdAt ASC")
    suspend fun getPendingAcknowledgements(userId: String): List<RelayInboxMessage>

    @Query("UPDATE relay_inbox_messages SET acknowledged = 1, status = 'delivered' WHERE replyId = :replyId")
    suspend fun markAcknowledged(replyId: String)
}

@Dao
interface TrailDao {
    @Query("SELECT * FROM trails ORDER BY name ASC")
    fun getAll(): Flow<List<Trail>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trails: List<Trail>)

    @Query("SELECT COUNT(*) FROM trails WHERE trailId = :trailId")
    suspend fun exists(trailId: String): Int
}

@Dao
interface KarmaEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: KarmaEvent)

    @Query("SELECT * FROM karma_events WHERE observationId = :observationId LIMIT 1")
    suspend fun findByObservationId(observationId: String): KarmaEvent?
}
