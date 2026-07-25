package com.webreader.webview

import android.webkit.WebView

fun injectReaderMode(webView: WebView?, callback: ((title: String, paragraphs: List<ReaderParagraph>, images: List<String>) -> Unit)? = null) {
    webView?.evaluateJavascript(
        """
        (function() {
            function getArticleContent() {
                var title = document.title || '';

                var article = null;
                var selectors = [
                    'article', '[role="article"]', '.article_content', '.article_right',
                    '.entry-content', '.post-body', '.story-body', '.article-body',
                    '.content-body', '.article__content', '.story-content', '.post-content',
                    '.main-content', '#article-content', '#main-content', '.markdown-body',
                    '.prose', '.rich-text', 'main'
                ];

                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el && el.textContent.trim().length > 200) {
                        article = el;
                        break;
                    }
                }

                if (!article) {
                    var paragraphs = document.querySelectorAll('p');
                    if (paragraphs.length >= 3) {
                        var parent = paragraphs[0].parentElement;
                        while (parent && parent.textContent.trim().length < 500) {
                            parent = parent.parentElement;
                        }
                        if (parent) article = parent;
                    }
                }

                if (!article) {
                    return JSON.stringify({title: title, content: '', images: [], error: 'No article found'});
                }

                var titleEl = article.querySelector('h1, h2, .title, .headline');
                if (titleEl) title = titleEl.textContent.trim();

                var images = [];
                article.querySelectorAll('img').forEach(function(img) {
                    var src = img.src || img.getAttribute('data-src');
                    if (src && src.startsWith('http') && !src.includes('icon') && !src.includes('logo') && !src.includes('avatar') && img.width > 100) {
                        if (images.indexOf(src) === -1) images.push(src);
                    }
                });

                var cleanArticle = article.cloneNode(true);
                cleanArticle.querySelectorAll('script, style, nav, header, footer, .ad, .ads, .advertisement, .sidebar, .social-share, .comments, .comment, .related, .recommend, .popup, .overlay, iframe, .video-embed, .social-buttons').forEach(function(el) { el.remove(); });

                var paragraphs = [];
                cleanArticle.querySelectorAll('p, h1, h2, h3, h4, h5, h6, blockquote, li, figcaption').forEach(function(el) {
                    if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') return;
                    var text = el.textContent.trim();
                    if (text.length === 0) return;

                    if (el.tagName.match(/^H[1-6]$/)) {
                        paragraphs.push({type: 'heading', level: parseInt(el.tagName[1]), text: text});
                    } else if (el.tagName === 'BLOCKQUOTE') {
                        paragraphs.push({type: 'quote', text: text});
                    } else if (el.tagName === 'LI') {
                        paragraphs.push({type: 'list', text: text});
                    } else {
                        paragraphs.push({type: 'paragraph', text: text});
                    }
                });

                return JSON.stringify({title: title, content: paragraphs, images: images});
            }

            return getArticleContent();
        })();
        """.trimIndent()
    ) { value ->
        try {
            val cleaned = value?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "\n")?.replace("\\\\", "\\") ?: ""
            val json = org.json.JSONObject(cleaned)
            val title = json.optString("title", "")
            val contentArray = json.optJSONArray("content")
            val imagesArray = json.optJSONArray("images")

            val content = mutableListOf<ReaderParagraph>()
            contentArray?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    content.add(
                        ReaderParagraph(
                            type = obj.optString("type", "paragraph"),
                            text = obj.optString("text", ""),
                            level = obj.optInt("level", 1)
                        )
                    )
                }
            }

            val images = mutableListOf<String>()
            imagesArray?.let { arr ->
                for (i in 0 until arr.length()) {
                    images.add(arr.getString(i))
                }
            }

            callback?.invoke(title, content, images)
        } catch (e: Exception) {
            callback?.invoke("", emptyList(), emptyList())
        }
    }
}

data class ReaderParagraph(
    val type: String,
    val text: String,
    val level: Int = 1
)
