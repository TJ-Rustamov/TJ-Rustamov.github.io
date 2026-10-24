package uz.camdect.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import uz.camdect.core.CameraDb
import uz.camdect.core.CameraIndex
import uz.camdect.core.IntList
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws cameras in the visible area directly on the canvas (much cheaper than
 * thousands of Marker objects, so it stays smooth on old phones).
 */
class CameraOverlay(
    private val density: Float,
    private val onTap: (Int) -> Unit,
) : Overlay() {
    var index: CameraIndex? = null
    var highlighted: Int = -1
    /** User names per type code (e.g. 237 -> "Lochin"), drawn under markers when zoomed in. */
    var typeNames: Map<Int, String> = emptyMap()
    /** True when zoomed out too far to draw individual cameras. */
    var tooFar = false
        private set

    private val visible = IntList(512)
    private val pt = Point()
    private val geo = GeoPoint(0.0, 0.0)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    private val fillNoLimit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2 * density
    }
    private val dir = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt(); strokeWidth = 3 * density; strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = 10 * density; isFakeBoldText = true
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2A38.toInt(); textAlign = Paint.Align.CENTER; textSize = 10 * density; isFakeBoldText = true
    }
    private val labelHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDDFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = 10 * density; isFakeBoldText = true
        style = Paint.Style.STROKE; strokeWidth = 3 * density
    }
    private var showLabels = false
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFEB3B }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val idx = index ?: return
        tooFar = mapView.zoomLevelDouble < MIN_ZOOM
        if (tooFar) return
        val box = mapView.boundingBox
        idx.inBox(box.latSouth, box.lonWest, box.latNorth, box.lonEast, visible, MAX_DRAWN)
        val db = idx.db
        val proj = mapView.projection
        val r = 9 * density
        // osmdroid draws overlays on a canvas already rotated by the map orientation:
        // geometry needs no correction, text is counter-rotated to stay upright.
        val rotation = mapView.mapOrientation
        showLabels = mapView.zoomLevelDouble >= LABEL_ZOOM
        for (k in 0 until visible.size) {
            val i = visible[k]
            geo.setCoords(db.latDeg(i), db.lonDeg(i))
            proj.toPixels(geo, pt)
            drawCamera(canvas, db, i, pt.x.toFloat(), pt.y.toFloat(), r, rotation)
        }
    }

    private fun drawCamera(canvas: Canvas, db: CameraDb, i: Int, x: Float, y: Float, r: Float, rotation: Float) {
        if (i == highlighted) canvas.drawCircle(x, y, r * 2.2f, glow)
        // Tick showing the direction of traffic the camera watches.
        val a = Math.toRadians(db.heading[i].toDouble())
        canvas.drawLine(x, y, x + (sin(a) * r * 2).toFloat(), y - (cos(a) * r * 2).toFloat(), dir)
        val limit = db.speedLimit[i].toInt()
        canvas.drawCircle(x, y, r, if (limit > 0) fill else fillNoLimit)
        canvas.drawCircle(x, y, r, ring)
        canvas.save()
        canvas.rotate(-rotation, x, y)
        if (limit > 0) canvas.drawText(limit.toString(), x, y + text.textSize / 3, text)
        if (showLabels) {
            val code = db.typeCode(i)
            val name = typeNames[code] ?: "#$code"
            val ty = y + r + label.textSize * 1.1f
            canvas.drawText(name, x, ty, labelHalo)
            canvas.drawText(name, x, ty, label)
        }
        canvas.restore()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val idx = index ?: return false
        if (tooFar) return false
        val db = idx.db
        val proj = mapView.projection
        // MapView already un-rotates touch events, so they match toPixels() space.
        var best = -1
        var bestD = 24 * density
        for (k in 0 until visible.size) {
            val i = visible[k]
            geo.setCoords(db.latDeg(i), db.lonDeg(i))
            proj.toPixels(geo, pt)
            val d = hypot((pt.x - e.x).toDouble(), (pt.y - e.y).toDouble()).toFloat()
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) onTap(best)
        return best >= 0
    }

    companion object {
        const val MIN_ZOOM = 11.0
        private const val LABEL_ZOOM = 15.0
        private const val MAX_DRAWN = 2500
    }
}
