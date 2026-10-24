package app.web.oneonone.data.games

import android.util.Log

internal object PongContactLog {
    fun record(v: Double, ct: Long, gap: Double, predicted: Boolean, confirmed: Boolean, delay: Double, snap: Double) {
        runCatching { Log.d("PongContact", "v=$v ct=$ct gap=$gap predicted=$predicted confirmed=$confirmed confirmationMs=$delay snap=$snap") }
    }
}
