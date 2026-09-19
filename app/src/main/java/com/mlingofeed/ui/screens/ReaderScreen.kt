package com.mlingofeed.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import com.mlingofeed.ui.components.ChatDialog
import com.mlingofeed.ui.components.DictionaryPopup
import com.mlingofeed.ui.components.TranslationPopup
import com.mlingofeed.webview.createReaderWebView
import com.mlingofeed.webview.setSelectionScriptEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: ReaderViewModel = viewModel(factory = remember { AppViewModelFactory(app) })
    LaunchedEffect(initialUrl) { vm.ensureInitialTab(initialUrl) }
    ReadingTimer(app)

    val fontSize by vm.fontSize.collectAsStateWithLifecycle()

    val currentTab = vm.currentTab

    BackHandler {
        currentTab?.webView?.let { wv ->
            if (wv.canGoBack()) wv.goBack() else onBack()
        } ?: onBack()
    }

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
                        IconButton(onClick = onGoHome) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
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
            AndroidView(
                factory = { ctx -> FrameLayout(ctx) },
                update = { container ->
                    val tab = vm.currentTab
                    val wv = tab?.takeIf { it.url != "about:blank" }?.webView
                    if (wv != null) {
                        if (wv.parent !== container) {
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            container.removeAllViews()
                            container.addView(
                                wv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    } else if (container.childCount > 0) {
                        container.removeAllViews()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (currentTab != null && currentTab.url == "about:blank") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewTabPage(app = app, onOpenUrl = { currentTab.url = it })
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { vm.toggleWordSelection() },
                color = if (vm.wordSelectionEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "选词",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (vm.wordSelectionEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    LaunchedEffect(vm.selectedIndex, currentTab?.url, fontSize) {
        val tab = vm.currentTab ?: return@LaunchedEffect
        val targetUrl = tab.url
        if (targetUrl == "about:blank") return@LaunchedEffect
        if (tab.webView == null) {
            val wv = createReaderWebView(
                context = context,
                selectionEnabled = { vm.wordSelectionEnabled },
                onWordTapped = { if (vm.wordSelectionEnabled) vm.openDictionary(it) },
                onSentenceLongPressed = { vm.openTranslation(it) },
                onPageFinished = { title -> vm.onPageLoaded(tab, targetUrl, title) }
            )
            tab.webView = wv
            wv.loadUrl(targetUrl)
        }
        tab.webView?.settings?.textZoom = fontSize
        setSelectionScriptEnabled(tab.webView, vm.wordSelectionEnabled)
    }

    LaunchedEffect(vm.wordSelectionEnabled, vm.selectedIndex) {
        vm.currentTab?.webView?.let { setSelectionScriptEnabled(it, vm.wordSelectionEnabled) }
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
    val bookmarks by app.bookmarkRepository.allBookmarks.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quick Access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

        if (bookmarks.isEmpty()) {
            Text("No bookmarks yet. Add bookmarks from the home screen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
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
private fun ReadingTimer(app: WebReaderApp) {
    val scope = rememberCoroutineScope()
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
                            if (accumulatedSeconds >= 60) {
                                val seconds = accumulatedSeconds
                                accumulatedSeconds = 0L
                                app.applicationScope.launch { app.settingsManager.addReadingSession(seconds) }
                            }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    timerJob?.cancel()
                    timerJob = null
                    if (accumulatedSeconds > 0) {
                        val seconds = accumulatedSeconds
                        accumulatedSeconds = 0L
                        app.applicationScope.launch { app.settingsManager.addReadingSession(seconds) }
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
                app.applicationScope.launch { app.settingsManager.addReadingSession(accumulatedSeconds) }
            }
        }
    }
}
