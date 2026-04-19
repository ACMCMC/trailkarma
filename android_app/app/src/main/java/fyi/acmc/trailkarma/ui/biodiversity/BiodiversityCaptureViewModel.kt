package fyi.acmc.trailkarma.ui.biodiversity

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import fyi.acmc.trailkarma.audio.TrailAudioRecorder
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.BiodiversityRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.sync.BiodiversityLocalInferenceWorker
import fyi.acmc.trailkarma.sync.BiodiversitySyncWorker
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
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

data class BiodiversityLocationSnapshot(
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyMeters: Float? = null,
    val source: String = "missing"
)

data class BiodiversityCaptureUiState(
    val isRecording: Boolean = false,
    val latestObservationId: String? = null,
    val errorMessage: String? = null,
    val currentUser: User? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BiodiversityCaptureViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val userRepo = UserRepository(app, db.userDao())
    private val repo = BiodiversityRepository(
        db.biodiversityContributionDao(),
        db.relayPacketDao(),
        db.karmaEventDao()
    )
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)

    private val _uiState = MutableStateFlow(BiodiversityCaptureUiState())
    val uiState: StateFlow<BiodiversityCaptureUiState> = _uiState.asStateFlow()

    val savedContributions = repo.savedContributions

    val currentContribution = _uiState.flatMapLatest { state ->
        val observationId = state.latestObservationId
        if (observationId == null) flowOf(null) else repo.observeByObservationId(observationId)
    }

    init {
        viewModelScope.launch {
            val user = userRepo.ensureLocalUser()
            _uiState.value = _uiState.value.copy(currentUser = user)
        }
    }

    @SuppressLint("MissingPermission")
    fun recordTrailSound() {
        if (_uiState.value.isRecording) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = true, errorMessage = null)
            try {
                val user = _uiState.value.currentUser ?: userRepo.ensureLocalUser()
                val observationId = UUID.randomUUID().toString()
                val timestamp = Instant.now().toString()
                val location = awaitLocationSnapshot()
                val audioFile = withContext(Dispatchers.IO) {
                    File(getApplication<Application>().filesDir, "captures/audio/$observationId.wav").also {
                        TrailAudioRecorder.recordFiveSecondWav(it)
                    }
                }

                repo.insert(
                    BiodiversityContribution(
                        id = UUID.randomUUID().toString(),
                        observationId = observationId,
                        userId = user.userId,
                        observerDisplayName = user.displayName,
                        observerWalletPublicKey = user.walletPublicKey.ifBlank { null },
                        createdAt = timestamp,
                        lat = location.lat,
                        lon = location.lon,
                        locationAccuracyMeters = location.accuracyMeters,
                        locationSource = location.source,
                        audioUri = audioFile.absolutePath,
                        dataShareStatus = if (location.lat != null && location.lon != null) {
                            "captured_local"
                        } else {
                            "location_missing"
                        }
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isRecording = false,
                    latestObservationId = observationId,
                    currentUser = user
                )
                BiodiversityLocalInferenceWorker.schedule(getApplication(), observationId)
                BiodiversitySyncWorker.schedule(getApplication())
                TrailFeedbackBus.emit("Trail sound stored locally. Classification is running on this phone.", FeedbackTone.Info)
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
            TrailFeedbackBus.emit("Photo attached to this biodiversity event.", FeedbackTone.Success)
        }
    }

    fun saveContribution() {
        val observationId = _uiState.value.latestObservationId ?: return
        viewModelScope.launch {
            repo.saveContribution(observationId)
            TrailFeedbackBus.emit("Biodiversity contribution saved to the local trail ledger.", FeedbackTone.Success)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun awaitLocationSnapshot(): BiodiversityLocationSnapshot = suspendCancellableCoroutine { continuation ->
        fusedLocation.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(
                    if (location != null) {
                        BiodiversityLocationSnapshot(
                            lat = location.latitude,
                            lon = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                            source = "fused_last_known"
                        )
                    } else {
                        BiodiversityLocationSnapshot(source = "missing")
                    }
                )
            }
            .addOnFailureListener { continuation.resume(BiodiversityLocationSnapshot(source = "missing")) }
    }
}
