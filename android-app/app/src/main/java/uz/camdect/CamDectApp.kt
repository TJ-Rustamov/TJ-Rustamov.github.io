package uz.camdect

import android.app.Application
import androidx.preference.PreferenceManager
import org.maplibre.android.MapLibre
import uz.camdect.data.CameraRepository
import uz.camdect.data.TypeLabels
import java.io.File
import kotlin.concurrent.thread

class CamDectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        migrateSettings()
        TypeLabels.clearLegacyNames(this)
        MapLibre.getInstance(this) // must exist before any MapView is created
        CameraRepository.load(this)
    }

    private fun migrateSettings() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        // Voice language "system" became "app" (follow the app language).
        if (sp.getString("voice_lang", null) == "system") sp.edit().putString("voice_lang", "app").apply()
        // Raster tiles from the old osmdroid map are no longer used; free the space.
        val old = File(filesDir, "osmdroid")
        if (old.exists()) thread { old.deleteRecursively() }
    }
}
