package uz.camdect.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import uz.camdect.R
import uz.camdect.core.Geo
import uz.camdect.core.HeadingFilter
import uz.camdect.data.AppLanguage
import uz.camdect.data.CameraRepository
import uz.camdect.data.MapPrefetcher
import uz.camdect.data.Prefs
import uz.camdect.data.TypeLabels
import uz.camdect.service.Announcer
import uz.camdect.service.DriveState
import uz.camdect.service.RadarService
import kotlin.math.abs
import kotlin.math.atan2

class MainActivity : AppCompatActivity(), LocationListener {

    /** HEADING_UP: map turns with the car, arrow fixed low on screen (racing-game style). */
    private enum class ViewMode { HEADING_UP, NORTH_UP, FREE }

    private lateinit var prefs: Prefs
    private var mapView: MapView? = null
    private var mapController: MapController? = null
    private lateinit var speedView: TextView
    private lateinit var statusView: TextView
    private lateinit var subStatusView: TextView
    private lateinit var limitBadge: TextView
    private lateinit var hintView: TextView
    private lateinit var modeLabel: TextView
    private lateinit var compass: ImageButton
    private lateinit var driveButton: Button
    private lateinit var infoScroll: ScrollView
    private lateinit var infoList: LinearLayout

    private var repoState: CameraRepository.State = CameraRepository.State.Loading
    private var startAfterPermission = false

