package com.mlingofeed.webview

import android.webkit.JavascriptInterface

class WebAppInterface(
    private val onWordTapped: (word: String, sentence: String) -> Unit,
    private val onSentenceLongPressed: (String) -> Unit
) {
    @JavascriptInterface
    fun onWordSelected(word: String, sentence: String) {
        onWordTapped(word, sentence)
    }

    @JavascriptInterface
    fun onSentenceSelected(sentence: String) {
        onSentenceLongPressed(sentence)
    }
}
