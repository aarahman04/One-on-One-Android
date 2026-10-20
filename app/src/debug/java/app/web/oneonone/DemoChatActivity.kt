package app.web.oneonone

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import app.web.oneonone.call.CallLauncher
import app.web.oneonone.data.DevicePreferences
import app.web.oneonone.data.AccountMutationLock
import app.web.oneonone.data.stickers.StickerLibrary
import app.web.oneonone.data.stickers.builtInStickers
import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.chat.ChatDatabase
import app.web.oneonone.data.chat.MessageService
import app.web.oneonone.data.chat.RoomMessageStore
import app.web.oneonone.data.media.AttachmentApi
import app.web.oneonone.data.media.MediaRepository
import app.web.oneonone.data.model.*
import app.web.oneonone.data.realtime.ConnectionState
import app.web.oneonone.data.transport.Transport
import app.web.oneonone.ui.chat.ChatScreen
import app.web.oneonone.data.games.GameService
import app.web.oneonone.ui.games.GameViewModel
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.SpeechViewModel
import app.web.oneonone.data.media.SpeechRepository
import app.web.oneonone.data.media.SpeechApi
import app.web.oneonone.data.media.AndroidNarrationAudio
import app.web.oneonone.data.media.AndroidDeviceSpeech
import app.web.oneonone.ui.chat.FeatureViewModel
import app.web.oneonone.ui.chat.StickerViewModel
import app.web.oneonone.ui.theme.OneOnOneTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.graphics.BitmapFactory
import android.net.Uri
import app.web.oneonone.data.media.MediaGateway
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.util.UUID

/**
 * DEBUG BUILDS ONLY ("1on1 Demo" launcher icon). The real ChatScreen, ViewModels, MessageService and Room store,
 * wired to [DemoTransport]: a scripted "Alex" who types, replies, reacts and can raise an alarm, with link previews
 * produced by the real server unfurler (baked into assets/demo_previews.json). No backend, no sign-in.
 * Send "alarm" to have Alex raise an emergency alarm.
 */
class DemoChatActivity : ComponentActivity() {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeScene by lazy { intent.component?.shortClassName?.takeIf { it.startsWith(".Store") } }
    private val storeDemo get() = storeScene != null
    private val transport by lazy {
        DemoTransport(this, json, scope, storeScene)
    }
    private val service by lazy {
        val db = Room.inMemoryDatabaseBuilder(applicationContext, ChatDatabase::class.java).build()
        MessageService(transport, RoomMessageStore(db, json), DemoSession, json, scope)
    }
    // Nothing here reaches a server: account/media calls fail with the app's normal "no connection" message.
    private val retrofit by lazy {
        Retrofit.Builder().baseUrl("https://demo.invalid/").addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    }
    private val account by lazy { retrofit.create(AccountApi::class.java) }
    private val realMedia by lazy { MediaRepository(applicationContext, retrofit.create(AttachmentApi::class.java), json) }
    private val speech: SpeechViewModel by viewModels { viewModelFactory { initializer {
        SpeechViewModel(SpeechRepository(applicationContext, retrofit.create(SpeechApi::class.java)), AndroidNarrationAudio(applicationContext),
            realMedia, AndroidDeviceSpeech(applicationContext), DevicePreferences(applicationContext), service)
    } } }
    private val launcher = object : CallLauncher {
        override fun start(kind: app.web.oneonone.call.CallKind) {
            runOnUiThread { Toast.makeText(this@DemoChatActivity, "Calls need the real backend", Toast.LENGTH_SHORT).show() }
        }
    }
    private val chat: ChatViewModel by viewModels { viewModelFactory { initializer { ChatViewModel(service, account, launcher, createSavedStateHandle()) } } }
    private val games: GameViewModel by viewModels { viewModelFactory { initializer { GameViewModel(GameService(transport, service)) } } }
    private val features: FeatureViewModel by viewModels { viewModelFactory { initializer {
        FeatureViewModel(service, account, DemoMedia(applicationContext, realMedia),
            DevicePreferences(applicationContext), createSavedStateHandle())
    } } }
    private val stickers: StickerViewModel by viewModels { viewModelFactory { initializer {
        StickerViewModel(applicationContext, service, DemoSession, AccountMutationLock(), StickerLibrary(applicationContext, json), createSavedStateHandle())
    } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val connection = CurrentConnection(id = DemoTransport.CONNECTION, status = "active", myUserId = DemoTransport.ME, isRequester = true,
            otherNickname = if (storeDemo) "Arjun" else "Alex", otherConnectionCode = "",
            otherLastReadAt = null, otherLastDeliveredAt = null, wallpaper = if (storeDemo) "love" else "off", messageStyle = "bubbles")
        setContent {
            OneOnOneTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize()) {
                    ChatScreen(connection, chat, features, stickers, onSettings = {}, onAccount = {}, onRefresh = {}, games = games, speech = speech)
                }
            }
        }
    }
}

