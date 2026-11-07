package mihon.ios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.IosAppTheme
import eu.kanade.presentation.theme.IosDarkMode
import eu.kanade.presentation.theme.IosMihonTheme
import kotlinx.coroutines.launch
import mihon.ios.domain.ChapterEntry
import mihon.ios.domain.LibraryDisplayMode
import mihon.ios.domain.LibrarySortMode
import mihon.ios.domain.MangaEntry
import mihon.ios.domain.MihonIosStateStore
import mihon.ios.domain.ReadingMode
import mihon.ios.domain.SourceCatalogItem
import mihon.ios.reader.MihonComposeReader

enum class MainNavTab(val label: String, val badgeText: String) {
    LIBRARY("Library", "📚"),
    UPDATES("Updates", "🔔"),
    HISTORY("History", "🕘"),
    BROWSE("Browse", "🌐"),
    MORE("More", "⋯"),
}

sealed class ActiveSubScreen {
    data object None : ActiveSubScreen()
    data class MangaDetails(val mangaId: Long) : ActiveSubScreen()
    data class Reader(val mangaId: Long, val chapterId: Long) : ActiveSubScreen()
    data class SourceBrowseCatalog(val sourceId: Long) : ActiveSubScreen()
    data object DownloadQueue : ActiveSubScreen()
    data object CategoryManager : ActiveSubScreen()
    data object Statistics : ActiveSubScreen()
    data object BackupRestore : ActiveSubScreen()
    data object Settings : ActiveSubScreen()
}

