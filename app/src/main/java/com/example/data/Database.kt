package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "book_records",
    indices = [
        Index("lastReadTime"),
        Index("isFavorite"),
        Index("category"),
        Index("title")
    ]
)
data class BookRecord(
    @PrimaryKey val path: String,
    val title: String,
    val lastReadTime: Long,
    val lastPage: Int = 0,
    val totalPages: Int = 0,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val zoom: Float = 1f,
    val cropMode: Int = 0, // 0 = none, 1 = auto, 2 = manual
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    val isFavorite: Boolean = false,
    val readingMode: Int = 1, // 0 = horizontal (pager), 1 = vertical (pager)
    val colorInverted: Boolean = false,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val tags: String = "",
    val category: String = "Uncategorized",
    val author: String = "Unknown Author",
    val series: String = "",
    val seriesNumber: Int = 1
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val page: Int,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BookRecordDao {
    @Query("SELECT * FROM book_records ORDER BY lastReadTime DESC")
    fun getAllRecentBooks(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE isFavorite = 1 ORDER BY lastReadTime DESC")
    fun getFavoriteBooks(): Flow<List<BookRecord>>

    @Query("SELECT * FROM book_records WHERE path = :path LIMIT 1")
    suspend fun getBookRecordByPath(path: String): BookRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBook(book: BookRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooksBatch(books: List<BookRecord>)

    @Query("SELECT COUNT(*) FROM book_records")
    fun getBookCountFlow(): Flow<Int>

    @Query("DELETE FROM book_records WHERE path = :path")
    suspend fun deleteBookRecord(path: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE filePath = :filePath ORDER BY page ASC")
    fun getBookmarksForBook(filePath: String): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Int)

    @Query("DELETE FROM bookmarks WHERE filePath = :filePath AND page = :page")
    suspend fun deleteBookmarkByPage(filePath: String, page: Int)
}

@Entity(tableName = "book_annotations")
data class BookAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val page: Int,
    val text: String,
    val type: String, // "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH"
    val color: Int,
    val colorName: String, // "YELLOW", "GREEN", "PINK", "BLUE", "PURPLE"
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BookAnnotationDao {
    @Query("SELECT * FROM book_annotations ORDER BY timestamp DESC")
    fun getAllAnnotations(): Flow<List<BookAnnotation>>

    @Query("SELECT * FROM book_annotations WHERE filePath = :filePath ORDER BY page ASC, timestamp ASC")
    fun getAnnotationsForBook(filePath: String): Flow<List<BookAnnotation>>

    @Query("SELECT * FROM book_annotations WHERE filePath = :filePath AND (text LIKE '%' || :query || '%' OR (note IS NOT NULL AND note LIKE '%' || :query || '%'))")
    fun searchAnnotationsOfBook(filePath: String, query: String): Flow<List<BookAnnotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: BookAnnotation)

    @Update
    suspend fun updateAnnotation(annotation: BookAnnotation)

    @Query("DELETE FROM book_annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: Int)

    @Query("DELETE FROM book_annotations WHERE filePath = :filePath")
    suspend fun clearAllAnnotationsForBook(filePath: String)
}

@Database(entities = [BookRecord::class, Bookmark::class, BookAnnotation::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookRecordDao(): BookRecordDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookAnnotationDao(): BookAnnotationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebook_droid_plus_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
