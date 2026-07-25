package com.webreader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webreader.WebReaderApp
import com.webreader.data.database.WordBookEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordQuizScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    val allWords by app.wordBookRepository.allWords.collectAsState(initial = emptyList())
    val dueWords by app.wordBookRepository.dueWords.collectAsState(initial = emptyList())

    var quizMode by remember { mutableStateOf("flashcard") }
    var quizWords by remember { mutableStateOf<List<WordBookEntry>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var isFlipped by remember { mutableStateOf(false) }

    var inputAnswer by remember { mutableStateOf("") }
    var isCorrect by remember { mutableStateOf<Boolean?>(null) }

    var quizComplete by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }

    val options = remember(quizWords, currentIndex) {
        if (quizWords.size >= 4 && currentIndex < quizWords.size) {
            val correct = quizWords[currentIndex]
            val others = quizWords.filter { it.word != correct.word }.shuffled().take(3)
            (others + correct).shuffled()
        } else emptyList()
    }

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

    LaunchedEffect(allWords) {
        if (allWords.isNotEmpty() && quizWords.isEmpty()) {
            startQuiz("flashcard", allWords.take(10))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word Quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (quizWords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No words to quiz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add words to your Word Book first", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
                return@Scaffold
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("Flashcard", quizMode == "flashcard") { startQuiz("flashcard", allWords.take(10)) }
                FilterChip("Multiple Choice", quizMode == "multiple") { startQuiz("multiple", allWords.take(10)) }
                FilterChip("Spelling", quizMode == "spelling") { startQuiz("spelling", allWords.take(10)) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${currentIndex + 1} / ${quizWords.size}", style = MaterialTheme.typography.labelMedium)
                Text("Correct: $correctCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (quizComplete) {
                QuizCompleteContent(
                    total = quizWords.size,
                    correct = correctCount,
                    onRestart = { startQuiz(quizMode, quizWords) },
                    onBack = onBack
                )
            } else {
                val currentWord = quizWords[currentIndex]

                when (quizMode) {
                    "flashcard" -> FlashcardContent(
                        word = currentWord,
                        isFlipped = isFlipped,
                        onFlip = { isFlipped = !isFlipped },
                        onKnown = {
                            if (!isFlipped) correctCount++
                            scope.launch { app.wordBookRepository.reviewWord(currentWord.word, true) }
                            if (currentIndex < quizWords.size - 1) {
                                currentIndex++
                                isFlipped = false
                            } else {
                                quizComplete = true
                            }
                        },
                        onUnknown = {
                            scope.launch { app.wordBookRepository.reviewWord(currentWord.word, false) }
                            if (currentIndex < quizWords.size - 1) {
                                currentIndex++
                                isFlipped = false
                            } else {
                                quizComplete = true
                            }
                        }
                    )
                    "multiple" -> MultipleChoiceContent(
                        word = currentWord,
                        options = options,
                        isAnswered = isCorrect != null,
                        selectedAnswer = isCorrect,
                        onSelect = { selected ->
                            val correct = selected.word == currentWord.word
                            isCorrect = correct
                            if (correct) correctCount++
                            scope.launch {
                                delay(1000)
                                if (currentIndex < quizWords.size - 1) {
                                    currentIndex++
                                    isCorrect = null
                                } else {
                                    quizComplete = true
                                }
                            }
                        }
                    )
                    "spelling" -> SpellingContent(
                        word = currentWord,
                        input = inputAnswer,
                        isCorrect = isCorrect,
                        onInputChange = { inputAnswer = it },
                        onSubmit = {
                            val correct = inputAnswer.trim().lowercase() == currentWord.word.lowercase()
                            isCorrect = correct
                            if (correct) correctCount++
                            scope.launch {
                                delay(1500)
                                inputAnswer = ""
                                isCorrect = null
                                if (currentIndex < quizWords.size - 1) {
                                    currentIndex++
                                } else {
                                    quizComplete = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlashcardContent(
    word: WordBookEntry,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(300),
        label = "flip"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .graphicsLayer {
                    scaleX = 1f
                    scaleY = 1f
                    cameraDistance = 12f * density
                }
                .clickable(onClick = onFlip),
            colors = CardDefaults.cardColors(
                containerColor = if (rotation < 90f) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (rotation < 90f) {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (word.phonetic.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(word.phonetic, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onUnknown,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Don't Know")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onKnown,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Know")
            }
        }
    }
}

@Composable
private fun MultipleChoiceContent(
    word: WordBookEntry,
    options: List<WordBookEntry>,
    isAnswered: Boolean,
    selectedAnswer: Boolean?,
    onSelect: (WordBookEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (word.phonetic.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(word.phonetic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Choose the correct definition:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        options.forEach { option ->
            val isCorrectOption = option.word == word.word
            val isSelected = isAnswered && option.word == word.word

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !isAnswered) { onSelect(option) },
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isAnswered && isCorrectOption -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAnswered && isSelected) {
                        Icon(
                            if (isCorrectOption) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isCorrectOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = option.definition.take(100),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAnswered && !isCorrectOption && isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SpellingContent(
    word: WordBookEntry,
    input: String,
    isCorrect: Boolean?,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Type the word:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text("Your answer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onSubmit() }),
            isError = isCorrect == false,
            supportingText = {
                when (isCorrect) {
                    true -> Text("Correct!", color = MaterialTheme.colorScheme.primary)
                    false -> Text("Answer: ${word.word}", color = MaterialTheme.colorScheme.error)
                    null -> {}
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSubmit,
            enabled = input.isNotBlank() && isCorrect == null,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun QuizCompleteContent(
    total: Int,
    correct: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val percentage = if (total > 0) (correct * 100 / total) else 0

        Text(
            text = "Quiz Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$correct / $total",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$percentage% correct",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
            Button(onClick = onBack) {
                Text("Done")
            }
        }
    }
}
