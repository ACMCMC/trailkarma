package fyi.acmc.trailkarma.ui.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailScreenHeader
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val userRepo = UserRepository(app, db.userDao())

    private val _user = MutableStateFlow<User?>(null)
    val user = _user
    private val _contacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val contacts = _contacts

    init {
        viewModelScope.launch {
            val current = userRepo.currentUser()
            _user.value = current
            if (current != null) {
                _contacts.value = db.trustedContactDao().getForUser(current.userId).first()
            }
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
                            subtitle = user?.phoneNumber?.takeIf { it.isNotBlank() }
                                ?.let { "Callback number $it" }
                                ?: "Configure how voice relays describe and reach you.",
                            accent = RewardsPalette.Pine
                        )
                    }

                    item {
                        TrailScreenHeader(
                            title = "Trusted contacts",
                            subtitle = "These are the people you can target first when you need a relay call."
                        )
                    }

                    if (contacts.isEmpty()) {
                        item {
                            Text(
                                "No trusted contacts saved yet.",
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