@Composable
fun MihonIosApp(
    stateStore: MihonIosStateStore,
    onRequestBiometricUnlock: (onResult: (Boolean) -> Unit) -> Unit = { it(true) },
) {
    val appTheme by stateStore.appTheme.collectAsState()
    val darkMode by stateStore.darkMode.collectAsState()
    val isAmoled by stateStore.isAmoled.collectAsState()
    val incognitoMode by stateStore.incognitoMode.collectAsState()
    val downloadedOnlyMode by stateStore.downloadedOnlyMode.collectAsState()
    val isAppLocked by stateStore.isAppLocked.collectAsState()

    var currentTab by remember { mutableStateOf(MainNavTab.LIBRARY) }
    var activeSubScreen by remember { mutableStateOf<ActiveSubScreen>(ActiveSubScreen.None) }

    IosMihonTheme(
        appTheme = appTheme,
        darkMode = darkMode,
        isAmoled = isAmoled,
    ) {
        if (isAppLocked) {
            BiometricLockScreen(
                onUnlockRequest = {
                    onRequestBiometricUnlock { success ->
                        if (success) stateStore.unlockApp()
                    }
                },
            )
            return@IosMihonTheme
        }

        // Full-screen Reader View
        val sub = activeSubScreen
        if (sub is ActiveSubScreen.Reader) {
            val mangaMap by stateStore.mangaMap.collectAsState()
            val chaptersMap by stateStore.chaptersByManga.collectAsState()
            val manga = mangaMap[sub.mangaId]
            val chapters = chaptersMap[sub.mangaId].orEmpty()
            val chapter = chapters.firstOrNull { it.id == sub.chapterId } ?: chapters.firstOrNull()
            if (manga != null && chapter != null) {
                MihonComposeReader(
                    stateStore = stateStore,
                    manga = manga,
                    initialChapter = chapter,
                    allChapters = chapters,
                    onBack = { activeSubScreen = ActiveSubScreen.MangaDetails(sub.mangaId) },
                )
                return@IosMihonTheme
            }
        }

        Scaffold(
            topBar = {
                Column {
                    if (incognitoMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "🕶 Incognito mode — Pauses reading history",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (downloadedOnlyMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "⬇ Downloaded only — Filters all manga in your library",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (activeSubScreen is ActiveSubScreen.None) {
                    NavigationBar {
                        MainNavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Text(tab.badgeText, fontSize = 18.sp) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (val screen = activeSubScreen) {
                    is ActiveSubScreen.MangaDetails -> {
                        MangaDetailsScreen(
                            stateStore = stateStore,
                            mangaId = screen.mangaId,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                            onOpenReader = { mId, cId ->
                                activeSubScreen = ActiveSubScreen.Reader(mId, cId)
                            },
                        )
                    }
                    is ActiveSubScreen.SourceBrowseCatalog -> {
                        SourceCatalogScreen(
                            stateStore = stateStore,
                            sourceId = screen.sourceId,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                            onOpenManga = { mId -> activeSubScreen = ActiveSubScreen.MangaDetails(mId) },
                        )
                    }
                    is ActiveSubScreen.DownloadQueue -> {
                        DownloadQueueScreen(
                            stateStore = stateStore,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                        )
                    }
                    is ActiveSubScreen.CategoryManager -> {
                        CategoryManagementScreen(
                            stateStore = stateStore,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                        )
                    }
                    is ActiveSubScreen.Statistics -> {
                        StatisticsScreen(
                            stateStore = stateStore,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                        )
                    }
                    is ActiveSubScreen.BackupRestore -> {
                        BackupRestoreScreen(
                            stateStore = stateStore,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                        )
                    }
                    is ActiveSubScreen.Settings -> {
                        SettingsScreen(
                            stateStore = stateStore,
                            onBack = { activeSubScreen = ActiveSubScreen.None },
                        )
                    }
                    is ActiveSubScreen.Reader -> Unit
                    ActiveSubScreen.None -> {
                        when (currentTab) {
                            MainNavTab.LIBRARY -> LibraryScreen(
                                stateStore = stateStore,
                                onOpenManga = { mId -> activeSubScreen = ActiveSubScreen.MangaDetails(mId) },
                            )
                            MainNavTab.UPDATES -> UpdatesAndUpcomingScreen(
                                stateStore = stateStore,
                                onOpenManga = { mId -> activeSubScreen = ActiveSubScreen.MangaDetails(mId) },
                                onOpenReader = { mId, cId -> activeSubScreen = ActiveSubScreen.Reader(mId, cId) },
                            )
                            MainNavTab.HISTORY -> HistoryScreen(
                                stateStore = stateStore,
                                onOpenManga = { mId -> activeSubScreen = ActiveSubScreen.MangaDetails(mId) },
                                onResumeChapter = { mId, cId -> activeSubScreen = ActiveSubScreen.Reader(mId, cId) },
                            )
                            MainNavTab.BROWSE -> BrowseScreen(
                                stateStore = stateStore,
                                onOpenSourceCatalog = { sId -> activeSubScreen = ActiveSubScreen.SourceBrowseCatalog(sId) },
                                onOpenManga = { mId -> activeSubScreen = ActiveSubScreen.MangaDetails(mId) },
                            )
                            MainNavTab.MORE -> MoreScreen(
                                stateStore = stateStore,
                                onNavigate = { target -> activeSubScreen = target },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(
    onUnlockRequest: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔒", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Mihon is Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Authenticate with Face ID, Touch ID, or device passcode to access your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlockRequest) {
                Text("Unlock with Face ID / Passcode")
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    stateStore: MihonIosStateStore,
    onOpenManga: (Long) -> Unit,
) {
    val categories by stateStore.categories.collectAsState()
    val mangaMap by stateStore.mangaMap.collectAsState()
    val chaptersByManga by stateStore.chaptersByManga.collectAsState()
    val tracksByManga by stateStore.tracksByManga.collectAsState()
    val filterState by stateStore.libraryFilter.collectAsState()
    val sortState by stateStore.librarySort.collectAsState()
    val displayMode by stateStore.libraryDisplayMode.collectAsState()
    val downloadedOnly by stateStore.downloadedOnlyMode.collectAsState()

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val activeCategory = categories.getOrElse(selectedCategoryIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0))) {
        categories.first()
    }

    val filteredLibrary = remember(
        mangaMap,
        chaptersByManga,
        tracksByManga,
        activeCategory,
        searchQuery,
        filterState,
        sortState,
        downloadedOnly,
    ) {
        mangaMap.values
            .filter { it.favorite }
            .filter { activeCategory.id == 0L || activeCategory.id in it.categoryIds }
            .filter { manga ->
                if (searchQuery.isBlank()) {
                    true
                } else {
                    val q = searchQuery.trim().lowercase()
                    when {
                        q.startsWith("author:") -> manga.author.lowercase().contains(q.removePrefix("author:").trim())
                        q.startsWith("genre:") -> manga.genres.any { g -> g.lowercase().contains(q.removePrefix("genre:").trim()) }
                        else -> manga.title.lowercase().contains(q) || manga.author.lowercase().contains(q)
                    }
                }
            }
            .filter { manga ->
                val chs = chaptersByManga[manga.id].orEmpty()
                val unreadCount = chs.count { !it.read }
                val dlCount = chs.count { it.downloaded }
                val hasBookmark = chs.any { it.bookmark }
                val isTracked = tracksByManga[manga.id].orEmpty().isNotEmpty()
                (!filterState.unreadOnly || unreadCount > 0) &&
                    (!(filterState.downloadedOnly || downloadedOnly) || dlCount > 0) &&
                    (!filterState.bookmarkedOnly || hasBookmark) &&
                    (!filterState.completedOnly || manga.status == 2) &&
                    (!filterState.trackedOnly || isTracked)
            }
            .let { list ->
                val comparator: Comparator<MangaEntry> = when (sortState.mode) {
                    LibrarySortMode.ALPHABETICAL -> compareBy { it.title.lowercase() }
                    LibrarySortMode.UNREAD_COUNT -> compareBy { m -> chaptersByManga[m.id].orEmpty().count { !it.read } }
                    LibrarySortMode.TOTAL_CHAPTERS -> compareBy { m -> chaptersByManga[m.id].orEmpty().size }
                    LibrarySortMode.LAST_READ -> compareBy { it.id }
                    LibrarySortMode.LATEST_CHAPTER -> compareBy { m -> chaptersByManga[m.id].orEmpty().maxOfOrNull { it.chapterNumber } ?: 0f }
                }
                if (sortState.ascending) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Library (${filteredLibrary.size})",
                    fontWeight = FontWeight.Bold,
                )
            },
            actions = {
                TextButton(onClick = { showFilterSheet = true }) {
                    Text("Filter / Sort")
                }
            },
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search library (or author:, genre:)...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (categories.size > 1) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex.coerceIn(0, categories.size - 1),
                edgePadding = 16.dp,
            ) {
                categories.forEachIndexed { idx, cat ->
                    Tab(
                        selected = selectedCategoryIndex == idx,
                        onClick = { selectedCategoryIndex = idx },
                        text = { Text(cat.name) },
                    )
                }
            }
        }

        if (displayMode == LibraryDisplayMode.LIST) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredLibrary, key = { it.id }) { manga ->
                    val chs = chaptersByManga[manga.id].orEmpty()
                    val unread = chs.count { !it.read }
                    val downloaded = chs.count { it.downloaded }
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenManga(manga.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MangaCoverThumbnail(title = manga.title, modifier = Modifier.size(56.dp, 78.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(manga.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(manga.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = manga.genres.take(3).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (downloaded > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                        Text("↓$downloaded", modifier = Modifier.padding(2.dp))
                                    }
                                }
                                if (unread > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("$unread", modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val columns = if (displayMode == LibraryDisplayMode.COMPACT_GRID) 3 else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredLibrary, key = { it.id }) { manga ->
                    val chs = chaptersByManga[manga.id].orEmpty()
                    val unread = chs.count { !it.read }
                    val downloaded = chs.count { it.downloaded }
                    MangaGridCard(
                        manga = manga,
                        unreadCount = unread,
                        downloadedCount = downloaded,
                        showTitleBelow = displayMode != LibraryDisplayMode.COVER_ONLY_GRID,
                        onClick = { onOpenManga(manga.id) },
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Library Filter, Sort & Display", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text("Filter", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterState.unreadOnly,
                        onClick = { stateStore.updateLibraryFilter { it.copy(unreadOnly = !it.unreadOnly) } },
                        label = { Text("Unread") },
                    )
                    FilterChip(
                        selected = filterState.downloadedOnly,
                        onClick = { stateStore.updateLibraryFilter { it.copy(downloadedOnly = !it.downloadedOnly) } },
                        label = { Text("Downloaded") },
                    )
                    FilterChip(
                        selected = filterState.bookmarkedOnly,
                        onClick = { stateStore.updateLibraryFilter { it.copy(bookmarkedOnly = !it.bookmarkedOnly) } },
                        label = { Text("Bookmarked") },
                    )
                    FilterChip(
                        selected = filterState.trackedOnly,
                        onClick = { stateStore.updateLibraryFilter { it.copy(trackedOnly = !it.trackedOnly) } },
                        label = { Text("Tracked") },
                    )
                }

                HorizontalDivider()

                Text("Sort By (${if (sortState.ascending) "Ascending ↑" else "Descending ↓"})", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibrarySortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sortState.mode == mode,
                            onClick = {
                                stateStore.updateLibrarySort { current ->
                                    if (current.mode == mode) {
                                        current.copy(ascending = !current.ascending)
                                    } else {
                                        current.copy(mode = mode, ascending = true)
                                    }
                                }
                            },
                            label = { Text(mode.displayName) },
                        )
                    }
                }

                HorizontalDivider()

                Text("Display Mode", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { stateStore.setLibraryDisplayMode(mode) },
                            label = { Text(mode.displayName) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MangaGridCard(
    manga: MangaEntry,
    unreadCount: Int,
    downloadedCount: Int,
    showTitleBelow: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f),
            ) {
                MangaCoverThumbnail(
                    title = manga.title,
                    modifier = Modifier.fillMaxSize(),
                )

                // Top-left Unread / Downloaded Badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (downloadedCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "↓$downloadedCount",
                                color = MaterialTheme.colorScheme.onTertiary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (unreadCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "$unreadCount",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (showTitleBelow) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = manga.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaCoverThumbnail(
    title: String,
    modifier: Modifier = Modifier,
) {
    val gradientColors = remember(title) {
        val hash = title.hashCode()
        listOf(
            Color(
                red = (((hash shr 16) and 0x7F) + 30) / 255f,
                green = (((hash shr 8) and 0x7F) + 40) / 255f,
                blue = ((hash and 0x7F) + 90) / 255f,
            ),
            Color(0xFF101522),
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(gradientColors))
            .padding(10.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MangaDetailsScreen(
    stateStore: MihonIosStateStore,
    mangaId: Long,
    onBack: () -> Unit,
    onOpenReader: (Long, Long) -> Unit,
) {
    val mangaMap by stateStore.mangaMap.collectAsState()
    val chaptersMap by stateStore.chaptersByManga.collectAsState()
    val categories by stateStore.categories.collectAsState()
    val sources by stateStore.sources.collectAsState()
    val tracksMap by stateStore.tracksByManga.collectAsState()

    val manga = mangaMap[mangaId] ?: return
    val chapters = chaptersMap[mangaId].orEmpty()
    val boundTracks = tracksMap[mangaId].orEmpty()
    val sourceName = sources.firstOrNull { it.id == manga.sourceId }?.name ?: "Source"

    var showNotesDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTrackerSheet by remember { mutableStateOf(false) }
    var showMigrateDialog by remember { mutableStateOf(false) }
    var unreadFilterOnly by remember { mutableStateOf(false) }

    val displayedChapters = remember(chapters, unreadFilterOnly) {
        if (unreadFilterOnly) chapters.filter { !it.read } else chapters
    }
    val nextUnreadChapter = remember(chapters) {
        chapters.sortedBy { it.chapterNumber }.firstOrNull { !it.read } ?: chapters.firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                },
                actions = {
                    TextButton(onClick = { showMigrateDialog = true }) { Text("Migrate") }
                },
            )
        },
        floatingActionButton = {
            if (nextUnreadChapter != null) {
                FloatingActionButton(
                    onClick = { onOpenReader(manga.id, nextUnreadChapter.id) },
                ) {
                    Text(
                        text = if (chapters.any { it.read }) {
                            "▶ Resume Ch. ${nextUnreadChapter.chapterNumber.toInt()}"
                        } else {
                            "▶ Start Reading"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MangaCoverThumbnail(
                        title = manga.title,
                        modifier = Modifier.size(110.dp, 156.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Author: ${manga.author}", style = MaterialTheme.typography.bodyMedium)
                        Text("Artist: ${manga.artist}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${manga.statusLabel} • $sourceName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Action Bar (In Library, Categories, Tracking, Notes)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilterChip(
                        selected = manga.favorite,
                        onClick = { stateStore.toggleFavorite(manga.id) },
                        label = { Text(if (manga.favorite) "♥ In Library" else "♡ Add to Library") },
                    )
                    FilterChip(
                        selected = boundTracks.isNotEmpty(),
                        onClick = { showTrackerSheet = true },
                        label = { Text("🔄 Tracking (${boundTracks.size})") },
                    )
                    FilterChip(
                        selected = manga.notes.isNotBlank(),
                        onClick = { showNotesDialog = true },
                        label = { Text("📝 Notes") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showCategoryDialog = true },
                        label = { Text("🏷 Categories") },
                    )
                }
            }

            // Description & Genre Chips
            item {
                Text(manga.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    manga.genres.forEach { genre ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                if (manga.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Note: ${manga.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            // Chapters Header & Actions
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${displayedChapters.size} Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = unreadFilterOnly,
                            onClick = { unreadFilterOnly = !unreadFilterOnly },
                            label = { Text("Unread Only") },
                        )
                        TextButton(onClick = { stateStore.markAllChaptersRead(manga.id, true) }) {
                            Text("Mark All Read")
                        }
                    }
                }
            }

            // Chapter Rows
            items(displayedChapters, key = { it.id }) { chapter ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenReader(manga.id, chapter.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (chapter.read) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (chapter.bookmark) {
                                    Text("★ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = chapter.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.SemiBold,
                                    color = if (chapter.read) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            Text(
                                text = "${chapter.dateUploadText} • ${chapter.scanlator}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TextButton(
                            onClick = { stateStore.markChapterRead(manga.id, chapter.id, !chapter.read) },
                        ) {
                            Text(if (chapter.read) "Unread" else "Read", fontSize = 11.sp)
                        }

                        TextButton(
                            onClick = { stateStore.enqueueChapterDownload(manga.id, chapter.id) },
                        ) {
                            Text(if (chapter.downloaded) "✓ Saved" else "⬇ DL", fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // Manga Notes Editor Modal
    if (showNotesDialog) {
        var draftNotes by remember(manga.notes) { mutableStateOf(manga.notes) }
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Manga Notes") },
            text = {
                OutlinedTextField(
                    value = draftNotes,
                    onValueChange = { draftNotes = it },
                    placeholder = { Text("Write personal reading notes, character names, or reminders...") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateStore.updateMangaNotes(manga.id, draftNotes)
                        showNotesDialog = false
                    },
                ) {
                    Text("Save Notes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Category Assignment Dialog
    if (showCategoryDialog) {
        var selectedIds by remember(manga.categoryIds) { mutableStateOf(manga.categoryIds) }
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Set Categories") },
            text = {
                Column {
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (cat.id in selectedIds) selectedIds - cat.id else selectedIds + cat.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = cat.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + cat.id else selectedIds - cat.id
                                },
                            )
                            Text(cat.name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateStore.setMangaCategories(manga.id, selectedIds)
                        showCategoryDialog = false
                    },
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") }
            },
        )
    }

    // 12-Tracker Sheet
    if (showTrackerSheet) {
        ModalBottomSheet(onDismissRequest = { showTrackerSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Trackers (All 12 Services)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                stateStore.trackManager.trackers.forEach { tracker ->
                    val existing = boundTracks.firstOrNull { it.trackerId == tracker.id }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tracker.name, fontWeight = FontWeight.Bold)
                                if (existing != null) {
                                    Text(
                                        text = "Tracked: Ch. ${existing.lastChapterRead.toInt()} • Score: ${existing.score}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(
                                        text = "Not linked yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val highestRead = chapters.filter { it.read }.maxOfOrNull { it.chapterNumber } ?: 0f
                                    stateStore.bindTrackerToManga(
                                        mangaId = manga.id,
                                        trackerId = tracker.id,
                                        title = manga.title,
                                        statusId = 1L,
                                        score = if (existing != null) (existing.score + 0.5).coerceAtMost(10.0) else 9.0,
                                        lastChapterRead = highestRead.toDouble(),
                                        totalChapters = chapters.size.toLong(),
                                    )
                                },
                            ) {
                                Text(if (existing != null) "Sync / +Score" else "Bind Tracker")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Source Migration Dialog
    if (showMigrateDialog) {
        AlertDialog(
            onDismissRequest = { showMigrateDialog = false },
            title = { Text("Migrate Source for ${manga.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select target source to migrate chapters, categories, and trackers:")
                    sources.forEach { targetSource ->
                        OutlinedButton(
                            onClick = {
                                stateStore.migrateMangaSource(manga.id, targetSource.id)
                                showMigrateDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${targetSource.name} (${targetSource.lang.uppercase()})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMigrateDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun UpdatesAndUpcomingScreen(
    stateStore: MihonIosStateStore,
    onOpenManga: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
) {
    val mangaMap by stateStore.mangaMap.collectAsState()
    val chaptersMap by stateStore.chaptersByManga.collectAsState()
    var selectedSubTab by remember { mutableIntStateOf(0) }

    val recentUpdates = remember(mangaMap, chaptersMap) {
        mangaMap.values.filter { it.favorite }.flatMap { m ->
            chaptersMap[m.id].orEmpty().take(3).map { ch -> m to ch }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Updates & Upcoming", fontWeight = FontWeight.Bold) })

        PrimaryTabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Recent Updates (${recentUpdates.size})") },
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Upcoming Calendar") },
            )
        }

        if (selectedSubTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(recentUpdates, key = { it.second.id }) { (manga, chapter) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReader(manga.id, chapter.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MangaCoverThumbnail(
                                title = manga.title,
                                modifier = Modifier
                                    .size(46.dp, 64.dp)
                                    .clickable { onOpenManga(manga.id) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(manga.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(chapter.name, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = chapter.dateUploadText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { stateStore.enqueueChapterDownload(manga.id, chapter.id) }) {
                                Text(if (chapter.downloaded) "✓ Saved" else "⬇")
                            }
                        }
                    }
                }
            }
        } else {
            // Upcoming Calendar View
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val upcomingManga = mangaMap.values.filter { it.favorite && it.status == 1 }
                    .sortedBy { it.predictedNextUpdateDays }
                items(upcomingManga, key = { it.id }) { manga ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenManga(manga.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(manga.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Expected next chapter in ${manga.predictedNextUpdateDays} day(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "In ${manga.predictedNextUpdateDays}d",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    stateStore: MihonIosStateStore,
    onOpenManga: (Long) -> Unit,
    onResumeChapter: (Long, Long) -> Unit,
) {
    val history by stateStore.history.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(history, searchQuery) {
        if (searchQuery.isBlank()) {
            history
        } else {
            history.filter { it.mangaTitle.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("History", fontWeight = FontWeight.Bold) },
            actions = {
                if (history.isNotEmpty()) {
                    TextButton(onClick = stateStore::clearAllHistory) {
                        Text("Clear All")
                    }
                }
            },
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search reading history...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenManga(item.mangaId) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaCoverThumbnail(title = item.mangaTitle, modifier = Modifier.size(48.dp, 66.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.mangaTitle, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Ch. ${item.chapterNumber.toInt()} — ${item.readAtText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { onResumeChapter(item.mangaId, item.chapterId) }) {
                            Text("Resume")
                        }
                        TextButton(onClick = { stateStore.removeHistoryEntry(item.id) }) {
                            Text("✕")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseScreen(
    stateStore: MihonIosStateStore,
    onOpenSourceCatalog: (Long) -> Unit,
    onOpenManga: (Long) -> Unit,
) {
    val sources by stateStore.sources.collectAsState()
    val extensions by stateStore.extensions.collectAsState()
    val extensionRepos by stateStore.extensionRepos.collectAsState()
    val mangaMap by stateStore.mangaMap.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var globalSearchQuery by remember { mutableStateOf("") }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Browse", fontWeight = FontWeight.Bold) },
            actions = {
                if (selectedSubTab == 1) {
                    TextButton(onClick = { showAddRepoDialog = true }) {
                        Text("+ Add Repo")
                    }
                }
            },
        )

        PrimaryTabRow(selectedTabIndex = selectedSubTab) {
            Tab(selected = selectedSubTab == 0, onClick = { selectedSubTab = 0 }, text = { Text("Sources (${sources.size})") })
            Tab(selected = selectedSubTab == 1, onClick = { selectedSubTab = 1 }, text = { Text("Extensions (${extensions.size})") })
            Tab(selected = selectedSubTab == 2, onClick = { selectedSubTab = 2 }, text = { Text("Migrate") })
        }

        when (selectedSubTab) {
            0 -> {
                OutlinedTextField(
                    value = globalSearchQuery,
                    onValueChange = { globalSearchQuery = it },
                    placeholder = { Text("Global search across all sources...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (globalSearchQuery.isNotBlank()) {
                        val matches = mangaMap.values.filter {
                            it.title.contains(globalSearchQuery, ignoreCase = true)
                        }
                        item {
                            Text(
                                text = "Global Search Matches (${matches.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(matches, key = { it.id }) { manga ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenManga(manga.id) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MangaCoverThumbnail(manga.title, Modifier.size(44.dp, 60.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(manga.title, fontWeight = FontWeight.Bold)
                                        Text(manga.author, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    items(sources.sortedByDescending { it.isPinned }, key = { it.id }) { source ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSourceCatalog(source.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${source.lang.uppercase()} • ${source.baseUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onOpenSourceCatalog(source.id) }) {
                                    Text("Latest")
                                }
                                TextButton(onClick = { stateStore.togglePinSource(source.id) }) {
                                    Text(if (source.isPinned) "📌 Pinned" else "Pin")
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "Configured Extension Repositories (${extensionRepos.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(extensionRepos, key = { it.indexUrl }) { repo ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(repo.name, fontWeight = FontWeight.Bold)
                                    Text(repo.indexUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { stateStore.removeExtensionRepo(repo.indexUrl) }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Cross-Platform Extensions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(extensions, key = { it.pkg }) { ext ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ext.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "v${ext.version} • ${ext.lang.uppercase()} • ${ext.baseUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = { stateStore.toggleInstallExtension(ext.pkg) }) {
                                    Text(if (ext.isInstalled) "Installed ✓" else "Install")
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                val favManga = mangaMap.values.filter { it.favorite }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "Select a title to migrate chapters, categories & trackers to another source:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(favManga, key = { it.id }) { manga ->
                        val srcName = sources.firstOrNull { it.id == manga.sourceId }?.name ?: "Unknown"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenManga(manga.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(manga.title, fontWeight = FontWeight.Bold)
                                    Text("Current source: $srcName", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onOpenManga(manga.id) }) {
                                    Text("Migrate →")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        var repoName by remember { mutableStateOf("") }
        var repoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Extension Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repoName,
                        onValueChange = { repoName = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        label = { Text("index.min.json URL") },
                        placeholder = { Text("https://.../index.min.json") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateStore.addExtensionRepo(repoName, repoUrl)
                        showAddRepoDialog = false
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SourceCatalogScreen(
    stateStore: MihonIosStateStore,
    sourceId: Long,
    onBack: () -> Unit,
    onOpenManga: (Long) -> Unit,
) {
    val sources by stateStore.sources.collectAsState()
    val mangaMap by stateStore.mangaMap.collectAsState()
    val source = sources.firstOrNull { it.id == sourceId }
    var searchQuery by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Popular") }

    val catalogItems = remember(mangaMap, searchQuery, mode) {
        val all = mangaMap.values.toList()
        val searched = if (searchQuery.isBlank()) all else all.filter { it.title.contains(searchQuery, ignoreCase = true) }
        if (mode == "Latest") searched.reversed() else searched
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source?.name ?: "Catalog") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = mode == "Popular", onClick = { mode = "Popular" }, label = { Text("🔥 Popular") })
                FilterChip(selected = mode == "Latest", onClick = { mode = "Latest" }, label = { Text("🆕 Latest") })
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${source?.name ?: "source"}...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(catalogItems, key = { it.id }) { manga ->
                    MangaGridCard(
                        manga = manga,
                        unreadCount = if (manga.favorite) 1 else 0,
                        downloadedCount = 0,
                        showTitleBelow = true,
                        onClick = { onOpenManga(manga.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    stateStore: MihonIosStateStore,
    onNavigate: (ActiveSubScreen) -> Unit,
) {
    val downloadedOnly by stateStore.downloadedOnlyMode.collectAsState()
    val incognito by stateStore.incognitoMode.collectAsState()
    val queue by stateStore.downloadManager.queueState.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Mihon for iOS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "v0.21.3-iOS • Kotlin Multiplatform & Compose Multiplatform",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Downloaded only", fontWeight = FontWeight.SemiBold)
                            Text("Filters all manga in your library", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = downloadedOnly, onCheckedChange = stateStore::setDownloadedOnlyMode)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Incognito mode", fontWeight = FontWeight.SemiBold)
                            Text("Pauses reading history", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = incognito, onCheckedChange = stateStore::setIncognitoMode)
                    }
                }
            }
        }

        item {
            MoreMenuRow(
                title = "⬇ Download Queue (${queue.size})",
                subtitle = "Manage active and queued chapter downloads",
                onClick = { onNavigate(ActiveSubScreen.DownloadQueue) },
            )
        }
        item {
            MoreMenuRow(
                title = "🏷 Categories",
                subtitle = "Create, rename, and organize library categories",
                onClick = { onNavigate(ActiveSubScreen.CategoryManager) },
            )
        }
        item {
            MoreMenuRow(
                title = "📊 Statistics",
                subtitle = "View library entries, chapters read, tracker scores, and reading time",
                onClick = { onNavigate(ActiveSubScreen.Statistics) },
            )
        }
        item {
            MoreMenuRow(
                title = "💾 Data and Storage (Backup & Restore)",
                subtitle = "Create and restore cross-platform .tachibk / .proto.gz backups",
                onClick = { onNavigate(ActiveSubScreen.BackupRestore) },
            )
        }
        item {
            MoreMenuRow(
                title = "⚙ Settings",
                subtitle = "Appearance (15 themes), Reader, Library, Tracking (12 services), Security (FaceID)",
                onClick = { onNavigate(ActiveSubScreen.Settings) },
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadQueueScreen(
    stateStore: MihonIosStateStore,
    onBack: () -> Unit,
) {
    val queue by stateStore.downloadManager.queueState.collectAsState()
    val isRunning by stateStore.downloadManager.isDownloaderRunning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Queue (${queue.size})") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
                actions = {
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = stateStore.downloadManager::clearQueue) { Text("Clear") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (queue.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (isRunning) stateStore.downloadManager.pauseDownloads() else stateStore.downloadManager.startDownloads()
                    },
                ) {
                    Text(
                        text = if (isRunning) "⏸ Pause" else "▶ Start",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No active downloads in queue.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(queue, key = { it.chapterId }) { job ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(job.mangaTitle, fontWeight = FontWeight.Bold)
                            Text("${job.chapterName} • ${job.status}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { job.progressFraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagementScreen(
    stateStore: MihonIosStateStore,
    onBack: () -> Unit,
) {
    val categories by stateStore.categories.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("New category name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        stateStore.addCategory(newCategoryName)
                        newCategoryName = ""
                    },
                ) {
                    Text("+ Add")
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.name, fontWeight = FontWeight.SemiBold)
                            if (cat.id != 0L) {
                                TextButton(onClick = { stateStore.deleteCategory(cat.id) }) {
                                    Text("Delete")
                                }
                            } else {
                                Text("System Default", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsScreen(
    stateStore: MihonIosStateStore,
    onBack: () -> Unit,
) {
    val stats = remember(
        stateStore.mangaMap.collectAsState().value,
        stateStore.chaptersByManga.collectAsState().value,
        stateStore.tracksByManga.collectAsState().value,
        stateStore.history.collectAsState().value,
    ) {
        stateStore.computeStatistics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Library Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("In Library: ${stats.totalLibraryManga} series")
                    Text("Completed Series: ${stats.completedManga}")
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chapters & Reading Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Total Chapters: ${stats.totalChapters}")
                    Text("Read Chapters: ${stats.readChapters}")
                    Text("Downloaded Chapters: ${stats.downloadedChapters}")
                    Text("Total Reading Duration: ${stats.totalReadDurationMinutes} min")
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Trackers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tracked Series: ${stats.trackedManga}")
                    Text("Mean Tracker Score: ${stats.meanScore}")
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreScreen(
    stateStore: MihonIosStateStore,
    onBack: () -> Unit,
) {
    var statusMessage by remember { mutableStateOf("") }
    var lastBackupBytes by remember { mutableStateOf<ByteArray?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup and Restore (.tachibk)") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Mihon iOS uses the exact same Gzipped ProtoBuf (.tachibk / .proto.gz) format as Android Mihon. Backups can be transferred seamlessly between Android and iOS via the Files app or iCloud Drive.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = {
                    val encoded = stateStore.exportBackupBytes()
                    lastBackupBytes = encoded
                    statusMessage = "Created .tachibk backup (${encoded.size} bytes Gzipped ProtoBuf) in On My iPhone > Mihon > autobackup."
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create .tachibk Backup Now")
            }

            OutlinedButton(
                onClick = {
                    val payload = lastBackupBytes ?: stateStore.exportBackupBytes()
                    val summary = stateStore.restoreFromBackupBytes(payload)
                    statusMessage = "Restored ${summary.restoredManga} series, ${summary.restoredChapters} chapters, and ${summary.restoredCategories} categories from .tachibk!"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Verify & Restore .tachibk Backup")
            }

            if (statusMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    stateStore: MihonIosStateStore,
    onBack: () -> Unit,
) {
    val appTheme by stateStore.appTheme.collectAsState()
    val darkMode by stateStore.darkMode.collectAsState()
    val isAmoled by stateStore.isAmoled.collectAsState()
    val defaultReadingMode by stateStore.defaultReadingMode.collectAsState()
    val requireBiometricLock by stateStore.requireBiometricLock.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Appearance & Color Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosDarkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = darkMode == mode,
                            onClick = { stateStore.setDarkMode(mode) },
                            label = { Text(mode.displayName) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pure Black Dark Mode (AMOLED)")
                    Switch(checked = isAmoled, onCheckedChange = stateStore::setAmoled)
                }
            }

            item {
                Text("App Theme (${appTheme.displayName})", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IosAppTheme.entries.chunked(3).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowThemes.forEach { theme ->
                                FilterChip(
                                    selected = appTheme == theme,
                                    onClick = { stateStore.setAppTheme(theme) },
                                    label = { Text(theme.displayName, fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }

            item {
                Text("Default Reader Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column {
                    ReadingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { stateStore.setDefaultReadingMode(mode) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(mode.displayName)
                            if (defaultReadingMode == mode) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }

            item {
                Text("Security & Biometrics (iOS LocalAuthentication)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Face ID / Touch ID Unlock")
                        Text(
                            text = "Locks Mihon and blurs the app switcher preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = requireBiometricLock,
                        onCheckedChange = stateStore::setRequireBiometricLock,
                    )
                }
            }
        }
    }
}

