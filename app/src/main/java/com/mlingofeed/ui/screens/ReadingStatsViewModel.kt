package com.mlingofeed.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ReadingStatsViewModel(app: WebReaderApp) : ViewModel() {
    val readingSessions = app.settingsManager.readingSessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val totalSeconds = app.settingsManager.readingTimeSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
}
