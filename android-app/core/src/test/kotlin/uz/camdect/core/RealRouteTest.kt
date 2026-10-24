package uz.camdect.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Replays a drive along the Kokand–General Uzoqov road through the real database. */
class RealRouteTest {
    private val route = listOf(
        40.6290 to 70.8860, 40.6369 to 70.8782, 40.6503 to 70.8670, 40.7036 to 70.8363,
        40.7089 to 70.8384, 40.7189 to 70.8364, 40.7375 to 70.8290, 40.7455 to 70.8258,
        40.7539 to 70.8224, 40.7589 to 70.8205, 40.7700 to 70.8160,
    )

    @Test
    fun alertsBothDirections() {
        val path = System.getProperty("realDb")
        assumeTrue(path != null && File(path).exists())
        val db = NeolineDbParser.parse(File(path!!).readBytes())
        for ((name, pts) in listOf("north" to route, "south" to route.reversed())) {
            val engine = AlertEngine(CameraIndex(db))
            var events = 0
            for (k in 0 until pts.size - 1) {
                val (la1, lo1) = pts[k]; val (la2, lo2) = pts[k + 1]
                val course = Geo.bearingDeg(la1, lo1, la2, lo2)
                val steps = (Geo.distanceM(la1, lo1, la2, lo2) / 20).toInt().coerceAtLeast(1)
                for (s in 0 until steps) {
                    val t = s.toDouble() / steps
                    val r = engine.update(la1 + (la2 - la1) * t, lo1 + (lo2 - lo1) * t, 20.0, course)
                    for (e in r.events) {
                        events++
                        println("$name: cam ${e.target.camera} ${e.target.level} ${e.target.distanceM.toInt()}m limit ${e.target.speedLimitKmh} hd ${db.heading[e.target.camera]}")
                    }
                }
            }
            println("$name total events: $events")
        }
    }
}
