package uz.camdect.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingFilterTest {
    @Test
    fun gyroTurnShowsImmediately() {
        val f = HeadingFilter()
        f.onCompass(10.0, moving = false)
        f.onRelative(100.0, 0)
        f.onRelative(190.0, 1000) // car turned 90° right within a second
        assertEquals(100.0, f.heading, 1e-9)
    }

    @Test
    fun gpsCorrectsDriftOnStraights() {
        val f = HeadingFilter()
        f.onGps(0.0, 15.0)
        f.onRelative(0.0, 0)
        var t = 0L
        // Gyro drifted 20°, car is actually going straight north.
        f.onRelative(20.0, 100.also { t = it.toLong() })
        repeat(20) { t += 100; f.onRelative(20.0, t); f.onGps(0.0, 15.0) }
        assertEquals(0.0, HeadingFilter.wrap(f.heading), 0.5)
    }

    @Test
    fun laggingGpsDoesNotUndoTurn() {
        val f = HeadingFilter()
        f.onGps(0.0, 10.0)
        f.onRelative(0.0, 0)
        var t = 0L
        var rel = 0.0
        // Turning right at 30°/s for 3 s while GPS still reports the old course.
        repeat(30) { i ->
            t += 100; rel += 3.0; f.onRelative(rel, t)
            if (i % 10 == 9) f.onGps(0.0, 10.0) // GPS at 1 Hz, still reporting the old course
        }
        assertEquals(90.0, f.heading, 15.0)
    }

    @Test
    fun ignoresCompassWhileDriving() {
        val f = HeadingFilter()
        f.onGps(90.0, 20.0)
        f.onRelative(0.0, 0)
        f.onCompass(180.0, moving = true)
        assertEquals(90.0, f.heading, 1e-9)
    }

    @Test
    fun wrapsAroundNorth() {
        val f = HeadingFilter()
        f.onCompass(350.0, moving = false)
        f.onRelative(0.0, 0)
        f.onRelative(20.0, 500)
        assertEquals(10.0, f.heading, 1e-9)
    }
}
