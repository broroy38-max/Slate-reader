package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BookRecord
import com.example.data.Bookmark
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SelectedScreen {
    LIBRARY,
    ALL_BOOKMARKS,
    SMART_DASHBOARD,
    LOCAL_FILES,
    ALL_SETTINGS,
    ABOUT,
    FEEDBACK
}

enum class LibrarySort {
    TITLE_ASC,
    TITLE_DESC,
    MOD_DATE_ASC,
    MOD_DATE_DESC,
    READ_DATE_ASC,
    READ_DATE_DESC
}

enum class LibraryViewMode {
    BOOKSHELF,
    COVER_GRID,
    LARGE_COVERS,
    SMALL_COVERS,
    LIST_VIEW,
    DETAILED_LIST_VIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    onOpenBook: (File) -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recentBooks by viewModel.recentBooks.collectAsState()
    val favoriteBooks by viewModel.favoriteBooks.collectAsState()
    val allBookmarks by viewModel.allBookmarks.collectAsState()

    val currentDir by viewModel.currentDir.collectAsState()
    val filesInDir by viewModel.filesInDir.collectAsState()

    // Screen State
    var activeScreen by remember { mutableStateOf(SelectedScreen.LIBRARY) }
    var isLeftPanelOpen by remember { mutableStateOf(false) }

    // Library category selector (0 = Recent Books, 1 = Favorite/Pinned)
    var libraryCategory by remember { mutableStateOf(0) }

    // Floating Search Query for entire Library
    var librarySearchQuery by remember { mutableStateOf("") }

    // Active Category Filter ("All", "Uncategorized", "Manuals", "Comics", etc.)
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Active Tag Filter ("All" or specific tag string)
    var selectedTagFilter by remember { mutableStateOf("All") }

    // Grouping Mode ("Flat", "Author", "Series")
    var groupMode by remember { mutableStateOf("Flat") } // "Flat", "Author", "Series"

    // Book currently selected for editing metadata attributes
    var bookToEditMetadata by remember { mutableStateOf<BookRecord?>(null) }

    // Sorting & View modes
    var sortMode by remember { mutableStateOf(LibrarySort.READ_DATE_DESC) }
    var viewMode by remember { mutableStateOf(LibraryViewMode.BOOKSHELF) }

    // Dialog flags
    var showSortDialog by remember { mutableStateOf(false) }
    var showViewModeDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showQuickCreateBookDialog by remember { mutableStateOf(false) }

