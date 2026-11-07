package uz.camdect.data

import android.content.Context
import androidx.preference.PreferenceManager
import uz.camdect.R
import uz.camdect.core.CameraDb
import uz.camdect.core.Control

/** Names for Neoline type codes. Fixed in the app; unknown codes show their number. */
object TypeLabels {
    val NAMES: Map<Int, String> = mapOf(
        237 to "Lochin",
        243 to "AutoCon",
        236 to "Cordon PRO",
        238 to "Skat",
        168 to "Strelka",
        230 to "AutoUragan",
        164 to "Arena",
        224 to "Binar",
        151 to "Kris-P",
        232 to "Integra",
        239 to "Police post",
        233 to "Hikvision",
        165 to "MultaRadar CD",
    )

    fun name(code: Int): String? = NAMES[code]

    fun label(context: Context, db: CameraDb, i: Int): String {
        val code = db.typeCode(i)
        return name(code)
            ?: context.getString(if (db.speedLimit[i] > 0) R.string.type_camera else R.string.type_control, code)
    }

    fun controlName(context: Context, c: Control): String = context.getString(
        when (c) {
            Control.RED_LIGHT -> R.string.control_red_light
            Control.CROSSWALK -> R.string.control_crosswalk
            Control.NO_STOPPING -> R.string.control_no_stopping
        }
    )

    /** "red light, pedestrian crossing", or "" for a speed-only camera. */
    fun controlsText(context: Context, db: CameraDb, i: Int): String =
        db.controls(i).joinToString(", ") { controlName(context, it) }

    /** Name plus what else it checks, e.g. "Lochin · pedestrian crossing". */
    fun labelWithControls(context: Context, db: CameraDb, i: Int): String {
        val controls = controlsText(context, db, i)
        return if (controls.isEmpty()) label(context, db, i) else "${label(context, db, i)} · $controls"
    }

    /** Removes names saved by older versions, which let users rename types. */
    fun clearLegacyNames(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val old = sp.all.keys.filter { it.startsWith("type_label_") }
        if (old.isNotEmpty()) sp.edit().apply { old.forEach { remove(it) } }.apply()
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
