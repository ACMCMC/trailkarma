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
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.repository.ReportRepository
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.OperationStateTone
import fyi.acmc.trailkarma.ui.feedback.OperationStepState
import fyi.acmc.trailkarma.ui.feedback.OperationStepUi
import fyi.acmc.trailkarma.ui.feedback.OperationUiState
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class CreateReportViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = ReportRepository(db.trailReportDao())
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)
    private val userRepo = UserRepository(app, db.userDao())
    private val syncRepo = DatabricksSyncRepository(app, db)
    private val rewardsRepo = RewardsRepository(app, db)
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
        fusedLocation.lastLocation.addOnSuccessListener { loc ->
            viewModelScope.launch {
                val user = userRepo.ensureLocalUser()
                val reportId = UUID.randomUUID().toString()

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

                if (networkUtil.isOnlineNow()) {
                    operation.value = OperationUiState(
                        title = "Syncing report",
                        message = "Service is available, so the app is uploading the report and checking the reward path now.",
                        tone = OperationStateTone.Working,
                        progress = 0.62f,
                        steps = listOf(
                            OperationStepUi("Saved on this phone", "Your report is safe locally.", OperationStepState.Complete),
                            OperationStepUi("Cloud sync", "Uploading report and trail context.", OperationStepState.Active),
                            OperationStepUi("KARMA claim", "Preparing the reward verification step.", OperationStepState.Pending),
                        )
                    )

                    if (syncRepo.isConfigured()) {
                        syncRepo.syncReports()
                    }

                    rewardsRepo.syncCurrentUserRegistration()
                    rewardsRepo.claimRewardsForPendingReports()

                    val refreshed = db.trailReportDao().getById(reportId)
                    val claimed = refreshed?.rewardClaimed == true
                    operation.value = OperationUiState(
                        title = if (claimed) "Reward submitted" else "Saved and syncing",
                        message = if (claimed) {
                            "The report was synced and its KARMA claim was recorded."
                        } else {
                            "The report is synced. Reward settlement will appear once verification completes."
                        },
                        tone = OperationStateTone.Success,
                        progress = 1f,
                        steps = listOf(
                            OperationStepUi("Saved on this phone", "Done.", OperationStepState.Complete),
                            OperationStepUi("Cloud sync", "Uploaded successfully.", OperationStepState.Complete),
                            OperationStepUi(
                                "KARMA claim",
                                if (claimed) "Submitted to the rewards pipeline." else "Waiting for verification.",
                                OperationStepState.Complete
                            ),
                        )
                    )
                    TrailFeedbackBus.emit(
                        if (claimed) "Report saved, synced, and sent to the KARMA ledger." else "Report saved and synced. Reward verification will continue in the background.",
                        FeedbackTone.Success
                    )
                } else {
                    operation.value = OperationUiState(
                        title = "Saved offline",
                        message = "The report is stored safely on this phone and will sync when service returns.",
                        tone = OperationStateTone.Success,
                        progress = 1f,
                        steps = listOf(
                            OperationStepUi("Saved on this phone", "Done.", OperationStepState.Complete),
                            OperationStepUi("Cloud sync", "Waiting for service.", OperationStepState.Pending),
                            OperationStepUi("KARMA claim", "Will run after sync.", OperationStepState.Pending),
                        )
                    )
                    TrailFeedbackBus.emit(
                        "Report saved offline. TrailKarma will sync and claim rewards when service returns.",
                        FeedbackTone.Success
                    )
                }

                saving.value = false
                saveCompleted.value = true
            }
        }.addOnFailureListener { error ->
            operation.value = OperationUiState(
                title = "Save failed",
                message = error.message ?: "The report could not be saved right now.",
                tone = OperationStateTone.Error,
                steps = listOf(
                    OperationStepUi("Saved on this phone", "The local write did not finish.", OperationStepState.Error),
                )
            )
            saving.value = false
            TrailFeedbackBus.emit("Unable to save the report right now.", FeedbackTone.Error)
        }
    }
}
