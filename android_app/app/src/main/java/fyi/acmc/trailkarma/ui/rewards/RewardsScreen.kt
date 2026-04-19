package fyi.acmc.trailkarma.ui.rewards

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.api.BadgeStatusResponse
import fyi.acmc.trailkarma.api.RewardActivityItemResponse
import fyi.acmc.trailkarma.api.WalletStateResponse
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.repository.RewardsRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RewardsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val rewardsRepository = RewardsRepository(app, db)

    val reports = db.trailReportDao().getAll()
    val relayJobs = db.relayJobIntentDao().getAll()

    private val _wallet = kotlinx.coroutines.flow.MutableStateFlow<WalletStateResponse?>(null)
    val wallet = _wallet

    private val _activity = kotlinx.coroutines.flow.MutableStateFlow<List<RewardActivityItemResponse>>(emptyList())
    val activity = _activity

    private val _loading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val loading = _loading

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message = _message

    private val _celebrationBadge = kotlinx.coroutines.flow.MutableStateFlow<BadgeStatusResponse?>(null)
    val celebrationBadge = _celebrationBadge

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val previousOwned = _wallet.value?.badgeDetails?.filter { it.earned }.orEmpty()
            val walletState = rewardsRepository.fetchWalletState() ?: rewardsRepository.syncCurrentUserRegistration()
            val activityItems = rewardsRepository.fetchRewardsActivity()
            _wallet.value = walletState
            _activity.value = activityItems
            _loading.value = false

            val nextOwned = walletState?.badgeDetails?.filter { it.earned }.orEmpty()
            val newlyEarned = nextOwned.firstOrNull { candidate ->
                previousOwned.none { it.code == candidate.code }
            }
            if (newlyEarned != null && previousOwned.isNotEmpty()) {
                _celebrationBadge.value = newlyEarned
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun dismissCelebration() {
        _celebrationBadge.value = null
    }

    fun sendTip(recipientWallet: String, amount: Int) {
        viewModelScope.launch {
            if (recipientWallet.isBlank()) {
                _message.value = "Enter a wallet address to send KARMA."
                return@launch
            }

            val prepared = rewardsRepository.prepareTip(recipientWallet.trim(), amount)
            if (prepared == null) {
                _message.value = "Unable to prepare this tip right now."
                return@launch
            }

            val ok = rewardsRepository.submitTip(prepared)
            _message.value = if (ok) {
                refresh()
                "Sent $amount KARMA."
            } else {
                "Tip submission failed."
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    onBack: () -> Unit,
    onOpenRelayMissions: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: RewardsViewModel = viewModel()
) {
    val wallet by vm.wallet.collectAsState()
    val activity by vm.activity.collectAsState()
    val loading by vm.loading.collectAsState()
    val reports by vm.reports.collectAsState(initial = emptyList())
    val relayJobs by vm.relayJobs.collectAsState(initial = emptyList())
    val message by vm.message.collectAsState()
    val celebrationBadge by vm.celebrationBadge.collectAsState()
    val snackbars = remember { SnackbarHostState() }

    var tipDialogOpen by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<BadgeStatusResponse?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    TrailKarmaRewardsTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Rewards", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "KARMA, collectibles, and relay wins",
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
                        IconButton(onClick = vm::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbars) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF7EC),
                                Color(0xFFF5F0E6),
                                Color(0xFFEFE4D0)
                            )
                        )
                    )
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        RewardsHeroCard(
                            wallet = wallet,
                            loading = loading,
                            localReports = reports,
                            relayJobs = relayJobs
                        )
                    }

                    item {
                        ActionDeck(
                            onTip = { tipDialogOpen = true },
                            onRelayMissions = onOpenRelayMissions,
                            onHistory = onOpenHistory
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Collectibles",
                            subtitle = "Each achievement is minted on Devnet and tied to real trail help."
                        )
                    }

                    if (wallet?.badgeDetails.isNullOrEmpty()) {
                        item {
                            RewardEmptyState(
                                title = "Your first collectible is close",
                                description = "Submit a verified report or finish a relay mission to start the collection."
                            )
                        }
                    } else {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(wallet?.badgeDetails.orEmpty(), key = { it.code }) { badge ->
                                    CollectibleBadgeCard(
                                        badge = badge,
                                        onClick = { selectedBadge = badge }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = "Progress",
                            subtitle = "Track the milestones that unlock stronger social proof on chain."
                        )
                    }

                    item {
                        ProgressOverview(
                            wallet = wallet,
                            reports = reports,
                            relayJobs = relayJobs
                        )
                    }

                    item {
                        SectionHeader(
                            title = "Recent activity",
                            subtitle = "Everything below is either settled on chain or queued for it."
                        )
                    }

                    if (activity.isEmpty()) {
                        item {
                            RewardEmptyState(
                                title = "No reward activity yet",
                                description = "Once claims, relay rewards, or tips happen, they will show up here."
                            )
                        }
                    } else {
                        items(activity, key = { it.id }) { item ->
                            RewardActivityRow(item = item)
                        }
                    }
                }

                if (celebrationBadge != null) {
                    CollectibleSpotlightDialog(
                        badge = celebrationBadge!!,
                        onDismiss = vm::dismissCelebration
                    )
                }
            }
        }
    }

    if (tipDialogOpen) {
        TipKarmaDialog(
            onDismiss = { tipDialogOpen = false },
            onSend = { walletAddress, amount ->
                tipDialogOpen = false
                vm.sendTip(walletAddress, amount)
            }
        )
    }

    if (selectedBadge != null) {
        CollectibleSpotlightDialog(
            badge = selectedBadge!!,
            onDismiss = { selectedBadge = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RewardsHeroCard(
    wallet: WalletStateResponse?,
    loading: Boolean,
    localReports: List<TrailReport>,
    relayJobs: List<RelayJobIntent>
) {
    val badgeCount = wallet?.rewardStats?.badgeCount ?: wallet?.badgeDetails?.count { it.earned } ?: 0
    val verifiedCount = wallet?.rewardStats?.verifiedContributionCount ?: localReports.count { it.rewardClaimed }
    val openRelayCount = relayJobs.count { it.status == "open" || it.status == "pending" }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            RewardsPalette.Forest,
                            RewardsPalette.Pine,
                            Color(0xFF6C4B31)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            wallet?.displayName ?: "Trail account",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            if (wallet?.walletPublicKey.isNullOrBlank()) {
                                "Register to activate your wallet"
                            } else {
                                "Wallet ${shortWallet(wallet!!.walletPublicKey)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = RewardsPalette.Gold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Devnet collectible wallet",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "KARMA",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        if (loading && wallet == null) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(24.dp),
                                color = RewardsPalette.Gold,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                wallet?.karmaBalance ?: "0",
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "$badgeCount collectibles",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            "$verifiedCount verified trail actions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RewardChip(
                        icon = Icons.Default.Route,
                        label = "$openRelayCount relay missions in play"
                    )
                    RewardChip(
                        icon = Icons.Default.Forest,
                        label = "${wallet?.rewardStats?.speciesCount ?: 0} biodiversity reports"
                    )
                    RewardChip(
                        icon = Icons.Default.Shield,
                        label = "${wallet?.rewardStats?.hazardCount ?: 0} hazard alerts"
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = RewardsPalette.Gold, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun ActionDeck(
    onTip: () -> Unit,
    onRelayMissions: () -> Unit,
    onHistory: () -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                title = "Tip KARMA",
                subtitle = "Thank another hiker instantly",
                icon = Icons.AutoMirrored.Filled.Send,
                accent = RewardsPalette.Gold,
                modifier = Modifier.weight(1f),
                onClick = onTip
            )
            ActionCard(
                title = "Relay missions",
                subtitle = "Create or fulfill delayed call tasks",
                icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                accent = RewardsPalette.Sky,
                modifier = Modifier.weight(1f),
                onClick = onRelayMissions
            )
        }

        Spacer(Modifier.height(12.dp))

        ActionCard(
            title = "Report ledger",
            subtitle = "Review claims, verification, and reward receipts",
            icon = Icons.Default.LocalActivity,
            accent = RewardsPalette.Clay,
            modifier = Modifier.fillMaxWidth(),
            onClick = onHistory
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.TrendingFlat,
                contentDescription = null,
                tint = accent
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollectibleBadgeCard(
    badge: BadgeStatusResponse,
    onClick: () -> Unit
) {
    val accent = colorFromHex(badge.accentHex)
    val background = if (badge.earned) {
        Brush.linearGradient(
            colors = listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f), RewardsPalette.Card)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFF3EBDD), Color(0xFFE7DCC8))
        )
    }

    Card(
        modifier = Modifier
            .width(242.dp)
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, accent.copy(alpha = if (badge.earned) 0.35f else 0.16f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (badge.earned) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = badge.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (badge.earned) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    if (badge.earned) "COLLECTED" else "LOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badge.earned) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = badgeIcon(badge.code),
                    contentDescription = null,
                    tint = if (badge.earned) Color.White else accent,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    badge.label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (badge.earned) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (badge.earned) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { badgeProgress(badge) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (badge.earned) Color.White else accent,
                    trackColor = Color.White.copy(alpha = if (badge.earned) 0.22f else 0.5f)
                )
                Text(
                    "${badge.currentCount} / ${badge.targetCount} progress",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badge.earned) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProgressOverview(
    wallet: WalletStateResponse?,
    reports: List<TrailReport>,
    relayJobs: List<RelayJobIntent>
) {
    val stats = wallet?.rewardStats
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressMetricCard(
                title = "Verified actions",
                value = "${stats?.verifiedContributionCount ?: reports.count { it.rewardClaimed }}",
                subtitle = "Real contributions settled into KARMA",
                accent = RewardsPalette.Forest,
                modifier = Modifier.weight(1f)
            )
            ProgressMetricCard(
                title = "Total earned",
                value = "${stats?.totalKarmaEarned ?: 0}",
                subtitle = "KARMA minted from verified events",
                accent = RewardsPalette.Gold,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressMetricCard(
                title = "Relay pipeline",
                value = "${relayJobs.count { it.status == "pending" || it.status == "open" }}",
                subtitle = "Missions waiting for delivery or fulfillment",
                accent = RewardsPalette.Sky,
                modifier = Modifier.weight(1f)
            )
            ProgressMetricCard(
                title = "Collectibles owned",
                value = "${stats?.badgeCount ?: wallet?.badgeDetails?.count { it.earned } ?: 0}",
                subtitle = "Non-transferable trail achievements",
                accent = RewardsPalette.Clay,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProgressMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            Text(value, style = MaterialTheme.typography.headlineLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RewardActivityRow(item: RewardActivityItemResponse) {
    val accent = when (item.kind) {
        "contribution_reward" -> RewardsPalette.Forest
        "relay_reward" -> RewardsPalette.Sky
        "badge_earned" -> RewardsPalette.Gold
        "tip_sent" -> RewardsPalette.Clay
        else -> RewardsPalette.Stone
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(activityIcon(item.kind), contentDescription = null, tint = accent)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                KarmaDeltaPill(delta = item.karmaDelta)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        formatTimestamp(item.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.txSignature?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(shortWallet(it), maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = accent.copy(alpha = 0.08f),
                            labelColor = accent,
                            leadingIconContentColor = accent
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun KarmaDeltaPill(delta: Int?) {
    if (delta == null) return

    val positive = delta >= 0
    val container = if (positive) RewardsPalette.Forest.copy(alpha = 0.12f) else RewardsPalette.Clay.copy(alpha = 0.12f)
    val content = if (positive) RewardsPalette.Forest else RewardsPalette.Clay

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container
    ) {
        Text(
            text = if (positive) "+$delta KARMA" else "$delta KARMA",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

@Composable
private fun RewardEmptyState(title: String, description: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipKarmaDialog(
    onDismiss: () -> Unit,
    onSend: (walletAddress: String, amount: Int) -> Unit
) {
    var walletAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(5) }
    val presetAmounts = listOf(5, 10, 20, 50)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onSend(walletAddress, amount) }) {
                Text("Send KARMA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Tip another hiker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Transfer KARMA from your app-managed wallet without making the recipient buy SOL.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = walletAddress,
                    onValueChange = { walletAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Recipient wallet") },
                    singleLine = true
                )
                Text("Amount", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetAmounts.forEach { value ->
                        FilterChip(
                            selected = amount == value,
                            onClick = { amount = value },
                            label = { Text("$value") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RewardsPalette.Gold.copy(alpha = 0.2f),
                                selectedLabelColor = RewardsPalette.Ink
                            )
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CollectibleSpotlightDialog(
    badge: BadgeStatusResponse,
    onDismiss: () -> Unit
) {
    val accent = colorFromHex(badge.accentHex)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (badge.earned) "Back to rewards" else "Keep climbing")
            }
        },
        title = {
            Text(
                if (badge.earned) "Collectible earned" else "Collectible preview",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (badge.earned) {
                                    listOf(accent, accent.copy(alpha = 0.72f), RewardsPalette.Ink)
                                } else {
                                    listOf(Color(0xFFF2E8D8), Color(0xFFE4D7C3))
                                }
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(26.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            badgeIcon(badge.code),
                            contentDescription = null,
                            tint = if (badge.earned) Color.White else accent,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            badge.label,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (badge.earned) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (badge.earned) "Minted to your wallet" else "Progress ${badge.currentCount}/${badge.targetCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (badge.earned) Color.White.copy(alpha = 0.84f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(badge.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Mint: ${badge.mint}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

private fun badgeProgress(badge: BadgeStatusResponse): Float {
    if (badge.targetCount <= 0) return 0f
    return (badge.currentCount.toFloat() / badge.targetCount.toFloat()).coerceIn(0f, 1f)
}

private fun activityIcon(kind: String): ImageVector = when (kind) {
    "contribution_reward" -> Icons.Default.TipsAndUpdates
    "relay_reward" -> Icons.AutoMirrored.Filled.BluetoothSearching
    "badge_earned" -> Icons.Default.Celebration
    "tip_sent" -> Icons.AutoMirrored.Filled.Send
    else -> Icons.Default.AutoAwesome
}

private fun badgeIcon(code: String): ImageVector = when (code) {
    "trail_scout" -> Icons.Default.Route
    "relay_ranger" -> Icons.AutoMirrored.Filled.BluetoothSearching
    "species_spotter" -> Icons.Default.Forest
    "water_guardian" -> Icons.Default.WaterDrop
    "hazard_herald" -> Icons.Default.Shield
    else -> Icons.Default.AutoAwesome
}

private fun colorFromHex(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrElse { RewardsPalette.Gold }

private fun shortWallet(value: String): String =
    if (value.length < 10) value else "${value.take(4)}...${value.takeLast(4)}"

private fun formatTimestamp(value: String): String =
    runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(value)
