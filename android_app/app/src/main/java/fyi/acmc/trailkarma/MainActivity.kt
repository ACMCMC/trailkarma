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
import fyi.acmc.trailkarma.ble.BleService
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
        // Start BLE mesh service — runs as long as app is alive, even backgrounded
        if (granted[Manifest.permission.BLUETOOTH_SCAN] == true &&
            granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) {
            BleService.start(this)
        }
        SyncWorker.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            val syncRepo = DatabricksSyncRepository(applicationContext, db)
            
            if (BuildConfig.DATABRICKS_TOKEN.isNotEmpty()) {
                syncRepo.setDatabricksConfig(
                    url = BuildConfig.DATABRICKS_URL,
                    token = BuildConfig.DATABRICKS_TOKEN,
                    warehouse = BuildConfig.DATABRICKS_WAREHOUSE
                )
                
                // Initial sync on startup
                if (syncRepo.isOnline()) {
                    syncRepo.syncReports()
                    syncRepo.pullReportsFromCloud()
                }
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


}
