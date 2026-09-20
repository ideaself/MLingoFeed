package com.mlingofeed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlingofeed.ui.theme.WebReaderTheme

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf<String?>(null)
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            sharedUrl.value = extractSharedUrl(intent)
            pendingDestination.value = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        } else {
            // Keep an unconsumed share / notification target alive across recreation.
            sharedUrl.value = savedInstanceState.getString(KEY_SHARED_URL)
            pendingDestination.value = savedInstanceState.getString(KEY_PENDING_DESTINATION)
        }
        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as WebReaderApp
            val themeMode by app.settingsManager.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeColor by app.settingsManager.themeColor.collectAsStateWithLifecycle(initialValue = "dynamic")
            WebReaderTheme(themeMode = themeMode, themeColor = themeColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebReaderNavHost(sharedUrl, pendingDestination)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedUrl(intent)?.let { sharedUrl.value = it }
        intent.getStringExtra(EXTRA_NAVIGATE_TO)?.let { pendingDestination.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SHARED_URL, sharedUrl.value)
        outState.putString(KEY_PENDING_DESTINATION, pendingDestination.value)
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        val urlRegex = Regex("""https?://\S+""")
                        urlRegex.find(text)?.value ?: text.takeIf { it.startsWith("http") }
                    }
                } else null
            }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
    }

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val DESTINATION_RSS = "rss"
        const val DESTINATION_WORDBOOK = "wordbook"
        private const val KEY_SHARED_URL = "pending_shared_url"
        private const val KEY_PENDING_DESTINATION = "pending_destination"
    }
}
