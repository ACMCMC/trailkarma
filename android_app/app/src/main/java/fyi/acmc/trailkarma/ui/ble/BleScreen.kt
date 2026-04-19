package fyi.acmc.trailkarma.ui.ble

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.repository.RewardsRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val repo = BleRepository(app, db.relayPacketDao())
    private val rewardsRepository = RewardsRepository(app, db)
    val nearbyDevices = repo.nearbyDevices
    val log = repo.eventLog
    val relayJobs = db.relayJobIntentDao().getAll()

    fun startScan() = repo.startScan()
    fun stopScan() = repo.stopScan()

    fun createDemoRelayJob() = viewModelScope.launch {
        rewardsRepository.createRelayIntent(
            destinationLabel = "demo-contact",
            payloadReference = "encrypted-message:${System.currentTimeMillis()}"
        )
    }

    fun openRelayJobs() = viewModelScope.launch {
        rewardsRepository.openPendingRelayJobs()
    }

    fun fulfill(jobId: String) = viewModelScope.launch {
        rewardsRepository.fulfillRelayJob(jobId, "mock-provider-proof:$jobId")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(vm: BleViewModel = viewModel()) {
    val devices by vm.nearbyDevices.collectAsState()
    val log by vm.log.collectAsState()
    val relayJobs by vm.relayJobs.collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("Nearby Hikers (BLE)") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startScan() }, modifier = Modifier.weight(1f)) { Text("Scan") }
                OutlinedButton(onClick = { vm.stopScan() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.createDemoRelayJob() }, modifier = Modifier.weight(1f)) { Text("Create Relay") }
                OutlinedButton(onClick = { vm.openRelayJobs() }, modifier = Modifier.weight(1f)) { Text("Open On-chain") }
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
            Text("Relay Jobs", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                items(relayJobs) { job ->
                    RelayJobCard(job = job, onFulfill = { vm.fulfill(job.jobId) })
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

@Composable
private fun RelayJobCard(job: RelayJobIntent, onFulfill: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Job ${job.jobId.take(8)}...", style = MaterialTheme.typography.titleSmall)
            Text("Reward ${job.rewardAmount} KARMA • ${job.status}", style = MaterialTheme.typography.bodySmall)
            job.openedTxSignature?.let { Text("open tx ${it.take(10)}...", style = MaterialTheme.typography.labelSmall) }
            job.fulfilledTxSignature?.let { Text("fulfill tx ${it.take(10)}...", style = MaterialTheme.typography.labelSmall) }
            if (job.status == "open") {
                Button(onClick = onFulfill) { Text("Fulfill") }
            }
        }
    }
}
