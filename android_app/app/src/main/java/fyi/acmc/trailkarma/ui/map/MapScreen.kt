package fyi.acmc.trailkarma.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.time.Instant
import java.util.*

@Composable
fun MapScreen(
    onNavigateToCamera: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsState(initial = emptyList())
    val userLocation by vm.userLocation.collectAsState(initial = null)
    val selectedReport by vm.selectedReport.collectAsState(initial = null)

    var mapView: MapView? by remember { mutableStateOf(null) }

    // Add mock warnings if no reports yet (for demo)
    val displayReports = if (reports.isEmpty()) {
        listOf(
            TrailReport(
                reportId = "mock-1",
                userId = "demo",
                type = ReportType.hazard,
                title = "Rockslide ahead",
                description = "Section near mile 24 has debris",
                lat = 32.88,
                lng = -117.24,
                timestamp = Instant.now().toString(),
                source = ReportSource.self
            ),
            TrailReport(
                reportId = "mock-2",
                userId = "demo",
                type = ReportType.hazard,
                title = "Rattlesnake spotted",
                description = "Stay alert, seen near water source",
                lat = 32.87,
                lng = -117.25,
                timestamp = Instant.now().toString(),
                source = ReportSource.relayed
            ),
            TrailReport(
                reportId = "mock-3",
                userId = "demo",
                type = ReportType.water,
                title = "Water source confirmed",
                description = "Spring flowing, fresh water tested",
                lat = 32.89,
                lng = -117.23,
                timestamp = Instant.now().toString(),
                source = ReportSource.self
            )
        )
    } else {
        reports
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Map View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.USGS_TOPO)
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

                // Add report markers with custom colored icons
                displayReports.forEach { report ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(report.lat, report.lng)
                        title = report.title
                        snippet = report.description
                        icon = MarkerFactory.createMarkerDrawable(context, report.type)

                        setOnMarkerClickListener { _, _ ->
                            vm.selectReport(report)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }

                // Add user location marker (blue circle with white dot at 32.88, -117.24)
                val userMarker = Marker(map).apply {
                    position = GeoPoint(32.88, -117.24)
                    title = "Your Location"
                    snippet = "Current position"
                    icon = MarkerFactory.createUserMarkerDrawable(context)
                }
                map.overlays.add(userMarker)

                map.invalidate()
            }
        )

        // Top status bar (respects status bar inset)
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Trail Status", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                Text(
                    "32.88°N, 117.24°W",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp
                )
                Text(
                    "Offline • ${displayReports.size} reports",
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

        // Bottom sheet - reports or detail
        if (selectedReport != null) {
            ReportDetailSheet(
                report = selectedReport!!,
                onDismiss = { vm.selectReport(null) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            ReportListSheet(
                reports = displayReports,
                onSelectReport = { vm.selectReport(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
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
private fun ReportDetailSheet(report: TrailReport, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(report.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(containerColor = when (report.type) {
                    ReportType.hazard -> Color(0xFFD50000)
                    ReportType.water -> Color(0xFF00B8CC)
                    ReportType.species -> Color(0xFF007ACC)
                }) {
                    Text(report.type.name, fontSize = 9.sp)
                }
                if (report.source == ReportSource.relayed) {
                    Badge(containerColor = Color(0xFF666666)) {
                        Text("relayed", fontSize = 8.sp)
                    }
                }
            }
            Text(report.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "${String.format("%.4f", report.lat)}, ${String.format("%.4f", report.lng)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
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
                            Badge(
                                containerColor = if (report.synced) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            ) {
                                Text(if (report.synced) "✓" else "◯", fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
