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
    @TypeConverter fun inferenceStateToString(v: InferenceState) = v.name
    @TypeConverter fun stringToInferenceState(v: String) = InferenceState.valueOf(v)
    @TypeConverter fun cloudSyncStateToString(v: CloudSyncState) = v.name
    @TypeConverter fun stringToCloudSyncState(v: String) = CloudSyncState.valueOf(v)
    @TypeConverter fun photoSyncStateToString(v: PhotoSyncState) = v.name
    @TypeConverter fun stringToPhotoSyncState(v: String) = PhotoSyncState.valueOf(v)
    @TypeConverter fun karmaStatusToString(v: KarmaStatus) = v.name
    @TypeConverter fun stringToKarmaStatus(v: String) = KarmaStatus.valueOf(v)
}

class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = Instant.now().toString()
        val columns = "(reportId, userId, type, title, description, lat, lng, timestamp, speciesName, confidence, source, synced, verificationStatus, rewardClaimed, highConfidenceBonus)"
        db.execSQL("""INSERT INTO trail_reports $columns VALUES ('mock-1', 'seed', 'hazard', 'Rockslide ahead', 'Section near mile 24 has debris', 32.88, -117.24, '$now', NULL, NULL, 'self', 0, 'pending', 0, 0)""")
        db.execSQL("""INSERT INTO trail_reports $columns VALUES ('mock-2', 'seed', 'hazard', 'Rattlesnake spotted', 'Stay alert, seen near water source', 32.87, -117.25, '$now', NULL, NULL, 'relayed', 0, 'pending', 0, 0)""")
        db.execSQL("""INSERT INTO trail_reports $columns VALUES ('mock-3', 'seed', 'water', 'Water source confirmed', 'Spring flowing, fresh water tested', 32.89, -117.23, '$now', NULL, NULL, 'self', 0, 'pending', 0, 0)""")
    }
}

@Database(
    entities = [
        User::class,
        TrustedContact::class,
        TrailReport::class,
        BiodiversityContribution::class,
        KarmaEvent::class,
        LocationUpdate::class,
        RelayPacket::class,
        RelayJobIntent::class,
        RelayInboxMessage::class,
        Trail::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun trailReportDao(): TrailReportDao
    abstract fun biodiversityContributionDao(): BiodiversityContributionDao
    abstract fun karmaEventDao(): KarmaEventDao
    abstract fun locationUpdateDao(): LocationUpdateDao
    abstract fun relayPacketDao(): RelayPacketDao
    abstract fun relayJobIntentDao(): RelayJobIntentDao
    abstract fun relayInboxMessageDao(): RelayInboxMessageDao
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
