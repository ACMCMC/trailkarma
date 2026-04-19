package fyi.acmc.trailkarma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import fyi.acmc.trailkarma.ble.BleService
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.location.LocationService
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.sync.BiodiversityLocalInferenceWorker
import fyi.acmc.trailkarma.sync.BiodiversitySyncWorker
import fyi.acmc.trailkarma.sync.SyncWorker
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.navigation.Routes
import fyi.acmc.trailkarma.ui.navigation.TrailKarmaNavGraph
import kotlinx.coroutines.launch
import fyi.acmc.trailkarma.BuildConfig

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        Log.i("TrailKarma/Main", "🔔 Permission dialog dismissed. Results: " +
            granted.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })

        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startService(Intent(this, LocationService::class.java))
        }

        // Check BLE permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bleGranted = granted[Manifest.permission.BLUETOOTH_SCAN] == true &&
                granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true
            if (bleGranted) {
                Log.i("TrailKarma/Main", "BLE permissions granted — starting BleService")
                BleService.start(this)
            } else {
                Log.w("TrailKarma/Main", "BLE permissions NOT fully granted — BleService not started. " +
                    "SCAN=${granted[Manifest.permission.BLUETOOTH_SCAN]}, " +
                    "ADVERTISE=${granted[Manifest.permission.BLUETOOTH_ADVERTISE]}")
            }
        } else {
            // On Android 11, BLE perms are install-time (already granted via manifest); BleService started eagerly in onCreate
            Log.i("TrailKarma/Main", "Android 11: BLE permissions are install-time, already handled in onCreate")
        }
        SyncWorker.schedule(this)
        BiodiversityLocalInferenceWorker.schedulePending(this)
        BiodiversitySyncWorker.schedule(this)
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i("TrailKarma/Main", "Requesting BLE permissions for Android 12+ (SCAN + ADVERTISE)...")
            permissionLauncher.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ))
        } else {
            Log.i("TrailKarma/Main", "Android 11: BLE permissions are install-time, already granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
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

                if (syncRepo.isOnline()) {
                    runCatching {
                        syncRepo.syncReports()
                        syncRepo.syncLocations()
                        syncRepo.syncRelayPackets()
                        syncRepo.pullReportsFromCloud()
                        syncRepo.pullTrailsFromCloud()
                    }
                }
            }
        }

        // If BLE permissions are already granted (re-launch), start the service immediately
        val blePermsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            // On Android 11, BLUETOOTH + BLUETOOTH_ADMIN are install-time permissions (always granted if manifest declares them)
            true
        }
        if (blePermsGranted) {
            Log.i("TrailKarma/Main", "BLE permissions already granted — starting BleService eagerly")
            BleService.start(this)
        }
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            startService(Intent(this, LocationService::class.java))
        }

        requestPermissions()

        setContent {
            TrailKarmaAppTheme {
                val navController = rememberNavController()
                var startDest by remember { mutableStateOf<String?>(null) }
                var bleMissingDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val db = AppDatabase.get(applicationContext)
                    val repo = UserRepository(applicationContext, db.userDao())
                    repo.ensureLocalUser()
                    startDest = Routes.MAP

                    // Show dialog if BLE permissions missing (Android 12+ only)
                    val bleMissing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val bleScan = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                        val bleAdv = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_ADVERTISE
                        ) == PackageManager.PERMISSION_GRANTED
                        !bleScan || !bleAdv
                    } else {
                        // On Android 11, BLE perms are install-time (assumed granted)
                        false
                    }
                    if (bleMissing) {
                        bleMissingDialog = true
                    }
                }

                if (bleMissingDialog) {
                    AlertDialog(
                        onDismissRequest = { bleMissingDialog = false },
                        title = { Text("Bluetooth Permission Required") },
                        text = { Text("Grant \"Nearby devices\" permission so other hikers can find you on the mesh network.") },
                        confirmButton = {
                            Button(onClick = {
                                requestBlePermissions()
                                bleMissingDialog = false
                            }) {
                                Text("Grant")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { bleMissingDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
                }

                startDest?.let {
                    TrailKarmaNavGraph(navController = navController, startDestination = it)
                }
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        // On Android 12+, request BLE permissions (install-time on 11, runtime on 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // On Android 11, BLUETOOTH + BLUETOOTH_ADMIN are install-time only; don't request at runtime
        permissionLauncher.launch(perms.toTypedArray())
    }

}
