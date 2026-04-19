package fyi.acmc.trailkarma.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.feedback.TrailOperationCard
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(
    onReportSaved: () -> Unit,
    vm: CreateReportViewModel = viewModel()
) {
    var type by remember { mutableStateOf(ReportType.hazard) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var speciesName by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }

    val saving by vm.saving.collectAsState()
    val operation by vm.operation.collectAsState()
    val saveCompleted by vm.saveCompleted.collectAsState()

    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            vm.consumeSaveCompleted()
            onReportSaved()
        }
    }

    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Field report")
                            Text(
                                "Save first, sync later, and let verified reports earn KARMA.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onReportSaved) {
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
                            colors = listOf(Color(0xFFF8FBF7), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        TrailHeroCard(
                            title = "Log what the next hiker should know",
                            subtitle = "Hazards, water, and species notes are always saved on this phone first, then synced and rewarded when the network path is available.",
                            accent = when (type) {
                                ReportType.hazard -> RewardsPalette.Clay
                                ReportType.water -> RewardsPalette.Sky
                                ReportType.species -> RewardsPalette.Moss
                            },
                            supporting = {
                                TrailInfoChip(
                                    icon = Icons.Default.Route,
                                    label = when (type) {
                                        ReportType.hazard -> "Hazards earn 10 KARMA"
                                        ReportType.water -> "Water reports earn 10 KARMA"
                                        ReportType.species -> "Species reports earn 8-13 KARMA"
                                    },
                                    accent = RewardsPalette.Gold
                                )
                            }
                        )
                    }

                    operation?.let { state ->
                        item {
                            TrailOperationCard(state = state)
                        }
                    }

                    item {
                        TrailSectionCard(title = "Report type", accent = RewardsPalette.Sand) {
                            ExposedDropdownMenuBox(
                                expanded = typeExpanded,
                                onExpandedChange = { typeExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = type.name.replaceFirstChar { it.uppercase() },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = typeExpanded,
                                    onDismissRequest = { typeExpanded = false }
                                ) {
                                    ReportType.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.name.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                type = option
                                                typeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        TrailSectionCard(title = "Details", accent = RewardsPalette.Forest) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Title") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                            if (type == ReportType.species) {
                                OutlinedTextField(
                                    value = speciesName,
                                    onValueChange = { speciesName = it },
                                    label = { Text("Species name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    item {
                        TrailSectionCard(title = "What happens next", accent = RewardsPalette.Gold) {
                            TrailInfoChip(
                                icon = Icons.Default.Info,
                                label = "Saved offline immediately",
                                accent = RewardsPalette.Forest
                            )
                            TrailInfoChip(
                                icon = Icons.Default.Info,
                                label = "Synced when service is available",
                                accent = RewardsPalette.Sky
                            )
                            TrailInfoChip(
                                icon = Icons.Default.Info,
                                label = "Verified reports mint KARMA on Solana",
                                accent = RewardsPalette.Gold
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    vm.save(type, title, description, speciesName.takeIf { it.isNotBlank() })
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            enabled = !saving && title.isNotBlank()
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save report")
                            }
                        }
                    }
                }
            }
        }
    }
}
