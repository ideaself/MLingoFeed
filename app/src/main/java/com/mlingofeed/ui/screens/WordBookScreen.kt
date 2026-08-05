package com.mlingofeed.ui.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.WordBookEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookScreen(onBack: () -> Unit, onNavigateToQuiz: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: WordBookViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allWords by vm.allWords.collectAsState()
    val dueWords by vm.dueWords.collectAsState()
    val masteredWords by vm.masteredWords.collectAsState()
    val searchResults by (if (vm.searchQuery.isNotBlank()) app.wordBookRepository.searchWords(vm.searchQuery) else app.wordBookRepository.allWords).collectAsState(initial = emptyList())

    val displayWords = when {
        vm.searchQuery.isNotBlank() -> searchResults
        vm.selectedTab == 0 -> allWords
        vm.selectedTab == 1 -> dueWords
        else -> masteredWords
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Word Book") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToQuiz) {
                            Icon(Icons.Default.Quiz, contentDescription = "Quiz")
                        }
                        IconButton(onClick = { vm.openExportDialog() }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                TextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = { Text("Search words...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.clearQuery() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                TabRow(selectedTabIndex = vm.selectedTab) {
                    Tab(selected = vm.selectedTab == 0, onClick = { vm.selectTab(0) }) {
                        Text("All (${allWords.size})", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = vm.selectedTab == 1, onClick = { vm.selectTab(1) }) {
                        Text("Due (${dueWords.size})", modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = vm.selectedTab == 2, onClick = { vm.selectTab(2) }) {
                        Text("Mastered (${masteredWords.size})", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (displayWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (vm.selectedTab) {
                        1 -> "No words due for review"
                        2 -> "No mastered words yet"
                        else -> if (vm.searchQuery.isNotBlank()) "No matching words" else "No words saved yet"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(displayWords, key = { it.id }) { entry ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch {
                                    vm.deleteWord(entry.word)
                                    snackbarHostState.showSnackbar("Deleted '${entry.word}'")
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        WordBookItem(
                            entry = entry,
                            expanded = vm.expandedWord == entry.word,
                            onClick = { vm.toggleExpanded(entry.word) },
                            onMasteredToggle = { vm.toggleMastered(entry) }
                        )
                    }
                }
            }
        }
    }

    if (vm.showExportDialog) {
        ExportDialog(
            words = displayWords,
            onDismiss = { vm.dismissExportDialog() },
            onExport = { content, type, subject ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    this.type = type
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }
                context.startActivity(Intent.createChooser(intent, "Export Words"))
                vm.dismissExportDialog()
            }
        )
    }
}

@Composable
private fun ExportDialog(
    words: List<WordBookEntry>,
    onDismiss: () -> Unit,
    onExport: (String, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Words") },
        text = {
            Column {
                Text("Choose export format:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                ExportOption(
                    title = "CSV",
                    description = "Compatible with Excel, Google Sheets",
                    onClick = {
                        val csv = StringBuilder("word,definition,phonetic,example,dateAdded\n")
                        words.forEach { w ->
                            csv.appendLine("${w.word},\"${w.definition}\",${w.phonetic},\"${w.exampleSentence}\",${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(w.dateAdded))}")
                        }
                        onExport(csv.toString(), "text/csv", "Word Book Export")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExportOption(
                    title = "Markdown",
                    description = "Formatted text for note-taking apps",
                    onClick = {
                        val md = StringBuilder("# Word Book\n\n")
                        md.appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
                        words.forEach { w ->
                            md.appendLine("## ${w.word}")
                            if (w.phonetic.isNotEmpty()) md.appendLine("*${w.phonetic}*")
                            if (w.definition.isNotEmpty()) md.appendLine("\n${w.definition}")
                            if (w.exampleSentence.isNotEmpty()) md.appendLine("\n> ${w.exampleSentence}")
                            md.appendLine("\n---\n")
                        }
                        onExport(md.toString(), "text/markdown", "Word Book Export")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExportOption(
                    title = "Anki Flashcards",
                    description = "Tab-separated for Anki import",
                    onClick = {
                        val anki = StringBuilder()
                        words.forEach { w ->
                            val back = buildString {
                                if (w.definition.isNotEmpty()) append(w.definition)
                                if (w.phonetic.isNotEmpty()) append(" (${w.phonetic})")
                                if (w.exampleSentence.isNotEmpty()) append("<br><br><i>${w.exampleSentence}</i>")
                            }
                            anki.appendLine("${w.word}\t$back")
                        }
                        onExport(anki.toString(), "text/plain", "Word Book Anki Import")
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportOption(title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordBookItem(
    entry: WordBookEntry,
    expanded: Boolean,
    onClick: () -> Unit,
    onMasteredToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 48.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.mastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (entry.phonetic.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.phonetic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.reviewCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "x${entry.reviewCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (entry.definition.isNotEmpty() && !expanded) {
                    Text(
                        text = entry.definition.take(60) + if (entry.definition.length > 60) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onMasteredToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = if (entry.mastered) "Mark as not mastered" else "Mark as mastered",
                    tint = if (entry.mastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            if (entry.definition.isNotEmpty()) {
                Text(
                    text = entry.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
            if (entry.exampleSentence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.exampleSentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.dateAdded))
            Text(
                text = "Added: $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
