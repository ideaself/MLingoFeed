package com.mlingofeed

import android.app.Application
import com.mlingofeed.data.database.AppDatabase
import com.mlingofeed.data.repository.BookmarkRepository
import com.mlingofeed.data.repository.ChatRepository
import com.mlingofeed.data.repository.HistoryRepository
import com.mlingofeed.data.repository.RssRepository
import com.mlingofeed.data.repository.DictionaryRepository
import com.mlingofeed.data.repository.WordBookRepository
import com.mlingofeed.data.settings.SettingsManager

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
