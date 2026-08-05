package com.mlingofeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlingofeed.webview.ReaderParagraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeScreen(
    title: String,
    paragraphs: List<ReaderParagraph>,
    images: List<String>,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var fontSize by remember { mutableFloatStateOf(18f) }
    var lineHeight by remember { mutableFloatStateOf(1.6f) }

    val fullText = remember(paragraphs) {
        paragraphs.joinToString("\n\n") { it.text }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(fullText))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All")
                    }
                    IconButton(onClick = { onOpenChat(fullText.take(2000)) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..28f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSize + 6).sp,
                        lineHeight = ((fontSize + 6) * lineHeight).sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(paragraphs) { para ->
                    when (para.type) {
                        "heading" -> {
                            Text(
                                text = para.text,
                                style = when (para.level) {
                                    1 -> MaterialTheme.typography.headlineSmall
                                    2 -> MaterialTheme.typography.titleLarge
                                    else -> MaterialTheme.typography.titleMedium
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize + 2 - para.level).sp,
                                lineHeight = ((fontSize + 2 - para.level) * lineHeight).sp
                            )
                        }
                        "quote" -> {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Text(
                                    text = para.text,
                                    modifier = Modifier.padding(16.dp),
                                    fontStyle = FontStyle.Italic,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineHeight).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        "list" -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("• ", fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = para.text,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineHeight).sp
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = para.text,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineHeight).sp
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
