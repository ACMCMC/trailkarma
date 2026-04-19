package fyi.acmc.trailkarma.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.api.WalletStateResponse
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.models.Trail
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
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
    private val syncRepo = DatabricksSyncRepository(app, db)
    private val rewardsRepository = RewardsRepository(app, db)

    val reports: Flow<List<TrailReport>> = db.trailReportDao().getAll()
    val userLocation: Flow<LocationUpdate?> = db.locationUpdateDao().getLatest()
    val trails: Flow<List<Trail>> = db.trailDao().getAll()
    val walletState = MutableStateFlow<WalletStateResponse?>(null)
    val isOnline = NetworkUtil(app).isOnline

    init {
        viewModelScope.launch {
            refreshCloudData()
            refreshWalletState()
        }
        listenForNetworkChanges()
    }

    private suspend fun refreshCloudData() {
        if (!syncRepo.isOnline() || !syncRepo.isConfigured()) return
        runCatching {
            syncRepo.syncReports()
            syncRepo.syncLocations()
            syncRepo.syncRelayPackets()
            syncRepo.pullReportsFromCloud()
            syncRepo.pullTrailsFromCloud()
        }
    }

    private fun listenForNetworkChanges() {
        viewModelScope.launch {
            val networkUtil = NetworkUtil(getApplication())
            networkUtil.networkChanged.collect { changed ->
                if (changed && networkUtil.isOnlineNow()) {
                    refreshCloudData()
                    refreshWalletState()
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

    fun refreshWalletState() {
        viewModelScope.launch {
            walletState.value = rewardsRepository.fetchWalletState() ?: rewardsRepository.syncCurrentUserRegistration()
        }
    }
}
