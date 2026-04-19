package fyi.acmc.trailkarma

import android.app.Application
import org.osmdroid.config.Configuration

class TrailKarmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = "TrailKarma/1.0"
            setOsmdroidBasePath(cacheDir)
            setOsmdroidTileCache(cacheDir)
        }
    }
}
