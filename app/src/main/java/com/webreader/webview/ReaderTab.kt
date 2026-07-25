package com.webreader.webview

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ReaderTab(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    initialUrl: String,
    initialTitle: String = "Loading..."
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var webView: WebView? = null
}