    private var mode = ViewMode.HEADING_UP
    private var followMode = ViewMode.HEADING_UP
    private var infoTabShown = false
    // Back closes the camera info page; enabled only while it is open.
    private val closeInfoOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = showInfoTab(false)
    }

    // Latest fix from our own GPS listener (UI only; alerts come from RadarService).
    private var fix: Location? = null
    private var fixAt = 0L // elapsedRealtime when the fix arrived
    private var course = Double.NaN
    private var speedKmh = 0
    private var shownLat = Double.NaN
    private var shownLon = Double.NaN
    private var shownCourse = Double.NaN
    private var snapCamera = true
    private var lastTouchAt = 0L

    // Heading: gyro for instant turns, GPS/compass to correct drift (see HeadingFilter).
    private lateinit var sensors: SensorManager
    private val headingFilter = HeadingFilter()
    private var declination = Float.NaN
    private val absRotation = FloatArray(9)
    private val gyroRotation = FloatArray(9)
    private var gyroWasFlat: Boolean? = null
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) = onSensor(e)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Frame loop: redraws the arrow/map ~30x per second with dead reckoning between GPS fixes.
    private var frameLoopRunning = false
    private var lastFrameAt = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt >= FRAME_MS) {
                lastFrameAt = now
                onFrame(now)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startUiLocation()
                if (startAfterPermission) startDriving()
            } else {
                Toast.makeText(this, R.string.perm_location_needed, Toast.LENGTH_LONG).show()
            }
        }

    private val pickDb = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDb(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.applySaved(this) // recreates once if the saved language is not active yet
        prefs = Prefs(this)
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        setupEdgeToEdge()
        setContentView(R.layout.activity_main)
        speedView = findViewById(R.id.speed)
        statusView = findViewById(R.id.status)
        subStatusView = findViewById(R.id.substatus)
        limitBadge = findViewById(R.id.limit_badge)
        hintView = findViewById(R.id.hint)
        modeLabel = findViewById(R.id.mode_label)
        compass = findViewById(R.id.btn_compass)
        driveButton = findViewById(R.id.btn_drive)
        infoScroll = findViewById(R.id.info_scroll)
        infoList = findViewById(R.id.info_list)
        applyInsets()

        followMode = if (prefs.sp.getString("follow_mode", "heading") == "north") ViewMode.NORTH_UP else ViewMode.HEADING_UP
        mode = followMode
        setupMap(savedInstanceState)

        statusView.setOnClickListener { if (repoState is CameraRepository.State.Missing) pickDb.launch(arrayOf("*/*")) }
        driveButton.setOnClickListener {
            if (DriveState.flow.value.running) RadarService.stop(this) else ensurePermissionsAndStart()
        }
        findViewById<Button>(R.id.btn_center).setOnClickListener { setMode(followMode) }
        compass.setOnClickListener { onCompassTap() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu(it) }
        onBackPressedDispatcher.addCallback(this, closeInfoOnBack)
        updateModeUi()
        showInfoTab(false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { CameraRepository.state.collect { onRepoState(it) } }
                launch { DriveState.flow.collect { onDriveState(it) } }
                launch { MapPrefetcher.get(this@MainActivity).progress.collect { updateSubStatus() } }
            }
        }

        if (!hasLocationPermission()) ensurePermissionsAndStart(startService = false)
    }

    // --- Layout / insets / tabs ---

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        // Same look on every Android version: map under transparent system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ContextCompat.getColor(this, R.color.primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun applyInsets() {
        val top = findViewById<View>(R.id.top_area)
        val bottom = findViewById<View>(R.id.bottom_area)
        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            (top.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = bars.top; leftMargin = bars.left; rightMargin = bars.right
            }
            bottom.setPadding(bars.left, 0, bars.right, bars.bottom)
            // Info list scrolls between the status bar and the tab bar.
            val pad = (16 * density).toInt()
            infoList.setPadding(pad, bars.top + pad, pad, bars.bottom + pad)
            top.requestLayout()
            insets
        }
    }

    /** Camera info page (opened from the ⋮ menu) over the map. */
    private fun showInfoTab(show: Boolean) {
        infoTabShown = show
        closeInfoOnBack.isEnabled = show
        infoScroll.visibility = if (show) View.VISIBLE else View.GONE
        val mapUi = if (show) View.GONE else View.VISIBLE
        findViewById<View>(R.id.top_area).visibility = mapUi
        findViewById<View>(R.id.bottom_area).visibility = mapUi
        if (show) {
            hintView.visibility = View.GONE
            CameraInfoView.fill(infoList, CameraRepository.ready?.db) { showInfoTab(false) }
            infoScroll.scrollTo(0, 0)
        } else {
            onMapMoved()
        }
    }

    // --- Map ---

    /** MapLibre needs OpenGL ES 3; very old phones get a plain screen (alerts still work). */
    private fun supportsMap(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        val container = findViewById<FrameLayout>(R.id.map_container)
        if (!supportsMap()) {
            container.addView(TextView(this).apply {
                setText(R.string.no_gl)
                gravity = Gravity.CENTER
                textSize = 16f
                setPadding(48, 0, 48, 0)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            compass.visibility = View.GONE
            modeLabel.visibility = View.GONE
            return
        }
        val mv = MapView(this)
        container.addView(mv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mv.onCreate(savedInstanceState)
        mapView = mv
        mapController = MapController(
            context = this,
            mapView = mv,
            language = AppLanguage.tag(this),
            labelFor = { db, i -> TypeLabels.labelWithControls(this, db, i) },
            onCameraTap = { i -> showCamera(i) },
            onUserGesture = {
                lastTouchAt = SystemClock.elapsedRealtime()
                if (mode != ViewMode.FREE) setMode(ViewMode.FREE)
            },
            onCameraMoved = { onMapMoved() },
        )
    }

    private fun onMapMoved() {
        val mc = mapController ?: return
        compass.rotation = -mc.bearing.toFloat()
        hintView.visibility = if (!infoTabShown && repoState is CameraRepository.State.Ready &&
            mc.zoom < MapController.MIN_CAMERA_ZOOM
        ) View.VISIBLE else View.GONE
    }

    private fun setMode(newMode: ViewMode) {
        mode = newMode
        if (newMode != ViewMode.FREE && newMode != followMode) {
            followMode = newMode
            prefs.sp.edit().putString("follow_mode", if (newMode == ViewMode.NORTH_UP) "north" else "heading").apply()
        }
        if (newMode != ViewMode.FREE) {
            snapCamera = true
            if (!shownLat.isNaN()) moveCamera()
        }
        updateModeUi()
    }

    private fun onCompassTap() {
        when (mode) {
            ViewMode.HEADING_UP -> setMode(ViewMode.NORTH_UP)
            ViewMode.NORTH_UP -> setMode(ViewMode.HEADING_UP)
            ViewMode.FREE -> mapController?.resetNorth()
        }
    }

    private fun updateModeUi() {
        modeLabel.setText(
            when (mode) {
                ViewMode.HEADING_UP -> R.string.mode_heading
                ViewMode.NORTH_UP -> R.string.mode_north
                ViewMode.FREE -> R.string.mode_free
            }
        )
    }

    private fun moveCamera() {
        val mc = mapController ?: return
        if (mode == ViewMode.FREE) return
        mc.follow(shownLat, shownLon, shownCourse, mode == ViewMode.HEADING_UP, autoZoom(), snapCamera)
        snapCamera = false
    }

    /** Closer when slow, wider on the highway. */
    private fun autoZoom(): Double {
        val s = speedKmh.coerceIn(20, 110)
        return 17.0 - (s - 20) / 90.0 * 1.6
    }

    // --- UI GPS feed ---

    @SuppressLint("MissingPermission")
    private fun startUiLocation() {
        if (!hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(this)
        if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            // 0 ms: every fix the chip produces (1-10 Hz depending on phone).
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this, Looper.getMainLooper())
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        }
        if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this, Looper.getMainLooper()) }
        }
    }

    private fun stopUiLocation() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        val last = fix
        // Prefer GPS; ignore a coarse network fix once we have a recent good GPS one.
        if (location.provider != LocationManager.GPS_PROVIDER && last != null &&
            last.provider == LocationManager.GPS_PROVIDER && location.time - last.time < 10_000
        ) return
        fix = location
        fixAt = SystemClock.elapsedRealtime()
        val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        speedKmh = (speedMps * 3.6).toInt()
        course = if (location.hasBearing() && speedMps >= GPS_COURSE_MIN_MPS) location.bearing.toDouble() else Double.NaN
        headingFilter.onGps(course, speedMps)
        // First fix ever: start downloading the map of this area for offline use.
        if (mapController != null) MapPrefetcher.get(this).onLocation(location.latitude, location.longitude)
        if (declination.isNaN()) declination = GeomagneticField(
            location.latitude.toFloat(), location.longitude.toFloat(), location.altitude.toFloat(), System.currentTimeMillis()
        ).declination
        // Back to following a while after the user stopped panning, like car navigation apps.
        if (mode == ViewMode.FREE && DriveState.flow.value.running &&
            SystemClock.elapsedRealtime() - lastTouchAt > REFOLLOW_MS
        ) setMode(followMode)
        speedView.text = speedKmh.toString()
        updateSubStatus()

        if (shownLat.isNaN()) {
            // First fix: jump there.
            shownLat = location.latitude; shownLon = location.longitude; shownCourse = arrowCourse()
            mapController?.setCar(shownLat, shownLon, shownCourse)
            if (mode == ViewMode.FREE) setMode(followMode) else { snapCamera = true; moveCamera() }
        }
    }

    private fun arrowCourse(): Double = if (headingFilter.heading.isNaN()) course else headingFilter.heading

    /**
     * Where we are *now*: the last fix pushed forward by speed × time since it arrived
     * (dead reckoning), then eased so corrections from new fixes do not jump.
     */
    private fun onFrame(now: Long) {
        val f = fix ?: return
        if (shownLat.isNaN() || infoTabShown) return
        val heading = arrowCourse()
        val speedMps = if (f.hasSpeed()) f.speed.toDouble() else 0.0
        var targetLat = f.latitude
        var targetLon = f.longitude
        if (speedMps >= GPS_COURSE_MIN_MPS && !heading.isNaN()) {
            val dt = ((now - fixAt) / 1000.0).coerceIn(0.0, MAX_PREDICT_S)
            val p = Geo.destination(f.latitude, f.longitude, speedMps * dt, heading)
            targetLat = p[0]; targetLon = p[1]
        }
        shownLat += (targetLat - shownLat) * POSITION_EASE
        shownLon += (targetLon - shownLon) * POSITION_EASE
        shownCourse = heading
        mapController?.setCar(shownLat, shownLon, shownCourse)
        moveCamera()
    }

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        frameLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun onSensor(e: SensorEvent) {
        val moving = speedKmh >= COMPASS_BELOW_KMH
        when (e.sensor.type) {
            // Gyro + accelerometer, no magnetometer: smooth and instant, arbitrary zero.
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(gyroRotation, e.values)
                val flat = isFlat(gyroRotation)
                headingFilter.onRelative(headingOf(gyroRotation, flat), SystemClock.elapsedRealtime(), reset = flat != gyroWasFlat)
                gyroWasFlat = flat
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(absRotation, e.values)
                headingFilter.onCompass(trueHeading(absRotation), moving)
            }
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD -> {
                if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) gravity = e.values.clone() else geomagnetic = e.values.clone()
                val g = gravity ?: return
                val m = geomagnetic ?: return
                if (SensorManager.getRotationMatrix(absRotation, null, g, m)) headingFilter.onCompass(trueHeading(absRotation), moving)
            }
        }
    }

    private fun isFlat(r: FloatArray) = abs(r[8]) > 0.75f

    private fun trueHeading(r: FloatArray): Double {
        val decl = if (declination.isNaN()) 0f else declination
        return (headingOf(r, isFlat(r)) + decl + 720) % 360
    }

    /**
     * Direction the phone faces in the rotation matrix's frame. Flat phone: the top edge of the
     * screen. Upright (car mount): the back of the phone, i.e. the windscreen direction.
     */
    @Suppress("DEPRECATION")
    private fun headingOf(r: FloatArray, flat: Boolean): Double {
        // Columns of r are the device X, Y, Z axes in world (east, north, up) coordinates.
        val (east, north) = if (flat) {
            when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> -r[0] to -r[3]
                Surface.ROTATION_180 -> -r[1] to -r[4]
                Surface.ROTATION_270 -> r[0] to r[3]
                else -> r[1] to r[4]
            }
        } else {
            -r[2] to -r[5]
        }
        return (Math.toDegrees(atan2(east.toDouble(), north.toDouble())) + 360) % 360
    }

    private fun startSensors() {
        sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let {
            sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        val rv = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv != null) {
            sensors.registerListener(sensorListener, rv, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // --- State from repository / service ---

    private fun onRepoState(state: CameraRepository.State) {
        repoState = state
        when (state) {
            is CameraRepository.State.Loading -> statusView.setText(R.string.db_loading)
            is CameraRepository.State.Missing -> statusView.setText(R.string.db_missing)
            is CameraRepository.State.Error -> statusView.text = getString(R.string.db_error, state.message)
            is CameraRepository.State.Ready -> {
                mapController?.setCameras(state.index)
                onDriveState(DriveState.flow.value)
                if (infoTabShown) CameraInfoView.fill(infoList, state.db) { showInfoTab(false) }
            }
        }
        onMapMoved()
    }

    private fun onDriveState(s: DriveState.Snapshot) {
        driveButton.setText(if (s.running) R.string.stop else R.string.start)
        if (s.running && prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val ready = repoState as? CameraRepository.State.Ready
        val target = s.target
        val opposite = s.oppositeAhead
        if (ready != null) {
            statusView.text = when {
                !s.running -> getString(R.string.db_loaded, ready.db.version, ready.db.size)
                !s.hasFix -> getString(R.string.no_gps)
                target != null -> getString(R.string.camera_ahead, TypeLabels.labelWithControls(this, ready.db, target.camera), target.distanceM.toInt() / 10 * 10)
                // A camera just ahead that only checks the other lane: say so instead of "no cameras".
                opposite != null -> getString(R.string.opposite_ahead, TypeLabels.label(this, ready.db, opposite.camera), opposite.distanceM.toInt() / 10 * 10)
                else -> getString(R.string.no_camera)
            }
            statusView.alpha = if (target == null && opposite != null && s.running) 0.7f else 1f
        }
        showLimit(target?.speedLimitKmh ?: 0, target?.overspeed == true)
        mapController?.setHighlight(target?.camera ?: -1)
        updateSubStatus()
    }

    private fun updateSubStatus() {
        val running = DriveState.flow.value.running
        val alerts = getString(if (running) R.string.service_on else R.string.service_off)
        val f = fix
        val base = if (f == null) alerts else getString(R.string.gps_accuracy, f.accuracy.toInt(), alerts)
        val dl = MapPrefetcher.get(this).progress.value
        subStatusView.text = if (dl == null) base else base + "\n" + getString(R.string.map_downloading, dl.percent)
    }

    private fun showLimit(limit: Int, overspeed: Boolean) {
        if (limit <= 0) {
            limitBadge.visibility = View.GONE
            return
        }
        limitBadge.visibility = View.VISIBLE
        limitBadge.text = limit.toString()
        val d = resources.displayMetrics.density
        limitBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (overspeed) 0xFFFFCDD2.toInt() else 0xFFFFFFFF.toInt())
            setStroke((5 * d).toInt(), 0xFFE53935.toInt())
        }
    }

    /** Camera details: limit, which way it checks, from the front or behind, danger area. */
    private fun showCamera(i: Int) {
        val db = CameraRepository.ready?.db ?: return
        val code = db.typeCode(i)
        val limit = db.speedLimit[i].toInt()
        val heading = db.heading[i].toInt()
        val controls = TypeLabels.controlsText(this, db, i)
        val checks = if (controls.isEmpty()) getString(R.string.checks_speed_only) else getString(R.string.checks_also, controls)
        AlertDialog.Builder(this)
            .setTitle(TypeLabels.label(this, db, i))
            .setMessage(
                checks + "\n" + getString(
                    R.string.type_dialog_msg,
                    if (limit > 0) "$limit ${getString(R.string.kmh)}" else getString(R.string.no_limit),
                    compassWord(heading.toDouble()), heading,
                    getString(if (db.isRear(i)) R.string.seen_rear else R.string.seen_front),
                    db.coverageM(i), code, TypeLabels.count(db, code),
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** "southwest ↙" for 227°. */
    private fun compassWord(bearing: Double): String {
        val words = resources.getStringArray(R.array.compass_words)
        return words[(((bearing % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
    }

    // --- Offline maps ---

    private fun showOfflineDialog() {
        val mc = mapController ?: return
        val (bounds, tiles) = mc.visibleAreaEstimate() ?: return
        mc.countDownloads { count ->
            // Vector tiles average roughly 30 KB in cities.
            val mb = (tiles * 30 / 1024).toInt().coerceAtLeast(1)
            val tooBig = tiles > MAX_OFFLINE_TILES
            val builder = AlertDialog.Builder(this)
                .setTitle(R.string.menu_offline)
                .setMessage(if (tooBig) getString(R.string.offline_too_big) else getString(R.string.offline_msg, count, mb))
                .setNegativeButton(R.string.cancel, null)
            if (!tooBig) builder.setPositiveButton(R.string.offline_download) { _, _ -> startDownload(bounds) }
            if (count > 0) builder.setNeutralButton(R.string.offline_delete) { _, _ ->
                mc.deleteDownloads { n ->
                    MapPrefetcher.get(this).forgetAll()
                    Toast.makeText(this, getString(R.string.offline_deleted, n), Toast.LENGTH_LONG).show()
                }
            }
            builder.show()
        }
    }

    private fun startDownload(bounds: org.maplibre.android.geometry.LatLngBounds) {
        val mc = mapController ?: return
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.menu_offline)
            .setMessage(getString(R.string.offline_progress, 0))
            .setCancelable(true)
            .show()
        mc.downloadArea(
            bounds, "area-${System.currentTimeMillis()}",
            onProgress = { p -> progress.setMessage(getString(R.string.offline_progress, p)) },
            onDone = {
                progress.dismiss()
                Toast.makeText(this, R.string.offline_done, Toast.LENGTH_LONG).show()
            },
            onFailed = { msg ->
                progress.dismiss()
                Toast.makeText(this, getString(R.string.offline_failed, msg), Toast.LENGTH_LONG).show()
            },
        )
    }

    // --- Permissions / service ---

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionsAndStart(startService: Boolean = true) {
        val needed = mutableListOf<String>()
        if (!hasLocationPermission()) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (startService && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) {
            startAfterPermission = startService
            requestPermissions.launch(needed.toTypedArray())
            return
        }
        startUiLocation()
        if (startService) startDriving()
    }

    private fun startDriving() {
        RadarService.start(this)
        if (mode == ViewMode.FREE) setMode(followMode)
        askBatteryOptimizationOnce()
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryOptimizationOnce() {
        if (Build.VERSION.SDK_INT < 23 || prefs.batteryAsked) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.batteryAsked = true
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_title)
            .setMessage(R.string.battery_msg)
            .setPositiveButton(R.string.allow) { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    // --- Imports ---

    private fun importDb(uri: Uri) {
        lifecycleScope.launch {
            CameraRepository.import(this@MainActivity, uri)
                .onSuccess { Toast.makeText(this@MainActivity, getString(R.string.db_loaded, it.db.version, it.db.size), Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this@MainActivity, getString(R.string.db_import_failed, it.message), Toast.LENGTH_LONG).show() }
        }
    }

    // --- Menu ---

    private var testAnnouncer: Announcer? = null

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main, popup.menu)
        popup.menu.findItem(R.id.action_offline)?.isVisible = mapController != null
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_info -> showInfoTab(true)
                R.id.action_import_db -> pickDb.launch(arrayOf("*/*"))
                R.id.action_offline -> showOfflineDialog()
                R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.action_test_alert -> {
                    val a = testAnnouncer ?: Announcer(applicationContext, prefs).also { testAnnouncer = it }
                    a.test()
                }
                R.id.action_log -> {
                    val lines = DriveState.logLines()
                    AlertDialog.Builder(this).setTitle(R.string.menu_log)
                        .setMessage(if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n"))
                        .setPositiveButton(android.R.string.ok, null).show()
                }
                R.id.action_db_info -> {
                    val msg = when (val s = repoState) {
                        is CameraRepository.State.Ready -> getString(R.string.db_loaded, s.db.version, s.db.size)
                        is CameraRepository.State.Error -> getString(R.string.db_error, s.message)
                        else -> getString(R.string.db_missing)
                    }
                    AlertDialog.Builder(this).setTitle(R.string.menu_db_info).setMessage(msg).setPositiveButton(android.R.string.ok, null).show()
                }
            }
            true
        }
        popup.show()
    }

    // --- Lifecycle ---

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        startUiLocation()
        startSensors()
        startFrameLoop()
    }

    override fun onPause() {
        // UI GPS sleeps in background; RadarService keeps alerting.
        stopUiLocation()
        sensors.unregisterListener(sensorListener)
        stopFrameLoop()
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        testAnnouncer?.shutdown()
        mapView?.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val GPS_COURSE_MIN_MPS = 1.5
        private const val FRAME_MS = 33L
        private const val MAX_PREDICT_S = 2.0
        private const val POSITION_EASE = 0.35
        private const val COMPASS_BELOW_KMH = 8
        private const val REFOLLOW_MS = 12_000L
        /** About 150 MB of vector tiles; bigger areas take too long on a phone. */
        private const val MAX_OFFLINE_TILES = 5000L
    }
}
