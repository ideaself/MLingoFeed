package com.mlingofeed.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlingofeed.WebReaderApp
import com.mlingofeed.data.api.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessageItem(
    val role: String,
    val content: String
)

data class PresetAction(val label: String, val prompt: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatDialog(
    initialContext: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessageItem>() }
    var isLoading by remember { mutableStateOf(false) }

    val apiUrl by app.settingsManager.aiApiUrl.collectAsState(initial = "")
    val apiKey by app.settingsManager.aiApiKey.collectAsState(initial = "")
    val model by app.settingsManager.aiModel.collectAsState(initial = "")

    val presets = remember(initialContext) {
        listOf(
            PresetAction("分析句子", "请用中文分析这个句子的语法结构、含义和用法：\"$initialContext\""),
            PresetAction("语法检查", "请用中文检查以下文本的语法错误并给出修改建议：\"$initialContext\""),
            PresetAction("翻译解释", "请将以下内容翻译成中文并解释重点词汇：\"$initialContext\""),
            PresetAction("单词解释", "请用中文简单解释 \"$initialContext\" 的含义和用法，并给出例句。"),
            PresetAction("同义词", "请用中文给出 \"$initialContext\" 的同义词、反义词和常见搭配。"),
            PresetAction("简化表达", "请用更简单的英语重写以下内容，并用中文解释：\"$initialContext\"")
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || isLoading) return

        messages.add(ChatMessageItem(role = "user", content = text))
        inputText = ""
        isLoading = true

        scope.launch {
            try {
                val chatMessages = mutableListOf<ChatMessage>()
                if (messages.size <= 1 && initialContext.isNotEmpty()) {
                    chatMessages.add(
                        ChatMessage(
                            role = "system",
                            content = "The user is reading a web page. The following context may be relevant: \"$initialContext\". Help the user understand the content, answer questions, and provide explanations. Always respond in Chinese (中文)."
                        )
                    )
                }
                messages.forEach { msg ->
                    chatMessages.add(ChatMessage(role = msg.role, content = msg.content))
                }

                val response = withContext(Dispatchers.IO) {
                    app.chatRepository.chat(
                        messages = chatMessages,
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = model
                    )
                }

                messages.add(ChatMessageItem(role = "assistant", content = response))
            } catch (e: Exception) {
                messages.add(
                    ChatMessageItem(role = "assistant", content = "Error: ${e.message}")
                )
            } finally {
                isLoading = false
            }
        }
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
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Chat",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row {
                        TextButton(onClick = {
                            messages.clear()
                        }) {
                            Text("Clear")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty() && initialContext.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = "Context: $initialContext",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    items(messages) { msg ->
                        ChatBubble(message = msg)
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        presets.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    inputText = preset.prompt
                                    sendMessage()
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask anything...") },
                        singleLine = false,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessageItem) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = androidx.compose.ui.unit.Dp(280f)),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
