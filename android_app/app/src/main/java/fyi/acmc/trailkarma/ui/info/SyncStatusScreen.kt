package fyi.acmc.trailkarma.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.Trail
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.LocationUpdate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(onBack: () -> Unit, vm: SyncStatusViewModel = viewModel()) {
    val syncData = vm.syncStatus.collectAsState(initial = SyncStatusData()).value
    val resetInProgress by vm.resetInProgress.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sync Status — Debug Info") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Clear Local Sync Data?") },
                text = {
                    Text("This removes local trail reports, biodiversity entries, relay packets, relay jobs, inbox replies, and KARMA event logs so you can demo offline sync again. Trails and profile data stay intact.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            vm.clearLocalSyncDemoData()
                        },
                        enabled = !resetInProgress
                    ) {
                        Text("Clear local data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        LazyColumn(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            item {
                Text(
                    "Database Summary",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                Button(
                    onClick = { showResetDialog = true },
                    enabled = !resetInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(if (resetInProgress) "Clearing local sync data..." else "Clear local sync demo data")
                }
            }
            item {
                Text(
                    "Trail Reports: ${syncData.totalReportsCount} (${syncData.unsyncedReportsCount} unsynced)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                Text(
                    "Trails: ${syncData.totalTrailsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                Text(
                    "Location Updates: ${syncData.totalLocationsCount}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (syncData.reports.isNotEmpty()) {
                item {
                    Text(
                        "Trail Reports (${syncData.reports.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .padding(6.dp)
                            .fillMaxWidth()
                    )
                }
                items(syncData.reports) { report ->
                    ReportDebugItem(report, vm)
                }
            }

            if (syncData.trails.isNotEmpty()) {
                item {
                    Text(
                        "Trails (${syncData.trails.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .padding(6.dp)
                            .fillMaxWidth()
                    )
                }
                items(syncData.trails) { trail ->
                    TrailDebugItem(trail)
                }
            }

            if (syncData.locations.isNotEmpty()) {
                item {
                    Text(
                        "Location History (${syncData.locations.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .padding(6.dp)
                            .fillMaxWidth()
                    )
                }
                items(syncData.locations.take(10)) { location ->
                    LocationDebugItem(location)
                }
                if (syncData.locations.size > 10) {
                    item {
                        Text(
                            "... and ${syncData.locations.size - 10} more",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }

            item {
                Text(
                    "Sync Config",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            }
            item {
                Text(
                    "• Poll interval: 1 second (when online)\n• Trigger on network change: Yes\n• Auto-sync: Enabled",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ReportDebugItem(report: TrailReport, vm: SyncStatusViewModel) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F5F5))
            .padding(6.dp)
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(report.title, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        Row(modifier = Modifier.padding(top = 2.dp)) {
            Text("ID: ${report.reportId.take(8)}", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray, modifier = Modifier.weight(1f))
            Text(if (report.synced) "✓ synced" else "✗ unsynced", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = if (report.synced) Color.Green else Color.Red)
        }
        Text("${report.type.name} • %.4f°N, %.4f°W".format(report.lat, report.lng), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
        Text(report.timestamp, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
    }
}

@Composable
private fun TrailDebugItem(trail: Trail) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F5F5))
            .padding(6.dp)
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(trail.name, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        Text("ID: ${trail.trailId.take(8)}", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
        if (trail.totalLengthMiles != null) {
            Text("Length: ${String.format("%.1f", trail.totalLengthMiles)} miles", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LocationDebugItem(location: LocationUpdate) {
    Column(
        modifier = Modifier
            .background(Color(0xFFF5F5F5))
            .padding(6.dp)
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            "%.4f°N, %.4f°W".format(location.lat, location.lng),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp
        )
        Text(
            location.timestamp,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = Color.Gray
        )
    }
}
