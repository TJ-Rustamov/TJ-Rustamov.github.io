package uz.camdect.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** The car arrow. Position/course are set by [MainActivity] (animated between GPS fixes). */
class PositionOverlay(private val density: Float) : Overlay() {
    var position: GeoPoint? = null
    var courseDeg: Double = Double.NaN
    var accuracyM: Float = 0f

    private val pt = Point()
    private val arrow = Path().apply {
        // Chevron pointing up, centred on (0,0), in dp.
        moveTo(0f, -20f); lineTo(14f, 14f); lineTo(0f, 6f); lineTo(-14f, 14f); close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2.5f; strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x221E88E5 }

    override fun draw(canvas: Canvas, mapView: MapView, shadowPass: Boolean) {
        if (shadowPass) return
        val p = position ?: return
        mapView.projection.toPixels(p, pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        if (accuracyM > 15) {
            val r = mapView.projection.metersToPixels(accuracyM)
            canvas.drawCircle(x, y, r, accuracyPaint)
        }
        if (courseDeg.isNaN()) {
            canvas.drawCircle(x, y, 11 * density, stroke.apply { strokeWidth = 3 * density })
            canvas.drawCircle(x, y, 9 * density, fill)
            return
        }
        canvas.save()
        canvas.translate(x, y)
        // osmdroid has already rotated the canvas by the map orientation.
        canvas.rotate(courseDeg.toFloat())
        canvas.scale(density * 1.2f, density * 1.2f)
        canvas.save()
        canvas.translate(1.5f, 2.5f)
        canvas.drawPath(arrow, shadow)
        canvas.restore()
        canvas.drawPath(arrow, fill)
        stroke.strokeWidth = 2.5f
        canvas.drawPath(arrow, stroke)
        canvas.restore()
    }
}
