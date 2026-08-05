package com.mlingofeed.ui.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.ui.components.ChatDialog
import com.mlingofeed.ui.components.DictionaryPopup
import com.mlingofeed.ui.components.TranslationPopup
import com.mlingofeed.webview.ReaderTab
import com.mlingofeed.webview.createReaderWebView
import com.mlingofeed.webview.injectSelectionScript
import kotlinx.coroutines.Job
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
    val vm: ReaderViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    remember(initialUrl) { vm.ensureInitialTab(initialUrl) }

    val fontSize by vm.fontSize.collectAsState()

    val currentTab = vm.currentTab

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTab?.title ?: "Loading...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (vm.isTranslating && vm.translateProgress.isNotEmpty()) {
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
                        IconButton(onClick = {
                            currentTab?.webView?.let { wv ->
                                if (wv.canGoBack()) wv.goBack() else onBack()
                            } ?: onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.startTranslation() }) {
                            Text(
                                text = if (vm.isTranslating) "\u23F9" else "\u8BD1",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { vm.toggleBookmark() }) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                if (vm.tabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        vm.tabs.forEachIndexed { index, tab ->
                            val isSelected = index == vm.selectedIndex
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { vm.selectTab(index) },
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
                                    if (vm.tabs.size > 1) {
                                        IconButton(onClick = { vm.closeTab(index) }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { vm.addTab("about:blank") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            vm.tabs.forEachIndexed { index, tab ->
                if (index == vm.selectedIndex) {
                    key(tab.url) {
                        if (tab.url == "about:blank") {
                            NewTabPage(app = app, onOpenUrl = { tab.url = it })
                        } else {
                            WebViewContent(
                                tab = tab, url = tab.url, fontSize = fontSize, app = app,
                                onWordTapped = { vm.openDictionary(it) },
                                onSentenceLongPressed = { vm.openTranslation(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (vm.showDictionary) {
        DictionaryPopup(word = vm.selectedWord, onDismiss = { vm.dismissDictionary() }, onOpenChat = { vm.dismissDictionary(); vm.openChat(vm.selectedWord) })
    }
    if (vm.showTranslation) {
        TranslationPopup(text = vm.selectedSentence, onDismiss = { vm.dismissTranslation() }, onOpenChat = { vm.dismissTranslation(); vm.openChat(vm.selectedSentence) })
    }
    if (vm.showChat) {
        ChatDialog(initialContext = vm.selectedSentence.ifEmpty { vm.selectedWord }, onDismiss = { vm.dismissChat() })
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
        var timerJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    timerJob?.cancel()
                    timerJob = scope.launch {
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
                    timerJob?.cancel()
                    timerJob = null
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
            timerJob?.cancel()
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