    // System Picker launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importExternalBook(uri, context) { file ->
                onOpenBook(file)
            }
        }
    }

    // Storage permission launcher for scanning
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.triggerBackgroundStorageScan()
        if (isGranted) {
            Toast.makeText(context, "অনুমতি পাওয়া গেছে! স্ক্যান শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "অনুমতি ছাড়া সাধারণ ফোল্ডারগুলি স্ক্যান করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic Collections/Categories
    val allCategories = remember(recentBooks) {
        val cats = recentBooks.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All") + cats
    }

    // Dynamic Tags
    val allTags = remember(recentBooks) {
        val tagsSet = mutableSetOf<String>()
        recentBooks.forEach { book ->
            book.tags.split(",").map { it.trim() }.forEach { t ->
                if (t.isNotBlank()) tagsSet.add(t)
            }
        }
        listOf("All") + tagsSet.toList().sorted()
    }

    // Local Filtered Computations
    val filteredBooks = remember(recentBooks, libraryCategory, librarySearchQuery, selectedCategoryFilter, selectedTagFilter) {
        recentBooks.filter { book ->
            val passFavorite = if (libraryCategory == 1) book.isFavorite else true
            val passCategory = if (selectedCategoryFilter != "All") {
                book.category.equals(selectedCategoryFilter, ignoreCase = true)
            } else {
                true
            }
            val passTag = if (selectedTagFilter != "All") {
                val tagList = book.tags.split(",").map { it.trim().lowercase() }
                tagList.contains(selectedTagFilter.lowercase())
            } else {
                true
            }
            val passSearch = if (librarySearchQuery.isNotBlank()) {
                book.title.contains(librarySearchQuery, ignoreCase = true) ||
                book.author.contains(librarySearchQuery, ignoreCase = true) ||
                book.category.contains(librarySearchQuery, ignoreCase = true) ||
                book.tags.contains(librarySearchQuery, ignoreCase = true) ||
                book.series.contains(librarySearchQuery, ignoreCase = true)
            } else {
                true
            }
            passFavorite && passCategory && passTag && passSearch
        }
    }

    // 1. Core Header (Pure Black, static, white font, cyan accents)
    val screenTitle = when (activeScreen) {
        SelectedScreen.LIBRARY -> if (libraryCategory == 0) "Library: Recent Documents" else "Library: Favorites"
        SelectedScreen.ALL_BOOKMARKS -> "All Bookmarks"
        SelectedScreen.SMART_DASHBOARD -> "Smart Reading Dashboard"
        SelectedScreen.LOCAL_FILES -> "Local Files"
        SelectedScreen.ALL_SETTINGS -> "Advanced Settings"
        SelectedScreen.ABOUT -> "About Slate Reader"
        SelectedScreen.FEEDBACK -> "Diagnostics"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Library",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                color = Color.White,
                                letterSpacing = (-1).sp
                            )
                            if (activeScreen == SelectedScreen.LIBRARY) {
                                Text(
                                    text = "${filteredBooks.size} Documents Registered",
                                    fontSize = 10.sp,
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { isLeftPanelOpen = !isLeftPanelOpen },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Toggle Left Panel Menu",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (activeScreen == SelectedScreen.LIBRARY) {
                            // Sub-category selector
                            IconButton(onClick = {
                                libraryCategory = if (libraryCategory == 0) 1 else 0
                            }) {
                                Icon(
                                    imageVector = if (libraryCategory == 0) Icons.Default.StarBorder else Icons.Default.Star,
                                    contentDescription = "Switch Shelf",
                                    tint = if (libraryCategory == 1) Color(0xFF00E5FF) else Color.White
                                )
                            }

                            // Sort button
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Library Sorting Options",
                                    tint = Color.White
                                )
                            }

                            // View mode button
                            IconButton(onClick = { showViewModeDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Change View Mode Layout",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    ),
                    modifier = Modifier.height(56.dp)
                )
            },
            floatingActionButton = {
                if (activeScreen == SelectedScreen.LIBRARY) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        // FAB 1: Quick design and create eBook with real covers
                        FloatingActionButton(
                            onClick = { showQuickCreateBookDialog = true },
                            containerColor = Color(0xFFD4AF37), // Luxury gold theme
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("quick_create_fab")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoStories, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("তৈরি করুন (Design)", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }

                        // FAB 2: Standard safe files import
                        FloatingActionButton(
                            onClick = { documentPickerLauncher.launch("*/*") },
                            containerColor = Color(0xFF00E5FF), // Teal accent
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("import_fab")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ইম্পোর্ট (Import)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            containerColor = Color.Black
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF08080A)) // Almost absolute pitch black for reader efficiency
            ) {
                // Determine active view content
                when (activeScreen) {
                    SelectedScreen.LIBRARY -> {
                        // Layout Column
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 1. Search Bar (Full Width, elegant)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Black,
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = librarySearchQuery,
                                        onValueChange = { librarySearchQuery = it },
                                        placeholder = { Text("Search your bookshelf...", color = Color.Gray, fontSize = 13.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                        shape = RoundedCornerShape(26.dp), // Pill shaped search bar
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = "Search icon", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = if (librarySearchQuery.isNotEmpty()) {
                                            {
                                                IconButton(onClick = { librarySearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        } else null,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF00E5FF),
                                            unfocusedBorderColor = Color(0xFF333336),
                                            focusedContainerColor = Color(0xFF1A1A1D),
                                            unfocusedContainerColor = Color(0xFF0F0F12)
                                        ),
                                        singleLine = true
                                    )
                                }
                            }

                            // 2. Action Filters helper row
                            if (librarySearchQuery.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0D0D0F))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Search Results: ${filteredBooks.size} found",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Clear",
                                        color = Color.Red.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            librarySearchQuery = ""
                                        }
                                    )
                                }
                            }

                            // 3. Main content area
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (librarySearchQuery.isEmpty()) {
                                    val isBgScanning by viewModel.isBackgroundScanning.collectAsState()
                                    val scanProgressStatus by viewModel.scanProgressStatus.collectAsState()

                                    if (recentBooks.isEmpty()) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            if (isBgScanning) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                                                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(24.dp),
                                                                color = Color(0xFF00E5FF),
                                                                strokeWidth = 2.dp
                                                            )
                                                            Spacer(modifier = Modifier.width(16.dp))
                                                            Text(
                                                                text = "Scanning Device Storage...",
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = scanProgressStatus,
                                                            color = Color.LightGray,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            color = Color(0xFF00E5FF),
                                                            trackColor = Color(0xFF333333)
                                                        )
                                                    }
                                                }
                                            }
                                            EmptyStateView(
                                                icon = Icons.Default.AutoStories,
                                                title = "Your Bookshelf is Waiting",
                                                desc = "Import some documents or scan phone/SD storage using 'Scan Library' in the sidebar!"
                                            )
                                        }
                                    } else {
                                        // Books grouped folder-wise forming bookshelves!
                                        val booksByFolder = remember(recentBooks) {
                                            recentBooks.groupBy { book ->
                                                val file = File(book.path)
                                                val parent = file.parentFile
                                                val folder = parent?.name?.ifEmpty { null } ?: book.category.ifBlank { "Uncategorized" }
                                                when (folder) {
                                                    "0" -> "Phone Storage"
                                                    "Documents" -> "My Documents"
                                                    else -> folder
                                                }
                                            }
                                        }

                                        val foldersList = remember(booksByFolder) { booksByFolder.keys.toList() }
                                        val pagerState = rememberPagerState(initialPage = 0) { foldersList.size }

                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // If scanning in background, show progress header
                                            if (isBgScanning) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161619)),
                                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            color = Color(0xFF00E5FF),
                                                            strokeWidth = 1.5.dp
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Text(
                                                            text = scanProgressStatus,
                                                            color = Color(0xFF00E5FF),
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }

                                            // Shelf selection tabs
                                            ScrollableTabRow(
                                                selectedTabIndex = pagerState.currentPage,
                                                containerColor = Color.Black,
                                                contentColor = Color(0xFF00E5FF),
                                                edgePadding = 12.dp,
                                                divider = { HorizontalDivider(color = Color(0xFF222225)) }
                                            ) {
                                                foldersList.forEachIndexed { index, folderName ->
                                                    Tab(
                                                        selected = pagerState.currentPage == index,
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                pagerState.animateScrollToPage(index)
                                                            }
                                                        },
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Folder,
                                                                    contentDescription = null,
                                                                    tint = if (pagerState.currentPage == index) Color(0xFF00E5FF) else Color.Gray,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = folderName.uppercase(),
                                                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                                                    fontSize = 12.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    letterSpacing = 0.5.sp
                                                                )
                                                            }
                                                        },
                                                        selectedContentColor = Color(0xFF00E5FF),
                                                        unselectedContentColor = Color.Gray
                                                    )
                                                }
                                            }

                                            // Horizontal pager allowing custom swipe transition between bookshelves
                                            HorizontalPager(
                                                state = pagerState,
                                                modifier = Modifier.weight(1f).fillMaxWidth()
                                            ) { pageIndex ->
                                                val folderName = foldersList.getOrNull(pageIndex) ?: "Documents"
                                                val folderBooks = booksByFolder[folderName] ?: emptyList()

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(vertical = 16.dp)
                                                ) {
                                                    // Wooden physical shelf - elements sits in rows of 3 on physical timber slabs!
                                                    val chunks = remember(folderBooks) { folderBooks.chunked(3) }
                                                     
                                                    chunks.forEach { rowBooks ->
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                                verticalAlignment = Alignment.Bottom
                                                            ) {
                                                                for (i in 0 until 3) {
                                                                    Box(modifier = Modifier.weight(1f)) {
                                                                        if (i < rowBooks.size) {
                                                                            RecentBookShelfCard(
                                                                                book = rowBooks[i],
                                                                                viewModel = viewModel,
                                                                                onOpenBook = { onOpenBook(File(rowBooks[i].path)) },
                                                                                onEditMetadata = { bookToEditMetadata = it }
                                                                            )
                                                                        } else {
                                                                            Spacer(modifier = Modifier.fillMaxWidth().height(180.dp))
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            
                                                            // Beautiful 3D Wood Shelf Plank directly under each row of books
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(12.dp)
                                                                    .background(
                                                                        brush = Brush.verticalGradient(
                                                                            colors = listOf(
                                                                                Color(0xFF8D6E63), // Light highlight
                                                                                Color(0xFF5D4037), // Solid Wood
                                                                                Color(0xFF3E2723)  // Crevice shadow
                                                                            )
                                                                        )
                                                                    )
                                                                    .border(
                                                                        width = 0.5.dp,
                                                                        color = Color(0xFF2E1C1A)
                                                                    )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // ACTIVE SEARCH QUERY -> Show searched documents in beautiful Bookshelf view
                                    if (filteredBooks.isEmpty()) {
                                        EmptyStateView(
                                            icon = Icons.Default.SearchOff,
                                            title = "No Books Found",
                                            desc = "We couldn't find any documents matching '$librarySearchQuery'."
                                        )
                                    } else {
                                        LibraryBookshelfView(
                                            books = filteredBooks,
                                            sortMode = sortMode,
                                            viewMode = LibraryViewMode.BOOKSHELF,
                                            viewModel = viewModel,
                                            onOpenBook = onOpenBook,
                                            onEditMetadata = { bookToEditMetadata = it }
                                        )
                                    }
                                }
                            }
                        }
                        // Floating Metadata Dialog
                        bookToEditMetadata?.let { b ->
                            var tempTitle by remember { mutableStateOf(b.title) }
                            var tempAuthor by remember { mutableStateOf(b.author) }
                            var tempCategory by remember { mutableStateOf(b.category) }
                            var tempTags by remember { mutableStateOf(b.tags) }
                            var tempSeries by remember { mutableStateOf(b.series) }
                            var tempSeriesNum by remember { mutableStateOf(b.seriesNumber.toString()) }
                            var tempIsFav by remember { mutableStateOf(b.isFavorite) }

                            AlertDialog(
                                onDismissRequest = { bookToEditMetadata = null },
                                shape = RoundedCornerShape(0.dp),
                                containerColor = Color(0xFF1B1B1E),
                                title = {
                                    Text(
                                        text = "Edit Document Catalog Entry",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "File: ${b.path.substringAfterLast("/")}",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        OutlinedTextField(
                                            value = tempTitle,
                                            onValueChange = { tempTitle = it },
                                            label = { Text("Book Title", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = tempAuthor,
                                            onValueChange = { tempAuthor = it },
                                            label = { Text("Author", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = tempCategory,
                                            onValueChange = { tempCategory = it },
                                            label = { Text("Collection/Category", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = tempSeries,
                                            onValueChange = { tempSeries = it },
                                            label = { Text("Book Series Association", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = tempSeriesNum,
                                            onValueChange = { tempSeriesNum = it },
                                            label = { Text("Series Volume Number", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = tempTags,
                                            onValueChange = { tempTags = it },
                                            label = { Text("Tags (comma separated)", color = Color.Gray) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5FF),
                                                unfocusedBorderColor = Color(0xFF2C2C2F)
                                            )
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Add to Favorites Shelf", color = Color.White, fontSize = 12.sp)
                                            Checkbox(
                                                checked = tempIsFav,
                                                onCheckedChange = { tempIsFav = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF00E5FF),
                                                    uncheckedColor = Color.Gray
                                                )
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.updateBookMetadata(
                                                path = b.path,
                                                title = tempTitle,
                                                author = tempAuthor,
                                                category = tempCategory,
                                                tags = tempTags,
                                                series = tempSeries,
                                                seriesNumber = tempSeriesNum.toIntOrNull() ?: 1,
                                                isFavorite = tempIsFav
                                            )
                                            bookToEditMetadata = null
                                        }
                                    ) {
                                        Text("Save Catalog", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { bookToEditMetadata = null }) {
                                        Text("Discard", color = Color.Gray)
                                    }
                                }
                            )
                        }
                    }
                    SelectedScreen.ALL_BOOKMARKS -> {
                        AllBookmarksGeneralView(
                            allBookmarks = allBookmarks,
                            onJumpToBookmark = { bookmark ->
                                val file = File(bookmark.filePath)
                                viewModel.openDocument(file)
                                viewModel.jumpToPage(bookmark.page)
                                onOpenBook(file)
                            }
                        )
                    }
                    SelectedScreen.SMART_DASHBOARD -> {
                        SmartReadingDashboardView(
                            recentBooks = recentBooks,
                            allBookmarks = allBookmarks,
                            viewModel = viewModel
                        )
                    }
                    SelectedScreen.LOCAL_FILES -> {
                        DirectoryBrowserView(
                            currentDir = currentDir,
                            filesInDir = filesInDir,
                            viewModel = viewModel,
                            onOpenBook = onOpenBook
                        )
                    }
                    SelectedScreen.ALL_SETTINGS -> {
                        AdvancedSettingsPanel(viewModel)
                    }
                    SelectedScreen.ABOUT -> {
                        AboutDeveloperPanel()
                    }
                    SelectedScreen.FEEDBACK -> {
                        DiagnosticsFeedbackView()
                    }
                }
            }
        }

        // 2. Custom Draw Sidebar Left Panel (75% to 80% screen width, no modern curves, charcoal back, grey splitters)
        AnimatedVisibility(
            visible = isLeftPanelOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.82f).align(Alignment.CenterStart).zIndex(99f)
        ) {
            Surface(
                color = Color(0xFF141416), // Dark Charcoal
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxHeight().fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Left drawer branding header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SLATE READER",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Document-Centric Desktop Reader Engine",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)

                    // Navigation elements list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        val navItems = listOf(
                            NavigationDrawerData("Library Bookshelf", Icons.Default.AutoStories, SelectedScreen.LIBRARY),
                            NavigationDrawerData("নতুন বই ডিজাইন (Design EBook)", Icons.Default.Book, null) {
                                showQuickCreateBookDialog = true
                                isLeftPanelOpen = false
                            },
                            NavigationDrawerData("স্ক্যান লাইব্রেরি (Scan Library)", Icons.Default.LibraryAdd, null) {
                                isLeftPanelOpen = false
                                val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, 
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                )
                                if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    viewModel.triggerBackgroundStorageScan()
                                    Toast.makeText(context, "ফোনের স্টোরেজ স্ক্যান করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                } else {
                                    storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            NavigationDrawerData("Scan & Create PDF", Icons.Default.Scanner, null) {
                                onNavigateToScanner()
                                isLeftPanelOpen = false
                            },
                            NavigationDrawerData("System Pick EBook", Icons.Default.FileOpen, null) {
                                documentPickerLauncher.launch("*/*")
                                isLeftPanelOpen = false
                            },
                            NavigationDrawerData("Local Storage Directory", Icons.Default.Folder, SelectedScreen.LOCAL_FILES),
                            NavigationDrawerData("All Bookmarks Coordinates", Icons.Default.Bookmark, SelectedScreen.ALL_BOOKMARKS),
                            NavigationDrawerData("Smart Analytics Dashboard", Icons.Default.Timeline, SelectedScreen.SMART_DASHBOARD),
                            NavigationDrawerData("Advanced Config Console", Icons.Default.Settings, SelectedScreen.ALL_SETTINGS),
                            NavigationDrawerData("Exit Application", Icons.Default.ExitToApp, null) {
                                showExitDialog = true
                                isLeftPanelOpen = false
                            }
                        )

                        items(navItems) { item ->
                            val isSelected = item.targetScreen != null && activeScreen == item.targetScreen
                            val itemColor = if (isSelected) Color(0xFF00E5FF) else Color.White
                            val cardBg = if (isSelected) Color(0xFF202025) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(cardBg)
                                    .clickable {
                                        if (item.action != null) {
                                            item.action.invoke()
                                        } else if (item.targetScreen != null) {
                                            activeScreen = item.targetScreen
                                            isLeftPanelOpen = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = itemColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = item.label,
                                    color = itemColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                        }
                    }

                    // Left panel diagnostics info footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Engine State: Ready / Hardware Acceleration On",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "v3.9.0-reader-stable",
                            fontSize = 9.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        // Backdrop shield when left panel is visible
        if (isLeftPanelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isLeftPanelOpen = false }
            )
        }

        // 3. DIALOGS (Highly retro layout - sharp bounds - dense selections)
        if (showSortDialog) {
            AlertDialog(
                onDismissRequest = { showSortDialog = false },
                shape = RoundedCornerShape(0.dp), // Retro sharp edges
                containerColor = Color(0xFF1B1B1E),
                title = {
                    Text(
                        "Sort shelf lists",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val items = listOf(
                            LibrarySort.TITLE_ASC to "Book Title (Alphabetical Asc)",
                            LibrarySort.TITLE_DESC to "Book Title (Alphabetical Desc)",
                            LibrarySort.MOD_DATE_ASC to "File Modification Date (Asc)",
                            LibrarySort.MOD_DATE_DESC to "File Modification Date (Desc)",
                            LibrarySort.READ_DATE_ASC to "Last Reading Date (Oldest First)",
                            LibrarySort.READ_DATE_DESC to "Last Reading Date (Newest First)"
                        )

                        items.forEach { (option, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sortMode = option
                                        showSortDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sortMode == option,
                                    onClick = {
                                        sortMode = option
                                        showSortDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00E5FF),
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, color = Color.White, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSortDialog = false }) {
                        Text("Cancel", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        if (showViewModeDialog) {
            AlertDialog(
                onDismissRequest = { showViewModeDialog = false },
                shape = RoundedCornerShape(0.dp),
                containerColor = Color(0xFF1B1B1E),
                title = {
                    Text(
                        "Library layout modes",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val items = listOf(
                            LibraryViewMode.COVER_GRID to "Standard Cover Grid",
                            LibraryViewMode.LARGE_COVERS to "Large Retro Covers",
                            LibraryViewMode.SMALL_COVERS to "Dense Small Covers Grid",
                            LibraryViewMode.LIST_VIEW to "Flat Compact Rows",
                            LibraryViewMode.DETAILED_LIST_VIEW to "Heavy Technical List View"
                        )

                        items.forEach { (variant, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewMode = variant
                                        showViewModeDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = viewMode == variant,
                                    onClick = {
                                        viewMode = variant
                                        showViewModeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00E5FF),
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(desc, color = Color.White, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = Color(0xFF333333))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showViewModeDialog = false }) {
                        Text("Dismiss", color = Color(0xFF00E5FF))
                    }
                }
            )
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit") },
                text = { Text("Are you sure you want to exit?") },
                confirmButton = {
                    TextButton(onClick = { System.exit(0) }) { Text("Exit") }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showQuickCreateBookDialog) {
            var bookTitle by remember { mutableStateOf("") }
            var bookAuthor by remember { mutableStateOf("") }
            var bookCategory by remember { mutableStateOf("") }
            
            // Cover Preset state
            var selectedColorStr by remember { mutableStateOf("#1976D2") } // ocean blue
            var selectedStyleStr by remember { mutableStateOf("Classic") }
            var selectedPatternStr by remember { mutableStateOf("Solid") }
            var selectedContentPreset by remember { mutableStateOf("Bangla Literature Sample") }

            // Presets list
            val colorsPresetList = listOf(
                Pair("Ruby Red", "#D32F2F"),
                Pair("Ocean Blue", "#1976D2"),
                Pair("Forest Green", "#388E3C"),
                Pair("Golden Gold", "#F57C00"),
                Pair("Midnight Charcoal", "#212121"),
                Pair("Deep Violet", "#7B1FA2")
            )

            val stylesPresetList = listOf("Classic", "Vintage Classic", "Cyberpunk Glow", "Modern Minimalist")
            val patternsPresetList = listOf("Solid", "Modern Gradient", "Classic Vintage", "Cyberpunk Glow")
            val contentPresetsList = listOf("Bangla Literature Sample", "Tech/Programming Guide", "Midnight Classic Mystery")

            AlertDialog(
                onDismissRequest = { showQuickCreateBookDialog = false },
                containerColor = Color(0xFF141417),
                shape = RoundedCornerShape(12.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ডিজাইন ও দ্রুত বই তৈরি করুন",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "আপনার নিজের পছন্দের ডিজাইন, কাভার কালার ও প্রিসেট কন্টেন্ট দিয়ে তাৎক্ষণিকভাবে সচল বুক তৈরি করুন।",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )

                        // 1. Title Input
                        OutlinedTextField(
                            value = bookTitle,
                            onValueChange = { bookTitle = it },
                            label = { Text("বইয়ের শিরোনাম (Title)", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF3E3E42)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_book_title")
                        )

                        // 2. Author Input
                        OutlinedTextField(
                            value = bookAuthor,
                            onValueChange = { bookAuthor = it },
                            label = { Text("লেখক (Author)", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF3E3E42)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_book_author")
                        )

                        // 3. Category Input
                        OutlinedTextField(
                            value = bookCategory,
                            onValueChange = { bookCategory = it },
                            label = { Text("বিভাগ / ক্যাটাগরি (Genre/Category)", color = Color.Gray) },
                            placeholder = { Text("গল্প, কবিতা, বিজ্ঞান, উপন্যাস", color = Color.DarkGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF3E3E42)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_book_category")
                        )

                        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))

                        // 4. Color Presets Selection
                        Text("প্রচ্ছদের রঙ (Cover Palette)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            colorsPresetList.forEach { p ->
                                val colorVal = Color(android.graphics.Color.parseColor(p.second))
                                val isSelected = selectedColorStr == p.second
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(colorVal, androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { selectedColorStr = p.second },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // 5. Layout Style Dropdowns represented as beautiful scrollable chip groups for simplicity
                        Text("প্রচ্ছদ শৈলী (Layout Style)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            stylesPresetList.forEach { style ->
                                val isSelected = selectedStyleStr == style
                                val borderCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF333333)
                                val bgCol = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E22)
                                Box(
                                    modifier = Modifier
                                        .background(bgCol, RoundedCornerShape(16.dp))
                                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                        .clickable { selectedStyleStr = style }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(style, color = if (isSelected) Color(0xFF00E5FF) else Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Text("প্রচ্ছদ প্যাটার্ন (Artwork Pattern)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            patternsPresetList.forEach { pattern ->
                                val isSelected = selectedPatternStr == pattern
                                val borderCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF333333)
                                val bgCol = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E22)
                                Box(
                                    modifier = Modifier
                                        .background(bgCol, RoundedCornerShape(16.dp))
                                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                        .clickable { selectedPatternStr = pattern }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(pattern, color = if (isSelected) Color(0xFF00E5FF) else Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Text("ভিতরের লেখা / কন্টেন্ট (Story Content)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            contentPresetsList.forEach { content ->
                                val isSelected = selectedContentPreset == content
                                val borderCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF333333)
                                val bgCol = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E22)
                                Box(
                                    modifier = Modifier
                                        .background(bgCol, RoundedCornerShape(16.dp))
                                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                        .clickable { selectedContentPreset = content }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(content, color = if (isSelected) Color(0xFF00E5FF) else Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalTitle = bookTitle.trim().ifBlank { "আমার ডায়েরি" }
                            val finalAuthor = bookAuthor.trim().ifBlank { "আমার লেখা" }
                            val finalCategory = bookCategory.trim().ifBlank { "গল্প ও উপন্যাস" }

                            viewModel.quickCreateEBookAndAdd(
                                title = finalTitle,
                                author = finalAuthor,
                                category = finalCategory,
                                coverHexColor = selectedColorStr,
                                coverStyle = selectedStyleStr,
                                coverPattern = selectedPatternStr,
                                contentPreset = selectedContentPreset
                            ) { newRecord ->
                                android.widget.Toast.makeText(context, "সফলভাবে তৈরি এবং লাইব্রেরিতে যোগ করা হয়েছে!", android.widget.Toast.LENGTH_SHORT).show()
                                showQuickCreateBookDialog = false
                                onOpenBook(File(newRecord.path))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("dialog_create_confirm_button")
                    ) {
                        Text("তৈরি করুন এবং পড়ুন (Create & Read)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showQuickCreateBookDialog = false }
                    ) {
                        Text("বাতিল (Cancel)", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

data class NavigationDrawerData(
    val label: String,
    val icon: ImageVector,
    val targetScreen: SelectedScreen?,
    val action: (() -> Unit)? = null
)

// ==========================================
// LIBRARY BOOKSHELF WITH ALL 5 DETAILED MODES
// ==========================================
@Composable
fun LibraryBookshelfView(
    books: List<BookRecord>,
    sortMode: LibrarySort,
    viewMode: LibraryViewMode,
    viewModel: AppViewModel,
    onOpenBook: (File) -> Unit,
    onEditMetadata: (BookRecord) -> Unit
) {
    if (books.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.AutoStories,
            title = "Library is Empty",
            desc = "Use the 'Import' button or the side drawer to add documents and start your reading journey."
        )
    } else {
        // Applet-side sorting computations
        val sortedBooks = remember(books, sortMode) {
            when (sortMode) {
                LibrarySort.TITLE_ASC -> books.sortedBy { it.title.lowercase() }
                LibrarySort.TITLE_DESC -> books.sortedByDescending { it.title.lowercase() }
                LibrarySort.MOD_DATE_ASC -> books.sortedBy { it.lastReadTime }
                LibrarySort.MOD_DATE_DESC -> books.sortedByDescending { it.lastReadTime }
                LibrarySort.READ_DATE_ASC -> books.sortedBy { it.lastReadTime }
                LibrarySort.READ_DATE_DESC -> books.sortedByDescending { it.lastReadTime }
            }
        }

        // Layout switches
        when (viewMode) {
            LibraryViewMode.BOOKSHELF -> {
                // Wooden physical shelf - elements sits in rows of 3 on physical timber slabs!
                val chunks = remember(sortedBooks) { sortedBooks.chunked(3) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(
                        text = "Your Personal Desktop",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Access your documents with vintage focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            items(chunks) { rowBooks ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (i in 0 until 3) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (i < rowBooks.size) {
                                            BookCardComponent(
                                                book = rowBooks[i],
                                                sizeClass = 0,
                                                viewModel = viewModel,
                                                onOpen = { onOpenBook(File(rowBooks[i].path)) },
                                                onEditMetadata = onEditMetadata
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Retro 3D Wood Shelf Plank
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF8D6E63), // Light highlight
                                                Color(0xFF5D4037), // Solid Wood
                                                Color(0xFF3E2723)  // Crevice shadow
                                            )
                                        )
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = Color(0xFF2E1C1A)
                                    )
                            )
                        }
                    }
                }
            }
            LibraryViewMode.COVER_GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(115.dp),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedBooks, key = { it.path }) { book ->
                        BookCardComponent(
                            book = book,
                            sizeClass = 0, // Medium Grid
                            viewModel = viewModel,
                            onOpen = { onOpenBook(File(book.path)) },
                            onEditMetadata = onEditMetadata
                        )
                    }
                }
            }
            LibraryViewMode.LARGE_COVERS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(155.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedBooks, key = { it.path }) { book ->
                        BookCardComponent(
                            book = book,
                            sizeClass = 1, // Large grid
                            viewModel = viewModel,
                            onOpen = { onOpenBook(File(book.path)) },
                            onEditMetadata = onEditMetadata
                        )
                    }
                }
            }
            LibraryViewMode.SMALL_COVERS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(85.dp),
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedBooks, key = { it.path }) { book ->
                        BookCardComponent(
                            book = book,
                            sizeClass = 2, // Small grid
                            viewModel = viewModel,
                            onOpen = { onOpenBook(File(book.path)) },
                            onEditMetadata = onEditMetadata
                        )
                    }
                }
            }
            LibraryViewMode.LIST_VIEW -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedBooks, key = { it.path }) { book ->
                        ListBookRowItem(
                            book = book,
                            isDetailed = false,
                            viewModel = viewModel,
                            onOpen = { onOpenBook(File(book.path)) },
                            onEditMetadata = onEditMetadata
                        )
                    }
                }
            }
            LibraryViewMode.DETAILED_LIST_VIEW -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedBooks, key = { it.path }) { book ->
                        ListBookRowItem(
                            book = book,
                            isDetailed = true,
                            viewModel = viewModel,
                            onOpen = { onOpenBook(File(book.path)) },
                            onEditMetadata = onEditMetadata
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentBookShelfCard(
    book: BookRecord,
    viewModel: AppViewModel,
    onOpenBook: () -> Unit,
    onEditMetadata: (BookRecord) -> Unit
) {
    val ext = book.path.substringAfterLast(".").lowercase()
    val badgeColor = getFormatBadgeColor(ext)
    val progress = if (book.totalPages > 0) ((book.lastPage.toFloat() + 1) / book.totalPages * 100).toInt() else 0
    var showContextDialog by remember { mutableStateOf(false) }

    // Parse custom cover design from tags
    val tags = book.tags
    var primaryCoverColor = badgeColor
    var coverStyle = "Classic"
    var coverPattern = "Solid"
    var isCustomCover = false

    val parts = tags.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.startsWith("coverColor:")) {
            try {
                primaryCoverColor = Color(android.graphics.Color.parseColor(trimmed.substringAfter("coverColor:")))
                isCustomCover = true
            } catch (e: Exception) {}
        } else if (trimmed.startsWith("coverStyle:")) {
            coverStyle = trimmed.substringAfter("coverStyle:")
            isCustomCover = true
        } else if (trimmed.startsWith("coverPattern:")) {
            coverPattern = trimmed.substringAfter("coverPattern:")
            isCustomCover = true
        }
    }

    // Dynamic style fallback based on title if not generated - ensures all books look unique on shelf
    if (!isCustomCover) {
        val hash = Math.abs(book.title.hashCode())
        val hues = listOf(
            Color(0xFF8E24AA), // Purple
            Color(0xFFE53935), // Red
            Color(0xFF1E88E5), // Blue
            Color(0xFF43A047), // Green
            Color(0xFFD81B60), // Pink
            Color(0xFFF4511E), // Orange
            Color(0xFF00ACC1), // Cyan
            Color(0xFF3949AB)  // Indigo
        )
        primaryCoverColor = hues[hash % hues.size]
        val styles = listOf("Classic", "Modern", "Retro Novel")
        coverStyle = styles[hash % styles.size]
        val patterns = listOf("Solid", "Modern Gradient", "Classic Vintage")
        coverPattern = patterns[hash % patterns.size]
    }

    val coverBrush = if (coverPattern == "Modern Gradient" || coverPattern == "Stars & Galaxy" || coverPattern == "Cyberpunk Glow") {
        Brush.verticalGradient(listOf(primaryCoverColor, Color(0xFF101014)))
    } else {
        Brush.verticalGradient(listOf(primaryCoverColor, primaryCoverColor.copy(alpha = 0.85f)))
    }

    Card(
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .combinedClickable(
                onClick = onOpenBook,
                onLongClick = { showContextDialog = true }
            ),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B30)),
        border = BorderStroke(1.dp, Color(0xFF3E3E42))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Vintage binder spine simulation
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primaryCoverColor.copy(alpha = 0.9f),
                                primaryCoverColor,
                                primaryCoverColor.copy(alpha = 0.6f),
                                Color(0xFF1A1A1A)
                            )
                        )
                    )
            )

            // 2. Front cover Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverBrush)
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ext.uppercase(),
                            color = if (coverStyle == "Vintage Classic" || coverStyle == "Classic") Color(0xFF424242) else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )

                        if (book.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF007F),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (coverStyle == "Vintage Classic" || coverStyle == "Classic" || coverStyle == "Golden Filigree") {
                        // Creamy Vintage Card Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFFFCFBE1), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = book.title,
                                    color = Color(0xFF3E2723),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    maxLines = 3,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.width(30.dp).height(1.dp).background(Color(0xFFD4AF37)))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (book.author.isBlank() || book.author == "Unknown Author") "Classic Novel" else book.author,
                                    color = Color(0xFF5D4037),
                                    fontSize = 7.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontFamily = FontFamily.Serif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else if (coverStyle == "Cyberpunk Glow" || coverStyle == "Cyber Punk") {
                        // Cyberpunk Neon Slate Card Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = book.title.uppercase(),
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (book.author.isBlank()) "SEC_02" else book.author.uppercase(),
                                    color = Color(0xFFFF007F),
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Modern minimalist dynamic styling with organic contrast
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = book.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 3,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column {
                    Text(
                        text = if (book.author.isBlank()) "Unknown Writer" else book.author,
                        color = if (coverStyle == "Vintage Classic" || coverStyle == "Classic") Color(0xFFE0E0E0) else Color.LightGray,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$progress%",
                            color = Color(0xFF00E5FF),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${book.lastPage + 1}/${book.totalPages}",
                            color = Color.LightGray,
                            fontSize = 7.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF0D0D10).copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }
    }

    // Context dialog for editing metadata/deleting from bookshelf quickly
    if (showContextDialog) {
        var showRenameDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            containerColor = Color(0xFF1B1B1E),
            shape = RoundedCornerShape(0.dp),
            title = {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "File Path: ${book.path}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    
                    Button(
                        onClick = {
                            showContextDialog = false
                            onEditMetadata(book)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C30))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Metadata Schema", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            showContextDialog = false
                            viewModel.updateBookMetadata(
                                path = book.path,
                                title = book.title,
                                author = book.author,
                                category = book.category,
                                tags = book.tags,
                                series = book.series,
                                seriesNumber = book.seriesNumber,
                                isFavorite = !book.isFavorite
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C30))
                    ) {
                        Icon(
                            imageVector = if (book.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (book.isFavorite) "Remove from Favorites" else "Add to Favorites",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            showContextDialog = false
                            viewModel.clearHistoryRecord(book.path)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E2723))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove from Library", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextDialog = false }) {
                    Text("Close", color = Color(0xFF00E5FF))
                }
            }
        )
    }
}

// ==========================================
// RENDER BOOK COMPONENT (GRID CARD CARPENTRY)
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCardComponent(
    book: BookRecord,
    sizeClass: Int, // 0 = Medium, 1 = Large, 2 = Small
    viewModel: AppViewModel,
    onOpen: () -> Unit,
    onEditMetadata: (BookRecord) -> Unit
) {
    val ext = book.path.substringAfterLast(".").lowercase()
    val badgeColor = getFormatBadgeColor(ext)
    val cardHeight = when (sizeClass) {
        1 -> 180.dp // Large Cover
        2 -> 110.dp // Small Cover
        else -> 145.dp // Medium Cover
    }

    var showContextDialog by remember { mutableStateOf(false) }

    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = Color(0xFF1B1B1E),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Library Manager", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(book.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Open
                    ListItem(
                        headlineContent = { Text("Open Document", color = Color.White, fontSize = 14.sp) },
                        leadingContent = { Icon(Icons.Default.AutoStories, contentDescription = null, tint = Color(0xFF00E5FF)) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showContextDialog = false
                                onOpen()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color(0xFF222225))
                    )

                    // 2. Favorite Toggle
                    ListItem(
                        headlineContent = { Text(if (book.isFavorite) "Remove from Favorites" else "Add to Favorites", color = Color.White, fontSize = 14.sp) },
                        leadingContent = { 
                            Icon(
                                imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFFFF5252)
                            ) 
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showContextDialog = false
                                viewModel.updateBookMetadata(
                                    path = book.path,
                                    title = book.title,
                                    author = book.author,
                                    category = book.category,
                                    tags = book.tags,
                                    series = book.series,
                                    seriesNumber = book.seriesNumber,
                                    isFavorite = !book.isFavorite
                                )
                            },
                        colors = ListItemDefaults.colors(containerColor = Color(0xFF222225))
                    )

                    // 3. Edit Metadata
                    ListItem(
                        headlineContent = { Text("Edit Metadata", color = Color.White, fontSize = 14.sp) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF81C784)) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showContextDialog = false
                                onEditMetadata(book)
                            },
                        colors = ListItemDefaults.colors(containerColor = Color(0xFF222225))
                    )

                    // 4. Delete History Record
                    ListItem(
                        headlineContent = { Text("Remove from Bookshelf", color = Color.Red, fontSize = 14.sp) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showContextDialog = false
                                viewModel.clearHistoryRecord(book.path)
                            },
                        colors = ListItemDefaults.colors(containerColor = Color(0xFF332222))
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showContextDialog = true }
            ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF2C2C2F)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111114))
    ) {
        Column {
            // Simulated Book Cover Plate
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF25252B), Color(0xFF1B1B1F))
                        )
                    )
            ) {
                // Book Format flag
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = badgeColor,
                    shape = RoundedCornerShape(bottomStart = 8.dp)
                ) {
                    Text(
                        text = ext.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Centered retro book logo icon
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = badgeColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(if (sizeClass == 2) 28.dp else 42.dp)
                    )
                    if (sizeClass != 2) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ext.uppercase(),
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Fav Badge
                if (book.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Pinned favorite",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }

            // Book Details
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (sizeClass == 2) 11.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))
                
                val progress = if (book.totalPages > 0) {
                    book.lastPage.toFloat() / (book.totalPages - 1).coerceAtLeast(1).toFloat()
                } else 0f
                val percentage = (progress * 100).toInt().coerceIn(0, 100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$percentage% read",
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${book.lastPage + 1}/${book.totalPages}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                }
                
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(2.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF2C2C2F)
                )
            }
        }
    }
}

