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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.stringResource
import com.mlingofeed.R
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WebReaderApp
    val vm: ReadingStatsViewModel = viewModel(factory = remember { AppViewModelFactory(app) })

    val readingSessions by vm.readingSessions.collectAsStateWithLifecycle()
    val totalSeconds by vm.totalSeconds.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoalMinutes.collectAsStateWithLifecycle()

    val stats = remember(readingSessions) { calculateStats(readingSessions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    Text(stringResource(R.string.total_reading_time), style = MaterialTheme.typography.titleMedium)
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
                    title = stringResource(R.string.today),
                    value = formatDuration(stats.todaySeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.this_week),
                    value = formatDuration(stats.weekSeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.this_month),
                    value = formatDuration(stats.monthSeconds)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.sessions),
                    value = "${stats.totalSessions}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.avg_session),
                    value = formatDuration(stats.avgSessionSeconds)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.longest),
                    value = formatDuration(stats.longestSessionSeconds)
                )
            }

            Text(stringResource(R.string.vocabulary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.words),
                    value = "${vm.vocabulary.total}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.mastered),
                    value = "${vm.vocabulary.mastered}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.due),
                    value = "${vm.vocabulary.due}"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.added_7d),
                    value = "${vm.vocabulary.addedLast7Days}"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.day_streak),
                    value = "${stats.streakDays}"
                )
            }

            if (dailyGoal > 0) {
                Text(stringResource(R.string.daily_goal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.daily_goal_progress, (stats.todaySeconds / 60).toInt(), dailyGoal),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (stats.todaySeconds.toFloat() / (dailyGoal * 60f)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (vm.weeklyWordCounts.any { it > 0 }) {
                Text(stringResource(R.string.vocabulary_growth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(96.dp).padding(16.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val maxCount = (vm.weeklyWordCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                        vm.weeklyWordCounts.forEach { count ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((6 + 48 * count / maxCount).dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.reading_heatmap), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ReadingHeatmap(sessions = readingSessions)
                }
            }

            if (stats.dailyData.isNotEmpty()) {
                Text(stringResource(R.string.weekly_trend), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
private fun ReadingHeatmap(sessions: List<Pair<Long, Long>>) {
    val dayMillis = 24L * 60 * 60 * 1000
    val today = startOfDayOf(System.currentTimeMillis())
    val mondayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
    val dayMinutes = remember(sessions) {
        sessions.groupBy { startOfDayOf(it.first) }
            .mapValues { entry -> entry.value.sumOf { it.second } / 60 }
    }
    val weeks = 6
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val offsetDays = (weeks - 1 - row) * 7 + (mondayIndex - col)
                    val day = today - offsetDays * dayMillis
                    val minutes = if (offsetDays < 0) -1L else dayMinutes[day] ?: 0L
                    val color = when {
                        minutes < 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        minutes == 0L -> MaterialTheme.colorScheme.surfaceVariant
                        minutes < 10 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        minutes < 30 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        minutes < 60 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }
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

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barWidth = size.width / (data.size * 2f)
            val chartHeight = size.height

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
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
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
    val streakDays: Int,
    val dailyData: List<Pair<String, Long>>
)

private fun calculateStats(sessions: List<Pair<Long, Long>>): ReadingStats {
    if (sessions.isEmpty()) {
        return ReadingStats(0, 0, 0, 0, 0, 0, 0, emptyList())
    }

    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = now

    fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayStart = startOfDay(now)

    // Calendar.set(DAY_OF_WEEK, firstDayOfWeek) can resolve to a future date for Monday-first
    // locales when today is Sunday, so walk back by the actual day distance instead.
    val daysSinceWeekStart = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
    val weekStart = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, -daysSinceWeekStart)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val monthStart = Calendar.getInstance().apply {
        timeInMillis = now
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
        streakDays = calculateStreak(sessions, todayStart),
        dailyData = dailyData
    )
}

private fun previousDayStart(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    add(Calendar.DAY_OF_MONTH, -1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDayOf(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun calculateStreak(sessions: List<Pair<Long, Long>>, todayStart: Long): Int {
    if (sessions.isEmpty()) return 0
    val readingDays = sessions.map { startOfDayOf(it.first) }.toHashSet()
    // A streak stays alive until the current day ends, so fall back to yesterday when the user
    // has not read yet today.
    var day = if (todayStart in readingDays) todayStart else previousDayStart(todayStart)
    var streak = 0
    while (day in readingDays) {
        streak++
        day = previousDayStart(day)
    }
    return streak
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
