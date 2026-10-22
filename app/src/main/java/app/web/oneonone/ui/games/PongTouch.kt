package app.web.oneonone.ui.games

/** One finger owns a stroke until lift/cancel; cancellation never applies an unrendered target. */
internal class PongTouch {
    var x = .5f; private set
    var y = 1.88f; private set
    private var pointer: Long? = null
    val held get() = pointer != null

    fun begin(id: Long, x: Float, y: Float): Boolean {
        if (held) return false
        pointer = id; this.x = x; this.y = y
        return true
    }
    fun move(id: Long, x: Float, y: Float) {
        if (pointer == id && x.isFinite() && y.isFinite()) { this.x = x; this.y = y }
    }
    fun end(id: Long, cancelled: Boolean, actualX: Float, actualY: Float): Pair<Float, Float>? {
        if (pointer != id) return null
        pointer = null
        return if (cancelled) actualX to actualY else x to y
    }
    fun cancel() { pointer = null }
}
