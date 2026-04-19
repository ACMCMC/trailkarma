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

    @Query("UPDATE users SET solanaRegistered = :registered, lastWalletSyncAt = :timestamp, walletPublicKey = :walletPublicKey WHERE userId = :userId")
    suspend fun updateWalletRegistration(userId: String, walletPublicKey: String, registered: Boolean, timestamp: String)
}

@Dao
interface TrailReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: TrailReport)

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
}

@Dao
interface RelayJobIntentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: RelayJobIntent)

    @Query("SELECT * FROM relay_job_intents ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RelayJobIntent>>

    @Query("SELECT * FROM relay_job_intents WHERE source = 'self' AND status = 'pending'")
    suspend fun getPendingToOpen(): List<RelayJobIntent>

    @Query("UPDATE relay_job_intents SET status = :status, openedTxSignature = :txSignature, synced = 1 WHERE jobId = :jobId")
    suspend fun markOpened(jobId: String, status: String, txSignature: String)

    @Query("UPDATE relay_job_intents SET status = 'fulfilled', fulfilledTxSignature = :txSignature, proofRef = :proofRef, synced = 1 WHERE jobId = :jobId")
    suspend fun markFulfilled(jobId: String, proofRef: String, txSignature: String)
}

@Dao
interface TrailDao {
    @Query("SELECT * FROM trails ORDER BY name ASC")
    fun getAll(): Flow<List<Trail>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trails: List<Trail>)
}
