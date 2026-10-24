package uz.camdect.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uz.camdect.core.AlertEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Live state shared from [RadarService] to the UI (same process). */
object DriveState {
    data class Snapshot(
        val running: Boolean = false,
        val hasFix: Boolean = false,
        val lat: Double = Double.NaN,
        val lon: Double = Double.NaN,
        val speedKmh: Int = 0,
        val courseDeg: Double = Double.NaN,
        val accuracyM: Int = 0,
        val target: AlertEngine.Target? = null,
    )

    internal val mutable = MutableStateFlow(Snapshot())
    val flow: StateFlow<Snapshot> = mutable

    // Small in-memory log so drives can be checked afterwards (menu → Alert log).
    private const val MAX_LOG = 80
    private val entries = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(message: String) {
        entries.addLast("${time.format(Date())}  $message")
        while (entries.size > MAX_LOG) entries.removeFirst()
    }

    @Synchronized
    fun logLines(): List<String> = entries.reversed()
}
