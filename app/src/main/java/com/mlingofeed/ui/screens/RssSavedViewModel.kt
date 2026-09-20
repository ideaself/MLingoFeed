package com.mlingofeed.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class RssSavedViewModel(app: WebReaderApp) : ViewModel() {
    val saved = app.rssRepository.savedArticles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
