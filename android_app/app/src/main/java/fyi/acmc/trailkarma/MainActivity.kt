package fyi.acmc.trailkarma

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.location.LocationService
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.sync.SyncWorker
import fyi.acmc.trailkarma.ui.navigation.Routes
import fyi.acmc.trailkarma.ui.navigation.TrailKarmaNavGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import fyi.acmc.trailkarma.BuildConfig
import java.time.Instant

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startService(Intent(this, LocationService::class.java))
        }
        SyncWorker.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            seedDatabaseIfEmpty(db)

            val syncRepo = DatabricksSyncRepository(applicationContext, db)
            if (BuildConfig.DATABRICKS_URL.isNotEmpty() &&
                BuildConfig.DATABRICKS_TOKEN.isNotEmpty() &&
                BuildConfig.DATABRICKS_WAREHOUSE.isNotEmpty()
            ) {
                syncRepo.setDatabricksConfig(
                    url = BuildConfig.DATABRICKS_URL,
                    token = BuildConfig.DATABRICKS_TOKEN,
                    warehouse = BuildConfig.DATABRICKS_WAREHOUSE
                )
            }
        }

        requestPermissions()

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                var startDest by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val db = AppDatabase.get(applicationContext)
                    val repo = UserRepository(applicationContext, db.userDao())
                    val userId = repo.currentUserId.first()
                    startDest = if (userId != null) Routes.MAP else Routes.LOGIN
                }

                startDest?.let {
                    TrailKarmaNavGraph(navController = navController, startDestination = it)
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ))
    }

    private suspend fun seedDatabaseIfEmpty(db: AppDatabase) {
        val count = db.trailReportDao().getAll().first().size
        if (count == 0) {
            val now = Instant.now().toString()
            val repo = UserRepository(applicationContext, db.userDao())
            val userId = repo.currentUserId.first() ?: "demo-user"

            db.trailReportDao().insert(TrailReport(
                reportId = "mock-1",
                userId = userId,
                type = ReportType.hazard,
                title = "Rockslide ahead",
                description = "Section near mile 24 has debris",
                lat = 32.88,
                lng = -117.24,
                timestamp = now,
                source = ReportSource.self
            ))
            db.trailReportDao().insert(TrailReport(
                reportId = "mock-2",
                userId = userId,
                type = ReportType.hazard,
                title = "Rattlesnake spotted",
                description = "Stay alert, seen near water source",
                lat = 32.87,
                lng = -117.25,
                timestamp = now,
                source = ReportSource.relayed
            ))
            db.trailReportDao().insert(TrailReport(
                reportId = "mock-3",
                userId = userId,
                type = ReportType.water,
                title = "Water source confirmed",
                description = "Spring flowing, fresh water tested",
                lat = 32.89,
                lng = -117.23,
                timestamp = now,
                source = ReportSource.self
            ))
        }
    }
}
