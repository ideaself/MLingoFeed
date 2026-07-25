package com.webreader

import android.app.Application
import com.webreader.data.database.AppDatabase
import com.webreader.data.repository.BookmarkRepository
import com.webreader.data.repository.ChatRepository
import com.webreader.data.repository.HistoryRepository
import com.webreader.data.repository.RssRepository
import com.webreader.data.repository.DictionaryRepository
import com.webreader.data.repository.WordBookRepository
import com.webreader.data.settings.SettingsManager

class WebReaderApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val bookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }
    val dictionaryRepository by lazy { DictionaryRepository() }
    val chatRepository by lazy { ChatRepository() }
    val historyRepository: HistoryRepository by lazy { HistoryRepository(database.historyDao()) }
    val rssRepository: RssRepository by lazy { RssRepository(database.rssDao()) }
    val wordBookRepository: WordBookRepository by lazy { WordBookRepository(database.wordBookDao()) }
    val settingsManager by lazy { SettingsManager(this) }
}
