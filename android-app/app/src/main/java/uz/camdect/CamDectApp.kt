package uz.camdect

import android.app.Application
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import uz.camdect.data.CameraRepository
import java.io.File

class CamDectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        configureOsmdroid()
        CameraRepository.load(this)
    }

    private fun configureOsmdroid() {
        val c = Configuration.getInstance()
        c.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        c.userAgentValue = BuildConfig.APPLICATION_ID
        // App-private storage: no storage permission needed on any Android version.
        val base = File(filesDir, "osmdroid").apply { mkdirs() }
        c.osmdroidBasePath = base
        c.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
        c.tileFileSystemCacheMaxBytes = 1024L * 1024 * 1024
        c.tileFileSystemCacheTrimBytes = 900L * 1024 * 1024
        // Treat cached tiles as fresh for a year: the map keeps working offline and saves data.
        c.expirationOverrideDuration = 365L * 24 * 60 * 60 * 1000
    }
}
