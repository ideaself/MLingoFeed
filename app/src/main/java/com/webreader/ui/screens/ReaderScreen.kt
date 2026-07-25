package com.webreader.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.webreader.WebReaderApp
import com.webreader.data.database.Bookmark
import com.webreader.ui.components.ChatDialog
import com.webreader.ui.components.DictionaryPopup
import com.webreader.ui.components.TranslationPopup
import com.webreader.webview.createReaderWebView
import com.webreader.webview.clearPageTranslations
import com.webreader.webview.injectSelectionScript
import com.webreader.webview.injectTranslationStyles
import com.webreader.webview.prepareTranslationParagraphs
import com.webreader.webview.updateParagraphTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

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
    val fontSize by app.settingsManager.fontSize.collectAsState(initial = 100)

    LaunchedEffect(url) {
        val bookmark = app.bookmarkRepository.getBookmarkByUrl(url)
        isBookmarked = bookmark != null
    }

    LaunchedEffect(pageTitle) {
        if (pageTitle != "Loading..." && pageTitle.isNotBlank()) {
            app.historyRepository.recordVisit(pageTitle, url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                val scrollY = wv.scrollY
                if (scrollY > 0) {
                    scope.launch { app.bookmarkRepository.updateScrollPosition(url, scrollY) }
                }
                wv.destroy()
            }
        }
    }

    LaunchedEffect(url) {
        val bookmark = app.bookmarkRepository.getBookmarkByUrl(url)
        if (bookmark != null && bookmark.scrollPosition > 0) {
            kotlinx.coroutines.delay(500)
            webView?.scrollTo(0, bookmark.scrollPosition)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var accumulatedSeconds = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    scope.launch {
                        while (isActive) {
                            delay(1000)
                            accumulatedSeconds++
                            if (accumulatedSeconds % 5 == 0L) {
                                app.settingsManager.addReadingSession(accumulatedSeconds)
                                accumulatedSeconds = 0L
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (accumulatedSeconds > 0) {
                        scope.launch { app.settingsManager.addReadingSession(accumulatedSeconds) }
                        accumulatedSeconds = 0L
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (accumulatedSeconds > 0) {
                scope.launch { app.settingsManager.addReadingSession(accumulatedSeconds) }
            }
        }
    }

    LaunchedEffect(fontSize) {
        webView?.settings?.textZoom = fontSize
    }

    fun startTranslation() {
        if (isTranslating) {
            isTranslating = false
            translateProgress = ""
            return
        }

        if (apiKey.isBlank()) return

        isTranslating = true
        translateProgress = "Preparing..."

        scope.launch {
            val paraCount = withContext(Dispatchers.Main) {
                injectTranslationStyles(webView)
                val wv = webView
                if (wv == null) 0
                else suspendCancellableCoroutine<Int> { cont ->
                    prepareTranslationParagraphs(wv) { count ->
                        if (cont.isActive) cont.resume(count) {}
                    }
                }
            }

            if (paraCount == 0 || !isTranslating) {
                isTranslating = false
                withContext(Dispatchers.Main) {
                    webView?.evaluateJavascript("""
                        (function() {
                            var loadings = document.querySelectorAll('.__wr-translation-loading');
                            loadings.forEach(function(el) { el.remove(); });
                        })();
                    """.trimIndent(), null)
                }
                translateProgress = ""
                if (paraCount == 0) {
                    translateProgress = "No content to translate"
                    delay(1500)
                    translateProgress = ""
                }
                return@launch
            }

            var currentIndex = 0
            var totalToProcess = paraCount

            while (isTranslating && isActive && currentIndex < totalToProcess) {
                val result = withContext(Dispatchers.Main) {
                    getTextByIndex(webView, currentIndex)
                }

                if (result == null) {
                    currentIndex++
                    continue
                }

                val text = result
                translateProgress = "Translating ${currentIndex + 1}/$totalToProcess..."

                val translation = withContext(Dispatchers.IO) {
                    try {
                        app.chatRepository.translate(
                            text = text,
                            targetLang = targetLang,
                            apiUrl = apiUrl,
                            apiKey = apiKey,
                            model = model
                        )
                    } catch (e: Exception) {
                        "Translation error: ${e.message}"
                    }
                }

                withContext(Dispatchers.Main) {
                    updateParagraphTranslation(webView, currentIndex, translation)
                }

                currentIndex++
                delay(50)
            }

            isTranslating = false
            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript("""
                    (function() {
                        var loadings = document.querySelectorAll('.__wr-translation-loading');
                        loadings.forEach(function(el) { el.remove(); });
                    })();
                """.trimIndent(), null)
            }
            translateProgress = ""
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = pageTitle,
                                maxLines = 1
                            )
                            if (isTranslating && translateProgress.isNotEmpty()) {
                                Text(
                                    text = translateProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = {
                            if (webView?.canGoBack() == true) {
                                webView?.goBack()
                            } else {
                                onBack()
                            }
                        }) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        androidx.compose.material3.IconButton(
                            onClick = { startTranslation() }
                        ) {
                            Text(text = if (isTranslating) "⏹" else "译")
                        }
                        androidx.compose.material3.IconButton(onClick = {
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
                            androidx.compose.material3.Icon(
                                if (isBookmarked) androidx.compose.material.icons.Icons.Filled.Bookmark
                                else androidx.compose.material.icons.Icons.Filled.BookmarkBorder,
                                contentDescription = "Bookmark"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            factory = { ctx ->
                createReaderWebView(
                    context = ctx,
                    onWordTapped = { word ->
                        selectedWord = word
                        showDictionary = true
                    },
                    onSentenceLongPressed = { sentence ->
                        selectedSentence = sentence
                        showTranslation = true
                    },
                    onPageFinished = { title ->
                        title?.let { pageTitle = it }
                        isLoading = false
                    },
                    onPageStarted = {
                        if (isTranslating) {
                            isTranslating = false
                            translateProgress = ""
                        }
                        injectSelectionScript(webView)
                    }
                ).also { wv ->
                    webView = wv
                    wv.loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

private suspend fun getTextByIndex(webView: WebView?, index: Int): String? {
    if (webView == null) return null
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(
            """
            (function() {
                var texts = window.__wrTexts || [];
                var text = texts[${index}];
                if (!text || text.length === 0) return null;
                if (text.length > 2000) text = text.substring(0, 2000);
                return JSON.stringify(text);
            })();
            """.trimIndent()
        ) { value ->
            if (!continuation.isActive) return@evaluateJavascript
            if (value != null && value != "null") {
                try {
                    val cleaned = value.trim('"').replace("\\\"", "\"").replace("\\n", "\n")
                    continuation.resume(cleaned) {}
                } catch (e: Exception) {
                    continuation.resume(null) {}
                }
            } else {
                continuation.resume(null) {}
            }
        }
    }
}

