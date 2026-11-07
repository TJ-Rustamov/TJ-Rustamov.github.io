package uz.camdect.core

/**
 * Decoded camera points, stored as parallel primitive arrays so ~100k points
 * stay around 2 MB of heap even on old phones.
 *
 * Coordinates are fixed-point: degrees * 10_000 (≈11 m precision), exactly as
 * stored in the Neoline file.
 */
class CameraDb(
    /** Database build date as written in the file, e.g. "241026" (yyMMdd). */
    val version: String,
    val lat: IntArray,
    val lon: IntArray,
    /** Direction of travel the camera watches, degrees 0..360. */
    val heading: ShortArray,
    /** Speed limit in km/h, 0 when the point has no limit (e.g. a police post). */
    val speedLimit: ShortArray,
    /** Raw Neoline type code (byte 0 of the record). See [CameraTypes]. */
    val type: ByteArray,
    /** Raw flag byte (0, 1 or 3); 3 appears on some zone/average-speed points. */
    val flag: ByteArray,
    /** Camera flag bits (byte 21). Bit 2 = sees cars from behind; others not yet known. */
    val cameraFlags: ByteArray,
    /** Distance the camera covers, metres (0 = not set in the database). */
    val rangeM: ShortArray,
) {
    val size: Int get() = lat.size

    fun latDeg(i: Int): Double = lat[i] / SCALE
    fun lonDeg(i: Int): Double = lon[i] / SCALE
    fun typeCode(i: Int): Int = type[i].toInt() and 0xFF

    /** True when the camera photographs cars from behind (they drive away from it). */
    fun isRear(i: Int): Boolean = (cameraFlags[i].toInt() and REAR_BIT) != 0

    /** How far the camera's danger area reaches, metres. */
    fun coverageM(i: Int): Int = rangeM[i].toInt().takeIf { it > 0 } ?: DEFAULT_COVERAGE_M

    /**
     * Bearing the camera lens points to. A front-facing camera looks back at oncoming cars
     * (opposite to the traffic direction); a rear-facing one looks the way traffic goes.
     */
    fun lensBearing(i: Int): Double {
        val h = heading[i].toDouble()
        return if (isRear(i)) h % 360.0 else (h + 180.0) % 360.0
    }

    companion object {
        const val SCALE = 10_000.0
        private const val REAR_BIT = 0x04
        const val DEFAULT_COVERAGE_M = 300
    }
}