private object DemoSession : AccountSession {
    override val signedIn = MutableStateFlow(true)
    override suspend fun signIn(activityContext: Context) {}
    override suspend fun signOut() {}
    override suspend fun accessToken() = "demo"
    override suspend fun refreshToken(rejectedToken: String) = "demo"
    override suspend fun invalidate(rejectedToken: String) {}
}

/**
 * Demo only: an "upload" copies the bytes into the app cache and its "signed URL" is that local file, so photos,
 * stickers and voice notes render and play with no backend. Everything else is the real [MediaRepository].
 */
private class DemoMedia(private val context: Context, real: MediaGateway) : MediaGateway by real {
    private val dir = File(context.cacheDir, "demo-media").apply { mkdirs() }

    private suspend fun store(connection: String, bytes: ByteArray, extension: String): String {
        delay(1_000) // A visible "Sending…" bubble, as on a real network.
        val name = "${UUID.randomUUID()}.$extension"
        File(dir, name).writeBytes(bytes)
        return "$connection/$name"
    }

    override suspend fun uploadSticker(connection: String, file: File): JsonObject = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        buildJsonObject {
            put("path", store(connection, bytes, "webp")); put("mime", "image/webp"); put("size", bytes.size)
            put("width", 512); put("height", 512)
        }
    }

    override suspend fun upload(connection: String, kind: String, uri: Uri, duration: Double?): JsonObject = withContext(Dispatchers.IO) {
        val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        val mime = if (kind == "voice") "audio/mp4" else context.contentResolver.getType(uri) ?: "image/jpeg"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        buildJsonObject {
            put("path", store(connection, bytes, if (kind == "voice") "m4a" else mime.substringAfter('/')))
            put("mime", mime); put("size", bytes.size)
            when (kind) {
                "image" -> { put("width", bounds.outWidth); put("height", bounds.outHeight) }
                "voice" -> put("duration", checkNotNull(duration))
                "file" -> put("name", "attachment")
            }
        }
    }

    override suspend fun signedUrl(connection: String, path: String): String = File(dir, path.substringAfterLast('/')).absolutePath
}

private class DemoTransport(context: Context, private val json: Json, private val scope: CoroutineScope, storeScene: String?) : Transport {
    companion object {
        const val ME = "00000000-0000-4000-8000-000000000001"
        const val ALEX = "00000000-0000-4000-8000-000000000002"
        const val CONNECTION = "00000000-0000-4000-8000-0000000000aa"
    }
    private val previews: Map<String, LinkPreview?> = runCatching {
        json.decodeFromString<Map<String, LinkPreview?>>(context.assets.open("demo_previews.json").bufferedReader().readText())
    }.getOrDefault(emptyMap())
    override val state = MutableStateFlow(ConnectionState.Connected)
    override val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    override val receipts = MutableSharedFlow<ReceiptUpdate>(extraBufferCapacity = 32)
    override val reactions = MutableSharedFlow<ReactionUpdate>(extraBufferCapacity = 32)
    override val ended = MutableSharedFlow<Unit>()
    override val updates = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    override val typing = MutableSharedFlow<TypingUpdate>(extraBufferCapacity = 32)
    override val pins = MutableSharedFlow<PinState>(extraBufferCapacity = 8)
    private val all = linkedMapOf<String, ChatMessage>()
    private var replies = 0

