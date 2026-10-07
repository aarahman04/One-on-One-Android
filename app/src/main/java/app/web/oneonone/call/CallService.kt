package app.web.oneonone.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the microphone (and camera) alive while the app is in the background during a call.
 * Started from the foreground (dialing, or answering opens the app first), as Android 14+
 * requires for microphone/camera service types.
 */
class CallService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val kind = CallProtocol.kindOf(intent?.getStringExtra(EXTRA_KIND))
        val peer = intent?.getStringExtra(EXTRA_PEER) ?: "Call"
        var types = 0
        if (Build.VERSION.SDK_INT >= 29) types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (Build.VERSION.SDK_INT >= 30) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (kind == CallKind.Video) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        try {
            ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, CallNotifications.ongoing(this, kind, peer), types)
        } catch (_: RuntimeException) {
            // Refused (e.g. started without a visible activity). The call still works while the app is open.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_STOP = "app.web.oneonone.call.STOP_SERVICE"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PEER = "peer"

        fun start(context: Context, kind: CallKind, peer: String) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)
                    .putExtra(EXTRA_KIND, CallProtocol.wire(kind)).putExtra(EXTRA_PEER, peer))
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, CallService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
