package com.example.modeltest.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modeltest.data.entity.ChallengeWithCategoryAndCompletion
import com.example.modeltest.ui.components.ChallengeCard
import com.example.modeltest.ui.components.ConfettiAnimation
import com.example.modeltest.ui.components.ProgressRing
import com.example.modeltest.data.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfetti by remember { mutableStateOf(false) }

    // Show confetti when all completed
    LaunchedEffect(uiState.allCompleted) {
        if (uiState.allCompleted && uiState.challenges.isNotEmpty()) {
            showConfetti = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = getFormattedDate(),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = getDailyQuote(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.generateChallenges() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新挑战")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isGenerating -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在生成今日挑战...")
                        }
                    }
                }
                uiState.challenges.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "暂无挑战",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点击右上角刷新按钮生成",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    val completed = uiState.challenges.count { it.completionId != null }
                    val total = uiState.challenges.size

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Progress Ring
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ProgressRing(
                                    completed = completed,
                                    total = total,
                                    size = 140.dp,
                                    strokeWidth = 14.dp
                                )
                            }
                        }

                        // Challenges grouped by category
                        val grouped = uiState.challenges.groupBy { it.categoryDisplayName }
                        grouped.forEach { (category, challenges) ->
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = challenges.first().categoryEmoji,
                                        fontSize = 24.sp
                                    )
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            items(challenges) { challenge ->
                                ChallengeCard(
                                    text = challenge.text,
                                    isCompleted = challenge.completionId != null,
                                    onToggle = { viewModel.toggleChallenge(challenge.id) }
                                )
                            }
                        }

                        // All completed message
                        if (uiState.allCompleted) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Text(
                                        text = "今日挑战全部完成！明天见！",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    )
                                }
                            }
                        }

                        // Bottom spacer
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Confetti overlay
        if (showConfetti) {
            ConfettiAnimation(
                trigger = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun getFormattedDate(): String {
    val sdf = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)
    return sdf.format(Date())
}

private fun getDailyQuote(): String {
    val quotes = listOf(
        "每天进步一点点",
        "积少成多，聚沙成塔",
        "小步快跑，持续前进",
        "今天也要加油哦",
        "坚持就是胜利",
        "相信自己，你可以的",
        "每一天都是新的开始",
        "慢慢来，比较快"
    )
    val dayOfYear = SimpleDateFormat("D", Locale.getDefault()).format(Date()).toInt()
    return quotes[dayOfYear % quotes.size]
}