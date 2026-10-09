package app.web.oneonone.ui.chat

import app.web.oneonone.data.model.ChatMessage
import java.time.Instant
import java.time.ZoneId

private const val GroupWindowMs = 60_000L

/**
 * Mirrors ChatPage.ts updateGroup: a message starts a new visual group (gets a tail and a larger top
 * margin) unless it directly follows a message from the same sender, 0..60s earlier, on the same local day.
 * A system line breaks a group, like the date separator does on the web.
 */
internal fun isGroupStart(prev: ChatMessage?, cur: ChatMessage, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (prev == null || prev.type == "system" || cur.type == "system") return true
    if (prev.senderId != cur.senderId) return true
    val before = Instant.parse(prev.createdAt)
    val now = Instant.parse(cur.createdAt)
    val delta = now.toEpochMilli() - before.toEpochMilli()
    if (delta < 0 || delta > GroupWindowMs) return true
    return before.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()
}
