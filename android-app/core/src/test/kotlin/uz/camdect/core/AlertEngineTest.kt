package uz.camdect.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    // Camera on a road going due north, watching northbound traffic.
    private val camLat = 41.3000
    private val camLon = 69.2800
    private val db = NeolineDbParser.parse(TestDb.build(listOf(TestDb.Point(camLat, camLon, 0, 60))))
    private val engine = AlertEngine(CameraIndex(db))

    private fun latSouthOfCam(metres: Double) = camLat - metres / 111_320.0

    @Test
    fun alertsWhenApproachingInCameraDirection() {
        val r = engine.update(latSouthOfCam(350.0), camLon, 20.0, 0.0)
        assertNotNull(r.target)
        assertEquals(AlertEngine.Level.NEAR, r.target!!.level)
        assertEquals(1, r.events.size)
        assertTrue(r.events[0].firstForCamera)
    }

    @Test
    fun escalatesOncePerLevelThenForgetsAfterPassing() {
        assertEquals(1, engine.update(latSouthOfCam(350.0), camLon, 20.0, 0.0).events.size)
        assertEquals(0, engine.update(latSouthOfCam(300.0), camLon, 20.0, 0.0).events.size)
        val close = engine.update(latSouthOfCam(100.0), camLon, 20.0, 0.0)
        assertEquals(AlertEngine.Level.CLOSE, close.events.single().target.level)
        // Passed the camera: now behind us.
        assertNull(engine.update(camLat + 200 / 111_320.0, camLon, 20.0, 0.0).target)
        // Coming around again alerts again.
        assertEquals(1, engine.update(latSouthOfCam(350.0), camLon, 20.0, 0.0).events.size)
    }

    @Test
    fun ignoresOppositeDirection() {
        // Driving south, camera north of us is behind; camera south of us faces the other way.
        assertNull(engine.update(camLat + 300 / 111_320.0, camLon, 20.0, 180.0).target)
        assertNull(engine.update(latSouthOfCam(300.0), camLon, 20.0, 180.0).target)
    }

    @Test
    fun ignoresCameraTooFarAhead() {
        assertNull(engine.update(latSouthOfCam(1400.0), camLon, 10.0, 0.0).target)
        // At highway speed the look-ahead grows: 30 m/s * 15 s = 450 m minimum, capped 1500.
        assertNotNull(AlertEngine(CameraIndex(db)).update(latSouthOfCam(440.0), camLon, 30.0, 0.0).target)
    }

    @Test
    fun flagsOverspeed() {
        val r = engine.update(latSouthOfCam(300.0), camLon, 80 / 3.6, 0.0)
        assertTrue(r.target!!.overspeed)
    }

    @Test
    fun keepsLastCourseWhenStopped() {
        engine.update(latSouthOfCam(500.0), camLon, 15.0, 0.0)
        // Stopped at a light: GPS course is garbage but we still know where we were heading.
        assertNotNull(engine.update(latSouthOfCam(300.0), camLon, 0.5, 250.0).target)
    }
}
