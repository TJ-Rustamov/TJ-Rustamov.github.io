package uz.camdect.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun destinationRoundTrips() {
        val p = Geo.destination(41.3, 69.28, 500.0, 47.0)
        assertEquals(500.0, Geo.distanceM(41.3, 69.28, p[0], p[1]), 1.0)
        assertEquals(47.0, Geo.bearingDeg(41.3, 69.28, p[0], p[1]), 0.1)
    }
}
