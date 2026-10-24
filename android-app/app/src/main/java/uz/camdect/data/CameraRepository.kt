package uz.camdect.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.camdect.core.CameraDb
import uz.camdect.core.CameraIndex
import uz.camdect.core.NeolineDbParser
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Holds the loaded camera database. Source order:
 *  1. a file the user imported (internal storage, survives updates),
 *  2. `assets/cameras.db` if one was bundled at build time.
 */
object CameraRepository {
    sealed class State {
        object Loading : State()
        object Missing : State()
        data class Error(val message: String) : State()
        class Ready(val db: CameraDb, val index: CameraIndex) : State()
    }

    private const val FILE_NAME = "cameras.db"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    val ready: State.Ready? get() = _state.value as? State.Ready

    fun load(context: Context) {
        val app = context.applicationContext
        scope.launch {
            _state.value = try {
                val bytes = readStored(app)
                if (bytes == null) State.Missing else build(bytes)
            } catch (e: IOException) {
                State.Error(e.message ?: e.toString())
            }
        }
    }

    /** Validates the picked file, then stores it as the active database. */
    suspend fun import(context: Context, uri: Uri): Result<State.Ready> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open file")
            val ready = build(bytes) // throws if it is not a Neoline database
            val target = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("Cannot save database")
            }
            _state.value = ready
            ready
        }
    }

    private fun readStored(context: Context): ByteArray? {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists()) return f.readBytes()
        return try {
            context.assets.open(FILE_NAME).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private fun build(bytes: ByteArray): State.Ready {
        val db = NeolineDbParser.parse(bytes)
        return State.Ready(db, CameraIndex(db))
    }
}
