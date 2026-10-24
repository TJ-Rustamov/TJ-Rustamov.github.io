package uz.camdect.core

import kotlin.math.cos
import kotlin.math.floor

/** Uniform lat/lon grid over [CameraDb] for fast "what is near me" queries. */
class CameraIndex(val db: CameraDb, private val cellDeg: Double = 0.05) {
    private val cells = HashMap<Long, IntArray>()

    init {
        val buckets = HashMap<Long, IntList>()
        for (i in 0 until db.size) {
            buckets.getOrPut(key(cell(db.latDeg(i)), cell(db.lonDeg(i)))) { IntList() }.add(i)
        }
        for ((k, v) in buckets) cells[k] = v.toArray()
    }

    /** Indices of cameras within [radiusM] of the point, appended to [out] (cleared first). */
    fun nearby(lat: Double, lon: Double, radiusM: Double, out: IntList): IntList {
        out.clear()
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        forCells(lat - dLat, lon - dLon, lat + dLat, lon + dLon) { i ->
            if (Geo.distanceM(lat, lon, db.latDeg(i), db.lonDeg(i)) <= radiusM) out.add(i)
        }
        return out
    }

    /** Indices of cameras inside a bounding box (for drawing the visible map area). */
    fun inBox(south: Double, west: Double, north: Double, east: Double, out: IntList, limit: Int = Int.MAX_VALUE): IntList {
        out.clear()
        forCells(south, west, north, east) { i ->
            if (out.size < limit) {
                val la = db.latDeg(i)
                val lo = db.lonDeg(i)
                if (la in south..north && lo in west..east) out.add(i)
            }
        }
        return out
    }

    private inline fun forCells(s: Double, w: Double, n: Double, e: Double, fn: (Int) -> Unit) {
        val c0 = cell(s); val c1 = cell(n)
        val r0 = cell(w); val r1 = cell(e)
        // Guard against huge boxes (zoomed far out): caller should limit via [inBox] limit.
        for (a in c0..c1) for (b in r0..r1) {
            val arr = cells[key(a, b)] ?: continue
            for (i in arr) fn(i)
        }
    }

    private fun cell(deg: Double): Int = floor(deg / cellDeg).toInt()
    private fun key(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
}

/** Minimal growable int list to avoid boxing on hot paths. */
class IntList(capacity: Int = 16) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
    fun clear() { size = 0 }
    fun toArray(): IntArray = data.copyOf(size)
}
