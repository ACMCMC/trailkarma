package fyi.acmc.trailkarma.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val displayName: String
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
    val synced: Boolean = false
)

@Entity(tableName = "relay_packets")
data class RelayPacket(
    @PrimaryKey val packetId: String,
    val payloadJson: String,
    val receivedAt: String,
    val senderDevice: String,
    val uploaded: Boolean = false
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
