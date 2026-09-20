package com.mlingofeed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mlingofeed.WebReaderApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R

@Composable
fun TranslationPopup(
    text: String,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val clipboardManager = LocalClipboardManager.current

    var translation by remember(text) { mutableStateOf("") }
    var isLoading by remember(text) { mutableStateOf(false) }

    LaunchedEffect(text) {
        isLoading = true
        // Read the AI settings once per popup. Collecting them separately would first run with
        // empty defaults and fire a doomed request before the stored values arrive.
        val settings = app.settingsManager.getAllSettings()
        val apiKey = settings["ai_api_key"].orEmpty()
        if (apiKey.isBlank()) {
            translation = context.getString(R.string.please_configure_ai_api_key_in_settings)
            isLoading = false
            return@LaunchedEffect
        }
        translation = withContext(Dispatchers.IO) {
            app.chatRepository.translate(
                text = text,
                targetLang = settings["translate_target_lang"] ?: "Chinese",
                apiUrl = settings["ai_api_url"].orEmpty(),
                apiKey = apiKey,
                model = settings["ai_model"].orEmpty()
            )
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = Dp(400f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.translation),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(text))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                        IconButton(onClick = onOpenChat) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.chat))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = translation,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
