package fyi.acmc.trailkarma.ui.biodiversity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.ui.biodiversitySourceLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiodiversityCaptureScreen(
    onBack: () -> Unit,
    vm: BiodiversityCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsState()
    val contribution by vm.currentContribution.collectAsState(initial = null)
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingPhotoPath?.let(vm::attachPhoto)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Trail Sound", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Capture a 5 second environmental clip. The app stores the WAV locally first, classifies it on-device, and only relays compact biodiversity metadata over BLE.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { vm.recordTrailSound() },
                        enabled = !uiState.isRecording,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isRecording) "Recording..." else "Record Trail Sound")
                    }
                    if (uiState.isRecording) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            contribution?.let { item ->
                BiodiversityContributionCard(
                    contribution = item,
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
}

@Composable
private fun BiodiversityContributionCard(
    contribution: BiodiversityContribution,
    onAttachPhoto: () -> Unit,
    onSaveContribution: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge {
                    Text(contribution.inferenceState.name.lowercase())
                }
                Badge(containerColor = if (contribution.cloudSyncState == CloudSyncState.SYNCED) Color(0xFF2E7D32) else Color(0xFF546E7A)) {
                    Text(contribution.cloudSyncState.name.lowercase())
                }
                if (contribution.savedLocally) {
                    Badge(containerColor = Color(0xFF2E7D32)) {
                        Text("saved")
                    }
                }
                if (contribution.karmaStatus.name == "pending") {
                    Badge(containerColor = Color(0xFF8E24AA)) {
                        Text("karma pending")
                    }
                }
            }

            Text("Observation ID: ${contribution.observationId}", style = MaterialTheme.typography.labelSmall)
            Text(
                "${String.format("%.5f", contribution.lat)}, ${String.format("%.5f", contribution.lon)} • ${contribution.createdAt}",
                style = MaterialTheme.typography.bodySmall
            )

            if (contribution.finalLabel == null) {
                Text(
                    when (contribution.inferenceState) {
                        InferenceState.FAILED_LOCAL -> contribution.explanation ?: "Local classification failed. The clip is still stored and can be retried."
                        InferenceState.CLASSIFYING_LOCAL -> "Classifying locally on this phone."
                        else -> "Queued locally. Classification will appear once on-device inference completes."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(contribution.finalLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${contribution.finalTaxonomicLevel ?: "unknown"} • ${contribution.confidenceBand ?: "pending"} • ${((contribution.confidence ?: 0f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                biodiversitySourceLabel(contribution.classificationSource)?.let { sourceLabel ->
                    Text(
                        "Classification source: $sourceLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (contribution.classificationSource == "heuristic_fallback") {
                            Color(0xFFB26A00)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(contribution.explanation ?: "", style = MaterialTheme.typography.bodyMedium)
            }

            if (contribution.photoUri != null) {
                Text("Photo attached for later verification", color = Color(0xFF2E7D32))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onAttachPhoto,
                    enabled = contribution.finalLabel != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Attach Photo")
                }
                Button(
                    onClick = onSaveContribution,
                    enabled = contribution.finalLabel != null && !contribution.savedLocally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (contribution.savedLocally) "Saved" else "Save Contribution")
                }
            }
        }
    }
}
