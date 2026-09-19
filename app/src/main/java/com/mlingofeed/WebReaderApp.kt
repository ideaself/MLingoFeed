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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WebReaderApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { AppDatabase.getDatabase(this) }
    val bookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao(), database) }
    val dictionaryRepository by lazy { DictionaryRepository() }
    val chatRepository by lazy { ChatRepository() }
    val historyRepository: HistoryRepository by lazy { HistoryRepository(database.historyDao(), database) }
    val rssRepository: RssRepository by lazy { RssRepository(database.rssDao(), database) }
    val wordBookRepository: WordBookRepository by lazy { WordBookRepository(database.wordBookDao(), database) }
    val settingsManager by lazy { SettingsManager(this) }
}
