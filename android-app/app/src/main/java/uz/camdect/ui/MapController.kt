package uz.camdect.ui

import android.content.Context
import android.graphics.RectF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import uz.camdect.core.CameraDb
import uz.camdect.core.CameraIndex
import uz.camdect.core.Control
import uz.camdect.core.Geo
import uz.camdect.core.IntList
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.tan

/**
 * Everything drawn on the MapLibre map: camera markers, danger cones, badges, labels and the
 * car arrow, plus the follow camera and offline downloads. Labels stay upright when the map
 * rotates because MapLibre places text separately from the rotated map.
 */
class MapController(
    private val context: Context,
    private val mapView: MapView,
    /** OSM name key for map labels: "uz", "uz-Cyrl", "ru" or "en". */
    private val language: String,
    private val labelFor: (CameraDb, Int) -> String,
    private val onCameraTap: (Int) -> Unit,
    private val onUserGesture: () -> Unit,
    private val onCameraMoved: () -> Unit,
) {
    var map: MapLibreMap? = null
        private set
    private var style: Style? = null
    private val density = context.resources.displayMetrics.density

    private var index: CameraIndex? = null
    private var highlighted = -1
    private val visible = IntList(1024)

    private var pendingCenter: LatLng? = null

    // What the camera features were last built for, to skip rebuilding while following.
    private var builtBounds: LatLngBounds? = null
    private var builtZoom = -1.0
    private var builtAt = 0L

    val bearing: Double get() = map?.cameraPosition?.bearing ?: 0.0
    val zoom: Double get() = map?.cameraPosition?.zoom ?: 0.0

    init {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = false // we have our own compass button
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.setAttributionMargins((8 * density).toInt(), 0, 0, (96 * density).toInt())
            m.uiSettings.setLogoMargins((8 * density).toInt(), 0, 0, (96 * density).toInt())
            m.setMinZoomPreference(4.0)
            m.setMaxZoomPreference(19.0)
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(pendingCenter ?: TASHKENT, 13.0))
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserGesture()
            }
            m.addOnCameraMoveListener { onCameraMoved() }
            m.addOnCameraIdleListener { refreshCameras(); onCameraMoved() }
            m.addOnMapClickListener { latLng -> handleTap(latLng) }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                style = s
                localizeLabels(s)
                addCameraLayers(s)
                refreshCameras(force = true)
            }
        }
        // Keep up to 500 MB of viewed map so driven areas also work offline.
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(500L * 1024 * 1024, object : OfflineManager.FileSourceCallback {
            override fun onSuccess() {}
            override fun onError(message: String) {}
        })
    }

    fun setCameras(newIndex: CameraIndex?) {
        index = newIndex
        refreshCameras(force = true)
    }

    fun setHighlight(camera: Int) {
        if (camera == highlighted) return
        highlighted = camera
        refreshCameras(force = true)
    }

    // --- Map labels in the app language ---

    private fun localizeLabels(s: Style) {
        val nameExpr = when (language) {
            "uz" -> coalesce(get("name:uz"), get("name:latin"), get("name"))
            "uz-Cyrl" -> coalesce(get("name:uz-Cyrl"), get("name:ru"), get("name"))
            "ru" -> coalesce(get("name:ru"), get("name"))
            else -> coalesce(get("name:en"), get("name:latin"), get("name"))
        }
        for (layer in s.layers) {
            if (layer !is SymbolLayer) continue
            val tf = layer.textField
            val current = (if (tf.isExpression) tf.expression?.toString() else tf.value?.toString()) ?: continue
            // Only place/street name labels; leave road numbers, house numbers etc. alone.
            if (current.contains("name")) layer.setProperties(textField(nameExpr))
        }
    }

    // --- Camera layers ---

    private fun addCameraLayers(s: Style) {
        s.addImage(MapIcons.CAR, MapIcons.car(density))
        s.addImage(MapIcons.LENS, MapIcons.lens(density))
        s.addImage(MapIcons.RED_LIGHT, MapIcons.redLight(density))
        s.addImage(MapIcons.CROSSWALK, MapIcons.crosswalk(density))
        s.addImage(MapIcons.NO_STOPPING, MapIcons.noStopping(density))

        s.addSource(GeoJsonSource(SRC_CONES))
        s.addSource(GeoJsonSource(SRC_CAMS))
        s.addSource(GeoJsonSource(SRC_CAR))
        val font = arrayOf("Noto Sans Bold")

        s.addLayer(FillLayer(L_CONE, SRC_CONES).withProperties(
            fillColor(0xFFE53935.toInt()),
            fillOpacity(switchCase(get("hot"), literal(0.45f), literal(0.2f))),
        ).apply { minZoom = CONE_ZOOM })
        s.addLayer(LineLayer(L_CONE_EDGE, SRC_CONES).withProperties(
            lineColor(0xCCE53935.toInt()), lineWidth(1.5f),
        ).apply { minZoom = CONE_ZOOM })
        s.addLayer(SymbolLayer(L_LENS, SRC_CAMS).withProperties(
            iconImage(MapIcons.LENS),
            iconRotate(get("lens")),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true), iconIgnorePlacement(true),
        ))
        s.addLayer(CircleLayer(L_CIRCLE, SRC_CAMS).withProperties(
            circleRadius(interpolate(linear(), zoom(), stop(10, 6f), stop(14, 14f))),
            circleColor(switchCase(eq(get("limit"), literal(0)), literal("#1E88E5"), literal("#E53935"))),
            circleStrokeColor(switchCase(get("hot"), literal("#FFEB3B"), literal("#FFFFFF"))),
            circleStrokeWidth(switchCase(get("hot"), literal(4f), literal(2.5f))),
        ))
        s.addLayer(SymbolLayer(L_LIMIT, SRC_CAMS).withProperties(
            textField(get("limitText")), textFont(font),
            textSize(interpolate(linear(), zoom(), stop(10, 8f), stop(14, 15f))),
            textColor(0xFFFFFFFF.toInt()),
            textAllowOverlap(true), textIgnorePlacement(true),
        ))
        // Badges sit above the marker, side by side in a fixed order.
        badgeLayer(s, L_BADGE_RL, "rl", MapIcons.RED_LIGHT, -16f)
        badgeLayer(s, L_BADGE_CW, "cw", MapIcons.CROSSWALK, 0f)
        badgeLayer(s, L_BADGE_NS, "ns", MapIcons.NO_STOPPING, 16f)
        s.addLayer(SymbolLayer(L_LABEL, SRC_CAMS).withProperties(
            textField(get("label")), textFont(font), textSize(13f),
            textColor(0xFF1E2A38.toInt()), textHaloColor(0xFFFFFFFF.toInt()), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.4f)),
            textAllowOverlap(false),
        ).apply { minZoom = LABEL_ZOOM })
        s.addLayer(SymbolLayer(L_CAR, SRC_CAR).withProperties(
            iconImage(MapIcons.CAR),
            iconRotate(get("h")),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true), iconIgnorePlacement(true),
        ))
    }

    private fun badgeLayer(s: Style, id: String, key: String, icon: String, dx: Float) {
        s.addLayer(SymbolLayer(id, SRC_CAMS).withProperties(
            iconImage(icon),
            iconOffset(arrayOf(dx, -30f)),
            iconAllowOverlap(true), iconIgnorePlacement(true),
        ).apply {
            setFilter(eq(get(key), literal(true)))
            minZoom = BADGE_ZOOM
        })
    }

    /**
     * Rebuilds the camera features for the visible area (plus a margin). While following, the
     * camera moves every frame, so this only rebuilds when the view left the area built last
     * time, the zoom changed noticeably, or a second has passed.
     */
    private fun refreshCameras(force: Boolean = false) {
        val s = style ?: return
        val m = map ?: return
        val view = m.projection.visibleRegion.latLngBounds
        val now = android.os.SystemClock.elapsedRealtime()
        val built = builtBounds
        if (!force && built != null && now - builtAt < 1000 &&
            abs(m.cameraPosition.zoom - builtZoom) < 0.5 &&
            built.contains(view.northEast) && built.contains(view.southWest)
        ) return
        val camsSrc = s.getSourceAs<GeoJsonSource>(SRC_CAMS) ?: return
        val conesSrc = s.getSourceAs<GeoJsonSource>(SRC_CONES) ?: return
        val idx = index
        val z = m.cameraPosition.zoom
        if (idx == null || z < MIN_CAMERA_ZOOM) {
            camsSrc.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            conesSrc.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            builtBounds = null
            return
        }
        val b = view
        val padLat = (b.latitudeNorth - b.latitudeSouth) * 0.25
        val padLon = (b.longitudeEast - b.longitudeWest) * 0.25
        idx.inBox(b.latitudeSouth - padLat, b.longitudeWest - padLon, b.latitudeNorth + padLat, b.longitudeEast + padLon, visible, MAX_CAMERAS)
        builtBounds = LatLngBounds.from(b.latitudeNorth + padLat, b.longitudeEast + padLon, b.latitudeSouth - padLat, b.longitudeWest - padLon)
        builtZoom = z
        builtAt = now
        val db = idx.db
        val cams = ArrayList<Feature>(visible.size)
        val cones = ArrayList<Feature>(visible.size)
        val drawn = HashSet<Long>()
        val withCones = z >= CONE_ZOOM - 0.5
        for (k in 0 until visible.size) {
            val i = visible[k]
            val lens = db.lensBearing(i)
            // Two records of one physical camera share position and lens: draw it once
            // (the highlighted one wins, so the alert target always shows).
            val key = (db.lat[i].toLong() shl 32) xor (db.lon[i].toLong() shl 9) xor round(lens / 5).toLong()
            if (!drawn.add(key) && i != highlighted) continue
            val hot = i == highlighted
            val limit = db.speedLimit[i].toInt()
            val f = Feature.fromGeometry(Point.fromLngLat(db.lonDeg(i), db.latDeg(i)))
            f.addNumberProperty("i", i)
            f.addNumberProperty("limit", limit)
            f.addStringProperty("limitText", if (limit > 0) limit.toString() else "")
            f.addNumberProperty("lens", lens)
            f.addBooleanProperty("hot", hot)
            f.addStringProperty("label", labelFor(db, i))
            f.addBooleanProperty("rl", db.hasControl(i, Control.RED_LIGHT))
            f.addBooleanProperty("cw", db.hasControl(i, Control.CROSSWALK))
            f.addBooleanProperty("ns", db.hasControl(i, Control.NO_STOPPING))
            cams += f
            if (withCones) cones += coneFeature(db, i, lens, hot)
        }
        camsSrc.setGeoJson(FeatureCollection.fromFeatures(cams))
        conesSrc.setGeoJson(FeatureCollection.fromFeatures(cones))
    }

    /** Danger area: apex at the camera, arc at the stored coverage distance around the lens bearing. */
    private fun coneFeature(db: CameraDb, i: Int, lens: Double, hot: Boolean): Feature {
        val lat = db.latDeg(i)
        val lon = db.lonDeg(i)
        val apex = Point.fromLngLat(lon, lat)
        val ring = ArrayList<Point>(12)
        ring += apex
        var a = lens - CONE_DEG / 2
        while (a <= lens + CONE_DEG / 2 + 0.01) {
            val p = Geo.destination(lat, lon, db.coverageM(i).toDouble(), a)
            ring += Point.fromLngLat(p[1], p[0])
            a += CONE_DEG / 8
        }
        ring += apex
        val f = Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
        f.addBooleanProperty("hot", hot)
        return f
    }

    private fun handleTap(latLng: LatLng): Boolean {
        val m = map ?: return false
        val p = m.projection.toScreenLocation(latLng)
        val r = 24 * density
        val hits = m.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), L_CIRCLE, L_LABEL)
        val i = hits.firstOrNull { it.hasProperty("i") }?.getNumberProperty("i")?.toInt() ?: return false
        onCameraTap(i)
        return true
    }

    // --- Car arrow and follow camera ---

    fun setCar(lat: Double, lon: Double, headingDeg: Double) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_CAR) ?: return
        val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
        f.addNumberProperty("h", if (headingDeg.isNaN()) 0.0 else headingDeg)
        src.setGeoJson(f)
    }

    /**
     * Follow camera. Heading-up turns the map so we drive "up" and puts the car in the lower
     * part of the screen (top padding), like a racing game; north-up keeps north up.
     */
    fun follow(lat: Double, lon: Double, headingDeg: Double, headingUp: Boolean, targetZoom: Double, snap: Boolean) {
        val m = map
        if (m == null) { pendingCenter = LatLng(lat, lon); return }
        val cur = m.cameraPosition
        val z = if (snap) targetZoom else cur.zoom + (targetZoom - cur.zoom) * 0.08
        val bearing = if (headingUp && !headingDeg.isNaN()) headingDeg else 0.0
        val top = if (headingUp) mapView.height * 0.45 else 0.0
        m.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(lat, lon)).zoom(z).bearing(bearing).tilt(0.0)
                .padding(0.0, top, 0.0, 0.0).build()
        ))
    }

    fun resetNorth() {
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.bearingTo(0.0))
    }

    // --- Offline download of the visible area ---

    /** Visible area and an estimate of the tiles needed for zoom [MIN_OFFLINE_ZOOM]..[MAX_OFFLINE_ZOOM]. */
    fun visibleAreaEstimate(): Pair<LatLngBounds, Long>? {
        val m = map ?: return null
        val b = m.projection.visibleRegion.latLngBounds
        var tiles = 0L
        for (z in MIN_OFFLINE_ZOOM.toInt()..MAX_OFFLINE_ZOOM.toInt()) {
            val x0 = tileX(b.longitudeWest, z); val x1 = tileX(b.longitudeEast, z)
            val y0 = tileY(b.latitudeNorth, z); val y1 = tileY(b.latitudeSouth, z)
            tiles += (abs(x1 - x0) + 1L) * (abs(y1 - y0) + 1L)
        }
        return b to tiles
    }

    fun downloadArea(
        bounds: LatLngBounds,
        name: String,
        onProgress: (percent: Int) -> Unit,
        onDone: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val def = OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, MIN_OFFLINE_ZOOM, MAX_OFFLINE_ZOOM, density)
        OfflineManager.getInstance(context).createOfflineRegion(def, name.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val req = max(1L, status.requiredResourceCount)
                        onProgress(min(100L, status.completedResourceCount * 100 / req).toInt())
                        if (status.isComplete) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            onDone()
                        }
                    }
                    override fun onError(error: OfflineRegionError) = onFailed(error.message)
                    override fun mapboxTileCountLimitExceeded(limit: Long) = onFailed("Tile limit $limit exceeded")
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) = onFailed(error)
        })
    }

    /** Deletes all downloaded areas; calls back with how many were removed. */
    fun deleteDownloads(onDone: (Int) -> Unit) {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions ?: emptyArray()
                if (regions.isEmpty()) { onDone(0); return }
                var left = regions.size
                for (r in regions) r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { if (--left == 0) onDone(regions.size) }
                    override fun onError(error: String) { if (--left == 0) onDone(regions.size) }
                })
            }
            override fun onError(error: String) = onDone(0)
        })
    }

    fun countDownloads(onCount: (Int) -> Unit) {
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = onCount(offlineRegions?.size ?: 0)
            override fun onError(error: String) = onCount(0)
        })
    }

    private fun tileX(lon: Double, z: Int): Long = floor((lon + 180.0) / 360.0 * (1 shl z)).toLong()
    private fun tileY(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return floor((1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / Math.PI) / 2.0 * (1 shl z)).toLong()
    }

    companion object {
        const val STYLE_URL = uz.camdect.data.MapPrefetcher.STYLE_URL
        private val TASHKENT = LatLng(41.3111, 69.2797)

        private const val SRC_CAMS = "camdect-cams"
        private const val SRC_CONES = "camdect-cones"
        private const val SRC_CAR = "camdect-car"
        private const val L_CONE = "camdect-cone"
        private const val L_CONE_EDGE = "camdect-cone-edge"
        private const val L_LENS = "camdect-lens"
        private const val L_CIRCLE = "camdect-circle"
        private const val L_LIMIT = "camdect-limit"
        private const val L_BADGE_RL = "camdect-badge-rl"
        private const val L_BADGE_CW = "camdect-badge-cw"
        private const val L_BADGE_NS = "camdect-badge-ns"
        private const val L_LABEL = "camdect-label"
        private const val L_CAR = "camdect-car"

        const val MIN_CAMERA_ZOOM = 10.0
        private const val CONE_ZOOM = 14f
        private const val BADGE_ZOOM = 13f
        private const val LABEL_ZOOM = 14f
        private const val CONE_DEG = 20.0
        private const val MAX_CAMERAS = 1500
        const val MIN_OFFLINE_ZOOM = 10.0
        /** OpenFreeMap vector tiles stop at 14; closer zooms are drawn from them. */
        const val MAX_OFFLINE_ZOOM = 14.0
    }
}
