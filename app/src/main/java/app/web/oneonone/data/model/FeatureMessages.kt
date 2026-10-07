package app.web.oneonone.data.model

import kotlinx.serialization.json.*
import java.time.Duration
import java.time.Instant

const val MiB = 1024 * 1024
val ImageMimes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
val VoiceMimes = setOf("audio/webm", "audio/mp4", "audio/ogg", "audio/mpeg")
val FileMimes = setOf("application/pdf", "text/plain", "text/csv", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation")
val Moods = listOf("great", "good", "okay", "down", "struggling")
val ReportCategories = listOf("harassment", "hate", "sexual", "child_safety", "spam", "other")
val FeatureCommands = listOf("letter", "ask", "countdown", "checkin", "thisorthat", "location", "alarm")

fun JsonObject?.text(key: String): String = (this?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject?.number(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull
fun attachmentLimit(kind: String): Int = when (kind) {
    "image" -> 10 * MiB
    "voice" -> 16 * MiB
    "file" -> 25 * MiB
    else -> error("Unsupported attachment kind.")
}
fun attachmentMimes(kind: String): Set<String> = when (kind) {
    "image" -> ImageMimes
    "voice" -> VoiceMimes
    "file" -> FileMimes
    else -> error("Unsupported attachment kind.")
}

/** Mirrors contract validators before a message enters the durable outbox. Backend still validates every send. */
fun validateFeaturePayload(connectionId: String, type: String, payload: JsonObject?) {
    fun field(key: String, max: Int): String = payload.text(key).trim().also {
        require(it.length in 1..max) { "$key must be 1–$max characters." }
    }
    when (type) {
        "text", "alarm" -> Unit // A4 owns alarm acknowledgement semantics; the server enforces them.
        "letter" -> {
            require(payload.text("appearance") in setOf("dawn", "botanical")) { "Choose a letter appearance." }
            field("from", 40); field("to", 40)
        }
        "ask" -> {
            field("question", 300); field("answerA", 500)
            if (payload?.get("answerB") != null && payload["answerB"] != JsonNull) field("answerB", 500)
        }
        "countdown" -> {
            field("label", 100)
            require(runCatching { Instant.parse(payload.text("targetIso")) }.isSuccess) { "Choose a valid countdown time." }
        }
        "checkin" -> { require(payload.text("mood") in Moods) { "Choose a mood." }; field("note", 300) }
        "thisorthat" -> {
            field("optionA", 100); field("optionB", 100)
            require(payload.text("pickSender") in setOf("a", "b")) { "Choose one option." }
            if (payload?.get("pickRecipient") != null && payload["pickRecipient"] != JsonNull)
                require(payload.text("pickRecipient") in setOf("a", "b")) { "Choose one option." }
        }
        "location" -> {
            val lat = payload.number("lat"); val lng = payload.number("lng")
            require(lat != null && lat.isFinite() && lat in -90.0..90.0) { "Invalid latitude." }
            require(lng != null && lng.isFinite() && lng in -180.0..180.0) { "Invalid longitude." }
            if (payload?.containsKey("accuracy") == true && payload["accuracy"] != JsonNull) require(payload.number("accuracy")?.let { it.isFinite() && it >= 0 } == true) { "Invalid location accuracy." }
        }
        "image", "voice", "file" -> {
            require(payload.text("path").startsWith("$connectionId/")) { "Attachment belongs to another conversation." }
            require(payload.text("mime") in attachmentMimes(type)) { "Unsupported attachment type." }
            val size = payload.number("size")
            require(size != null && size.isFinite() && size >= 1 && size <= attachmentLimit(type)) { "Attachment exceeds the size limit." }
            when (type) {
                "image" -> listOf("width", "height").forEach { key ->
                    require((payload?.get(key) as? JsonPrimitive)?.intOrNull in 1..20_000) { "Invalid image dimensions." }
                }
                "voice" -> require(payload.number("duration")?.let { it.isFinite() && it > 0 && it <= 3_600 } == true) { "Invalid voice duration." }
                "file" -> field("name", 255)
            }
        }
        else -> error("Unsupported client message type.")
    }
}

fun featureContent(type: String, payload: JsonObject): String = when (type) {
    "ask" -> payload.text("question")
    "countdown" -> payload.text("label")
    "checkin" -> payload.text("note")
    "thisorthat" -> "${payload.text("optionA")} vs ${payload.text("optionB")}" 
    "location" -> "${payload.number("lat")}, ${payload.number("lng")}" 
    else -> ""
}

fun countdownText(target: String, now: Instant = Instant.now()): String {
    val seconds = Duration.between(now, Instant.parse(target)).seconds
    if (seconds <= 0) return "Now"
    val days = seconds / 86_400; val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60; val remaining = seconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${remaining}s"
        else -> "${minutes}m ${remaining}s"
    }
}

fun messageSummary(message: ChatMessage): String = when (message.type) {
    "image" -> "Photo"
    "voice" -> "Voice note"
    "file" -> message.payload.text("name").ifBlank { "File" }
    "letter" -> "Letter to ${message.payload.text("to")}" 
    "alarm" -> "Emergency alarm"
    "call" -> "Call"
    else -> message.content
}.take(120)
