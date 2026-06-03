package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val bookRecordDao: BookRecordDao,
    private val bookmarkDao: BookmarkDao,
    private val bookAnnotationDao: BookAnnotationDao
) {
    val recentBooks: Flow<List<BookRecord>> = bookRecordDao.getAllRecentBooks()
    val favoriteBooks: Flow<List<BookRecord>> = bookRecordDao.getFavoriteBooks()
    val allBookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
    val allAnnotations: Flow<List<BookAnnotation>> = bookAnnotationDao.getAllAnnotations()
    val bookCount: Flow<Int> = bookRecordDao.getBookCountFlow()

    suspend fun getBookRecord(path: String): BookRecord? {
        return bookRecordDao.getBookRecordByPath(path)
    }

    suspend fun saveBookRecord(book: BookRecord) {
        bookRecordDao.insertOrUpdateBook(book)
    }

    suspend fun saveBooksBatch(books: List<BookRecord>) {
        bookRecordDao.insertBooksBatch(books)
    }

    suspend fun deleteBookRecord(path: String) {
        bookRecordDao.deleteBookRecord(path)
    }

    fun getBookmarks(filePath: String): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForBook(filePath)
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(id: Int) {
        bookmarkDao.deleteBookmark(id)
    }

    suspend fun deleteBookmarkByPage(filePath: String, page: Int) {
        bookmarkDao.deleteBookmarkByPage(filePath, page)
    }

    fun getAnnotations(filePath: String): Flow<List<BookAnnotation>> {
        return bookAnnotationDao.getAnnotationsForBook(filePath)
    }

    fun searchAnnotations(filePath: String, query: String): Flow<List<BookAnnotation>> {
        return bookAnnotationDao.searchAnnotationsOfBook(filePath, query)
    }

    suspend fun addAnnotation(annotation: BookAnnotation) {
        bookAnnotationDao.insertAnnotation(annotation)
    }

    suspend fun updateAnnotation(annotation: BookAnnotation) {
        bookAnnotationDao.updateAnnotation(annotation)
    }

    suspend fun deleteAnnotation(id: Int) {
        bookAnnotationDao.deleteAnnotation(id)
    }

    suspend fun clearAllAnnotationsForBook(filePath: String) {
        bookAnnotationDao.clearAllAnnotationsForBook(filePath)
    }
}
