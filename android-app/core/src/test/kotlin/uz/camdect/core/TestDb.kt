package uz.camdect.core

/** Builds a Neoline-format file in memory (the inverse of [NeolineDbParser]). */
object TestDb {
    data class Point(val lat: Double, val lon: Double, val heading: Int, val speed: Int, val type: Int = 0xA5)

    fun build(points: List<Point>, version: String = "241026"): ByteArray {
        val h = NeolineDbParser.HEADER_SIZE
        val r = NeolineDbParser.RECORD_SIZE
        val out = ByteArray(h + (NeolineDbParser.FIRST_POINT_RECORD + points.size) * r)
        "BRMSC\u0000\u0000JHKIM".toByteArray(Charsets.US_ASCII).copyInto(out)
        putLe(out, 263, out.size - h)
        version.toByteArray(Charsets.US_ASCII).copyInto(out, h + 22 * r)
        putLe(out, h + 23 * r, points.size)
        points.sortedByDescending { it.lat }.forEachIndexed { n, p ->
            val o = h + (NeolineDbParser.FIRST_POINT_RECORD + n) * r
            val plain = IntArray(13)
            plain[0] = p.type
            putBe(plain, 1, 4, Math.round(p.lat * 10_000).toInt())
            putBe(plain, 5, 4, Math.round(p.lon * 10_000).toInt())
            putBe(plain, 9, 2, p.heading)
            putBe(plain, 12, 1, p.speed)
            for (i in plain.indices) out[o + i] = (plain[i] xor NeolineDbParser.KEY[i]).toByte()
        }
        return out
    }

    private fun putBe(dst: IntArray, start: Int, len: Int, v: Int) {
        for (i in 0 until len) dst[start + i] = (v ushr (8 * (len - 1 - i))) and 0xFF
    }

    private fun putLe(dst: ByteArray, o: Int, v: Int) {
        for (i in 0 until 4) dst[o + i] = (v ushr (8 * i)).toByte()
    }
}
