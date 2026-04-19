package fyi.acmc.trailkarma.ui.ble

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.ble.BleRepository
import fyi.acmc.trailkarma.ble.BleRepositoryHolder
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayInboxMessage
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailScreenHeader
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.OperationStateTone
import fyi.acmc.trailkarma.ui.feedback.OperationStepState
import fyi.acmc.trailkarma.ui.feedback.OperationStepUi
import fyi.acmc.trailkarma.ui.feedback.OperationUiState
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
import fyi.acmc.trailkarma.ui.feedback.TrailOperationCard
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val rewardsRepository = RewardsRepository(app, db)
    private val userRepository = UserRepository(app, db.userDao())
    private val networkUtil = NetworkUtil(app)

    val repo = BleRepositoryHolder.getInstance(app)
    val nearbyDevices = repo.nearbyDevices
    val syncingPeer = repo.syncingPeer
    val log = repo.eventLog
    val relayJobs = db.relayJobIntentDao().getAll()
    val isOnline = networkUtil.isOnline

    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val contacts = _contacts
    private val _inbox = MutableStateFlow<List<RelayInboxMessage>>(emptyList())
    val inbox = _inbox
    private val _operation = MutableStateFlow<OperationUiState?>(null)
    val operation = _operation
    private val _celebration = MutableStateFlow<String?>(null)
    val celebration = _celebration
    private val _isSubmittingRelay = MutableStateFlow(false)
    val isSubmittingRelay = _isSubmittingRelay
    private var knownBadges = emptySet<String>()
    private var announcedNearbyDevices = emptySet<String>()

    init {
        viewModelScope.launch {
            val user = userRepository.ensureLocalUser()
            _contacts.value = db.trustedContactDao().getForUser(user.userId).first()
            _inbox.value = db.relayInboxMessageDao().getForUser(user.userId).first()
            knownBadges = rewardsRepository.fetchWalletState()
                ?.badgeDetails
                ?.filter { it.earned }
                ?.map { it.label }
                ?.toSet()
                .orEmpty()
            if (networkUtil.isOnlineNow()) {
                syncVoiceRelayNow(showFeedback = false)
            }
        }
        viewModelScope.launch {
            nearbyDevices.collectLatest { devices ->
                val newDevices = devices - announcedNearbyDevices
                newDevices.forEach { device ->
                    TrailFeedbackBus.emit("New nearby hiker found: $device", FeedbackTone.Info)
                }
                announcedNearbyDevices = announcedNearbyDevices + newDevices
            }
        }
    }

    fun dismissCelebration() {
        _celebration.value = null
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
        if (_isSubmittingRelay.value) {
            TrailFeedbackBus.emit("A relay is already being prepared.", FeedbackTone.Info)
            return@launch
        }
        _isSubmittingRelay.value = true
        _operation.value = OperationUiState(
            title = "Preparing relay mission",
            message = "The message is being signed on this phone so it can survive offline carry and later settlement.",
            tone = OperationStateTone.Working,
            progress = 0.18f,
            steps = relaySteps(saved = OperationStepState.Active)
        )

        try {
            val intent = rewardsRepository.createVoiceRelayIntent(
                recipientName = recipientName,
                recipientPhoneNumber = recipientPhone,
                messageBody = messageBody,
                shareLocation = shareLocation,
                shareRealName = shareRealName,
                shareCallbackNumber = shareCallback
            )

            if (intent == null) {
                _operation.value = OperationUiState(
                    title = "Relay creation failed",
                    message = "The app could not create the relay intent right now.",
                    tone = OperationStateTone.Error,
                    steps = relaySteps(saved = OperationStepState.Error)
                )
                TrailFeedbackBus.emit("Unable to create the relay mission.", FeedbackTone.Error)
                return@launch
            }

            TrailFeedbackBus.emit("Relay saved on this phone.", FeedbackTone.Success)

            if (networkUtil.isOnlineNow()) {
                _operation.value = OperationUiState(
                    title = "Sending now",
                    message = "You have service, so TrailKarma is opening the relay on-chain and starting the outbound call immediately.",
                    tone = OperationStateTone.Working,
                    progress = 0.42f,
                    steps = relaySteps(
                        saved = OperationStepState.Complete,
                        posted = OperationStepState.Active
                    )
                )
                syncVoiceRelayNow(focusJobId = intent.jobId, showFeedback = true)
            } else {
                _operation.value = OperationUiState(
                    title = "Queued for the mesh",
                    message = "This relay is ready to travel phone-to-phone until the first hiker with service can post it and trigger the call.",
                    tone = OperationStateTone.Success,
                    progress = 1f,
                    steps = relaySteps(
                        saved = OperationStepState.Complete,
                        mesh = OperationStepState.Active
                    )
                )
                TrailFeedbackBus.emit(
                    "Relay queued offline. The first online hiker can carry it to the call path.",
                    FeedbackTone.Info
                )
            }
        } finally {
            _isSubmittingRelay.value = false
        }
    }

    fun syncVoiceRelayNow(
        focusJobId: String? = null,
        showFeedback: Boolean = true
    ) = viewModelScope.launch {
        if (!networkUtil.isOnlineNow()) {
            _operation.value = OperationUiState(
                title = "No service yet",
                message = "TrailKarma can keep carrying relay packets offline, but blockchain posting and calling need connectivity.",
                tone = OperationStateTone.Error,
                steps = relaySteps(
                    saved = OperationStepState.Complete,
                    mesh = OperationStepState.Active
                )
            )
            if (showFeedback) {
                TrailFeedbackBus.emit("No network yet. Relay delivery will continue when a phone gets service.", FeedbackTone.Info)
            }
            return@launch
        }

        val beforeJobs = db.relayJobIntentDao().getAll().first()
        val beforeStatuses = beforeJobs.associateBy({ it.jobId }, { it.status })

        _operation.value = OperationUiState(
            title = "Syncing relay state",
            message = "Checking relay jobs, refreshing the call pipeline, moving replies through the mesh, and updating on-chain milestones.",
            tone = OperationStateTone.Working,
            progress = 0.58f,
            steps = relaySteps(
                saved = OperationStepState.Complete,
                posted = OperationStepState.Active,
                calling = OperationStepState.Pending,
                rewarded = OperationStepState.Pending
            )
        )

        rewardsRepository.openPendingVoiceRelayJobs()
        rewardsRepository.refreshVoiceRelayJobs()
        rewardsRepository.syncMeshRelayReplies()
        val newInboxItems = rewardsRepository.syncRelayInbox()

        val user = userRepository.ensureLocalUser()
        _contacts.value = db.trustedContactDao().getForUser(user.userId).first()
        _inbox.value = db.relayInboxMessageDao().getForUser(user.userId).first()

        val afterJobs = db.relayJobIntentDao().getAll().first()
        val focusJob = afterJobs.firstOrNull { it.jobId == focusJobId } ?: afterJobs.firstOrNull()
        val newlyFulfilled = afterJobs.filter { job ->
            job.status == "fulfilled" && beforeStatuses[job.jobId] != "fulfilled"
        }
        val newlyCalling = afterJobs.filter { job ->
            job.status == "calling" && beforeStatuses[job.jobId] != "calling"
        }

        val walletState = rewardsRepository.fetchWalletState()
        val currentBadges = walletState?.badgeDetails?.filter { it.earned }?.map { it.label }?.toSet().orEmpty()
        val newBadge = currentBadges.firstOrNull { it !in knownBadges }
        if (newBadge != null) {
            _celebration.value = newBadge
        }
        knownBadges = currentBadges

        if (showFeedback) {
            when {
                newlyFulfilled.isNotEmpty() -> {
                    TrailFeedbackBus.emit("Relay completed and KARMA was awarded on-chain.", FeedbackTone.Success)
                }
                newlyCalling.isNotEmpty() -> {
                    val job = newlyCalling.first()
                    val recipient = job.recipientName.ifBlank { job.recipientPhoneNumber.ifBlank { "your contact" } }
                    TrailFeedbackBus.emit("Call placed to $recipient.", FeedbackTone.Success)
                }
                focusJob?.status == "open" -> {
                    TrailFeedbackBus.emit("Relay posted on-chain and is waiting for call completion.", FeedbackTone.Info)
                }
            }
            if (newInboxItems > 0) {
                TrailFeedbackBus.emit("A returned voice reply reached your relay inbox.", FeedbackTone.Success)
            }
        }

        _operation.value = focusJob?.toOperationState() ?: OperationUiState(
            title = "Relay state refreshed",
            message = "Your relay queue, inbox, and blockchain milestones are up to date.",
            tone = OperationStateTone.Success,
            progress = 1f,
            steps = relaySteps(
                saved = OperationStepState.Complete,
                posted = OperationStepState.Complete,
                calling = OperationStepState.Complete,
                rewarded = OperationStepState.Complete
            )
        )
    }

    private fun relaySteps(
        saved: OperationStepState = OperationStepState.Pending,
        mesh: OperationStepState = OperationStepState.Pending,
        posted: OperationStepState = OperationStepState.Pending,
        calling: OperationStepState = OperationStepState.Pending,
        rewarded: OperationStepState = OperationStepState.Pending
    ): List<OperationStepUi> = listOf(
        OperationStepUi("Saved on this phone", "The signed relay intent exists locally.", saved),
        OperationStepUi("Mesh carry", "Offline hikers can carry the packet until service appears.", mesh),
        OperationStepUi("Posted on-chain", "The relay mission is opened on Solana.", posted),
        OperationStepUi("Calling contact", "ElevenLabs/Twilio is delivering the message.", calling),
        OperationStepUi("Reward + reply return", "KARMA and any return message are settled back to the hiker.", rewarded),
    )

    private fun RelayJobIntent.toOperationState(): OperationUiState {
        val tone = when (status) {
            "fulfilled" -> OperationStateTone.Success
            "failed" -> OperationStateTone.Error
            "suppressed_duplicate" -> OperationStateTone.Error
            else -> OperationStateTone.Working
        }
        val message = when (status) {
            "queued_offline" -> "This relay is waiting for a connected hiker to post it and start the call."
            "open" -> "The relay is on-chain and ready for the outbound call or completion refresh."
            "calling" -> "The outbound call has been placed and is now in progress."
            "fulfilled" -> "The call completed, the relay was fulfilled, and the reward path settled."
            "failed" -> "The call path did not complete successfully."
            "suppressed_duplicate" -> "A duplicate copy of this relay was suppressed so the recipient only gets one call."
            else -> "Relay state is updating."
        }
        return OperationUiState(
            title = recipientName.ifBlank { "Relay ${jobId.take(8)}" },
            message = message,
            tone = tone,
            progress = when (status) {
                "queued_offline" -> 0.35f
                "open" -> 0.56f
                "calling" -> 0.76f
                "fulfilled", "failed", "suppressed_duplicate" -> 1f
                else -> 0.42f
            },
            steps = relaySteps(
                saved = OperationStepState.Complete,
                mesh = if (status == "queued_offline") OperationStepState.Active else OperationStepState.Complete,
                posted = when (status) {
                    "queued_offline" -> OperationStepState.Pending
                    "open", "calling", "fulfilled", "failed" -> OperationStepState.Complete
                    else -> OperationStepState.Active
                },
                calling = when (status) {
                    "calling" -> OperationStepState.Active
                    "fulfilled" -> OperationStepState.Complete
                    "failed", "suppressed_duplicate" -> OperationStepState.Error
                    else -> OperationStepState.Pending
                },
                rewarded = when (status) {
                    "fulfilled" -> OperationStepState.Complete
                    "failed", "suppressed_duplicate" -> OperationStepState.Error
                    else -> OperationStepState.Pending
                }
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen(
    onBack: () -> Unit = {},
    vm: BleViewModel = viewModel()
) {
    Log.d("BleScreen", "🔧 BleScreen() called")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun blePermissionsGranted() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    } else {
        // On Android 11, BLUETOOTH + BLUETOOTH_ADMIN are install-time permissions (always granted)
        true
    }

    var permissionsGranted by remember { mutableStateOf(blePermissionsGranted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsGranted = blePermissionsGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val devices by vm.nearbyDevices.collectAsState()
    val log by vm.log.collectAsState()
    val relayJobs by vm.relayJobs.collectAsState(initial = emptyList())
    val contacts by vm.contacts.collectAsState()
    val inbox by vm.inbox.collectAsState()
    val operation by vm.operation.collectAsState()
    val isOnline by vm.isOnline.collectAsState(initial = false)
    val celebration by vm.celebration.collectAsState()
    val isSubmittingRelay by vm.isSubmittingRelay.collectAsState()

    Log.d("BleScreen", "🖥️ rendering BleScreen with ${devices.size} devices")

    var selectedContact by remember(contacts) { mutableStateOf(contacts.firstOrNull()) }
    var manualRecipientName by remember { mutableStateOf("") }
    var manualRecipientPhone by remember { mutableStateOf(selectedContact?.phoneNumber ?: "") }
    var relayMessage by remember { mutableStateOf("") }
    var shareLocation by remember { mutableStateOf(true) }
    var shareRealName by remember { mutableStateOf(false) }
    var shareCallback by remember { mutableStateOf(true) }
    var contactMenuExpanded by remember { mutableStateOf(false) }

    TrailKarmaAppTheme {
        celebration?.let { badge ->
            AlertDialog(
                onDismissRequest = vm::dismissCelebration,
                confirmButton = {
                    TextButton(onClick = vm::dismissCelebration) {
                        Text("Keep exploring")
                    }
                },
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = RewardsPalette.Gold)
                        Text("Collectible unlocked")
                    }
                },
                text = {
                    Text("You earned $badge. TrailKarma recorded the reward on-chain and added the collectible to your account.")
                }
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Relay Hub")
                            Text(
                                "Queue voice calls offline, carry them over BLE, and settle the reward path when service appears.",
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
                        IconButton(onClick = { vm.syncVoiceRelayNow(showFeedback = true) }) {
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
                            subtitle = if (isOnline) {
                                "You have service right now, so sending a relay will immediately open the mission on-chain and start the outbound call."
                            } else {
                                "You are offline, so the app stores your signed relay and lets the first online hiker trigger the call on your behalf."
                            },
                            accent = RewardsPalette.Sky
                        )
                    }

                    operation?.let { state ->
                        item { TrailOperationCard(state = state) }
                    }

                    item {
                        TrailSectionCard(title = "Compose a relay", accent = RewardsPalette.Sky) {
                            Box {
                                OutlinedTextField(
                                    value = selectedContact?.displayName ?: "Choose a saved contact",
                                    onValueChange = { },
                                    modifier = Modifier.fillMaxWidth(),
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
                                    enabled = manualRecipientPhone.isNotBlank() && relayMessage.isNotBlank() && !isSubmittingRelay
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                    Text(
                                        when {
                                            isSubmittingRelay -> "Preparing..."
                                            isOnline -> "Send now"
                                            else -> "Queue offline"
                                        }
                                    )
                                }
                                OutlinedButton(
                                    onClick = { vm.syncVoiceRelayNow(showFeedback = true) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Text("Refresh state")
                                }
                            }
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Relay queue",
                            subtitle = "Every mission below shows where it is in the local-save, mesh, call, and blockchain path."
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
                            subtitle = "When a recipient leaves a return message, it arrives here and can keep traveling over the mesh."
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
                                subtitle = "${item.messageSummary} • ${item.status.replace('_', ' ')}",
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
                    if (!permissionsGranted) {
                        item { BlePermissionBanner(context) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = vm::startScan,
                                modifier = Modifier.weight(1f),
                                enabled = permissionsGranted
                            ) {
                                Text(if (devices.isEmpty()) "Start scan" else "Refresh scan")
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
private fun BlePermissionBanner(context: Context) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RewardsPalette.Clay.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = RewardsPalette.Clay
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Bluetooth permission required",
                    style = MaterialTheme.typography.titleSmall,
                    color = RewardsPalette.Clay
                )
                Text(
                    "Grant \"Nearby devices\" access so this device can be detected by others on the trail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            ) {
                Text("Settings")
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
    TrailSectionCard(
        title = job.recipientName.ifBlank { "Voice relay ${job.jobId.take(8)}" },
        accent = when (job.status) {
            "fulfilled" -> RewardsPalette.Forest
            "calling" -> RewardsPalette.Gold
            "failed" -> RewardsPalette.Clay
            else -> RewardsPalette.Sky
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(job.status.replace('_', ' ')) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when (job.status) {
                        "fulfilled" -> RewardsPalette.Forest.copy(alpha = 0.12f)
                        "calling" -> RewardsPalette.Gold.copy(alpha = 0.14f)
                        "failed" -> RewardsPalette.Clay.copy(alpha = 0.14f)
                        else -> RewardsPalette.Sky.copy(alpha = 0.12f)
                    },
                    labelColor = when (job.status) {
                        "fulfilled" -> RewardsPalette.Forest
                        "calling" -> RewardsPalette.Ink
                        "failed" -> RewardsPalette.Clay
                        else -> RewardsPalette.Sky
                    }
                )
            )
            AssistChip(
                onClick = {},
                label = { Text(if (job.source == "self") "from you" else "carried relay") },
            )
        }
        if (job.status == "calling") {
            Text(
                "Call placed. TrailKarma is speaking to the recipient now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            job.recipientPhoneNumber.ifBlank { "Recipient hidden" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (job.messageBody.isNotBlank()) {
            Text(job.messageBody, style = MaterialTheme.typography.bodyMedium)
        }
        if (job.transcriptSummary?.isNotBlank() == true) {
            Text(
                "Call summary: ${job.transcriptSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            job.openedTxSignature?.let {
                Text(
                    "Open tx ${it.take(10)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            job.fulfilledTxSignature?.let {
                Text(
                    "Reward tx ${it.take(10)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RelayJobTimeline(job)
    }
}

@Composable
private fun RelayJobTimeline(job: RelayJobIntent) {
    val steps = listOf(
        "Saved locally" to OperationStepState.Complete,
        "Mesh carry" to if (job.status == "queued_offline") OperationStepState.Active else OperationStepState.Complete,
        "Posted on-chain" to when (job.status) {
            "queued_offline" -> OperationStepState.Pending
            else -> OperationStepState.Complete
        },
        "Calling" to when (job.status) {
            "calling" -> OperationStepState.Active
            "fulfilled" -> OperationStepState.Complete
            "failed" -> OperationStepState.Error
            else -> OperationStepState.Pending
        },
        "Rewarded" to when (job.status) {
            "fulfilled" -> OperationStepState.Complete
            "failed" -> OperationStepState.Error
            else -> OperationStepState.Pending
        }
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { (label, state) ->
            Text(
                text = "\u2022 $label",
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    OperationStepState.Complete -> RewardsPalette.Forest
                    OperationStepState.Active -> RewardsPalette.Sky
                    OperationStepState.Error -> RewardsPalette.Clay
                    OperationStepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (state == OperationStepState.Active) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
