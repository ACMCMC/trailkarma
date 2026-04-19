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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import org.osmdroid.views.overlay.Polyline
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Composable
fun MapScreen(
    onNavigateToCamera: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToReportDetail: (String) -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSyncStatus: () -> Unit = {},
    onNavigateToContact: () -> Unit = {},
    onNavigateToContactTracing: () -> Unit = {},
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsState(initial = emptyList())
    val userLocation by vm.userLocation.collectAsState(initial = null)
    val trails by vm.trails.collectAsState(initial = emptyList())

    var mapView: MapView? by remember { mutableStateOf(null) }
    var currentMapStyle by remember { mutableStateOf(TileSourceFactory.MAPNIK) }
    var selectedTrail by remember { mutableStateOf<fyi.acmc.trailkarma.models.Trail?>(null) }
    var zoomLevel by remember { mutableStateOf(13) }

    LaunchedEffect(trails) {
        if (selectedTrail == null && trails.isNotEmpty()) {
            selectedTrail = trails.first()
        }
    }

    val displayReports = reports
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.75f)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text("Menu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

                    NavigationDrawerItem(
                        label = { Text("About", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            onNavigateToAbout()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Info, contentDescription = "About") }
                    )

                    NavigationDrawerItem(
                        label = { Text("Sync Status", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            onNavigateToSyncStatus()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Sync, contentDescription = "Sync Status") }
                    )

                    NavigationDrawerItem(
                        label = { Text("Contact", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            onNavigateToContact()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Mail, contentDescription = "Contact") }
                    )

                    NavigationDrawerItem(
                        label = { Text("Contact Tracing", style = MaterialTheme.typography.bodyMedium) },
                        selected = false,
                        onClick = {
                            onNavigateToContactTracing()
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Share, contentDescription = "Contact Tracing") }
                    )
                }
            }
        }
    ) {
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
                // Update zoom level and scale markers accordingly
                val currentZoom = map.zoomLevelDouble.toInt()
                if (currentZoom != zoomLevel) {
                    zoomLevel = currentZoom
                }

                val markerSize = (20 + (zoomLevel - 10) * 4).coerceIn(20, 80)

                map.overlays.clear()
                map.setTileSource(currentMapStyle)

                // Add selected trail geometry if available
                selectedTrail?.geometryJson?.let { geoJsonStr ->
                    try {
                        val moshi = Moshi.Builder().build()
                        @Suppress("UNCHECKED_CAST")
                        val adapter = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any?>>
                        val geoJson = adapter.fromJson(geoJsonStr) ?: return@let

                        if (geoJson["type"] == "LineString") {
                            val coords = geoJson["coordinates"] as? List<*> ?: return@let
                            val polyline = Polyline(map).apply {
                                for (coord in coords) {
                                    val point = coord as? List<*> ?: continue
                                    if (point.size >= 2) {
                                        val lng = (point[0] as? Number)?.toDouble() ?: continue
                                        val lat = (point[1] as? Number)?.toDouble() ?: continue
                                        addPoint(GeoPoint(lat, lng))
                                    }
                                }
                                color = 0xFF4CAF50.toInt()
                                width = 6f
                            }
                            map.overlays.add(polyline)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MapScreen", "Failed to parse trail geometry", e)
                    }
                }

                // Add report markers with custom colored icons
                displayReports.forEach { report ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(report.lat, report.lng)
                        title = report.title
                        snippet = report.description
                        icon = MarkerFactory.createMarkerDrawable(context, report.type, markerSize)

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
                    icon = MarkerFactory.createUserMarkerDrawable(context, markerSize)
                }
                map.overlays.add(userMarker)

                map.invalidate()
            }
        )

        // Top status bar (minimal flat design)
        var expanded by remember { mutableStateOf(false) }

        // Menu button
        IconButton(
            onClick = { scope.launch { drawerState.open() } },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black, modifier = Modifier.size(24.dp))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(start = 56.dp, top = 12.dp, end = 12.dp)
                .clickable { expanded = true },
            color = Color.White
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedTrail?.name ?: "Select Trail", style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Trail", modifier = Modifier.size(16.dp))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (trails.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No trails synced", style = MaterialTheme.typography.bodySmall) },
                            onClick = { expanded = false }
                        )
                    } else {
                        trails.forEach { trail ->
                            DropdownMenuItem(
                                text = { Text(trail.name, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    selectedTrail = trail
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    userLocation?.let { "${String.format("%.4f", it.lat)}°N, ${String.format("%.4f", it.lng)}°W" } ?: "32.88°N, 117.24°W",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
                Text(
                    "${displayReports.size} reports",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }

        // Minimal flat buttons (top-right)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onNavigateToCamera, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = Color.Black)
            }
            IconButton(onClick = onNavigateToReport, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Report", tint = Color.Black)
            }
            IconButton(onClick = {
                val center = userLocation?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(32.88, -117.24)
                mapView?.controller?.animateTo(center)
            }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter", tint = Color.Black)
            }
            IconButton(onClick = {
                currentMapStyle = when (currentMapStyle) {
                    TileSourceFactory.MAPNIK -> TileSourceFactory.USGS_TOPO
                    TileSourceFactory.USGS_TOPO -> TileSourceFactory.USGS_SAT
                    else -> TileSourceFactory.MAPNIK
                }
            }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Layers, contentDescription = "Layers", tint = Color.Black)
            }
        }

        // Bottom sheet — always shows the report list; tapping a row navigates to full detail
        ReportListSheet(
            reports = displayReports,
            onSelectReport = { onNavigateToReportDetail(it.reportId) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
            .heightIn(max = 200.dp)
            .padding(12.dp),
        color = Color.White
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Nearby (${reports.size})",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(12.dp)
            )

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(reports.take(5)) { report ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectReport(report) },
                        color = Color.White
                    ) {
                        Row(
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(report.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text(
                                    report.type.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(if (report.synced) "✓" else "…", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
