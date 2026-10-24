package uz.camdect.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import uz.camdect.R
import uz.camdect.core.CameraTypes
import uz.camdect.data.CameraRepository
import uz.camdect.data.Prefs
import uz.camdect.service.DriveState
import uz.camdect.service.RadarService
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var map: MapView
    private lateinit var speedView: TextView
    private lateinit var statusView: TextView
    private lateinit var limitBadge: TextView
    private lateinit var hintView: TextView
    private lateinit var driveButton: Button
    private lateinit var cameraOverlay: CameraOverlay
    private var myLocation: MyLocationNewOverlay? = null
    private var repoState: CameraRepository.State = CameraRepository.State.Loading
    private var startAfterPermission = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                enableMyLocation()
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
        setContentView(R.layout.activity_main)
        speedView = findViewById(R.id.speed)
        statusView = findViewById(R.id.status)
        limitBadge = findViewById(R.id.limit_badge)
        hintView = findViewById(R.id.hint)
        driveButton = findViewById(R.id.btn_drive)
        map = findViewById(R.id.map)

        setupMap()
        statusView.setOnClickListener { if (repoState is CameraRepository.State.Missing) pickDb.launch(arrayOf("*/*")) }
        driveButton.setOnClickListener {
            if (DriveState.flow.value.running) RadarService.stop(this) else ensurePermissionsAndStart()
        }
        findViewById<Button>(R.id.btn_center).setOnClickListener { centerOnMe() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { CameraRepository.state.collect { onRepoState(it) } }
                launch { DriveState.flow.collect { onDriveState(it) } }
            }
        }
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        map.isTilesScaledToDpi = true
        map.controller.setZoom(13.0)
        map.controller.setCenter(GeoPoint(41.3111, 69.2797)) // Tashkent until we get a fix

        cameraOverlay = CameraOverlay(resources.displayMetrics.density) { i -> showCamera(i) }
        map.overlays.add(cameraOverlay)
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?) = false.also { updateHint() }
            override fun onZoom(event: ZoomEvent?) = false.also { updateHint() }
        })
        if (hasLocationPermission()) enableMyLocation()
    }

    private fun enableMyLocation() {
        if (myLocation != null) return
        val overlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        overlay.enableMyLocation()
        overlay.enableFollowLocation()
        overlay.runOnFirstFix { runOnUiThread { map.controller.animateTo(overlay.myLocation); map.controller.setZoom(15.0) } }
        map.overlays.add(overlay)
        myLocation = overlay
    }

    private fun centerOnMe() {
        val overlay = myLocation
        if (overlay == null) {
            ensurePermissionsAndStart(startService = false)
            return
        }
        overlay.enableFollowLocation()
        overlay.myLocation?.let { map.controller.animateTo(it) }
    }

    private fun updateHint() {
        hintView.visibility =
            if (repoState is CameraRepository.State.Ready && map.zoomLevelDouble < CameraOverlay.MIN_ZOOM) View.VISIBLE else View.GONE
    }

    private fun onRepoState(state: CameraRepository.State) {
        repoState = state
        when (state) {
            is CameraRepository.State.Loading -> statusView.setText(R.string.db_loading)
            is CameraRepository.State.Missing -> statusView.setText(R.string.db_missing)
            is CameraRepository.State.Error -> statusView.text = getString(R.string.db_error, state.message)
            is CameraRepository.State.Ready -> {
                cameraOverlay.index = state.index
                if (!DriveState.flow.value.running) statusView.text = getString(R.string.db_loaded, state.db.version, state.db.size)
                map.invalidate()
            }
        }
        updateHint()
    }

    private fun onDriveState(s: DriveState.Snapshot) {
        driveButton.setText(if (s.running) R.string.stop else R.string.start)
        if (s.running && prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        speedView.text = if (s.hasFix) s.speedKmh.toString() else "--"
        val ready = repoState as? CameraRepository.State.Ready
        val target = s.target
        if (s.running && ready != null) {
            statusView.text = when {
                !s.hasFix -> getString(R.string.no_gps)
                target == null -> getString(R.string.no_camera)
                else -> getString(R.string.camera_ahead, CameraTypes.label(ready.db, target.camera), target.distanceM.toInt() / 10 * 10)
            }
        } else if (ready != null) {
            statusView.text = getString(R.string.db_loaded, ready.db.version, ready.db.size)
        }
        showLimit(target?.speedLimitKmh ?: 0, target?.overspeed == true)
        val newHighlight = target?.camera ?: -1
        if (newHighlight != cameraOverlay.highlighted) {
            cameraOverlay.highlighted = newHighlight
            map.invalidate()
        }
        if (s.running && prefs.rotateMap && !s.courseDeg.isNaN() && s.speedKmh > 7) {
            map.mapOrientation = -s.courseDeg.toFloat()
        } else if (!prefs.rotateMap && map.mapOrientation != 0f) {
            map.mapOrientation = 0f
        }
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

    private fun showCamera(i: Int) {
        val db = CameraRepository.ready?.db ?: return
        Toast.makeText(
            this,
            getString(R.string.camera_info, CameraTypes.label(db, i), db.heading[i].toInt(), db.typeCode(i)),
            Toast.LENGTH_LONG,
        ).show()
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty() && (startService || !hasLocationPermission())) {
            startAfterPermission = startService
            requestPermissions.launch(needed.toTypedArray())
            return
        }
        enableMyLocation()
        if (startService) startDriving()
    }

    private fun startDriving() {
        RadarService.start(this)
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

    // --- Menu / lifecycle ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_db -> pickDb.launch(arrayOf("*/*"))
            R.id.action_import_map -> pickMap.launch(arrayOf("*/*"))
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.action_db_info -> {
                val msg = when (val s = repoState) {
                    is CameraRepository.State.Ready -> getString(R.string.db_loaded, s.db.version, s.db.size)
                    is CameraRepository.State.Error -> getString(R.string.db_error, s.message)
                    else -> getString(R.string.db_missing)
                }
                AlertDialog.Builder(this).setTitle(R.string.menu_db_info).setMessage(msg).setPositiveButton(android.R.string.ok, null).show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        myLocation?.enableMyLocation()
    }

    override fun onPause() {
        // The map's own GPS overlay sleeps in background; RadarService keeps alerting.
        myLocation?.disableMyLocation()
        map.onPause()
        super.onPause()
    }
}
