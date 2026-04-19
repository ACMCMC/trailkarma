package fyi.acmc.trailkarma.ui.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.repository.ReportRepository
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import fyi.acmc.trailkarma.ui.rewards.TrailKarmaRewardsTheme

class ReportHistoryViewModel(app: Application) : AndroidViewModel(app) {
    val reports = ReportRepository(AppDatabase.get(app).trailReportDao()).allReports
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    onBack: () -> Unit = {},
    vm: ReportHistoryViewModel = viewModel()
) {
    val reports by vm.reports.collectAsState(initial = emptyList())

    TrailKarmaRewardsTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Report ledger")
                            Text(
                                "Verification and reward status for every field report",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
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
                            colors = listOf(
                                Color(0xFFFFF8EE),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(padding)
            ) {
                if (reports.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No reports yet")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(reports) { report -> ReportItem(report) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportItem(report: TrailReport) {
    val accent = when (report.type) {
        ReportType.hazard -> RewardsPalette.Clay
        ReportType.water -> RewardsPalette.Sky
        ReportType.species -> RewardsPalette.Moss
    }
    val icon: ImageVector = when (report.type) {
        ReportType.hazard -> Icons.Default.Shield
        ReportType.water -> Icons.Default.WaterDrop
        ReportType.species -> Icons.Default.Forest
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(report.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${report.type.name.replaceFirstChar(Char::uppercase)} • ${report.timestamp.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (report.synced) "Synced" else "Offline") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (report.synced) RewardsPalette.Forest.copy(alpha = 0.12f) else RewardsPalette.Gold.copy(alpha = 0.18f),
                            labelColor = if (report.synced) RewardsPalette.Forest else RewardsPalette.Ink
                        )
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when {
                                    report.rewardClaimed -> "KARMA settled"
                                    report.verificationStatus == "rejected" -> "Rejected"
                                    else -> "Pending review"
                                }
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when {
                                report.rewardClaimed -> RewardsPalette.Forest.copy(alpha = 0.12f)
                                report.verificationStatus == "rejected" -> RewardsPalette.Clay.copy(alpha = 0.14f)
                                else -> RewardsPalette.Sky.copy(alpha = 0.12f)
                            },
                            labelColor = when {
                                report.rewardClaimed -> RewardsPalette.Forest
                                report.verificationStatus == "rejected" -> RewardsPalette.Clay
                                else -> RewardsPalette.Sky
                            }
                        )
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        report.rewardClaimed && report.type == ReportType.species && report.highConfidenceBonus -> "+13"
                        report.rewardClaimed && report.type != ReportType.species -> "+10"
                        report.rewardClaimed -> "+8"
                        else -> "--"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (report.rewardClaimed) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "KARMA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                report.rewardTxSignature?.let {
                    Text(
                        it.take(10) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
