package fyi.acmc.trailkarma.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import java.time.Instant

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, vm: LoginViewModel = viewModel()) {
    var trailName by remember { mutableStateOf("") }
    var realName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var defaultRelayPhone by remember { mutableStateOf("+18582148608") }
    var shareLocation by remember { mutableStateOf(true) }
    var shareRealName by remember { mutableStateOf(false) }
    var shareCallback by remember { mutableStateOf(true) }

    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TrailHeroCard(
                    title = "Set up your trail identity",
                    subtitle = "Create one local hiker profile, keep your wallet app-managed, and choose what the relay agent can share when you are offline.",
                    accent = RewardsPalette.Forest,
                    supporting = {
                        TrailInfoChip(
                            icon = Icons.Default.Forest,
                            label = "Offline-first profile setup",
                            accent = RewardsPalette.Gold
                        )
                    }
                )

                TrailSectionCard(title = "Hiker profile") {
                    OutlinedTextField(
                        value = trailName,
                        onValueChange = { trailName = it },
                        label = { Text("Trail name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = realName,
                        onValueChange = { realName = it },
                        label = { Text("Real name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Your callback number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TrailSectionCard(title = "Trusted relay contact", accent = RewardsPalette.Sky) {
                    Text(
                        "The first saved trusted contact is used as the suggested recipient for voice relay messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = defaultRelayPhone,
                        onValueChange = { defaultRelayPhone = it },
                        label = { Text("Default recipient phone") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TrailSectionCard(title = "Sharing defaults", accent = RewardsPalette.Gold) {
                    PermissionToggleRow(
                        title = "Share last known location",
                        checked = shareLocation,
                        onCheckedChange = { shareLocation = it }
                    )
                    PermissionToggleRow(
                        title = "Share real name in relay calls",
                        checked = shareRealName,
                        onCheckedChange = { shareRealName = it }
                    )
                    PermissionToggleRow(
                        title = "Share callback number",
                        checked = shareCallback,
                        onCheckedChange = { shareCallback = it }
                    )
                }

                Button(
                    onClick = {
                        if (trailName.isNotBlank()) {
                            vm.completeProfileSetup(
                                displayName = trailName.trim(),
                                realName = realName.trim(),
                                phoneNumber = phoneNumber.trim(),
                                defaultRelayPhoneNumber = defaultRelayPhone.trim(),
                                contacts = listOf(
                                    TrustedContact(
                                        userId = "",
                                        displayName = "Suraj",
                                        phoneNumber = defaultRelayPhone.trim(),
                                        relationshipLabel = "Trusted contact",
                                        isDefault = true,
                                        createdAt = Instant.now().toString()
                                    )
                                ),
                                onComplete = onLoginSuccess
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("Launch TrailKarma")
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
