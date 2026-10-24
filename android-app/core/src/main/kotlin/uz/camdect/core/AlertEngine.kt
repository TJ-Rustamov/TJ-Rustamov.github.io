package uz.camdect.core

import kotlin.math.max
import kotlin.math.min

/**
 * Decides which camera (if any) the driver is approaching, from a stream of GPS fixes.
 * Pure logic: no Android dependencies, so it is unit tested on the JVM.
 */
class AlertEngine(private val index: CameraIndex, var config: Config = Config()) {

    data class Config(
        /** Alert distance never goes below this, metres. */
        val minAlertDistanceM: Double = 400.0,
        /** Look-ahead time: alert distance = speed * this, seconds. */
        val lookAheadSec: Double = 15.0,
        val maxAlertDistanceM: Double = 1500.0,
        /** Camera must be within this angle of our course to count as "ahead". */
        val aheadToleranceDeg: Double = 35.0,
        /** Camera heading must match our course within this angle. */
        val headingToleranceDeg: Double = 50.0,
        /** Warn about overspeed when speed > limit + this, km/h. */
        val overspeedMarginKmh: Int = 5,
        /** Skip points without a speed limit (police posts etc.). */
        val ignoreNoLimit: Boolean = false,
        /** Below this speed the GPS course is unreliable; keep the last good course. */
        val minCourseSpeedMps: Double = 2.0,
    )

    enum class Level { FAR, NEAR, CLOSE }

    /** The camera currently ahead of us. */
    data class Target(
        val camera: Int,
        val distanceM: Double,
        val level: Level,
        val speedLimitKmh: Int,
        val overspeed: Boolean,
    )

    /** One-shot event the UI/service should announce. */
    data class Event(val target: Target, val firstForCamera: Boolean)

    data class Result(val target: Target?, val events: List<Event>, val speedKmh: Int)

    private val db = index.db
    private val candidates = IntList(64)
    private val announced = HashMap<Int, Level>()
    private var lastCourse = Double.NaN

    fun reset() {
        announced.clear()
        lastCourse = Double.NaN
    }

    /**
     * @param courseDeg GPS bearing, or NaN if unavailable.
     */
    fun update(lat: Double, lon: Double, speedMps: Double, courseDeg: Double): Result {
        val speedKmh = (speedMps * 3.6).toInt()
        if (!courseDeg.isNaN() && speedMps >= config.minCourseSpeedMps) lastCourse = courseDeg
        val course = lastCourse

        val alertDist = min(config.maxAlertDistanceM, max(config.minAlertDistanceM, speedMps * config.lookAheadSec))
        index.nearby(lat, lon, max(alertDist, FORGET_DISTANCE_M), candidates)

        var best: Target? = null
        val stillRelevant = HashSet<Int>()
        for (k in 0 until candidates.size) {
            val i = candidates[k]
            val limit = db.speedLimit[i].toInt()
            if (config.ignoreNoLimit && limit == 0) continue
            val cLat = db.latDeg(i)
            val cLon = db.lonDeg(i)
            val d = Geo.distanceM(lat, lon, cLat, cLon)
            if (course.isNaN()) continue
            val bearingTo = Geo.bearingDeg(lat, lon, cLat, cLon)
            // Very close points: bearing is noisy, accept a wider cone.
            val aheadTol = if (d < 60) 75.0 else config.aheadToleranceDeg
            val ahead = Geo.angleDiff(bearingTo, course) <= aheadTol
            val sameDirection = Geo.angleDiff(db.heading[i].toDouble(), course) <= config.headingToleranceDeg
            if (!ahead || !sameDirection) continue
            stillRelevant.add(i)
            if (d > alertDist) continue
            if (best == null || d < best.distanceM) {
                val level = when {
                    d <= CLOSE_M -> Level.CLOSE
                    d <= NEAR_M -> Level.NEAR
                    else -> Level.FAR
                }
                best = Target(i, d, level, limit, limit > 0 && speedKmh > limit + config.overspeedMarginKmh)
            }
        }

        // Forget cameras we have passed or turned away from, so they can alert again next time.
        announced.keys.retainAll(stillRelevant)

        val events = ArrayList<Event>(1)
        if (best != null) {
            val prev = announced[best.camera]
            if (prev == null || best.level > prev) {
                announced[best.camera] = best.level
                events.add(Event(best, prev == null))
            }
        }
        return Result(best, events, speedKmh)
    }

    companion object {
        const val NEAR_M = 400.0
        const val CLOSE_M = 150.0
        private const val FORGET_DISTANCE_M = 600.0
    }
}
