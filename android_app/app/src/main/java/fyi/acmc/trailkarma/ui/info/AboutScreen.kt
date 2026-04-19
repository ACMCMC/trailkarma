package fyi.acmc.trailkarma.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("About TrailKarma") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Text(
                    "TrailKarma v1.0",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item {
                Text(
                    "A decentralized trail reporting app built for outdoor enthusiasts to share real-time trail conditions, hazards, and wildlife sightings.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                Text(
                    "Features:",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }
            item {
                Text(
                    "• Offline-first map with real-time trail data\n• Species identification with AI\n• P2P data sync via Bluetooth\n• Decentralized architecture",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
