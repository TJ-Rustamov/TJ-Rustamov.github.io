package uz.camdect.data

import android.content.Context
import androidx.preference.PreferenceManager
import uz.camdect.R
import uz.camdect.core.CameraDb

/**
 * Names for Neoline type codes. The codes are undocumented, so the user names
 * them (e.g. "Lochin") by tapping a camera; until then we show the raw code.
 */
object TypeLabels {
    private fun key(code: Int) = "type_label_$code"

    /** User-given name for the code, or null. */
    fun custom(context: Context, code: Int): String? =
        PreferenceManager.getDefaultSharedPreferences(context).getString(key(code), null)?.takeIf { it.isNotBlank() }

    fun label(context: Context, db: CameraDb, i: Int): String {
        val code = db.typeCode(i)
        return custom(context, code)
            ?: context.getString(if (db.speedLimit[i] > 0) R.string.type_camera else R.string.type_control, code)
    }

    /** All user-given names, keyed by type code. */
    fun all(context: Context): Map<Int, String> {
        val out = HashMap<Int, String>()
        for ((k, v) in PreferenceManager.getDefaultSharedPreferences(context).all) {
            if (k.startsWith("type_label_") && v is String && v.isNotBlank()) k.removePrefix("type_label_").toIntOrNull()?.let { out[it] = v }
        }
        return out
    }

    fun set(context: Context, code: Int, name: String?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context).edit()
        if (name.isNullOrBlank()) sp.remove(key(code)) else sp.putString(key(code), name.trim())
        sp.apply()
    }

    /** Number of points per type code, computed once per database. */
    private var countsFor: CameraDb? = null
    private var counts = IntArray(256)

    fun count(db: CameraDb, code: Int): Int {
        if (countsFor !== db) {
            val c = IntArray(256)
            for (i in 0 until db.size) c[db.typeCode(i)]++
            counts = c
            countsFor = db
        }
        return counts[code]
    }
}
