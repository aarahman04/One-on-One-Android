package app.web.oneonone.ui.chat

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.web.oneonone.data.realtime.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ChatPresence(val label: String, val online: Boolean = false)

internal fun chatPresence(
    state: ConnectionState,
    lastReadAt: String?,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    timePattern: String,
): ChatPresence {
    when (state) {
        ConnectionState.Offline -> return ChatPresence("Waiting for network")
        ConnectionState.Connecting -> return ChatPresence("Connecting…")
        ConnectionState.Connected -> Unit
    }
    val lastRead = lastReadAt?.let(Instant::parse) ?: return ChatPresence("Offline")
    if (now.toEpochMilli() - lastRead.toEpochMilli() < 15_000) return ChatPresence("Online", online = true)
    val seen = lastRead.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = seen.format(DateTimeFormatter.ofPattern(timePattern, locale))
    val label = when (seen.toLocalDate()) {
        today -> "Last seen today at $time"
        today.minusDays(1) -> "Last seen yesterday at $time"
        else -> "Last seen ${seen.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
    }
    return ChatPresence(label)
}

@Composable
internal fun rememberChatPresence(state: ConnectionState, lastReadAt: String?): ChatPresence {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lastReadAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                value = Instant.now()
                delay(5_000)
            }
        }
    }
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
    return chatPresence(state, lastReadAt, now, ZoneId.systemDefault(), locale, pattern)
}