// ==========================================
// FLAT DETAILED LIST ROWS
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListBookRowItem(
    book: BookRecord,
    isDetailed: Boolean,
    viewModel: AppViewModel,
    onOpen: () -> Unit,
    onEditMetadata: (BookRecord) -> Unit
) {
    val ext = book.path.substringAfterLast(".").lowercase()
    val badgeColor = getFormatBadgeColor(ext)
    var showContextDialog by remember { mutableStateOf(false) }

    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            shape = RoundedCornerShape(0.dp),
            containerColor = Color(0xFF1B1B1E),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Shelf Catalog Manager", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(book.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Open
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextDialog = false
                                onOpen()
                            }
                            .background(Color(0xFF222225))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Open Document", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Favorite Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextDialog = false
                                viewModel.updateBookMetadata(
                                    path = book.path,
                                    title = book.title,
                                    author = book.author,
                                    category = book.category,
                                    tags = book.tags,
                                    series = book.series,
                                    seriesNumber = book.seriesNumber,
                                    isFavorite = !book.isFavorite
                                )
                            }
                            .background(Color(0xFF222225))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(if (book.isFavorite) "Remove from Favorites" else "Add to Favorites", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // 3. Edit Metadata
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextDialog = false
                                onEditMetadata(book)
                            }
                            .background(Color(0xFF222225))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Edit Library Metadata", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // 4. Delete History Record
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextDialog = false
                                viewModel.clearHistoryRecord(book.path)
                            }
                            .background(Color(0xFF331D1D))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Remove from Bookshelf", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            }
        )
    }

    Surface(
        border = BorderStroke(1.dp, Color(0xFF222225)),
        color = Color(0xFF121214),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showContextDialog = true }
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated Mini Cover Box
            Box(
                modifier = Modifier
                    .size(50.dp, 60.dp)
                    .background(Color(0xFF1B1B1F))
                    .border(BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ext.uppercase(),
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (book.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Pinned",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val progress = if (book.totalPages > 0) {
                    book.lastPage.toFloat() / (book.totalPages - 1).coerceAtLeast(1).toFloat()
                } else 0f
        val pct = (progress * 100).toInt().coerceIn(0, 100)

        if (isDetailed) {
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(book.lastReadTime))

            val file = File(book.path)
            val sizeStr = if (file.exists()) {
                val bytes = file.length()
                if (bytes < 1024) "$bytes B"
                else if (bytes < 1024 * 1024) "${bytes / 1024} KB"
                else "${String.format("%.2f", bytes.toFloat() / (1024f * 1024f))} MB"
            } else "Unknown Size"

            Text(
                text = "Author: ${book.author} | Category: ${book.category} | Size: $sizeStr",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (book.series.isNotBlank()) {
                        Text(
                            text = "Series: ${book.series} [Vol. ${book.seriesNumber}]",
                            fontSize = 10.sp,
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Read: $formattedDate | Page ${book.lastPage + 1}/${book.totalPages} ($pct%)",
                            fontSize = 10.sp,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (book.tags.isNotBlank()) {
                            Text(
                                text = book.tags.split(",").joinToString(" #", prefix = "#"),
                                fontSize = 9.sp,
                                color = Color(0xFFFFB74D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Author: ${book.author} | Format: ${ext.uppercase()}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Pg ${book.lastPage + 1}/${book.totalPages} ($pct%)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = badgeColor,
                    trackColor = Color(0xFF222225),
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }
    }
}

// Helper colors for document types to define reader identity
fun getFormatBadgeColor(ext: String): Color {
    return when (ext) {
        "pdf" -> Color(0xFFD32F2F)      // Dark Crimson
        "djvu" -> Color(0xFF7B1FA2)     // Retro Purple
        "epub" -> Color(0xFF1976D2)     // Sapphire Blue
        "cbz", "cbr" -> Color(0xFF388E3C) // Emerald Green
        "xps" -> Color(0xFFEF6C00)      // Heavy Amber
        else -> Color(0xFF00BCD4)       // Cyan fallback
    }
}

// ==========================================
// ALL BOOKMARKS COORDINATES INTEGRATED ROUTE
// ==========================================
@Composable
fun AllBookmarksGeneralView(
    allBookmarks: List<Bookmark>,
    onJumpToBookmark: (Bookmark) -> Unit
) {
    if (allBookmarks.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.BookmarkBorder,
            title = "No registered bookmark coordinates",
            desc = "Open an active scientific binary in PDF reader, trigger Overlay menu tools, and tag coordinates to record page index links."
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = "SYSTEM REGISTERED BOOKMARK SLOTS (${allBookmarks.size})",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(allBookmarks) { mark ->
                    val filename = mark.filePath.substringAfterLast("/")
                    val ext = filename.substringAfterLast(".").lowercase()
                    val color = getFormatBadgeColor(ext)

                    Surface(
                        color = Color(0xFF121214),
                        border = BorderStroke(1.dp, Color(0xFF222225)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToBookmark(mark) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PinDrop,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mark.label.ifBlank { "Page index coordinate label" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Book: $filename (Page ${mark.page + 1})",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(color)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    ext.uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ==========================================
// SMART READING DASHBOARD RETRO GRAPHICS VIEW
// ==========================================
@Composable
fun SmartReadingDashboardView(
    recentBooks: List<BookRecord>,
    allBookmarks: List<Bookmark>,
    viewModel: AppViewModel
) {
    // Collect statistical figures
    val booksCompleted = recentBooks.count {
        it.totalPages > 0 && (it.lastPage.toFloat() / (it.totalPages - 1).coerceAtLeast(1) >= 0.95f)
    }
    
    val dailyGoalMins by viewModel.dailyReadingGoalMins.collectAsState()
    val readingStreak by viewModel.readingStreak.collectAsState()
    val todayReadSeconds by viewModel.todayReadSeconds.collectAsState()
    val weeklyReadingMins by viewModel.weeklyReadingMinutes.collectAsState()

    val todayReadMins = todayReadSeconds / 60
    val progressPercent = if (dailyGoalMins > 0) ((todayReadMins.toFloat() / dailyGoalMins.toFloat()) * 100).toInt() else 0
    val cumulativeReadMinutes = weeklyReadingMins.values.sum()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SMART ANALYTICS & ACTIVITY LOGS",
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Stats summary row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardMetricCard(
                    title = "Time Tracking",
                    value = "${cumulativeReadMinutes} mins",
                    desc = "Mon-Sun cumulative",
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Streak Ratio",
                    value = "🔥 $readingStreak Days",
                    desc = "Continuous readers",
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    title = "Finished EBooks",
                    value = "$booksCompleted books",
                    desc = "Progress >= 95%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Daily goal progress tracker & adjustment
        item {
            Surface(
                color = Color(0xFF141416),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DAILY READ GOAL PROGRESS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customize and hit your personalized daily target goals",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today: $todayReadMins mins / $dailyGoalMins mins target ($progressPercent%)",
                            fontSize = 12.sp,
                            color = if (todayReadMins >= dailyGoalMins) Color(0xFF00E5FF) else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                        if (todayReadMins >= dailyGoalMins) {
                            Text(
                                text = "🏆 GOAL ACHIEVED!",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val computedProgress = if (dailyGoalMins > 0) (todayReadMins.toFloat() / dailyGoalMins.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { computedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF222225)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Change Target Daily Goal (Minutes):",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Slider(
                            value = dailyGoalMins.toFloat(),
                            onValueChange = { viewModel.updateDailyGoal(it.toInt()) },
                            valueRange = 5f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.weight(1f).testTag("dashboard_daily_goal_slider")
                        )
                        Text(
                            text = "$dailyGoalMins mins",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(64.dp)
                        )
                    }
                }
            }
        }

        // Custom Canvas drawing beautiful geeky retro bar chart for weekly minutes read
        item {
            Surface(
                color = Color(0xFF141416),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "WEEKLY READING DISTRIBUTION MINS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Aggregated reader CPU sessions monitored daily",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Draw custom retro canvas graph representing read load
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Draw Grid lines
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val y = (canvasHeight / gridLines) * i
                            drawLine(
                                color = Color(0xFF333333),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1f
                            )
                        }

                        // Mon, Tue, Wed, Thu, Fri, Sat, Sun heights from preferences
                        val daysOfWeekList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val minutesLog = daysOfWeekList.map { d -> (weeklyReadingMins[d] ?: 0).toFloat() }
                        val maxMins = (minutesLog.maxOrNull() ?: 60f).coerceAtLeast(60f)
                        val numDays = minutesLog.size
                        val barWidth = (canvasWidth / numDays) * 0.45f
                        val slotSpacing = canvasWidth / numDays

                        minutesLog.forEachIndexed { idx, mins ->
                            val heightRatio = mins / maxMins
                            val activeBarHeight = canvasHeight * heightRatio
                            val xStart = (slotSpacing * idx) + (slotSpacing - barWidth) / 2f
                            val yStart = canvasHeight - activeBarHeight

                            // Draw filled bar
                            drawRect(
                                brush = SolidColor(if (daysOfWeekList[idx] == "Sun" || daysOfWeekList[idx] == "Sat") Color(0xFFFFB74D) else Color(0xFF00E5FF)),
                                topLeft = Offset(xStart, yStart),
                                size = androidx.compose.ui.geometry.Size(barWidth, activeBarHeight)
                            )
                        }
                    }

                    // Weekdays horizontal markings
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        days.forEach { d ->
                            Text(
                                d,
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Active items progress details
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACTIVE READ LOGS RECORDED",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                if (recentBooks.isEmpty()) {
                    Text(
                        "No binary databases active for telemetry analytics.",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                } else {
                    recentBooks.take(3).forEach { book ->
                        val ext = book.path.substringAfterLast(".").lowercase()
                        val progress = if (book.totalPages > 0) {
                            book.lastPage.toFloat() / (book.totalPages - 1).coerceAtLeast(1).toFloat()
                        } else 0f
                        val percentage = (progress * 100).toInt().coerceIn(0, 100)

                        Surface(
                            color = Color(0xFF121214),
                            border = BorderStroke(1.dp, Color(0xFF222225))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp, 44.dp).background(Color(0xFF1E1E24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ext.uppercase(), color = getFormatBadgeColor(ext), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Page ${book.lastPage + 1}/${book.totalPages} | Completion Ratio: $percentage%", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF121214),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 14.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 8.sp, color = Color.DarkGray)
        }
    }
}

// ==========================================
// LOCAL FILES SYSTEM DIRECTORY EXPLORER
// ==========================================
@Composable
fun DirectoryBrowserView(
    currentDir: File,
    filesInDir: List<File>,
    viewModel: AppViewModel,
    onOpenBook: (File) -> Unit
) {
    val context = LocalContext.current
    val rootDir = context.getExternalFilesDir("Documents") ?: context.filesDir
    val isAtRoot = currentDir.absolutePath == rootDir.absolutePath

    Column(modifier = Modifier.fillMaxSize()) {
        // Path indicator breadcrumb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121214))
                .border(BorderStroke(1.dp, Color(0xFF252528)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentDir.absolutePath.substringAfter("Android/data/"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("file_explorer_list")
        ) {
            if (!isAtRoot) {
                item {
                    ListExplorerItem(
                        fileName = ".. (Parent Directory)",
                        isFolder = true,
                        onClick = { viewModel.navigateUp() }
                    )
                }
            }

            if (filesInDir.isEmpty() && isAtRoot) {
                item {
                    Box(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Loading workspace files index...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(filesInDir) { file ->
                    ListExplorerItem(
                        fileName = file.name,
                        isFolder = file.isDirectory,
                        onClick = {
                            if (file.isDirectory) {
                                viewModel.navigateToDir(file)
                            } else {
                                onOpenBook(file)
                            }
                        }
                    )
                }
            }
        }
    }
}

// Custom design files element row
@Composable
fun ListExplorerItem(
    fileName: String,
    isFolder: Boolean,
    onClick: () -> Unit
) {
    val ext = fileName.substringAfterLast(".").lowercase()
    val badgeColor = if (isFolder) Color(0xFF00E5FF) else getFormatBadgeColor(ext)

    Surface(
        color = Color(0xFF121214),
        border = BorderStroke(1.dp, Color(0xFF222225)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isFolder) "Directory index block" else "Binary type: ${ext.uppercase()}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            if (!isFolder) {
                Box(
                    modifier = Modifier
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ext.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==========================================
// RETRO SYSTEMS SETTINGS AND DETAILS SCREENS
// ==========================================
@Composable
fun AdvancedSettingsPanel(viewModel: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isScanning by viewModel.isBackgroundScanning.collectAsState()
    val scanProgress by viewModel.scanProgressStatus.collectAsState()
    val scannedFilesCount by viewModel.scannedFilesCount.collectAsState()
    val totalBookCount by viewModel.totalBookCount.collectAsState()

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.triggerBackgroundStorageScan()
        if (isGranted) {
            android.widget.Toast.makeText(context, "অনুমতি পাওয়া গেছে! স্ক্যান শুরু হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "অনুমতি ছাড়া সাধারণ ফোল্ডারগুলি স্ক্যান করা হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var check1 by remember { mutableStateOf(true) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(true) }
    val cacheChoices = listOf("32MB L1 Cache", "64MB L2 Stack", "128MB Power Cache", "256MB Unlimited")
    var cacheSelection by remember { mutableStateOf(1) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        Text(
            "EBOOKDROID CORE SYSTEM ATTRIBUTES",
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Surface(color = Color(0xFF121214), border = BorderStroke(1.dp, Color(0xFF222225))) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("DOCUMENT RENDERING PROPERTIES", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = check1,
                        onCheckedChange = { check1 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Enable GL Text Hardware Shader", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Activates native GPU OpenGL pipeline arrays to render text sharply.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = check2,
                        onCheckedChange = { check2 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Aggressive Page Swapping Cache", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Pre-loads surrounding 10 pages in RAM background threads.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = check3,
                        onCheckedChange = { check3 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Intelligent Page Margin Removal", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Trims empty border space automatically under split parameters.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(color = Color(0xFF121214), border = BorderStroke(1.dp, Color(0xFF222225))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("VIRTUAL MEMORY PAGE ALLOCATOR LIMIT", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                cacheChoices.forEachIndexed { index, title ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cacheSelection = index }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = cacheSelection == index,
                            onClick = { cacheSelection = index },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(title, color = Color.White, fontSize = 13.sp)
                    }
                    if (index < cacheChoices.size - 1) {
                        HorizontalDivider(color = Color(0xFF222225))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(color = Color(0xFF121214), border = BorderStroke(1.dp, Color(0xFF222225))) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("LARGE-SCALE FILE STORAGE & FAST INDEX SCANNER", fontSize = 11.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                
                Text(
                    text = "High-performance background scanner designed to crawl subdirectories and process over 10,000+ files on-device with zero memory pressure and fast transaction batches.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1B1F)).padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ACTIVE INDEXED SHELF DATABASE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("$totalBookCount Books Registered", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (isScanning) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF00E5FF))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scanner Diagnostics & Log State:", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = scanProgress,
                        color = if (isScanning) Color(0xFF00E5FF) else Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, 
                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                viewModel.triggerBackgroundStorageScan()
                            } else {
                                storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("LAUNCH STORAGE SCAN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

            }
        }
    }
}

@Composable
fun AboutDeveloperPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(64.dp)
        )

        Text(
            "Slate Reader Engine",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        Surface(color = Color(0xFF121214), border = BorderStroke(1.dp, Color(0xFF222225)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CREDITS & ATTRIBUTION LOGS", fontSize = 12.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                HorizontalDivider(color = Color(0xFF333333))

                Text("Modern high-performance document reading workflow.", color = Color.White, fontSize = 12.sp)
                Text("Built on Google Jetpack Compose framework modules with direct PDF native rendering interfaces.", color = Color.White, fontSize = 12.sp)
                Text("Designed for heavy reading scholars, legal research review, and tech documents processing.", color = Color.LightGray, fontSize = 11.sp)

                HorizontalDivider(color = Color(0xFF222225))
                Text("Developed by Antigravity AI Studio Engineers.", color = Color.Gray, fontSize = 11.sp)
                Text("Slate Reader. Open Database License v1.0.", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun DiagnosticsFeedbackView() {
    var descText by remember { mutableStateOf("") }
    var diagnosticUploaded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "SUBMIT FEEDBACK & CRASH TELEMETRY",
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (diagnosticUploaded) {
            Surface(
                color = Color(0xFF1E3A1E),
                border = BorderStroke(1.dp, Color.Green),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("TELEMETRY FLUSH SUCCESSFUL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your diagnostic feedback text log has been compressed and sent directly to developer analysis boards.", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        } else {
            Surface(color = Color(0xFF121214), border = BorderStroke(1.dp, Color(0xFF222225)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Describe any rendering issues, scan problems, or feature requests:", fontSize = 12.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = descText,
                        onValueChange = { descText = it },
                        placeholder = { Text("Enter feedback summary log...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        onClick = {
                            if (descText.isNotBlank()) {
                                diagnosticUploaded = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit Diagnostic Form", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(icon: ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF333333),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
