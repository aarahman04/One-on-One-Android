package app.web.oneonone.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Call audio the WebView could never do: MODE_IN_COMMUNICATION for the whole call (full
 * voice-call volume, hardware echo cancelling), earpiece by default on voice calls, speaker
 * on video, headsets win, and the proximity sensor turns the screen off at the ear.
 * Everything is restored in [stop].
 */
@Singleton
class AudioRouter @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val mutableRoute = MutableStateFlow(AudioRoute.Earpiece)
    val route: StateFlow<AudioRoute> = mutableRoute.asStateFlow()

    private var active = false
    private var kind = CallKind.Audio
    private var speakerToggle: Boolean? = null
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeaker = false
    private var proximity: PowerManager.WakeLock? = null

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = apply()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = apply()
    }

    @Synchronized fun start(kind: CallKind) {
        if (active) return
        active = true
        this.kind = kind
        speakerToggle = null
        savedMode = audio.mode
        @Suppress("DEPRECATION") run { savedSpeaker = audio.isSpeakerphoneOn }
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.registerAudioDeviceCallback(devices, main)
        apply()
    }

    /** Video toggled on/off mid-call is not a feature; kind is fixed per call. */
    @Synchronized fun toggleSpeaker() {
        if (!active) return
        speakerToggle = route.value != AudioRoute.Speaker
        apply()
    }

    @Synchronized fun stop() {
        if (!active) return
        active = false
        audio.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
        else @Suppress("DEPRECATION") run {
            audio.stopBluetoothSco()
            audio.isBluetoothScoOn = false
            audio.isSpeakerphoneOn = savedSpeaker
        }
        audio.mode = savedMode
        releaseProximity()
    }

    @Synchronized private fun apply() {
        if (!active) return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        val bluetooth = outputs.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && it == AudioDeviceInfo.TYPE_BLE_HEADSET) }
        val wired = outputs.any { it == AudioDeviceInfo.TYPE_WIRED_HEADSET || it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_USB_HEADSET }
        val earpiece = AudioDeviceInfo.TYPE_BUILTIN_EARPIECE in outputs ||
            context.packageManager.hasSystemFeature("android.hardware.telephony")
        val target = RoutePolicy.choose(kind, speakerToggle, wired, bluetooth, earpiece)
        if (Build.VERSION.SDK_INT >= 31) applyModern(target) else applyLegacy(target)
        mutableRoute.value = target
        if (target == AudioRoute.Earpiece) acquireProximity() else releaseProximity()
    }

    private fun applyModern(target: AudioRoute) {
        if (Build.VERSION.SDK_INT < 31) return
        val wanted = when (target) {
            AudioRoute.Earpiece -> setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            AudioRoute.Speaker -> setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            AudioRoute.Wired -> setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
            AudioRoute.Bluetooth -> setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val device = audio.availableCommunicationDevices.firstOrNull { it.type in wanted }
        if (device != null) audio.setCommunicationDevice(device) else audio.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(target: AudioRoute) {
        if (target == AudioRoute.Bluetooth) {
            audio.startBluetoothSco()
            audio.isBluetoothScoOn = true
        } else if (audio.isBluetoothScoOn) {
            audio.stopBluetoothSco()
            audio.isBluetoothScoOn = false
        }
        audio.isSpeakerphoneOn = target == AudioRoute.Speaker
    }

    @SuppressLint("WakelockTimeout") // held exactly for the call; released in stop()
    private fun acquireProximity() {
        if (proximity?.isHeld == true) return
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        proximity = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "oneonone:call-proximity").also { it.acquire() }
    }

    private fun releaseProximity() {
        proximity?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        proximity = null
    }
}
