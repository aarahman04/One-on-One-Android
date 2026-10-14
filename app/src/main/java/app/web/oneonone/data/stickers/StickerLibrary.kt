package app.web.oneonone.data.stickers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// shortcut: JSON indexes are capped at 1 MiB, use Room when libraries approach a few thousand stickers.
private const val INDEX_MAX_BYTES = 1_048_576

@Serializable
data class StickerEntry(
    val id: String,
    val createdAt: Long,
    val lastUsedAt: Long = 0,
    val favourite: Boolean = false,
    val title: String = "Your sticker",
    val pack: String? = null,
)

internal data class StickerAsset(val entry: StickerEntry, val bytes: ByteArray)
data class StickerSection(val title: String, val entries: List<StickerEntry>)

fun stickerSections(entries: List<StickerEntry>): List<StickerSection> {
    val newest = entries.sortedWith(compareByDescending<StickerEntry> { it.createdAt }.thenBy { it.id })
    val used = entries.filter { it.lastUsedAt > 0 }
        .sortedWith(compareByDescending<StickerEntry> { it.lastUsedAt }.thenByDescending { it.createdAt }.thenBy { it.id })
    return listOf(
        StickerSection("Favourites", newest.filter { it.favourite }),
        StickerSection("Recent", used.take(16)),
        StickerSection("My stickers", newest.filter { it.pack == null }),
    ) + newest.filter { it.pack != null }.groupBy { checkNotNull(it.pack) }
        .toSortedMap().map { (pack, stickers) -> StickerSection(pack, stickers) }
}

@Singleton
class StickerLibrary internal constructor(
    private val root: File,
    private val json: Json,
    private val builtIns: () -> List<StickerAsset> = { emptyList() },
) {
    @Inject constructor(@ApplicationContext context: Context, json: Json) :
        this(File(context.filesDir, "stickers"), json, { builtInStickers(context) })

    private val mutex = Mutex()
    private val indexJson = Json(json) { encodeDefaults = true }

    private fun directory(owner: String): File {
        require(owner.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) { "Invalid sticker owner." }
        return File(root, owner)
    }

    fun file(owner: String, id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid sticker." }
        return File(directory(owner), "$id.webp")
    }

    private fun read(owner: String): List<StickerEntry> {
        val index = File(directory(owner), "index.json")
        if (!index.exists()) return emptyList()
        check(index.length() <= INDEX_MAX_BYTES) { "Couldn't read your sticker library." }
        val entries = indexJson.decodeFromString<List<StickerEntry>>(index.readText())
        check(entries.map { it.id }.distinct().size == entries.size) { "Couldn't read your sticker library." }
        entries.forEach {
            file(owner, it.id)
            check(it.createdAt >= 0 && it.lastUsedAt >= 0 && it.title.length <= 80) { "Couldn't read your sticker library." }
        }
        return entries.filter { file(owner, it.id).isFile }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Couldn't save your sticker library." }
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            temporary.writeBytes(bytes)
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    private fun write(owner: String, entries: List<StickerEntry>) {
        val bytes = indexJson.encodeToString(entries).toByteArray(Charsets.UTF_8)
        check(bytes.size <= INDEX_MAX_BYTES) { "Your sticker library is full. Delete a few stickers before adding more." }
        atomicWrite(File(directory(owner), "index.json"), bytes)
    }

    suspend fun load(owner: String): List<StickerEntry> = withContext(Dispatchers.IO) { mutex.withLock {
        // An empty existing index is deliberate: deleted built-in stickers must not reappear.
        if (!File(directory(owner), "index.json").exists()) {
            val assets = builtIns()
            assets.forEach {
                require(it.bytes.size in 1..STICKER_MAX_BYTES)
                atomicWrite(file(owner, it.entry.id), it.bytes)
            }
            write(owner, assets.map { it.entry })
        }
        read(owner)
    } }

    suspend fun save(owner: String, bytes: ByteArray, now: Long = System.currentTimeMillis()): List<StickerEntry> =
        withContext(Dispatchers.IO) { mutex.withLock {
            require(bytes.size in 1..STICKER_MAX_BYTES && now >= 0)
            val entries = read(owner)
            val entry = StickerEntry(UUID.randomUUID().toString(), now)
            val target = file(owner, entry.id)
            atomicWrite(target, bytes)
            try { (entries + entry).also { write(owner, it) } }
            catch (failure: Exception) { target.delete(); throw failure }
        } }

    suspend fun favourite(owner: String, id: String): List<StickerEntry> = update(owner, id) { it.copy(favourite = !it.favourite) }
    suspend fun used(owner: String, id: String, now: Long = System.currentTimeMillis()): List<StickerEntry> {
        require(now >= 0)
        return update(owner, id) { it.copy(lastUsedAt = maxOf(it.lastUsedAt, now)) }
    }

    private suspend fun update(owner: String, id: String, change: (StickerEntry) -> StickerEntry): List<StickerEntry> =
        withContext(Dispatchers.IO) { mutex.withLock {
            val entries = read(owner)
            check(entries.any { it.id == id }) { "That sticker was deleted." }
            entries.map { if (it.id == id) change(it) else it }.also { write(owner, it) }
        } }

    suspend fun delete(owner: String, id: String): List<StickerEntry> = withContext(Dispatchers.IO) { mutex.withLock {
        val target = file(owner, id)
        val entries = read(owner).filterNot { it.id == id }
        write(owner, entries)
        check(!target.exists() || target.delete()) { "Couldn't delete that sticker. Try again." }
        entries
    } }

    /** Called inside the shared account-mutation lock, before credentials are removed. */
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        check(!root.exists() || root.deleteRecursively()) { "Couldn't clear your sticker library. Try again." }
    } }
}
