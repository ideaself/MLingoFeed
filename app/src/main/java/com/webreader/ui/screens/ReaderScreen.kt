package com.webreader.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.webreader.WebReaderApp
import com.webreader.data.database.Bookmark
import com.webreader.ui.components.ChatDialog
import com.webreader.ui.components.DictionaryPopup
import com.webreader.ui.components.TranslationPopup
import com.webreader.webview.createReaderWebView
import com.webreader.webview.clearPageTranslations
import com.webreader.webview.getParagraphText
import com.webreader.webview.injectTranslationStyles
import com.webreader.webview.prepareTranslationParagraphs
import com.webreader.webview.updateParagraphTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Loading...") }
    var isBookmarked by rememberSaveable(url) { mutableStateOf(false) }
    var showDictionary by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translateProgress by remember { mutableStateOf("") }
    var selectedWord by remember { mutableStateOf("") }
    var selectedSentence by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(url) }
    var isLoading by remember { mutableStateOf(true) }

    val apiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val apiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val model by app.settingsManager.aiModel.collectAsState(initial = "")
    val targetLang by app.settingsManager.translateTargetLang.collectAsState(initial = "Chinese")

    LaunchedEffect(url) {
        val bookmark = app.bookmarkRepository.getBookmarkByUrl(url)
        isBookmarked = bookmark != null
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }

    fun startTranslation() {
        if (isTranslating) {
            // If already translating, clear translations
            clearPageTranslations(webView)
            isTranslating = false
            translateProgress = ""
            return
        }

        if (apiKey.isBlank()) return

        isTranslating = true
        translateProgress = "Preparing..."

        scope.launch {
            withContext(Dispatchers.Main) {
                injectTranslationStyles(webView)
                prepareTranslationParagraphs(webView)
            }

            delay(300)

            var index = 0
            while (isTranslating) {
                val text = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        getParagraphText(webView, index) { result ->
                            continuation.resume(result) {}
                        }
                    }
                }

                if (text.isNullOrBlank()) break

                translateProgress = "Translating ${index + 1}..."

                val translation = withContext(Dispatchers.IO) {
                    app.chatRepository.translate(
                        text = text,
                        targetLang = targetLang,
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = model
                    )
                }

                withContext(Dispatchers.Main) {
                    updateParagraphTranslation(webView, index, translation)
                }

                index++
            }

            isTranslating = false
            translateProgress = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = pageTitle,
                        maxLines = 1
                    )
                    if (isTranslating && translateProgress.isNotEmpty()) {
                        Text(
                            text = translateProgress
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = { startTranslation() },
                    enabled = !isTranslating || true
                ) {
                    Text(
                        text = if (isTranslating) "⏹" else "译",
                        modifier = Modifier
                    )
                }
                IconButton(onClick = {
                    scope.launch {
                        if (isBookmarked) {
                            app.bookmarkRepository.deleteByUrl(currentUrl)
                            isBookmarked = false
                        } else {
                            app.bookmarkRepository.insert(
                                Bookmark(title = pageTitle, url = currentUrl)
                            )
                            isBookmarked = true
                        }
                    }
                }) {
                    Icon(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Bookmark"
                    )
                }
            }
        )

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            factory = { ctx ->
                createReaderWebView(
                    context = ctx,
                    onWordTapped = { word ->
                        if (!isTranslating) {
                            selectedWord = word
                            showDictionary = true
                        }
                    },
                    onSentenceLongPressed = { sentence ->
                        if (!isTranslating) {
                            selectedSentence = sentence
                            showTranslation = true
                        }
                    },
                    onPageFinished = { title ->
                        title?.let { pageTitle = it }
                        isLoading = false
                    }
                ).also { wv ->
                    webView = wv
                    wv.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showDictionary) {
        DictionaryPopup(
            word = selectedWord,
            onDismiss = { showDictionary = false },
            onOpenChat = {
                showDictionary = false
                showChat = true
            }
        )
    }

    if (showTranslation) {
        TranslationPopup(
            text = selectedSentence,
            onDismiss = { showTranslation = false },
            onOpenChat = {
                showTranslation = false
                showChat = true
            }
        )
    }

    if (showChat) {
        ChatDialog(
            initialContext = selectedSentence.ifEmpty { selectedWord },
            onDismiss = { showChat = false }
        )
    }
}
