package uz.camdect.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import uz.camdect.core.CameraDb
import uz.camdect.core.CameraIndex
import uz.camdect.core.Control
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
    /** Text under a marker when zoomed in: name and purpose, e.g. "Lochin · red light". */
    var labelFor: (CameraDb, Int) -> String = { db, i -> "#${db.typeCode(i)}" }
    private val labelCache = HashMap<Int, String>()
    private var labelCacheDb: CameraDb? = null
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
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2A38.toInt() }
    private val cone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33E53935 }
    private val coneHot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66E53935 }
    private val coneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x99E53935.toInt(); strokeWidth = 1.5f * density
    }
    private val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF263238.toInt() }
    private val badgeBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1565C0.toInt() }
    private val badgeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val badgeRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    private val badgeRedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFE53935.toInt(); strokeWidth = 1.6f * density
    }
    private val badgeYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }
    private val badgeGreen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF43A047.toInt() }
    private val badgeRect = RectF()
    private val path = Path()
    private val oval = RectF()
    private var showCones = false
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
        showCones = mapView.zoomLevelDouble >= CONE_ZOOM
        if (showCones) {
            // Cones first so every marker sits on top of all danger areas.
            val pxPerMetre = proj.metersToPixels(1f)
            for (k in 0 until visible.size) {
                val i = visible[k]
                geo.setCoords(db.latDeg(i), db.lonDeg(i))
                proj.toPixels(geo, pt)
                drawCone(canvas, db, i, pt.x.toFloat(), pt.y.toFloat(), db.coverageM(i) * pxPerMetre)
            }
        }
        for (k in 0 until visible.size) {
            val i = visible[k]
            geo.setCoords(db.latDeg(i), db.lonDeg(i))
            proj.toPixels(geo, pt)
            drawCamera(canvas, db, i, pt.x.toFloat(), pt.y.toFloat(), r, rotation)
        }
    }

    /**
     * Danger area: a wedge from the camera along the direction its lens points, as long as the
     * coverage distance stored in the database. The database has no width, so it is fixed.
     */
    private fun drawCone(canvas: Canvas, db: CameraDb, i: Int, x: Float, y: Float, radius: Float) {
        // Pixel space here is north-up (osmdroid rotates the canvas itself);
        // Android arc angles start at east and run clockwise, so bearing b is angle b - 90.
        val start = (db.lensBearing(i) - 90.0 - CONE_DEG / 2).toFloat()
        oval.set(x - radius, y - radius, x + radius, y + radius)
        path.reset()
        path.moveTo(x, y)
        path.arcTo(oval, start, CONE_DEG)
        path.close()
        canvas.drawPath(path, if (i == highlighted) coneHot else cone)
        canvas.drawPath(path, coneEdge)
    }

    private fun drawCamera(canvas: Canvas, db: CameraDb, i: Int, x: Float, y: Float, r: Float, rotation: Float) {
        if (i == highlighted) canvas.drawCircle(x, y, r * 2.2f, glow)
        // Lens: a dark triangle on the side of the marker the camera faces.
        val a = Math.toRadians(db.lensBearing(i))
        val ux = sin(a).toFloat()
        val uy = -cos(a).toFloat()
        path.reset()
        path.moveTo(x + ux * r * 1.9f, y + uy * r * 1.9f)
        path.lineTo(x + ux * r * 0.6f - uy * r * 0.75f, y + uy * r * 0.6f + ux * r * 0.75f)
        path.lineTo(x + ux * r * 0.6f + uy * r * 0.75f, y + uy * r * 0.6f - ux * r * 0.75f)
        path.close()
        canvas.drawPath(path, lens)
        val limit = db.speedLimit[i].toInt()
        canvas.drawCircle(x, y, r, if (limit > 0) fill else fillNoLimit)
        canvas.drawCircle(x, y, r, ring)
        canvas.save()
        canvas.rotate(-rotation, x, y)
        if (limit > 0) canvas.drawText(limit.toString(), x, y + text.textSize / 3, text)
        drawBadges(canvas, db, i, x + r * 0.8f, y - r * 1.9f, r * 0.95f)
        if (showLabels) {
            if (labelCacheDb !== db) { labelCache.clear(); labelCacheDb = db }
            val name = labelCache.getOrPut(i) { labelFor(db, i) }
            val ty = y + r + label.textSize * 1.1f
            canvas.drawText(name, x, ty, labelHalo)
            canvas.drawText(name, x, ty, label)
        }
        canvas.restore()
    }

    /** Small icons above the marker for what the camera checks besides speed. */
    private fun drawBadges(canvas: Canvas, db: CameraDb, i: Int, left: Float, top: Float, size: Float) {
        var bx = left
        val gap = size * 0.2f
        if (db.hasControl(i, Control.RED_LIGHT)) {
            // Traffic light: dark body with red / yellow / green lamps.
            badgeRect.set(bx, top, bx + size * 0.6f, top + size * 1.4f)
            canvas.drawRoundRect(badgeRect, size * 0.15f, size * 0.15f, badgeBg)
            val cx = bx + size * 0.3f
            val lr = size * 0.17f
            canvas.drawCircle(cx, top + size * 0.28f, lr, badgeRed)
            canvas.drawCircle(cx, top + size * 0.7f, lr, badgeYellow)
            canvas.drawCircle(cx, top + size * 1.12f, lr, badgeGreen)
            bx += size * 0.6f + gap
        }
        if (db.hasControl(i, Control.CROSSWALK)) {
            // Zebra: blue square with white stripes.
            badgeRect.set(bx, top, bx + size, top + size)
            canvas.drawRoundRect(badgeRect, size * 0.15f, size * 0.15f, badgeBlue)
            val stripe = size / 7f
            for (k in 0 until 3) {
                val sx = bx + stripe * (1 + k * 2)
                canvas.drawRect(sx, top + size * 0.2f, sx + stripe, top + size * 0.8f, badgeWhite)
            }
            bx += size + gap
        }
        if (db.hasControl(i, Control.NO_STOPPING)) {
            // No stopping: blue disc, red ring and red cross.
            val c = size / 2
            canvas.drawCircle(bx + c, top + c, c, badgeBlue)
            canvas.drawCircle(bx + c, top + c, c - badgeRedLine.strokeWidth / 2, badgeRedLine)
            val d = c * 0.62f
            canvas.drawLine(bx + c - d, top + c - d, bx + c + d, top + c + d, badgeRedLine)
            canvas.drawLine(bx + c + d, top + c - d, bx + c - d, top + c + d, badgeRedLine)
        }
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
        private const val CONE_ZOOM = 14.0
        private const val CONE_DEG = 20f
        private const val MAX_DRAWN = 2500
    }
}
