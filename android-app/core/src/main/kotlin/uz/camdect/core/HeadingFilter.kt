package uz.camdect.core

import kotlin.math.abs

/**
 * Fuses three heading sources into one low-latency value (degrees, 0..360):
 *  - gyroscope (relative, instant, drifts)  → applied directly, so turns show immediately
 *  - GPS course (absolute, 1-3 s late)      → slowly pulls the gyro back, mostly on straights
 *  - compass (absolute, noisy in cars)      → only used when stopped / slow
 */
class HeadingFilter {
    var heading = Double.NaN
        private set

    private var lastRelative = Double.NaN
    private var lastRelativeMs = 0L
    /** Smoothed absolute turn rate from the gyro, deg/s. */
    var turnRate = 0.0
        private set

    val hasGyro: Boolean get() = !lastRelative.isNaN()

    /**
     * Heading from a gyro-based sensor with an arbitrary zero. Only its changes are used.
     * Pass [reset] when its frame jumped (e.g. phone switched between flat and upright).
     */
    fun onRelative(relativeDeg: Double, timeMs: Long, reset: Boolean = false) {
        if (!lastRelative.isNaN() && !reset && !heading.isNaN()) {
            val d = wrap(relativeDeg - lastRelative)
            heading = norm(heading + d)
            val dt = ((timeMs - lastRelativeMs) / 1000.0).coerceIn(0.005, 1.0)
            turnRate = turnRate * 0.6 + (abs(d) / dt) * 0.4
        }
        lastRelative = relativeDeg
        lastRelativeMs = timeMs
    }

    /** Absolute compass heading. Trusted only when not driving (car metal skews it). */
    fun onCompass(compassDeg: Double, moving: Boolean) {
        if (heading.isNaN()) { heading = norm(compassDeg); return }
        if (moving) return
        heading = norm(heading + wrap(compassDeg - heading) * if (hasGyro) 0.05 else 0.25)
    }

    /** GPS course; ignored below [minSpeedMps] where it is meaningless. */
    fun onGps(courseDeg: Double, speedMps: Double, minSpeedMps: Double = 2.5) {
        if (courseDeg.isNaN() || speedMps < minSpeedMps) return
        if (heading.isNaN() || !hasGyro) { heading = norm(courseDeg); return }
        // GPS lags in turns: correct drift on straights only.
        if (turnRate >= 5.0) return
        val k = 0.3
        heading = norm(heading + wrap(courseDeg - heading) * k)
    }

    companion object {
        fun wrap(d: Double): Double = ((d % 360.0) + 540.0) % 360.0 - 180.0
        fun norm(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
    }
}
