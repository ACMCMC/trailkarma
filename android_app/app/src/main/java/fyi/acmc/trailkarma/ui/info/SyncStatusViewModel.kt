package fyi.acmc.trailkarma.ui.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.Trail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

data class SyncStatusData(
    val reports: List<TrailReport> = emptyList(),
    val trails: List<Trail> = emptyList(),
    val locations: List<LocationUpdate> = emptyList(),
    val unsyncedReportsCount: Int = 0,
    val totalReportsCount: Int = 0,
    val totalTrailsCount: Int = 0,
    val totalLocationsCount: Int = 0
)

class SyncStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val syncStatus: Flow<SyncStatusData> = combine(
        db.trailReportDao().getAll(),
        db.trailDao().getAll(),
        db.locationUpdateDao().getAll()
    ) { reports, trails, locations ->
        val unsyncedCount = reports.count { !it.synced }
        SyncStatusData(
            reports = reports,
            trails = trails,
            locations = locations,
            unsyncedReportsCount = unsyncedCount,
            totalReportsCount = reports.size,
            totalTrailsCount = trails.size,
            totalLocationsCount = locations.size
        )
    }

    fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    fun formatDate(millis: Long): String {
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
