package com.example.modeltest.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.UserSettingRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToWeeklyPlan: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { UserSettingRepository(db.userSettingDao()) }
    val scope = rememberCoroutineScope()

    val dailyCount by repo.getDailyChallengeCount().collectAsState(initial = 5)
    val refreshMode by repo.getRefreshMode().collectAsState(initial = "auto")
    val defaultCategories by repo.getDefaultCategories().collectAsState(initial = listOf("health", "mindfulness", "learning", "fitness"))

    val categories = listOf(
        Triple("health", "健康", "💧"),
        Triple("mindfulness", "正念", "🧘"),
        Triple("learning", "学习", "📚"),
        Triple("creativity", "创造", "🎨"),
        Triple("social", "社交", "💬"),
        Triple("fitness", "运动", "🏃"),
        Triple("nature", "自然", "🌿")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Daily Challenge Count
        item {
            SettingsCard(title = "每日挑战数量", icon = Icons.Default.Star) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(3, 5, 7).forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = dailyCount == count,
                            onClick = { scope.launch { repo.setDailyChallengeCount(count) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) {
                            Text("${count}个")
                        }
                    }
                }
            }
        }

        // Default Categories
        item {
            SettingsCard(title = "关注分类", icon = Icons.Default.Star) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (name, displayName, emoji) ->
                        val isSelected = name in defaultCategories
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newCategories = if (isSelected) {
                                        defaultCategories.filter { it != name }
                                    } else {
                                        defaultCategories + name
                                    }
                                    scope.launch { repo.setDefaultCategories(newCategories) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isSelected,
                                onCheckedChange = {
                                    val newCategories = if (isSelected) {
                                        defaultCategories.filter { it != name }
                                    } else {
                                        defaultCategories + name
                                    }
                                    scope.launch { repo.setDefaultCategories(newCategories) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Refresh Mode
        item {
            SettingsCard(title = "挑战刷新", icon = Icons.Default.Refresh) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "打开时自动刷新",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = refreshMode == "auto",
                        onCheckedChange = {
                            scope.launch { repo.setRefreshMode(if (it) "auto" else "manual") }
                        }
                    )
                }
            }
        }

        // Weekly Plan
        item {
            SettingsCard(
                title = "每周规划",
                icon = Icons.Default.DateRange,
                modifier = Modifier.clickable { onNavigateToWeeklyPlan() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "规划下周挑战分类",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }

        // About
        item {
            SettingsCard(title = "关于", icon = Icons.Default.Info) {
                Column {
                    Text(
                        text = "每日一小步 v1.0",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "每天完成几个微小挑战，成为更好的自己",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Spacer for bottom nav
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}