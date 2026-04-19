package fyi.acmc.trailkarma.ui.info

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.ui.ble.BleViewModel
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactTracingScreen(onBack: () -> Unit, vm: BleViewModel = viewModel()) {
    Log.d("ContactTracing", "🔧 ContactTracingScreen() called")
    val devices by vm.nearbyDevices.collectAsState()
    val syncingPeer by vm.syncingPeer.collectAsState()
    val eventLog by vm.log.collectAsState()
    val recentEvents = eventLog.take(12)
    val bleStatus = when {
        syncingPeer != null -> "Syncing with $syncingPeer now."
        devices.isNotEmpty() -> "Found ${devices.size} nearby hiker${if (devices.size == 1) "" else "s"} ready for relay exchange."
        else -> "Scanning for nearby hikers. Recent encounters will appear here as they happen."
    }

    LaunchedEffect(Unit) {
        vm.startScan()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Contact Tracing") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Text(
                    "P2P Contacts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item {
                Text(
                    "Active Connections",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                Text(
                    "Nearby devices: ${devices.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            syncingPeer?.let { peer ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Sync in progress", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Currently syncing with $peer",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            items(devices.toList()) { device ->
                TrailListRow(
                    title = device,
                    subtitle = if (device == syncingPeer) "Currently syncing now" else "Available as a potential relay carrier",
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                    accent = RewardsPalette.Sky
                )
            }
            item {
                Text(
                    "Recent Syncs",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (recentEvents.isEmpty()) {
                item {
                    Text(
                        "No peer activity yet on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            } else {
                items(recentEvents) { event ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            item {
                Text(
                    "Bluetooth LE Status",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                Text(
                    bleStatus,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
