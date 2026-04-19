package fyi.acmc.trailkarma.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    onNavigateToCamera: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToReportDetail: (String) -> Unit = {},
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsState(initial = emptyList())
    val userLocation by vm.userLocation.collectAsState(initial = null)

    var mapView: MapView? by remember { mutableStateOf(null) }
    var currentMapStyle by remember { mutableStateOf(TileSourceFactory.USGS_TOPO) }

    val displayReports = reports

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Map View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)
                    controller.apply {
                        setZoom(13)
                        setCenter(GeoPoint(32.88, -117.24)) // San Diego PCT area
                    }
                    mapView = this
                }
            },
            update = { map ->
                map.overlays.clear()
                map.setTileSource(currentMapStyle)

                // Add report markers with custom colored icons
                displayReports.forEach { report ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(report.lat, report.lng)
                        title = report.title
                        snippet = report.description
                        icon = MarkerFactory.createMarkerDrawable(context, report.type)

                        setOnMarkerClickListener { _, _ ->
                            onNavigateToReportDetail(report.reportId)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                // Add user location marker
                val userMarker = Marker(map).apply {
                    position = userLocation?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(32.88, -117.24)
                    title = "Your Location"
                    snippet = "Current position"
                    icon = MarkerFactory.createUserMarkerDrawable(context)
                }
                map.overlays.add(userMarker)

                map.invalidate()
            }
        )

        // Top status bar (respects status bar inset)
        val trails by vm.trails.collectAsState(initial = emptyList())
        var expanded by remember { mutableStateOf(false) }
        var selectedTrail by remember { mutableStateOf<fyi.acmc.trailkarma.models.Trail?>(null) }

        LaunchedEffect(trails) {
            if (selectedTrail == null && trails.isNotEmpty()) {
                selectedTrail = trails.first()
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(12.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedTrail?.name ?: "Select a Trail", style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Trail")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (trails.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No trails synced yet", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { expanded = false }
                        )
                    } else {
                        trails.forEach { trail ->
                            DropdownMenuItem(
                                text = { Text(trail.name, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    selectedTrail = trail
                                    expanded = false
                                    // In a real app, this would trigger vm.loadTrailData(trail)
                                }
                            )
                        }
                    }
                }

                Text(
                    userLocation?.let { "${String.format("%.4f", it.lat)}°N, ${String.format("%.4f", it.lng)}°W" } ?: "32.88°N, 117.24°W",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp
                )
                Text(
                    "Syncing • ${displayReports.size} reports",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legend (top-right)
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Map Legend", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                LegendItem("Hazard", Color(0xFFD50000))
                LegendItem("Water", Color(0xFF00B8CC))
                LegendItem("Species", Color(0xFF007ACC))
                LegendItem("You", Color(0xFF007ACC))
            }
        }

        // Camera FAB (bottom-right, primary) - identify species
        FloatingActionButton(
            onClick = onNavigateToCamera,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = 280.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Identify Species", tint = Color.White)
        }

        // Report FAB (secondary, above camera) - add report
        FloatingActionButton(
            onClick = onNavigateToReport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = 360.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Report", tint = Color.White)
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = {
                val center = userLocation?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(32.88, -117.24)
                mapView?.controller?.animateTo(center)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = 440.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = CircleShape
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = MaterialTheme.colorScheme.onSurface)
        }

        // Layers Toggle FAB
        FloatingActionButton(
            onClick = {
                currentMapStyle = when (currentMapStyle) {
                    TileSourceFactory.USGS_TOPO -> TileSourceFactory.USGS_SAT
                    TileSourceFactory.USGS_SAT -> TileSourceFactory.MAPNIK
                    else -> TileSourceFactory.USGS_TOPO
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = 520.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Layers, contentDescription = "Toggle Map Style", tint = MaterialTheme.colorScheme.onSurface)
        }

        // Bottom sheet — always shows the report list; tapping a row navigates to full detail
        ReportListSheet(
            reports = displayReports,
            onSelectReport = { onNavigateToReportDetail(it.reportId) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color, shape = CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color, shape = CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
    }
}


@Composable
private fun ReportListSheet(
    reports: List<TrailReport>,
    onSelectReport: (TrailReport) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .padding(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Nearby (${reports.size})", style = MaterialTheme.typography.titleSmall)
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(reports.take(5)) { report ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                        onClick = { onSelectReport(report) }
                    ) {
                        Row(
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(report.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Text(
                                    report.type.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (report.synced) {
                                Badge(containerColor = Color(0xFF4CAF50)) {
                                    Text("✓", fontSize = 8.sp)
                                }
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
