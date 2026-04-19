package fyi.acmc.trailkarma.ble

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import fyi.acmc.trailkarma.ble.BleRepositoryHolder

private const val TAG = "TrailKarma/BleService"

private const val CHANNEL_ID = "ble_channel"
private const val NOTIFICATION_ID = 2

/**
 * Persistent foreground service that owns the full BLE lifecycle:
 *   - Advertises a structured beacon so nearby phones can discover us
 *   - Scans for peer beacons and logs encounters as RelayPackets
 *   - Launches the GATT server so peers can pull our report manifest
 *   - Connects as a GATT client to any newly-discovered peer to pull their reports
 *
 * Runs as long as the app is alive (even when backgrounded).
 * Started from MainActivity once BLE permissions are granted.
 */
class BleService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleRepo: BleRepository
    private lateinit var gattServer: GattServer

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BleService onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            val db = AppDatabase.get(applicationContext)
            val userRepo = UserRepository(applicationContext, db.userDao())
            val user = userRepo.ensureLocalUser()
            val hikerName = user.displayName.ifBlank { user.userId }
            Log.i(TAG, "BLE stack starting for hiker: $hikerName")

            bleRepo = BleRepositoryHolder.getInstance(applicationContext)
            gattServer = GattServer(applicationContext, db.trailReportDao(), db.relayPacketDao())

            gattServer.start()
            bleRepo.startAdvertising(hikerName)
            bleRepo.startScan()

            Log.i(TAG, "BLE stack fully started — advertising + scanning + GATT server running")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "BleService onDestroy")
        if (::bleRepo.isInitialized) {
            bleRepo.stopScan()
            bleRepo.stopAdvertising()
        }
        if (::gattServer.isInitialized) gattServer.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY // restart automatically if killed by the OS

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nearby Hikers (BLE)",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "TrailKarma mesh network — detecting nearby hikers" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrailKarma — Mesh Active")
            .setContentText("Scanning for nearby hikers…")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, BleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleService::class.java))
        }
    }
}
