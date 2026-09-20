package com.mlingofeed.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileUpload
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
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
private val EXPORT_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatExportDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DISPLAY_DATE_FORMATTER)

private fun csvField(value: String): String =
    if (value.none { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
        value
    } else {
        "\"" + value.replace("\"", "\"\"") + "\""
    }

private fun buildCsvExport(words: List<WordBookEntry>): String = buildString {
    append("word,definition,phonetic,example,dateAdded\n")
    words.forEach { w ->
        append(csvField(w.word))
        append(',')
        append(csvField(w.definition))
        append(',')
        append(csvField(w.phonetic))
        append(',')
        append(csvField(w.exampleSentence))
        append(',')
        append(formatExportDate(w.dateAdded))
        append('\n')
    }
}

private fun buildMarkdownExport(words: List<WordBookEntry>): String = buildString {
    append("# Word Book\n\n")
    append("Exported: ${Instant.now().atZone(ZoneId.systemDefault()).format(EXPORT_DATETIME_FORMATTER)}\n\n")
    words.forEach { w ->
        append("## ${w.word}\n")
        if (w.phonetic.isNotEmpty()) append("*${w.phonetic}*\n")
        if (w.definition.isNotEmpty()) append("\n${w.definition}\n")
        if (w.exampleSentence.isNotEmpty()) append("\n> ${w.exampleSentence}\n")
        append("\n---\n\n")
    }
}

private fun buildAnkiExport(words: List<WordBookEntry>): String = buildString {
    words.forEach { w ->
        val back = buildString {
            if (w.definition.isNotEmpty()) append(w.definition)
            if (w.phonetic.isNotEmpty()) append(" (${w.phonetic})")
            if (w.exampleSentence.isNotEmpty()) append("<br><br><i>${w.exampleSentence}</i>")
        }
        append("${w.word}\t$back\n")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookScreen(onBack: () -> Unit, onNavigateToQuiz: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: WordBookViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importWords(uri)
    }

    LaunchedEffect(vm.importResult) {
        val result = vm.importResult ?: return@LaunchedEffect
        val message = when {
            result < 0 -> context.getString(R.string.import_failed_could_not_read_file)
            result == 0 -> context.getString(R.string.no_new_words_found)
            else -> context.getString(R.string.imported_words, result)
        }
        snackbarHostState.showSnackbar(message)
        vm.consumeImportResult()
    }

    val allWords by vm.allWords.collectAsStateWithLifecycle()
    val dueWords by vm.dueWords.collectAsStateWithLifecycle()
    val masteredWords by vm.masteredWords.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()

    val displayWords = when {
        vm.searchQuery.isNotBlank() -> searchResults
        vm.selectedTab == 0 -> allWords
        vm.selectedTab == 1 -> dueWords
        else -> masteredWords
    }

    var filterDays by remember { mutableStateOf<Int?>(null) }
    var filterSource by remember { mutableStateOf<String?>(null) }
    val wordSources = remember(allWords) {
        allWords.map { it.sourceTitle }.filter { it.isNotBlank() }.distinct().take(20)
    }
    val filteredWords = remember(displayWords, filterDays, filterSource) {
        val cutoff = filterDays?.let { System.currentTimeMillis() - it * 24L * 60L * 60L * 1000L }
        displayWords.filter { entry ->
            (cutoff == null || entry.dateAdded >= cutoff) &&
                (filterSource == null || entry.sourceTitle == filterSource)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.word_book)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToQuiz) {
                            Icon(Icons.Default.Quiz, contentDescription = stringResource(R.string.quiz))
                        }
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.import_words))
                        }
                        IconButton(onClick = { vm.openExportDialog() }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.export))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                TextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = { Text(stringResource(R.string.search_words)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { vm.clearQuery() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
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
                        Text(stringResource(R.string.wordbook_tab_all, allWords.size), modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = vm.selectedTab == 1, onClick = { vm.selectTab(1) }) {
                        Text(stringResource(R.string.wordbook_tab_due, dueWords.size), modifier = Modifier.padding(12.dp))
                    }
                    Tab(selected = vm.selectedTab == 2, onClick = { vm.selectTab(2) }) {
                        Text(stringResource(R.string.wordbook_tab_mastered, masteredWords.size), modifier = Modifier.padding(12.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterDays == null && filterSource == null,
                        onClick = { filterDays = null; filterSource = null },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                    FilterChip(
                        selected = filterDays == 7,
                        onClick = { filterDays = if (filterDays == 7) null else 7 },
                        label = { Text(stringResource(R.string.filter_last_7_days)) }
                    )
                    FilterChip(
                        selected = filterDays == 30,
                        onClick = { filterDays = if (filterDays == 30) null else 30 },
                        label = { Text(stringResource(R.string.filter_last_30_days)) }
                    )
                    if (wordSources.isNotEmpty()) {
                        Box {
                            var showSourceMenu by remember { mutableStateOf(false) }
                            FilterChip(
                                selected = filterSource != null,
                                onClick = { showSourceMenu = true },
                                label = {
                                    Text(
                                        text = filterSource ?: stringResource(R.string.filter_source),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showSourceMenu,
                                onDismissRequest = { showSourceMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_all)) },
                                    onClick = { filterSource = null; showSourceMenu = false }
                                )
                                wordSources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(source, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = { filterSource = source; showSourceMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (filteredWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (vm.selectedTab) {
                        1 -> stringResource(R.string.no_words_due_for_review)
                        2 -> stringResource(R.string.no_mastered_words_yet)
                        else -> if (vm.searchQuery.isNotBlank()) stringResource(R.string.no_matching_words) else stringResource(R.string.no_words_saved_yet)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredWords, key = { it.id }) { entry ->
                    val isExpanded by remember(entry) { derivedStateOf { vm.expandedWord == entry.word } }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch {
                                    vm.deleteWord(entry.word)
                                    snackbarHostState.showSnackbar(context.getString(R.string.word_deleted, entry.word))
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
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        WordBookItem(
                            entry = entry,
                            expanded = isExpanded,
                            mnemonicLoading = vm.mnemonicLoadingWord == entry.word,
                            onClick = { vm.toggleExpanded(entry.word) },
                            onMasteredToggle = { vm.toggleMastered(entry) },
                            onGenerateMnemonic = { vm.generateMnemonic(entry) },
                            onExplainWord = { vm.explainWord(entry) }
                        )
                    }
                }
            }
        }
    }

    if (vm.showExportDialog) {
        ExportDialog(
            words = filteredWords,
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

    if (vm.wordDetailWord.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.dismissWordDetail() },
            title = { Text(vm.wordDetailWord) },
            text = {
                if (vm.isLoadingWordDetail) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(vm.wordDetailText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissWordDetail() }) {
                    Text(stringResource(R.string.close))
                }
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
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_words)) },
        text = {
            Column {
                Text(stringResource(R.string.choose_export_format), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                ExportOption(
                    title = stringResource(R.string.csv),
                    description = stringResource(R.string.compatible_with_excel_google_sheets),
                    onClick = {
                        scope.launch {
                            val csv = withContext(Dispatchers.Default) { buildCsvExport(words) }
                            onExport(csv, "text/csv", "Word Book Export")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExportOption(
                    title = stringResource(R.string.markdown),
                    description = stringResource(R.string.formatted_text_for_note_taking_apps),
                    onClick = {
                        scope.launch {
                            val md = withContext(Dispatchers.Default) { buildMarkdownExport(words) }
                            onExport(md, "text/markdown", "Word Book Export")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExportOption(
                    title = stringResource(R.string.anki_flashcards),
                    description = stringResource(R.string.tab_separated_for_anki_import),
                    onClick = {
                        scope.launch {
                            val anki = withContext(Dispatchers.Default) { buildAnkiExport(words) }
                            onExport(anki, "text/plain", "Word Book Anki Import")
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
    mnemonicLoading: Boolean,
    onClick: () -> Unit,
    onMasteredToggle: () -> Unit,
    onGenerateMnemonic: () -> Unit,
    onExplainWord: () -> Unit
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
                    contentDescription = if (entry.mastered) stringResource(R.string.mark_as_not_mastered) else stringResource(R.string.mark_as_mastered),
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
            if (entry.mnemonic.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.mnemonic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 20.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.mnemonic.isEmpty()) {
                    TextButton(onClick = onGenerateMnemonic, enabled = !mnemonicLoading) {
                        Text(
                            text = if (mnemonicLoading) stringResource(R.string.generating) else stringResource(R.string.ai_mnemonic),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                TextButton(onClick = onExplainWord) {
                    Text(
                        text = stringResource(R.string.ai_explain),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val dateStr = Instant.ofEpochMilli(entry.dateAdded).atZone(ZoneId.systemDefault()).format(DISPLAY_DATE_FORMATTER)
            Text(
                text = stringResource(R.string.added_date, dateStr),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
