package com.webreader.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.webreader.WebReaderApp
import com.webreader.data.database.RssArticle
import com.webreader.data.repository.RssParser
import com.webreader.ui.components.ChatDialog
import com.webreader.ui.components.DictionaryPopup
import com.webreader.ui.components.TranslationPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val subscriptions by app.rssRepository.allSubscriptions.collectAsState(initial = emptyList())
    var article by remember { mutableStateOf<RssArticle?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var isLoadingContent by remember { mutableStateOf(false) }
    var fullContent by remember { mutableStateOf<String?>(null) }

    var showDictionary by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf("") }
    var selectedSentence by remember { mutableStateOf("") }

    var isTranslatingAll by remember { mutableStateOf(false) }
    var translateProgress by remember { mutableStateOf("") }
    val translatedParagraphs = remember { mutableStateMapOf<Int, String>() }
    val translatingParagraphs = remember { mutableStateMapOf<Int, Boolean>() }

    var fontSize by remember { mutableFloatStateOf(17f) }

    val apiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val apiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val model by app.settingsManager.aiModel.collectAsState(initial = "")
    val targetLang by app.settingsManager.translateTargetLang.collectAsState(initial = "Chinese")

    LaunchedEffect(articleId) {
        val loaded = app.rssRepository.getArticleById(articleId)
        article = loaded
        isFavorite = loaded?.isFavorite ?: false
        if (loaded != null && !loaded.isRead) {
            app.rssRepository.markAsRead(articleId)
        }
        if (loaded != null && loaded.content.isBlank()) {
            isLoadingContent = true
            val content = RssParser.fetchFullContent(loaded.link)
            app.rssRepository.updateArticleContent(articleId, content)
            fullContent = content
            isLoadingContent = false
        } else if (loaded != null) {
            fullContent = loaded.content
        }
    }

    val paragraphs = remember(fullContent) {
        if (fullContent.isNullOrBlank()) emptyList()
        else fullContent!!.split("\n\n").filter { it.isNotBlank() }
    }

    fun translateParagraph(index: Int, text: String) {
        if (apiKey.isBlank()) return
        val trimmed = text.trim()
        if (trimmed.length < 3) return
        if (translatingParagraphs[index] == true) return
        translatingParagraphs[index] = true
        scope.launch {
            val translation = withContext(Dispatchers.IO) {
                try {
                    app.chatRepository.translate(trimmed, targetLang, apiUrl, apiKey, model)
                } catch (e: Exception) { "Error: ${e.message}" }
            }
            translatedParagraphs[index] = translation
            translatingParagraphs.remove(index)
        }
    }

    fun translateAll() {
        if (isTranslatingAll) {
            isTranslatingAll = false
            translateProgress = ""
            return
        }
        if (apiKey.isBlank() || paragraphs.isEmpty()) return
        isTranslatingAll = true
        translateProgress = "Translating..."
        scope.launch {
            paragraphs.forEachIndexed { index, para ->
                if (!isTranslatingAll) return@launch
                val trimmed = para.trim()
                if (trimmed.length < 3) return@forEachIndexed
                translateProgress = "Translating ${index + 1}/${paragraphs.size}..."
                translatingParagraphs[index] = true
                val translation = withContext(Dispatchers.IO) {
                    try {
                        app.chatRepository.translate(trimmed, targetLang, apiUrl, apiKey, model)
                    } catch (e: Exception) { "Error: ${e.message}" }
                }
                translatedParagraphs[index] = translation
                translatingParagraphs.remove(index)
                kotlinx.coroutines.delay(50)
            }
            isTranslatingAll = false
            translateProgress = ""
        }
    }

    val articleData = article

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = articleData?.title ?: "Article",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isTranslatingAll && translateProgress.isNotEmpty()) {
                                Text(
                                    text = translateProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { translateAll() }) {
                            if (isTranslatingAll) {
                                Text(text = "\u23F9", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Default.Translate, contentDescription = "Translate all")
                            }
                        }
                        IconButton(onClick = {
                            article?.let { a ->
                                val shareText = "${a.title}\n\n${a.link}"
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share article"))
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            article?.let { onOpenExternal(it.link) }
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser")
                        }
                        IconButton(onClick = {
                            article?.let { a ->
                                clipboardManager.setText(AnnotatedString(a.link))
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            articleData?.let {
                FloatingActionButton(onClick = {
                    scope.launch {
                        app.rssRepository.toggleFavorite(articleId)
                        isFavorite = !isFavorite
                    }
                }) {
                    Icon(
                        if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                    )
                }
            }
        }
    ) { padding ->
        if (articleData == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    articleData.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = (fontSize + 4).sp,
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 13f..24f, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${fontSize.toInt()}sp", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isLoadingContent) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading full content...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (paragraphs.isNotEmpty()) {
                    Column {
                        paragraphs.forEachIndexed { index, paragraph ->
                            ParagraphBlock(
                                index = index,
                                text = paragraph.trim(),
                                fontSize = fontSize,
                                isTranslating = translatingParagraphs[index] == true,
                                translation = translatedParagraphs[index],
                                onWordTap = { word ->
                                    val clean = word.replace(Regex("[^a-zA-Z\\-']"), "")
                                    if (clean.length >= 2) {
                                        selectedWord = clean
                                        showDictionary = true
                                    }
                                },
                                onSentenceLongPress = { sentence ->
                                    if (sentence.isNotBlank()) {
                                        selectedSentence = sentence
                                        showTranslation = true
                                    }
                                },
                                onTranslateParagraph = { translateParagraph(index, paragraph) }
                            )
                        }
                    }
                } else if (articleData.description.isNotBlank()) {
                    ParagraphText(
                        text = articleData.description.trim(),
                        fontSize = fontSize,
                        onWordTap = { word ->
                            val clean = word.replace(Regex("[^a-zA-Z\\-']"), "")
                            if (clean.length >= 2) {
                                selectedWord = clean
                                showDictionary = true
                            }
                        },
                        onSentenceLongPress = { sentence ->
                            if (sentence.isNotBlank()) {
                                selectedSentence = sentence
                                showTranslation = true
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showDictionary) {
        DictionaryPopup(word = selectedWord, onDismiss = { showDictionary = false }, onOpenChat = { showDictionary = false; selectedSentence = selectedWord; showChat = true })
    }
    if (showTranslation) {
        TranslationPopup(text = selectedSentence, onDismiss = { showTranslation = false }, onOpenChat = { showTranslation = false; showChat = true })
    }
    if (showChat) {
        ChatDialog(initialContext = selectedSentence.ifEmpty { selectedWord }, onDismiss = { showChat = false })
    }
}

@Composable
private fun ParagraphBlock(
    index: Int,
    text: String,
    fontSize: Float,
    isTranslating: Boolean,
    translation: String?,
    onWordTap: (String) -> Unit,
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
                    if (isTranslating) "Translating..." else "Translate",
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
                    Text("Translating...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onWordTap: (String) -> Unit,
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
                                onWordTap(word)
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

private fun formatPubDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
