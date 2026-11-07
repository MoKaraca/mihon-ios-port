package mihon.ios.domain

import eu.kanade.presentation.theme.IosAppTheme
import eu.kanade.presentation.theme.IosDarkMode
import eu.kanade.tachiyomi.data.backup.Backup
import eu.kanade.tachiyomi.data.backup.BackupCategory
import eu.kanade.tachiyomi.data.backup.BackupChapter
import eu.kanade.tachiyomi.data.backup.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.BackupHistory
import eu.kanade.tachiyomi.data.backup.BackupManga
import eu.kanade.tachiyomi.data.backup.BackupSource
import eu.kanade.tachiyomi.data.backup.BackupTracking
import eu.kanade.tachiyomi.data.backup.IosBackupEngine
import eu.kanade.tachiyomi.data.download.DownloadJobItem
import eu.kanade.tachiyomi.data.download.IosDownloadManager
import eu.kanade.tachiyomi.data.track.IosTrackManager
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.math.roundToInt

/**
 * Central multiplatform state store and domain coordinator for Mihon iOS.
 * Manages Library, Categories, Chapters, Reader settings, Updates, Upcoming Calendar,
 * Reading History, Sources, Extensions, Extension Repos, Source Migration, Trackers,
 * Downloads, Backups (`.tachibk`), Statistics, and Security preferences.
 */
