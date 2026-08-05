package com.mlingofeed.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private data class DifficultyData(
    val cefrLevel: String = "?",
    val difficulty: String = "?",
    val wordCount: Int = 0,
    val avgSentenceLength: Double = 0.0,
    val suggestions: List<String> = emptyList(),
    val keyVocabulary: List<String> = emptyList()
)

private fun parseDifficultyResult(result: String): DifficultyData? {
    return try {
        val json = JSONObject(result)
        val suggestions = mutableListOf<String>()
        json.optJSONArray("suggestions")?.let { arr ->
            for (i in 0 until arr.length()) suggestions.add(arr.getString(i))
        }
        val keyVocab = mutableListOf<String>()
        json.optJSONArray("keyVocabulary")?.let { arr ->
            for (i in 0 until arr.length()) keyVocab.add(arr.getString(i))
        }
        DifficultyData(
            cefrLevel = json.optString("cefrLevel", "?"),
            difficulty = json.optString("difficulty", "?"),
            wordCount = json.optInt("wordCount", 0),
            avgSentenceLength = json.optDouble("avgSentenceLength", 0.0),
            suggestions = suggestions,
            keyVocabulary = keyVocab
        )
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyDialog(
    isLoading: Boolean,
    result: String?,
    onDismiss: () -> Unit,
    onAnalyze: () -> Unit
) {
    val data = remember(result) { result?.let { parseDifficultyResult(it) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reading Difficulty", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Analyzing difficulty...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    result == null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Analyze the difficulty of the current article")
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = onAnalyze) {
                                Text("Start Analysis")
                            }
                        }
                    }
                    data != null -> {
                        DifficultyResultContent(data = data, onReanalyze = onAnalyze)
                    }
                    else -> {
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onAnalyze) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifficultyResultContent(data: DifficultyData, onReanalyze: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = data.cefrLevel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = getCefrColor(data.cefrLevel)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Difficulty", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = data.difficulty,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("Words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${data.wordCount}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        Column {
            Text("Avg Sentence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format("%.1f", data.avgSentenceLength)} words", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }

    if (data.keyVocabulary.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Key Vocabulary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            data.keyVocabulary.forEach { word ->
                AssistChip(
                    onClick = {},
                    label = { Text(word, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    }

    if (data.suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Suggestions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        data.suggestions.forEach { suggestion ->
            Text(
                text = "• $suggestion",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    TextButton(onClick = onReanalyze) {
        Text("Re-analyze")
    }
}

@Composable
private fun getCefrColor(level: String): androidx.compose.ui.graphics.Color {
    return when (level.uppercase()) {
        "A1" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        "A2" -> androidx.compose.ui.graphics.Color(0xFF8BC34A)
        "B1" -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        "B2" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        "C1" -> androidx.compose.ui.graphics.Color(0xFFFF5722)
        "C2" -> androidx.compose.ui.graphics.Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface
    }
}
