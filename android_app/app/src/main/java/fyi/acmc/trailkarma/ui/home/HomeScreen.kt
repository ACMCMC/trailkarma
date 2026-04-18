package fyi.acmc.trailkarma.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReport: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBle: () -> Unit,
    onNavigateToMap: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val location by vm.latestLocation.collectAsState(initial = null)
    val syncStatus by vm.syncStatus.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("TrailKarma") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Trail Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    if (location != null) {
                        Text("${String.format("%.5f", location!!.lat)}, ${String.format("%.5f", location!!.lng)}")
                    } else {
                        Text("Location: waiting...", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(24.dp))
            NavButton("Report Hazard / Water / Species", Icons.Default.Warning, onNavigateToReport)
            Spacer(Modifier.height(12.dp))
            NavButton("My Reports", Icons.Default.List, onNavigateToHistory)
            Spacer(Modifier.height(12.dp))
            NavButton("BLE Nearby Hikers", Icons.Default.Bluetooth, onNavigateToBle)
            Spacer(Modifier.height(12.dp))
            NavButton("Trail Map", Icons.Default.Map, onNavigateToMap)
        }
    }
}

@Composable
private fun NavButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
