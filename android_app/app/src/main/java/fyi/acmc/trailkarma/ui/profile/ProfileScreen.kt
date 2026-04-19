package fyi.acmc.trailkarma.ui.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailScreenHeader
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class BiodiversityProfileStats(
    val savedCount: Int = 0,
    val verifiedCount: Int = 0,
    val pendingCollectibles: Int = 0,
    val distinctLabels: Int = 0
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val userRepo = UserRepository(app, db.userDao())
    private val rewardsRepository = RewardsRepository(app, db)

    private val _user = MutableStateFlow<User?>(null)
    val user = _user
    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val contacts = _contacts
    val saving = MutableStateFlow(false)
    private val _biodiversityStats = MutableStateFlow(BiodiversityProfileStats())
    val biodiversityStats = _biodiversityStats

    init {
        refresh()
        viewModelScope.launch {
            db.biodiversityContributionDao().getSaved().collectLatest { contributions ->
                _biodiversityStats.value = BiodiversityProfileStats(
                    savedCount = contributions.size,
                    verifiedCount = contributions.count { it.collectibleStatus == "verified" },
                    pendingCollectibles = contributions.count { it.collectibleStatus == "pending_verification" },
                    distinctLabels = contributions.mapNotNull { it.finalLabel }.distinct().size
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = userRepo.ensureLocalUser()
            _user.value = current
            _contacts.value = db.trustedContactDao().getForUser(current.userId).first()
        }
    }

    fun saveProfile(
        displayName: String,
        realName: String,
        phoneNumber: String,
        defaultRelayPhoneNumber: String
    ) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val current = userRepo.ensureLocalUser()
            val updated = current.copy(
                displayName = displayName.trim().ifBlank { current.displayName },
                realName = realName.trim().ifBlank { null },
                phoneNumber = phoneNumber.trim(),
                defaultRelayPhoneNumber = defaultRelayPhoneNumber.trim()
            )
            userRepo.saveUser(updated)

            val existingContacts = db.trustedContactDao().getForUser(updated.userId).first()
            val defaultContact = existingContacts.firstOrNull { it.isDefault }
            if (updated.defaultRelayPhoneNumber.isNotBlank()) {
                db.trustedContactDao().insert(
                    (defaultContact ?: TrustedContact(
                        contactId = UUID.randomUUID().toString(),
                        userId = updated.userId,
                        displayName = "Primary relay contact",
                        phoneNumber = updated.defaultRelayPhoneNumber,
                        relationshipLabel = "Relay destination",
                        isDefault = true,
                        createdAt = Instant.now().toString()
                    )).copy(
                        userId = updated.userId,
                        phoneNumber = updated.defaultRelayPhoneNumber,
                        displayName = defaultContact?.displayName ?: "Primary relay contact",
                        relationshipLabel = defaultContact?.relationshipLabel ?: "Relay destination",
                        isDefault = true
                    )
                )
            }

            _user.value = updated
            _contacts.value = db.trustedContactDao().getForUser(updated.userId).first()
            rewardsRepository.syncCurrentUserRegistration()
            TrailFeedbackBus.emit("Profile saved. Relay identity updated.", FeedbackTone.Success)
            saving.value = false
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    onOpenContact: () -> Unit,
    onOpenTracing: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val user by vm.user.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val saving by vm.saving.collectAsState()
    val biodiversityStats by vm.biodiversityStats.collectAsState()

    var displayName by remember(user?.userId) { mutableStateOf(user?.displayName.orEmpty()) }
    var realName by remember(user?.userId) { mutableStateOf(user?.realName.orEmpty()) }
    var phoneNumber by remember(user?.userId) { mutableStateOf(user?.phoneNumber.orEmpty()) }
    var defaultRelayPhone by remember(user?.userId) { mutableStateOf(user?.defaultRelayPhoneNumber.orEmpty()) }

    LaunchedEffect(user?.displayName, user?.realName, user?.phoneNumber, user?.defaultRelayPhoneNumber) {
        displayName = user?.displayName.orEmpty()
        realName = user?.realName.orEmpty()
        phoneNumber = user?.phoneNumber.orEmpty()
        defaultRelayPhone = user?.defaultRelayPhoneNumber.orEmpty()
    }

    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
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
                            colors = listOf(Color(0xFFFFF7EC), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TrailHeroCard(
                            title = user?.displayName ?: "Trail profile",
                            subtitle = if (user?.solanaRegistered == true) {
                                "Your local identity is connected to the rewards ledger and ready for relay jobs."
                            } else {
                                "Your hiker identity is ready locally and will register with Solana once the network path is available."
                            },
                            accent = RewardsPalette.Pine,
                            supporting = {
                                TrailInfoChip(
                                    icon = Icons.Default.Sync,
                                    label = if (user?.solanaRegistered == true) "Wallet registered" else "Wallet pending sync",
                                    accent = if (user?.solanaRegistered == true) RewardsPalette.Forest else RewardsPalette.Gold
                                )
                            }
                        )
                    }

                    item {
                        TrailSectionCard(title = "Biodiversity ledger", accent = RewardsPalette.Moss) {
                            TrailListRow(
                                title = "${biodiversityStats.savedCount} saved field records",
                                subtitle = "Observer-linked biodiversity events that can be exported to partners later.",
                                icon = Icons.Default.Mic,
                                accent = RewardsPalette.Moss
                            )
                            TrailListRow(
                                title = "${biodiversityStats.pendingCollectibles} collectible checks pending",
                                subtitle = "Eligible audio contributions waiting on verification and blockchain settlement.",
                                icon = Icons.Default.Sync,
                                accent = RewardsPalette.Gold
                            )
                            TrailListRow(
                                title = "${biodiversityStats.verifiedCount} verified collectibles",
                                subtitle = "${biodiversityStats.distinctLabels} unique taxa recorded by this profile so far.",
                                icon = Icons.Default.Verified,
                                accent = RewardsPalette.Forest
                            )
                        }
                    }

                    item {
                        TrailSectionCard(title = "Trail identity", accent = RewardsPalette.Forest) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Trail name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = realName,
                                onValueChange = { realName = it },
                                label = { Text("Real name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Callback number") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = defaultRelayPhone,
                                onValueChange = { defaultRelayPhone = it },
                                label = { Text("Default relay recipient") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    vm.saveProfile(
                                        displayName = displayName,
                                        realName = realName,
                                        phoneNumber = phoneNumber,
                                        defaultRelayPhoneNumber = defaultRelayPhone
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = !saving && displayName.isNotBlank()
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Save profile")
                                }
                            }
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Trusted contacts",
                            subtitle = "These contacts anchor the relay flow and are suggested first when you queue a call."
                        )
                    }

                    if (contacts.isEmpty()) {
                        item {
                            Text(
                                "No trusted contacts saved yet. The default relay recipient will appear here once you save it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(contacts) { contact ->
                            TrailListRow(
                                title = contact.displayName,
                                subtitle = "${contact.phoneNumber}${contact.relationshipLabel?.let { " • $it" } ?: ""}",
                                icon = Icons.Default.Phone,
                                accent = if (contact.isDefault) RewardsPalette.Gold else RewardsPalette.Sky
                            )
                        }
                    }

                    item {
                        TrailScreenHeader(
                            title = "Utilities",
                            subtitle = "Debug, support, and project information."
                        )
                    }
                    item {
                        TrailListRow("Sync status", "Inspect local and cloud state", Icons.Default.Sync, accent = RewardsPalette.Sky) {
                            IconButton(onClick = onOpenSyncStatus) { Icon(Icons.Default.Route, contentDescription = null) }
                        }
                    }
                    item {
                        TrailListRow("Contact tracing", "Review nearby peer exchange status", Icons.Default.Share, accent = RewardsPalette.Moss) {
                            IconButton(onClick = onOpenTracing) { Icon(Icons.Default.Route, contentDescription = null) }
                        }
                    }
                    item {
                        TrailListRow("About TrailKarma", "Why this app exists and who built it", Icons.Default.Info, accent = RewardsPalette.Forest) {
                            IconButton(onClick = onOpenAbout) { Icon(Icons.Default.Route, contentDescription = null) }
                        }
                    }
                    item {
                        TrailListRow("Contact & support", "Reach the team behind the build", Icons.Default.Mail, accent = RewardsPalette.Clay) {
                            IconButton(onClick = onOpenContact) { Icon(Icons.Default.Route, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }
}
