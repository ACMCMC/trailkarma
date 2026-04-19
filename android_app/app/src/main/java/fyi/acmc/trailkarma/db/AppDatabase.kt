package fyi.acmc.trailkarma.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import fyi.acmc.trailkarma.models.*
import java.time.Instant

class Converters {
    @TypeConverter fun reportTypeToString(v: ReportType) = v.name
    @TypeConverter fun stringToReportType(v: String) = ReportType.valueOf(v)
    @TypeConverter fun reportSourceToString(v: ReportSource) = v.name
    @TypeConverter fun stringToReportSource(v: String) = ReportSource.valueOf(v)
}

class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // No mock data - sync from cloud instead
    }
}

// v3 -> v4: LocationUpdate.id changed from autoGenerate Long -> UUID String
// No migration needed — fallbackToDestructiveMigration wipes and rebuilds.
// The cloud (Databricks) is the source of truth; local DB re-syncs on next launch.
@Database(
    entities = [User::class, TrailReport::class, LocationUpdate::class, RelayPacket::class, Trail::class],
    version = 6, // hopCount added to RelayPacket; h3Cell on TrailReport + LocationUpdate
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun trailReportDao(): TrailReportDao
    abstract fun locationUpdateDao(): LocationUpdateDao
    abstract fun relayPacketDao(): RelayPacketDao
    abstract fun trailDao(): TrailDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "trailkarma.db")
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
