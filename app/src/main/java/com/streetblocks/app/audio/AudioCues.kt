package com.streetblocks.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.streetblocks.app.data.model.AppSettings
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Système sonore « terrain » :
 *  - bips synthétisés (aucun fichier audio), joués sur le flux ALARME
 *    → audibles même si le volume média est bas ;
 *  - timbre riche en harmoniques + LoudnessEnhancer en mode extérieur ;
 *  - volume alarme poussé au maximum pendant la séance (restauré après) ;
 *  - annonces vocales (synthèse vocale française) ;
 *  - vibrations fortes.
 */
class AudioCues(context: Context) {

    enum class Cue { START, GO, SET_END, CHANGE, TICK, FINAL_TICK, HALF, SESSION_END }

    private data class Note(val freq: Double, val ms: Int)

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()

    private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(alarmAttrs)
        .build()
    private val abandonFocus = Runnable { runCatching { am?.abandonAudioFocusRequest(focusRequest) } }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") appContext.getSystemService(Vibrator::class.java)

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var savedAlarmVolume: Int? = null

    private var settings = AppSettings()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val r = t.setLanguage(Locale.FRANCE)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.FRENCH)
                t.setAudioAttributes(alarmAttrs)
                ttsReady = true
            }
        }
    }

    fun configure(s: AppSettings) {
        settings = s
        tts?.setSpeechRate(if (s.outdoorMode) 0.95f else 1.05f)
    }

    // ---------------------------------------------------------------- volume de séance

    fun beginSession() {
        val manager = am ?: return
        runCatching {
            val maxV = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = manager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (savedAlarmVolume == null) savedAlarmVolume = cur
            val target = if (settings.outdoorMode) maxV else max(cur, (maxV * 0.75).roundToInt())
            if (target != cur) manager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }.onFailure { Log.w(TAG, "Volume alarme non modifiable", it) }
    }

    fun endSession() {
        val v = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching { am?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
    }

    // ---------------------------------------------------------------- sons

    fun play(cue: Cue) {
        val outdoor = settings.outdoorMode
        if (settings.sounds) {
            val pcm = cache.getOrPut("$cue-$outdoor") { synth(pattern(cue, outdoor), rich = outdoor || cue != Cue.TICK) }
            playPcm(pcm, outdoor)
        }
        if (settings.vibration) vibrate(cue, outdoor)
    }

    /** Durée approximative d'un signal, pour enchaîner la voix après. */
    fun cueMs(cue: Cue): Long = if (!settings.sounds) 0 else pattern(cue, settings.outdoorMode).sumOf { it.ms }.toLong()

    fun speak(text: String, delayMs: Long = 0) {
        if (!settings.voice || text.isBlank()) return
        main.postAtTime({
            val t = tts
            if (t != null && ttsReady) {
                duck(1200L + text.length * 75L)
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
                t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sb-${System.nanoTime()}")
            }
        }, SPEECH_TOKEN, SystemClock.uptimeMillis() + delayMs)
    }

    fun cancelSpeech() {
        main.removeCallbacksAndMessages(SPEECH_TOKEN)
        runCatching { tts?.stop() }
    }

    private fun pattern(cue: Cue, outdoor: Boolean): List<Note> {
        val gap = Note(0.0, 70)
        val base = when (cue) {
            Cue.TICK -> listOf(Note(1175.0, if (outdoor) 200 else 140))
            Cue.FINAL_TICK -> listOf(Note(1568.0, if (outdoor) 260 else 180))
            Cue.GO -> listOf(Note(2093.0, if (outdoor) 900 else 650))
            Cue.START -> listOf(Note(1047.0, 150), gap, Note(1319.0, 150), gap, Note(1568.0, 150), gap, Note(2093.0, 550))
            Cue.SET_END -> listOf(Note(1760.0, 330), Note(0.0, 110), Note(1760.0, 330), Note(0.0, 110), Note(1319.0, 550))
            Cue.CHANGE -> listOf(Note(1568.0, 160), gap, Note(1319.0, 160), gap, Note(1568.0, 160), gap, Note(2093.0, 320))
            Cue.HALF -> listOf(Note(1319.0, 200), gap, Note(1319.0, 200))
            Cue.SESSION_END -> listOf(
                Note(1047.0, 180), gap, Note(1319.0, 180), gap, Note(1568.0, 180), gap, Note(2093.0, 300),
                Note(0.0, 120), Note(1568.0, 160), gap, Note(2093.0, 700),
            )
        }
        // Mode extérieur : signaux importants répétés
        val repeat = outdoor && cue in setOf(Cue.GO, Cue.SET_END, Cue.START, Cue.CHANGE)
        return if (repeat) base + Note(0.0, 220) + base else base
    }

    private fun synth(notes: List<Note>, rich: Boolean): ShortArray {
        val total = notes.sumOf { it.ms } * SR / 1000
        val out = ShortArray(total)
        var pos = 0
        for (n in notes) {
            val len = n.ms * SR / 1000
            if (n.freq > 0) {
                val fade = min(len / 4, SR * 6 / 1000).coerceAtLeast(1)
                val w = 2 * PI * n.freq / SR
                for (i in 0 until len) {
                    if (pos + i >= total) break
                    val x = w * i
                    var v = sin(x)
                    if (rich) v = (v + sin(3 * x) / 3 + sin(5 * x) / 5 + sin(7 * x) / 7) * 1.05
                    val env = when {
                        i < fade -> i.toDouble() / fade
                        i > len - fade -> (len - i).toDouble() / fade
                        else -> 1.0
                    }
                    out[pos + i] = ((v * env).coerceIn(-1.0, 1.0) * 0.97 * Short.MAX_VALUE).toInt().toShort()
                }
            }
            pos += len
        }
        return out
    }

    private fun playPcm(data: ShortArray, outdoor: Boolean) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(alarmAttrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            var enhancer: LoudnessEnhancer? = null
            if (settings.volumeBoost) {
                enhancer = runCatching {
                    LoudnessEnhancer(track.audioSessionId).apply {
                        setTargetGain(if (outdoor) 1200 else 500)
                        enabled = true
                    }
                }.getOrNull()
            }
            track.setVolume(1f)
            val durMs = data.size * 1000L / SR
            duck(durMs + 200)
            track.play()
            main.postDelayed({
                runCatching { track.stop() }
                runCatching { track.release() }
                runCatching { enhancer?.release() }
            }, durMs + 400)
        } catch (t: Throwable) {
            Log.w(TAG, "Lecture du son impossible", t)
        }
    }

    /** Baisse momentanément la musique de l'utilisateur. */
    private fun duck(ms: Long) {
        runCatching { am?.requestAudioFocus(focusRequest) }
        main.removeCallbacks(abandonFocus)
        main.postDelayed(abandonFocus, ms)
    }

    private fun vibrate(cue: Cue, outdoor: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = when (cue) {
            Cue.TICK -> longArrayOf(0, 90)
            Cue.FINAL_TICK -> longArrayOf(0, 160)
            Cue.GO -> longArrayOf(0, 600)
            Cue.START -> longArrayOf(0, 200, 100, 200, 100, 500)
            Cue.SET_END -> longArrayOf(0, 400, 150, 400)
            Cue.CHANGE -> longArrayOf(0, 150, 80, 150, 80, 300)
            Cue.HALF -> longArrayOf(0, 120, 100, 120)
            Cue.SESSION_END -> longArrayOf(0, 300, 120, 300, 120, 800)
        }
        val scaled = if (outdoor) pattern.mapIndexed { i, d -> if (i % 2 == 1) (d * 1.5).toLong() else d }.toLongArray() else pattern
        runCatching {
            if (v.hasAmplitudeControl()) {
                val amps = IntArray(scaled.size) { i -> if (i % 2 == 1) 255 else 0 }
                v.vibrate(VibrationEffect.createWaveform(scaled, amps, -1))
            } else {
                v.vibrate(VibrationEffect.createWaveform(scaled, -1))
            }
        }
    }

    companion object {
        private const val TAG = "AudioCues"
        private const val SR = 44100
        private val SPEECH_TOKEN = Any()
    }
}
