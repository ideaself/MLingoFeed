package com.mlingofeed.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mlingofeed.AppViewModelFactory
import com.mlingofeed.WebReaderApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: ReadingStatsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val readingSessions by vm.readingSessions.collectAsState()
    val totalSeconds by vm.totalSeconds.collectAsState()

    val stats = remember(readingSessions) { calculateStats(readingSessions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Reading Time", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatDuration(totalSeconds),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Today",
                    value = formatDuration(stats.todaySeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "This Week",
                    value = formatDuration(stats.weekSeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "This Month",
                    value = formatDuration(stats.monthSeconds)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Sessions",
                    value = "${stats.totalSessions}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Avg Session",
                    value = formatDuration(stats.avgSessionSeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Longest",
                    value = formatDuration(stats.longestSessionSeconds)
                )
            }

            if (stats.dailyData.isNotEmpty()) {
                Text("Weekly Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    DailyBarChart(
                        data = stats.dailyData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DailyBarChart(data: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val maxValue = data.maxOfOrNull { it.second } ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val barWidth = size.width / (data.size * 2f)
        val chartHeight = size.height - 30f

        for (i in data.indices) {
            val barHeight = if (maxValue > 0) (data[i].second.toFloat() / maxValue) * chartHeight else 0f
            val x = i * (size.width / data.size) + barWidth / 2

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

private data class ReadingStats(
    val todaySeconds: Long,
    val weekSeconds: Long,
    val monthSeconds: Long,
    val totalSessions: Int,
    val avgSessionSeconds: Long,
    val longestSessionSeconds: Long,
    val dailyData: List<Pair<String, Long>>
)

private fun calculateStats(sessions: List<Pair<Long, Long>>): ReadingStats {
    if (sessions.isEmpty()) {
        return ReadingStats(0, 0, 0, 0, 0, 0, emptyList())
    }

    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()

    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val weekStart = calendar.apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val monthStart = calendar.apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    var todaySeconds = 0L
    var weekSeconds = 0L
    var monthSeconds = 0L
    var longestSession = 0L

    sessions.forEach { (timestamp, duration) ->
        if (timestamp >= todayStart) todaySeconds += duration
        if (timestamp >= weekStart) weekSeconds += duration
        if (timestamp >= monthStart) monthSeconds += duration
        if (duration > longestSession) longestSession = duration
    }

    val avgSession = sessions.map { it.second }.average().toLong()

    val dailyData = mutableListOf<Pair<String, Long>>()
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.timeInMillis = weekStart

    for (i in 0..6) {
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dayEnd = cal.timeInMillis

        val daySeconds = sessions
            .filter { it.first in dayStart until dayEnd }
            .sumOf { it.second }

        dailyData.add(Pair(dayFormat.format(Date(dayStart)), daySeconds))
    }

    return ReadingStats(
        todaySeconds = todaySeconds,
        weekSeconds = weekSeconds,
        monthSeconds = monthSeconds,
        totalSessions = sessions.size,
        avgSessionSeconds = avgSession,
        longestSessionSeconds = longestSession,
        dailyData = dailyData
    )
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
