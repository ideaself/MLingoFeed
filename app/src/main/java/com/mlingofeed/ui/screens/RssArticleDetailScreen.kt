package com.mlingofeed.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.database.RssArticle
import com.mlingofeed.ui.components.ChatDialog
import com.mlingofeed.ui.components.DictionaryPopup
import com.mlingofeed.ui.components.TranslationPopup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: RssArticleDetailViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(articleId) { vm.ensureLoaded(articleId) }

    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
    val rssFontSize by vm.rssFontSize.collectAsStateWithLifecycle()

    val paragraphs = remember(vm.fullContent) {
        if (vm.fullContent.isNullOrBlank()) emptyList()
        else vm.fullContent!!.split("\n\n").filter { it.isNotBlank() }
    }

    val articleData = vm.article
    var showAiMenu by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    val articleTags by vm.articleTags.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = articleData?.title ?: stringResource(R.string.article_fallback),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vm.isTranslatingAll && vm.translateProgress.isNotEmpty()) {
                                Text(
                                    text = vm.translateProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.translateAll(paragraphs) }) {
                            if (vm.isTranslatingAll) {
                                Text(text = "\u23F9", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.translate_all))
                            }
                        }
                        IconButton(onClick = { vm.toggleSaved(articleId) }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = if (vm.isSaved) stringResource(R.string.remove_from_read_later) else stringResource(R.string.save_for_later),
                                tint = if (vm.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { showAiMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showAiMenu,
                                onDismissRequest = { showAiMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.article?.let { a ->
                                            val shareText = "${a.title}\n\n${a.link}"
                                            val shareIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share article"))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_in_browser)) },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.article?.let { onOpenExternal(it.link) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_link)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.article?.let { a ->
                                            clipboardManager.setText(AnnotatedString(a.link))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.reload_content)) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.reloadContent()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.analyze_difficulty)) },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.analyzeDifficulty()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.find_collocations)) },
                                    onClick = {
                                        showAiMenu = false
                                        vm.extractCollocations()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            articleData?.let {
                FloatingActionButton(onClick = { vm.toggleFavorite(articleId) }) {
                    Icon(
                        if (vm.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (vm.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites)
                    )
                }
            }
        }
    ) { padding ->
        if (articleData == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (vm.articleNotFound) {
                    Text(
                        text = stringResource(R.string.article_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                item(key = "header") {
                    Column {
                        Text(
                            articleData.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontSize = (rssFontSize + 4).sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatPubDate(articleData.pubDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sub = subscriptions.find { it.id == articleData.subscriptionId }
                            if (sub != null) {
                                Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(sub.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            articleTags.forEach { tag ->
                                AssistChip(
                                    onClick = { vm.detachTag(tag.id) },
                                    label = { Text(tag.name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.remove_tag),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                            AssistChip(
                                onClick = { showTagDialog = true },
                                label = { Text(stringResource(R.string.tag)) }
                            )
                        }

                        if (vm.summary.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.ai_summary),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = vm.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        } else {
                            TextButton(
                                onClick = { vm.summarizeArticle() },
                                enabled = !vm.isSummarizing
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (vm.isSummarizing) stringResource(R.string.summarizing) else stringResource(R.string.ai_summary),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (vm.isLoadingContent) {
                    item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.loading_full_content), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (paragraphs.isNotEmpty()) {
                    itemsIndexed(paragraphs, key = { index, _ -> index }) { index, paragraph ->
                        ParagraphBlock(
                            index = index,
                            text = paragraph.trim(),
                            fontSize = rssFontSize,
                            isTranslating = vm.translatingParagraphs[index] == true,
                            translation = vm.translatedParagraphs[index],
                            onWordTap = { word, sentence ->
                                val clean = word.replace(Regex("[^a-zA-Z\\-']"), "")
                                if (clean.length >= 2) {
                                    vm.openDictionary(clean, sentence)
                                }
                            },
                            onSentenceLongPress = { sentence ->
                                if (sentence.isNotBlank()) {
                                    vm.openTranslation(sentence)
                                }
                            },
                            onTranslateParagraph = { vm.translateParagraph(index, paragraph) }
                        )
                    }
                } else if (articleData.description.isNotBlank()) {
                    item(key = "description") {
                        ParagraphText(
                            text = articleData.description.trim(),
                            fontSize = rssFontSize,
                            onWordTap = { word, sentence ->
                                val clean = word.replace(Regex("[^a-zA-Z\\-']"), "")
                                if (clean.length >= 2) {
                                    vm.openDictionary(clean, sentence)
                                }
                            },
                            onSentenceLongPress = { sentence ->
                                if (sentence.isNotBlank()) {
                                    vm.openTranslation(sentence)
                                }
                            }
                        )
                    }
                }

                item(key = "bottomSpacer") { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    if (vm.showDictionary) {
        DictionaryPopup(
            word = vm.selectedWord,
            exampleSentence = vm.selectedSentence,
            sourceUrl = articleData?.link.orEmpty(),
            sourceTitle = articleData?.title.orEmpty(),
            onDismiss = { vm.dismissDictionary() },
            onOpenChat = { vm.dismissDictionary(); vm.openChat(vm.selectedWord) },
            onOpenWebDictionary = { url ->
                vm.dismissDictionary()
                onOpenExternal(url)
            }
        )
    }
    if (vm.showTranslation) {
        TranslationPopup(text = vm.selectedSentence, onDismiss = { vm.dismissTranslation() }, onOpenChat = { vm.dismissTranslation(); vm.openChat(vm.selectedSentence) })
    }
    if (vm.showChat) {
        ChatDialog(initialContext = vm.selectedSentence.ifEmpty { vm.selectedWord }, onDismiss = { vm.dismissChat() })
    }
    if (showTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text(stringResource(R.string.tags)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (allTags.isEmpty()) {
                        Text(
                            stringResource(R.string.no_tags_yet_create_one_below),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        allTags.forEach { tag ->
                            val attached = articleTags.any { it.id == tag.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (attached) vm.detachTag(tag.id) else vm.attachTag(tag.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (attached) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(tag.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = { Text(stringResource(R.string.new_tag)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (newTagName.isNotBlank()) {
                                    vm.createTag(newTagName)
                                    newTagName = ""
                                }
                            },
                            enabled = newTagName.isNotBlank()
                        ) {
                            Text(stringResource(R.string.add))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        )
    }

    if (vm.showAiPanel) {
        AlertDialog(
            onDismissRequest = { vm.dismissAiPanel() },
            title = { Text(vm.aiPanelTitle) },
            text = {
                if (vm.isAnalyzing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(vm.aiPanelContent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAiPanel() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ParagraphBlock(
    index: Int,
    text: String,
    fontSize: Float,
    isTranslating: Boolean,
    translation: String?,
    onWordTap: (word: String, sentence: String) -> Unit,
    onSentenceLongPress: (String) -> Unit,
    onTranslateParagraph: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ParagraphText(
            text = text,
            fontSize = fontSize,
            onWordTap = onWordTap,
            onSentenceLongPress = onSentenceLongPress
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onTranslateParagraph,
                enabled = !isTranslating
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (isTranslating) "Translating..." else stringResource(R.string.translate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isTranslating -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.translating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            translation != null && translation.isNotBlank() -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Text(
                        translation,
                        fontSize = (fontSize - 1).sp,
                        lineHeight = (fontSize * 1.6).sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ParagraphText(
    text: String,
    fontSize: Float,
    onWordTap: (word: String, sentence: String) -> Unit,
    onSentenceLongPress: (String) -> Unit
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(fontSize = fontSize.sp, lineHeight = (fontSize * 1.7).sp, color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .pointerInput(text) {
                detectTapGestures(
                    onTap = { offset ->
                        layoutResult?.let { layout ->
                            val charIndex = layout.getOffsetForPosition(offset)
                            extractWordAtOffset(text, charIndex)?.let { word ->
                                onWordTap(word, extractSentenceAtOffset(text, charIndex))
                            }
                        }
                    },
                    onLongPress = { offset ->
                        layoutResult?.let { layout ->
                            val charIndex = layout.getOffsetForPosition(offset)
                            onSentenceLongPress(extractSentenceAtOffset(text, charIndex))
                        }
                    }
                )
            },
        onTextLayout = { result -> layoutResult = result }
    )
}

private fun extractWordAtOffset(text: String, offset: Int): String? {
    if (text.isBlank()) return null
    val pos = offset.coerceIn(0, text.length - 1)
    var start = pos
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    var end = pos
    while (end < text.length && !text[end].isWhitespace()) end++
    if (start >= end) return null
    return text.substring(start, end).trim()
}

private fun extractSentenceAtOffset(text: String, offset: Int): String {
    if (text.isBlank()) return text
    val pos = offset.coerceIn(0, text.length - 1)

    var start = 0
    var idx = pos
    while (idx > 0) {
        val ch = text[idx - 1]
        if (ch == '\n') { start = idx; break }
        if ((ch == '.' || ch == '!' || ch == '?') && idx < text.length && text[idx].isWhitespace()) {
            start = idx
            break
        }
        idx--
    }

    var end = text.length
    idx = pos
    while (idx < text.length) {
        val ch = text[idx]
        if (ch == '\n') { end = idx; break }
        if ((ch == '.' || ch == '!' || ch == '?') && (idx + 1 >= text.length || text[idx + 1].isWhitespace())) {
            end = idx + 1
            break
        }
        idx++
    }

    return text.substring(start, end).trim()
}

private val PUB_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.getDefault())

private fun formatPubDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(PUB_DATE_FORMATTER)
}
