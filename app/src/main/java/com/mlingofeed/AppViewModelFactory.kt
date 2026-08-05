package com.mlingofeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mlingofeed.ui.screens.HistoryViewModel
import com.mlingofeed.ui.screens.HomeViewModel
import com.mlingofeed.ui.screens.ReadingStatsViewModel
import com.mlingofeed.ui.screens.ReaderViewModel
import com.mlingofeed.ui.screens.RssArticleDetailViewModel
import com.mlingofeed.ui.screens.RssArticlesViewModel
import com.mlingofeed.ui.screens.RssFavoritesViewModel
import com.mlingofeed.ui.screens.RssSearchViewModel
import com.mlingofeed.ui.screens.RssSettingsViewModel
import com.mlingofeed.ui.screens.RssSubscriptionsViewModel
import com.mlingofeed.ui.screens.RssUnreadViewModel
import com.mlingofeed.ui.screens.SettingsViewModel
import com.mlingofeed.ui.screens.WordBookViewModel
import com.mlingofeed.ui.screens.WordQuizViewModel

class AppViewModelFactory(private val app: WebReaderApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
            ReaderViewModel(app) as T
        modelClass.isAssignableFrom(WordQuizViewModel::class.java) ->
            WordQuizViewModel(app) as T
        modelClass.isAssignableFrom(RssSubscriptionsViewModel::class.java) ->
            RssSubscriptionsViewModel(app) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app) as T
        modelClass.isAssignableFrom(RssFavoritesViewModel::class.java) ->
            RssFavoritesViewModel(app) as T
        modelClass.isAssignableFrom(RssUnreadViewModel::class.java) ->
            RssUnreadViewModel(app) as T
        modelClass.isAssignableFrom(RssSearchViewModel::class.java) ->
            RssSearchViewModel() as T
        modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
            HistoryViewModel(app) as T
        modelClass.isAssignableFrom(WordBookViewModel::class.java) ->
            WordBookViewModel(app) as T
        modelClass.isAssignableFrom(ReadingStatsViewModel::class.java) ->
            ReadingStatsViewModel(app) as T
        modelClass.isAssignableFrom(RssArticlesViewModel::class.java) ->
            RssArticlesViewModel(app) as T
        modelClass.isAssignableFrom(RssArticleDetailViewModel::class.java) ->
            RssArticleDetailViewModel(app) as T
        modelClass.isAssignableFrom(RssSettingsViewModel::class.java) ->
            RssSettingsViewModel(app) as T
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
