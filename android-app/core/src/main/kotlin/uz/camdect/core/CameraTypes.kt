package uz.camdect.core

/**
 * Human labels for Neoline type codes. The code meanings are not documented;
 * until they are mapped we describe a point by its speed limit.
 */
object CameraTypes {
    fun label(db: CameraDb, i: Int): String {
        val limit = db.speedLimit[i].toInt()
        return if (limit > 0) "Camera $limit km/h" else "Control point"
    }
}
