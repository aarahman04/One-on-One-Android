package app.web.oneonone.data.games

@Suppress("UNUSED_PARAMETER")
internal object PongContactLog {
    fun record(v: Double, ct: Long, gap: Double, predicted: Boolean, confirmed: Boolean, delay: Double, snap: Double) = Unit
}
