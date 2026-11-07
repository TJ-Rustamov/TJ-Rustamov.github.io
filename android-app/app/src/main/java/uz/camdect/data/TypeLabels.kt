package uz.camdect.data

import android.content.Context
import androidx.preference.PreferenceManager
import uz.camdect.R
import uz.camdect.core.CameraDb
import uz.camdect.core.Control

/**
 * Names and descriptions for Neoline type codes, as string resources so they follow the app
 * language (Cyrillic names also let the Russian voice pronounce them). Fixed in the app;
 * unknown codes show their number.
 */
object TypeLabels {
    /** Code → (name, description), in the order shown on the camera info tab. */
    val KNOWN: List<Triple<Int, Int, Int>> = listOf(
        Triple(237, R.string.cam_237, R.string.desc_237),
        Triple(243, R.string.cam_243, R.string.desc_243),
        Triple(236, R.string.cam_236, R.string.desc_236),
        Triple(238, R.string.cam_238, R.string.desc_238),
        Triple(168, R.string.cam_168, R.string.desc_168),
        Triple(165, R.string.cam_165, R.string.desc_165),
        Triple(233, R.string.cam_233, R.string.desc_233),
        Triple(230, R.string.cam_230, R.string.desc_230),
        Triple(232, R.string.cam_232, R.string.desc_232),
        Triple(164, R.string.cam_164, R.string.desc_164),
        Triple(151, R.string.cam_151, R.string.desc_151),
        Triple(224, R.string.cam_224, R.string.desc_224),
        Triple(239, R.string.cam_239, R.string.desc_239),
    )
    private val NAME_RES: Map<Int, Int> = KNOWN.associate { it.first to it.second }

    fun name(context: Context, code: Int): String? = NAME_RES[code]?.let { context.getString(it) }

    fun label(context: Context, db: CameraDb, i: Int): String {
        val code = db.typeCode(i)
        return name(context, code)
            ?: context.getString(if (db.speedLimit[i] > 0) R.string.type_camera else R.string.type_control, code)
    }

    fun controlName(context: Context, c: Control): String = context.getString(controlRes(c))

    fun controlRes(c: Control): Int = when (c) {
        Control.RED_LIGHT -> R.string.control_red_light
        Control.CROSSWALK -> R.string.control_crosswalk
        Control.NO_STOPPING -> R.string.control_no_stopping
    }

    /** "red light, pedestrian crossing", or "" for a speed-only camera. */
    fun controlsText(context: Context, db: CameraDb, i: Int): String =
        db.controls(i).joinToString(", ") { controlName(context, it) }

    /** Name plus what else it checks, e.g. "Lochin · red light". */
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

    @Volatile private var countsFor: CameraDb? = null
    @Volatile private var counts = IntArray(256)

    /** Number of points per type code. Call [prepareCounts] off the UI thread first. */
    fun count(db: CameraDb, code: Int): Int {
        if (countsFor !== db) prepareCounts(db)
        return counts[code]
    }

    fun prepareCounts(db: CameraDb) {
        if (countsFor === db) return
        val c = IntArray(256)
        for (i in 0 until db.size) c[db.typeCode(i)]++
        counts = c
        countsFor = db
    }
}
