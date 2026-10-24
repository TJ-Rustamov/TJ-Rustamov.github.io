package uz.camdect.ui

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.provider.Settings
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import uz.camdect.R
import uz.camdect.data.CameraRepository
import uz.camdect.data.Prefs
import uz.camdect.data.TypeLabels
import uz.camdect.service.Announcer
import uz.camdect.service.DriveState
import uz.camdect.service.RadarService
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class MainActivity : AppCompatActivity(), LocationListener {

    /** HEADING_UP: map turns with the car, arrow fixed low on screen (racing-game style). */
    private enum class ViewMode { HEADING_UP, NORTH_UP, FREE }

    private lateinit var prefs: Prefs
    private lateinit var map: MapView
    private lateinit var speedView: TextView
    private lateinit var statusView: TextView
    private lateinit var subStatusView: TextView
    private lateinit var limitBadge: TextView
    private lateinit var hintView: TextView
    private lateinit var modeLabel: TextView
    private lateinit var compass: ImageButton
    private lateinit var driveButton: Button
    private lateinit var cameraOverlay: CameraOverlay
    private lateinit var positionOverlay: PositionOverlay

    private var repoState: CameraRepository.State = CameraRepository.State.Loading
    private var startAfterPermission = false

    private var mode = ViewMode.HEADING_UP
    private var followMode = ViewMode.HEADING_UP

    // Latest fix from our own GPS listener (UI only; alerts come from RadarService).
    private var fix: Location? = null
    private var course = Double.NaN
    private var speedKmh = 0
    private var animator: ValueAnimator? = null
    private var shownLat = Double.NaN
    private var shownLon = Double.NaN
    private var shownCourse = Double.NaN

    private var touchX = 0f
    private var touchY = 0f
    private var lastTouchAt = 0L

    // Phone compass: gives the arrow a direction when GPS course is unknown (stopped / slow).
    private lateinit var sensors: SensorManager
    private var compassDeg = Double.NaN
    private var declination = Float.NaN
    private var lastCompassDraw = 0L
    private val rotation = FloatArray(9)
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) = onSensor(e)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

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

    private val pickMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importMap(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        map = findViewById(R.id.map)
        applyInsets()

        followMode = if (prefs.sp.getString("follow_mode", "heading") == "north") ViewMode.NORTH_UP else ViewMode.HEADING_UP
        mode = followMode
        setupMap()

        statusView.setOnClickListener { if (repoState is CameraRepository.State.Missing) pickDb.launch(arrayOf("*/*")) }
        driveButton.setOnClickListener {
            if (DriveState.flow.value.running) RadarService.stop(this) else ensurePermissionsAndStart()
        }
        findViewById<Button>(R.id.btn_center).setOnClickListener { setMode(followMode) }
        compass.setOnClickListener { onCompassTap() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu(it) }
        updateModeUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { CameraRepository.state.collect { onRepoState(it) } }
                launch { DriveState.flow.collect { onDriveState(it) } }
            }
        }

        if (!hasLocationPermission()) ensurePermissionsAndStart(startService = false)
    }

    // --- Layout / insets ---

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        // Same look on every Android version: map under transparent system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = 0x66000000
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun applyInsets() {
        val top = findViewById<View>(R.id.top_area)
        val bottom = findViewById<View>(R.id.bottom_area)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            (top.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = bars.top; leftMargin = bars.left; rightMargin = bars.right
            }
            (bottom.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = bars.bottom; leftMargin = bars.left; rightMargin = bars.right
            }
            top.requestLayout()
            bottom.requestLayout()
            insets
        }
    }

    // --- Map ---

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.isTilesScaledToDpi = true
        map.minZoomLevel = 4.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(13.0)
        map.controller.setCenter(GeoPoint(41.3111, 69.2797)) // Tashkent until we get a fix

        val density = resources.displayMetrics.density
        map.overlays.add(RotationGestureOverlay(map).apply { isEnabled = true })
        cameraOverlay = CameraOverlay(density) { i -> showCamera(i) }
        cameraOverlay.typeNames = TypeLabels.all(this)
        map.overlays.add(cameraOverlay)
        positionOverlay = PositionOverlay(density)
        map.overlays.add(positionOverlay)

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?) = false.also { onMapMoved() }
            override fun onZoom(event: ZoomEvent?) = false.also { onMapMoved() }
        })
        // Any drag / pinch / rotate by the user leaves follow mode (like Google Maps).
        map.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchX = ev.x; touchY = ev.y; lastTouchAt = SystemClock.elapsedRealtime() }
                MotionEvent.ACTION_POINTER_DOWN -> if (mode != ViewMode.FREE) setMode(ViewMode.FREE)
                MotionEvent.ACTION_MOVE -> if (mode != ViewMode.FREE &&
                    hypot((ev.x - touchX).toDouble(), (ev.y - touchY).toDouble()) > touchSlop
                ) setMode(ViewMode.FREE)
            }
            if (ev.actionMasked != MotionEvent.ACTION_DOWN) lastTouchAt = SystemClock.elapsedRealtime()
            false
        }
    }

    private fun onMapMoved() {
        compass.rotation = map.mapOrientation
        hintView.visibility =
            if (repoState is CameraRepository.State.Ready && map.zoomLevelDouble < CameraOverlay.MIN_ZOOM) View.VISIBLE else View.GONE
    }

    private fun setMode(newMode: ViewMode) {
        mode = newMode
        if (newMode != ViewMode.FREE && newMode != followMode) {
            followMode = newMode
            prefs.sp.edit().putString("follow_mode", if (newMode == ViewMode.NORTH_UP) "north" else "heading").apply()
        }
        if (newMode != ViewMode.FREE && !shownLat.isNaN()) {
            if (newMode == ViewMode.NORTH_UP) map.mapOrientation = 0f
            moveCamera(shownLat, shownLon, shownCourse, snap = true)
        }
        updateModeUi()
    }

    private fun onCompassTap() {
        when (mode) {
            ViewMode.HEADING_UP -> setMode(ViewMode.NORTH_UP)
            ViewMode.NORTH_UP -> setMode(ViewMode.HEADING_UP)
            ViewMode.FREE -> {
                map.mapOrientation = 0f
                onMapMoved()
            }
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
        compass.rotation = map.mapOrientation
    }

    /** Positions the map for the follow modes. In heading-up the car sits in the lower third. */
    private fun moveCamera(lat: Double, lon: Double, courseDeg: Double, snap: Boolean = false) {
        if (mode == ViewMode.FREE) return
        val targetZoom = autoZoom()
        val z = map.zoomLevelDouble
        val newZoom = if (snap) targetZoom else z + (targetZoom - z) * 0.08
        if (abs(newZoom - z) > 0.005) map.controller.setZoom(newZoom)

        val me = GeoPoint(lat, lon)
        if (mode == ViewMode.HEADING_UP && !courseDeg.isNaN()) {
            map.mapOrientation = (-courseDeg).toFloat()
            val pxPerMetre = map.projection.metersToPixels(1f).toDouble().coerceAtLeast(1e-6)
            val aheadM = map.height * 0.25 / pxPerMetre
            map.controller.setCenter(me.destinationPoint(aheadM, courseDeg))
        } else {
            map.controller.setCenter(me)
        }
        compass.rotation = map.mapOrientation
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
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        }
        if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this, Looper.getMainLooper()) }
        }
    }

    private fun stopUiLocation() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        animator?.cancel()
    }

    override fun onLocationChanged(location: Location) {
        val last = fix
        // Prefer GPS; ignore a coarse network fix once we have a recent good GPS one.
        if (location.provider != LocationManager.GPS_PROVIDER && last != null &&
            last.provider == LocationManager.GPS_PROVIDER && location.time - last.time < 10_000
        ) return
        fix = location
        speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
        if (location.hasBearing() && location.hasSpeed() && location.speed >= GPS_COURSE_MIN_MPS) course = location.bearing.toDouble()
        if (declination.isNaN()) declination = GeomagneticField(
            location.latitude.toFloat(), location.longitude.toFloat(), location.altitude.toFloat(), System.currentTimeMillis()
        ).declination
        // Back to following a while after the user stopped panning, like car navigation apps.
        if (mode == ViewMode.FREE && DriveState.flow.value.running &&
            SystemClock.elapsedRealtime() - lastTouchAt > REFOLLOW_MS
        ) setMode(followMode)
        positionOverlay.accuracyM = location.accuracy
        speedView.text = speedKmh.toString()
        updateSubStatus()

        if (shownLat.isNaN()) {
            // First fix: jump there.
            shownLat = location.latitude; shownLon = location.longitude; shownCourse = arrowCourse()
            positionOverlay.position = GeoPoint(shownLat, shownLon)
            positionOverlay.courseDeg = shownCourse
            if (mode == ViewMode.FREE) setMode(followMode) else moveCamera(shownLat, shownLon, shownCourse, snap = true)
            map.invalidate()
            return
        }
        animateTo(location.latitude, location.longitude, arrowCourse())
    }

    private fun usingCompass() = speedKmh < COMPASS_BELOW_KMH || course.isNaN()

    /** GPS course while driving; phone compass when slow or stopped. */
    private fun arrowCourse(): Double = if (usingCompass() && !compassDeg.isNaN()) compassDeg else course

    private fun onSensor(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(rotation, e.values)
            Sensor.TYPE_ACCELEROMETER -> { gravity = e.values.clone(); if (!fusedFromAccelMag()) return }
            Sensor.TYPE_MAGNETIC_FIELD -> { geomagnetic = e.values.clone(); if (!fusedFromAccelMag()) return }
            else -> return
        }
        val heading = headingFromRotation()
        compassDeg = if (compassDeg.isNaN()) heading else {
            val diff = ((heading - compassDeg + 540) % 360) - 180
            (compassDeg + diff * 0.2 + 360) % 360
        }
        if (!usingCompass() || shownLat.isNaN()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCompassDraw < 66) return // ~15 fps is plenty
        lastCompassDraw = now
        if (animator?.isRunning == true) return
        shownCourse = compassDeg
        positionOverlay.courseDeg = compassDeg
        moveCamera(shownLat, shownLon, compassDeg)
        map.invalidate()
    }

    private fun fusedFromAccelMag(): Boolean {
        val g = gravity ?: return false
        val m = geomagnetic ?: return false
        return SensorManager.getRotationMatrix(rotation, null, g, m)
    }

    /**
     * Direction the phone faces, degrees from true north. Flat phone: the top edge of the screen.
     * Upright (car mount): the back of the phone, i.e. the windscreen direction.
     */
    @Suppress("DEPRECATION")
    private fun headingFromRotation(): Double {
        val r = rotation // device axes in world (east, north, up) coordinates: columns X, Y, Z
        val flat = abs(r[8]) > 0.75f
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
        val magnetic = Math.toDegrees(atan2(east.toDouble(), north.toDouble()))
        val decl = if (declination.isNaN()) 0f else declination
        return (magnetic + decl + 720) % 360
    }

    private fun startCompass() {
        val rv = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv != null) {
            sensors.registerListener(sensorListener, rv, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    /** Glides the arrow (and map, when following) to the new fix over ~1 s, like a game camera. */
    private fun animateTo(lat: Double, lon: Double, newCourse: Double) {
        animator?.cancel()
        val fromLat = shownLat; val fromLon = shownLon
        val fromCourse = if (shownCourse.isNaN()) newCourse else shownCourse
        val turn = if (newCourse.isNaN() || fromCourse.isNaN()) 0.0 else ((newCourse - fromCourse + 540) % 360) - 180
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                shownLat = fromLat + (lat - fromLat) * f
                shownLon = fromLon + (lon - fromLon) * f
                shownCourse = if (newCourse.isNaN()) fromCourse else (fromCourse + turn * f + 360) % 360
                positionOverlay.position = GeoPoint(shownLat, shownLon)
                positionOverlay.courseDeg = shownCourse
                moveCamera(shownLat, shownLon, shownCourse)
                map.invalidate()
            }
            start()
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
                cameraOverlay.index = state.index
                onDriveState(DriveState.flow.value)
                map.invalidate()
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
        if (ready != null) {
            statusView.text = when {
                !s.running -> getString(R.string.db_loaded, ready.db.version, ready.db.size)
                !s.hasFix -> getString(R.string.no_gps)
                target == null -> getString(R.string.no_camera)
                else -> getString(R.string.camera_ahead, TypeLabels.label(this, ready.db, target.camera), target.distanceM.toInt() / 10 * 10)
            }
        }
        showLimit(target?.speedLimitKmh ?: 0, target?.overspeed == true)
        val newHighlight = target?.camera ?: -1
        if (newHighlight != cameraOverlay.highlighted) {
            cameraOverlay.highlighted = newHighlight
            map.invalidate()
        }
        updateSubStatus()
    }

    private fun updateSubStatus() {
        val running = DriveState.flow.value.running
        val alerts = getString(if (running) R.string.service_on else R.string.service_off)
        val f = fix
        subStatusView.text = if (f == null) alerts else getString(R.string.gps_accuracy, f.accuracy.toInt(), alerts)
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

    /** Camera details + naming its type code (e.g. "Lochin"). */
    private fun showCamera(i: Int) {
        val db = CameraRepository.ready?.db ?: return
        val code = db.typeCode(i)
        val limit = db.speedLimit[i].toInt()
        val input = EditText(this).apply {
            setSingleLine()
            setText(TypeLabels.custom(this@MainActivity, code) ?: "")
            hint = getString(R.string.type_camera, code)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle(TypeLabels.label(this, db, i))
            .setMessage(
                getString(
                    R.string.type_dialog_msg, code, TypeLabels.count(db, code),
                    if (limit > 0) "$limit ${getString(R.string.kmh)}" else getString(R.string.no_limit),
                    db.heading[i].toInt(),
                )
            )
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ -> renameType(code, input.text.toString()) }
            .setNeutralButton(R.string.reset) { _, _ -> renameType(code, null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renameType(code: Int, name: String?) {
        TypeLabels.set(this, code, name)
        cameraOverlay.typeNames = cameraOverlay.typeNames.toMutableMap().apply {
            if (name.isNullOrBlank()) remove(code) else put(code, name.trim())
        }
        map.invalidate()
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

    /** Copies an osmdroid-readable archive into the osmdroid folder, then reloads the map. */
    private fun importMap(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(uri)
                    require(name.endsWith(".mbtiles") || name.endsWith(".sqlite") || name.endsWith(".zip") || name.endsWith(".gemf")) {
                        "Unsupported file: $name"
                    }
                    val dest = File(Configuration.getInstance().osmdroidBasePath, name)
                    contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
                }
            }
            ok.onSuccess {
                Toast.makeText(this@MainActivity, R.string.map_imported, Toast.LENGTH_LONG).show()
                recreate() // tile provider scans archives on creation
            }.onFailure {
                Toast.makeText(this@MainActivity, getString(R.string.db_import_failed, it.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).lowercase()
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.lowercase() ?: "map.mbtiles"
    }

    // --- Menu ---

    private var testAnnouncer: Announcer? = null

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_import_db -> pickDb.launch(arrayOf("*/*"))
                R.id.action_import_map -> pickMap.launch(arrayOf("*/*"))
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

    override fun onResume() {
        super.onResume()
        map.onResume()
        startUiLocation()
        startCompass()
    }

    override fun onPause() {
        // UI GPS sleeps in background; RadarService keeps alerting.
        stopUiLocation()
        sensors.unregisterListener(sensorListener)
        map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        testAnnouncer?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val GPS_COURSE_MIN_MPS = 1.5f
        private const val COMPASS_BELOW_KMH = 8
        private const val REFOLLOW_MS = 12_000L
    }
}
