package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.WordBookEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordQuizViewModel(app: WebReaderApp) : ViewModel() {

    private val repository = app.wordBookRepository

    val allWords = repository.allWords.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dueWords = repository.dueWords.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var quizMode by mutableStateOf("flashcard")
        private set
    var quizWords by mutableStateOf<List<WordBookEntry>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var showAnswer by mutableStateOf(false)
        private set
    var isFlipped by mutableStateOf(false)
        private set
    var inputAnswer by mutableStateOf("")
        private set
    var isCorrect by mutableStateOf<Boolean?>(null)
        private set
    var quizComplete by mutableStateOf(false)
        private set
    var correctCount by mutableIntStateOf(0)
        private set

    fun startQuiz(mode: String, words: List<WordBookEntry>) {
        quizMode = mode
        quizWords = words.shuffled()
        currentIndex = 0
        showAnswer = false
        isFlipped = false
        inputAnswer = ""
        isCorrect = null
        quizComplete = false
        correctCount = 0
    }

    fun flipCard() {
        isFlipped = !isFlipped
    }

    fun answerKnown(word: WordBookEntry) {
        if (!isFlipped) correctCount++
        reviewAndAdvance(word, true)
    }

    fun answerUnknown(word: WordBookEntry) {
        reviewAndAdvance(word, false)
    }

    fun selectOption(word: WordBookEntry, selected: WordBookEntry) {
        if (isCorrect != null) return
        val correct = selected.word == word.word
        isCorrect = correct
        if (correct) correctCount++
        viewModelScope.launch {
            delay(1000)
            advance()
        }
    }

    fun updateInput(value: String) {
        inputAnswer = value
    }

    fun submitSpelling(word: WordBookEntry) {
        if (isCorrect != null) return
        val correct = inputAnswer.trim().lowercase() == word.word.lowercase()
        isCorrect = correct
        if (correct) correctCount++
        viewModelScope.launch {
            delay(1500)
            inputAnswer = ""
            isCorrect = null
            advance()
        }
    }

    private fun reviewAndAdvance(word: WordBookEntry, known: Boolean) {
        viewModelScope.launch {
            repository.reviewWord(word.word, known)
        }
        advance()
    }

    private fun advance() {
        if (currentIndex < quizWords.size - 1) {
            currentIndex++
            isFlipped = false
            isCorrect = null
        } else {
            quizComplete = true
        }
    }
}
