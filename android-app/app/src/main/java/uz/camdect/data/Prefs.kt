package uz.camdect.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import uz.camdect.core.AlertEngine

/** Typed access to the settings screen values. */
class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val voice get() = sp.getBoolean("voice", true)
    val voiceLang get() = sp.getString("voice_lang", "system") ?: "system"
    val beep get() = sp.getBoolean("beep", true)
    val vibrate get() = sp.getBoolean("vibrate", true)
    val keepScreenOn get() = sp.getBoolean("keep_screen_on", true)
    val rotateMap get() = sp.getBoolean("rotate_map", false)
    var batteryAsked: Boolean
        get() = sp.getBoolean("battery_asked", false)
        set(v) = sp.edit().putBoolean("battery_asked", v).apply()

    fun alertConfig() = AlertEngine.Config(
        minAlertDistanceM = (sp.getString("min_distance", "400") ?: "400").toDouble(),
        overspeedMarginKmh = (sp.getString("overspeed_margin", "5") ?: "5").toInt(),
        ignoreNoLimit = sp.getBoolean("ignore_no_limit", false),
    )
}
