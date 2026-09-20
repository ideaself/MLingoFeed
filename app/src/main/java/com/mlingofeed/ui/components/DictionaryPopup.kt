package com.mlingofeed.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton

data class DictionaryResult(
    val name: String,
    val definition: String,
    val isLoading: Boolean = false,
    val error: String? = null
)

@Composable
fun DictionaryPopup(
    word: String,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    exampleSentence: String = "",
    sourceUrl: String = "",
    sourceTitle: String = "",
    onOpenWebDictionary: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val dictionaries by app.settingsManager.dictionaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabledDicts = dictionaries.filter { it.isEnabled }
    var dictionariesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Wait for the first real DataStore value so an empty initial list does not flash the
        // "no dictionaries configured" message.
        app.settingsManager.dictionaries.first()
        dictionariesLoaded = true
    }

    var editableWord by remember(word) { mutableStateOf(word) }
    var searchWord by remember(word) { mutableStateOf(word) }
    var results by remember(searchWord) { mutableStateOf<List<DictionaryResult>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSaved by remember { mutableStateOf(false) }
    var isPlayingPronunciation by remember { mutableStateOf(false) }

    val audioPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    fun playPronunciation(uk: Boolean) {
        val spokenWord = searchWord.trim()
        if (spokenWord.isBlank()) return
        try {
            audioPlayer.reset()
            audioPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val type = if (uk) 1 else 2
            audioPlayer.setDataSource(
                "https://dict.youdao.com/dictvoice?audio=${Uri.encode(spokenWord)}&type=$type"
            )
            audioPlayer.setOnPreparedListener { player ->
                player.start()
                isPlayingPronunciation = true
            }
            audioPlayer.setOnCompletionListener { isPlayingPronunciation = false }
            audioPlayer.setOnErrorListener { _, _, _ ->
                isPlayingPronunciation = false
                true
            }
            audioPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlayingPronunciation = false
        }
    }

    LaunchedEffect(searchWord) {
        isSaved = app.wordBookRepository.isWordSaved(searchWord)
    }

    fun lookup(w: String) {
        searchWord = w
        results = emptyList()
    }

    LaunchedEffect(searchWord, enabledDicts, dictionariesLoaded) {
        if (!dictionariesLoaded) return@LaunchedEffect
        if (enabledDicts.isEmpty()) {
            results = listOf(DictionaryResult(
                name = context.getString(R.string.no_dictionary),
                definition = context.getString(R.string.please_configure_at_least_one_dictionary_in_sett),
                isLoading = false
            ))
            return@LaunchedEffect
        }

        results = enabledDicts.map { dict ->
            DictionaryResult(name = dict.name, definition = "", isLoading = true)
        }

        results = coroutineScope {
            enabledDicts.map { dict ->
                async(Dispatchers.IO) {
                    val definition = app.dictionaryRepository.lookupWord(
                        dict.urlTemplate,
                        dict.cssSelector,
                        searchWord
                    )
                    DictionaryResult(
                        name = dict.name,
                        definition = definition,
                        isLoading = false,
                        error = if (definition.startsWith("Error:") || definition.startsWith("No result")) definition else null
                    )
                }
            }.awaitAll()
        }
    }

    val hasMultipleDicts = results.size > 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = editableWord,
                            onValueChange = { editableWord = it },
                            label = { Text(stringResource(R.string.word)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (editableWord.isNotBlank() && editableWord != searchWord) {
                                        lookup(editableWord.trim())
                                    }
                                }
                            )
                        )
                    }
                    Row {
                        if (onOpenWebDictionary != null) {
                            IconButton(onClick = {
                                onOpenWebDictionary(
                                    "https://www.youdao.com/result?word=${Uri.encode(searchWord)}&lang=en"
                                )
                            }) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = stringResource(R.string.web_dictionary)
                                )
                            }
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(searchWord))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (isSaved) {
                                    app.wordBookRepository.deleteWord(searchWord)
                                    isSaved = false
                                } else {
                                    val def = results.firstOrNull()?.definition ?: ""
                                    // Only attach the captured context when the user saves the
                                    // word they originally tapped, and only when it looks like a
                                    // real sentence (not the bare word or a stray fragment).
                                    val contextSentence = if (
                                        searchWord == word &&
                                        exampleSentence.length >= 15 &&
                                        exampleSentence.contains(' ')
                                    ) {
                                        exampleSentence
                                    } else {
                                        ""
                                    }
                                    app.wordBookRepository.addWord(
                                        word = searchWord,
                                        definition = def,
                                        exampleSentence = contextSentence,
                                        sourceUrl = sourceUrl,
                                        sourceTitle = sourceTitle
                                    )
                                    isSaved = true
                                }
                            }
                        }) {
                            Icon(
                                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) stringResource(R.string.remove_from_word_book) else stringResource(R.string.save_to_word_book),
                                tint = if (isSaved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenChat) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.chat))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasMultipleDicts) stringResource(R.string.dict_count, results.size) else results.firstOrNull()?.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { playPronunciation(uk = false) },
                        enabled = !isPlayingPronunciation
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.us_pronunciation),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("US", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = { playPronunciation(uk = true) },
                        enabled = !isPlayingPronunciation
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.uk_pronunciation),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("UK", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (results.isEmpty() || (results.size == 1 && results[0].isLoading)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (hasMultipleDicts) {
                    TabRow(selectedTabIndex = selectedTab) {
                        results.forEachIndexed { index, result ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = result.name,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (hasMultipleDicts) {
                        ResultContent(result = results.getOrNull(selectedTab))
                    } else {
                        results.firstOrNull()?.let { ResultContent(result = it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultContent(result: DictionaryResult?) {
    when {
        result == null -> {
            Text(stringResource(R.string.no_result), style = MaterialTheme.typography.bodyMedium)
        }
        result.isLoading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
            }
        }
        result.error != null -> {
            Text(
                text = result.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        result.definition.isNotEmpty() -> {
            val paragraphs = result.definition.split("\n").filter { it.isNotBlank() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                paragraphs.forEach { para ->
                    Text(
                        text = para.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
        }
        else -> {
            Text(
                text = stringResource(R.string.no_definition_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}