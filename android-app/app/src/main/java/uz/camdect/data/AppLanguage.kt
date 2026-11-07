package uz.camdect.data

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * App language chosen in Settings: "uz" (Latin), "uz-Cyrl", "ru" or "en".
 * Our own preference is the source of truth so services and the map can read it too.
 */
object AppLanguage {
    const val KEY = "app_lang"
    const val DEFAULT = "uz"

    fun tag(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, DEFAULT) ?: DEFAULT

    fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

    /** Switches the UI language; AppCompat recreates open activities. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Makes AppCompat use the saved language (first launch, or after an update). */
    fun applySaved(context: Context) {
        val tag = tag(context)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) apply(tag)
    }

    /** Context whose resources use the app language (for services on Android < 13). */
    fun wrap(base: Context): Context {
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(locale(tag(base)))
        return base.createConfigurationContext(conf)
    }
}
