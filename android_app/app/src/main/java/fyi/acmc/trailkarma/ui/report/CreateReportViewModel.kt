package fyi.acmc.trailkarma.ui.report

import android.app.Application
import android.annotation.SuppressLint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import fyi.acmc.trailkarma.ble.BleRepositoryHolder
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.repository.ReportRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.OperationStateTone
import fyi.acmc.trailkarma.ui.feedback.OperationStepState
import fyi.acmc.trailkarma.ui.feedback.OperationStepUi
import fyi.acmc.trailkarma.ui.feedback.OperationUiState
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
import fyi.acmc.trailkarma.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.time.Instant
import java.util.UUID

class CreateReportViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = ReportRepository(db.trailReportDao())
    private val bleRepo = BleRepositoryHolder.getInstance(app)
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)
    private val userRepo = UserRepository(app, db.userDao())
    private val networkUtil = NetworkUtil(app)

    val saving = MutableStateFlow(false)
    val operation = MutableStateFlow<OperationUiState?>(null)
    val saveCompleted = MutableStateFlow(false)

    fun consumeSaveCompleted() {
        saveCompleted.value = false
    }

    @SuppressLint("MissingPermission")
    fun save(type: ReportType, title: String, description: String, speciesName: String?) {
        if (saving.value) return
        saving.value = true
        operation.value = OperationUiState(
            title = "Saving report",
            message = "Writing your field note to this phone first so it is safe even without service.",
            tone = OperationStateTone.Working,
            progress = 0.18f,
            steps = listOf(
                OperationStepUi("Saved on this phone", "Your report is being written locally.", OperationStepState.Active),
                OperationStepUi("Cloud sync", "Will start if service is available.", OperationStepState.Pending),
                OperationStepUi("KARMA claim", "Runs after verification checks.", OperationStepState.Pending),
            )
        )
        fusedLocation.lastLocation.addOnCompleteListener { task ->
            val loc = task.result
            if (!task.isSuccessful) {
                Log.w("CreateReport", "⚠ Unable to read cached location, saving report without it", task.exception)
            }
            viewModelScope.launch {
                try {
                    val user = userRepo.ensureLocalUser()
                    val reportId = UUID.randomUUID().toString()

                    Log.d("CreateReport", "💾 Saving report locally: $reportId - $title")
                    repo.save(
                        TrailReport(
                            reportId = reportId,
                            userId = user.userId,
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
                    Log.d("CreateReport", "✓ Report saved locally")
                    bleRepo.onNewLocalReportCreated(reportId)

                    val online = networkUtil.isOnlineNow()
                    operation.value = OperationUiState(
                        title = "Report saved",
                        message = if (online) {
                            "Your field note is saved. Sync and KARMA settlement are continuing in the background."
                        } else {
                            "Your field note is saved on this phone and will sync when service returns."
                        },
                        tone = OperationStateTone.Success,
                        progress = 1f,
                        steps = listOf(
                            OperationStepUi("Saved on this phone", "Done.", OperationStepState.Complete),
                            OperationStepUi(
                                "Cloud sync",
                                if (online) "Queued in the background." else "Waiting for service.",
                                if (online) OperationStepState.Active else OperationStepState.Pending
                            ),
                            OperationStepUi(
                                "KARMA claim",
                                "Runs after sync and verification checks.",
                                if (online) OperationStepState.Active else OperationStepState.Pending
                            ),
                        )
                    )
                    TrailFeedbackBus.emit(
                        if (online) {
                            "Report saved. Sync and KARMA updates are continuing in the background."
                        } else {
                            "Report saved offline. TrailKarma will sync and claim rewards when service returns."
                        },
                        FeedbackTone.Success
                    )
                    SyncWorker.schedule(getApplication())
                    saveCompleted.value = true
                } catch (error: Exception) {
                    Log.e("CreateReport", "✗ Failed to save report", error)
                    operation.value = OperationUiState(
                        title = "Save failed",
                        message = error.message ?: "The report could not be saved right now.",
                        tone = OperationStateTone.Error,
                        steps = listOf(
                            OperationStepUi("Saved on this phone", "The local write did not finish.", OperationStepState.Error),
                        )
                    )
                    TrailFeedbackBus.emit("Unable to save the report right now.", FeedbackTone.Error)
                } finally {
                    saving.value = false
                }
            }
        }
    }
}
