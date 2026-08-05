package com.mlingofeed.ui.components

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlingofeed.WebReaderApp
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class TranslationSegment(
    val original: String,
    val translation: String,
    val isTranslating: Boolean = false
)

@Composable
fun PageTranslationDialog(
    webView: WebView?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    val segments = remember { mutableStateListOf<TranslationSegment>() }
    var isExtracting by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var currentSegmentIndex by remember { mutableStateOf(0) }
    var hasStarted by remember { mutableStateOf(false) }

    val apiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val apiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val model by app.settingsManager.aiModel.collectAsState(initial = "")
    val targetLang by app.settingsManager.translateTargetLang.collectAsState(initial = "Chinese")

    val listState = rememberLazyListState()

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty() && currentSegmentIndex < segments.size) {
            listState.animateScrollToItem(currentSegmentIndex)
        }
    }

    LaunchedEffect(apiUrl, apiKey, model, targetLang) {
        if (hasStarted) return@LaunchedEffect
        if (apiKey.isBlank()) return@LaunchedEffect

        hasStarted = true

        isExtracting = true
        val extractedText = extractPageText(webView)
        isExtracting = false

        if (extractedText.isBlank()) {
            segments.add(
                TranslationSegment(
                    original = "",
                    translation = "No text content found on this page",
                    isTranslating = false
                )
            )
            return@LaunchedEffect
        }

        val chunks = splitIntoSegments(extractedText)
        chunks.forEach { chunk ->
            segments.add(TranslationSegment(original = chunk, translation = "", isTranslating = true))
        }

        isTranslating = true
        for (i in segments.indices) {
            currentSegmentIndex = i
            val segment = segments[i]
            if (!segment.isTranslating) continue

            val translation = withContext(Dispatchers.IO) {
                app.chatRepository.translate(
                    text = segment.original,
                    targetLang = targetLang,
                    apiUrl = apiUrl,
                    apiKey = apiKey,
                    model = model
                )
            }

            segments[i] = segments[i].copy(translation = translation, isTranslating = false)
        }
        isTranslating = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(min = androidx.compose.ui.unit.Dp(500f), max = androidx.compose.ui.unit.Dp(700f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Page Translation",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isTranslating) {
                            Text(
                                text = "Translating ${currentSegmentIndex + 1}/${segments.size}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(if (isTranslating || isExtracting) "Close in background" else "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isExtracting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Extracting page content...")
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(segments) { index, segment ->
                            SegmentItem(
                                index = index,
                                segment = segment
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentItem(
    index: Int,
    segment: TranslationSegment
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = segment.original,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (segment.isTranslating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).then(Modifier.height(16.dp)),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Translating...",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (segment.translation.isNotEmpty()) {
                Text(
                    text = segment.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private suspend fun extractPageText(webView: WebView?): String {
    if (webView == null) return ""

    return suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(
            """
            (function() {
                // Try to get main content first
                var content = document.querySelector('article') ||
                              document.querySelector('main') ||
                              document.querySelector('[role="main"]') ||
                              document.querySelector('.post-content') ||
                              document.querySelector('.article-content') ||
                              document.querySelector('.entry-content') ||
                              document.body;

                if (!content) return '';

                // Remove script, style, nav, footer, header elements
                var clones = content.cloneNode(true);
                var removeTags = clones.querySelectorAll('script, style, nav, footer, header, aside, noscript, iframe, .nav, .navigation, .menu, .sidebar, .advertisement, .ad, .social-share, .comments');
                removeTags.forEach(function(el) { el.remove(); });

                // Get text and clean up
                var text = clones.innerText || clones.textContent || '';
                text = text.replace(/\\s+/g, ' ').trim();

                return text;
            })();
            """.trimIndent()
        ) { value ->
            val result = if (value != null && value != "null") {
                value.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
            } else {
                ""
            }
            continuation.resume(result)
        }
    }
}

private fun splitIntoSegments(text: String, maxChars: Int = 500): List<String> {
    if (text.length <= maxChars) return listOf(text)

    val segments = mutableListOf<String>()

    // Split by paragraphs first
    val paragraphs = text.split("\n").filter { it.isNotBlank() }

    var currentSegment = StringBuilder()

    for (paragraph in paragraphs) {
        if (currentSegment.length + paragraph.length > maxChars && currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString().trim())
            currentSegment = StringBuilder()
        }

        if (paragraph.length > maxChars) {
            // Split long paragraph by sentences
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment.toString().trim())
                currentSegment = StringBuilder()
            }

            val sentences = paragraph.split(Regex("(?<=[.!?。！？])\\s+"))
            for (sentence in sentences) {
                if (currentSegment.length + sentence.length > maxChars && currentSegment.isNotEmpty()) {
                    segments.add(currentSegment.toString().trim())
                    currentSegment = StringBuilder()
                }
                currentSegment.append(sentence).append(" ")
            }
        } else {
            currentSegment.append(paragraph).append("\n")
        }
    }

    if (currentSegment.isNotEmpty()) {
        segments.add(currentSegment.toString().trim())
    }

    return segments.filter { it.isNotBlank() }
}