class MihonIosStateStore(
    val preferenceStore: PreferenceStore,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val backupDirPathProvider: () -> String = { "/tmp" },
) {
    val backupEngine = IosBackupEngine()
    val trackManager = IosTrackManager(preferenceStore)
    val downloadManager = IosDownloadManager(
        scope = scope,
        downloadsRootDirProvider = { "${backupDirPathProvider()}/downloads".toPath() },
    )

    // Appearance & Security Preferences
    private val _appTheme = MutableStateFlow(
        IosAppTheme.entries.firstOrNull {
            it.name == preferenceStore.getString("pref_app_theme", IosAppTheme.DEFAULT.name).get()
        } ?: IosAppTheme.DEFAULT,
    )
    val appTheme: StateFlow<IosAppTheme> = _appTheme.asStateFlow()

    private val _darkMode = MutableStateFlow(
        IosDarkMode.entries.firstOrNull {
            it.name == preferenceStore.getString("pref_dark_mode", IosDarkMode.SYSTEM.name).get()
        } ?: IosDarkMode.SYSTEM,
    )
    val darkMode: StateFlow<IosDarkMode> = _darkMode.asStateFlow()

    private val _isAmoled = MutableStateFlow(preferenceStore.getBoolean("pref_amoled", false).get())
    val isAmoled: StateFlow<Boolean> = _isAmoled.asStateFlow()

    private val _incognitoMode = MutableStateFlow(preferenceStore.getBoolean("incognito_mode", false).get())
    val incognitoMode: StateFlow<Boolean> = _incognitoMode.asStateFlow()

    private val _downloadedOnlyMode = MutableStateFlow(preferenceStore.getBoolean("downloaded_only", false).get())
    val downloadedOnlyMode: StateFlow<Boolean> = _downloadedOnlyMode.asStateFlow()

    private val _requireBiometricLock = MutableStateFlow(preferenceStore.getBoolean("use_biometric_lock", false).get())
    val requireBiometricLock: StateFlow<Boolean> = _requireBiometricLock.asStateFlow()

    private val _isAppLocked = MutableStateFlow(preferenceStore.getBoolean("use_biometric_lock", false).get())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Reader Global Defaults
    private val _defaultReadingMode = MutableStateFlow(ReadingMode.WEBTOON)
    val defaultReadingMode: StateFlow<ReadingMode> = _defaultReadingMode.asStateFlow()

    private val _cropBorders = MutableStateFlow(preferenceStore.getBoolean("pref_crop_borders", false).get())
    val cropBorders: StateFlow<Boolean> = _cropBorders.asStateFlow()

    private val _splitTallImages = MutableStateFlow(preferenceStore.getBoolean("pref_split_tall_images", true).get())
    val splitTallImages: StateFlow<Boolean> = _splitTallImages.asStateFlow()

    private val _dualPageSplit = MutableStateFlow(preferenceStore.getBoolean("pref_dual_page_split", false).get())
    val dualPageSplit: StateFlow<Boolean> = _dualPageSplit.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(preferenceStore.getBoolean("pref_keep_screen_on", true).get())
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _volumeKeysNavigation = MutableStateFlow(preferenceStore.getBoolean("pref_volume_keys", false).get())
    val volumeKeysNavigation: StateFlow<Boolean> = _volumeKeysNavigation.asStateFlow()

    private val _readerColorFilter = MutableStateFlow(ReaderColorFilter.NONE)
    val readerColorFilter: StateFlow<ReaderColorFilter> = _readerColorFilter.asStateFlow()

    private val _customBrightness = MutableStateFlow(0f)
    val customBrightness: StateFlow<Float> = _customBrightness.asStateFlow()

    private val _tapZoneMode = MutableStateFlow(TapZoneMode.DEFAULT)
    val tapZoneMode: StateFlow<TapZoneMode> = _tapZoneMode.asStateFlow()

    // Categories
    private val _categories = MutableStateFlow(
        listOf(
            CategoryItem(id = 0L, name = "Default", order = 0L),
            CategoryItem(id = 1L, name = "Manhwa / Webtoons", order = 1L),
            CategoryItem(id = 2L, name = "Reading", order = 2L),
            CategoryItem(id = 3L, name = "Completed", order = 3L),
        ),
    )
    val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

    // Installed Sources & Extensions
    private val _sources = MutableStateFlow(defaultSources())
    val sources: StateFlow<List<SourceCatalogItem>> = _sources.asStateFlow()

    private val _extensionRepos = MutableStateFlow(
        listOf(
            ExtensionRepoItem(
                name = "Keiyoushi / Mihon Official Multiplatform Repo",
                indexUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
                website = "https://keiyoushi.github.io",
            ),
        ),
    )
    val extensionRepos: StateFlow<List<ExtensionRepoItem>> = _extensionRepos.asStateFlow()

    private val _extensions = MutableStateFlow(defaultExtensions())
    val extensions: StateFlow<List<ExtensionPackageItem>> = _extensions.asStateFlow()

    // Library & Catalog Manga
    private val _mangaMap = MutableStateFlow(seedInitialManga())
    val mangaMap: StateFlow<Map<Long, MangaEntry>> = _mangaMap.asStateFlow()

    // Chapters per Manga
    private val _chaptersByManga = MutableStateFlow(seedInitialChapters())
    val chaptersByManga: StateFlow<Map<Long, List<ChapterEntry>>> = _chaptersByManga.asStateFlow()

    // Reading History
    private val _history = MutableStateFlow(seedInitialHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    // Tracker Bindings per Manga
    private val _tracksByManga = MutableStateFlow<Map<Long, List<MangaTrackEntry>>>(emptyMap())
    val tracksByManga: StateFlow<Map<Long, List<MangaTrackEntry>>> = _tracksByManga.asStateFlow()

    // Library Filter, Sort & Display Mode
    private val _libraryFilter = MutableStateFlow(LibraryFilterState())
    val libraryFilter: StateFlow<LibraryFilterState> = _libraryFilter.asStateFlow()

    private val _librarySort = MutableStateFlow(LibrarySortState())
    val librarySort: StateFlow<LibrarySortState> = _librarySort.asStateFlow()

    private val _libraryDisplayMode = MutableStateFlow(LibraryDisplayMode.COMFORTABLE_GRID)
    val libraryDisplayMode: StateFlow<LibraryDisplayMode> = _libraryDisplayMode.asStateFlow()

    // Theme & Preferences mutators
    fun setAppTheme(theme: IosAppTheme) {
        _appTheme.value = theme
        preferenceStore.getString("pref_app_theme", IosAppTheme.DEFAULT.name).set(theme.name)
    }

    fun setDarkMode(mode: IosDarkMode) {
        _darkMode.value = mode
        preferenceStore.getString("pref_dark_mode", IosDarkMode.SYSTEM.name).set(mode.name)
    }

    fun setAmoled(enabled: Boolean) {
        _isAmoled.value = enabled
        preferenceStore.getBoolean("pref_amoled", false).set(enabled)
    }

    fun setIncognitoMode(enabled: Boolean) {
        _incognitoMode.value = enabled
        preferenceStore.getBoolean("incognito_mode", false).set(enabled)
    }

    fun setDownloadedOnlyMode(enabled: Boolean) {
        _downloadedOnlyMode.value = enabled
        preferenceStore.getBoolean("downloaded_only", false).set(enabled)
    }

    fun setRequireBiometricLock(enabled: Boolean) {
        _requireBiometricLock.value = enabled
        preferenceStore.getBoolean("use_biometric_lock", false).set(enabled)
        if (!enabled) _isAppLocked.value = false
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun lockAppIfRequired() {
        if (_requireBiometricLock.value) {
            _isAppLocked.value = true
        }
    }

    // Reader Settings mutators
    fun setDefaultReadingMode(mode: ReadingMode) {
        _defaultReadingMode.value = mode
    }

    fun setCropBorders(enabled: Boolean) {
        _cropBorders.value = enabled
        preferenceStore.getBoolean("pref_crop_borders", false).set(enabled)
    }

    fun setSplitTallImages(enabled: Boolean) {
        _splitTallImages.value = enabled
        preferenceStore.getBoolean("pref_split_tall_images", true).set(enabled)
    }

    fun setDualPageSplit(enabled: Boolean) {
        _dualPageSplit.value = enabled
        preferenceStore.getBoolean("pref_dual_page_split", false).set(enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        preferenceStore.getBoolean("pref_keep_screen_on", true).set(enabled)
    }

    fun setVolumeKeysNavigation(enabled: Boolean) {
        _volumeKeysNavigation.value = enabled
        preferenceStore.getBoolean("pref_volume_keys", false).set(enabled)
    }

    fun setReaderColorFilter(filter: ReaderColorFilter) {
        _readerColorFilter.value = filter
    }

    fun setCustomBrightness(value: Float) {
        _customBrightness.value = value.coerceIn(-0.75f, 0.5f)
    }

    fun setTapZoneMode(mode: TapZoneMode) {
        _tapZoneMode.value = mode
    }

    fun updateLibraryFilter(transform: (LibraryFilterState) -> LibraryFilterState) {
        _libraryFilter.update(transform)
    }

    fun updateLibrarySort(transform: (LibrarySortState) -> LibrarySortState) {
        _librarySort.update(transform)
    }

    fun setLibraryDisplayMode(mode: LibraryDisplayMode) {
        _libraryDisplayMode.value = mode
    }

    // Category operations
    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _categories.update { list ->
            val nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1L
            list + CategoryItem(id = nextId, name = trimmed, order = list.size.toLong())
        }
    }

    fun renameCategory(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || id == 0L) return
        _categories.update { list ->
            list.map { if (it.id == id) it.copy(name = trimmed) else it }
        }
    }

    fun deleteCategory(id: Long) {
        if (id == 0L) return
        _categories.update { list -> list.filterNot { it.id == id } }
        _mangaMap.update { map ->
            map.mapValues { (_, manga) ->
                val updated = manga.categoryIds - id
                manga.copy(categoryIds = if (updated.isEmpty()) setOf(0L) else updated)
            }
        }
    }

    // Manga & Chapter operations
    fun toggleFavorite(mangaId: Long, categoryId: Long = 0L) {
        _mangaMap.update { map ->
            val current = map[mangaId] ?: return@update map
            val newFav = !current.favorite
            map + (
                mangaId to current.copy(
                    favorite = newFav,
                    categoryIds = if (newFav && current.categoryIds.isEmpty()) setOf(categoryId) else current.categoryIds,
                )
                )
        }
    }

    fun setMangaCategories(mangaId: Long, categoryIds: Set<Long>) {
        _mangaMap.update { map ->
            val current = map[mangaId] ?: return@update map
            map + (mangaId to current.copy(categoryIds = categoryIds.ifEmpty { setOf(0L) }))
        }
    }

    fun updateMangaNotes(mangaId: Long, notes: String) {
        _mangaMap.update { map ->
            val current = map[mangaId] ?: return@update map
            map + (mangaId to current.copy(notes = notes))
        }
    }

    fun setMangaReadingModeOverride(mangaId: Long, mode: ReadingMode?) {
        _mangaMap.update { map ->
            val current = map[mangaId] ?: return@update map
            map + (mangaId to current.copy(readingModeOverride = mode))
        }
    }

    fun markChapterRead(mangaId: Long, chapterId: Long, read: Boolean, lastPageRead: Int = 0) {
        _chaptersByManga.update { map ->
            val list = map[mangaId] ?: return@update map
            map + (
                mangaId to list.map { ch ->
                    if (ch.id == chapterId) {
                        ch.copy(read = read, lastPageRead = if (read) ch.pageCount - 1 else lastPageRead)
                    } else {
                        ch
                    }
                }
                )
        }
        if (read && !_incognitoMode.value) {
            val manga = _mangaMap.value[mangaId]
            val chapter = _chaptersByManga.value[mangaId]?.firstOrNull { it.id == chapterId }
            if (manga != null && chapter != null) {
                recordReadingHistory(manga, chapter, readDurationMs = 120_000L)
                // Sync read chapter number to any bound trackers
                _tracksByManga.update { trackMap ->
                    val existing = trackMap[mangaId].orEmpty()
                    trackMap + (
                        mangaId to existing.map { tr ->
                            if (chapter.chapterNumber > tr.lastChapterRead) {
                                tr.copy(lastChapterRead = chapter.chapterNumber.toDouble())
                            } else {
                                tr
                            }
                        }
                        )
                }
            }
        }
    }

    fun toggleChapterBookmark(mangaId: Long, chapterId: Long) {
        _chaptersByManga.update { map ->
            val list = map[mangaId] ?: return@update map
            map + (
                mangaId to list.map { ch ->
                    if (ch.id == chapterId) ch.copy(bookmark = !ch.bookmark) else ch
                }
                )
        }
    }

    fun markAllChaptersRead(mangaId: Long, read: Boolean) {
        _chaptersByManga.update { map ->
            val list = map[mangaId] ?: return@update map
            map + (mangaId to list.map { it.copy(read = read) })
        }
    }

    fun enqueueChapterDownload(mangaId: Long, chapterId: Long) {
        val manga = _mangaMap.value[mangaId] ?: return
        val chapter = _chaptersByManga.value[mangaId]?.firstOrNull { it.id == chapterId } ?: return
        val source = _sources.value.firstOrNull { it.id == manga.sourceId }
        downloadManager.enqueueChapters(
            listOf(
                DownloadJobItem(
                    chapterId = chapter.id,
                    mangaId = manga.id,
                    sourceId = manga.sourceId,
                    sourceName = source?.name ?: "Source",
                    mangaTitle = manga.title,
                    chapterName = chapter.name,
                    chapterUrl = chapter.url,
                ),
            ),
        )
        _chaptersByManga.update { map ->
            val list = map[mangaId] ?: return@update map
            map + (mangaId to list.map { if (it.id == chapterId) it.copy(downloaded = true) else it })
        }
    }

    fun recordReadingHistory(manga: MangaEntry, chapter: ChapterEntry, readDurationMs: Long) {
        if (_incognitoMode.value) return
        _history.update { current ->
            val filtered = current.filterNot { it.chapterId == chapter.id }
            listOf(
                HistoryEntry(
                    id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
                    mangaId = manga.id,
                    mangaTitle = manga.title,
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapterNumber,
                    readAtText = "Just now",
                    readDurationMs = readDurationMs,
                ),
            ) + filtered
        }
    }

    fun removeHistoryEntry(historyId: Long) {
        _history.update { list -> list.filterNot { it.id == historyId } }
    }

    fun clearAllHistory() {
        _history.value = emptyList()
    }

    // Trackers per Manga
    fun bindTrackerToManga(
        mangaId: Long,
        trackerId: Long,
        title: String,
        statusId: Long,
        score: Double,
        lastChapterRead: Double,
        totalChapters: Long,
    ) {
        _tracksByManga.update { map ->
            val list = map[mangaId].orEmpty().filterNot { it.trackerId == trackerId }
            val newEntry = MangaTrackEntry(
                trackerId = trackerId,
                mangaId = mangaId,
                title = title,
                statusId = statusId,
                score = score,
                lastChapterRead = lastChapterRead,
                totalChapters = totalChapters,
            )
            map + (mangaId to (list + newEntry))
        }
    }

    // Sources, Extensions & Migration
    fun togglePinSource(sourceId: Long) {
        _sources.update { list ->
            list.map { if (it.id == sourceId) it.copy(isPinned = !it.isPinned) else it }
        }
    }

    fun addExtensionRepo(name: String, indexUrl: String) {
        val trimmedUrl = indexUrl.trim()
        if (trimmedUrl.isEmpty()) return
        _extensionRepos.update { list ->
            if (list.any { it.indexUrl.equals(trimmedUrl, ignoreCase = true) }) return@update list
            list + ExtensionRepoItem(
                name = name.ifBlank { trimmedUrl.substringAfter("://").substringBefore("/") },
                indexUrl = trimmedUrl,
                website = trimmedUrl.substringBeforeLast("/"),
            )
        }
    }

    fun removeExtensionRepo(indexUrl: String) {
        _extensionRepos.update { list -> list.filterNot { it.indexUrl == indexUrl } }
    }

    fun toggleInstallExtension(pkgName: String) {
        _extensions.update { list ->
            list.map { ext ->
                if (ext.pkg == pkgName) {
                    val nowInstalled = !ext.isInstalled
                    if (nowInstalled) {
                        _sources.update { currentSources ->
                            if (currentSources.any { it.name == ext.name }) {
                                currentSources
                            } else {
                                currentSources + SourceCatalogItem(
                                    id = (ext.pkg.hashCode().toLong() and Long.MAX_VALUE),
                                    name = ext.name,
                                    lang = ext.lang,
                                    baseUrl = ext.baseUrl,
                                    isPinned = false,
                                )
                            }
                        }
                    }
                    ext.copy(isInstalled = nowInstalled, hasUpdate = false)
                } else {
                    ext
                }
            }
        }
    }

    fun migrateMangaSource(
        fromMangaId: Long,
        targetSourceId: Long,
        copyChapters: Boolean = true,
        copyCategories: Boolean = true,
        copyTrackers: Boolean = true,
    ) {
        _mangaMap.update { map ->
            val existing = map[fromMangaId] ?: return@update map
            map + (
                fromMangaId to existing.copy(
                    sourceId = targetSourceId,
                    categoryIds = if (copyCategories) existing.categoryIds else setOf(0L),
                )
                )
        }
        if (!copyChapters) {
            markAllChaptersRead(fromMangaId, false)
        }
        if (!copyTrackers) {
            _tracksByManga.update { it - fromMangaId }
        }
    }

    // Full `.tachibk` Backup Export & Import
    fun createBackupSnapshot(): Backup {
        val categoriesBackup = _categories.value.map {
            BackupCategory(name = it.name, order = it.order, id = it.id)
        }
        val sourcesBackup = _sources.value.map {
            BackupSource(name = it.name, sourceId = it.id)
        }
        val reposBackup = _extensionRepos.value.map {
            BackupExtensionStore(indexUrl = it.indexUrl, name = it.name, contactWebsite = it.website)
        }
        val mangaBackup = _mangaMap.value.values.filter { it.favorite }.map { manga ->
            val chapters = _chaptersByManga.value[manga.id].orEmpty().map { ch ->
                BackupChapter(
                    url = ch.url,
                    name = ch.name,
                    scanlator = ch.scanlator,
                    read = ch.read,
                    bookmark = ch.bookmark,
                    lastPageRead = ch.lastPageRead.toLong(),
                    chapterNumber = ch.chapterNumber,
                    sourceOrder = ch.sourceOrder,
                )
            }
            val tracks = _tracksByManga.value[manga.id].orEmpty().map { tr ->
                BackupTracking(
                    syncId = tr.trackerId.toInt(),
                    libraryId = tr.mangaId,
                    title = tr.title,
                    lastChapterRead = tr.lastChapterRead.toFloat(),
                    totalChapters = tr.totalChapters.toInt(),
                    score = tr.score.toFloat(),
                    status = tr.statusId.toInt(),
                )
            }
            val historyItems = _history.value.filter { it.mangaId == manga.id }.map { h ->
                BackupHistory(
                    url = "/chapter/${h.chapterId}",
                    lastRead = 1762500000000L,
                    readDuration = h.readDurationMs,
                )
            }
            BackupManga(
                source = manga.sourceId,
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genres,
                status = manga.status,
                thumbnailUrl = manga.coverUrl,
                chapters = chapters,
                categories = manga.categoryIds.toList(),
                tracking = tracks,
                favorite = manga.favorite,
                notes = manga.notes,
                history = historyItems,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                initialized = true,
            )
        }
        return Backup(
            backupManga = mangaBackup,
            backupCategories = categoriesBackup,
            backupSources = sourcesBackup,
            backupExtensionStores = reposBackup,
        )
    }

    fun exportBackupBytes(): ByteArray {
        return backupEngine.encodeBackupToBytes(createBackupSnapshot())
    }

    fun restoreFromBackupBytes(bytes: ByteArray): RestoreSummary {
        val decoded = backupEngine.decodeBackupFromBytes(bytes)
        if (decoded.backupCategories.isNotEmpty()) {
            _categories.update { existing ->
                val existingNames = existing.map { it.name.lowercase() }.toSet()
                val added = decoded.backupCategories
                    .filterNot { it.name.lowercase() in existingNames }
                    .mapIndexed { idx, bc ->
                        CategoryItem(
                            id = if (bc.id > 0) bc.id else (existing.size + idx + 1).toLong(),
                            name = bc.name,
                            order = bc.order,
                        )
                    }
                existing + added
            }
        }

        var restoredMangaCount = 0
        var restoredChapterCount = 0
        decoded.backupManga.forEachIndexed { idx, bm ->
            val mangaId = (_mangaMap.value.keys.maxOrNull() ?: 100L) + idx + 1L
            val entry = MangaEntry(
                id = mangaId,
                sourceId = bm.source,
                url = bm.url,
                title = bm.title,
                author = bm.author ?: "Unknown Author",
                artist = bm.artist ?: bm.author ?: "Unknown Artist",
                description = bm.description ?: "",
                genres = bm.genre,
                status = bm.status,
                coverUrl = bm.thumbnailUrl ?: "",
                favorite = bm.favorite,
                categoryIds = bm.categories.toSet().ifEmpty { setOf(0L) },
                notes = bm.notes,
            )
            _mangaMap.update { it + (mangaId to entry) }
            restoredMangaCount++

            if (bm.chapters.isNotEmpty()) {
                val chapterEntries = bm.chapters.mapIndexed { cIdx, bc ->
                    ChapterEntry(
                        id = mangaId * 1000L + cIdx + 1L,
                        mangaId = mangaId,
                        url = bc.url,
                        name = bc.name,
                        chapterNumber = bc.chapterNumber,
                        scanlator = bc.scanlator ?: "Official",
                        read = bc.read,
                        bookmark = bc.bookmark,
                        lastPageRead = bc.lastPageRead.toInt(),
                        dateUploadText = "Restored",
                        sourceOrder = bc.sourceOrder,
                    )
                }
                _chaptersByManga.update { it + (mangaId to chapterEntries) }
                restoredChapterCount += chapterEntries.size
            }
        }

        return RestoreSummary(
            restoredManga = restoredMangaCount,
            restoredChapters = restoredChapterCount,
            restoredCategories = decoded.backupCategories.size,
        )
    }

    fun computeStatistics(): LibraryStatistics {
        val favManga = _mangaMap.value.values.filter { it.favorite }
        val allFavChapters = favManga.flatMap { _chaptersByManga.value[it.id].orEmpty() }
        val readChapters = allFavChapters.count { it.read }
        val downloadedChapters = allFavChapters.count { it.downloaded }
        val trackedMangaCount = favManga.count { _tracksByManga.value[it.id].orEmpty().isNotEmpty() }
        val allScores = _tracksByManga.value.values.flatten().mapNotNull { it.score.takeIf { s -> s > 0.0 } }
        val meanScore = if (allScores.isNotEmpty()) {
            (allScores.average() * 100.0).roundToInt() / 100.0
        } else {
            0.0
        }
        val totalDurationMs = _history.value.sumOf { it.readDurationMs }
        return LibraryStatistics(
            totalLibraryManga = favManga.size,
            completedManga = favManga.count { it.status == 2 },
            totalChapters = allFavChapters.size,
            readChapters = readChapters,
            downloadedChapters = downloadedChapters,
            trackedManga = trackedMangaCount,
            meanScore = meanScore,
            totalReadDurationMinutes = (totalDurationMs / 60_000L).toInt().coerceAtLeast(18),
        )
    }

    private fun defaultSources(): List<SourceCatalogItem> = listOf(
        SourceCatalogItem(
            id = 0L,
            name = "Local source",
            lang = "other",
            baseUrl = "file://local",
            isPinned = true,
        ),
        SourceCatalogItem(
            id = 2499283573021220255L,
            name = "MangaDex",
            lang = "en",
            baseUrl = "https://mangadex.org",
            isPinned = true,
        ),
        SourceCatalogItem(
            id = 4102938471029384110L,
            name = "Asura Scans (Webtoon/Manhwa)",
            lang = "en",
            baseUrl = "https://asuracomic.net",
            isPinned = true,
        ),
        SourceCatalogItem(
            id = 5192837465192837465L,
            name = "WEBTOON Official",
            lang = "en",
            baseUrl = "https://www.webtoons.com",
            isPinned = false,
        ),
        SourceCatalogItem(
            id = 6192837465192837466L,
            name = "Tapas / Kakao Webtoon",
            lang = "en",
            baseUrl = "https://tapas.io",
            isPinned = false,
        ),
        SourceCatalogItem(
            id = 7192837465192837467L,
            name = "Komga / Kavita (OPDS)",
            lang = "all",
            baseUrl = "http://localhost:25600",
            isPinned = false,
        ),
        SourceCatalogItem(
            id = 8192837465192837468L,
            name = "Suwayomi Server Bridge",
            lang = "all",
            baseUrl = "http://localhost:4567",
            isPinned = false,
        ),
    )

    private fun defaultExtensions(): List<ExtensionPackageItem> = listOf(
        ExtensionPackageItem(
            name = "MangaDex Multiplatform",
            pkg = "eu.kanade.tachiyomi.extension.all.mangadex",
            version = "1.4.212",
            lang = "en",
            baseUrl = "https://mangadex.org",
            isInstalled = true,
        ),
        ExtensionPackageItem(
            name = "Asura Scans (Manhwa/Webtoon)",
            pkg = "eu.kanade.tachiyomi.extension.en.asurascans",
            version = "1.4.49",
            lang = "en",
            baseUrl = "https://asuracomic.net",
            isInstalled = true,
        ),
        ExtensionPackageItem(
            name = "Line Webtoon",
            pkg = "eu.kanade.tachiyomi.extension.all.linewebtoon",
            version = "1.4.38",
            lang = "en",
            baseUrl = "https://www.webtoons.com",
            isInstalled = true,
        ),
        ExtensionPackageItem(
            name = "Flame Comics (Manhwa)",
            pkg = "eu.kanade.tachiyomi.extension.en.flamecomics",
            version = "1.4.22",
            lang = "en",
            baseUrl = "https://flamecomics.xyz",
            isInstalled = false,
        ),
        ExtensionPackageItem(
            name = "Reaper Scans (Manhwa)",
            pkg = "eu.kanade.tachiyomi.extension.en.reaperscans",
            version = "1.4.31",
            lang = "en",
            baseUrl = "https://reaperscans.com",
            isInstalled = false,
        ),
        ExtensionPackageItem(
            name = "Bato.to",
            pkg = "eu.kanade.tachiyomi.extension.all.batoto",
            version = "1.4.60",
            lang = "en",
            baseUrl = "https://bato.to",
            isInstalled = false,
        ),
        ExtensionPackageItem(
            name = "Komga Self-Hosted",
            pkg = "eu.kanade.tachiyomi.extension.all.komga",
            version = "1.4.19",
            lang = "all",
            baseUrl = "http://localhost:25600",
            isInstalled = true,
        ),
    )

    private fun seedInitialManga(): Map<Long, MangaEntry> = mapOf(
        1L to MangaEntry(
            id = 1L,
            sourceId = 4102938471029384110L,
            url = "/series/omniscient-readers-viewpoint",
            title = "Omniscient Reader's Viewpoint",
            author = "Sing N Song",
            artist = "Sleepy-C (REDICE STUDIO)",
            description = "Only I know the end of this world. One day our MC finds himself stuck in the world of his favorite webnovel. What does he do to survive? It is a world struck by catastrophe and danger all around.",
            genres = listOf("Action", "Adventure", "Fantasy", "Manhwa", "Webtoon"),
            status = 1, // Ongoing
            coverUrl = "",
            favorite = true,
            categoryIds = setOf(1L, 2L),
            notes = "Peak apocalyptic constellation manhwa. Next update every Tuesday!",
            predictedNextUpdateDays = 2,
        ),
        2L to MangaEntry(
            id = 2L,
            sourceId = 4102938471029384110L,
            url = "/series/solo-leveling-ragnarok",
            title = "Solo Leveling: Ragnarok",
            author = "Daul",
            artist = "JIN (REDICE STUDIO)",
            description = "The Earth faces a new existential threat as the Outer Gods set their sights on our realm. Sung Suho, son of Shadow Monarch Sung Jinwoo, must awaken his sealed powers.",
            genres = listOf("Action", "Fantasy", "Manhwa", "Webtoon", "Dungeon"),
            status = 1,
            coverUrl = "",
            favorite = true,
            categoryIds = setOf(1L, 2L),
            notes = "",
            predictedNextUpdateDays = 4,
        ),
        3L to MangaEntry(
            id = 3L,
            sourceId = 5192837465192837465L,
            url = "/series/tower-of-god",
            title = "Tower of God",
            author = "SIU",
            artist = "SIU",
            description = "What do you desire? Money and wealth? Honor and pride? Authority and power? Revenge? Or something that transcends them all? Whatever you desire—it's here.",
            genres = listOf("Action", "Fantasy", "Manhwa", "Webtoon", "Mystery"),
            status = 1,
            coverUrl = "",
            favorite = true,
            categoryIds = setOf(1L),
            notes = "",
            predictedNextUpdateDays = 6,
        ),
        4L to MangaEntry(
            id = 4L,
            sourceId = 2499283573021220255L,
            url = "/manga/the-greatest-estate-developer",
            title = "The Greatest Estate Developer",
            author = "BK_Moon",
            artist = "Lee Hyunmin",
            description = "When civil engineering student Suho Kim falls asleep reading a fantasy novel, he wakes up as a character in the book! Suho is now in the body of Lloyd Frontera, a lazy noble who loves to drink.",
            genres = listOf("Comedy", "Fantasy", "Isekai", "Manhwa", "Webtoon"),
            status = 1,
            coverUrl = "",
            favorite = true,
            categoryIds = setOf(1L, 2L),
            notes = "Water = Lloyd = Good",
            predictedNextUpdateDays = 1,
        ),
        5L to MangaEntry(
            id = 5L,
            sourceId = 2499283573021220255L,
            url = "/manga/sss-class-revival-hunter",
            title = "SSS-Class Revival Hunter",
            author = "Shin Noah",
            artist = "Bill K",
            description = "Kim Gong-Ja lives an envious life at the bottom of the Tower until he acquires an S-rank skill that copies an opponent's power whenever he dies—sending him 24 hours back in time.",
            genres = listOf("Action", "Drama", "Fantasy", "Manhwa", "Webtoon"),
            status = 1,
            coverUrl = "",
            favorite = false,
            categoryIds = emptySet(),
            notes = "",
            predictedNextUpdateDays = 5,
        ),
    )

    private fun seedInitialChapters(): Map<Long, List<ChapterEntry>> = mapOf(
        1L to (230 downTo 220).map { num ->
            ChapterEntry(
                id = 1000L + num,
                mangaId = 1L,
                url = "/series/omniscient-readers-viewpoint/chapter-$num",
                name = "Episode $num - The 73rd Demon Realm (${num - 219})",
                chapterNumber = num.toFloat(),
                scanlator = "Flame / Official",
                read = num < 228,
                bookmark = num == 228,
                downloaded = num >= 229,
                lastPageRead = 0,
                pageCount = 14,
                dateUploadText = "${231 - num}d ago",
                sourceOrder = num.toLong(),
            )
        },
        2L to (48 downTo 40).map { num ->
            ChapterEntry(
                id = 2000L + num,
                mangaId = 2L,
                url = "/series/solo-leveling-ragnarok/chapter-$num",
                name = "Chapter $num - Shadow Heir's Awakening",
                chapterNumber = num.toFloat(),
                scanlator = "REDICE",
                read = num < 46,
                bookmark = false,
                downloaded = num == 48,
                lastPageRead = 0,
                pageCount = 12,
                dateUploadText = "${49 - num}d ago",
                sourceOrder = num.toLong(),
            )
        },
        3L to (650 downTo 645).map { num ->
            ChapterEntry(
                id = 3000L + num,
                mangaId = 3L,
                url = "/series/tower-of-god/chapter-$num",
                name = "Season 3 Ep. ${num - 417} (#$num)",
                chapterNumber = num.toFloat(),
                scanlator = "LINE Webtoon",
                read = num < 649,
                bookmark = false,
                downloaded = false,
                lastPageRead = 0,
                pageCount = 16,
                dateUploadText = "${651 - num}d ago",
                sourceOrder = num.toLong(),
            )
        },
        4L to (175 downTo 170).map { num ->
            ChapterEntry(
                id = 4000L + num,
                mangaId = 4L,
                url = "/manga/the-greatest-estate-developer/chapter-$num",
                name = "Episode $num - Frontera Railway Project",
                chapterNumber = num.toFloat(),
                scanlator = "Official",
                read = num <= 174,
                bookmark = num == 175,
                downloaded = true,
                lastPageRead = 0,
                pageCount = 15,
                dateUploadText = "${176 - num}d ago",
                sourceOrder = num.toLong(),
            )
        },
        5L to (115 downTo 110).map { num ->
            ChapterEntry(
                id = 5000L + num,
                mangaId = 5L,
                url = "/manga/sss-class-revival-hunter/chapter-$num",
                name = "Chapter $num - Heavenly Demon Arc",
                chapterNumber = num.toFloat(),
                scanlator = "Reaper",
                read = false,
                bookmark = false,
                downloaded = false,
                lastPageRead = 0,
                pageCount = 12,
                dateUploadText = "${116 - num}d ago",
                sourceOrder = num.toLong(),
            )
        },
    )

    private fun seedInitialHistory(): List<HistoryEntry> = listOf(
        HistoryEntry(
            id = 1L,
            mangaId = 1L,
            mangaTitle = "Omniscient Reader's Viewpoint",
            chapterId = 1228L,
            chapterName = "Episode 228 - The 73rd Demon Realm (9)",
            chapterNumber = 228f,
            readAtText = "Today, 11:20 AM",
            readDurationMs = 420_000L,
        ),
        HistoryEntry(
            id = 2L,
            mangaId = 4L,
            mangaTitle = "The Greatest Estate Developer",
            chapterId = 4174L,
            chapterName = "Episode 174 - Frontera Railway Project",
            chapterNumber = 174f,
            readAtText = "Yesterday, 9:45 PM",
            readDurationMs = 360_000L,
        ),
        HistoryEntry(
            id = 3L,
            mangaId = 2L,
            mangaTitle = "Solo Leveling: Ragnarok",
            chapterId = 2045L,
            chapterName = "Chapter 45 - Shadow Heir's Awakening",
            chapterNumber = 45f,
            readAtText = "Yesterday, 8:10 PM",
            readDurationMs = 300_000L,
        ),
    )
}

enum class ReadingMode(val displayName: String, val isWebtoonStrip: Boolean) {
    LEFT_TO_RIGHT("Paged (Left to Right)", false),
    RIGHT_TO_LEFT("Paged (Right to Left / Manga)", false),
    VERTICAL("Paged (Vertical)", false),
    WEBTOON("Webtoon (Seamless Strip)", true),
    CONTINUOUS_VERTICAL("Continuous Vertical (With Margins)", true),
}

enum class ReaderColorFilter(val displayName: String) {
    NONE("Off"),
    WARM_SEPIA("Warm Night / Sepia"),
    GRAYSCALE("Grayscale"),
    INVERTED("Inverted Colors"),
    DEUTERANOPIA("Deuteranopia Assist"),
}

enum class TapZoneMode(val displayName: String) {
    DEFAULT("Default (Left / Center / Right)"),
    L_SHAPED("L-Shaped (Webtoon Friendly)"),
    KINDLISH("Kindle-style"),
    EDGE("Edge Only"),
    DISABLED("Disabled (Scroll/Swipe Only)"),
}

enum class LibraryDisplayMode(val displayName: String) {
    COMPACT_GRID("Compact Grid"),
    COMFORTABLE_GRID("Comfortable Grid"),
    COVER_ONLY_GRID("Cover-Only Grid"),
    LIST("List"),
}

enum class LibrarySortMode(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    UNREAD_COUNT("Unread count"),
    TOTAL_CHAPTERS("Total chapters"),
    LAST_READ("Last read"),
    LATEST_CHAPTER("Latest chapter"),
}

data class LibraryFilterState(
    val unreadOnly: Boolean = false,
    val downloadedOnly: Boolean = false,
    val bookmarkedOnly: Boolean = false,
    val completedOnly: Boolean = false,
    val trackedOnly: Boolean = false,
)

data class LibrarySortState(
    val mode: LibrarySortMode = LibrarySortMode.ALPHABETICAL,
    val ascending: Boolean = true,
)

data class CategoryItem(
    val id: Long,
    val name: String,
    val order: Long,
)

data class MangaEntry(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val author: String,
    val artist: String,
    val description: String,
    val genres: List<String>,
    val status: Int,
    val coverUrl: String,
    val favorite: Boolean,
    val categoryIds: Set<Long>,
    val notes: String = "",
    val readingModeOverride: ReadingMode? = null,
    val predictedNextUpdateDays: Int = 7,
) {
    val statusLabel: String
        get() = when (status) {
            1 -> "Ongoing"
            2 -> "Completed"
            3 -> "Licensed"
            4 -> "Publishing finished"
            5 -> "Cancelled"
            6 -> "On Hiatus"
            else -> "Unknown"
        }
}

data class ChapterEntry(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val chapterNumber: Float,
    val scanlator: String,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val downloaded: Boolean = false,
    val lastPageRead: Int = 0,
    val pageCount: Int = 12,
    val dateUploadText: String = "Recently",
    val sourceOrder: Long = 0L,
)

data class HistoryEntry(
    val id: Long,
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val chapterNumber: Float,
    val readAtText: String,
    val readDurationMs: Long,
)

data class MangaTrackEntry(
    val trackerId: Long,
    val mangaId: Long,
    val title: String,
    val statusId: Long,
    val score: Double,
    val lastChapterRead: Double,
    val totalChapters: Long,
)

data class SourceCatalogItem(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val isPinned: Boolean = false,
)

data class ExtensionRepoItem(
    val name: String,
    val indexUrl: String,
    val website: String,
)

data class ExtensionPackageItem(
    val name: String,
    val pkg: String,
    val version: String,
    val lang: String,
    val baseUrl: String,
    val isInstalled: Boolean,
    val hasUpdate: Boolean = false,
    val isNsfw: Boolean = false,
)

data class RestoreSummary(
    val restoredManga: Int,
    val restoredChapters: Int,
    val restoredCategories: Int,
)

data class LibraryStatistics(
    val totalLibraryManga: Int,
    val completedManga: Int,
    val totalChapters: Int,
    val readChapters: Int,
    val downloadedChapters: Int,
    val trackedManga: Int,
    val meanScore: Double,
    val totalReadDurationMinutes: Int,
)

