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
) {
    val size: Int get() = lat.size

    fun latDeg(i: Int): Double = lat[i] / SCALE
    fun lonDeg(i: Int): Double = lon[i] / SCALE
    fun typeCode(i: Int): Int = type[i].toInt() and 0xFF

    companion object {
        const val SCALE = 10_000.0
    }
}
