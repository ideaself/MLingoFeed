package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VocabularyStats(
    val total: Int = 0,
    val mastered: Int = 0,
    val due: Int = 0,
    val addedLast7Days: Int = 0
)

class ReadingStatsViewModel(app: WebReaderApp) : ViewModel() {
    val readingSessions = app.settingsManager.readingSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalSeconds = app.settingsManager.readingTimeSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val dailyGoalMinutes = app.settingsManager.dailyReadingGoalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var vocabulary by mutableStateOf(VocabularyStats())
        private set

    var weeklyWordCounts by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            val dates = app.wordBookRepository.getWordDatesSince(now - 8L * 7 * day)
            weeklyWordCounts = (0 until 8).map { week ->
                val end = now - (7 - week) * 7 * day
                val start = end - 7 * day
                dates.count { it in start until end }
            }
        }
    }

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            vocabulary = VocabularyStats(
                total = app.wordBookRepository.getTotalCount(),
                mastered = app.wordBookRepository.getMasteredCount(),
                due = app.wordBookRepository.getDueCount(),
                addedLast7Days = app.wordBookRepository.getWordsAddedBetween(
                    now - 7L * 24 * 60 * 60 * 1000,
                    now
                )
            )
        }
    }
}
