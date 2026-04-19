package fyi.acmc.trailkarma.ui.biodiversity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.ui.biodiversitySourceLabel
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailScreenHeader
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.feedback.OperationStateTone
import fyi.acmc.trailkarma.ui.feedback.OperationStepState
import fyi.acmc.trailkarma.ui.feedback.OperationStepUi
import fyi.acmc.trailkarma.ui.feedback.OperationUiState
import fyi.acmc.trailkarma.ui.feedback.TrailOperationCard
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiodiversityCaptureScreen(
    onBack: () -> Unit,
    vm: BiodiversityCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    val contribution by vm.currentContribution.collectAsState(initial = null)
    val savedContributions by vm.savedContributions.collectAsState(initial = emptyList())
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingPhotoPath?.let(vm::attachPhoto)
        }
    }

    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Biodiversity audio") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFF8F3EA), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TrailHeroCard(
                            title = "Listen first, verify later",
                            subtitle = "Record a five second trail sound, classify it on-device, attach a proof photo, and save an auditable biodiversity record with contributor identity, coordinates, and collectible status.",
                            accent = RewardsPalette.Pine,
                            supporting = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TrailInfoChip(
                                        icon = Icons.Default.Mic,
                                        label = "Offline-first capture",
                                        accent = RewardsPalette.Gold
                                    )
                                    uiState.currentUser?.let { user ->
                                        TrailInfoChip(
                                            icon = Icons.Default.Verified,
                                            label = user.displayName,
                                            accent = if (user.walletPublicKey.isNotBlank()) RewardsPalette.Forest else RewardsPalette.Sky
                                        )
                                    }
                                }
                            }
                        )
                    }

                    item {
                        TrailSectionCard(
                            title = "Capture flow",
                            accent = RewardsPalette.Sky
                        ) {
                            TrailScreenHeader(
                                title = "Record one environmental clip",
                                subtitle = "Audio stays local first. BLE only carries compact event metadata. Full audio and photo sync later when a network path appears."
                            )

                            Button(
                                onClick = { vm.recordTrailSound() },
                                enabled = !uiState.isRecording,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RewardsPalette.Forest)
                            ) {
                                if (uiState.isRecording) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                        modifier = Modifier.height(18.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(if (uiState.isRecording) "Recording 5 second clip..." else "Record Trail Sound")
                            }

                            uiState.errorMessage?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    contribution?.let { item ->
                        item {
                            TrailOperationCard(
                                state = contributionOperationState(item),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            TrailSectionCard(
                                title = "Current finding",
                                accent = RewardsPalette.Gold
                            ) {
                                BiodiversityContributionDetail(
                                    contribution = item,
                                    currentUser = uiState.currentUser,
                                    onAttachPhoto = {
                                        val file = vm.createPhotoFile(item.observationId)
                                        pendingPhotoPath = file.absolutePath
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        takePhotoLauncher.launch(uri)
                                    },
                                    onSaveContribution = { vm.saveContribution() }
                                )
                            }
                        }
                    }

                    item {
                        TrailSectionCard(
                            title = "Local biodiversity ledger",
                            accent = RewardsPalette.Moss
                        ) {
                            TrailScreenHeader(
                                title = "Share-ready field records",
                                subtitle = "Each saved event keeps the who, what, where, when, confidence, proof photo state, sync state, and collectible lifecycle needed for later export to biodiversity partners."
                            )

                            if (savedContributions.isEmpty()) {
                                Text(
                                    "No saved biodiversity entries yet. Record one clip and save it to start the local ledger.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                savedContributions.take(5).forEach { item ->
                                    BiodiversityLedgerRow(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiodiversityContributionDetail(
    contribution: BiodiversityContribution,
    currentUser: User?,
    onAttachPhoto: () -> Unit,
    onSaveContribution: () -> Unit
) {
    val confidencePercent = ((contribution.confidence ?: 0f) * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (contribution.finalLabel == null) {
            Text(
                when (contribution.inferenceState) {
                    InferenceState.FAILED_LOCAL -> contribution.explanation ?: "Local classification failed, but the raw clip is still stored and can be retried."
                    InferenceState.CLASSIFYING_LOCAL -> "This phone is classifying the clip now."
                    else -> "The observation is queued locally. Classification will appear as soon as the model finishes."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                contribution.finalLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${contribution.finalTaxonomicLevel ?: "unknown"} confidence • $confidencePercent% score",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            contribution.explanation?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrailInfoChip(
                icon = Icons.Default.Explore,
                label = locationSummary(contribution),
                accent = locationAccent(contribution)
            )
            TrailInfoChip(
                icon = Icons.Default.Verified,
                label = contribution.observerDisplayName ?: currentUser?.displayName ?: "anonymous hiker",
                accent = RewardsPalette.Forest
            )
        }

        biodiversitySourceLabel(contribution.classificationSource)?.let { sourceLabel ->
            TrailInfoChip(
                icon = Icons.Default.GraphicEq,
                label = sourceLabel,
                accent = if (contribution.classificationSource == "heuristic_fallback") {
                    RewardsPalette.Gold
                } else {
                    RewardsPalette.Pine
                }
            )
        }

        StatusStrip(contribution)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Contribution record", style = MaterialTheme.typography.titleMedium)
                Text("Observation ID: ${contribution.observationId}", style = MaterialTheme.typography.labelSmall)
                Text("Captured at: ${contribution.createdAt}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Verifier state: ${contribution.verificationStatus.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Collectible state: ${contribution.collectibleStatus.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Data sharing state: ${contribution.dataShareStatus.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Location quality: ${locationDetail(contribution)}",
                    style = MaterialTheme.typography.bodySmall
                )
                contribution.collectibleName?.let { collectibleName ->
                    Text("Collectible: $collectibleName", style = MaterialTheme.typography.bodySmall)
                }
                contribution.verificationTxSignature?.let { tx ->
                    Text("Verification tx: ${tx.take(16)}...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onAttachPhoto,
                enabled = contribution.finalLabel != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (contribution.photoUri == null) "Attach proof photo" else "Replace photo")
            }
            Button(
                onClick = onSaveContribution,
                enabled = contribution.finalLabel != null && !contribution.savedLocally,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = RewardsPalette.Forest)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (contribution.savedLocally) "Saved to ledger" else "Save contribution")
            }
        }
    }
}

@Composable
private fun StatusStrip(contribution: BiodiversityContribution) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrailInfoChip(
                icon = Icons.Default.Save,
                label = if (contribution.savedLocally) "Saved locally" else "Unsaved draft",
                accent = if (contribution.savedLocally) RewardsPalette.Forest else RewardsPalette.Gold
            )
            TrailInfoChip(
                icon = Icons.Default.CloudUpload,
                label = contribution.cloudSyncState.name.lowercase().replace('_', ' '),
                accent = when (contribution.cloudSyncState) {
                    CloudSyncState.SYNCED -> RewardsPalette.Forest
                    CloudSyncState.SYNC_FAILED -> RewardsPalette.Clay
                    else -> RewardsPalette.Sky
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrailInfoChip(
                icon = Icons.Default.CameraAlt,
                label = if (contribution.photoUri != null) "Photo linked" else "Photo optional",
                accent = if (contribution.photoUri != null) RewardsPalette.Moss else RewardsPalette.Stone
            )
            TrailInfoChip(
                icon = Icons.Default.AutoAwesome,
                label = collectibleLabel(contribution),
                accent = collectibleAccent(contribution)
            )
        }
    }
}

@Composable
private fun BiodiversityLedgerRow(item: BiodiversityContribution) {
    TrailListRow(
        title = item.finalLabel ?: "Unresolved biodiversity capture",
        subtitle = buildString {
            append(item.observerDisplayName ?: "unknown hiker")
            append(" • ")
            append(item.finalTaxonomicLevel ?: item.inferenceState.name.lowercase())
            append(" • ")
            append(locationShortLabel(item))
        },
        icon = when (item.collectibleStatus) {
            "verified" -> Icons.Default.Verified
            "pending_verification" -> Icons.Default.AutoAwesome
            else -> Icons.Default.Mic
        },
        accent = collectibleAccent(item),
        trailing = {
            Text(
                item.dataShareStatus.replace('_', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

private fun contributionOperationState(item: BiodiversityContribution): OperationUiState {
    val steps = listOf(
        OperationStepUi(
            label = "Clip stored locally",
            detail = "The 5 second WAV stays on this device first.",
            state = OperationStepState.Complete
        ),
        OperationStepUi(
            label = "On-device classification",
            detail = when (item.inferenceState) {
                InferenceState.CLASSIFIED_LOCAL -> item.finalLabel ?: "Classification complete"
                InferenceState.CLASSIFYING_LOCAL -> "Running Perch embedding and open-world ranking now."
                InferenceState.FAILED_LOCAL -> item.explanation ?: "Local classification failed."
                InferenceState.PENDING_LOCAL -> "Waiting for local inference."
            },
            state = when (item.inferenceState) {
                InferenceState.CLASSIFIED_LOCAL -> OperationStepState.Complete
                InferenceState.CLASSIFYING_LOCAL -> OperationStepState.Active
                InferenceState.FAILED_LOCAL -> OperationStepState.Error
                InferenceState.PENDING_LOCAL -> OperationStepState.Pending
            }
        ),
        OperationStepUi(
            label = "Saved to biodiversity ledger",
            detail = if (item.lat != null && item.lon != null) {
                "What was found, where, when, and by whom stays queryable for later partner sharing."
            } else {
                "This record is missing coordinates and should be recaptured or enriched before scientific export."
            },
            state = when {
                item.lat == null || item.lon == null -> OperationStepState.Error
                item.savedLocally -> OperationStepState.Complete
                else -> OperationStepState.Pending
            }
        ),
        OperationStepUi(
            label = "Proof photo link",
            detail = if (item.photoUri != null) "Photo linked to the same observation." else "Optional photo can be attached for later verification.",
            state = if (item.photoUri != null) OperationStepState.Complete else OperationStepState.Pending
        ),
        OperationStepUi(
            label = "Cloud mirror and org export readiness",
            detail = item.dataShareStatus.replace('_', ' '),
            state = when (item.cloudSyncState) {
                CloudSyncState.SYNCED -> OperationStepState.Complete
                CloudSyncState.SYNC_FAILED -> OperationStepState.Error
                CloudSyncState.SYNCING -> OperationStepState.Active
                else -> OperationStepState.Pending
            }
        ),
        OperationStepUi(
            label = "Collectible and blockchain verification",
            detail = collectibleLabel(item),
            state = when (item.collectibleStatus) {
                "verified" -> OperationStepState.Complete
                "duplicate_species", "verified_no_collectible" -> OperationStepState.Complete
                "pending_verification" -> OperationStepState.Active
                "not_eligible" -> OperationStepState.Pending
                else -> OperationStepState.Pending
            }
        )
    )

    val completed = steps.count { it.state == OperationStepState.Complete }
    val active = steps.any { it.state == OperationStepState.Active }
    val errored = steps.any { it.state == OperationStepState.Error }

    return OperationUiState(
        title = "Observation lifecycle",
        message = when {
            errored -> "Some parts of the biodiversity workflow need attention."
            item.collectibleStatus == "verified" -> "This observation is verified and collectible-backed."
            item.collectibleStatus == "duplicate_species" -> "This species was verified again, but its collectible was already discovered."
            item.collectibleStatus == "verified_no_collectible" -> "This observation was verified, but it did not unlock a unique species card."
            active -> "This contribution is moving through classification, sync, and verification."
            else -> "The record is waiting on the next user action or sync window."
        },
        tone = when {
            errored -> OperationStateTone.Error
            item.collectibleStatus in setOf("verified", "duplicate_species", "verified_no_collectible") -> OperationStateTone.Success
            else -> OperationStateTone.Working
        },
        progress = completed / steps.size.toFloat(),
        steps = steps
    )
}

private fun collectibleLabel(item: BiodiversityContribution): String = when (item.collectibleStatus) {
    "verified" -> item.collectibleName ?: "Collectible verified"
    "duplicate_species" -> "${item.collectibleName ?: item.finalLabel ?: "Species"} already collected"
    "verified_no_collectible" -> "Verified contribution without a new species card"
    "pending_verification" -> "Collectible pending verification"
    "not_eligible" -> "Not currently reward-eligible"
    else -> "No collectible yet"
}

private fun collectibleAccent(item: BiodiversityContribution): Color = when (item.collectibleStatus) {
    "verified" -> RewardsPalette.Gold
    "duplicate_species", "verified_no_collectible" -> RewardsPalette.Forest
    "pending_verification" -> RewardsPalette.Moss
    "not_eligible" -> RewardsPalette.Stone
    else -> RewardsPalette.Sky
}

private fun locationSummary(item: BiodiversityContribution): String = when {
    item.lat != null && item.lon != null -> {
        val base = "${String.format("%.5f", item.lat)}, ${String.format("%.5f", item.lon)}"
        item.locationAccuracyMeters?.let { "$base (${it.toInt()}m)" } ?: base
    }
    else -> "Location missing"
}

private fun locationShortLabel(item: BiodiversityContribution): String = when {
    item.lat != null && item.lon != null -> {
        val quality = locationQualityLabel(item)
        "${String.format("%.4f", item.lat)}, ${String.format("%.4f", item.lon)} • $quality"
    }
    else -> "location missing"
}

private fun locationDetail(item: BiodiversityContribution): String = when {
    item.lat == null || item.lon == null -> "Missing coordinates. This record should not be treated as export-ready biodiversity data."
    else -> buildString {
        append(locationQualityLabel(item))
        item.locationAccuracyMeters?.let { append(" (${it.toInt()}m accuracy)") }
        append(" via ")
        append(item.locationSource.replace('_', ' '))
    }
}

private fun locationQualityLabel(item: BiodiversityContribution): String = when {
    item.locationAccuracyMeters == null -> "coordinates present"
    item.locationAccuracyMeters <= 25f -> "precise gps"
    item.locationAccuracyMeters <= 100f -> "approximate gps"
    else -> "coarse location"
}

private fun locationAccent(item: BiodiversityContribution): Color = when {
    item.lat == null || item.lon == null -> RewardsPalette.Clay
    item.locationAccuracyMeters == null -> RewardsPalette.Sky
    item.locationAccuracyMeters <= 25f -> RewardsPalette.Forest
    item.locationAccuracyMeters <= 100f -> RewardsPalette.Moss
    else -> RewardsPalette.Gold
}
