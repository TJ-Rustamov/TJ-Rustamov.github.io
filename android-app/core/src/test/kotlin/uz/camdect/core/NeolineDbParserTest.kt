package uz.camdect.core

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class NeolineDbParserTest {
    @Test
    fun roundTripsSyntheticFile() {
        val db = NeolineDbParser.parse(
            TestDb.build(
                listOf(
                    TestDb.Point(41.3111, 69.2797, 90, 60),
                    TestDb.Point(55.7539, 37.6208, 270, 0, type = 0xEF),
                )
            )
        )
        assertEquals("241026", db.version)
        assertEquals(2, db.size)
        // Sorted north first, like the real file.
        assertEquals(55.7539, db.latDeg(0), 1e-9)
        assertEquals(37.6208, db.lonDeg(0), 1e-9)
        assertEquals(270, db.heading[0].toInt())
        assertEquals(0, db.speedLimit[0].toInt())
        assertEquals(0xEF, db.typeCode(0))
        assertEquals(69.2797, db.lonDeg(1), 1e-9)
        assertEquals(60, db.speedLimit[1].toInt())
    }

    @Test(expected = IOException::class)
    fun rejectsOtherFiles() {
        NeolineDbParser.parse(ByteArray(4096))
    }

    /** Runs only with -PrealDb=/path/to/X-COP_7500s_Baza_GPS.db */
    @Test
    fun parsesRealDatabase() {
        val path = System.getProperty("realDb")
        assumeTrue(path != null && File(path).exists())
        val db = NeolineDbParser.parse(File(path!!).readBytes())
        assertEquals(98_788, db.size)
        assertEquals("241026", db.version)
        for (i in 0 until db.size) {
            check(db.latDeg(i) in 35.0..72.0) { "lat out of range at $i" }
            check(db.lonDeg(i) in 19.0..180.0) { "lon out of range at $i" }
            check(db.heading[i] in 0..360) { "heading out of range at $i" }
            check(db.speedLimit[i] % 5 == 0) { "odd speed limit at $i" }
            if (i > 0) check(db.lat[i] <= db.lat[i - 1]) { "not sorted at $i" }
        }
        val index = CameraIndex(db)
        // Dense camera coverage in central Moscow and central Tashkent.
        check(index.nearby(55.7539, 37.6208, 3000.0, IntList()).size > 50)
        check(index.nearby(41.3111, 69.2797, 3000.0, IntList()).size > 50)
    }
}
