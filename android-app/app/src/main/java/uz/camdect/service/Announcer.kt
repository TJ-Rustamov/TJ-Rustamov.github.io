package uz.camdect.service

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import uz.camdect.R
import uz.camdect.core.AlertEngine
import uz.camdect.data.Prefs
import uz.camdect.data.TypeLabels
import java.util.Locale

/** Speaks / beeps / vibrates alerts. Uses the phone's offline TTS engine. */
class Announcer(private val context: Context, private val prefs: Prefs) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsLang: String? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()

    init {
        initTts()
    }

    private fun initTts() {
        ttsLang = prefs.voiceLang
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                configureTts()
                if (pendingTest) { pendingTest = false; speak(voiceStrings().getString(R.string.tts_test)) }
            } else DriveState.log("Voice engine failed to start ($status)")
        }
    }

    private fun configureTts() {
        val t = tts ?: return
        val locale = locale()
        if (t.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) t.language = locale
        else DriveState.log("Voice: ${locale.displayLanguage} not installed, using ${t.voice?.locale?.displayLanguage ?: "default"}")
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = abandonFocus()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = abandonFocus()
        })
    }

    private fun locale(): Locale = when (val l = prefs.voiceLang) {
        "system" -> Locale.getDefault()
        else -> Locale(l)
    }

    /** Strings in the voice language, independent of the UI language. */
    private fun voiceStrings(): Context {
        val conf = Configuration(context.resources.configuration)
        conf.setLocale(locale())
        return context.createConfigurationContext(conf)
    }

    fun announce(event: AlertEngine.Event, db: uz.camdect.core.CameraDb) {
        if (prefs.voiceLang != ttsLang) {
            tts?.shutdown()
            initTts()
        }
        val t = event.target
        if (prefs.beep) beep(if (t.level == AlertEngine.Level.CLOSE) 2 else 1)
        if (prefs.vibrate) vibrate()
        if (prefs.voice) {
            val metres = roundDistance(t.distanceM)
            val s = voiceStrings()
            val name = TypeLabels.custom(context, db.typeCode(t.camera))
            val withLimit = t.speedLimitKmh > 0 && event.firstForCamera
            val text = when {
                name != null && withLimit -> s.getString(R.string.tts_named_limit, name, metres, t.speedLimitKmh)
                name != null -> s.getString(R.string.tts_named, name, metres)
                withLimit -> s.getString(R.string.tts_camera_limit, metres, t.speedLimitKmh)
                t.speedLimitKmh > 0 -> s.getString(R.string.tts_camera, metres)
                else -> s.getString(R.string.tts_control, metres)
            }
            speak(text)
        }
    }

    /** Plays a sample alert so the user can check volume and voice. */
    fun test() {
        beep(1)
        vibrate()
        if (ttsReady) speak(voiceStrings().getString(R.string.tts_test))
        else pendingTest = true
    }

    private var pendingTest = false

    fun overspeed() {
        if (prefs.beep) beep(3)
        if (prefs.voice) speak(voiceStrings().getString(R.string.tts_slow_down))
    }

    private fun speak(text: String) {
        val t = tts ?: return
        if (!ttsReady) return
        requestFocus()
        t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "alert")
    }

    private fun beep(times: Int) {
        val g = tone ?: return
        // Short ascending beeps; ToneGenerator is async so stack them with a duration.
        g.startTone(if (times >= 3) ToneGenerator.TONE_CDMA_HIGH_L else ToneGenerator.TONE_PROP_BEEP2, 150 * times)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
        else v.vibrate(longArrayOf(0, 200, 120, 200), -1)
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        audio.abandonAudioFocus(focusListener)
    }

    fun shutdown() {
        tts?.shutdown()
        tone?.release()
    }

    companion object {
        /** 437 m → 450, 1180 m → 1200: easier to hear. */
        fun roundDistance(m: Double): Int = if (m < 100) ((m / 10).toInt() * 10) else (Math.round(m / 50.0) * 50).toInt()
    }
}
