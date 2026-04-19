package fyi.acmc.trailkarma.ui.biodiversity

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import fyi.acmc.trailkarma.audio.TrailAudioRecorder
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.repository.BiodiversityRepository
import fyi.acmc.trailkarma.sync.BiodiversityLocalInferenceWorker
import fyi.acmc.trailkarma.sync.BiodiversitySyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

data class BiodiversityCaptureUiState(
    val isRecording: Boolean = false,
    val latestObservationId: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BiodiversityCaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = BiodiversityRepository(
        db.biodiversityContributionDao(),
        db.relayPacketDao(),
        db.karmaEventDao()
    )
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)

    private val _uiState = MutableStateFlow(BiodiversityCaptureUiState())
    val uiState: StateFlow<BiodiversityCaptureUiState> = _uiState.asStateFlow()

    val currentContribution = _uiState.flatMapLatest { state ->
        val observationId = state.latestObservationId
        if (observationId == null) flowOf(null) else repo.observeByObservationId(observationId)
    }

    @SuppressLint("MissingPermission")
    fun recordTrailSound() {
        if (_uiState.value.isRecording) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = true, errorMessage = null)
            try {
                val observationId = UUID.randomUUID().toString()
                val timestamp = Instant.now().toString()
                val location = awaitLastLocation()
                val audioFile = withContext(Dispatchers.IO) {
                    File(getApplication<Application>().filesDir, "captures/audio/$observationId.wav").also {
                        TrailAudioRecorder.recordFiveSecondWav(it)
                    }
                }

                repo.insert(
                    BiodiversityContribution(
                        id = UUID.randomUUID().toString(),
                        observationId = observationId,
                        createdAt = timestamp,
                        lat = location?.latitude ?: 0.0,
                        lon = location?.longitude ?: 0.0,
                        audioUri = audioFile.absolutePath
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    latestObservationId = observationId
                )
                BiodiversityLocalInferenceWorker.schedule(getApplication(), observationId)
                BiodiversitySyncWorker.schedule(getApplication())
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    errorMessage = t.message ?: "Unable to record trail sound"
                )
            }
        }
    }

    fun createPhotoFile(observationId: String): File =
        File(getApplication<Application>().filesDir, "captures/photos/$observationId.jpg").apply {
            parentFile?.mkdirs()
        }

    fun attachPhoto(photoPath: String) {
        val observationId = _uiState.value.latestObservationId ?: return
        viewModelScope.launch {
            repo.attachLocalPhoto(observationId, photoPath)
            BiodiversitySyncWorker.schedule(getApplication())
        }
    }

    fun saveContribution() {
        val observationId = _uiState.value.latestObservationId ?: return
        viewModelScope.launch {
            repo.saveContribution(observationId)
        }
    }

    suspend fun awaitLastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        fusedLocation.lastLocation
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
