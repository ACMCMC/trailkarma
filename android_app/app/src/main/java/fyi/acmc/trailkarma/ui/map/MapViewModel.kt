package fyi.acmc.trailkarma.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

data class MapMarker(
    val id: String,
    val geoPoint: GeoPoint,
    val type: String, // "user", "hazard", "water", "species"
    val title: String,
    val description: String = ""
)

class MapViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val reports: Flow<List<TrailReport>> = db.trailReportDao().getAll()
    val userLocation: Flow<LocationUpdate?> = db.locationUpdateDao().getLatest()
    val trails: Flow<List<fyi.acmc.trailkarma.models.Trail>> = db.trailDao().getAll()
    val selectedReport = MutableStateFlow<TrailReport?>(null)

    init {
        startPolling()
        listenForNetworkChanges()
    }

    private fun startPolling() {
        viewModelScope.launch {
            val syncRepo = fyi.acmc.trailkarma.repository.DatabricksSyncRepository(getApplication(), db)
            while (true) {
                if (syncRepo.isOnline()) {
                    try {
                        syncRepo.syncReports()
                        syncRepo.pullReportsFromCloud()
                        syncRepo.pullTrailsFromCloud()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // Poll every 15 seconds for near real-time updates
                kotlinx.coroutines.delay(15_000)
            }
        }
    }

    private fun listenForNetworkChanges() {
        viewModelScope.launch {
            val networkUtil = fyi.acmc.trailkarma.network.NetworkUtil(getApplication())
            networkUtil.networkChanged.collect { changed ->
                if (changed && networkUtil.isOnlineNow()) {
                    android.util.Log.d("MapViewModel", "Network became available, triggering sync")
                    val syncRepo = fyi.acmc.trailkarma.repository.DatabricksSyncRepository(getApplication(), db)
                    try {
                        syncRepo.syncReports()
                        syncRepo.pullReportsFromCloud()
                        syncRepo.pullTrailsFromCloud()
                    } catch (e: Exception) {
                        android.util.Log.e("MapViewModel", "Auto-sync failed", e)
                    }
                    networkUtil.clearNetworkChangeFlag()
                }
            }
        }
    }

    // Combined markers for the map
    val mapMarkers: Flow<List<MapMarker>> = combine(
        reports,
        userLocation
    ) { reports, location ->
        val markers = mutableListOf<MapMarker>()

        // User location marker
        if (location != null) {
            markers.add(
                MapMarker(
                    id = "user",
                    geoPoint = GeoPoint(location.lat, location.lng),
                    type = "user",
                    title = "You",
                    description = "Current position"
                )
            )
        }

        // Report markers
        reports.forEach { report ->
            markers.add(
                MapMarker(
                    id = report.reportId,
                    geoPoint = GeoPoint(report.lat, report.lng),
                    type = when (report.type.name) {
                        "hazard" -> "hazard"
                        "water" -> "water"
                        "species" -> "species"
                        else -> "hazard"
                    },
                    title = report.title,
                    description = report.description
                )
            )
        }

        markers
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectReport(report: TrailReport?) {
        selectedReport.value = report
    }
}
