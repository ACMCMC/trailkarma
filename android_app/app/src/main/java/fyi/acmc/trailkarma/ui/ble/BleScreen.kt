package fyi.acmc.trailkarma.ui.ble

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.ble.BleRepository
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayInboxMessage
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailScreenHeader
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val rewardsRepository = RewardsRepository(app, db)
    private val userRepository = UserRepository(app, db.userDao())

    var statusMessage = MutableStateFlow<String?>(null)
        private set

    val repo = BleRepository(
        context = app,
        relayPacketDao = db.relayPacketDao(),
        trailReportDao = db.trailReportDao(),
        relayJobIntentDao = db.relayJobIntentDao(),
        relayInboxMessageDao = db.relayInboxMessageDao()
    )
    val nearbyDevices = repo.nearbyDevices
    val log = repo.eventLog
    val relayJobs = db.relayJobIntentDao().getAll()

    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val contacts = _contacts
    private val _inbox = MutableStateFlow<List<RelayInboxMessage>>(emptyList())
    val inbox = _inbox

    init {
        viewModelScope.launch {
            val currentUser = userRepository.currentUser()
            if (currentUser != null) {
                _contacts.value = db.trustedContactDao().getForUser(currentUser.userId).first()
                _inbox.value = db.relayInboxMessageDao().getForUser(currentUser.userId).first()
            }
        }
    }

    fun startScan() = repo.startScan()
    fun stopScan() = repo.stopScan()

    fun queueVoiceRelay(
        recipientName: String,
        recipientPhone: String,
        messageBody: String,
        shareLocation: Boolean,
        shareRealName: Boolean,
        shareCallback: Boolean
    ) = viewModelScope.launch {
        val intent = rewardsRepository.createVoiceRelayIntent(
            recipientName = recipientName,
            recipientPhoneNumber = recipientPhone,
            messageBody = messageBody,
            shareLocation = shareLocation,
            shareRealName = shareRealName,
            shareCallbackNumber = shareCallback
        )
        statusMessage.value = if (intent != null) {
            "Queued voice relay ${intent.jobId.take(8)} offline."
        } else {
            "Finish profile setup before creating relays."
        }
    }

    fun syncVoiceRelayNow() = viewModelScope.launch {
        rewardsRepository.openPendingVoiceRelayJobs()
        rewardsRepository.syncRelayInbox()
        val currentUser = userRepository.currentUser()
        if (currentUser != null) {
            _inbox.value = db.relayInboxMessageDao().getForUser(currentUser.userId).first()
        }
        statusMessage.value = "Synced relay queue and inbox."
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
    val contacts by vm.contacts.collectAsState()
    val inbox by vm.inbox.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()

    var selectedContact by remember(contacts) { mutableStateOf(contacts.firstOrNull()) }
    var manualRecipientName by remember { mutableStateOf("") }
    var manualRecipientPhone by remember { mutableStateOf(selectedContact?.phoneNumber ?: "") }
    var relayMessage by remember { mutableStateOf("") }
    var shareLocation by remember { mutableStateOf(true) }
    var shareRealName by remember { mutableStateOf(false) }
    var shareCallback by remember { mutableStateOf(true) }
    var contactMenuExpanded by remember { mutableStateOf(false) }

    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Relay Hub")
                            Text(
                                "Queue voice calls offline, carry them over BLE, and deliver them once any hiker gets service.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::syncVoiceRelayNow) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync")
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
                            colors = listOf(Color(0xFFF4FAFF), MaterialTheme.colorScheme.background)
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
                            title = "Voice relay missions",
                            subtitle = "The app stores your message, signs the claim payload, and lets the first online hiker trigger an ElevenLabs call on your behalf.",
                            accent = RewardsPalette.Sky
                        )
                    }

                    statusMessage?.let { message ->
                        item {
                            TrailSectionCard(title = "Status", accent = RewardsPalette.Gold) {
                                Text(message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        TrailSectionCard(title = "Compose a relay", accent = RewardsPalette.Sky) {
                            Box {
                                OutlinedTextField(
                                    value = selectedContact?.displayName ?: "Choose a saved contact",
                                    onValueChange = { },
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    label = { Text("Saved contacts") },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { contactMenuExpanded = !contactMenuExpanded }) {
                                            Icon(Icons.Default.Route, contentDescription = "Toggle contacts")
                                        }
                                    }
                                )
                                DropdownMenu(
                                    expanded = contactMenuExpanded,
                                    onDismissRequest = { contactMenuExpanded = false }
                                ) {
                                    contacts.forEach { contact ->
                                        DropdownMenuItem(
                                            text = { Text("${contact.displayName} • ${contact.phoneNumber}") },
                                            onClick = {
                                                selectedContact = contact
                                                manualRecipientName = contact.displayName
                                                manualRecipientPhone = contact.phoneNumber
                                                contactMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = manualRecipientName,
                                onValueChange = { manualRecipientName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Recipient name") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = manualRecipientPhone,
                                onValueChange = { manualRecipientPhone = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Recipient phone number") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = relayMessage,
                                onValueChange = { relayMessage = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                label = { Text("What should the agent say?") }
                            )

                            PermissionRow("Share last known location", shareLocation) { shareLocation = it }
                            PermissionRow("Share real name", shareRealName) { shareRealName = it }
                            PermissionRow("Share callback number", shareCallback) { shareCallback = it }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        vm.queueVoiceRelay(
                                            recipientName = manualRecipientName.ifBlank { selectedContact?.displayName.orEmpty() },
                                            recipientPhone = manualRecipientPhone,
                                            messageBody = relayMessage,
                                            shareLocation = shareLocation,
                                            shareRealName = shareRealName,
                                            shareCallback = shareCallback
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = manualRecipientPhone.isNotBlank() && relayMessage.isNotBlank()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Text("Queue offline")
                                }
                                OutlinedButton(onClick = vm::syncVoiceRelayNow, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Text("Try delivery")
                                }
                            }
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Relay queue",
                            subtitle = "Every job below is a signed claim waiting for an online carrier or a call completion event."
                        )
                    }
                    if (relayJobs.isEmpty()) {
                        item {
                            Text(
                                "No relay jobs yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(relayJobs) { job ->
                            RelayJobCard(job)
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Replies",
                            subtitle = "When a called recipient answers and leaves a message back, it lands here for the original hiker."
                        )
                    }
                    if (inbox.isEmpty()) {
                        item {
                            Text(
                                "No inbound replies yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(inbox) { item ->
                            TrailListRow(
                                title = item.senderLabel,
                                subtitle = item.messageSummary,
                                icon = Icons.Default.MarkEmailRead,
                                accent = RewardsPalette.Gold
                            )
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Nearby hikers",
                            subtitle = "BLE peers can carry your queued relay jobs if they later regain service."
                        )
                    }
                    if (devices.isEmpty()) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = vm::startScan, modifier = Modifier.weight(1f)) {
                                    Text("Start scan")
                                }
                                OutlinedButton(onClick = vm::stopScan, modifier = Modifier.weight(1f)) {
                                    Text("Stop")
                                }
                            }
                        }
                    } else {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = vm::startScan, modifier = Modifier.weight(1f)) {
                                    Text("Refresh scan")
                                }
                                OutlinedButton(onClick = vm::stopScan, modifier = Modifier.weight(1f)) {
                                    Text("Stop")
                                }
                            }
                        }
                        items(devices.toList()) { device ->
                            TrailListRow(
                                title = device,
                                subtitle = "Available as a potential relay carrier",
                                icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                                accent = RewardsPalette.Sky
                            )
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Relay log",
                            subtitle = "Transport-level BLE activity and sync traces."
                        )
                    }
                    items(log) { entry ->
                        TrailListRow(
                            title = entry,
                            subtitle = "Mesh transport event",
                            icon = Icons.Default.Route,
                            accent = RewardsPalette.Forest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun RelayJobCard(job: RelayJobIntent) {
    TrailListRow(
        title = job.recipientName.ifBlank { "Voice relay ${job.jobId.take(8)}" },
        subtitle = "${job.status.replace('_', ' ')} • ${job.recipientPhoneNumber.ifBlank { "recipient hidden" }}",
        icon = Icons.Default.Phone,
        accent = when {
            job.status.contains("fulfilled") -> RewardsPalette.Forest
            job.status.contains("calling") -> RewardsPalette.Gold
            else -> RewardsPalette.Sky
        }
    )
}
