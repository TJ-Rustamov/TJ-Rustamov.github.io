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
                    TestDb.Point(41.3111, 69.2797, 90, 60, controls = setOf(Control.RED_LIGHT, Control.CROSSWALK)),
                    TestDb.Point(55.7539, 37.6208, 270, 0, type = 0xEF, rear = true, rangeM = 0),
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
        // Rear-facing camera looks the way traffic goes; front-facing one looks back at it.
        assertEquals(true, db.isRear(0))
        assertEquals(270.0, db.lensBearing(0), 1e-9)
        assertEquals(CameraDb.DEFAULT_COVERAGE_M, db.coverageM(0))
        assertEquals(false, db.isRear(1))
        assertEquals(270.0, db.lensBearing(1), 1e-9)
        assertEquals(400, db.coverageM(1))
        assertEquals(listOf(Control.RED_LIGHT, Control.CROSSWALK), db.controls(1))
        assertEquals(false, db.isRear(1)) // control bits must not touch the rear bit
        assertEquals(emptyList<Control>(), db.controls(0))
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
        var rear = 0
        for (i in 0 until db.size) {
            if (db.isRear(i)) rear++
            check(db.rangeM[i] in 0..1500) { "range out of range at $i" }
        }
        check(rear in 20_000..35_000) { "unexpected rear-facing count $rear" }
        // One physical camera often has two records: front view for one direction, rear view for the other.
        var pairs = 0
        var oneRear = 0
        for (i in 0 until db.size - 1) {
            val j = i + 1
            if (Math.abs(db.lat[i] - db.lat[j]) <= 3 && Math.abs(db.lon[i] - db.lon[j]) <= 3 &&
                db.type[i] == db.type[j] && Geo.angleDiff(db.heading[i].toDouble(), db.heading[j].toDouble()) > 150
            ) {
                pairs++
                if (db.isRear(i) != db.isRear(j)) oneRear++
            }
        }
        check(oneRear > pairs * 0.6) { "rear flag pattern missing: $oneRear of $pairs" }
        val index = CameraIndex(db)
        // Dense camera coverage in central Moscow and central Tashkent.
        check(index.nearby(55.7539, 37.6208, 3000.0, IntList()).size > 50)
        check(index.nearby(41.3111, 69.2797, 3000.0, IntList()).size > 50)
    }
}
