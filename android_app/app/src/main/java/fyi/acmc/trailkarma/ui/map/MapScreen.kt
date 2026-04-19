package fyi.acmc.trailkarma.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import fyi.acmc.trailkarma.api.WalletStateResponse
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.Trail
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.ui.design.TrailInfoChip
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToBiodiversity: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToReportDetail: (String) -> Unit = {},
    onNavigateToRewards: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToBle: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSyncStatus: () -> Unit = {},
    onNavigateToContact: () -> Unit = {},
    onNavigateToContactTracing: () -> Unit = {},
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val reports by vm.reports.collectAsState(initial = emptyList())
    val biodiversity by vm.biodiversity.collectAsState(initial = emptyList())
    val userLocation by vm.userLocation.collectAsState(initial = null)
    val trails by vm.trails.collectAsState(initial = emptyList())
    val walletState by vm.walletState.collectAsState()
    val isOnline by vm.isOnline.collectAsState(initial = false)

    var mapView: MapView? by remember { mutableStateOf(null) }
    var currentMapStyle by remember { mutableStateOf(TileSourceFactory.MAPNIK) }
    var selectedTrail by remember { mutableStateOf<Trail?>(null) }
    var zoomLevel by remember { mutableStateOf(13) }
    var trailMenuExpanded by remember { mutableStateOf(false) }
    data class OverlayKey(
        val reports: List<Any>,
        val biodiversity: List<Any>,
        val loc: Any?,
        val trail: Any?,
        val zoom: Int,
        val style: Any
    )
    var lastOverlayKey by remember { mutableStateOf<OverlayKey?>(null) }

    LaunchedEffect(trails) {
        if (selectedTrail == null && trails.isNotEmpty()) {
            selectedTrail = trails.first()
        }
    }

    LaunchedEffect(
        reports.count { it.rewardClaimed },
        reports.count { it.verificationStatus == "rejected" }
    ) {
        vm.refreshWalletState()
    }

    val displayReports = reports
    val displayBiodiversity = biodiversity.filter {
        it.savedLocally && it.lat != null && it.lon != null && !it.finalLabel.isNullOrBlank()
    }
    val offlineReportCount = reports.count { !it.synced }
    val pendingRewardCount = reports.count { !it.rewardClaimed && it.verificationStatus != "rejected" }
    val collectibleCount = displayBiodiversity.count {
        it.collectibleStatus == "verified" || it.collectibleStatus == "verified_no_collectible"
    }
    val earnedBadgeCount = walletState?.badgeDetails?.count { it.earned } ?: walletState?.badges?.size ?: 0
    val showHeroMetrics = walletState != null || pendingRewardCount > 0 || collectibleCount > 0 || earnedBadgeCount > 0
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.82f),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TrailKarma", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Offline trail intel, mesh-delivered help, and on-chain rewards in one field app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            TrailInfoChip(
                                icon = Icons.Default.Route,
                                label = "${displayReports.size} trail reports",
                                accent = RewardsPalette.Forest
                            )
                            TrailInfoChip(
                                icon = Icons.Default.Forest,
                                label = "${displayBiodiversity.size} species records",
                                accent = RewardsPalette.Moss
                            )
                            TrailInfoChip(
                                icon = Icons.Default.AutoAwesome,
                                label = "${walletState?.karmaBalance ?: "--"} KARMA",
                                accent = RewardsPalette.Gold
                            )
                        }
                    }

                    DrawerDestination("Rewards", "KARMA, badges, and collectibles", Icons.Default.AutoAwesome) {
                        onNavigateToRewards()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Profile", "Identity, contact defaults, and wallet state", Icons.Default.Person) {
                        onNavigateToProfile()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Relay Hub", "Queue and carry delayed voice messages", Icons.Default.Bluetooth) {
                        onNavigateToBle()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Trail feed", "See the full report and biodiversity timeline", Icons.AutoMirrored.Filled.List) {
                        onNavigateToHistory()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("About", "Core idea, team, and demo framing", Icons.Default.Info) {
                        onNavigateToAbout()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Sync status", "Inspect local, BLE, and cloud state", Icons.Default.Sync) {
                        onNavigateToSyncStatus()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Contact", "Reach the team behind the build", Icons.Default.Mail) {
                        onNavigateToContact()
                        scope.launch { drawerState.close() }
                    }
                    DrawerDestination("Contact tracing", "Review nearby peer exchange details", Icons.Default.Share) {
                        onNavigateToContactTracing()
                        scope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setBuiltInZoomControls(true)
                        setMultiTouchControls(true)
                        controller.apply {
                            setZoom(13)
                            setCenter(GeoPoint(32.88, -117.24))
                        }
                        mapView = this
                    }
                },
                update = { map ->
                    val currentZoom = map.zoomLevelDouble.toInt()
                    if (currentZoom != zoomLevel) {
                        zoomLevel = currentZoom
                    }

                    val markerSize = (40 + (zoomLevel - 10) * 6).coerceIn(40, 140)
                    val overlayKey = OverlayKey(
                        reports = displayReports,
                        biodiversity = displayBiodiversity,
                        loc = userLocation,
                        trail = selectedTrail,
                        zoom = markerSize,
                        style = currentMapStyle
                    )
                    if (overlayKey == lastOverlayKey) return@AndroidView
                    lastOverlayKey = overlayKey

                    map.overlays.clear()
                    if (map.tileProvider.tileSource != currentMapStyle) {
                        map.setTileSource(currentMapStyle)
                    }

                    selectedTrail?.geometryJson?.let { geoJsonStr ->
                        try {
                            val moshi = Moshi.Builder().build()
                            @Suppress("UNCHECKED_CAST")
                            val adapter = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any?>>
                            val geoJson = adapter.fromJson(geoJsonStr) ?: return@let

                            if (geoJson["type"] == "LineString") {
                                val coords = geoJson["coordinates"] as? List<*> ?: return@let
                                val polyline = Polyline(map).apply {
                                    coords.forEach { coord ->
                                        val point = coord as? List<*> ?: return@forEach
                                        if (point.size >= 2) {
                                            val lng = (point[0] as? Number)?.toDouble() ?: return@forEach
                                            val lat = (point[1] as? Number)?.toDouble() ?: return@forEach
                                            addPoint(GeoPoint(lat, lng))
                                        }
                                    }
                                    color = RewardsPalette.Forest.value.toInt()
                                    width = 7f
                                }
                                map.overlays.add(polyline)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MapScreen", "Failed to parse trail geometry", e)
                        }
                    }

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

                    displayBiodiversity.forEach { observation ->
                        val marker = Marker(map).apply {
                            position = GeoPoint(observation.lat!!, observation.lon!!)
                            title = observation.collectibleName ?: observation.finalLabel!!
                            snippet = buildString {
                                append(observation.finalTaxonomicLevel ?: "biodiversity")
                                observation.collectibleStatus.takeIf { it.isNotBlank() }?.let {
                                    append(" • ")
                                    append(it.replace('_', ' '))
                                }
                            }
                            icon = MarkerFactory.createMarkerDrawable(context, ReportType.species, markerSize)
                            setOnMarkerClickListener { clickedMarker, _ ->
                                clickedMarker.showInfoWindow()
                                true
                            }
                        }
                        map.overlays.add(marker)
                    }

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

            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        shape = CircleShape
                    )
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(start = 68.dp, top = 12.dp, end = 86.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Trail briefing", style = MaterialTheme.typography.labelLarge, color = RewardsPalette.Stone)
                        Text(
                            selectedTrail?.name ?: "PCT demo region",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (userLocation != null) {
                                "${formatCoordinate(userLocation!!.lat, "N", "S")} • ${formatCoordinate(userLocation!!.lng, "E", "W")}"
                            } else {
                                "Using the seeded route until GPS or a synced trail arrives."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        TrailInfoChip(
                            icon = if (isOnline) Icons.Default.Sync else Icons.Default.Route,
                            label = if (isOnline) "Online now" else "Offline-ready",
                            accent = if (isOnline) RewardsPalette.Forest else RewardsPalette.Gold
                        )
                        TrailInfoChip(
                            icon = Icons.Default.Add,
                            label = "$offlineReportCount local saves",
                            accent = RewardsPalette.Sky
                        )
                        TrailInfoChip(
                            icon = Icons.Default.AutoAwesome,
                            label = "$pendingRewardCount rewards pending",
                            accent = RewardsPalette.Gold
                        )
                    }

                    if (showHeroMetrics) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BriefingMetricCard(
                                value = walletState?.karmaBalance ?: "--",
                                label = "KARMA",
                                accent = RewardsPalette.Gold,
                                modifier = Modifier.weight(1f)
                            )
                            BriefingMetricCard(
                                value = earnedBadgeCount.toString(),
                                label = "Badges",
                                accent = RewardsPalette.Forest,
                                modifier = Modifier.weight(1f)
                            )
                            BriefingMetricCard(
                                value = collectibleCount.toString(),
                                label = "Species cards",
                                accent = RewardsPalette.Moss,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onNavigateToRewards,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Text("Rewards")
                        }
                        OutlinedButton(
                            onClick = { trailMenuExpanded = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Explore, contentDescription = null)
                            Text("Trail")
                        }
                        DropdownMenu(
                            expanded = trailMenuExpanded,
                            onDismissRequest = { trailMenuExpanded = false }
                        ) {
                            if (trails.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No trails synced yet") },
                                    onClick = { trailMenuExpanded = false }
                                )
                            } else {
                                trails.forEach { trail ->
                                    DropdownMenuItem(
                                        text = { Text(trail.name) },
                                        onClick = {
                                            selectedTrail = trail
                                            trailMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WindowInsets.systemBars.asPaddingValues())
                    .padding(top = 16.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MapActionButton("Audio", Icons.Default.Mic, RewardsPalette.Forest, onNavigateToBiodiversity)
                MapActionButton("Photo", Icons.Default.CameraAlt, RewardsPalette.Gold, onNavigateToCamera)
                MapActionButton("Report", Icons.Default.Add, RewardsPalette.Clay, onNavigateToReport)
                MapActionButton(
                    "Locate",
                    Icons.Default.MyLocation,
                    RewardsPalette.Sky
                ) {
                    val center = userLocation?.let { GeoPoint(it.lat, it.lng) } ?: GeoPoint(32.88, -117.24)
                    mapView?.controller?.animateTo(center)
                }
            }

            TrailBriefingSheet(
                reports = displayReports,
                biodiversity = displayBiodiversity,
                walletState = walletState,
                onOpenReport = { onNavigateToReportDetail(it.reportId) },
                onOpenReportComposer = onNavigateToReport,
                onOpenRelay = onNavigateToBle,
                onOpenRewards = onNavigateToRewards,
                onOpenProfile = onNavigateToProfile,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun DrawerDestination(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = title) }
    )
}

@Composable
private fun BriefingMetricCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun MapActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = accent)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TrailBriefingSheet(
    reports: List<TrailReport>,
    biodiversity: List<BiodiversityContribution>,
    walletState: WalletStateResponse?,
    onOpenReport: (TrailReport) -> Unit,
    onOpenReportComposer: () -> Unit,
    onOpenRelay: () -> Unit,
    onOpenRewards: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActivity = reports.isNotEmpty() || biodiversity.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (hasActivity) 320.dp else 252.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Demo control deck", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Show the live trail ecosystem: create intel, relay help, and open rewards without leaving the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                QuickActionCard("New report", "Hazard, water, or species", Icons.Default.Add, RewardsPalette.Clay, onOpenReportComposer)
                QuickActionCard("Relay hub", "Queue delayed voice calls", Icons.Default.Bluetooth, RewardsPalette.Sky, onOpenRelay)
                QuickActionCard("Rewards", "Open KARMA and collectibles", Icons.Default.AutoAwesome, RewardsPalette.Gold, onOpenRewards)
                QuickActionCard("Profile", "Identity and relay defaults", Icons.Default.Person, RewardsPalette.Forest, onOpenProfile)
            }

            if (hasActivity) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BriefingMetricCard(
                        value = reports.size.toString(),
                        label = "Trail intel",
                        accent = RewardsPalette.Clay,
                        modifier = Modifier.weight(1f)
                    )
                    BriefingMetricCard(
                        value = biodiversity.size.toString(),
                        label = "Species sightings",
                        accent = RewardsPalette.Moss,
                        modifier = Modifier.weight(1f)
                    )
                    BriefingMetricCard(
                        value = walletState?.rewardStats?.verifiedContributionCount?.toString() ?: "--",
                        label = "Verified wins",
                        accent = RewardsPalette.Forest,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!hasActivity) {
                Text(
                    "The map is ready, but there’s no saved local intel yet. Create a report or record biodiversity to populate the demo scene.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                    if (reports.isNotEmpty()) {
                        item {
                            Text("Recent trail intel", style = MaterialTheme.typography.titleMedium)
                        }
                        items(reports.take(3)) { report ->
                            IntelCard(
                                title = report.title,
                                subtitle = report.description.ifBlank { report.type.name.replaceFirstChar { it.uppercase() } },
                                meta = buildString {
                                    append(report.type.name.replaceFirstChar { it.uppercase() })
                                    append(" • ")
                                    append(if (report.rewardClaimed) "KARMA settled" else "Awaiting settlement")
                                },
                                accent = reportAccent(report.type),
                                onClick = { onOpenReport(report) }
                            )
                        }
                    }

                    if (biodiversity.isNotEmpty()) {
                        item {
                            Text("Species cards on the map", style = MaterialTheme.typography.titleMedium)
                        }
                        items(biodiversity.take(3)) { item ->
                            IntelCard(
                                title = item.collectibleName ?: item.finalLabel ?: "Biodiversity observation",
                                subtitle = item.explanation ?: "Saved local species record",
                                meta = buildString {
                                    append(item.finalTaxonomicLevel ?: "biodiversity")
                                    append(" • ")
                                    append(item.collectibleStatus.replace('_', ' '))
                                },
                                accent = biodiversityAccent(item),
                                onClick = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntelCard(
    title: String,
    subtitle: String,
    meta: String,
    accent: Color,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Route, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(meta, style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
    }
}

private fun reportAccent(type: ReportType): Color = when (type) {
    ReportType.hazard -> RewardsPalette.Clay
    ReportType.water -> RewardsPalette.Sky
    ReportType.species -> RewardsPalette.Moss
}

private fun biodiversityAccent(item: BiodiversityContribution): Color = when (item.collectibleStatus) {
    "verified" -> RewardsPalette.Gold
    "verified_no_collectible", "duplicate_species" -> RewardsPalette.Forest
    "pending_verification" -> RewardsPalette.Moss
    else -> RewardsPalette.Sky
}

private fun formatCoordinate(value: Double, positiveHemisphere: String, negativeHemisphere: String): String {
    val hemisphere = if (value >= 0) positiveHemisphere else negativeHemisphere
    return "${String.format("%.4f", abs(value))}°$hemisphere"
}
