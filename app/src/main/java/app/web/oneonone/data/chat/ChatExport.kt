package app.web.oneonone.data.chat

import app.web.oneonone.data.model.*
import kotlinx.serialization.json.Json
private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

internal fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

fun letterHtml(message: ChatMessage): String {
    val botanical = message.payload.text("appearance") == "botanical"
    val background = if (botanical) "#f7f3e8" else "linear-gradient(160deg,#ffe7d0 0%,#ffd1dc 38%,#cfe6ff 100%)"
    val text = if (botanical) "#34432f" else "#3a2e3a"
    return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Letter</title></head><body style="margin:0;padding:32px;background:$background;color:$text;font:20px Georgia,serif"><main style="max-width:720px;margin:auto"><p>Dear ${htmlEscape(message.payload.text("to"))},</p><p style="white-space:pre-wrap">${htmlEscape(message.content)}</p><p>With love,<br>${htmlEscape(message.payload.text("from"))}</p></main></body></html>"""
}

fun exportChat(messages: List<ChatMessage>, owner: String, other: String, format: String): String {
    fun author(m: ChatMessage) = if (m.senderId == owner) "You" else other
    fun body(m: ChatMessage) = if (m.type == "text" || m.type == "letter") m.content else
        "[${m.type}] ${m.content.ifBlank { messageSummary(m) }}\n${m.payload ?: ""}"
    return when (format) {
        "json" -> exportJson.encodeToString(messages)
        "txt" -> messages.joinToString("\n\n") { "[${it.createdAt}] ${author(it)}: ${body(it)}" }
        "html" -> "<!doctype html><html><head><meta charset=\"utf-8\"><title>One on One conversation</title></head><body>" +
            messages.joinToString("") { "<article><p><b>${htmlEscape(author(it))}</b> · ${htmlEscape(it.createdAt)}</p><pre style=\"white-space:pre-wrap\">${htmlEscape(body(it))}</pre></article>" } + "</body></html>"
        else -> error("Unsupported export format.")
    }
}
