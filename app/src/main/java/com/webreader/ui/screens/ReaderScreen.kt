package com.webreader.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.webreader.WebReaderApp
import com.webreader.data.database.Bookmark
import com.webreader.ui.components.ChatDialog
import com.webreader.ui.components.DictionaryPopup
import com.webreader.ui.components.DifficultyDialog
import com.webreader.ui.components.TranslationPopup
import com.webreader.webview.ReaderTab
import com.webreader.webview.createReaderWebView
import com.webreader.webview.injectSelectionScript
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    val tabs = remember { mutableStateListOf(ReaderTab(initialUrl = initialUrl)) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showDictionary by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showDifficulty by remember { mutableStateOf(false) }
    var difficultyLoading by remember { mutableStateOf(false) }
    var difficultyResult by remember { mutableStateOf<String?>(null) }
    var selectedWord by remember { mutableStateOf("") }
    var selectedSentence by remember { mutableStateOf("") }

    val fontSize by app.settingsManager.fontSize.collectAsState(initial = 100)

    val currentTab = tabs.getOrNull(selectedIndex)

    fun addTab(url: String) {
        tabs.add(ReaderTab(initialUrl = url))
        selectedIndex = tabs.size - 1
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.getOrNull(index)?.webView?.destroy()
        tabs.removeAt(index)
        if (selectedIndex >= tabs.size) selectedIndex = tabs.size - 1
        if (selectedIndex < 0) selectedIndex = 0
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = currentTab?.title ?: "Loading...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            currentTab?.webView?.let { wv ->
                                if (wv.canGoBack()) wv.goBack() else onBack()
                            } ?: onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showDifficulty = true
                            difficultyResult = null
                        }) {
                            Text("D", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val tab = currentTab ?: return@launch
                                val url = tab.url
                                val isBookmarked = app.bookmarkRepository.getBookmarkByUrl(url) != null
                                if (isBookmarked) {
                                    app.bookmarkRepository.deleteByUrl(url)
                                } else {
                                    app.bookmarkRepository.insert(Bookmark(title = tab.title, url = url))
                                }
                            }
                        }) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (tabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = index == selectedIndex
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedIndex = index },
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tab.title.take(15),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        modifier = Modifier.width(100.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (tabs.size > 1) {
                                        IconButton(onClick = { closeTab(index) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { addTab("about:blank") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            tabs.forEachIndexed { index, tab ->
                if (index == selectedIndex) {
                    key(tab.url) {
                        if (tab.url == "about:blank") {
                            NewTabPage(app = app, onOpenUrl = { tab.url = it })
                        } else {
                            WebViewContent(
                                tab = tab, url = tab.url, fontSize = fontSize, app = app,
                                onWordTapped = { selectedWord = it; showDictionary = true },
                                onSentenceLongPressed = { selectedSentence = it; showTranslation = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDictionary) {
        DictionaryPopup(word = selectedWord, onDismiss = { showDictionary = false }, onOpenChat = { showDictionary = false; showChat = true })
    }
    if (showTranslation) {
        TranslationPopup(text = selectedSentence, onDismiss = { showTranslation = false }, onOpenChat = { showTranslation = false; showChat = true })
    }
    if (showChat) {
        ChatDialog(initialContext = selectedSentence.ifEmpty { selectedWord }, onDismiss = { showChat = false })
    }
    if (showDifficulty) {
        DifficultyDialog(
            isLoading = difficultyLoading,
            result = difficultyResult,
            onDismiss = { showDifficulty = false },
            onAnalyze = {
                difficultyLoading = true
                scope.launch {
                    val tab = currentTab ?: run {
                        difficultyLoading = false
                        return@launch
                    }
                    val wv = tab.webView
                    if (wv == null) {
                        difficultyLoading = false
                        return@launch
                    }
                    wv.evaluateJavascript("document.body.innerText") { text ->
                        if (text == null || text.length < 50) {
                            difficultyLoading = false
                            difficultyResult = "Error: No readable content found"
                            return@evaluateJavascript
                        }
                        val cleanText = text.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
                        scope.launch {
                            val apiUrl = app.settingsManager.aiApiUrl.first()
                            val apiKey = app.settingsManager.aiApiKey.first()
                            val model = app.settingsManager.aiModel.first()
                            if (apiKey.isBlank()) {
                                difficultyResult = "Error: Please configure AI API key in Settings"
                                difficultyLoading = false
                                return@launch
                            }
                            val result = app.chatRepository.analyzeDifficulty(cleanText, apiUrl, apiKey, model)
                            difficultyResult = result
                            difficultyLoading = false
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun NewTabPage(app: WebReaderApp, onOpenUrl: (String) -> Unit) {
    val bookmarks by app.bookmarkRepository.allBookmarks.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quick Access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

        if (bookmarks.isEmpty()) {
            Text("No bookmarks yet. Add bookmarks from the home screen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks) { bookmark ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenUrl(bookmark.url) },
                        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = bookmark.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = bookmark.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebViewContent(
    tab: ReaderTab, url: String, fontSize: Int, app: WebReaderApp,
    onWordTapped: (String) -> Unit, onSentenceLongPressed: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageTitleRef = remember { mutableStateOf("Loading...") }

    LaunchedEffect(pageTitleRef.value) {
        val title = pageTitleRef.value
        if (title != "Loading..." && title.isNotBlank()) {
            app.historyRepository.recordVisit(title, url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tab.webView?.let { wv ->
                val scrollY = wv.scrollY
                if (scrollY > 0) { scope.launch { app.bookmarkRepository.updateScrollPosition(url, scrollY) } }
                wv.destroy()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var accumulatedSeconds = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    scope.launch {
                        while (true) {
                            kotlinx.coroutines.delay(1000)
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

    LaunchedEffect(fontSize) { tab.webView?.settings?.textZoom = fontSize }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                createReaderWebView(
                    context = ctx,
                    onWordTapped = onWordTapped,
                    onSentenceLongPressed = onSentenceLongPressed,
                    onPageFinished = { title -> title?.let { pageTitleRef.value = it; tab.title = it } },
                    onPageStarted = { injectSelectionScript(tab.webView) }
                ).also { wv -> tab.webView = wv; wv.loadUrl(url) }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
