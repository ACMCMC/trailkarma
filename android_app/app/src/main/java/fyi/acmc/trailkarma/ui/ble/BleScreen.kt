package fyi.acmc.trailkarma.ui.ble

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.ble.BleRepository
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import fyi.acmc.trailkarma.ui.rewards.TrailKarmaRewardsTheme
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val rewardsRepository = RewardsRepository(app, db)
    var statusMessage by mutableStateOf<String?>(null)
        private set

    val repo = BleRepository(
        context        = app,
        relayPacketDao = db.relayPacketDao(),
        trailReportDao = db.trailReportDao()
    )
    val nearbyDevices = repo.nearbyDevices
    val log = repo.eventLog
    val relayJobs = db.relayJobIntentDao().getAll()

    fun startScan() = repo.startScan()
    fun stopScan() = repo.stopScan()

    fun createDemoRelayJob() = viewModelScope.launch {
        val intent = rewardsRepository.createRelayIntent(
            destinationLabel = "demo-contact",
            payloadReference = "encrypted-message:${System.currentTimeMillis()}"
        )
        statusMessage = if (intent != null) {
            "Created relay job ${intent.jobId.take(8)}..."
        } else {
            "Create a user profile first before creating relay jobs."
        }
    }

    fun openRelayJobs() = viewModelScope.launch {
        rewardsRepository.openPendingRelayJobs()
        statusMessage = "Attempted to open pending relay jobs."
    }

    fun fulfill(jobId: String) = viewModelScope.launch {
        val ok = rewardsRepository.fulfillRelayJob(jobId, "mock-provider-proof:$jobId")
        statusMessage = if (ok) {
            "Fulfilled relay job ${jobId.take(8)}..."
        } else {
            "Unable to fulfill relay job right now."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(
    onBack: () -> Unit = {},
    vm: BleViewModel = viewModel()
) {
    val devices by vm.nearbyDevices.collectAsState()
    val log by vm.log.collectAsState()
    val relayJobs by vm.relayJobs.collectAsState(initial = emptyList())

    TrailKarmaRewardsTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Relay missions")
                            Text(
                                "Offline-first jobs that open and settle on chain later",
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF5FAFF),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(RewardsPalette.Sky, Color(0xFF6CAFE1), Color(0xFFB6D7F1))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "Phone-to-phone relay layer",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    "Create delayed delivery tasks offline, open them when someone regains connectivity, and reward the first valid fulfiller.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.84f)
                                )
                                vm.statusMessage?.let {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.White.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = it,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.startScan() }, modifier = Modifier.weight(1f)) {
                            Text("Scan")
                        }
                        OutlinedButton(onClick = { vm.stopScan() }, modifier = Modifier.weight(1f)) {
                            Text("Stop")
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.createDemoRelayJob() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create relay")
                        }
                        OutlinedButton(onClick = { vm.openRelayJobs() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open on chain")
                        }
                    }
                }

                item {
                    Text("Nearby hikers", style = MaterialTheme.typography.titleLarge)
                }

                if (devices.isEmpty()) {
                    item {
                        MissionEmptyCard("Scanning for nearby devices. Turn on BLE on another phone to test the relay path.")
                    }
                } else {
                    items(devices.toList()) { device ->
                        DeviceCard(device)
                    }
                }

                item {
                    Text("Mission queue", style = MaterialTheme.typography.titleLarge)
                }

                if (relayJobs.isEmpty()) {
                    item {
                        MissionEmptyCard("No relay jobs yet. Create a demo relay to exercise the reward flow.")
                    }
                } else {
                    items(relayJobs) { job ->
                        RelayJobCard(job = job, onFulfill = { vm.fulfill(job.jobId) })
                    }
                }

                item {
                    Text("Event log", style = MaterialTheme.typography.titleLarge)
                }

                if (log.isEmpty()) {
                    item {
                        MissionEmptyCard("BLE scans and relay events will appear here.")
                    }
                } else {
                    items(log) { entry ->
                        Text(
                            entry,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayJobCard(job: RelayJobIntent, onFulfill: () -> Unit) {
    val accent = when (job.status) {
        "fulfilled" -> RewardsPalette.Forest
        "open" -> RewardsPalette.Sky
        else -> RewardsPalette.Gold
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Job ${job.jobId.take(8)}...", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reward ${job.rewardAmount} KARMA",
                style = MaterialTheme.typography.bodyMedium,
                color = accent
            )
            AssistChip(
                onClick = {},
                label = { Text(job.status.replaceFirstChar(Char::uppercase)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = accent.copy(alpha = 0.14f),
                    labelColor = accent
                )
            )
            job.openedTxSignature?.let { Text("open tx ${it.take(10)}...", style = MaterialTheme.typography.labelSmall) }
            job.fulfilledTxSignature?.let { Text("fulfill tx ${it.take(10)}...", style = MaterialTheme.typography.labelSmall) }
            if (job.status == "open") {
                Button(onClick = onFulfill) { Text("Fulfill") }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = RewardsPalette.Sky)
            Text(device, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MissionEmptyCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
