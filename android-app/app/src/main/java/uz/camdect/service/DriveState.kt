package uz.camdect.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uz.camdect.core.AlertEngine

/** Live state shared from [RadarService] to the UI (same process). */
object DriveState {
    data class Snapshot(
        val running: Boolean = false,
        val hasFix: Boolean = false,
        val lat: Double = Double.NaN,
        val lon: Double = Double.NaN,
        val speedKmh: Int = 0,
        val courseDeg: Double = Double.NaN,
        val target: AlertEngine.Target? = null,
    )

    internal val mutable = MutableStateFlow(Snapshot())
    val flow: StateFlow<Snapshot> = mutable
}
