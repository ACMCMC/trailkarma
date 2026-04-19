package fyi.acmc.trailkarma.ui.report

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReportDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {

    private val _report = MutableStateFlow<TrailReport?>(null)
    val report: StateFlow<TrailReport?> = _report

    init {
        val reportId: String = checkNotNull(savedStateHandle["reportId"])
        viewModelScope.launch {
            _report.value = AppDatabase.get(app).trailReportDao().getById(reportId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onBack: () -> Unit,
    vm: ReportDetailViewModel = viewModel()
) {
    val report by vm.report.collectAsState()

    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val r = report!!
    val typeColor = when (r.type) {
        ReportType.hazard  -> Color(0xFFD50000)
        ReportType.water   -> Color(0xFF00B8CC)
        ReportType.species -> Color(0xFF2E7D32)
    }
    val typeIcon: ImageVector = when (r.type) {
        ReportType.hazard  -> Icons.Default.Warning
        ReportType.water   -> Icons.Default.Water
        ReportType.species -> Icons.Default.Pets
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Hero header ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(typeColor.copy(alpha = 0.85f), typeColor.copy(alpha = 0.4f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Text(
                        r.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // ── Detail card ──────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-20).dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Badges row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(containerColor = typeColor) {
                            Text(r.type.name.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (r.source == ReportSource.relayed) {
                            Badge(containerColor = Color(0xFF555555)) {
                                Text("via BLE relay", fontSize = 10.sp)
                            }
                        }
                        // Sync status
                        if (r.synced) {
                            Badge(containerColor = Color(0xFF4CAF50)) {
                                Text("✓ synced", fontSize = 10.sp)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF9800)
                                )
                                Text("pending upload", fontSize = 10.sp, color = Color(0xFFFF9800))
                            }
                        }
                        when {
                            r.rewardClaimed -> {
                                Badge(containerColor = Color(0xFF2E7D32)) {
                                    Text("KARMA settled", fontSize = 10.sp)
                                }
                            }
                            r.verificationStatus == "rejected" -> {
                                Badge(containerColor = Color(0xFFD50000)) {
                                    Text("rejected", fontSize = 10.sp)
                                }
                            }
                            else -> {
                                Badge(containerColor = Color(0xFF1976D2)) {
                                    Text("pending review", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Description
                    if (r.description.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Description", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Species info (if applicable)
                    if (r.speciesName != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Species", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(r.speciesName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (r.confidence != null) {
                                    val pct = (r.confidence * 100).toInt()
                                    Badge(containerColor = if (pct >= 85) Color(0xFF2E7D32) else Color(0xFFFF9800)) {
                                        Text("$pct% confidence", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Location row
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            "${String.format("%.5f", r.lat)}°N, ${String.format("%.5f", r.lng)}°W",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Timestamp row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            r.timestamp.take(19).replace("T", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    r.rewardTxSignature?.let { signature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Text(
                                "Reward tx ${signature.take(12)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
