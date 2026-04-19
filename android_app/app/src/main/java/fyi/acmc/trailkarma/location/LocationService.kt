package fyi.acmc.trailkarma.location

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

private const val CHANNEL_ID = "location_channel"
private const val NOTIFICATION_ID = 1

class LocationService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        startTracking()
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L).build()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    val db = AppDatabase.get(applicationContext)
                    val userRepo = UserRepository(applicationContext, db.userDao())
                    val userId = userRepo.currentUserId.first() ?: "unknown"
                    db.locationUpdateDao().insert(
                        LocationUpdate(userId = userId, lat = loc.latitude, lng = loc.longitude, timestamp = Instant.now().toString())
                    )
                }
            }
        }
        fusedClient.requestLocationUpdates(request, callback, mainLooper)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(callback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrailKarma")
            .setContentText("Logging your trail position")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
}
