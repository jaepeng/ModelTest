package com.example.modeltest.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.DateUtils
import com.example.modeltest.data.UserSettingRepository
import com.example.modeltest.data.WeeklyPlanRepository
import com.example.modeltest.data.entity.Category
import com.example.modeltest.data.entity.WeeklyPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyPlanScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val planRepo = remember { WeeklyPlanRepository(db.weeklyPlanDao()) }
    val userSettingRepo = remember { UserSettingRepository(db.userSettingDao()) }
    val scope = rememberCoroutineScope()

    // Get current week dates
    val calendar = Calendar.getInstance()
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    val weekDates = (0..6).map {
        val date = calendar.time
        val result = DateUtils.dateToString(date)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        result
    }
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    // State for selected date and its categories
    var selectedDate by remember { mutableStateOf(weekDates[0]) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }

    // Categories
    val allCategories = remember {
        listOf(
            Triple("health", "健康", "\uD83D\uDCA7"),
            Triple("mindfulness", "正念", "\uD83E\uDDD8"),
            Triple("learning", "学习", "\uD83D\uDCDA"),
            Triple("creativity", "创造", "\uD83C\uDFA8"),
            Triple("social", "社交", "\uD83D\uDCAC"),
            Triple("fitness", "运动", "\uD83C\uDFC3"),
            Triple("nature", "自然", "\uD83C\uDF3F")
        )
    }

    // Load existing plans
    val plansForWeek by planRepo.getPlansForWeek(weekDates.first(), weekDates.last())
        .collectAsState(initial = emptyList())

    // Update selection when selected date changes
    val defaultCategories by userSettingRepo.getDefaultCategories()
        .collectAsState(initial = listOf("health", "mindfulness", "learning", "fitness"))

    // Determine selected categories for the selected date
    val currentCategories = remember(selectedDate, plansForWeek, defaultCategories) {
        val plansForDate = plansForWeek.filter { it.date == selectedDate }
        if (plansForDate.isNotEmpty()) {
            plansForDate.map { plan ->
                allCategories.find { it.first.hashCode().toLong() == plan.categoryId }?.first ?: "health"
            }.toSet()
        } else {
            defaultCategories.toSet()
        }
    }

    // Keep a mutable state for editing
    var editingCategories by remember(currentCategories) { mutableStateOf(currentCategories) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("每周规划") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Week date selector
            Text(
                text = "选择日期",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                weekDates.forEachIndexed { index, date ->
                    val isSelected = date == selectedDate
                    val dayNum = date.takeLast(2).toIntOrNull() ?: 0
                    val hasCustomPlan = plansForWeek.any { it.date == date && it.isCustom }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedDate = date
                                editingCategories = if (plansForWeek.any { it.date == date }) {
                                    plansForWeek.filter { it.date == date }.mapNotNull { plan ->
                                        allCategories.find { cat ->
                                            cat.first.hashCode().toLong() == plan.categoryId
                                        }?.first
                                    }.toSet()
                                } else {
                                    defaultCategories.toSet()
                                }
                            }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = dayLabels[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (hasCustomPlan) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (hasCustomPlan) MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Category selector
            Text(
                text = "选择分类",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allCategories.forEach { (name, displayName, emoji) ->
                    val isSelected = name in editingCategories
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            editingCategories = if (isSelected) {
                                editingCategories - name
                            } else {
                                editingCategories + name
                            }
                        },
                        label = { Text("$emoji $displayName") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        // We need to get category IDs from names
                        // Using the seed data mapping
                        val categoryIds = editingCategories.mapNotNull { name ->
                            when (name) {
                                "health" -> 1L
                                "mindfulness" -> 2L
                                "learning" -> 3L
                                "creativity" -> 4L
                                "social" -> 5L
                                "fitness" -> 6L
                                "nature" -> 7L
                                else -> null
                            }
                        }
                        planRepo.savePlansForDate(selectedDate, categoryIds, true)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存规划", modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

private fun DateUtils.Companion.dateToString(date: java.util.Date): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(date)
}