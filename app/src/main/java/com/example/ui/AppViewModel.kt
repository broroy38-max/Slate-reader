package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.graphics.Typeface
import androidx.lifecycle.*
import com.example.data.*
import com.example.reader.*
import com.example.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class AppViewModel(private val context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val repository = DocumentRepository(db.bookRecordDao(), db.bookmarkDao(), db.bookAnnotationDao())

    // 1. Data Lists from Room
    val recentBooks: StateFlow<List<BookRecord>> = repository.recentBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteBooks: StateFlow<List<BookRecord>> = repository.favoriteBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBookmarks: StateFlow<List<Bookmark>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBookCount: StateFlow<Int> = repository.bookCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isBackgroundScanning = MutableStateFlow(false)
    val isBackgroundScanning: StateFlow<Boolean> = _isBackgroundScanning.asStateFlow()

    private val _scanProgressStatus = MutableStateFlow("Idle")
    val scanProgressStatus: StateFlow<String> = _scanProgressStatus.asStateFlow()

    private val _scannedFilesCount = MutableStateFlow(0)
    val scannedFilesCount: StateFlow<Int> = _scannedFilesCount.asStateFlow()

    // 2. File Browser state
    private val _currentDir = MutableStateFlow<File>(context.getExternalFilesDir("Documents") ?: context.filesDir)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _filesInDir = MutableStateFlow<List<File>>(emptyList())
    val filesInDir: StateFlow<List<File>> = _filesInDir.asStateFlow()

    // 3. Reader screen states
    private val _currentBookRecord = MutableStateFlow<BookRecord?>(null)
    val currentBookRecord: StateFlow<BookRecord?> = _currentBookRecord.asStateFlow()

    private val _currentEngine = MutableStateFlow<BookEngine?>(null)
    val currentEngine: StateFlow<BookEngine?> = _currentEngine.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    // Interactive Image controls (copied from active book record when opened)
    val readingMode = MutableStateFlow(1) // 0 = horizontal paging, 1 = vertical paging
    val isNightMode = MutableStateFlow(false)
    val isColorInverted = MutableStateFlow(false)
    val brightness = MutableStateFlow(1f)
    val contrast = MutableStateFlow(1f)
    val renderQuality = MutableStateFlow("Balanced") // Fast, Balanced, High Quality
    val zoomLevel = MutableStateFlow(1f)
    val zoomLocked = MutableStateFlow(false)

    // Crop settings
    val cropMode = MutableStateFlow(0) // 0 = none, 1 = auto, 2 = manual
    val cropLeft = MutableStateFlow(0f)
    val cropTop = MutableStateFlow(0f)
    val cropRight = MutableStateFlow(0f)
    val cropBottom = MutableStateFlow(0f)

    // Dynamic reflowable text styling configurations backed by SharedPreferences
    private val prefs = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    val fontSizeMultiplier = MutableStateFlow(prefs.getFloat("font_size_multiplier", 1.0f))
    val lineSpacingMultiplier = MutableStateFlow(prefs.getFloat("line_spacing_multiplier", 1.0f))
    val paragraphSpacingMultiplier = MutableStateFlow(prefs.getFloat("paragraph_spacing_multiplier", 1.0f))
    val marginPercent = MutableStateFlow(prefs.getFloat("margin_percent", 0.08f))
    val textAlignment = MutableStateFlow(prefs.getInt("text_alignment", 0)) // 0=Left, 1=Center, 2=Right, 3=Justified
    val fontWeight = MutableStateFlow(prefs.getInt("font_weight", 0)) // 0=Normal, 1=Bold
    val fontType = MutableStateFlow(prefs.getString("font_type", "Serif") ?: "Serif") // "Serif", "Sans-Serif", "Monospace", "Custom"
    val screenOrientation = MutableStateFlow(prefs.getInt("screen_orientation", 0)) // 0=Unspecified, 1=Portrait, 2=Landscape
    val fullScreenMode = MutableStateFlow(prefs.getBoolean("full_screen_mode", true))

    fun updateFontSize(multiplier: Float) {
        fontSizeMultiplier.value = multiplier
        prefs.edit().putFloat("font_size_multiplier", multiplier).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateLineSpacing(multiplier: Float) {
        lineSpacingMultiplier.value = multiplier
        prefs.edit().putFloat("line_spacing_multiplier", multiplier).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateParagraphSpacing(multiplier: Float) {
        paragraphSpacingMultiplier.value = multiplier
        prefs.edit().putFloat("paragraph_spacing_multiplier", multiplier).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateMarginPercent(percent: Float) {
        marginPercent.value = percent
        prefs.edit().putFloat("margin_percent", percent).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateTextAlignment(alignment: Int) {
        textAlignment.value = alignment
        prefs.edit().putInt("text_alignment", alignment).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateFontWeight(weight: Int) {
        fontWeight.value = weight
        prefs.edit().putInt("font_weight", weight).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateFontType(type: String) {
        fontType.value = type
        prefs.edit().putString("font_type", type).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateScreenOrientation(orientation: Int) {
        screenOrientation.value = orientation
        prefs.edit().putInt("screen_orientation", orientation).apply()
    }

    fun updateFullScreenMode(enabled: Boolean) {
        fullScreenMode.value = enabled
        prefs.edit().putBoolean("full_screen_mode", enabled).apply()
    }

    // Theme, color, auto-switching, blue-light and brightness controls
    val themeMode = MutableStateFlow(prefs.getString("theme_mode", "DAY") ?: "DAY")
    val themeCustomBgColor = MutableStateFlow(prefs.getInt("theme_custom_bg_color", 0xFFFAF9F6.toInt()))
    val themeCustomTextColor = MutableStateFlow(prefs.getInt("theme_custom_text_color", 0xFF2C2C2C.toInt()))
    val autoDayNight = MutableStateFlow(prefs.getBoolean("auto_day_night", false))
    val blueLightReduction = MutableStateFlow(prefs.getFloat("blue_light_reduction", 0f))
    val readerBrightness = MutableStateFlow(prefs.getFloat("reader_brightness", 1.0f))

    fun updateThemeMode(mode: String) {
        themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
        val isDark = (mode == "NIGHT" || mode == "AMOLED" || mode == "GREEN_PHOSPHOR" || mode == "HIGH_CONTRAST")
        isNightMode.value = isDark
        isColorInverted.value = isDark
        _currentBookRecord.value?.let { record ->
            viewModelScope.launch {
                repository.saveBookRecord(record.copy(colorInverted = isDark))
            }
        }
        triggerReflowSettingsUpdate()
    }

    fun updateThemeCustomBgColor(color: Int) {
        themeCustomBgColor.value = color
        prefs.edit().putInt("theme_custom_bg_color", color).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateThemeCustomTextColor(color: Int) {
        themeCustomTextColor.value = color
        prefs.edit().putInt("theme_custom_text_color", color).apply()
        triggerReflowSettingsUpdate()
    }

    fun updateAutoDayNight(enabled: Boolean) {
        autoDayNight.value = enabled
        prefs.edit().putBoolean("auto_day_night", enabled).apply()
        if (enabled) {
            applyAutoDayNightDetection()
        }
    }

    fun updateBlueLightReduction(value: Float) {
        blueLightReduction.value = value
        prefs.edit().putFloat("blue_light_reduction", value).apply()
    }

    fun updateReaderBrightness(value: Float) {
        readerBrightness.value = value
        prefs.edit().putFloat("reader_brightness", value).apply()
    }

    fun applyAutoDayNightDetection() {
        if (!autoDayNight.value) return
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val targetMode = if (hour >= 18 || hour < 6) "NIGHT" else "DAY"
        themeMode.value = targetMode
        prefs.edit().putString("theme_mode", targetMode).apply()
    }

    fun getThemeBgColor(mode: String): Int {
        return when (mode.uppercase()) {
            "DAY" -> 0xFFFFFFFF.toInt()
            "SEPIA" -> 0xFFF5EFEB.toInt()
            "NIGHT" -> 0xFF121212.toInt()
            "AMOLED" -> 0xFF000000.toInt()
            "GREEN_PHOSPHOR" -> 0xFF051005.toInt()
            "HIGH_CONTRAST" -> 0xFF000000.toInt()
            "CUSTOM" -> themeCustomBgColor.value
            else -> 0xFFFFFFFF.toInt()
        }
    }

    fun getThemeTextColor(mode: String): Int {
        return when (mode.uppercase()) {
            "DAY" -> 0xFF000000.toInt()
            "SEPIA" -> 0xFF3E2723.toInt()
            "NIGHT" -> 0xFFE0E0E0.toInt()
            "AMOLED" -> 0xFFFFFFFF.toInt()
            "GREEN_PHOSPHOR" -> 0xFF00FF00.toInt()
            "HIGH_CONTRAST" -> 0xFFFFFF00.toInt()
            "CUSTOM" -> themeCustomTextColor.value
            else -> 0xFF000000.toInt()
        }
    }

    fun triggerReflowSettingsUpdate() {
        val engine = _currentEngine.value ?: return
        val bgCol = getThemeBgColor(themeMode.value)
        val textCol = getThemeTextColor(themeMode.value)

        val settings = ReflowSettings(
            fontSizeMultiplier = fontSizeMultiplier.value,
            lineSpacingMultiplier = lineSpacingMultiplier.value,
            paragraphSpacingMultiplier = paragraphSpacingMultiplier.value,
            marginPercent = marginPercent.value,
            textAlignment = textAlignment.value,
            fontWeight = fontWeight.value,
            fontType = fontType.value,
            backgroundColor = bgCol,
            textColor = textCol,
            activeAnnotations = _activeAnnotations.value
        )
        viewModelScope.launch(Dispatchers.Default) {
            engine.applyReflowSettings(settings)
            val newTotalPageCount = engine.totalPages
            withContext(Dispatchers.Main) {
                _totalPages.value = newTotalPageCount
                _currentPage.value = _currentPage.value.coerceIn(0, (newTotalPageCount - 1).coerceAtLeast(0))
            }
        }
    }

    fun uploadCustomFont(uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fontFile = File(context.filesDir, "custom_font.ttf")
                    fontFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    if (fontFile.exists()) {
                        com.example.reader.customTypeface = Typeface.createFromFile(fontFile)
                        prefs.edit().putBoolean("has_custom_font", true).apply()
                        triggerReflowSettingsUpdate()
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to upload custom font", e)
            }
        }
    }

    // Bookmarks for reader
    private val _activeBookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val activeBookmarks: StateFlow<List<Bookmark>> = _activeBookmarks.asStateFlow()

    // Book annotations for reader
    private val _activeAnnotations = MutableStateFlow<List<BookAnnotation>>(emptyList())
    val activeAnnotations: StateFlow<List<BookAnnotation>> = _activeAnnotations.asStateFlow()

    // PDF Inner-Search Manager
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _searchIndex = MutableStateFlow(0)
    val searchIndex: StateFlow<Int> = _searchIndex.asStateFlow()

    // 4. Scanning Module Queue
    private val _scanQueue = MutableStateFlow<List<ScanPage>>(emptyList())
    val scanQueue: StateFlow<List<ScanPage>> = _scanQueue.asStateFlow()

    // ==========================================
    // READING STATISTICS & TELEMETRY
    // ==========================================
    private val statsPrefs = context.getSharedPreferences("reading_statistics", Context.MODE_PRIVATE)

    private val _dailyReadingGoalMins = MutableStateFlow(statsPrefs.getInt("daily_goal", 30))
    val dailyReadingGoalMins: StateFlow<Int> = _dailyReadingGoalMins.asStateFlow()

    private val _readingStreak = MutableStateFlow(statsPrefs.getInt("streak_count", 0))
    val readingStreak: StateFlow<Int> = _readingStreak.asStateFlow()

    private val _todayReadSeconds = MutableStateFlow(statsPrefs.getInt("today_read_seconds", 0))
    val todayReadSeconds: StateFlow<Int> = _todayReadSeconds.asStateFlow()

    // Map of Day-of-Week to reading minutes
    private val _weeklyReadingMinutes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val weeklyReadingMinutes: StateFlow<Map<String, Int>> = _weeklyReadingMinutes.asStateFlow()

    fun loadWeeklyStats() {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val map = mutableMapOf<String, Int>()
        var isAllEmpty = true
        days.forEach { day ->
            val valMins = statsPrefs.getInt("mins_$day", -1)
            if (valMins != -1) {
                isAllEmpty = false
                map[day] = valMins
            } else {
                map[day] = 0
            }
        }

        // If completely empty (first run), initialize with zero/empty map
        if (isAllEmpty) {
            days.forEach { day ->
                map[day] = 0
            }
        }
        _weeklyReadingMinutes.value = map
    }

    // Call this update when setting new daily goal
    fun updateDailyGoal(minutes: Int) {
        _dailyReadingGoalMins.value = minutes
        statsPrefs.edit().putInt("daily_goal", minutes).apply()
    }

    // Update reading streak logic
    fun checkAndUpdateStreak() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayStr = sdf.format(java.util.Date())
        val lastReadDate = statsPrefs.getString("last_read_date", "") ?: ""

        if (lastReadDate != todayStr) {
            val yesterdayStr = sdf.format(java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))
            val currentStreak = statsPrefs.getInt("streak_count", 0)

            val newStreak = when (lastReadDate) {
                "" -> currentStreak
                yesterdayStr -> currentStreak + 1
                else -> if (_todayReadSeconds.value > 0) 1 else currentStreak
            }

            statsPrefs.edit()
                .putString("last_read_date", todayStr)
                .putInt("streak_count", newStreak)
                .apply()

            _readingStreak.value = newStreak
        }
    }

    // Call this to add reading time, usually every 1 second or on page turn
    fun addReadingTime(seconds: Int) {
        val currentSeconds = _todayReadSeconds.value + seconds
        _todayReadSeconds.value = currentSeconds
        statsPrefs.edit().putInt("today_read_seconds", currentSeconds).apply()

        // Sync to active Day of Week
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeekInt = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        val dayKey = when (dayOfWeekInt) {
            java.util.Calendar.MONDAY -> "Mon"
            java.util.Calendar.TUESDAY -> "Tue"
            java.util.Calendar.WEDNESDAY -> "Wed"
            java.util.Calendar.THURSDAY -> "Thu"
            java.util.Calendar.FRIDAY -> "Fri"
            java.util.Calendar.SATURDAY -> "Sat"
            java.util.Calendar.SUNDAY -> "Sun"
            else -> "Mon"
        }

        // Calculate and add to that day's minutes
        val totalMinsToday = currentSeconds / 60
        statsPrefs.edit().putInt("mins_$dayKey", (statsPrefs.getInt("mins_$dayKey", 0) + (seconds.toFloat() / 60f).toInt()).coerceAtLeast(totalMinsToday)).apply()

        // Also check if they read today & update streak
        checkAndUpdateStreak()

        // Reload weekly stats
        loadWeeklyStats()
    }

    init {
        // Initialize reading statistics
        loadWeeklyStats()
        checkAndUpdateStreak()

        // Prepare primary user directories
        val docsDir = context.getExternalFilesDir("Documents") ?: context.filesDir
        if (!docsDir.exists()) docsDir.mkdirs()

        // Apply automatic day/night theme detection if active
        if (autoDayNight.value) {
            applyAutoDayNightDetection()
        }

        // Sync custom font typeface if pre-uploaded
        val fontFile = File(context.filesDir, "custom_font.ttf")
        if (fontFile.exists()) {
            try {
                com.example.reader.customTypeface = Typeface.createFromFile(fontFile)
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to load pre-existing custom font layout", e)
            }
        }

        viewModelScope.launch {
            refreshFiles()
        }
    }

    // Navigates folder path hierarchy
    fun navigateToDir(dir: File) {
        _currentDir.value = dir
        viewModelScope.launch {
            refreshFiles()
        }
    }

    fun navigateUp(): Boolean {
        val root = context.getExternalFilesDir("Documents") ?: context.filesDir
        val current = _currentDir.value
        if (current.absolutePath == root.absolutePath) {
            return false // Already at top directory
        }
        val parent = current.parentFile ?: return false
        _currentDir.value = parent
        viewModelScope.launch { refreshFiles() }
        return true
    }

    suspend fun refreshFiles() = withContext(Dispatchers.IO) {
        val root = _currentDir.value
        val list = root.listFiles()?.toList() ?: emptyList()
        _filesInDir.value = list.filter {
            it.isDirectory || (it.isFile && it.extension.lowercase() in listOf("pdf", "cbz", "epub", "mobi", "azw3", "fb2", "txt", "docx", "djvu", "xps"))
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    // Copies standard system storage file picker element in local files space (allowing modern files custom access)
    fun importExternalBook(uri: Uri, context: Context, onImported: (File) -> Unit) {
        viewModelScope.launch {
            val file = copyUriToWorkspace(uri, context) ?: return@launch
            refreshFiles()
            onImported(file)
        }
    }

    fun handleIntent(intent: android.content.Intent?) {
        val action = intent?.action
        val data = intent?.data
        if (action == android.content.Intent.ACTION_VIEW && data != null) {
            viewModelScope.launch {
                val file = copyUriToWorkspace(data, context)
                if (file != null) {
                    openDocument(file)
                }
            }
        }
    }

    private suspend fun copyUriToWorkspace(uri: Uri, context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            var dispName = "imported_book.pdf"
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) dispName = it.getString(idx)
                }
            }

            val targetFile = File(_currentDir.value, dispName)
            val ip = contentResolver.openInputStream(uri) ?: return@withContext null
            val op = FileOutputStream(targetFile)
            ip.copyTo(op)
            ip.close()
            op.close()
            targetFile
        } catch (e: Exception) {
            Log.e("AppViewModel", "Error importing uri", e)
            null
        }
    }

    // No longer populates demo books to respect clean install requests
    private suspend fun generateDemoBooks(context: Context, parent: File) = withContext(Dispatchers.IO) {
        // Implementation removed
    }

    // -------------------------------------------------------------
    // DOCUMENT READER OPERATIONS
    // -------------------------------------------------------------
    fun openDocument(file: File) {
        viewModelScope.launch {
            // 1. Terminate active engines if any
            _currentEngine.value?.close()
            _currentEngine.value = null

            var resolvedFile = file
            // Copy external file to sandbox if not directly readable
            if (!file.exists() || !file.canRead()) {
                val copiedFile = copyExternalFileToSandbox(file, context)
                if (copiedFile != null) {
                    resolvedFile = copiedFile
                }
            }

            // 2. Load or instantiate book state from Room database - check with both paths
            var record = repository.getBookRecord(file.absolutePath) ?: repository.getBookRecord(resolvedFile.absolutePath)
            if (record == null) {
                record = BookRecord(
                    path = resolvedFile.absolutePath,
                    title = file.name,
                    lastReadTime = System.currentTimeMillis()
                )
                repository.saveBookRecord(record)
            } else {
                if (resolvedFile != file) {
                    record = record.copy(path = resolvedFile.absolutePath, lastReadTime = System.currentTimeMillis())
                } else {
                    record = record.copy(lastReadTime = System.currentTimeMillis())
                }
                repository.saveBookRecord(record)
            }

            _currentBookRecord.value = record

            // 3. Setup core interface engine properties
            try {
                val engine = BookEngineFactory.createEngine(context, resolvedFile)
                _currentEngine.value = engine
                triggerReflowSettingsUpdate()
                _currentPage.value = record.lastPage.coerceIn(0, engine.totalPages - 1)

                // Apply saved settings
                readingMode.value = record.readingMode
                isNightMode.value = record.colorInverted // night mode/invert sync
                isColorInverted.value = record.colorInverted
                brightness.value = record.brightness
                contrast.value = record.contrast
                zoomLevel.value = record.zoom
                cropMode.value = record.cropMode
                cropLeft.value = record.cropLeft
                cropTop.value = record.cropTop
                cropRight.value = record.cropRight
                cropBottom.value = record.cropBottom

                // Load bookmarks & annotations concurrently in separate scopes to avoid blocking
                viewModelScope.launch {
                    repository.getBookmarks(file.absolutePath).collectLatest { list ->
                        _activeBookmarks.value = list
                    }
                }
                viewModelScope.launch {
                    repository.getAnnotations(file.absolutePath).collectLatest { list ->
                        _activeAnnotations.value = list
                        triggerReflowSettingsUpdate()
                    }
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Error starting BookEngine", e)
                _totalPages.value = 0
                _currentPage.value = 0
            }
        }
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun jumpToPage(index: Int) {
        val total = _totalPages.value
        if (total <= 0) return
        val target = index.coerceIn(0, total - 1)
        _currentPage.value = target
        saveLastReadPosition()
        clearSearch()
    }

    fun nextPage() {
        val curr = _currentPage.value
        if (curr < _totalPages.value - 1) {
            jumpToPage(curr + 1)
        }
    }

    fun prevPage() {
        val curr = _currentPage.value
        if (curr > 0) {
            jumpToPage(curr - 1)
        }
    }

    fun toggleFavorite() {
        val record = _currentBookRecord.value ?: return
        viewModelScope.launch {
            val updated = record.copy(isFavorite = !record.isFavorite)
            repository.saveBookRecord(updated)
            _currentBookRecord.value = updated
        }
    }

    fun updateReadingMode(mode: Int) {
        readingMode.value = mode
        saveLastReadPosition()
    }

    fun updateImageAdjustment(bright: Float, contr: Float, dark: Boolean, invert: Boolean) {
        brightness.value = bright
        contrast.value = contr
        isNightMode.value = dark
        isColorInverted.value = invert
        saveLastReadPosition()
    }

    fun updateCropSettings(mode: Int, left: Float, top: Float, right: Float, bottom: Float) {
        cropMode.value = mode
        cropLeft.value = left
        cropTop.value = top
        cropRight.value = right
        cropBottom.value = bottom
        saveLastReadPosition()
    }

    fun clearHistoryRecord(path: String) {
         viewModelScope.launch {
             repository.deleteBookRecord(path)
         }
    }

    fun updateBookMetadata(
        path: String,
        title: String,
        author: String,
        category: String,
        tags: String,
        series: String,
        seriesNumber: Int,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val record = repository.getBookRecord(path) ?: BookRecord(
                path = path,
                title = title,
                lastReadTime = System.currentTimeMillis()
            )
            val updated = record.copy(
                title = title,
                author = author,
                category = category,
                tags = tags,
                series = series,
                seriesNumber = seriesNumber,
                isFavorite = isFavorite
            )
            repository.saveBookRecord(updated)
            if (_currentBookRecord.value?.path == path) {
                _currentBookRecord.value = updated
            }
        }
    }

    // Bookmarks Management
    fun addBookmarkAtCurrent() {
        val path = _currentBookRecord.value?.path ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            repository.addBookmark(Bookmark(
                filePath = path,
                page = page,
                label = "Bookmark (Page ${page + 1})",
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    // Book Annotations (Highlights, Underline, Strikethrough, Notes) Management
    fun addAnnotation(
        text: String,
        type: String, // "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH"
        color: Int,
        colorName: String, // "YELLOW", "GREEN", "PINK", "BLUE", "PURPLE"
        note: String? = null
    ) {
        val path = _currentBookRecord.value?.path ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            repository.addAnnotation(BookAnnotation(
                filePath = path,
                page = page,
                text = text,
                type = type,
                color = color,
                colorName = colorName,
                note = note,
                timestamp = System.currentTimeMillis()
            ))
            triggerReflowSettingsUpdate()
        }
    }

    fun updateAnnotationNote(id: Int, newNote: String?) {
        val list = _activeAnnotations.value
        val anno = list.find { it.id == id } ?: return
        viewModelScope.launch {
            repository.addAnnotation(anno.copy(note = newNote, timestamp = System.currentTimeMillis()))
            triggerReflowSettingsUpdate()
        }
    }

    fun deleteAnnotation(id: Int) {
        viewModelScope.launch {
            repository.deleteAnnotation(id)
            triggerReflowSettingsUpdate()
        }
    }

    private fun saveLastReadPosition() {
        val record = _currentBookRecord.value ?: return
        viewModelScope.launch {
            val currentEngineObj = _currentEngine.value
            val updated = record.copy(
                lastPage = _currentPage.value,
                totalPages = _totalPages.value,
                readingMode = readingMode.value,
                colorInverted = isColorInverted.value,
                brightness = brightness.value,
                contrast = contrast.value,
                zoom = zoomLevel.value,
                cropMode = cropMode.value,
                cropLeft = cropLeft.value,
                cropTop = cropTop.value,
                cropRight = cropRight.value,
                cropBottom = cropBottom.value
            )
            repository.saveBookRecord(updated)
            _currentBookRecord.value = updated
        }
    }

    // PDF Text Search
    fun executeSearch(query: String) {
        val engine = _currentEngine.value ?: return
        viewModelScope.launch {
            val results = engine.searchText(query)
            _searchResults.value = results
            _searchIndex.value = 0
            if (results.isNotEmpty()) {
                jumpToPage(results[0].pageIndex)
            }
        }
    }

    fun nextSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIdx = (_searchIndex.value + 1) % results.size
        _searchIndex.value = nextIdx
        jumpToPage(results[nextIdx].pageIndex)
    }

    fun prevSearchResult() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        var prevIdx = _searchIndex.value - 1
        if (prevIdx < 0) prevIdx = results.size - 1
        _searchIndex.value = prevIdx
        jumpToPage(results[prevIdx].pageIndex)
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchIndex.value = 0
    }

    // -------------------------------------------------------------
    // LARGE LIBRARY SCANNERS & SIMULATORS (Offline-First, Low Memory)
    // -------------------------------------------------------------
    fun triggerBackgroundStorageScan() {
        if (_isBackgroundScanning.value) return
        _isBackgroundScanning.value = true
        _scannedFilesCount.value = 0
        _scanProgressStatus.value = "Starting storage & SD card discovery..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val validExtensions = setOf("pdf", "cbz", "epub", "mobi", "azw3", "fb2", "txt", "docx", "djvu", "xps")
                val filesToIndex = mutableListOf<File>()

                // 1. Scan standard directories
                val rootsToScan = mutableSetOf<File>()
                
                // App private / public Documents folder
                val appDocs = context.getExternalFilesDir("Documents") ?: context.filesDir
                rootsToScan.add(appDocs)

                // Common download & documents directories
                try {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloads != null && downloads.exists()) {
                        rootsToScan.add(downloads)
                    }
                } catch (e: Exception) {}
                try {
                    val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    if (docs != null && docs.exists()) {
                        rootsToScan.add(docs)
                    }
                } catch (e: Exception) {}

                // Main phone storage root
                try {
                    val extStorage = Environment.getExternalStorageDirectory()
                    if (extStorage != null && extStorage.exists()) {
                        rootsToScan.add(extStorage)
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to access main storage root", e)
                }

                // SD card and external mounts in `/storage`
                val storageFolder = File("/storage")
                if (storageFolder.exists() && storageFolder.isDirectory) {
                    try {
                        storageFolder.listFiles()?.forEach { file ->
                            if (file.isDirectory && file.name != "self" && file.name != "emulated") {
                                rootsToScan.add(file)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AppViewModel", "Failed to query storage directory", e)
                    }
                }

                // Recursive fast safe walker
                fun scanDir(dir: File) {
                    val list = try { dir.listFiles() } catch (e: Exception) { null } ?: return
                    for (file in list) {
                        if (file.isDirectory) {
                            val name = file.name
                            if (name.startsWith(".") ||
                                name.equals("Android", ignoreCase = true) ||
                                name.equals("sys", ignoreCase = true) ||
                                name.equals("proc", ignoreCase = true) ||
                                name.equals("data", ignoreCase = true) ||
                                name.equals("DCIM", ignoreCase = true) ||
                                name.equals("Pictures", ignoreCase = true) ||
                                name.equals("obb", ignoreCase = true) ||
                                name.equals("cache", ignoreCase = true)
                            ) {
                                continue
                            }
                            scanDir(file)
                        } else if (file.isFile) {
                            if (file.extension.lowercase() in validExtensions) {
                                if (!filesToIndex.any { it.absolutePath == file.absolutePath }) {
                                    filesToIndex.add(file)
                                    _scannedFilesCount.value = filesToIndex.size
                                    if (filesToIndex.size % 20 == 0) {
                                        _scanProgressStatus.value = "Discovered ${filesToIndex.size} eBooks in storage..."
                                    }
                                }
                            }
                        }
                    }
                }

                _scanProgressStatus.value = "Scanning phone folders & downloads..."
                rootsToScan.forEach { root ->
                    scanDir(root)
                }

                // 2. Query MediaStore indexing for absolute coverage bypass of Scoped Storage directory listing restrictions
                try {
                    _scanProgressStatus.value = "Scanning system-indexed documents..."
                    val mediaStoreUri = android.provider.MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(
                        android.provider.MediaStore.Files.FileColumns.DATA,
                        android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                        android.provider.MediaStore.Files.FileColumns.SIZE
                    )
                    
                    val clauses = validExtensions.map { ext ->
                        "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE '%.${ext}'"
                    }.joinToString(" OR ")
                    
                    context.contentResolver.query(mediaStoreUri, projection, clauses, null, null)?.use { cursor ->
                        val dataCol = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                        if (dataCol >= 0) {
                            while (cursor.moveToNext()) {
                                val path = cursor.getString(dataCol)
                                if (!path.isNullOrEmpty()) {
                                    val file = File(path)
                                    val ext = file.extension.lowercase()
                                    if (ext in validExtensions) {
                                        if (!filesToIndex.any { it.absolutePath == file.absolutePath }) {
                                            filesToIndex.add(file)
                                            _scannedFilesCount.value = filesToIndex.size
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Failed to query MediaStore indexing", e)
                }

                if (filesToIndex.isEmpty()) {
                    _scanProgressStatus.value = "Scan complete. No library files found."
                    _isBackgroundScanning.value = false
                    return@launch
                }

                _scanProgressStatus.value = "Indexing ${filesToIndex.size} files into local database..."

                // Chunk into arrays of 100 items for high speed low memory SQLite transaction commits
                val chunks = filesToIndex.chunked(100)
                var currentIndexedIndex = 0

                for (chunk in chunks) {
                    val recordsList = chunk.map { file ->
                        val parentFile = file.parentFile
                        val folderCategory = when {
                            parentFile == null -> "Root Storage"
                            parentFile.absolutePath == Environment.getExternalStorageDirectory().absolutePath -> "Phone Storage"
                            parentFile.parentFile?.name == "storage" && parentFile.name != "emulated" -> "SD Card"
                            parentFile.name == "0" -> "Phone Storage"
                            else -> parentFile.name
                        }
                        BookRecord(
                            path = file.absolutePath,
                            title = file.name,
                            lastReadTime = file.lastModified(),
                            category = folderCategory,
                            author = "Unknown Author",
                            tags = "scanned, local"
                        )
                    }
                    repository.saveBooksBatch(recordsList)
                    currentIndexedIndex += chunk.size
                    _scanProgressStatus.value = "Indexed $currentIndexedIndex of ${filesToIndex.size} eBooks..."
                }

                _scanProgressStatus.value = "Found and indexed ${filesToIndex.size} eBooks!"
                refreshFiles()
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to perform background scanning indexing", e)
                _scanProgressStatus.value = "Error: ${e.message}"
            } finally {
                _isBackgroundScanning.value = false
            }
        }
    }

    // Builders for Scanner correctly
    fun addPhotoToScanQueue(imagePath: String) {
        val currentList = _scanQueue.value.toMutableList()
        val newPage = ScanPage(
            id = UUID.randomUUID().toString(),
            originalImagePath = imagePath
        )
        currentList.add(newPage)
        _scanQueue.value = currentList
    }

    fun removeScanPage(id: String) {
        val currentList = _scanQueue.value.toMutableList()
        currentList.removeAll { it.id == id }
        _scanQueue.value = currentList
    }

    fun rotateScanPage(id: String) {
        val currentList = _scanQueue.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = currentList[index]
            val nextDeg = (item.rotationDegrees + 90f) % 360f
            currentList[index] = item.copy(rotationDegrees = nextDeg)
            _scanQueue.value = currentList
        }
    }

    fun adjustScanPageSettings(id: String, bright: Float, contr: Float, threshold: Boolean) {
        val currentList = _scanQueue.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = currentList[index]
            currentList[index] = item.copy(
                brightness = bright,
                contrast = contr,
                thresholdingEnabled = threshold
            )
            _scanQueue.value = currentList
        }
    }

    fun swapScanPages(fromIndex: Int, toIndex: Int) {
        val currentList = _scanQueue.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val temp = currentList[fromIndex]
            currentList[fromIndex] = currentList[toIndex]
            currentList[toIndex] = temp
            _scanQueue.value = currentList
        }
    }

    // Builds the PDF from active scan queue & saves it to "Documents"
    fun compileScannerPdf(fileName: String, onFinished: (File) -> Unit) {
        val pages = _scanQueue.value
        if (pages.isEmpty()) return

        viewModelScope.launch {
            val folder = context.getExternalFilesDir("Documents") ?: context.filesDir
            var name = fileName.trim()
            if (!name.lowercase().endsWith(".pdf")) {
                name = "$name.pdf"
            }
            val targetFile = File(folder, name)
            val success = PdfGenerator.createPdf(targetFile, pages)
            if (success) {
                // Clear Active Queue
                _scanQueue.value = emptyList()
                refreshFiles()
                onFinished(targetFile)
            }
        }
    }

    fun quickCreateEBookAndAdd(
        title: String,
        author: String,
        category: String,
        coverHexColor: String,
        coverStyle: String,
        coverPattern: String,
        contentPreset: String,
        onFinished: (BookRecord) -> Unit
    ) {
        viewModelScope.launch {
            val folder = context.getExternalFilesDir("Documents") ?: context.filesDir
            val sanitizedTitle = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val name = "QuickBook_${sanitizedTitle}_${System.currentTimeMillis()}.pdf"
            val targetFile = File(folder, name)
            
            val success = PdfGenerator.generateEBookPdf(
                outputFile = targetFile,
                title = title,
                author = author,
                coverHexColor = coverHexColor,
                coverStyle = coverStyle,
                coverPattern = coverPattern,
                contentPreset = contentPreset
            )
            
            if (success) {
                val tagsString = "scanned,local,customCover,coverColor:$coverHexColor,coverStyle:$coverStyle,coverPattern:$coverPattern,preset:$contentPreset"
                val record = BookRecord(
                    path = targetFile.absolutePath,
                    title = title,
                    author = author,
                    category = category,
                    tags = tagsString,
                    totalPages = 3, // Cover page + 2 story pages
                    lastReadTime = System.currentTimeMillis()
                )
                repository.saveBookRecord(record)
                refreshFiles()
                onFinished(record)
            }
        }
    }

    private suspend fun copyExternalFileToSandbox(file: File, context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(android.provider.MediaStore.Files.FileColumns._ID)
            val selection = "${android.provider.MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            
            var fileId: Long? = null
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns._ID)
                    if (idCol >= 0) {
                        fileId = cursor.getLong(idCol)
                    }
                }
            }
            
            val id = fileId ?: return@withContext null
            val contentUri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Files.getContentUri("external"),
                id
            )
            
            val targetFolder = context.getExternalFilesDir("Documents") ?: context.filesDir
            val targetFile = File(targetFolder, "Scanned_" + file.name)
            
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
                return@withContext targetFile
            }
        } catch (e: Exception) {
            Log.e("AppViewModel", "Failed to copy external file to sandbox", e)
        }
        null
    }

    override fun onCleared() {
        super.onCleared()
        _currentEngine.value?.close()
    }
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
