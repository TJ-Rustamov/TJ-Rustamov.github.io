package uz.camdect.data

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import uz.camdect.service.DriveState
import kotlin.math.floor

/**
 * Downloads the map for offline use without the user doing anything:
 *  - first fix: an overview of Uzbekistan (zoom 0-9) plus ~30 km around the user (zoom 10-14);
 *  - while moving: 0.1° cells (~10 km) around the current position, before you reach them.
 * Vector tiles stop at zoom 14; MapLibre draws closer zooms from them, so nothing above 14.
 * Finished areas are remembered and never downloaded twice. Must be used on the main thread.
 */
class MapPrefetcher private constructor(context: Context) {
    data class Progress(val active: Int, val percent: Int)

    private val app = context.applicationContext
    private val sp = PreferenceManager.getDefaultSharedPreferences(app)
    private val manager = OfflineManager.getInstance(app)
    private val density = app.resources.displayMetrics.density

    private val done = HashSet(sp.getStringSet(KEY_DONE, emptySet()) ?: emptySet())
    private val running = HashMap<String, Int>() // key -> percent
    private val lastTry = HashMap<String, Long>()
    private var resumed = false
    private var lastCell: String? = null

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    // No point downloading maps on phones that cannot draw them (no OpenGL ES 3).
    private val canShowMap = (app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
        .deviceConfigurationInfo.reqGlEsVersion >= 0x30000

    val enabled: Boolean get() = canShowMap && sp.getBoolean(KEY_ENABLED, true)

    init {
        resumeUnfinished()
    }

    /** Call on every GPS fix (UI or service). Cheap when nothing new is needed. */
    fun onLocation(lat: Double, lon: Double) {
        if (!enabled) return
        if (!resumed) return // wait until unfinished regions from last time are picked up
        request(KEY_COUNTRY, UZBEKISTAN, 0.0, 9.0)
        request(KEY_HOME, LatLngBounds.from(lat + HOME_DLAT, lon + HOME_DLON, lat - HOME_DLAT, lon - HOME_DLON), 10.0, 14.0)
        if (!done.contains(KEY_HOME)) return // home area first, it is what the user sees now
        val ci = floor(lat / CELL).toInt()
        val cj = floor(lon / CELL).toInt()
        val cell = "$ci:$cj"
        if (cell == lastCell) return
        lastCell = cell
        for (di in -1..1) for (dj in -1..1) {
            val i = ci + di
            val j = cj + dj
            request("c:$i:$j", LatLngBounds.from((i + 1) * CELL, (j + 1) * CELL, i * CELL, j * CELL), 11.0, 14.0)
        }
    }

    /** Forget everything (after the user deleted the downloaded maps). */
    fun forgetAll() {
        done.clear()
        running.clear()
        lastCell = null
        save()
        publish()
    }

    private fun request(key: String, bounds: LatLngBounds, minZoom: Double, maxZoom: Double) {
        if (key in done || key in running || running.size >= MAX_PARALLEL) return
        val now = System.currentTimeMillis()
        if (now - (lastTry[key] ?: 0L) < RETRY_MS) return
        lastTry[key] = now
        running[key] = 0
        publish()
        val def = OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, minZoom, maxZoom, density)
        manager.createOfflineRegion(def, key.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = start(key, offlineRegion)
            override fun onError(error: String) = fail(key, error)
        })
    }

    private fun start(key: String, region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    running.remove(key)
                    if (done.add(key)) {
                        save()
                        DriveState.log("Offline map saved: $key (${status.completedResourceSize / (1024 * 1024)} MB)")
                    }
                } else {
                    val req = maxOf(1L, status.requiredResourceCount)
                    running[key] = (status.completedResourceCount * 100 / req).toInt().coerceIn(0, 99)
                }
                publish()
            }

            // Network errors are retried by MapLibre itself while the region stays active.
            override fun onError(error: OfflineRegionError) {
                if (error.reason != OfflineRegionError.REASON_CONNECTION) DriveState.log("Offline map $key: ${error.message}")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) = fail(key, "tile limit $limit")
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun fail(key: String, error: String) {
        running.remove(key)
        DriveState.log("Offline map $key failed: $error")
        publish()
    }

    /** Regions created by an earlier run that never finished (app closed): continue them. */
    private fun resumeUnfinished() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                for (r in offlineRegions ?: emptyArray()) {
                    val key = String(r.metadata)
                    if (key in done || key in running) continue
                    if (!key.startsWith("c:") && key != KEY_HOME && key != KEY_COUNTRY) continue
                    running[key] = 0
                    start(key, r)
                }
                resumed = true
                publish()
            }

            override fun onError(error: String) { resumed = true }
        })
    }

    private fun save() = sp.edit().putStringSet(KEY_DONE, HashSet(done)).apply()

    private fun publish() {
        _progress.value = if (running.isEmpty()) null else Progress(running.size, running.values.minOrNull() ?: 0)
    }

    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val KEY_ENABLED = "auto_map"
        private const val KEY_DONE = "map_done"
        private const val KEY_COUNTRY = "country"
        private const val KEY_HOME = "home"
        private val UZBEKISTAN = LatLngBounds.from(45.6, 73.2, 37.1, 55.9)
        private const val HOME_DLAT = 0.3  // ~33 km north/south
        private const val HOME_DLON = 0.4  // ~33 km east/west at Uzbek latitudes
        private const val CELL = 0.1       // ~11 x 8 km
        private const val MAX_PARALLEL = 3
        private const val RETRY_MS = 2 * 60 * 1000L

        @Volatile private var instance: MapPrefetcher? = null

        fun get(context: Context): MapPrefetcher =
            instance ?: synchronized(this) { instance ?: MapPrefetcher(context).also { instance = it } }
    }
}
