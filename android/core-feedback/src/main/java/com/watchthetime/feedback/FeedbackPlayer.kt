package com.watchthetime.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.watchthetime.domain.engine.Cue
import com.watchthetime.domain.engine.CueType
import com.watchthetime.domain.settings.FeedbackSettings

/**
 * Plays the sound and vibration for cues.
 *
 * Audio uses USAGE_GAME so it follows the media volume and the system's normal routing: when
 * wired or Bluetooth earbuds are connected the cues go to the earbuds (the referee hears the
 * buzzer, the gym doesn't), otherwise to the loudspeaker. [outputRoute] reports which.
 */
class FeedbackPlayer(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded = mutableSetOf<Int>()
    private val soundIds: Map<CueType, Int>
    private val pendingPlay = mutableMapOf<Int, Float>()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)

    init {
        pool.setOnLoadCompleteListener { p, id, status ->
            if (status == 0) {
                loaded += id
                pendingPlay.remove(id)?.let { vol -> p.play(id, vol, vol, 1, 0, 1f) }
            }
        }
        soundIds = CueCatalog.all.mapValues { (_, spec) -> pool.load(app, spec.soundRes, 1) }
    }

    /**
     * Plays a batch of cues produced by one action. Only the highest-priority sound and the
     * highest-priority vibration are played, so one tap never produces a cacophony.
     */
    fun play(cues: List<Cue>, settings: FeedbackSettings) {
        if (cues.isEmpty()) return
        cues.sortedByDescending { it.type.priority }.firstOrNull { settings.soundEnabled(it.type) }
            ?.let { playSound(it.type, settings.masterVolume) }
        cues.sortedByDescending { it.type.priority }.firstOrNull { settings.hapticEnabled(it.type) }
            ?.let { vibrate(it.type, settings.hapticStrength) }
    }

    /** Settings screen preview: plays regardless of per-cue toggles. */
    fun preview(type: CueType, settings: FeedbackSettings, sound: Boolean = true, haptic: Boolean = true) {
        if (sound) playSound(type, settings.masterVolume.coerceAtLeast(0.3f))
        if (haptic) vibrate(type, settings.hapticStrength)
    }

    private fun playSound(type: CueType, volume: Float) {
        val id = soundIds[type] ?: return
        val v = volume.coerceIn(0f, 1f)
        if (id in loaded) pool.play(id, v, v, 1, 0, 1f) else pendingPlay[id] = v
    }

    private fun vibrate(type: CueType, strength: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val h = CueCatalog.of(type).haptic
        val s = strength.coerceIn(0.1f, 1f)
        val effect = if (vib.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(
                h.timings, h.amplitudes.map { if (it == 0) 0 else (it * s).toInt().coerceIn(1, 255) }.toIntArray(), -1,
            )
        } else {
            VibrationEffect.createWaveform(h.timings, -1)
        }
        vib.cancel()
        vib.vibrate(effect)
    }

    enum class Route(val label: String) { SPEAKER("Phone speaker"), WIRED("Wired earphones"), BLUETOOTH("Bluetooth earbuds") }

    fun outputRoute(): Route {
        val devices = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        val types = devices.map { it.type }.toSet()
        return when {
            types.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_BLE_HEADSET } -> Route.BLUETOOTH
            types.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_WIRED_HEADSET || it == AudioDeviceInfo.TYPE_USB_HEADSET } -> Route.WIRED
            else -> Route.SPEAKER
        }
    }

    fun release() {
        pool.release()
        vibrator?.cancel()
    }
}
