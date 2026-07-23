package com.webreader.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
fun createReaderWebView(
    context: Context,
    onWordTapped: (String) -> Unit,
    onSentenceLongPressed: (String) -> Unit,
    onPageFinished: (String?) -> Unit
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        addJavascriptInterface(
            WebAppInterface(onWordTapped, onSentenceLongPressed),
            "Android"
        )

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectSelectionScript(view)
                onPageFinished(view?.title)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        webChromeClient = WebChromeClient()
    }
}

private fun injectSelectionScript(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            if (window.__webReaderInjected) return;
            window.__webReaderInjected = true;

            let longPressTimer = null;
            let isLongPress = false;
            let isScrolling = false;
            let touchStartX = 0;
            let touchStartY = 0;

            document.documentElement.style.webkitUserSelect = 'none';
            document.documentElement.style.userSelect = 'none';
            document.documentElement.style.webkitTouchCallout = 'none';

            document.addEventListener('contextmenu', function(e) {
                e.preventDefault();
                e.stopPropagation();
                return false;
            }, true);

            function getWordAtPoint(x, y) {
                var range = document.caretRangeFromPoint(x, y);
                if (!range) return null;
                var textNode = range.startContainer;
                if (textNode.nodeType !== Node.TEXT_NODE) return null;
                var offset = range.startOffset;
                var text = textNode.textContent;

                var start = offset;
                while (start > 0 && /[a-zA-Z'-]/.test(text[start - 1])) start--;

                var end = offset;
                while (end < text.length && /[a-zA-Z'-]/.test(text[end])) end++;

                var word = text.substring(start, end);
                if (word.length === 0) return null;

                var before = text.substring(0, start);
                var sentenceStart = Math.max(
                    before.lastIndexOf('.') + 1,
                    before.lastIndexOf('!') + 1,
                    before.lastIndexOf('?') + 1,
                    before.lastIndexOf('\n') + 1
                );
                var sentence = text.substring(sentenceStart).split(/[.!?\n]/)[0].trim();

                return { word: word, sentence: sentence };
            }

            function clearNativeSelection() {
                if (window.getSelection) {
                    window.getSelection().removeAllRanges();
                }
            }

            document.addEventListener('touchstart', function(e) {
                var touch = e.touches[0];
                touchStartX = touch.clientX;
                touchStartY = touch.clientY;
                isLongPress = false;
                isScrolling = false;

                longPressTimer = setTimeout(function() {
                    isLongPress = true;
                    clearNativeSelection();
                    var result = getWordAtPoint(touchStartX, touchStartY);
                    if (result && result.sentence) {
                        Android.onSentenceSelected(result.sentence);
                    }
                }, 500);
            }, true);

            document.addEventListener('touchmove', function(e) {
                var touch = e.touches[0];
                if (Math.abs(touch.clientX - touchStartX) > 10 ||
                    Math.abs(touch.clientY - touchStartY) > 10) {
                    isScrolling = true;
                    clearTimeout(longPressTimer);
                }
            }, true);

            document.addEventListener('touchend', function(e) {
                clearTimeout(longPressTimer);
                if (!isLongPress && !isScrolling) {
                    var touch = e.changedTouches[0];
                    var result = getWordAtPoint(touch.clientX, touch.clientY);
                    if (result && result.word) {
                        Android.onWordSelected(result.word);
                    }
                }
                isLongPress = false;
                isScrolling = false;
            }, true);

            document.addEventListener('click', function(e) {
                var result = getWordAtPoint(e.clientX, e.clientY);
                if (result && result.word) {
                    Android.onWordSelected(result.word);
                }
            }, true);
        })();
        """.trimIndent(),
        null
    )
}

fun injectTranslationStyles(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            if (!document.getElementById('__webReaderTranslationStyle')) {
                var style = document.createElement('style');
                style.id = '__webReaderTranslationStyle';
                style.textContent = `
                    .__wr-translation {
                        color: #1976D2;
                        font-size: 0.92em;
                        line-height: 1.6;
                        padding: 6px 10px;
                        margin: 2px 0 8px 0;
                        background: #E3F2FD;
                        border-left: 3px solid #1976D2;
                        border-radius: 0 4px 4px 0;
                        display: block;
                    }
                    .__wr-translation-loading {
                        color: #999;
                        font-style: italic;
                    }
                `;
                document.head.appendChild(style);
            }
        })();
        """.trimIndent(),
        null
    )
}

fun prepareTranslationParagraphs(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            // Remove any existing translations first
            var existing = document.querySelectorAll('.__wr-translation');
            existing.forEach(function(el) { el.remove(); });

            // Find all paragraph-like elements
            var selectors = 'p, li, td, th, blockquote, pre, h1, h2, h3, h4, h5, h6';
            var elements = document.querySelectorAll(selectors);

            var count = 0;
            elements.forEach(function(el) {
                var text = el.textContent.trim();
                // Only translate paragraphs with meaningful content
                if (text.length < 10) return;
                if (!/[a-zA-Z]{3,}/.test(text)) return;

                // Create translation container
                var transDiv = document.createElement('div');
                transDiv.className = '__wr-translation __wr-translation-loading';
                transDiv.setAttribute('data-paragraph-index', count);
                transDiv.textContent = 'Translating...';

                // Insert after the element
                if (el.nextSibling) {
                    el.parentNode.insertBefore(transDiv, el.nextSibling);
                } else {
                    el.parentNode.appendChild(transDiv);
                }

                count++;
            });

            return count;
        })();
        """.trimIndent(),
        null
    )
}

fun getParagraphText(webView: WebView?, index: Int, callback: (String?) -> Unit) {
    webView?.evaluateJavascript(
        """
        (function() {
            var el = document.querySelector('[data-paragraph-index="${index}"]');
            if (!el) return null;
            var prev = el.previousElementSibling;
            if (!prev) return null;
            return prev.textContent.trim();
        })();
        """.trimIndent()
    ) { value ->
        val result = if (value != null && value != "null") {
            value.trim('"').replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t")
        } else {
            null
        }
        callback(result)
    }
}

fun updateParagraphTranslation(webView: WebView?, index: Int, translation: String) {
    val escapedTranslation = translation
        .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\n", "\\n")

    webView?.evaluateJavascript(
        """
        (function() {
            var el = document.querySelector('[data-paragraph-index="${index}"]');
            if (el) {
                el.className = '__wr-translation';
                el.textContent = `${escapedTranslation}`;
            }
        })();
        """.trimIndent(),
        null
    )
}

fun clearPageTranslations(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            var existing = document.querySelectorAll('.__wr-translation');
            existing.forEach(function(el) { el.remove(); });
        })();
        """.trimIndent(),
        null
    )
}
