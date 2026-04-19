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
}

@Dao
interface TrailReportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: TrailReport)

    @Query("SELECT * FROM trail_reports ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TrailReport>>

    @Query("SELECT * FROM trail_reports WHERE synced = 0")
    suspend fun getUnsynced(): List<TrailReport>

    @Query("UPDATE trail_reports SET synced = 1 WHERE reportId = :id")
    suspend fun markSynced(id: String)

    // Used by BLE set-diff: "what IDs do I already have?"
    @Query("SELECT reportId FROM trail_reports")
    suspend fun getIds(): List<String>
}

@Dao
interface LocationUpdateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) // UUID PK — duplicate pings are silently dropped
    suspend fun insert(update: LocationUpdate)

    @Query("SELECT * FROM location_updates WHERE synced = 0")
    suspend fun getUnsynced(): List<LocationUpdate>

    @Query("UPDATE location_updates SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String) // String UUID now

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
}

@Dao
interface TrailDao {
    @Query("SELECT * FROM trails ORDER BY name ASC")
    fun getAll(): Flow<List<Trail>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trails: List<Trail>)
}
