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
    onPageFinished: (String?) -> Unit,
    onPageStarted: () -> Unit = {}
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
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageStarted()
            }

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

fun prepareTranslationParagraphs(webView: WebView?, onDone: ((Int) -> Unit)? = null) {
    webView?.evaluateJavascript(
        """
        (function() {
            var existing = document.querySelectorAll('.__wr-translation');
            existing.forEach(function(el) { el.remove(); });

            var contentTags = 'p, li, dd, dt, blockquote, pre, h1, h2, h3, h4, h5, h6, figcaption, td, th, summary';
            var allElements = document.querySelectorAll(contentTags);

            var count = 0;
            allElements.forEach(function(el) {
                if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'NOSCRIPT') return;
                if (el.closest('.__wr-translation')) return;

                var rect = el.getBoundingClientRect();
                if (rect.height === 0 || rect.width === 0) return;

                var computed = window.getComputedStyle(el);
                if (computed.display === 'none' || computed.visibility === 'hidden') return;

                var text = el.textContent.trim();
                if (text.length < 15) return;
                if (!/[a-zA-Z]{3,}/.test(text)) return;
                var letterCount = text.match(/[a-zA-Z]/g);
                if (!letterCount || letterCount.length < 8) return;
                if (text.length > 3000) return;

                var next = el.nextElementSibling;
                if (next && next.classList.contains('__wr-translation')) return;

                el.setAttribute('data-wr-para', count);

                var transDiv = document.createElement('div');
                transDiv.className = '__wr-translation __wr-translation-loading';
                transDiv.setAttribute('data-paragraph-index', count);
                transDiv.textContent = 'Translating...';

                if (el.nextSibling) {
                    el.parentNode.insertBefore(transDiv, el.nextSibling);
                } else {
                    el.parentNode.appendChild(transDiv);
                }

                count++;
            });

            return '' + count;
        })();
        """.trimIndent()
    ) { value ->
        val count = value?.trim('"')?.toIntOrNull() ?: 0
        onDone?.invoke(count)
    }
}

fun updateParagraphTranslation(webView: WebView?, index: Int, translation: String) {
    val quoted = org.json.JSONObject.quote(translation)

    webView?.evaluateJavascript(
        """
        (function() {
            var el = document.querySelector('[data-paragraph-index="${index}"]');
            if (el) {
                el.className = '__wr-translation';
                el.textContent = '';
                var t = document.createTextNode(${quoted});
                el.appendChild(t);
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
