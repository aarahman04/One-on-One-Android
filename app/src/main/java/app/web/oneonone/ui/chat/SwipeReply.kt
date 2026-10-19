package app.web.oneonone.ui.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** Reply swipes go left on my bubbles and right on theirs, and only when clearly horizontal (|dx| > 2|dy|) past touch slop. */
internal fun claimsReplySwipe(dx: Float, dy: Float, slop: Float, mine: Boolean): Boolean =
    abs(dx) > slop && abs(dx) > 2 * abs(dy) && (dx < 0) == mine

/** Past [trigger] extra travel counts half, up to [cap] of total translation. */
internal fun rubberBand(travel: Float, trigger: Float, cap: Float): Float =
    if (travel <= trigger) travel else minOf(trigger + (travel - trigger) * .5f, cap)

/** Released past the trigger, or a fast fling that already covered half of it. */
internal fun replyTriggered(travel: Float, velocity: Float, trigger: Float, flingVelocity: Float): Boolean =
    travel >= trigger || (travel >= trigger / 2 && velocity >= flingVelocity)

/**
 * Swipe-to-reply that claims the pointer only for a clearly horizontal drag, so the list keeps scrolling vertically.
 * [onDrag] gets the signed, rubber-banded translation in px; [onEnd] says whether to reply.
 */
internal fun Modifier.replySwipe(enabled: Boolean, mine: Boolean, triggerPx: Float, capPx: Float, flingPx: Float,
    onDrag: (Float) -> Unit, onTrigger: () -> Unit, onEnd: (Boolean) -> Unit): Modifier =
    if (!enabled) this else pointerInput(mine, triggerPx) {
        val slop = viewConfiguration.touchSlop
        val sign = if (mine) -1f else 1f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero; var claimed = false; var armed = false; var travel = 0f; var finished = false
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id } ?: break
                    // Someone else (list scroll, long press) owns this pointer: never claim it.
                    if (!claimed && change.isConsumed) break
                    if (change.changedToUpIgnoreConsumed()) {
                        if (claimed) {
                            finished = true; change.consume()
                            onEnd(replyTriggered(travel, tracker.calculateVelocity().x * sign, triggerPx, flingPx))
                        }
                        break
                    }
                    total += change.positionChange(); tracker.addPosition(change.uptimeMillis, change.position)
                    if (!claimed) {
                        if (abs(total.y) > slop && !claimsReplySwipe(total.x, total.y, slop, mine)) break // vertical intent: leave it to the list
                        claimed = claimsReplySwipe(total.x, total.y, slop, mine)
                    }
                    if (claimed) {
                        change.consume()
                        travel = rubberBand((total.x * sign - slop).coerceAtLeast(0f), triggerPx, capPx)
                        if (!armed && travel >= triggerPx) { armed = true; onTrigger() } else if (armed && travel < triggerPx) armed = false
                        onDrag(travel * sign)
                    }
                }
            } finally { if (claimed && !finished) onEnd(false) }
        }
    }
