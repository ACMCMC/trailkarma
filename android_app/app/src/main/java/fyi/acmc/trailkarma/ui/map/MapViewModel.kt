package fyi.acmc.trailkarma.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.api.WalletStateResponse
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.repository.RewardsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val rewardsRepository = RewardsRepository(app, db)

    val reports: Flow<List<TrailReport>> = db.trailReportDao().getAll()
    val userLocation: Flow<LocationUpdate?> = db.locationUpdateDao().getLatest()
    val selectedReport = MutableStateFlow<TrailReport?>(null)
    val walletState = MutableStateFlow<WalletStateResponse?>(null)

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

    init {
        refreshWalletState()
    }

    fun refreshWalletState() {
        viewModelScope.launch {
            walletState.value = rewardsRepository.fetchWalletState() ?: rewardsRepository.syncCurrentUserRegistration()
        }
    }
}
