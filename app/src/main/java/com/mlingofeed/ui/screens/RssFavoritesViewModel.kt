package com.mlingofeed.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class RssFavoritesViewModel(app: WebReaderApp) : ViewModel() {
    val favorites = app.rssRepository.favoriteArticles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
