package uz.camdect.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Bitmaps for the map's symbol layers, drawn in code so they scale with screen density. */
object MapIcons {
    // Prefixed: the map style ships its own sprites (it has a "car" icon) and a clash
    // silently replaces our bitmap.
    const val CAR = "camdect-arrow"
    const val LENS = "camdect-lens"
    const val RED_LIGHT = "camdect-badge-red-light"
    const val CROSSWALK = "camdect-badge-crosswalk"
    const val NO_STOPPING = "camdect-badge-no-stopping"

    private fun paint(color: Int, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (stroke > 0) { style = Paint.Style.STROKE; strokeWidth = stroke; strokeJoin = Paint.Join.ROUND }
    }

    /** Car chevron pointing up, centred; MapLibre rotates it to the heading. */
    fun car(density: Float): Bitmap {
        val s = (44 * density).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val k = s / 48f
        val arrow = Path().apply {
            moveTo(24 * k, 3 * k); lineTo(41 * k, 42 * k); lineTo(24 * k, 33 * k); lineTo(7 * k, 42 * k); close()
        }
        c.save(); c.translate(1.5f * k, 2.5f * k); c.drawPath(arrow, paint(0x55000000)); c.restore()
        c.drawPath(arrow, paint(0xFF1E88E5.toInt()))
        c.drawPath(arrow, paint(0xFFFFFFFF.toInt(), 3 * k))
        return b
    }

    /**
     * Lens notch: a dark triangle whose tip sticks out of the top of the marker circle and whose
     * base hides under it. The bitmap centre is the camera point, so rotating it by the lens
     * bearing points the tip the way the camera looks.
     */
    fun lens(density: Float): Bitmap {
        val s = (60 * density).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val mid = s / 2f
        val path = Path().apply {
            moveTo(mid, 0f); lineTo(mid + s * 0.17f, s * 0.42f); lineTo(mid - s * 0.17f, s * 0.42f); close()
        }
        c.drawPath(path, paint(0xFF1E2A38.toInt()))
        c.drawPath(path, paint(0xFFFFFFFF.toInt(), 1.5f * density))
        return b
    }

    fun redLight(density: Float): Bitmap {
        val w = (16 * density).toInt(); val h = (34 * density).toInt()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 4 * density, 4 * density, paint(0xFF263238.toInt()))
        val cx = w / 2f; val r = w * 0.3f
        c.drawCircle(cx, h * 0.2f, r, paint(0xFFE53935.toInt()))
        c.drawCircle(cx, h * 0.5f, r, paint(0xFFFFC107.toInt()))
        c.drawCircle(cx, h * 0.8f, r, paint(0xFF43A047.toInt()))
        return b
    }

    fun crosswalk(density: Float): Bitmap {
        val s = (26 * density).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRoundRect(RectF(0f, 0f, s.toFloat(), s.toFloat()), 4 * density, 4 * density, paint(0xFF1565C0.toInt()))
        val stripe = s / 7f
        val white = paint(0xFFFFFFFF.toInt())
        for (k in 0 until 3) {
            val x = stripe * (1 + k * 2)
            c.drawRect(x, s * 0.2f, x + stripe, s * 0.8f, white)
        }
        return b
    }

    fun noStopping(density: Float): Bitmap {
        val s = (26 * density).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val m = s / 2f
        val ring = 3 * density
        c.drawCircle(m, m, m, paint(0xFF1565C0.toInt()))
        val red = paint(0xFFE53935.toInt(), ring)
        c.drawCircle(m, m, m - ring / 2, red)
        val d = m * 0.6f
        c.drawLine(m - d, m - d, m + d, m + d, red)
        c.drawLine(m + d, m - d, m - d, m + d, red)
        return b
    }
}
