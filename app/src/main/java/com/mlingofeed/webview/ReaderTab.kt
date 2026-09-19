package com.mlingofeed.webview

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong

private val nextTabId = AtomicLong(System.currentTimeMillis())

class ReaderTab(
    val id: Long = nextTabId.incrementAndGet(),
    initialUrl: String,
    initialTitle: String = "Loading..."
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var webView by mutableStateOf<WebView?>(null)
}
