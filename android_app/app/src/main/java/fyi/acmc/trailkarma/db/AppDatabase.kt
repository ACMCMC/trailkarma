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

@Database(
    entities = [User::class, TrailReport::class, LocationUpdate::class, RelayPacket::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun trailReportDao(): TrailReportDao
    abstract fun locationUpdateDao(): LocationUpdateDao
    abstract fun relayPacketDao(): RelayPacketDao

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