    private fun at(minutesAgo: Long) = Instant.now().minusSeconds(minutesAgo * 60).toString()
    private fun msg(from: String, text: String, minutesAgo: Long, replyTo: String? = null, id: String = UUID.randomUUID().toString()) =
        ChatMessage(id = id, senderId = from, content = text, createdAt = at(minutesAgo), replyTo = replyTo)

    init {
        val first = msg(ALEX, "Hey! Did you see this? https://www.youtube.com/watch?v=dQw4w9WgXcQ", 180, id = "00000000-0000-4000-8000-0000000000b1")
        val seed = if (storeScene == ".StoreAlarmActivity") listOf(
            msg(ALEX, "Maya, can you call me? I need you right now.", 8),
            msg(ME, "I'm here, Arjun. What's going on?", 7),
            msg(ALEX, "Please call as soon as you see this.", 1),
        ) else if (storeScene == ".StoreStickersActivity") listOf(
            msg(ALEX, "Maya, this is me when I see your name.", 8),
            msg(ME, "Arjun, you always make me smile.", 7),
        ) else if (storeScene != null) listOf(
            msg(ALEX, "Hey Maya, made it home. How was your day?", 30),
            msg(ME, "Hey Arjun. A little busy. Your message made it better.", 28),
            msg(ALEX, "Saving a walk and a sunset for us.", 26),
            msg(ME, "Same time tomorrow? I missed you today.", 24),
            msg(ALEX, "Always, Maya. Tell me the little things.", 22),
            msg(ME, "You are my favourite part of the day, Arjun.", 20),
        ) else listOf(
            first,
            msg(ME, "Haha classic. This is on repeat for me today https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", 175),
            msg(ALEX, "Also thinking of getting this https://www.amazon.in/dp/B0CHX1W1XY", 120),
            msg(ME, "Call me on +91 98765 43210 when you're free, or check shirt.online", 110, replyTo = first.id),
            msg(ALEX, "My new profile 👉 https://www.instagram.com/instagram/", 60),
            msg(ALEX, "Played Chess · 12 min", 30).copy(type = "game", payload = buildJsonObject {
                put("game", "chess"); put("inviter", ALEX); put("winner", ME); put("reason", "checkmate"); put("durationSec", 734)
            }),
            msg(ME, "", 25).copy(type = "call", payload = buildJsonObject { put("kind", "audio"); put("outcome", "completed"); put("durationSec", 312) }),
            msg(ME, "Typo-ed message you can still edit or delete (sent 5 min ago)", 5),
        )
        seed.forEach { all[checkNotNull(it.id)] = it }
        val card = when (storeScene) {
            ".StoreLetterActivity" -> msg(ME, "Thank you for turning ordinary days into something I look forward to. Here is to more walks, more stories, and more little moments with you.", 2)
                .copy(type = "letter", payload = buildJsonObject { put("appearance", "dawn"); put("from", "Maya"); put("to", "Arjun") })
            ".StoreAlarmActivity" -> msg(ALEX, "", 0).copy(type = "alarm", payload = buildJsonObject {})
            ".StoreChoicesActivity" -> msg(ALEX, "", 2).copy(type = "thisorthat", payload = buildJsonObject {
                put("optionA", "A sunset walk"); put("optionB", "A cosy movie night"); put("pickSender", "a"); put("pickRecipient", "a")
            })
            ".StoreCountdownActivity" -> msg(ME, "", 2).copy(type = "countdown", payload = buildJsonObject {
                put("label", "Our next adventure"); put("targetIso", Instant.now().plusSeconds(12 * 24 * 60 * 60L).toString())
            })
            else -> null
        }
        card?.let {
            validateFeaturePayload(CONNECTION, it.type, checkNotNull(it.payload))
            all[checkNotNull(it.id)] = it
        }
        if (storeScene == ".StoreStickersActivity") {
            val assets = builtInStickers(context).filter { it.entry.id in setOf("cat-love", "cat-happy", "signal-love") }
            check(assets.size == 3) { "Store sticker fixtures are missing." }
            val dir = File(context.cacheDir, "demo-media").apply { mkdirs() }
            assets.forEachIndexed { index, asset ->
                val file = File(dir, "store-${asset.entry.id}.webp").apply { writeBytes(asset.bytes) }
                val sticker = msg(if (index == 1) ME else ALEX, "", 3L - index).copy(type = "image", payload = buildJsonObject {
                    put("path", "$CONNECTION/${file.name}"); put("mime", "image/webp"); put("size", file.length())
                    put("width", 512); put("height", 512)
                })
                validateFeaturePayload(CONNECTION, sticker.type, sticker.payload)
                all[checkNotNull(sticker.id)] = sticker
            }
        }
    }

