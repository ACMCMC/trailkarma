package fyi.acmc.trailkarma.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fyi.acmc.trailkarma.ui.design.TrailHeroCard
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.design.TrailListRow
import fyi.acmc.trailkarma.ui.design.TrailSectionCard
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    TrailKarmaAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("About TrailKarma") },
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
                            colors = listOf(Color(0xFFFFF8EE), MaterialTheme.colorScheme.background)
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
                            title = "A hiking network that rewards helpful behavior",
                            subtitle = "TrailKarma combines offline-first reporting, BLE-based relay, biodiversity capture, and Solana rewards so hikers can help one another and leave a richer trail record behind.",
                            accent = RewardsPalette.Forest,
                            supporting = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TrailInfoChip(
                                        icon = Icons.Default.Route,
                                        label = "Trail safety + social good",
                                        accent = RewardsPalette.Gold
                                    )
                                    TrailInfoChip(
                                        icon = Icons.Default.AutoAwesome,
                                        label = "On-chain KARMA and collectibles",
                                        accent = RewardsPalette.Sky
                                    )
                                }
                            }
                        )
                    }

                    item {
                        TrailSectionCard(title = "What the demo shows", accent = RewardsPalette.Sky) {
                            TrailListRow(
                                title = "Offline trail reporting",
                                subtitle = "Hazards, water, and species reports save locally first, then sync later.",
                                icon = Icons.Default.Route,
                                accent = RewardsPalette.Clay
                            )
                            TrailListRow(
                                title = "BLE carrier mesh",
                                subtitle = "Nearby hikers can carry delayed help messages when another device regains service.",
                                icon = Icons.Default.Bluetooth,
                                accent = RewardsPalette.Sky
                            )
                            TrailListRow(
                                title = "Biodiversity field capture",
                                subtitle = "On-device audio inference and optional photo verification turn sightings into collectible-ready records.",
                                icon = Icons.Default.Mic,
                                accent = RewardsPalette.Moss
                            )
                            TrailListRow(
                                title = "Solana settlement layer",
                                subtitle = "KARMA, relay uniqueness, and achievement ownership are recorded on Devnet without requiring hikers to hold SOL.",
                                icon = Icons.Default.AutoAwesome,
                                accent = RewardsPalette.Gold
                            )
                        }
                    }

                    item {
                        TrailSectionCard(title = "Why the architecture works", accent = RewardsPalette.Gold) {
                            Text(
                                "Real events happen in the field. TrailKarma keeps those events off-chain until the app, backend, or external provider can attest to them. Solana then handles the part it is actually good at: anti-duplication, reward settlement, first-fulfiller logic, and ownership of KARMA and collectibles.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item {
                        TrailSectionCard(title = "Team", accent = RewardsPalette.Moss) {
                            TrailListRow(
                                title = "Aldan Creo",
                                subtitle = "Android app, contact tracing, database sync",
                                icon = Icons.Default.Route,
                                accent = RewardsPalette.Forest
                            )
                            TrailListRow(
                                title = "Qianqian Zhang",
                                subtitle = "Online data processing, hazard prediction, GIS data",
                                icon = Icons.Default.Forest,
                                accent = RewardsPalette.Moss
                            )
                            TrailListRow(
                                title = "Edith Gu",
                                subtitle = "Website and cloud deployment",
                                icon = Icons.Default.Bluetooth,
                                accent = RewardsPalette.Sky
                            )
                            TrailListRow(
                                title = "Suraj Ranganath",
                                subtitle = "Blockchain rewards and ML training",
                                icon = Icons.Default.AutoAwesome,
                                accent = RewardsPalette.Gold
                            )
                        }
                    }
                }
            }
        }
    }
}
