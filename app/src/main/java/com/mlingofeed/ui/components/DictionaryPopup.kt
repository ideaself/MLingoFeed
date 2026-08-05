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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onOpenChat: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val dictionaries by app.settingsManager.dictionaries.collectAsState(initial = emptyList())
    val enabledDicts = dictionaries.filter { it.isEnabled }

    var editableWord by remember(word) { mutableStateOf(word) }
    var searchWord by remember(word) { mutableStateOf(word) }
    var results by remember(searchWord) { mutableStateOf<List<DictionaryResult>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(searchWord) {
        isSaved = app.wordBookRepository.isWordSaved(searchWord)
    }

    fun lookup(w: String) {
        searchWord = w
        results = emptyList()
    }

    LaunchedEffect(searchWord, enabledDicts) {
        if (enabledDicts.isEmpty()) {
            results = listOf(DictionaryResult(
                name = "No Dictionary",
                definition = "Please configure at least one dictionary in Settings",
                isLoading = false
            ))
            return@LaunchedEffect
        }

        results = enabledDicts.map { dict ->
            DictionaryResult(name = dict.name, definition = "", isLoading = true)
        }

        withContext(Dispatchers.IO) {
            results = enabledDicts.mapIndexed { index, dict ->
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
                            label = { Text("Word") },
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
                        Text(
                            text = if (hasMultipleDicts) "${results.size} dictionaries" else results.firstOrNull()?.name ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(searchWord))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (isSaved) {
                                    app.wordBookRepository.deleteWord(searchWord)
                                    isSaved = false
                                } else {
                                    val def = results.firstOrNull()?.definition ?: ""
                                    app.wordBookRepository.addWord(
                                        word = searchWord,
                                        definition = def
                                    )
                                    isSaved = true
                                }
                            }
                        }) {
                            Icon(
                                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "Remove from word book" else "Save to word book",
                                tint = if (isSaved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenChat) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
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
            Text("No result", style = MaterialTheme.typography.bodyMedium)
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
                text = "No definition found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}