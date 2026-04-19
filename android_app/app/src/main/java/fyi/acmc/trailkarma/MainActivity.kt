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
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.sync.SyncWorker
import fyi.acmc.trailkarma.ui.navigation.Routes
import fyi.acmc.trailkarma.ui.navigation.TrailKarmaNavGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

                    if (userId != null) {
                        val syncRepo = DatabricksSyncRepository(applicationContext, db)
                        syncRepo.setDatabricksConfig(
                            url = "https://dbc-f1d1578e-8435.cloud.databricks.com",
                            token = "dapia5720e0dfb4e196415703773b1f4aa78",
                            warehouse = "5fa7bca37483870e"
                        )
                    }
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
