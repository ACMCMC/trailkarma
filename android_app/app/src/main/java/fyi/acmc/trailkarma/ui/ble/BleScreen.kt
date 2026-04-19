package fyi.acmc.trailkarma.ui.ble

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.ble.BleRepository
import fyi.acmc.trailkarma.db.AppDatabase

class BleViewModel(app: Application) : AndroidViewModel(app) {
    val repo = BleRepository(
        context        = app,
        relayPacketDao = AppDatabase.get(app).relayPacketDao(),
        trailReportDao = AppDatabase.get(app).trailReportDao()
    )
    val nearbyDevices = repo.nearbyDevices
    val log = repo.eventLog

    fun startScan() = repo.startScan()
    fun stopScan() = repo.stopScan()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(vm: BleViewModel = viewModel()) {
    val devices by vm.nearbyDevices.collectAsState()
    val log by vm.log.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Nearby Hikers (BLE)") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startScan() }, modifier = Modifier.weight(1f)) { Text("Scan") }
                OutlinedButton(onClick = { vm.stopScan() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Nearby (${devices.size})", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                items(devices.toList()) { device ->
                    Text("• $device", modifier = Modifier.padding(vertical = 2.dp))
                }
                if (devices.isEmpty()) {
                    item { Text("Scanning...", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Event Log", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                items(log) { entry ->
                    Text(entry, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}
