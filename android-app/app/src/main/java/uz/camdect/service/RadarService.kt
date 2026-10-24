package uz.camdect.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.camdect.R
import uz.camdect.core.AlertEngine
import uz.camdect.core.CameraTypes
import uz.camdect.data.CameraRepository
import uz.camdect.data.Prefs
import uz.camdect.ui.MainActivity

/**
 * Foreground service: keeps GPS running with the screen off / app in background
 * and drives the [AlertEngine]. Works without internet or Google Play services.
 */
class RadarService : Service(), LocationListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var announcer: Announcer
    private var engine: AlertEngine? = null
    private var engineDb: Any? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifText: String? = null
    private var lastOverspeedWarn = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        announcer = Announcer(this, prefs)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(getString(R.string.notif_running)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (!startLocation()) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        DriveState.mutable.value = DriveState.Snapshot(running = true)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocation(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(this)
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        return true
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamDect:drive").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    override fun onLocationChanged(location: Location) {
        val ready = CameraRepository.ready
        val speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val course = if (location.hasBearing()) location.bearing.toDouble() else Double.NaN

        var target: AlertEngine.Target? = null
        if (ready != null) {
            if (engine == null || engineDb !== ready.db) {
                engine = AlertEngine(ready.index, prefs.alertConfig())
                engineDb = ready.db
            }
            val result = engine!!.update(location.latitude, location.longitude, speedMps, course)
            target = result.target
            for (e in result.events) announcer.announce(e)
            val now = System.currentTimeMillis()
            if (target != null && target.overspeed && result.events.isEmpty() && now - lastOverspeedWarn > OVERSPEED_REPEAT_MS) {
                lastOverspeedWarn = now
                announcer.overspeed()
            }
            updateNotification(target, ready)
        }
        DriveState.mutable.value = DriveState.Snapshot(
            running = true, hasFix = true,
            lat = location.latitude, lon = location.longitude,
            speedKmh = (speedMps * 3.6).toInt(), courseDeg = course, target = target,
        )
    }

    private fun updateNotification(target: AlertEngine.Target?, ready: CameraRepository.State.Ready) {
        val text = if (target == null) getString(R.string.notif_running)
        else getString(R.string.camera_ahead, CameraTypes.label(ready.db, target.camera), target.distanceM.toInt() / 10 * 10)
        if (text == lastNotifText) return
        lastNotifText = text
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, alert = target != null))
    }

    private fun buildNotification(text: String, alert: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlags(),
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RadarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlags(),
        )
        return NotificationCompat.Builder(this, if (alert) CH_ALERT else CH_DRIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(true) // sound comes from Announcer
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    private fun pendingFlags() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_DRIVE, getString(R.string.notif_channel_drive), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, getString(R.string.notif_channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
        )
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        engine?.config = prefs.alertConfig()
    }

    override fun onDestroy() {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        announcer.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        DriveState.mutable.value = DriveState.Snapshot(running = false)
        super.onDestroy()
    }

    // Required on API < 29 where these are abstract.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    companion object {
        private const val NOTIF_ID = 1
        private const val CH_DRIVE = "drive"
        private const val CH_ALERT = "alert"
        private const val ACTION_STOP = "uz.camdect.STOP"
        private const val OVERSPEED_REPEAT_MS = 8_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RadarService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RadarService::class.java))
        }
    }
}
