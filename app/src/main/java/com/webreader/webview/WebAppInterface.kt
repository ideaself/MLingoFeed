package com.webreader.webview

import android.webkit.JavascriptInterface

class WebAppInterface(
    private val onWordTapped: (String) -> Unit,
    private val onSentenceLongPressed: (String) -> Unit
) {
    @JavascriptInterface
    fun onWordSelected(word: String) {
        onWordTapped(word)
    }

    @JavascriptInterface
    fun onSentenceSelected(sentence: String) {
        onSentenceLongPressed(sentence)
    }
}
