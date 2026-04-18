package fyi.acmc.trailkarma.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val latestLocation: Flow<LocationUpdate?> = db.locationUpdateDao().getLatest()
    val syncStatus = MutableStateFlow("Offline — not yet synced")
}
