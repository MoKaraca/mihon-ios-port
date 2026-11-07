package mihon.ios.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mihon.ios.domain.ChapterEntry
import mihon.ios.domain.MangaEntry
import mihon.ios.domain.MihonIosStateStore
import mihon.ios.domain.ReaderColorFilter
import mihon.ios.domain.ReadingMode
import mihon.ios.domain.TapZoneMode
import kotlin.math.roundToInt

/**
 * Full-featured Compose Multiplatform Manga & Manhwa/Webtoon Reader for Mihon iOS.
 * Supports:
 * - 5 Reading Modes: Webtoon (Zero-Gap Continuous Strip), Continuous Vertical, Paged LTR, Paged RTL, Paged Vertical
 * - Pinch-to-zoom (1x..4x) & Double-tap zoom (1x <-> 2.25x)
 * - Tap Zone navigation (Default, L-Shaped, Kindlish, Edge, Disabled) + visual Tap Zone overlay
 * - Crop Borders, Split Tall Images, Dual-Page Split
 * - Color Filters (Warm Sepia, Grayscale, Inverted, Deuteranopia) & Custom Brightness
 * - Previous / Next Chapter seamless transition & automatic reading progress + tracker sync
 */
@Composable
fun MihonComposeReader(
    stateStore: MihonIosStateStore,
    manga: MangaEntry,
    initialChapter: ChapterEntry,
    allChapters: List<ChapterEntry>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentChapter by remember(initialChapter.id) { mutableStateOf(initialChapter) }

    val defaultMode by stateStore.defaultReadingMode.collectAsState()
    val effectiveMode = manga.readingModeOverride ?: defaultMode
    val cropBorders by stateStore.cropBorders.collectAsState()
    val splitTallImages by stateStore.splitTallImages.collectAsState()
    val dualPageSplit by stateStore.dualPageSplit.collectAsState()
    val colorFilter by stateStore.readerColorFilter.collectAsState()
    val customBrightness by stateStore.customBrightness.collectAsState()
    val tapZoneMode by stateStore.tapZoneMode.collectAsState()
    val keepScreenOn by stateStore.keepScreenOn.collectAsState()

    val pageCount = remember(currentChapter.id, dualPageSplit) {
        val base = currentChapter.pageCount.coerceAtLeast(1)
        if (dualPageSplit) base * 2 else base
    }

    var currentPage by remember(currentChapter.id) {
        mutableIntStateOf(currentChapter.lastPageRead.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }
    var showHud by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTapZoneOverlay by remember { mutableStateOf(false) }

    // Zoom & pan state
    var zoomScale by remember(currentChapter.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentChapter.id) { mutableStateOf(Offset.Zero) }

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pageCount },
    )
    val webtoonListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    // Keep currentPage synchronized with active viewer
    val visibleWebtoonPage by remember {
        derivedStateOf { webtoonListState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }
    }

    LaunchedEffect(visibleWebtoonPage, effectiveMode) {
        if (effectiveMode.isWebtoonStrip) {
            currentPage = visibleWebtoonPage
        }
    }

    LaunchedEffect(pagerState.currentPage, effectiveMode) {
        if (!effectiveMode.isWebtoonStrip) {
            currentPage = pagerState.currentPage
        }
    }

    // Mark chapter read automatically when reaching the last page
    LaunchedEffect(currentPage, pageCount, currentChapter.id) {
        val isLastPage = currentPage >= pageCount - 1
        stateStore.markChapterRead(
            mangaId = manga.id,
            chapterId = currentChapter.id,
            read = isLastPage || currentChapter.read,
            lastPageRead = currentPage,
        )
    }

    val sortedChapters = remember(allChapters) {
        allChapters.sortedBy { it.chapterNumber }
    }
    val currentChapterIndex = sortedChapters.indexOfFirst { it.id == currentChapter.id }
    val prevChapter = sortedChapters.getOrNull(currentChapterIndex - 1)
    val nextChapter = sortedChapters.getOrNull(currentChapterIndex + 1)

    fun navigatePage(delta: Int) {
        val target = (currentPage + delta).coerceIn(0, pageCount - 1)
        currentPage = target
        scope.launch {
            if (effectiveMode.isWebtoonStrip) {
                webtoonListState.animateScrollToItem(target)
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .pointerInput(tapZoneMode, effectiveMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                    zoomScale = newScale
                    panOffset = if (newScale > 1.02f) {
                        Offset(
                            x = (panOffset.x + pan.x).coerceIn(-600f, 600f),
                            y = (panOffset.y + pan.y).coerceIn(-900f, 900f),
                        )
                    } else {
                        Offset.Zero
                    }
                }
            }
            .pointerInput(tapZoneMode, effectiveMode, currentPage, pageCount) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.1f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.25f
                        }
                    },
                    onTap = { tapOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val relX = tapOffset.x / width
                        val relY = tapOffset.y / height

                        when (tapZoneMode) {
                            TapZoneMode.DISABLED -> {
                                showHud = !showHud
                            }
                            TapZoneMode.L_SHAPED -> {
                                when {
                                    relY < 0.25f || (relX < 0.30f && relY < 0.70f) -> navigatePage(-1)
                                    relY > 0.75f || (relX > 0.70f && relY > 0.30f) -> navigatePage(1)
                                    else -> showHud = !showHud
                                }
                            }
                            else -> {
                                val rtlMultiplier = if (effectiveMode == ReadingMode.RIGHT_TO_LEFT) -1 else 1
                                when {
                                    relX < 0.33f -> navigatePage(-1 * rtlMultiplier)
                                    relX > 0.66f -> navigatePage(1 * rtlMultiplier)
                                    else -> showHud = !showHud
                                }
                            }
                        }
                    },
                )
            },
    ) {
        // Main Reader Viewport (Webtoon Strip vs Paged Viewers)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                },
        ) {
            when (effectiveMode) {
                ReadingMode.WEBTOON, ReadingMode.CONTINUOUS_VERTICAL -> {
                    val itemSpacing = if (effectiveMode == ReadingMode.WEBTOON) 0.dp else 12.dp
                    LazyColumn(
                        state = webtoonListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                        contentPadding = PaddingValues(
                            horizontal = if (cropBorders) 0.dp else 4.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        items(count = pageCount, key = { it }) { pageIndex ->
                            RenderedManhwaPagePanel(
                                mangaTitle = manga.title,
                                chapterName = currentChapter.name,
                                chapterNumber = currentChapter.chapterNumber,
                                pageIndex = pageIndex,
                                totalPages = pageCount,
                                isWebtoonStrip = true,
                                cropBorders = cropBorders,
                                splitTallImages = splitTallImages,
                            )
                        }

                        // Chapter Transition Footer at end of Webtoon strip
                        item(key = "chapter_transition_footer") {
                            ChapterTransitionBanner(
                                currentChapter = currentChapter,
                                prevChapter = prevChapter,
                                nextChapter = nextChapter,
                                onSelectChapter = { target ->
                                    currentChapter = target
                                    currentPage = 0
                                },
                            )
                        }
                    }
                }

                ReadingMode.LEFT_TO_RIGHT, ReadingMode.RIGHT_TO_LEFT -> {
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = effectiveMode == ReadingMode.RIGHT_TO_LEFT,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            RenderedManhwaPagePanel(
                                mangaTitle = manga.title,
                                chapterName = currentChapter.name,
                                chapterNumber = currentChapter.chapterNumber,
                                pageIndex = pageIndex,
                                totalPages = pageCount,
                                isWebtoonStrip = false,
                                cropBorders = cropBorders,
                                splitTallImages = splitTallImages,
                            )
                        }
                    }
                }

                ReadingMode.VERTICAL -> {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            RenderedManhwaPagePanel(
                                mangaTitle = manga.title,
                                chapterName = currentChapter.name,
                                chapterNumber = currentChapter.chapterNumber,
                                pageIndex = pageIndex,
                                totalPages = pageCount,
                                isWebtoonStrip = false,
                                cropBorders = cropBorders,
                                splitTallImages = splitTallImages,
                            )
                        }
                    }
                }
            }
        }

        // Color Filter & Custom Brightness Overlay
        if (colorFilter != ReaderColorFilter.NONE || customBrightness != 0f) {
            val filterOverlayColor = when (colorFilter) {
                ReaderColorFilter.NONE -> Color.Transparent
                ReaderColorFilter.WARM_SEPIA -> Color(0x38FF9E40)
                ReaderColorFilter.GRAYSCALE -> Color(0x44808080)
                ReaderColorFilter.INVERTED -> Color(0x33FFFFFF)
                ReaderColorFilter.DEUTERANOPIA -> Color(0x2800A896)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(filterOverlayColor),
            )
            if (customBrightness < 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (-customBrightness).coerceIn(0f, 0.75f))),
                )
            }
        }

        // Optional Visual Tap Zone Guide Overlay
        AnimatedVisibility(
            visible = showTapZoneOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showTapZoneOverlay = false },
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(Color(0x552979FF))
                        .border(2.dp, Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (effectiveMode == ReadingMode.RIGHT_TO_LEFT) "NEXT\nPAGE" else "PREV\nPAGE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(Color(0x55424242))
                        .border(2.dp, Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "MENU\nHUD\n(Tap to close guide)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(Color(0x5500E676))
                        .border(2.dp, Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (effectiveMode == ReadingMode.RIGHT_TO_LEFT) "PREV\nPAGE" else "NEXT\nPAGE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Persistent subtle page indicator pill at bottom when HUD is hidden
        if (!showHud) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        // Top Reader HUD Bar
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = Color(0xE612151E),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manga.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentChapter.name,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = {
                            stateStore.toggleChapterBookmark(manga.id, currentChapter.id)
                            currentChapter = currentChapter.copy(bookmark = !currentChapter.bookmark)
                        },
                    ) {
                        Text(
                            text = if (currentChapter.bookmark) "★ Bookmarked" else "☆ Bookmark",
                            color = if (currentChapter.bookmark) Color(0xFFFFD54F) else Color.White,
                        )
                    }
                }
            }
        }

        // Bottom Reader HUD Bar (Seekbar, Chapter Switchers, Mode & Settings Controls)
        AnimatedVisibility(
            visible = showHud,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = Color(0xE612151E),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // Chapter Seekbar Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                prevChapter?.let {
                                    currentChapter = it
                                    currentPage = 0
                                }
                            },
                            enabled = prevChapter != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Prev Ch", fontSize = 12.sp)
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = "${currentPage + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )

                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { value ->
                                val target = value.roundToInt().coerceIn(0, pageCount - 1)
                                currentPage = target
                                scope.launch {
                                    if (effectiveMode.isWebtoonStrip) {
                                        webtoonListState.scrollToItem(target)
                                    } else {
                                        pagerState.scrollToPage(target)
                                    }
                                }
                            },
                            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )

                        Text(
                            text = "$pageCount",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )

                        Spacer(Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                nextChapter?.let {
                                    currentChapter = it
                                    currentPage = 0
                                }
                            },
                            enabled = nextChapter != null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Next Ch", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Quick Reader Action Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = {
                                val modes = ReadingMode.entries
                                val nextMode = modes[(modes.indexOf(effectiveMode) + 1) % modes.size]
                                stateStore.setMangaReadingModeOverride(manga.id, nextMode)
                            },
                            label = { Text(effectiveMode.displayName, fontSize = 11.sp) },
                        )

                        FilterChip(
                            selected = cropBorders,
                            onClick = { stateStore.setCropBorders(!cropBorders) },
                            label = { Text(if (cropBorders) "Crop: ON" else "Crop: OFF", fontSize = 11.sp) },
                        )

                        FilterChip(
                            selected = showTapZoneOverlay,
                            onClick = { showTapZoneOverlay = !showTapZoneOverlay },
                            label = { Text("Tap Zones", fontSize = 11.sp) },
                        )

                        Button(
                            onClick = { showSettingsSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Reader Settings", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Full Reader Settings Modal Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Reader Settings & Image Pipeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "Reading Mode (Per-Manga Override)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReadingMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { stateStore.setMangaReadingModeOverride(manga.id, mode) }
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(mode.displayName)
                            if (effectiveMode == mode) {
                                Text(
                                    text = "Active",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Image Processing Toggles
                ReaderToggleRow(
                    title = "Crop whitespace borders",
                    subtitle = "Automatically trim outer margins via iOS CoreGraphics",
                    checked = cropBorders,
                    onCheckedChange = stateStore::setCropBorders,
                )
                ReaderToggleRow(
                    title = "Split tall images (Webtoon optimization)",
                    subtitle = "Slices >4096px vertical strips for smooth 120Hz ProMotion scrolling",
                    checked = splitTallImages,
                    onCheckedChange = stateStore::setSplitTallImages,
                )
                ReaderToggleRow(
                    title = "Split wide double-page spreads",
                    subtitle = "Splits two-page manga spreads into individual pages",
                    checked = dualPageSplit,
                    onCheckedChange = stateStore::setDualPageSplit,
                )
                ReaderToggleRow(
                    title = "Keep screen on while reading",
                    subtitle = "Disables iOS idle sleep timer",
                    checked = keepScreenOn,
                    onCheckedChange = stateStore::setKeepScreenOn,
                )

                HorizontalDivider()

                // Color Filters & Brightness
                Text("Color Filter", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReaderColorFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = colorFilter == filter,
                            onClick = { stateStore.setReaderColorFilter(filter) },
                            label = { Text(filter.displayName, fontSize = 11.sp) },
                        )
                    }
                }

                Text("Custom Brightness Dimmer: ${(customBrightness * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = customBrightness,
                    onValueChange = stateStore::setCustomBrightness,
                    valueRange = -0.70f..0f,
                )

                Text("Tap Zone Layout: ${tapZoneMode.displayName}", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TapZoneMode.entries.take(3).forEach { mode ->
                        FilterChip(
                            selected = tapZoneMode == mode,
                            onClick = { stateStore.setTapZoneMode(mode) },
                            label = { Text(mode.name, fontSize = 11.sp) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ReaderToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RenderedManhwaPagePanel(
    mangaTitle: String,
    chapterName: String,
    chapterNumber: Float,
    pageIndex: Int,
    totalPages: Int,
    isWebtoonStrip: Boolean,
    cropBorders: Boolean,
    splitTallImages: Boolean,
) {
    val paletteColors = remember(pageIndex, chapterNumber) {
        val hueShift = ((pageIndex * 37 + chapterNumber.toInt() * 19) % 360)
        listOf(
            Color(0xFF141E30),
            Color(0xFF243B55),
            Color(
                red = ((hueShift % 120) + 40) / 255f,
                green = (((hueShift + 60) % 120) + 30) / 255f,
                blue = (((hueShift + 140) % 150) + 70) / 255f,
                alpha = 1f,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isWebtoonStrip) 0.58f else 0.70f)
            .padding(if (cropBorders) 0.dp else 6.dp)
            .background(Brush.verticalGradient(paletteColors)),
    ) {
        // Stylized Manhwa / Webtoon Comic Panel Art Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Panel frames
            drawRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(w * 0.06f, h * 0.05f),
                size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.40f),
            )
            drawRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset(w * 0.06f, h * 0.49f),
                size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.44f),
            )
            // Dynamic action speed-lines
            for (i in 1..8) {
                val y = h * (i / 9f)
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, y),
                    end = Offset(w, y + 35f),
                    strokeWidth = 2f,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "$mangaTitle • Ch. ${chapterNumber.toInt()}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.68f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "PAGE ${pageIndex + 1} OF $totalPages",
                        color = Color(0xFF82B1FF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = chapterName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildString {
                            append(if (isWebtoonStrip) "Mode: Seamless Webtoon Strip" else "Mode: Paged Tiled Viewer")
                            if (splitTallImages) append(" • Tall Slice Ready")
                            if (cropBorders) append(" • Border Cropped")
                        },
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = "#${pageIndex + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterTransitionBanner(
    currentChapter: ChapterEntry,
    prevChapter: ChapterEntry?,
    nextChapter: ChapterEntry?,
    onSelectChapter: (ChapterEntry) -> Unit,
) {
    Surface(
        color = Color(0xFF151924),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Finished: ${currentChapter.name}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            if (nextChapter != null) {
                Text(
                    text = "Next: ${nextChapter.name}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { onSelectChapter(nextChapter) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue to Next Chapter →")
                }
            } else {
                Text(
                    text = "There's no next chapter — you're all caught up!",
                    color = Color(0xFF82B1FF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (prevChapter != null) {
                OutlinedButton(
                    onClick = { onSelectChapter(prevChapter) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("← Previous: ${prevChapter.name}")
                }
            }
        }
    }
}

