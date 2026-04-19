package fyi.acmc.trailkarma.ui.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.ui.biodiversitySourceLabel
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import fyi.acmc.trailkarma.ui.rewards.TrailKarmaRewardsTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryItem(
    val id: String,
    val timestamp: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val synced: Boolean,
    val badges: List<Pair<String, Color>>
)

class ReportHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val history = combine(
        db.trailReportDao().getAll(),
        db.biodiversityContributionDao().getSaved()
    ) { reports, biodiversity ->
        buildList {
            addAll(reports.map(::reportItem))
            addAll(biodiversity.map(::biodiversityItem))
        }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun reportItem(report: TrailReport): HistoryItem = HistoryItem(
        id = report.reportId,
        timestamp = report.timestamp,
        title = report.title,
        subtitle = report.type.name,
        body = report.description,
        synced = report.synced,
        badges = buildList {
            add(report.type.name to Color(0xFF546E7A))
            if (report.source.name == "relayed") add("relayed" to Color(0xFF6D4C41))
            when {
                report.rewardClaimed -> add("karma settled" to Color(0xFF2E7D32))
                report.verificationStatus == "rejected" -> add("rejected" to Color(0xFFC62828))
                else -> add("pending review" to Color(0xFF1565C0))
            }
        }
    )

    private fun biodiversityItem(item: BiodiversityContribution): HistoryItem = HistoryItem(
        id = item.id,
        timestamp = item.createdAt,
        title = item.finalLabel ?: "Queued local biodiversity capture",
        subtitle = buildString {
            append(item.observerDisplayName ?: "unknown hiker")
            append(" • biodiversity_audio_detection")
        },
        body = item.explanation ?: "Audio captured at ${item.createdAt}",
        synced = item.cloudSyncState == CloudSyncState.SYNCED || item.synced,
        badges = buildList {
            add((item.finalTaxonomicLevel ?: "pending") to Color(0xFF1565C0))
            add((item.confidenceBand ?: item.inferenceState.name.lowercase()) to Color(0xFF00897B))
            biodiversitySourceLabel(item.classificationSource)?.let { sourceLabel ->
                add(
                    sourceLabel to if (item.classificationSource == "heuristic_fallback") {
                        Color(0xFFEF6C00)
                    } else {
                        Color(0xFF455A64)
                    }
                )
            }
            if (item.photoUri != null) add("photo" to Color(0xFF6A1B9A))
            if (item.karmaStatus.name == "pending") add("karma pending" to Color(0xFF8E24AA))
            if (item.collectibleStatus == "verified") add("collectible" to Color(0xFFE7A64F))
            if (item.collectibleStatus == "duplicate_species") add("species already collected" to Color(0xFF2E7D32))
            if (item.verificationStatus == "verified" && item.collectibleStatus == "verified_no_collectible") {
                add("verified" to Color(0xFF2E7D32))
            }
            if (item.lat == null || item.lon == null) add("location missing" to Color(0xFFC62828))
            add(item.dataShareStatus.replace('_', ' ') to Color(0xFF546E7A))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    onBack: () -> Unit = {},
    vm: ReportHistoryViewModel = viewModel()
) {
    val history by vm.history.collectAsState()
    val savedCount = history.size
    val syncedCount = history.count { it.synced }
    val collectibleCount = history.count { item -> item.badges.any { it.first == "collectible" } }

    TrailKarmaRewardsTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Trail feed")
                            Text(
                                "Everything this device has captured, carried, or verified",
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
                            colors = listOf(Color(0xFFFFF8EE), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(padding)
            ) {
                if (history.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No activity yet")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            TrailHeroCard(
                                title = "Your field record",
                                subtitle = "This feed ties together local reports, biodiversity captures, pending sync work, and settled rewards so the demo feels like one coherent system rather than separate tools.",
                                accent = RewardsPalette.Pine,
                                supporting = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        TrailInfoChip(
                                            icon = Icons.Default.Route,
                                            label = "$savedCount total items",
                                            accent = RewardsPalette.Sky
                                        )
                                        TrailInfoChip(
                                            icon = Icons.Default.CheckCircle,
                                            label = "$syncedCount synced",
                                            accent = RewardsPalette.Forest
                                        )
                                        TrailInfoChip(
                                            icon = Icons.Default.AutoAwesome,
                                            label = "$collectibleCount collectible wins",
                                            accent = RewardsPalette.Gold
                                        )
                                    }
                                }
                            )
                        }

                        items(history) { item -> HistoryItemCard(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(item: HistoryItem) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Badge(containerColor = if (item.synced) RewardsPalette.Forest else RewardsPalette.Gold) {
                    Text(if (item.synced) "synced" else "offline")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                item.badges.forEach { (label, color) ->
                    Badge(containerColor = color) {
                        Text(label)
                    }
                }
            }

            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
