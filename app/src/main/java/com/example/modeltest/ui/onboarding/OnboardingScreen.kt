package com.example.modeltest.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.UserSettingRepository
import com.example.modeltest.llm.SelfDescriptionService
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.Favorite,
        title = "每日一小步",
        description = "每天完成几个微小的挑战\n积少成多，成为更好的自己"
    ),
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = "轻松完成",
        description = "每个挑战只需5分钟\n点击卡片即可标记完成"
    ),
    OnboardingPage(
        icon = Icons.Default.DateRange,
        title = "记录成长",
        description = "查看历史统计图表\n见证你的每一点进步"
    )
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val repo = remember { UserSettingRepository(db.userSettingDao()) }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { onboardingPages.size + 1 })

    var selectedCount by remember { mutableIntStateOf(5) }
    var selectedCategories by remember { mutableStateOf(setOf("health", "mindfulness", "learning", "fitness")) }
    var selectedPeriod by remember { mutableStateOf("allday") }
    var selectedIntensity by remember { mutableStateOf("moderate") }
    var selfDescription by remember { mutableStateOf("") }
    var isSummarizing by remember { mutableStateOf(false) }

    val selfDescService = remember { SelfDescriptionService(context) }

    val categories = listOf(
        Triple("health", "健康", "\uD83D\uDCA7"),
        Triple("mindfulness", "正念", "\uD83E\uDDD8"),
        Triple("learning", "学习", "\uD83D\uDCDA"),
        Triple("creativity", "创造", "\uD83C\uDFA8"),
        Triple("social", "社交", "\uD83D\uDCAC"),
        Triple("fitness", "运动", "\uD83C\uDFC3"),
        Triple("nature", "自然", "\uD83C\uDF3F")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Skip button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            if (pagerState.currentPage < onboardingPages.size - 1) {
                TextButton(onClick = {
                    scope.launch {
                        repo.setDefaultCategories(selectedCategories.toList())
                        repo.setDailyChallengeCount(selectedCount)
                        repo.setActivePeriod(selectedPeriod)
                        repo.setIntensity(selectedIntensity)
                        if (selfDescription.isNotBlank()) {
                            isSummarizing = true
                            selfDescService.summarizeAndSave(repo, selfDescription)
                            isSummarizing = false
                        }
                        repo.setOnboardingCompleted()
                        onComplete()
                    }
                }) {
                    Text("跳过")
                }
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when {
                page < onboardingPages.size -> {
                    val item = onboardingPages[page]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                page == onboardingPages.size -> {
                    // Configuration page
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "个性化设置",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        // Daily count selector
                        Text(
                            text = "每日挑战数量",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(3, 5, 7).forEachIndexed { index, count ->
                                SegmentedButton(
                                    selected = selectedCount == count,
                                    onClick = { selectedCount = count },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) {
                                    Text("${count}个")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Category selector
                        Text(
                            text = "关注分类",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { (name, displayName, emoji) ->
                                val isSelected = name in selectedCategories
                                Card(
                                    modifier = Modifier
                                        .clickable {
                                            selectedCategories = if (isSelected) {
                                                selectedCategories - name
                                            } else {
                                                selectedCategories + name
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = emoji, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Active period
                        Text(
                            text = "活跃时段",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "morning" to "早晨",
                                "afternoon" to "下午",
                                "evening" to "晚上",
                                "allday" to "全天"
                            ).forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = selectedPeriod == key,
                                    onClick = { selectedPeriod = key },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 4)
                                ) {
                                    Text(label)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Intensity
                        Text(
                            text = "强度",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "light" to "轻松",
                                "moderate" to "适中",
                                "hard" to "挑战"
                            ).forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = selectedIntensity == key,
                                    onClick = { selectedIntensity = key },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                                ) {
                                    Text(label)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Self description
                        Text(
                            text = "自我介绍（可选）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        Text(
                            text = "例：我是程序员，久坐多，想减压和改善体态，喜欢户外但没时间，只有晚上有空。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = selfDescription,
                            onValueChange = { if (it.length <= 200) selfDescription = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("简单介绍一下自己…") },
                            supportingText = { Text("${selfDescription.length}/200") },
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                }
            }
        }

        // Bottom indicators and buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(onboardingPages.size + 1) { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action button
            Button(
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        scope.launch {
                            repo.setDefaultCategories(selectedCategories.toList())
                            repo.setDailyChallengeCount(selectedCount)
                            repo.setActivePeriod(selectedPeriod)
                            repo.setIntensity(selectedIntensity)
                            if (selfDescription.isNotBlank()) {
                                isSummarizing = true
                                selfDescService.summarizeAndSave(repo, selfDescription)
                                isSummarizing = false
                            }
                            repo.setOnboardingCompleted()
                            onComplete()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSummarizing && (pagerState.currentPage < onboardingPages.size || selectedCategories.isNotEmpty())
            ) {
                if (isSummarizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI 正在分析你的偏好…")
                } else {
                    Text(
                        text = if (pagerState.currentPage < onboardingPages.size) "下一步" else "开始使用",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}