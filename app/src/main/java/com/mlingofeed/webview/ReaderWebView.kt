package com.mlingofeed.webview

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

fun injectSelectionScript(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            if (window.__webReaderInjected) {
                window.__webReaderInjected = false;
                var oldScript = document.getElementById('__webReaderSelectionScript');
                if (oldScript) oldScript.remove();
            }

            var script = document.createElement('script');
            script.id = '__webReaderSelectionScript';
            script.textContent = `
            (function() {
                if (window.__webReaderSelectionActive) return;
                window.__webReaderSelectionActive = true;

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
                        before.lastIndexOf('\\n') + 1
                    );
                    var sentence = text.substring(sentenceStart).split(/[.!?\\n]/)[0].trim();

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
            `;
            document.documentElement.appendChild(script);
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
            window.__wrTexts = [];
            window.__wrPickedUp = {};

            var count = 0;
            var capturedTexts = {};

            function isValidText(text) {
                if (!text || text.length < 15) return false;
                if (!/[a-zA-Z]{3,}/.test(text)) return false;
                var letterCount = text.match(/[a-zA-Z]/g);
                if (!letterCount || letterCount.length < 8) return false;
                if (text.length > 3000) return false;
                if (capturedTexts[text]) return false;
                capturedTexts[text] = true;
                return true;
            }

            function addParagraph(text, refNode, container) {
                if (!isValidText(text)) return false;
                if (text.length > 2000) text = text.substring(0, 2000);
                window.__wrTexts[count] = text;
                var transDiv = document.createElement('div');
                transDiv.className = '__wr-translation __wr-translation-loading';
                transDiv.setAttribute('data-paragraph-index', count);
                transDiv.textContent = 'Translating...';
                if (refNode && refNode.parentNode) {
                    if (refNode.nextSibling) {
                        refNode.parentNode.insertBefore(transDiv, refNode.nextSibling);
                    } else {
                        refNode.parentNode.appendChild(transDiv);
                    }
                } else if (container) {
                    container.appendChild(transDiv);
                } else {
                    var containers = document.querySelectorAll('.article_content, .article_right, article, [role="article"], .entry-content, .post-body, .story-body, .article-body, .content-body, .article__content, .story-content');
                    if (containers.length > 0) {
                        containers[0].appendChild(transDiv);
                    } else {
                        document.body.appendChild(transDiv);
                    }
                }
                count++;
                return true;
            }

            var contentTags = 'p, li, dd, dt, blockquote, pre, h1, h2, h3, h4, h5, h6, figcaption, td, th, summary';
            document.querySelectorAll(contentTags).forEach(function(el) {
                if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'NOSCRIPT') return;
                if (el.closest('.__wr-translation')) return;
                var rect = el.getBoundingClientRect();
                if (rect.height === 0 || rect.width === 0) return;
                var computed = window.getComputedStyle(el);
                if (computed.display === 'none' || computed.visibility === 'hidden') return;
                addParagraph(el.textContent.trim(), el, null);
            });

            var articleSelectors = '.article_content, .article_right, article, [role="article"], .entry-content, .post-body, .story-body, .article-body, .content-body, .article__content, .story-content';
            document.querySelectorAll(articleSelectors).forEach(function(container) {
                var segText = '';
                var lastBr = null;
                var children = container.childNodes;
                for (var i = 0; i < children.length; i++) {
                    var node = children[i];
                    var isBr = false;
                    if (node.nodeType === 1) {
                        if (node.tagName === 'BR') { isBr = true; }
                        else if (node.tagName === 'P' || node.tagName === 'H1' || node.tagName === 'H2' || node.tagName === 'H3' || node.tagName === 'H4' || node.tagName === 'H5' || node.tagName === 'H6' || node.tagName === 'LI' || node.tagName === 'BLOCKQUOTE') {
                            if (segText.length >= 15) { addParagraph(segText, lastBr || node, container); segText = ''; lastBr = null; }
                            addParagraph(node.textContent.trim(), node, null);
                            continue;
                        }
                        else if (node.classList && node.classList.contains('__wr-translation')) { continue; }
                        else if (node.tagName === 'IMG' || node.tagName === 'CENTER' || node.tagName === 'IFRAME' || node.tagName === 'SCRIPT' || node.tagName === 'STYLE') {
                            if (segText.length >= 15) { addParagraph(segText, lastBr || node, container); segText = ''; lastBr = null; }
                            continue;
                        }
                        else {
                            if (node.matches && node.matches('.article_content, .article_right, article, [role="article"], .entry-content, .post-body, .story-body, .article-body, .content-body, .article__content, .story-content')) {
                                if (segText.length >= 15) { addParagraph(segText, lastBr || node, container); segText = ''; lastBr = null; }
                                continue;
                            }
                            var directText = '';
                            for (var j = 0; j < node.childNodes.length; j++) {
                                if (node.childNodes[j].nodeType === 3) directText += node.childNodes[j].textContent;
                            }
                            if (directText.trim().length > 0) { segText += directText; continue; }
                            if (segText.length >= 15) { addParagraph(segText, lastBr || node, container); segText = ''; lastBr = null; }
                            continue;
                        }
                    } else if (node.nodeType === 3) {
                        segText += node.textContent;
                    }
                    if (isBr) {
                        if (segText.length >= 15) { addParagraph(segText, lastBr || node, container); }
                        segText = '';
                        lastBr = node;
                    }
                }
                if (segText.length >= 15) { addParagraph(segText, lastBr || null, container); }
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
            if (!el) {
                el = document.createElement('div');
                el.className = '__wr-translation';
                el.setAttribute('data-paragraph-index', '${index}');
                var containers = document.querySelectorAll('.article_content, .article_right, article, [role="article"], .entry-content, .post-body, .story-body, .article-body, .content-body, .article__content, .story-content');
                if (containers.length > 0) {
                    containers[0].appendChild(el);
                } else {
                    document.body.appendChild(el);
                }
            } else {
                el.className = '__wr-translation';
            }
            el.textContent = '';
            var t = document.createTextNode(${quoted});
            el.appendChild(t);
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
            window.__wrTexts = [];
            window.__wrPickedUp = {};
        })();
        """.trimIndent(),
        null
    )
}