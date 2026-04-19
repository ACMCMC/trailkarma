package fyi.acmc.trailkarma.ui.report

import android.app.Application
import android.annotation.SuppressLint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.repository.ReportRepository
import fyi.acmc.trailkarma.repository.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class CreateReportViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReportRepository(AppDatabase.get(app).trailReportDao())
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)

    @SuppressLint("MissingPermission")
    fun save(type: ReportType, title: String, description: String, speciesName: String?) {
        fusedLocation.lastLocation.addOnSuccessListener { loc ->
            viewModelScope.launch {
                val db = AppDatabase.get(getApplication())
                val userRepo = UserRepository(getApplication(), db.userDao())
                val userId = userRepo.currentUserId.first() ?: "unknown"

                repo.save(
                    TrailReport(
                        reportId = UUID.randomUUID().toString(),
                        userId = userId,
                        type = type,
                        title = title,
                        description = description,
                        lat = loc?.latitude ?: 0.0,
                        lng = loc?.longitude ?: 0.0,
                        timestamp = Instant.now().toString(),
                        speciesName = speciesName,
                        source = ReportSource.self
                    )
                )
            }
        }
    }
}
