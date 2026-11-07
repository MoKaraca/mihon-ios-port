package mihon.ios

import eu.kanade.tachiyomi.data.backup.Backup
import eu.kanade.tachiyomi.data.backup.BackupCategory
import eu.kanade.tachiyomi.data.backup.BackupChapter
import eu.kanade.tachiyomi.data.backup.BackupManga
import eu.kanade.tachiyomi.data.backup.IosBackupEngine
import eu.kanade.tachiyomi.data.track.IosTrackManager
import mihon.ios.domain.MihonIosStateStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MihonIosPortTest {

    @Test
    fun testTachibkProtobufBackupRoundTrip() {
        val engine = IosBackupEngine()
        val original = Backup(
            backupCategories = listOf(
                BackupCategory(name = "Webtoons", order = 1L, id = 10L),
            ),
            backupManga = listOf(
                BackupManga(
                    source = 4102938471029384110L,
                    url = "/series/omniscient-readers-viewpoint",
                    title = "Omniscient Reader's Viewpoint",
                    author = "Sing N Song",
                    artist = "Sleepy-C",
                    genre = listOf("Action", "Fantasy", "Webtoon"),
                    status = 1,
                    favorite = true,
                    notes = "Best constellation manhwa",
                    chapters = listOf(
                        BackupChapter(
                            url = "/ch/230",
                            name = "Episode 230",
                            chapterNumber = 230f,
                            read = true,
                            bookmark = true,
                            lastPageRead = 13L,
                        ),
                    ),
                ),
            ),
        )

        val compressedBytes = engine.encodeBackupToBytes(original)
        // Verify GZIP magic header (0x1f, 0x8b)
        assertEquals(0x1f.toByte(), compressedBytes[0])
        assertEquals(0x8b.toByte(), compressedBytes[1])

        val decoded = engine.decodeBackupFromBytes(compressedBytes)
        assertEquals(1, decoded.backupCategories.size)
        assertEquals("Webtoons", decoded.backupCategories.first().name)
        assertEquals(1, decoded.backupManga.size)
        val restoredManga = decoded.backupManga.first()
        assertEquals("Omniscient Reader's Viewpoint", restoredManga.title)
        assertEquals("Best constellation manhwa", restoredManga.notes)
        assertEquals(230f, restoredManga.chapters.first().chapterNumber)
        assertTrue(restoredManga.chapters.first().read)
        assertTrue(restoredManga.chapters.first().bookmark)
    }

    @Test
    fun testAllTwelveTrackersRegisteredAndOAuthCallback() {
        val store = InMemoryPreferenceStore()
        val trackManager = IosTrackManager(store)
        assertEquals(12, trackManager.trackers.size)

        assertFalse(trackManager.isLoggedIn(IosTrackManager.ANILIST_ID))
        val handled = trackManager.handleOAuthCallbackUrl("mihon://anilist-auth#access_token=eyJ_test_token_123&token_type=Bearer")
        assertTrue(handled)
        assertTrue(trackManager.isLoggedIn(IosTrackManager.ANILIST_ID))
    }

    @Test
    fun testStateStoreLibraryChaptersMigrationAndBackupRestore() {
        val store = InMemoryPreferenceStore()
        val stateStore = MihonIosStateStore(store)

        // Category management
        val initialCategories = stateStore.categories.value.size
        stateStore.addCategory("Isekai Manhwa")
        assertEquals(initialCategories + 1, stateStore.categories.value.size)

        // Chapter reading & tracker sync
        stateStore.bindTrackerToManga(
            mangaId = 1L,
            trackerId = IosTrackManager.ANILIST_ID,
            title = "Omniscient Reader's Viewpoint",
            statusId = 1L,
            score = 9.5,
            lastChapterRead = 220.0,
            totalChapters = 230L,
        )
        stateStore.markChapterRead(mangaId = 1L, chapterId = 1230L, read = true, lastPageRead = 13)
        val updatedTracks = stateStore.tracksByManga.value[1L].orEmpty()
        assertEquals(230.0, updatedTracks.first().lastChapterRead)

        // Source migration
        stateStore.migrateMangaSource(fromMangaId = 1L, targetSourceId = 2499283573021220255L)
        assertEquals(2499283573021220255L, stateStore.mangaMap.value[1L]?.sourceId)

        // Full backup export & restore
        val backupBytes = stateStore.exportBackupBytes()
        val summary = stateStore.restoreFromBackupBytes(backupBytes)
        assertTrue(summary.restoredManga >= 4)
        assertTrue(summary.restoredChapters > 0)
    }
}

