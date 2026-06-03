package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.shape.CircleShape
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.reader.ImageProcessingUtils
import kotlinx.coroutines.launch

enum class RenderTheme {
    DAY,
    SEPIA,
    NIGHT,
    DARK_ROOM
}

enum class TapAction {
    NEXT_PAGE,
    PREV_PAGE,
    OPEN_MENU,
    TOGGLE_UI,
    BOOKMARK,
    SEARCH,
    ZOOM_IN,
    ZOOM_OUT,
    NO_ACTION
}

@Composable
fun ReaderPageItem(
    pageIndex: Int,
    viewModel: AppViewModel,
    onTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    zoomLocked: Boolean,
    brightness: Float,
    contrast: Float,
    isNightMode: Boolean,
    isColorInverted: Boolean,
    blueLightReduction: Float,
    themeMode: String,
    cropMode: Int,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    externalScale: Float = 1f,
    externalOffset: Offset = Offset.Zero,
    onScaleChange: (Float) -> Unit = {},
    onOffsetChange: (Offset) -> Unit = {}
) {
    val context = LocalContext.current
    val currentEngine by viewModel.currentEngine.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()

    val scale = externalScale
    val offset = externalOffset

    val setScale: (Float) -> Unit = onScaleChange
    val setOffset: (Offset) -> Unit = onOffsetChange
    
    val renderBitmap by produceState<Bitmap?>(
        initialValue = null,
        pageIndex, currentEngine, brightness, contrast, isNightMode, isColorInverted, themeMode, blueLightReduction, cropMode, cropLeft, cropTop, cropRight, cropBottom
    ) {
        val engine = currentEngine
        if (engine != null && pageIndex in 0 until totalPages) {
            val cropSettings = com.example.reader.CropSettings(
                cropMode = cropMode,
                left = cropLeft,
                top = cropTop,
                right = cropRight,
                bottom = cropBottom
            )
            val processed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val baseBmp = engine.renderPage(pageIndex, 1100, 1500, cropSettings)
                if (baseBmp != null) {
                    ImageProcessingUtils.processBitmap(
                        src = baseBmp,
                        brightness = brightness,
                        contrast = contrast,
                        isColorInverted = isColorInverted,
                        isNightMode = isNightMode,
                        bgColor = viewModel.getThemeBgColor(themeMode),
                        textColor = viewModel.getThemeTextColor(themeMode),
                        blueLightReduction = blueLightReduction,
                        isReflowable = (engine is com.example.reader.BaseReflowBookEngine || engine is com.example.reader.FallbackBookEngine)
                    )
                } else {
                    null
                }
            }
            value = processed
        }
    }

    var pageModifier = Modifier.fillMaxSize()

    if (scale <= 1f) {
        pageModifier = pageModifier.pointerInput(pageIndex) {
            awaitPointerEventScope {
                var initialDistance = -1f
                while (true) {
                    val event = awaitPointerEvent()
                    val activePointers = event.changes.filter { it.pressed }
                    if (activePointers.size >= 2) {
                        event.changes.forEach { it.consume() }
                        val p1 = activePointers[0].position
                        val p2 = activePointers[1].position
                        val currentDistance = (p1 - p2).getDistance()
                        if (initialDistance < 0f) {
                            initialDistance = currentDistance
                        } else if (initialDistance > 0f) {
                            val zoomRatio = currentDistance / initialDistance
                            if (zoomRatio > 1.02f || zoomRatio < 0.98f) {
                                setScale((scale * zoomRatio).coerceIn(1f, 5f))
                            }
                        }
                    } else {
                        initialDistance = -1f
                    }
                }
            }
        }
    }

    pageModifier = pageModifier.pointerInput(pageIndex) {
        detectTapGestures(
            onTap = { tapOffset -> onTap(tapOffset, size.width.toFloat(), size.height.toFloat()) },
            onDoubleTap = { onDoubleTap(it) }
        )
    }

    if (scale > 1f) {
        pageModifier = pageModifier.pointerInput(pageIndex) {
            detectTransformGestures { _, pan, zoomChange, _ ->
                val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                setScale(nextScale)
                if (nextScale > 1f) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val maxTranslationX = ((w * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                    val maxTranslationY = ((h * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                    val newOffset = offset + pan
                    val clampedX = newOffset.x.coerceIn(-maxTranslationX, maxTranslationX)
                    val clampedY = newOffset.y.coerceIn(-maxTranslationY, maxTranslationY)
                    setOffset(Offset(clampedX, clampedY))
                } else {
                    setOffset(Offset.Zero)
                }
            }
        }
    }

    Box(
        modifier = pageModifier,
        contentAlignment = Alignment.Center
    ) {
        if (renderBitmap != null) {
            Image(
                bitmap = renderBitmap!!.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier
                    .fillMaxSize(0.99f)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window
    val coroutineScope = rememberCoroutineScope()

    // 1. View Model States
    val currentBookRecord by viewModel.currentBookRecord.collectAsState()
    val currentEngine by viewModel.currentEngine.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val isToolbarVisible by viewModel.isToolbarVisible.collectAsState()

    val readingMode by viewModel.readingMode.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val isColorInverted by viewModel.isColorInverted.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val renderQuality by viewModel.renderQuality.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val zoomLocked by viewModel.zoomLocked.collectAsState()

    // Crops
    val cropMode by viewModel.cropMode.collectAsState()
    val cropLeft by viewModel.cropLeft.collectAsState()
    val cropTop by viewModel.cropTop.collectAsState()
    val cropRight by viewModel.cropRight.collectAsState()
    val cropBottom by viewModel.cropBottom.collectAsState()

    // Bookmarks and Inner Searches
    val activeBookmarks by viewModel.activeBookmarks.collectAsState()
    val activeAnnotations by viewModel.activeAnnotations.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchIndex by viewModel.searchIndex.collectAsState()

    val fontSizeMultiplier by viewModel.fontSizeMultiplier.collectAsState()
    val lineSpacingMultiplier by viewModel.lineSpacingMultiplier.collectAsState()
    val paragraphSpacingMultiplier by viewModel.paragraphSpacingMultiplier.collectAsState()
    val marginPercent by viewModel.marginPercent.collectAsState()
    val textAlignment by viewModel.textAlignment.collectAsState()
    val fontWeight by viewModel.fontWeight.collectAsState()
    val fontType by viewModel.fontType.collectAsState()
    val screenOrientation by viewModel.screenOrientation.collectAsState()
    val fullScreenMode by viewModel.fullScreenMode.collectAsState()

    // Collect theme, color customization, automatic switching, blue-light and brightness settings
    val themeMode by viewModel.themeMode.collectAsState()
    val themeCustomBgColor by viewModel.themeCustomBgColor.collectAsState()
    val themeCustomTextColor by viewModel.themeCustomTextColor.collectAsState()
    val autoDayNight by viewModel.autoDayNight.collectAsState()
    val blueLightReduction by viewModel.blueLightReduction.collectAsState()
    val readerBrightness by viewModel.readerBrightness.collectAsState()

    // Apply hardware screen brightness inside reader dynamically
    LaunchedEffect(readerBrightness) {
        val window = activity?.window ?: return@LaunchedEffect
        val layoutParams = window.attributes
        // Map 0f-1f slider to 0.01f-1.0f screen brightness
        layoutParams.screenBrightness = readerBrightness.coerceIn(0.01f, 1.0f)
        window.attributes = layoutParams
    }

    LaunchedEffect(screenOrientation) {
        activity?.requestedOrientation = when (screenOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(fullScreenMode) {
        val decorView = window?.decorView ?: return@LaunchedEffect
        if (fullScreenMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = decorView.windowInsetsController
                controller?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = decorView.windowInsetsController
                controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // 2. Local Retro Reader States
    var isLeftDrawerOpen by remember { mutableStateOf(false) }
    var isRightDrawerOpen by remember { mutableStateOf(false) }

    // Dialog flags
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showTapsDialog by remember { mutableStateOf(false) }
    var showKeysDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteInputText by remember { mutableStateOf("") }

    var showAllAnnotationsDialog by remember { mutableStateOf(false) }
    var annotationSearchQuery by remember { mutableStateOf("") }

    // State for creating highlights/annotations
    var annoSelectedType by remember { mutableStateOf("HIGHLIGHT") } // "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH"
    var annoSelectedColorName by remember { mutableStateOf("Yellow") }
    var annoTextSnippet by remember { mutableStateOf("") }
    var annoCommentText by remember { mutableStateOf("") }

    // Table of contents dynamic selection
    var showTocDialog by remember { mutableStateOf(false) }
    var engineToc by remember { mutableStateOf<List<com.example.reader.TOCItem>>(emptyList()) }

    var showFootnoteDialog by remember { mutableStateOf(false) }
    var activeFootnoteContent by remember { mutableStateOf<String?>(null) }

    var showMoreTelemetry by remember { mutableStateOf(false) }
    var speedSelection by remember { mutableStateOf(90) } // reading speed seconds per page

    // 3. 3x3 TAP ZONES ASSIGNMENTS
    var tapZones by remember {
        mutableStateOf(
            mapOf(
                "Top Left" to TapAction.PREV_PAGE,
                "Top Center" to TapAction.TOGGLE_UI,
                "Top Right" to TapAction.NEXT_PAGE,
                "Middle Left" to TapAction.PREV_PAGE,
                "Center" to TapAction.OPEN_MENU,
                "Middle Right" to TapAction.NEXT_PAGE,
                "Bottom Left" to TapAction.PREV_PAGE,
                "Bottom Center" to TapAction.BOOKMARK,
                "Bottom Right" to TapAction.NEXT_PAGE
            )
        )
    }

    // Hardware buttons bindings mapping state
    var volumeUpAction by remember { mutableStateOf(TapAction.PREV_PAGE) }
    var volumeDownAction by remember { mutableStateOf(TapAction.NEXT_PAGE) }

    // Search and render content states
    var searchQuery by remember { mutableStateOf("") }
    var isCaseSensitive by remember { mutableStateOf(false) }
    var isWholeWord by remember { mutableStateOf(false) }
    var isHighlightMatches by remember { mutableStateOf(true) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val pageScales = remember { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
    val pageOffsets = remember { androidx.compose.runtime.mutableStateMapOf<Int, Offset>() }

    // Collapsible drawer panel item toggles
    var openGoTo by remember { mutableStateOf(false) }
    var openFindText by remember { mutableStateOf(false) }
    var openShow by remember { mutableStateOf(false) }
    var openTools by remember { mutableStateOf(false) }
    var openSendTo by remember { mutableStateOf(false) }
    var openRenderingMode by remember { mutableStateOf(false) }
    var openBookSettings by remember { mutableStateOf(false) }
    var openReflowSettings by remember { mutableStateOf(false) }
    var openTtsSettings by remember { mutableStateOf(false) }
    var openViewMode by remember { mutableStateOf(false) }
    var openOrientation by remember { mutableStateOf(false) }
    var openCommonSettings by remember { mutableStateOf(false) }
    var openTemplates by remember { mutableStateOf(false) }

    // Configure theme variables based on rendering selections
    val currentBgColor = Color(viewModel.getThemeBgColor(themeMode))
    val currentTextColor = Color(viewModel.getThemeTextColor(themeMode))

    // Page scaling & pan states tracking is now decentralized in ReaderPageItem

    LaunchedEffect(currentEngine) {
        currentEngine?.let {
            engineToc = it.getTableOfContents()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            viewModel.addReadingTime(1)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // MAIN VIEWPORT SCENARIO
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = currentBgColor
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CENTRAL CANVAS WORKSPACE
                val pagerState = rememberPagerState(initialPage = currentPage) { totalPages }
                val currentActiveScale = if (zoomLocked) scale else (pageScales[pagerState.currentPage] ?: 1f)

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != currentPage) {
                        viewModel.jumpToPage(pagerState.currentPage)
                    }
                }

                LaunchedEffect(currentPage) {
                    if (pagerState.currentPage != currentPage) {
                        pagerState.scrollToPage(currentPage)
                    }
                }

                val onTap: (Offset, Float, Float) -> Unit = { tapOffset, screenWidthPx, screenHeightPx ->
                    val xRatio = tapOffset.x / screenWidthPx
                    val yRatio = tapOffset.y / screenHeightPx

                    // Intercept links or footnotes clicks
                    val pageLinks = currentEngine?.getPageLinks(currentPage) ?: emptyList()
                    val clickedLink = pageLinks.firstOrNull { link ->
                        xRatio in link.rect.left..link.rect.right && yRatio in link.rect.top..link.rect.bottom
                    }

                    if (clickedLink != null) {
                        if (clickedLink.isFootnote) {
                            activeFootnoteContent = clickedLink.footnoteText
                            showFootnoteDialog = true
                        } else {
                            val target = clickedLink.target
                            if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) || target.startsWith("www.", ignoreCase = true)) {
                                Toast.makeText(context, "Opening Hyperlink: $target", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target))
                                    context.startActivity(intent)
                                } catch (e: Exception) { /* ignore */ }
                            } else if (target.startsWith("#footnote-") || target.startsWith("#note")) {
                                activeFootnoteContent = clickedLink.footnoteText
                                showFootnoteDialog = true
                            } else {
                                val targetPageIndex = engineToc.firstOrNull {
                                    it.title.contains(target.replace("#", ""), ignoreCase = true)
                                }?.pageIndex
                                if (targetPageIndex != null) {
                                    viewModel.jumpToPage(targetPageIndex)
                                } else {
                                    val idx = target.toIntOrNull()
                                    if (idx != null) viewModel.jumpToPage(idx.coerceIn(0, totalPages - 1))
                                }
                            }
                        }
                    } else {
                        val col = if (xRatio < 0.33f) 0 else if (xRatio > 0.66f) 2 else 1
                        val row = if (yRatio < 0.33f) 0 else if (yRatio > 0.66f) 2 else 1
                        val zoneName = when {
                            row == 0 && col == 0 -> "Top Left"
                            row == 0 && col == 1 -> "Top Center"
                            row == 0 && col == 2 -> "Top Right"
                            row == 1 && col == 0 -> "Middle Left"
                            row == 1 && col == 1 -> "Center"
                            row == 1 && col == 2 -> "Middle Right"
                            row == 2 && col == 0 -> "Bottom Left"
                            row == 2 && col == 1 -> "Bottom Center"
                            row == 2 && col == 2 -> "Bottom Right"
                            else -> "Center"
                        }
                        val mappedAction = tapZones[zoneName] ?: TapAction.TOGGLE_UI
                        when (mappedAction) {
                            TapAction.NEXT_PAGE -> viewModel.nextPage()
                            TapAction.PREV_PAGE -> viewModel.prevPage()
                            TapAction.OPEN_MENU -> isRightDrawerOpen = !isRightDrawerOpen
                            TapAction.TOGGLE_UI -> viewModel.toggleToolbar()
                            TapAction.BOOKMARK -> {
                                viewModel.addBookmarkAtCurrent()
                                Toast.makeText(context, "Added Bookmark Coordinate!", Toast.LENGTH_SHORT).show()
                            }
                            TapAction.SEARCH -> showSearchDialog = true
                            TapAction.ZOOM_IN -> {
                                val nextScale = (currentActiveScale + 0.3f).coerceAtMost(5f)
                                if (zoomLocked) {
                                    scale = nextScale
                                } else {
                                    pageScales[pagerState.currentPage] = nextScale
                                }
                            }
                            TapAction.ZOOM_OUT -> {
                                val nextScale = (currentActiveScale - 0.3f).coerceAtLeast(1f)
                                if (zoomLocked) {
                                    scale = nextScale
                                    if (nextScale <= 1f) {
                                        offset = Offset.Zero
                                    }
                                } else {
                                    pageScales[pagerState.currentPage] = nextScale
                                    if (nextScale <= 1f) {
                                        pageOffsets[pagerState.currentPage] = Offset.Zero
                                    }
                                }
                            }
                            TapAction.NO_ACTION -> {}
                        }
                    }
                }

                val onDoubleTap: (Offset) -> Unit = {
                    isRightDrawerOpen = !isRightDrawerOpen
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("reader_viewport_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    if (readingMode == 1) {
                        // Vertical Touch Paging
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = currentActiveScale <= 1f,
                            beyondViewportPageCount = 1
                        ) { pageIdx ->
                            ReaderPageItem(
                                pageIndex = pageIdx,
                                viewModel = viewModel,
                                onTap = onTap,
                                onDoubleTap = onDoubleTap,
                                zoomLocked = zoomLocked,
                                brightness = brightness,
                                contrast = contrast,
                                isNightMode = isNightMode,
                                isColorInverted = isColorInverted,
                                blueLightReduction = blueLightReduction,
                                themeMode = themeMode,
                                cropMode = cropMode,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                externalScale = if (zoomLocked) scale else (pageScales[pageIdx] ?: 1f),
                                externalOffset = if (zoomLocked) offset else (pageOffsets[pageIdx] ?: Offset.Zero),
                                onScaleChange = { newScale ->
                                    if (zoomLocked) {
                                        scale = newScale
                                    } else {
                                        pageScales[pageIdx] = newScale
                                    }
                                },
                                onOffsetChange = { newOffset ->
                                    if (zoomLocked) {
                                        offset = newOffset
                                    } else {
                                        pageOffsets[pageIdx] = newOffset
                                    }
                                }
                            )
                        }
                    } else {
                        // Horizontal Touch Paging (Default)
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = currentActiveScale <= 1f,
                            beyondViewportPageCount = 1
                        ) { pageIdx ->
                            ReaderPageItem(
                                pageIndex = pageIdx,
                                viewModel = viewModel,
                                onTap = onTap,
                                onDoubleTap = onDoubleTap,
                                zoomLocked = zoomLocked,
                                brightness = brightness,
                                contrast = contrast,
                                isNightMode = isNightMode,
                                isColorInverted = isColorInverted,
                                blueLightReduction = blueLightReduction,
                                themeMode = themeMode,
                                cropMode = cropMode,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                externalScale = if (zoomLocked) scale else (pageScales[pageIdx] ?: 1f),
                                externalOffset = if (zoomLocked) offset else (pageOffsets[pageIdx] ?: Offset.Zero),
                                onScaleChange = { newScale ->
                                    if (zoomLocked) {
                                        scale = newScale
                                    } else {
                                        pageScales[pageIdx] = newScale
                                    }
                                },
                                onOffsetChange = { newOffset ->
                                    if (zoomLocked) {
                                        offset = newOffset
                                    } else {
                                        pageOffsets[pageIdx] = newOffset
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom Search Badge Tracker
                if (searchResults.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 85.dp)
                            .background(Color.Black.copy(alpha = 0.9f))
                            .border(BorderStroke(1.dp, Color(0xFF333333)))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.prevSearchResult() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Prev result", tint = Color.White)
                                }
                                Text(
                                    text = "Index match ${searchIndex + 1} of ${searchResults.size}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(onClick = { viewModel.nextSearchResult() }) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next result", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Done search", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // FIXED PURE BLACK TOP HEADER
                if (isToolbarVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { isLeftDrawerOpen = true },
                                modifier = Modifier.testTag("left_sidebar_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Open System Navigation Menu", tint = Color(0xFF00E5FF))
                            }

                            // Slate classic layout centered string "(page/total) Book Title"
                            val pageLabel = "(${currentPage + 1}/$totalPages)"
                            val bookTitle = currentBookRecord?.title ?: "Document"
                            Text(
                                text = "$pageLabel $bookTitle",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                textAlign = TextAlign.Center
                            )

                            IconButton(
                                onClick = { isRightDrawerOpen = true },
                                modifier = Modifier.testTag("right_sidebar_button")
                             ) {
                                Icon(Icons.Default.Tune, contentDescription = "Open Reading Style & Format Menu", tint = Color(0xFF00E5FF))
                            }

                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.Close, contentDescription = "Exit to bookshelf", tint = Color.White)
                            }
                        }
                    }
                }


            }
            
            // ===============================================
            // READER OVERLAY MENU SIDE PANEL (Drawer overlay)
            // ===============================================
            // ===============================================
            // LEFT SIDEBAR: SYSTEM & NAVIGATION
            // ===============================================
            AnimatedVisibility(
                visible = isLeftDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).align(Alignment.CenterStart).zIndex(1001f)
            ) {
            Surface(
                color = Color(0xFF161618), // Dark Charcoal Background
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxHeight().fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header inside drawer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "READER PRESETS PANEL",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    HorizontalDivider(color = Color(0xFF444444))

                    // Collapsible Groups Lazy Scroll (DENSE, RETRO, NO MODERN SHAPES)
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        // 1. Go To Page
                        item {
                            DrawerHeaderItem("Go To Page", openGoTo) { openGoTo = !openGoTo }
                            if (openGoTo) {
                                DrawerSelectionBlock {
                                    var pageTextVal by remember { mutableStateOf("") }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = pageTextVal,
                                            onValueChange = { pageTextVal = it },
                                            placeholder = { Text("Page #", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF00E5FF)
                                            ),
                                            modifier = Modifier.size(80.dp, 50.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            shape = RoundedCornerShape(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2F)),
                                            onClick = {
                                                val target = pageTextVal.toIntOrNull()
                                                if (target != null && target in 1..totalPages) {
                                                    viewModel.jumpToPage(target - 1)
                                                    isLeftDrawerOpen = false
                                                }
                                            }
                                        ) {
                                            Text("GO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Find Text
                        item {
                            DrawerHeaderItem("Find Text", openFindText) { openFindText = !openFindText }
                            if (openFindText) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Query text...", fontSize = 11.sp, color = Color.Gray) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF00E5FF)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isCaseSensitive,
                                                onCheckedChange = { isCaseSensitive = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                            )
                                            Text("Case sensitive", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isWholeWord,
                                                onCheckedChange = { isWholeWord = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                            )
                                            Text("Whole word match", color = Color.LightGray, fontSize = 11.sp)
                                        }

                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                            onClick = {
                                                if (searchQuery.isNotBlank()) {
                                                    viewModel.executeSearch(searchQuery)
                                                    isLeftDrawerOpen = false
                                                }
                                            }
                                        ) {
                                            Text("EXECUTE SEARCH", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Show (Bookmarks, TOC, Annotations, Reading Stats)
                        item {
                            DrawerHeaderItem("Show Metadata & Notes", openShow) { openShow = !openShow }
                            if (openShow) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextActionItem("View Book Highlights & Notes") {
                                            showAllAnnotationsDialog = true
                                            isLeftDrawerOpen = false
                                        }
                                        TextActionItem("View Document Bookmarks") {
                                            showBookmarksDialog = true
                                            isLeftDrawerOpen = false
                                        }
                                        TextActionItem("Document Table Of Contents") {
                                            showTocDialog = true
                                            isLeftDrawerOpen = false
                                        }
                                        TextActionItem("File Technical Metadata") {
                                            showInfoDialog = true
                                            isLeftDrawerOpen = false
                                        }
                                    }
                                }
                            }
                        }




                        // 4. Tools (Annotate selection, Export Notes)
                        item {
                            DrawerHeaderItem("Advanced Menu Tools", openTools) { openTools = !openTools }
                            if (openTools) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextActionItem("Add active paper note") {
                                            showAddNoteDialog = true
                                            isLeftDrawerOpen = false
                                        }
                                        TextActionItem("Open selected with dictionary") {
                                            Toast.makeText(context, "Dictionary databases syncing...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Send To
                        item {
                            DrawerHeaderItem("Send To / Share", openSendTo) { openSendTo = !openSendTo }
                            if (openSendTo) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextActionItem("Share Page Image") {
                                            Toast.makeText(context, "Generating page image...", Toast.LENGTH_SHORT).show()
                                        }
                                        TextActionItem("Share PDF document") {
                                            Toast.makeText(context, "Invoking sharing transport...", Toast.LENGTH_SHORT).show()
                                        }
                                        TextActionItem("Native Printer transport") {
                                            Toast.makeText(context, "Contacting local print servers...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Book Settings
                        item {
                            DrawerHeaderItem("Book Advanced Settings", openBookSettings) { openBookSettings = !openBookSettings }
                            if (openBookSettings) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = zoomLocked,
                                                onCheckedChange = { viewModel.zoomLocked.value = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Lock Page Scale factor limits", color = Color.White, fontSize = 11.sp)
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Manual Crop: ${(cropLeft * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = cropLeft,
                                                onValueChange = {
                                                    viewModel.updateCropSettings(if (it > 0f) 2 else 0, it, it, it, it)
                                                },
                                                valueRange = 0f..0.2f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Rendering Brightness: ${(brightness * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = brightness,
                                                onValueChange = { viewModel.updateImageAdjustment(it, contrast, isNightMode, isColorInverted) },
                                                valueRange = 0.5f..1.8f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Rendering Contrast: ${(contrast * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = contrast,
                                                onValueChange = { viewModel.updateImageAdjustment(brightness, it, isNightMode, isColorInverted) },
                                                valueRange = 0.5f..2.5f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 8. View Mode Settings
                        item {
                            DrawerHeaderItem("View Mode Settings", openViewMode) { openViewMode = !openViewMode }
                            if (openViewMode) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        val modes = listOf(
                                            "Horizontal Paging (Swiping)",
                                            "Vertical Paging (Swiping)"
                                        )
                                        modes.forEachIndexed { i, m ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                modifier = Modifier.fillMaxWidth().clickable { viewModel.updateReadingMode(i) }
                                            ) {
                                                RadioButton(
                                                    selected = (readingMode == i),
                                                    onClick = { viewModel.updateReadingMode(i) },
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(m, color = Color.White, fontSize = 11.sp)
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Checkbox(
                                                checked = fullScreenMode,
                                                onCheckedChange = { viewModel.updateFullScreenMode(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Force Full-Screen Reading Mode", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 8b. Typography & Text Formatting Settings
                        item {
                            DrawerHeaderItem("Typography & Text Formatting", openReflowSettings) { openReflowSettings = !openReflowSettings }
                            if (openReflowSettings) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // 1. Font Family
                                        Column {
                                            Text("Font Style Family", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val fontFamilies = listOf("Serif", "Sans-Serif", "Monospace", "Dyslexic", "Custom")
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                fontFamilies.forEach { family ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .border(
                                                                1.dp,
                                                                if (fontType == family) Color(0xFF00E5FF) else Color(0xFF444444)
                                                            )
                                                            .background(if (fontType == family) Color(0xFF333333) else Color.Transparent)
                                                            .clickable { viewModel.updateFontType(family) }
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(family, color = Color.White, fontSize = 10.sp, maxLines = 1)
                                                    }
                                                }
                                            }
                                        }

                                        // 2. Custom Font Upload Button
                                        if (fontType == "Custom") {
                                            val launcher = rememberLauncherForActivityResult(
                                                contract = ActivityResultContracts.GetContent()
                                            ) { uri ->
                                                if (uri != null) {
                                                    viewModel.uploadCustomFont(uri)
                                                }
                                            }
                                            Button(
                                                onClick = { launcher.launch("font/ttf") },
                                                shape = RoundedCornerShape(0.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = Color.White),
                                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00E5FF))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Pick Custom TTF Font", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        // 3. Text Alignment (Left, Center, Right, Justified)
                                        Column {
                                            Text("Text Alignment Style", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val alignments = listOf("Left", "Center", "Right", "Justify")
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                alignments.forEachIndexed { idx, align ->
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .border(
                                                                1.dp,
                                                                if (textAlignment == idx) Color(0xFF00E5FF) else Color(0xFF444444)
                                                            )
                                                            .background(if (textAlignment == idx) Color(0xFF333333) else Color.Transparent)
                                                            .clickable { viewModel.updateTextAlignment(idx) }
                                                            .padding(vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(align, color = Color.White, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        // 4. Font Weight
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Checkbox(
                                                checked = fontWeight == 1,
                                                onCheckedChange = { viewModel.updateFontWeight(if (it) 1 else 0) },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Force Bold layout weight", color = Color.White, fontSize = 11.sp)
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        // 5. Font Size multiplier
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Font Scale Size: ${(fontSizeMultiplier * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = fontSizeMultiplier,
                                                onValueChange = { viewModel.updateFontSize(it) },
                                                valueRange = 0.6f..2.5f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        // 6. Line height / spacing multiplier
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Line Spacing Height: ${(lineSpacingMultiplier * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = lineSpacingMultiplier,
                                                onValueChange = { viewModel.updateLineSpacing(it) },
                                                valueRange = 0.7f..2.5f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        // 7. Paragraph extra spacing multiplier
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Paragraph spacing: ${(paragraphSpacingMultiplier * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = paragraphSpacingMultiplier,
                                                onValueChange = { viewModel.updateParagraphSpacing(it) },
                                                valueRange = 0.5f..3.0f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        // 8. Page Margins percent
                                        Column {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Page Margin boundary: ${(marginPercent * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            }
                                            Slider(
                                                value = marginPercent,
                                                onValueChange = { viewModel.updateMarginPercent(it) },
                                                valueRange = 0.02f..0.22f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 8c. Accessibility Assistant Suite Controls
                        item {
                            DrawerHeaderItem("Visual Accessibility Suite", openTtsSettings) { openTtsSettings = !openTtsSettings }
                            if (openTtsSettings) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        // A. Dyslexia-friendly Font Engine Setup
                                        Text(
                                            text = "DYSLEXIA-FRIENDLY LAYOUT",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Dyslexic Typeface & Spacing", color = Color.White, fontSize = 11.sp)
                                            Switch(
                                                checked = fontType.lowercase() == "dyslexic",
                                                onCheckedChange = { active ->
                                                    if (active) {
                                                        viewModel.updateFontType("Dyslexic")
                                                    } else {
                                                        viewModel.updateFontType("Serif")
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                                            )
                                        }
                                        Text(
                                            text = "Applies wide letters, heavy baseline alignment, and balanced visual weights to assist readability filters.",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )

                                        HorizontalDivider(color = Color(0xFF333333))

                                        // D. High Contrast Mode
                                        Text(
                                            text = "HIGH CONTRAST THEME",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Vivid Contrast Theme (Yellow-on-Black)", color = Color.White, fontSize = 11.sp)
                                            Switch(
                                                checked = themeMode == "HIGH_CONTRAST",
                                                onCheckedChange = { active ->
                                                    if (active) {
                                                        viewModel.updateThemeMode("HIGH_CONTRAST")
                                                    } else {
                                                        viewModel.updateThemeMode("DAY")
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        HorizontalDivider(color = Color(0xFF333333))

                                        // E. Large Text Accessibility presets
                                        Text(
                                            text = "LARGE TEXT PROFILE ACCORD",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("X-Large Reading View Preset (180%)", color = Color.White, fontSize = 11.sp)
                                            Switch(
                                                checked = fontSizeMultiplier >= 1.75f,
                                                onCheckedChange = { active ->
                                                    if (active) {
                                                        viewModel.updateFontSize(1.80f)
                                                        viewModel.updateLineSpacing(1.35f)
                                                        viewModel.updateParagraphSpacing(1.35f)
                                                    } else {
                                                        viewModel.updateFontSize(1.00f)
                                                        viewModel.updateLineSpacing(1.00f)
                                                        viewModel.updateParagraphSpacing(1.00f)
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                                            )
                                        }
                                        Text(
                                            text = "Changes screen layout parameters instantly to double text scaling with airy generous spacing between paragraphs.",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 9. Screen Orientation
                        item {
                            DrawerHeaderItem("Screen Rotation Lock", openOrientation) { openOrientation = !openOrientation }
                            if (openOrientation) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val orients = listOf("Force System Default", "Force Portrait Static", "Force Landscape Horizontal")
                                        orients.forEachIndexed { idx, o ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.updateScreenOrientation(idx) }) {
                                                RadioButton(
                                                    selected = screenOrientation == idx,
                                                    onClick = { viewModel.updateScreenOrientation(idx) },
                                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(o, color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 10. Common Settings (Configure Taps, Configure Keys)
                        item {
                            DrawerHeaderItem("Configure Tap Zones & Keys", openCommonSettings) { openCommonSettings = !openCommonSettings }
                            if (openCommonSettings) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextActionItem("Configure 3x3 Tap Zones Map") {
                                            showTapsDialog = true
                                        }
                                        TextActionItem("Configure Keyboard / Volume Keys") {
                                            showKeysDialog = true
                                        }
                                        TextActionItem("Reset Action Command Bars") {
                                            Toast.makeText(context, "Action controllers reset!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        // 11. Setting Templates
                        item {
                            DrawerHeaderItem("Presets Templates Profiles", openTemplates) { openTemplates = !openTemplates }
                            if (openTemplates) {
                                DrawerSelectionBlock {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TextActionItem("Apply: Dense PDF Layout Profile") {
                                            viewModel.updateCropSettings(1, 0f, 0f, 0f, 0f) // Auto Crop
                                            viewModel.updateImageAdjustment(0.95f, 1.4f, false, false)
                                            viewModel.zoomLocked.value = true
                                            Toast.makeText(context, "Pre-set loaded!", Toast.LENGTH_SHORT).show()
                                        }
                                        TextActionItem("Apply: Warm EPUB Flux Profile") {
                                            viewModel.updateThemeMode("SEPIA")
                                            viewModel.updateCropSettings(0, 0f, 0f, 0f, 0f)
                                            viewModel.updateImageAdjustment(1.05f, 0.95f, false, false)
                                            Toast.makeText(context, "Pre-set loaded!", Toast.LENGTH_SHORT).show()
                                        }
                                        TextActionItem("Apply: Heavy Comic Trim Profile") {
                                            viewModel.updateCropSettings(2, 0.08f, 0.08f, 0.08f, 0.08f)
                                            viewModel.updateImageAdjustment(1.0f, 1.5f, false, false)
                                            Toast.makeText(context, "Pre-set loaded!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }

                        // Close button at foot of panel lists
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isLeftDrawerOpen = false }
                                    .padding(vertical = 14.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CLOSE SYSTEM MENU", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().clickable { isLeftDrawerOpen = false }
                                        .padding(vertical = 14.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CLOSE SYSTEM DRAWER", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===============================================
            // RIGHT SIDEBAR: READING STYLE & FORMAT
            // ===============================================
            AnimatedVisibility(
                visible = isRightDrawerOpen,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).align(Alignment.CenterEnd).zIndex(1001f)
            ) {
                Surface(
                    color = Color(0xFF161618),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier.fillMaxHeight().fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().background(Color.Black).padding(16.dp)
                        ) {
                            Text(
                                text = "READING STYLE & FORMAT",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        HorizontalDivider(color = Color(0xFF444444))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            // 1. View Mode
                            item {
                                DrawerHeaderItem("View Mode settings", openViewMode) { openViewMode = !openViewMode }
                                if (openViewMode) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            val modes = listOf("Horizontal Paging", "Vertical Paging")
                                            modes.forEachIndexed { i, m ->
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.updateReadingMode(i) }) {
                                                    RadioButton(selected = readingMode == i, onClick = { viewModel.updateReadingMode(i) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF)))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(m, color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Checkbox(checked = fullScreenMode, onCheckedChange = { viewModel.updateFullScreenMode(it) }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF)))
                                                Text("Full-Screen Mode", color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Themes & Rendering
                            item {
                                DrawerHeaderItem("Visual Themes", openRenderingMode) { openRenderingMode = !openRenderingMode }
                                if (openRenderingMode) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column {
                                                Text("Brightness", color = Color.Gray, fontSize = 10.sp)
                                                Slider(value = readerBrightness, onValueChange = { viewModel.updateReaderBrightness(it) }, valueRange = 0.05f..1.0f, colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF)))
                                            }
                                            Column {
                                                Text("Blue-Light Shield", color = Color.Gray, fontSize = 10.sp)
                                                Slider(value = blueLightReduction, onValueChange = { viewModel.updateBlueLightReduction(it) }, valueRange = 0.0f..1.0f, colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB300)))
                                            }
                                            val themesList = listOf("DAY", "SEPIA", "NIGHT", "AMOLED", "GREEN_PHOSPHOR", "HIGH_CONTRAST", "CUSTOM")
                                            themesList.forEach { mode ->
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.updateThemeMode(mode) }) {
                                                    RadioButton(selected = themeMode == mode, onClick = { viewModel.updateThemeMode(mode) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF)))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(mode, color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Book Settings
                            item {
                                DrawerHeaderItem("Book Adjustments", openBookSettings) { openBookSettings = !openBookSettings }
                                if (openBookSettings) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(checked = zoomLocked, onCheckedChange = { viewModel.zoomLocked.value = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF)))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Lock Page Scale", color = Color.White, fontSize = 11.sp)
                                            }
                                            Column {
                                                Text("Image Brightness: ${(brightness * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                                Slider(value = brightness, onValueChange = { viewModel.updateImageAdjustment(it, contrast, isNightMode, isColorInverted) }, valueRange = 0.5f..1.8f)
                                            }
                                            Column {
                                                Text("Image Contrast: ${(contrast * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                                Slider(value = contrast, onValueChange = { viewModel.updateImageAdjustment(brightness, it, isNightMode, isColorInverted) }, valueRange = 0.5f..2.5f)
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Typography
                            item {
                                DrawerHeaderItem("Typography & Text", openReflowSettings) { openReflowSettings = !openReflowSettings }
                                if (openReflowSettings) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("Font Size: ${(fontSizeMultiplier * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            Slider(value = fontSizeMultiplier, onValueChange = { viewModel.updateFontSize(it) }, valueRange = 0.6f..2.5f)
                                            Text("Line Spacing: ${(lineSpacingMultiplier * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            Slider(value = lineSpacingMultiplier, onValueChange = { viewModel.updateLineSpacing(it) }, valueRange = 0.7f..2.5f)
                                            Text("Margins: ${(marginPercent * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                                            Slider(value = marginPercent, onValueChange = { viewModel.updateMarginPercent(it) }, valueRange = 0.02f..0.22f)
                                        }
                                    }
                                }
                            }

                            // 5. Accessibility
                            item {
                                DrawerHeaderItem("Accessibility Suite", openTtsSettings) { openTtsSettings = !openTtsSettings }
                                if (openTtsSettings) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Dyslexic Font", color = Color.White, fontSize = 11.sp)
                                                Switch(checked = fontType == "Dyslexic", onCheckedChange = { viewModel.updateFontType(if (it) "Dyslexic" else "Serif") }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF)))
                                            }
                                        }
                                    }
                                }
                            }

                            // 6. Orientation
                            item {
                                DrawerHeaderItem("Screen Rotation", openOrientation) { openOrientation = !openOrientation }
                                if (openOrientation) {
                                    DrawerSelectionBlock {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("System", "Portrait", "Landscape").forEachIndexed { idx, o ->
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.updateScreenOrientation(idx) }) {
                                                    RadioButton(selected = screenOrientation == idx, onClick = { viewModel.updateScreenOrientation(idx) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF)))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(o, color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().clickable { isRightDrawerOpen = false }
                                        .padding(vertical = 14.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("CLOSE STYLE DRAWER", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }



        // Backdrop shielding inside reader overlays
        if (isLeftDrawerOpen || isRightDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isLeftDrawerOpen = false; isRightDrawerOpen = false }
                    .zIndex(1000f)
            )
        }

        // ===============================================
        // MAPPED ADVANCED ACTION DIALOGS
        // ===============================================

        // 1. Saved Bookmarks popup
        if (showBookmarksDialog) {
            AlertDialog(
                onDismissRequest = { showBookmarksDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page Coordinates Linker", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.addBookmarkAtCurrent() }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Bookmark", tint = Color(0xFF00E5FF))
                        }
                    }
                },
                text = {
                    Box(modifier = Modifier.heightIn(max = 280.dp).fillMaxWidth()) {
                        if (activeBookmarks.isEmpty()) {
                            Text(
                                "No registered coordinate pointers. Tap '+' to link active page index.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn {
                                items(activeBookmarks) { mark ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.jumpToPage(mark.page)
                                                showBookmarksDialog = false
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(mark.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Linked coordinate: Page ${mark.page + 1}", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        IconButton(onClick = { viewModel.deleteBookmark(mark.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete bookmark", tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFF333333))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBookmarksDialog = false }) {
                        Text("Dismiss", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        // 2. Dynamic Chapters Table Of Contents Dialogue
        if (showTocDialog) {
            AlertDialog(
                onDismissRequest = { showTocDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = { Text("Ebook Table Of Chapters", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        if (engineToc.isEmpty()) {
                            Text("No sections or chapters available in this file style.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(16.dp))
                        } else {
                            engineToc.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.jumpToPage(item.pageIndex.coerceIn(0, totalPages - 1))
                                            showTocDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val indents = "   ".repeat(item.level)
                                    Text("$indents${item.title}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("Page ${item.pageIndex + 1}", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                HorizontalDivider(color = Color(0xFF2B2B2F))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTocDialog = false }) {
                        Text("Dismiss", color = Color.Gray)
                    }
                }
            )
        }

        // 3. Technical Book Metadata Details Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = { Text("Technical Metadata Details", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaTextRow("Title", currentBookRecord?.title ?: "File")
                        MetaTextRow("System Address Path", currentBookRecord?.path ?: "Unknown location")
                        MetaTextRow("Virtual Reader Code", currentEngine?.javaClass?.simpleName ?: "PdfEngine")
                        MetaTextRow("Total Page Blocks", "$totalPages sheets")
                        MetaTextRow("Allocated Memory Buffers", "64 MB")
                        MetaTextRow("DPI Render Matrix", "300 DPI")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Dismiss", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        // 4. Tap Zones Assignment Grid Configurer (3x3 beautiful overlay dialog)
        if (showTapsDialog) {
            AlertDialog(
                onDismissRequest = { showTapsDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = { Text("Map active Tap Zones (3x3 grid layout)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                    ) {
                        Text("Select screen zone triggers below to remap actions:", color = Color.Gray, fontSize = 11.sp)

                        // 3x3 layout of configurations
                        val gridData = listOf(
                            listOf("Top Left", "Top Center", "Top Right"),
                            listOf("Middle Left", "Center", "Middle Right"),
                            listOf("Bottom Left", "Bottom Center", "Bottom Right")
                        )

                        gridData.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { cellName ->
                                    val currentAction = tapZones[cellName] ?: TapAction.NO_ACTION
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, Color(0xFF444444)))
                                            .background(Color(0xFF222225))
                                            .clickable {
                                                // Cycle through available actions for quick mapping!
                                                val nextAct = when (currentAction) {
                                                    TapAction.NEXT_PAGE -> TapAction.PREV_PAGE
                                                    TapAction.PREV_PAGE -> TapAction.TOGGLE_UI
                                                    TapAction.TOGGLE_UI -> TapAction.OPEN_MENU
                                                    TapAction.OPEN_MENU -> TapAction.BOOKMARK
                                                    TapAction.BOOKMARK -> TapAction.SEARCH
                                                    TapAction.SEARCH -> TapAction.ZOOM_IN
                                                    TapAction.ZOOM_IN -> TapAction.ZOOM_OUT
                                                    TapAction.ZOOM_OUT -> TapAction.NO_ACTION
                                                    TapAction.NO_ACTION -> TapAction.NEXT_PAGE
                                                }
                                                tapZones = tapZones + (cellName to nextAct)
                                            }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(cellName, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = currentAction.name.replace("_", " ").take(10),
                                                fontSize = 10.sp,
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        onClick = { showTapsDialog = false }
                    ) {
                        Text("Apply Matrix")
                    }
                }
            )
        }

        // 5. Volume/Keys configuration dialog
        if (showKeysDialog) {
            AlertDialog(
                onDismissRequest = { showKeysDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = { Text("Map hard keyboard and volume keys", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Map physical keys behaviors below:", color = Color.Gray, fontSize = 11.sp)

                        MetaSwitchKey("Volume UP trigger", "Navigates Backwards (Prev Page)", volumeUpAction == TapAction.PREV_PAGE) {
                            volumeUpAction = if (it) TapAction.PREV_PAGE else TapAction.NO_ACTION
                        }

                        MetaSwitchKey("Volume DOWN trigger", "Navigates Forwards (Next Page)", volumeDownAction == TapAction.NEXT_PAGE) {
                            volumeDownAction = if (it) TapAction.NEXT_PAGE else TapAction.NO_ACTION
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showKeysDialog = false }) {
                        Text("Save Config", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        // 6. Detailed Page Annotations & Highlighting tools
        if (showAddNoteDialog) {
            val annotationsForCurrentPage = activeAnnotations.filter { it.page == currentPage }
            AlertDialog(
                onDismissRequest = { 
                    showAddNoteDialog = false
                    annoTextSnippet = ""
                    annoCommentText = ""
                },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page Annotator Tool", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Page ${currentPage + 1}", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Text segment input
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Phrase to style on current page:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = annoTextSnippet,
                                onValueChange = { annoTextSnippet = it },
                                placeholder = { Text("Enter sentence or word precisely...", color = Color.Gray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )
                        }

                        // 2. Annotation Type row
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Visual Style Type:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val types = listOf(
                                    "HIGHLIGHT" to "Highlight",
                                    "UNDERLINE" to "Underline",
                                    "STRIKETHROUGH" to "Strikeline"
                                )
                                types.forEach { (typeKey, label) ->
                                    val isSelected = annoSelectedType == typeKey
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { annoSelectedType = typeKey }
                                            .background(
                                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2C2C30),
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            color = if (isSelected) Color.Black else Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Modern Color chip list
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Highlight Color Palette:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val colorsList = listOf(
                                    "Yellow" to 0xFFFFF176.toInt(),
                                    "Green" to 0xFF81C784.toInt(),
                                    "Pink" to 0xFFFF8A80.toInt(),
                                    "Blue" to 0xFF80D8FF.toInt(),
                                    "Purple" to 0xFFE040FB.toInt()
                                )
                                colorsList.forEach { (cName, hex) ->
                                    val isChosen = annoSelectedColorName == cName
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(Color(hex), shape = CircleShape)
                                            .clickable { annoSelectedColorName = cName }
                                            .border(
                                                width = if (isChosen) 2.dp else 0.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        // 4. Notes & comments text
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Note / Comment Overlay (Optional):", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = annoCommentText,
                                onValueChange = { annoCommentText = it },
                                placeholder = { Text("Definition, analysis, note, comment...", color = Color.Gray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.DarkGray
                                )
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.DarkGray))

                        // 5. Existing annotations on page
                        Text("Active page annotations count: ${annotationsForCurrentPage.size}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        annotationsForCurrentPage.forEach { ann ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF232326))
                                    .padding(8.dp)
                                    .clickable {
                                        annoTextSnippet = ann.text
                                        annoSelectedType = ann.type
                                        annoSelectedColorName = ann.colorName
                                        annoCommentText = ann.note ?: ""
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val styleColor = Color(ann.color)
                                    Text(
                                        text = ann.text,
                                        color = styleColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = if (ann.type == "UNDERLINE") TextStyle(textDecoration = TextDecoration.Underline) else TextStyle.Default
                                    )
                                    Text(
                                        text = "Styled: ${ann.type.lowercase()} " + if (ann.note != null) " | Com: ${ann.note}" else "",
                                        color = Color.LightGray,
                                        fontSize = 9.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteAnnotation(ann.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete annotation", tint = Color.Red, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        onClick = {
                            if (annoTextSnippet.isNotBlank()) {
                                val colCode = when(annoSelectedColorName) {
                                    "Yellow" -> 0xFFFFF176.toInt()
                                    "Green" -> 0xFF81C784.toInt()
                                    "Pink" -> 0xFFFF8A80.toInt()
                                    "Blue" -> 0xFF80D8FF.toInt()
                                    "Purple" -> 0xFFE040FB.toInt()
                                    else -> 0xFFFFF176.toInt()
                                }
                                viewModel.addAnnotation(
                                    text = annoTextSnippet,
                                    type = annoSelectedType,
                                    color = colCode,
                                    colorName = annoSelectedColorName,
                                    note = if (annoCommentText.isBlank()) null else annoCommentText
                                )
                                Toast.makeText(context, "Annotation recorded on page text context", Toast.LENGTH_SHORT).show()
                                annoTextSnippet = ""
                                annoCommentText = ""
                                showAddNoteDialog = false
                            } else {
                                Toast.makeText(context, "Please enter a phrase segment to style context", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("Apply & Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAddNoteDialog = false
                        annoTextSnippet = ""
                        annoCommentText = ""
                    }) {
                        Text("Dismiss", color = Color.Gray)
                    }
                }
            )
        }

        // 8. Annotations Manager & Highlight Search & Markdown Exporter
        if (showAllAnnotationsDialog) {
            val processedQuery = annotationSearchQuery.trim()
            val filteredAnnotations = if (processedQuery.isEmpty()) {
                activeAnnotations
            } else {
                activeAnnotations.filter { 
                    it.text.contains(processedQuery, ignoreCase = true) || 
                    (it.note != null && it.note.contains(processedQuery, ignoreCase = true))
                }
            }

            AlertDialog(
                onDismissRequest = { showAllAnnotationsDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Annotations Catalog & Tools", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (activeAnnotations.isEmpty()) {
                                    Toast.makeText(context, "No annotations found to compile!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val markdownReport = StringBuilder("# Reader Annotation Export Summary\n\n")
                                    markdownReport.append("Document: **${viewModel.currentBookRecord.value?.title ?: "Unknown"}**\n")
                                    markdownReport.append("Generated On: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                                    markdownReport.append("---\n\n")

                                    activeAnnotations.forEach { ann ->
                                        markdownReport.append("### Page ${ann.page + 1} (${ann.type})\n")
                                        markdownReport.append("> ${ann.text}\n\n")
                                        if (!ann.note.isNullOrBlank()) {
                                            markdownReport.append("**User Note:** ${ann.note}\n\n")
                                        }
                                        markdownReport.append("---\n\n")
                                    }

                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Book Annotations Export", markdownReport.toString())
                                    clipboard.setPrimaryClip(clip)

                                    Toast.makeText(context, "Compiled Markdown exported & copied to Clipboard!", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Export annotations to markdown clip", tint = Color(0xFF00E5FF))
                            }
                        }

                        // Search Input ("Highlight Search")
                        OutlinedTextField(
                            value = annotationSearchQuery,
                            onValueChange = { annotationSearchQuery = it },
                            placeholder = { Text("Search highlight text or notes...", color = Color.Gray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 48.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.heightIn(max = 280.dp).fillMaxWidth()) {
                        if (filteredAnnotations.isEmpty()) {
                            Text(
                                if (processedQuery.isEmpty()) "No annotations linked to this document yet." else "No matches found for '$processedQuery'",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredAnnotations) { ann ->
                                    Card(
                                        shape = RoundedCornerShape(2.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222225)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .clickable {
                                                    viewModel.jumpToPage(ann.page)
                                                    showAllAnnotationsDialog = false
                                                }
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(ann.color).copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        ann.type,
                                                        color = Color(ann.color),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text("Page ${ann.page + 1}", color = Color.Gray, fontSize = 10.sp)
                                            }

                                            Text(
                                                text = ann.text,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                style = if (ann.type == "UNDERLINE") TextStyle(textDecoration = TextDecoration.Underline) else TextStyle.Default
                                            )

                                            if (!ann.note.isNullOrBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Note comment logo",
                                                        tint = Color(0xFF00E5FF),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        ann.note,
                                                        color = Color.LightGray,
                                                        fontSize = 10.sp,
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = { viewModel.deleteAnnotation(ann.id) },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.height(24.dp)
                                                ) {
                                                    Text("Delete", color = Color.Red, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAllAnnotationsDialog = false }) {
                        Text("Close", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        // 7. Dynamic Footnotes Popup Dialogue
        if (showFootnoteDialog && activeFootnoteContent != null) {
            AlertDialog(
                onDismissRequest = { showFootnoteDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reference Footnote",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Text(
                        text = activeFootnoteContent ?: "",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showFootnoteDialog = false }) {
                        Text("Dismiss", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Collapsible item header details
@Composable
fun DrawerHeaderItem(
    title: String,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Icon(
            imageVector = if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(16.dp)
        )
    }
    HorizontalDivider(color = Color(0xFF222225))
}

@Composable
fun DrawerSelectionBlock(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E21))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
    HorizontalDivider(color = Color(0xFF333336))
}

@Composable
fun TextActionItem(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.DoubleArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MetaTextRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MetaSwitchKey(label: String, def: String, isActive: Boolean, onValChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(def, color = Color.Gray, fontSize = 10.sp)
        }
        Switch(
            checked = isActive,
            onCheckedChange = onValChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
        )
    }
}
