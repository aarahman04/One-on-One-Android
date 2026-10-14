package app.web.oneonone.data.stickers

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StickerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val owner = "00000000-0000-4000-8000-000000000001"
    private val other = "00000000-0000-4000-8000-000000000002"

    @Test fun cropFitsWithTransparentPaddingAndClampsPanAndZoom() {
        assertEquals(StickerPlacement(0f, .25f, 1f, .5f), stickerPlacement(1000, 500, StickerCrop()))
        val crop = boundedStickerCrop(1000, 500, StickerCrop(2f, 99f, -99f))
        assertEquals(StickerCrop(2f, .5f, 0f), crop)
        assertEquals(StickerPlacement(0f, 0f, 2f, 1f), stickerPlacement(1000, 500, crop))
        assertEquals(6f, boundedStickerCrop(10, 10, StickerCrop(20f)).zoom, 0f)
        assertEquals(StickerCrop(), boundedStickerCrop(10, 10, StickerCrop(.1f, 4f, 4f)))
        assertTrue(runCatching { boundedStickerCrop(0, 10, StickerCrop()) }.isFailure)
        assertTrue(runCatching { boundedStickerCrop(10, 10, StickerCrop(Float.NaN)) }.isFailure)
    }

    @Test fun pinchKeepsTheFocusPointAndDragCannotExposeTheSquareEdge() {
        val crop = transformStickerCrop(512, 512, StickerCrop(), 2f, 0f, 0f, .2f, -.1f)
        assertEquals(StickerCrop(2f, -.2f, .1f), crop)
        val dragged = transformStickerCrop(512, 512, crop, 1f, 20f, -20f)
        assertEquals(StickerCrop(2f, .5f, -.5f), dragged)
        assertEquals(StickerCrop(), transformStickerCrop(512, 512, crop, .5f, 0f, 0f))
        assertTrue(runCatching { transformStickerCrop(1, 1, StickerCrop(), 0f, 0f, 0f) }.isFailure)
    }

    @Test fun scalingKeepsAspectRatioInside512IncludingTinyAndThinPhotos() {
        assertEquals(512 to 256, stickerDimensions(4000, 2000))
        assertEquals(256 to 512, stickerDimensions(2000, 4000))
        assertEquals(512 to 512, stickerDimensions(1, 1))
        assertEquals(1 to 512, stickerDimensions(1, 20_000))
        assertEquals(512 to 1, stickerDimensions(20_000, 1))
        assertTrue(runCatching { stickerDimensions(-1, 100) }.isFailure)
    }

    @Test fun webPSizeCapRetriesWithLowerQualityAndStopsAt80() {
        val qualities = mutableListOf<Int>()
        val bytes = encodeStickerWebP { quality ->
            qualities += quality
            ByteArray(if (quality > 90) STICKER_MAX_BYTES + 1 else STICKER_MAX_BYTES)
        }
        assertEquals(listOf(100, 95, 90), qualities); assertEquals(STICKER_MAX_BYTES, bytes.size)
        qualities.clear()
        assertTrue(runCatching { encodeStickerWebP { qualities += it; ByteArray(STICKER_MAX_BYTES + 1) } }.isFailure)
        assertEquals(listOf(100, 95, 90, 85, 80), qualities)
        assertEquals(1, encodeStickerWebP { if (it == 80) byteArrayOf(1) else ByteArray(STICKER_MAX_BYTES + 1) }.size)
        assertTrue(runCatching { encodeStickerWebP { byteArrayOf() } }.isFailure)
    }

    @Test fun maskPreservesSoftEdgesButClearsBackgroundAndInvalidConfidence() {
        assertEquals(0, foregroundAlpha(1f)); assertEquals(255, foregroundAlpha(0f))
        assertEquals(128, foregroundAlpha(.5f))
        assertEquals(0, foregroundAlpha(Float.NaN))
    }

    @Test fun favouritesRecentAndMyStickersHaveStableOrderingAndRecentIsCappedAt16() {
        val entries = (1..20).map { StickerEntry("id-$it", it.toLong(), it.toLong(), favourite = it <= 2) } +
            StickerEntry("pack", 30, 30, favourite = true, pack = "Little signals")
        val sections = stickerSections(entries).associate { it.title to it.entries.map { entry -> entry.id } }
        assertEquals(listOf("pack", "id-2", "id-1"), sections["Favourites"])
        assertEquals(listOf("pack") + (20 downTo 6).map { "id-$it" }, sections["Recent"])
        assertEquals((20 downTo 1).map { "id-$it" }, sections["My stickers"])
        assertEquals(listOf("pack"), sections["Little signals"])
        val tied = listOf(StickerEntry("b", 2, 5), StickerEntry("a", 2, 5), StickerEntry("c", 1, 5))
        assertEquals(listOf("a", "b", "c"), stickerSections(tied).first { it.title == "Recent" }.entries.map { it.id })
    }

    @Test fun indexAndFilesSurviveReopeningArePerAccountAndClearTogether() = runTest {
        val root = temporary.newFolder()
        val library = StickerLibrary(root, Json)
        library.load(owner)
        val entry = library.save(owner, byteArrayOf(1, 2), 100).single()
        library.favourite(owner, entry.id); library.used(owner, entry.id, 200)
        val reopened = StickerLibrary(root, Json)
        assertEquals(entry.copy(favourite = true, lastUsedAt = 200), reopened.load(owner).single())
        assertEquals(emptyList<StickerEntry>(), reopened.load(other))
        val otherEntry = reopened.save(other, byteArrayOf(3), 300).single()
        reopened.delete(owner, entry.id)
        assertFalse(library.file(owner, entry.id).exists())
        assertTrue(library.file(other, otherEntry.id).exists())
        reopened.clear()
        assertFalse(root.exists())
    }

    @Test fun deletedBuiltInsAreNeverReseeded() = runTest {
        var seeds = 0
        val library = StickerLibrary(temporary.newFolder(), Json) {
            seeds++
            listOf(StickerAsset(StickerEntry("signal-love", 0, pack = "Little signals"), byteArrayOf(1)))
        }
        library.load(owner); library.delete(owner, "signal-love")
        assertTrue(library.load(owner).isEmpty()); assertEquals(1, seeds)
    }

    @Test fun unsafePathsAndCorruptIndexAreRejectedWithoutOverwritingData() = runTest {
        val root = temporary.newFolder()
        val library = StickerLibrary(root, Json)
        assertTrue(runCatching { library.file("../escape", "id") }.isFailure)
        assertTrue(runCatching { library.file(owner, "../escape") }.isFailure)
        library.load(owner)
        val index = File(File(root, owner), "index.json")
        index.writeText("corrupt")
        assertTrue(runCatching { library.load(owner) }.isFailure)
        assertEquals("corrupt", index.readText())
        assertTrue(runCatching { library.save(other, ByteArray(STICKER_MAX_BYTES + 1)) }.isFailure)
    }
}
