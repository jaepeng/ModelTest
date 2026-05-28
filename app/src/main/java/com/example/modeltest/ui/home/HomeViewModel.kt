package com.example.modeltest.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.ChallengeRepository
import com.example.modeltest.data.DateUtils
import com.example.modeltest.data.UserSettingRepository
import com.example.modeltest.data.WeeklyPlanRepository
import com.example.modeltest.data.entity.Challenge
import com.example.modeltest.data.entity.ChallengeWithCategoryAndCompletion
import com.example.modeltest.llm.ChallengeParser
import com.example.modeltest.llm.LlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val challengeRepo = ChallengeRepository(db.challengeDao())
    private val userSettingRepo = UserSettingRepository(db.userSettingDao())
    private val weeklyPlanRepo = WeeklyPlanRepository(db.weeklyPlanDao())
    private val llmService = LlmService(application)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _celebrationTrigger = MutableStateFlow(false)
    val celebrationTrigger: StateFlow<Boolean> = _celebrationTrigger.asStateFlow()

    private val today = DateUtils.todayString()

    val challenges: StateFlow<List<ChallengeWithCategoryAndCompletion>> =
        challengeRepo.getTodayChallenges()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedCount: StateFlow<Int> =
        challenges.map { list -> list.count { it.completionId != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> =
        challenges.map { list -> list.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        checkAndGenerateChallenges()
    }

    private fun checkAndGenerateChallenges() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (!challengeRepo.todayHasChallenges()) {
                    generateChallenges()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateChallenges() {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                llmService.initialize()

                val defaultCategories = userSettingRepo.getDefaultCategories().first()
                val dailyCount = userSettingRepo.getDailyChallengeCount().first()

                val categoriesPerChallenge = maxOf(1, dailyCount / defaultCategories.size)
                val prompt = buildPrompt(defaultCategories, categoriesPerChallenge)

                val llmOutput = llmService.generate(prompt)
                val parsed = ChallengeParser.parse(llmOutput)

                if (parsed.isNotEmpty()) {
                    val categoryMap = mapOf(
                        "health" to 1L,
                        "mindfulness" to 2L,
                        "learning" to 3L,
                        "creativity" to 4L,
                        "social" to 5L,
                        "fitness" to 6L,
                        "nature" to 7L
                    )

                    val challenges = parsed.flatMap { (categoryName, challengeTexts) ->
                        val categoryId = categoryMap[categoryName] ?: return@flatMap emptyList<Challenge>()
                        challengeTexts.take(categoriesPerChallenge).map { text ->
                            Challenge(
                                categoryId = categoryId,
                                text = text,
                                date = today
                            )
                        }
                    }

                    challengeRepo.insertChallenges(challenges)
                    Log.d("HomeViewModel", "Generated ${challenges.size} challenges")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to generate challenges", e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun buildPrompt(categories: List<String>, countPerCategory: Int): String {
        val categoryNames = categories.joinToString("、") { getCategoryDisplayName(it) }
        return """你是一个每日挑战生成助手。请为以下分类各生成${countPerCategory}个微小的、积极向上的日常挑战。
要求：
- 每个挑战必须在5分钟内完成
- 语言简洁，15字以内
- 积极向上，让人有成就感
- 具体可执行

分类：$categoryNames

输出JSON格式：
{"health":["挑战1","挑战2"],"mindfulness":["挑战1","挑战2"]}"""
    }

    private fun getCategoryDisplayName(name: String): String = when (name) {
        "health" -> "健康"
        "mindfulness" -> "正念"
        "learning" -> "学习"
        "creativity" -> "创造"
        "social" -> "社交"
        "fitness" -> "运动"
        "nature" -> "自然"
        else -> name
    }

    fun toggleChallenge(challengeId: Long) {
        viewModelScope.launch {
            val nowCompleted = challengeRepo.toggleCompletion(challengeId)
            if (nowCompleted) {
                val currentCompleted = completedCount.value + 1
                val currentTotal = totalCount.value
                if (currentCompleted == currentTotal && currentTotal > 0) {
                    _celebrationTrigger.value = true
                }
            }
        }
    }

    fun resetCelebration() {
        _celebrationTrigger.value = false
    }

    fun refreshChallenges() {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                generateChallenges()
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmService.release()
    }
}