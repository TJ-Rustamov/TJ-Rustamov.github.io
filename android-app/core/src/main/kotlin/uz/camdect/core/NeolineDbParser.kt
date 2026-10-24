package uz.camdect.core

import java.io.IOException

/**
 * Reader for the Neoline X-COP 7500s GPS database (`X-COP_7500s_Baza_GPS.db`).
 *
 * Layout (reverse engineered):
 *  - 512-byte header starting with "BRMSC"; u32 LE at offset 263 = payload size.
 *  - 24-byte records from offset 512. Record 22 holds the ASCII date, record 23
 *    the u32 LE point count, points start at record 24 (sorted by latitude, desc).
 *  - Each point is big-endian and XOR-obfuscated with a fixed per-byte key.
 */
object NeolineDbParser {
    const val HEADER_SIZE = 512
    const val RECORD_SIZE = 24
    private const val VERSION_RECORD = 22
    private const val COUNT_RECORD = 23
    const val FIRST_POINT_RECORD = 24

    private val MAGIC = "BRMSC".toByteArray(Charsets.US_ASCII)

    // XOR key for the decoded bytes of a 24-byte point record (bytes 13..23 are unused).
    internal val KEY = intArrayOf(
        0x00,                   // 0: type code (left raw)
        0xAE, 0xCB, 0x2C, 0x7D, // 1-4: latitude * 1e4
        0x99, 0x80, 0x46, 0x4A, // 5-8: longitude * 1e4
        0x00, 0x13,             // 9-10: heading
        0xAC,                   // 11: flag
        0x8A,                   // 12: speed limit
    )

    @Throws(IOException::class)
    fun parse(bytes: ByteArray): CameraDb {
        if (bytes.size < HEADER_SIZE + RECORD_SIZE * FIRST_POINT_RECORD) throw IOException("File too small")
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) throw IOException("Not a Neoline GPS database")

        if ((bytes.size - HEADER_SIZE) % RECORD_SIZE != 0) throw IOException("Truncated or corrupt database")

        val versionOff = HEADER_SIZE + VERSION_RECORD * RECORD_SIZE
        val version = String(bytes, versionOff, 6, Charsets.US_ASCII).trim { it <= ' ' }

        val totalRecords = (bytes.size - HEADER_SIZE) / RECORD_SIZE - FIRST_POINT_RECORD
        val declared = u32le(bytes, HEADER_SIZE + COUNT_RECORD * RECORD_SIZE)
        val count = if (declared in 1..totalRecords) declared.toInt() else totalRecords

        val lat = IntArray(count)
        val lon = IntArray(count)
        val heading = ShortArray(count)
        val speed = ShortArray(count)
        val type = ByteArray(count)
        val flag = ByteArray(count)

        var n = 0
        for (r in 0 until count) {
            val o = HEADER_SIZE + (FIRST_POINT_RECORD + r) * RECORD_SIZE
            if (isBlank(bytes, o)) continue
            lat[n] = field(bytes, o, 1, 4)
            lon[n] = field(bytes, o, 5, 4)
            heading[n] = field(bytes, o, 9, 2).toShort()
            flag[n] = field(bytes, o, 11, 1).toByte()
            speed[n] = field(bytes, o, 12, 1).toShort()
            type[n] = bytes[o]
            n++
        }
        return CameraDb(
            version, lat.copyOf(n), lon.copyOf(n), heading.copyOf(n),
            speed.copyOf(n), type.copyOf(n), flag.copyOf(n),
        )
    }

    /** Big-endian de-obfuscated value of [len] bytes starting at byte [start] of the record at [o]. */
    private fun field(b: ByteArray, o: Int, start: Int, len: Int): Int {
        var v = 0
        for (i in start until start + len) v = (v shl 8) or ((b[o + i].toInt() and 0xFF) xor KEY[i])
        return v
    }

    private fun isBlank(b: ByteArray, o: Int): Boolean {
        val first = b[o]
        if (first != 0.toByte() && first != 0xFF.toByte()) return false
        for (i in 1 until RECORD_SIZE) if (b[o + i] != first) return false
        return true
    }

    private fun u32le(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}