    override suspend fun start() {}
    override suspend fun stop() {}
    override suspend fun history(connectionId: String, before: String?, after: String?): List<ChatMessage> =
        if (before == null && after == null) all.values.toList() else emptyList()
    override suspend fun markRead(connectionId: String) {}

    override suspend fun send(message: SendMessage): ChatMessage {
        delay(250)
        val saved = ChatMessage(id = UUID.randomUUID().toString(), senderId = ME, content = message.content, createdAt = Instant.now().toString(),
            type = message.type, payload = message.payload, replyTo = message.replyTo, tempId = message.tempId)
        all[checkNotNull(saved.id)] = saved
        if (message.type == "text") scope.launch { alexResponds(saved) }
        // Alex sends the same photo/sticker back, so both the sent and received layouts can be checked.
        if (message.type == "image") scope.launch {
            delay(1_200)
            val echo = ChatMessage(id = UUID.randomUUID().toString(), senderId = ALEX, content = "", createdAt = Instant.now().toString(),
                type = "image", payload = message.payload)
            all[checkNotNull(echo.id)] = echo; incoming.emit(echo)
        }
        return saved
    }

    private suspend fun alexResponds(to: ChatMessage) {
        delay(700); receipts.emit(ReceiptUpdate(ALEX, lastReadAt = Instant.now().toString(), lastDeliveredAt = Instant.now().toString()))
        if (to.content.trim().equals("alarm", ignoreCase = true)) {
            delay(800)
            val raise = ChatMessage(id = UUID.randomUUID().toString(), senderId = ALEX, content = "", createdAt = Instant.now().toString(), type = "alarm")
            all[checkNotNull(raise.id)] = raise; incoming.emit(raise); return
        }
        typing.emit(TypingUpdate(ALEX, true)); delay(2_200)
        val lines = listOf("Haha nice 😄", "Totally agree", "Sending you this too: https://github.com/aarahman04", "Okay, talk soon!",
            "Read this later https://en.wikipedia.org/wiki/Android_(operating_system)")
        val reply = ChatMessage(id = UUID.randomUUID().toString(), senderId = ALEX, content = lines[replies++ % lines.size],
            createdAt = Instant.now().toString(), replyTo = to.id.takeIf { replies % 3 == 0 })
        all[checkNotNull(reply.id)] = reply
        incoming.emit(reply)
        if (replies % 2 == 0) { delay(600); reactions.emit(ReactionUpdate(checkNotNull(to.id), "❤️", ALEX, "add")) }
    }

    override suspend fun react(messageId: String, emoji: String, remove: Boolean) {
        reactions.emit(ReactionUpdate(messageId, emoji, ME, if (remove) "remove" else "add"))
    }
    override suspend fun edit(messageId: String, content: String): ChatMessage {
        val edited = checkNotNull(all[messageId]).copy(content = content, editedAt = Instant.now().toString())
        all[messageId] = edited; return edited
    }
    override suspend fun delete(messageId: String): ChatMessage {
        val gone = checkNotNull(all[messageId]).copy(content = "", payload = null, reactions = emptyList(), deletedAt = Instant.now().toString())
        all[messageId] = gone; return gone
    }
    override suspend fun pin(messageId: String?) = PinState(messageId, messageId?.let { ME }, messageId?.let { Instant.now().toString() })
    override fun typing(active: Boolean) {}
    override suspend fun linkPreview(url: String): LinkPreview? { delay(400); return previews[url] }
}

