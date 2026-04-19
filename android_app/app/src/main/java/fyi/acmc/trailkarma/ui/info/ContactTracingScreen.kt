package fyi.acmc.trailkarma.ui.info

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
            items(devices.toList()) { device ->
                TrailListRow(
                    title = device,
                    subtitle = "Available as a potential relay carrier",
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
            item {
                Text(
                    "No recent peer connections",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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
                    "BLE is active and scanning for nearby trail reporters.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
