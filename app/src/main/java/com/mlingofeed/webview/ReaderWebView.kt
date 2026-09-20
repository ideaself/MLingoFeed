package com.mlingofeed.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
fun createReaderWebView(
    context: Context,
    onWordTapped: (word: String, sentence: String) -> Unit,
    onSentenceLongPressed: (String) -> Unit,
    onPageFinished: (url: String?, title: String?) -> Unit,
    onPageStarted: () -> Unit = {},
    selectionEnabled: () -> Boolean = { true }
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
                setSelectionScriptEnabled(view, selectionEnabled())
                onPageFinished(url, view?.title)
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
            if (window.__webReaderInjected) return;
            window.__webReaderInjected = true;

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
                let enabled = (window.__webReaderSelectionEnabled !== false);
                window.__webReaderSelectionEnabled = enabled;

                window.__webReaderSetSelectionEnabled = function(flag) {
                    enabled = !!flag;
                    window.__webReaderSelectionEnabled = enabled;
                    var uSelect = enabled ? 'none' : '';
                    document.documentElement.style.webkitUserSelect = uSelect;
                    document.documentElement.style.userSelect = uSelect;
                    document.documentElement.style.webkitTouchCallout = uSelect;
                };
                window.__webReaderSetSelectionEnabled(enabled);

                document.addEventListener('contextmenu', function(e) {
                    if (!enabled) return;
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

                    // When the word is inside a saved-word highlight, read the sentence from the
                    // enclosing block so the context is not just the highlighted span.
                    var sentenceSource = text;
                    var wordOffset = start;
                    var parentEl = textNode.parentElement;
                    if (parentEl && parentEl.classList && parentEl.classList.contains('__wr-saved-word')) {
                        var block = parentEl.closest('p, li, td, th, blockquote, dd, figcaption') || parentEl.parentElement;
                        if (block) {
                            sentenceSource = block.textContent || text;
                            var wordIndex = sentenceSource.toLowerCase().indexOf(word.toLowerCase());
                            wordOffset = wordIndex >= 0 ? wordIndex : 0;
                        }
                    }

                    var before = sentenceSource.substring(0, wordOffset);
                    var sentenceStart = Math.max(
                        before.lastIndexOf('.') + 1,
                        before.lastIndexOf('!') + 1,
                        before.lastIndexOf('?') + 1,
                        before.lastIndexOf('\\n') + 1
                    );
                    var sentence = sentenceSource.substring(sentenceStart).split(/[.!?\\n]/)[0].trim();

                    return { word: word, sentence: sentence };
                }

                function clearNativeSelection() {
                    if (window.getSelection) {
                        window.getSelection().removeAllRanges();
                    }
                }

                document.addEventListener('touchstart', function(e) {
                    if (!enabled) return;
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
                    if (!enabled) return;
                    var touch = e.touches[0];
                    if (Math.abs(touch.clientX - touchStartX) > 10 ||
                        Math.abs(touch.clientY - touchStartY) > 10) {
                        isScrolling = true;
                        clearTimeout(longPressTimer);
                    }
                }, true);

                document.addEventListener('touchend', function(e) {
                    if (!enabled) return;
                    clearTimeout(longPressTimer);
                    if (!isLongPress && !isScrolling) {
                        var touch = e.changedTouches[0];
                        var result = getWordAtPoint(touch.clientX, touch.clientY);
                        if (result && result.word) {
                            Android.onWordSelected(result.word, result.sentence || '');
                        }
                    }
                    isLongPress = false;
                    isScrolling = false;
                }, true);

                document.addEventListener('click', function(e) {
                    if (!enabled) return;
                    var result = getWordAtPoint(e.clientX, e.clientY);
                    if (result && result.word) {
                        Android.onWordSelected(result.word, result.sentence || '');
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

fun clearTranslationPlaceholders(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            var loadings = document.querySelectorAll('.__wr-translation-loading');
            loadings.forEach(function(el) { el.remove(); });
        })();
        """.trimIndent(),
        null
    )
}

fun highlightSentence(webView: WebView?, sentence: String) {
    val quoted = JSONObject.quote(sentence.take(80))
    webView?.evaluateJavascript(
        """
        (function() {
            var prev = document.querySelectorAll('.__wr-tts-highlight');
            for (var i = prev.length - 1; i >= 0; i--) {
                var el = prev[i];
                var parent = el.parentNode;
                if (!parent) continue;
                parent.replaceChild(document.createTextNode(el.textContent), el);
                parent.normalize();
            }
            var needle = $quoted;
            if (!needle || !document.body) return;
            function findNode(text) {
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                while (walker.nextNode()) {
                    var node = walker.currentNode;
                    var idx = node.nodeValue.indexOf(text);
                    if (idx >= 0 && node.parentElement && !node.parentElement.closest('script, style')) {
                        return { node: node, index: idx };
                    }
                }
                return null;
            }
            var hit = findNode(needle);
            if (!hit && needle.length > 24) {
                needle = needle.substring(0, 24);
                hit = findNode(needle);
            }
            if (!hit) return;
            var range = document.createRange();
            range.setStart(hit.node, hit.index);
            range.setEnd(hit.node, Math.min(hit.node.nodeValue.length, hit.index + needle.length));
            var span = document.createElement('span');
            span.className = '__wr-tts-highlight';
            span.style.cssText = 'background: rgba(255,214,0,.45); border-radius: 2px;';
            try { range.surroundContents(span); } catch (e) { return; }
            try { span.scrollIntoView({ block: 'center' }); } catch (e) {}
        })();
        """.trimIndent(),
        null
    )
}

fun clearSentenceHighlight(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            var prev = document.querySelectorAll('.__wr-tts-highlight');
            for (var i = prev.length - 1; i >= 0; i--) {
                var el = prev[i];
                var parent = el.parentNode;
                if (!parent) continue;
                parent.replaceChild(document.createTextNode(el.textContent), el);
                parent.normalize();
            }
        })();
        """.trimIndent(),
        null
    )
}

fun setSelectionScriptEnabled(webView: WebView?, enabled: Boolean) {
    webView?.evaluateJavascript(
        "window.__webReaderSetSelectionEnabled ? window.__webReaderSetSelectionEnabled($enabled) : null",
        null
    )
}

fun applyReadingAppearance(webView: WebView?, lineHeight: Float, serif: Boolean, darkWeb: Boolean = false) {
    webView?.evaluateJavascript(
        """
        (function() {
            if (!document.head) return;
            var style = document.getElementById('__wr-appearance-style');
            if (!style) {
                style = document.createElement('style');
                style.id = '__wr-appearance-style';
                document.head.appendChild(style);
            }
            style.textContent =
                'body, p, li, dd, blockquote, td, th { line-height: $lineHeight !important; }' +
                ($serif ? "body, p, li, dd, blockquote { font-family: Georgia, 'Times New Roman', serif !important; }" : '') +
                ($darkWeb ? "html { filter: invert(90%) hue-rotate(180deg) !important; background: #111 !important; } img, video, picture, svg, canvas, iframe { filter: invert(100%) hue-rotate(180deg) !important; }" : '');
        })();
        """.trimIndent(),
        null
    )
}

/**
 * Marks saved word-book words in the page. Passing [enabled] = false unwraps any previous
 * highlights. Words are matched on word boundaries, case-insensitively.
 */
fun highlightSavedWords(webView: WebView?, words: List<String>, enabled: Boolean) {
    val cleanedWords = words
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 }
        .distinct()
        .take(500)
    val wordsJson = JSONArray(cleanedWords).toString()

    webView?.evaluateJavascript(
        """
        (function() {
            if ($enabled) {
                if (!document.getElementById('__wr-saved-word-style')) {
                    var style = document.createElement('style');
                    style.id = '__wr-saved-word-style';
                    style.textContent = '.__wr-saved-word{background:rgba(255,214,0,.35);border-bottom:1px solid rgba(255,160,0,.8);border-radius:2px}';
                    document.head.appendChild(style);
                }
            } else {
                var style = document.getElementById('__wr-saved-word-style');
                if (style) style.remove();
            }

            var existing = document.querySelectorAll('.__wr-saved-word');
            for (var i = existing.length - 1; i >= 0; i--) {
                var mark = existing[i];
                var parent = mark.parentNode;
                if (!parent) continue;
                parent.replaceChild(document.createTextNode(mark.textContent), mark);
                parent.normalize();
            }
            if (!$enabled || !document.body) return;

            var words = $wordsJson;
            if (!words || !words.length) return;

            var lookup = {};
            for (var i = 0; i < words.length; i++) lookup[words[i]] = true;

            var pattern;
            try {
                // Lookarounds instead of \b so tokens like "c++" (ending in a non-word
                // character) still match, while "hellos" does not match "hello".
                pattern = new RegExp('(?<![A-Za-z0-9_])(?:' + words.map(function(w) {
                    return w.replace(/[.*+?^$(){}|[\]\\]/g, '\\${'$'}&');
                }).join('|') + ')(?![A-Za-z0-9_])', 'gi');
            } catch (e) {
                return;
            }

            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
                acceptNode: function(node) {
                    if (!node.nodeValue || node.nodeValue.length < 2) return NodeFilter.FILTER_REJECT;
                    var el = node.parentElement;
                    if (!el) return NodeFilter.FILTER_REJECT;
                    var tag = el.tagName;
                    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'TEXTAREA' ||
                        tag === 'INPUT' || tag === 'CODE' || tag === 'PRE') return NodeFilter.FILTER_REJECT;
                    if (el.closest('.__wr-saved-word')) return NodeFilter.FILTER_REJECT;
                    pattern.lastIndex = 0;
                    return pattern.test(node.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
                }
            });

            var nodes = [];
            while (walker.nextNode()) nodes.push(walker.currentNode);

            for (var n = 0; n < nodes.length; n++) {
                var textNode = nodes[n];
                var text = textNode.nodeValue;
                pattern.lastIndex = 0;
                var fragment = document.createDocumentFragment();
                var lastIndex = 0;
                var match;
                while ((match = pattern.exec(text)) !== null) {
                    if (!lookup[match[0].toLowerCase()]) continue;
                    if (match.index > lastIndex) {
                        fragment.appendChild(document.createTextNode(text.substring(lastIndex, match.index)));
                    }
                    var span = document.createElement('span');
                    span.className = '__wr-saved-word';
                    span.textContent = match[0];
                    fragment.appendChild(span);
                    lastIndex = match.index + match[0].length;
                }
                if (lastIndex === 0) continue;
                if (lastIndex < text.length) {
                    fragment.appendChild(document.createTextNode(text.substring(lastIndex)));
                }
                if (textNode.parentNode) {
                    textNode.parentNode.replaceChild(fragment, textNode);
                }
            }
        })();
        """.trimIndent(),
        null
    )
}