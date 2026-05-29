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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val challenges: List<ChallengeWithCategoryAndCompletion> = emptyList(),
    val allCompleted: Boolean = false,
    val thinkingText: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val db = AppDatabase.getDatabase(application)
    private val challengeRepo = ChallengeRepository(db.challengeDao())
    private val categoryDao = db.categoryDao()
    private val userSettingRepo = UserSettingRepository(db.userSettingDao())
    private val weeklyPlanRepo = WeeklyPlanRepository(db.weeklyPlanDao())
    private val llmService = LlmService(application)

    private val _isLoading = MutableStateFlow(false)
    private val _isGenerating = MutableStateFlow(false)
    private val _celebrationTrigger = MutableStateFlow(false)
    private val _thinkingText = MutableStateFlow("")
    val celebrationTrigger: StateFlow<Boolean> = _celebrationTrigger.asStateFlow()

    private val today = DateUtils.todayString()

    private val challenges: StateFlow<List<ChallengeWithCategoryAndCompletion>> =
        challengeRepo.getTodayChallenges()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        _isLoading, _isGenerating, challenges, _thinkingText
    ) { isLoading, isGenerating, challengesList, thinkingText ->
        HomeUiState(
            isLoading = isLoading,
            isGenerating = isGenerating,
            challenges = challengesList,
            allCompleted = challengesList.isNotEmpty() && challengesList.all { it.completionId != null },
            thinkingText = thinkingText
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val completedCount: StateFlow<Int> =
        challenges.map { list -> list.count { it.completionId != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> =
        challenges.map { list -> list.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        Log.d(TAG, "init called, today=$today")
        checkAndGenerateChallenges()
    }

    private fun checkAndGenerateChallenges() {
        Log.d(TAG, "checkAndGenerateChallenges")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val hasChallenges = challengeRepo.todayHasChallenges()
                Log.d(TAG, "todayHasChallenges=$hasChallenges")
                if (!hasChallenges) {
                    generateChallenges()
                } else {
                    Log.d(TAG, "Skipping - today already has challenges")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateChallenges() {
        Log.d(TAG, "generateChallenges() called")
        viewModelScope.launch {
            _isGenerating.value = true
            _thinkingText.value = ""
            try {
                Log.d(TAG, "Step 1: Initializing LLM...")
                llmService.initialize()

                Log.d(TAG, "Step 2: Fetching user settings...")
                val defaultCategories = userSettingRepo.getDefaultCategories().first()
                val dailyCount = userSettingRepo.getDailyChallengeCount().first()
                Log.d(TAG, "Step 2: categories=$defaultCategories, dailyCount=$dailyCount")

                val baseCount = dailyCount / defaultCategories.size
                val remainder = dailyCount % defaultCategories.size
                val prompt = buildPromptWithRemainder(defaultCategories, baseCount, remainder)
                Log.d(TAG, "Step 3: Prompt length=${prompt.length}")

                // Step 4: Stream generate with real-time token display
                Log.d(TAG, "Step 4: Calling LLM streaming generate...")
                val fullOutput = StringBuilder()
                llmService.generateStreaming(prompt).collect { token ->
                    fullOutput.append(token)
                    _thinkingText.value = fullOutput.toString()
                    Log.d(TAG, "Token: $token")
                }
                val llmOutput = fullOutput.toString()
                Log.d(TAG, "Step 4: LLM output complete, length=${llmOutput.length}")

                // Step 5: Parse and insert challenges
                Log.d(TAG, "Step 5: Parsing output...")
                val parsed = ChallengeParser.parse(llmOutput)
                Log.d(TAG, "Step 5: parsed=$parsed")

                if (parsed.isNotEmpty()) {
                    val allCategories = categoryDao.getCategoriesByNames(parsed.keys.toList())
                    val categoryMap = allCategories.flatMap { cat ->
                        listOf(cat.name to cat.id, cat.displayName to cat.id)
                    }.toMap()
                    Log.d(TAG, "Category lookup map (from DB): $categoryMap")

                    val challenges = parsed.flatMap { (categoryName, challengeTexts) ->
                        val categoryId = categoryMap[categoryName]
                            ?: run {
                                Log.w(TAG, "Unknown category: '$categoryName', skipping")
                                return@flatMap emptyList<Challenge>()
                            }
                        challengeTexts.map { text ->
                            Challenge(
                                categoryId = categoryId,
                                text = text,
                                date = today
                            )
                        }
                    }

                    Log.d(TAG, "Step 6: Archiving completed & cleaning up old challenges...")
                    challengeRepo.archiveAndCleanupToday()
                    challengeRepo.insertChallenges(challenges)
                    Log.d(TAG, "Step 7: Inserted ${challenges.size} challenges to DB")
                } else {
                    Log.w(TAG, "Step 5: parsed is empty - no challenges generated")
                }
            } catch (e: Exception) {
                Log.e(TAG, "generateChallenges FAILED", e)
            } finally {
                _isGenerating.value = false
                _thinkingText.value = ""
            }
        }
    }
    fun buildTextPrompt(userQuery: String): String {
        return "<|im_start|>user\n${userQuery}<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun buildPromptWithRemainder(categories: List<String>, baseCount: Int, remainder: Int): String {
        val detail = categories.mapIndexed { index, cat ->
            val count = if (index < remainder) baseCount + 1 else baseCount
            "$cat: $count 个"
        }.joinToString(", ")
        return buildTextPrompt("你是每日挑战生成助手。\n" +
                "分类及任务数量如下：$detail\n" +
                "规则：\n" +
                "- 每个挑战5分钟内可完成\n" +
                "- 必须是单次可直接执行的动作，拒绝理念、口号、概括性语句\n" +
                "- 禁止：坚持XX、养成XX、保持XX 这类空泛描述\n" +
                "- 内容具体可执行，积极向上\n" +
                "- 每条15字以内，必须是中文\n" +
                "- 不允许生成空白挑战\n" +
                "\n" +
                "严格输出JSON格式，key必须是英文：格式为：{\"类别1\":[\"任务1\",\"任务2\"],\"类别2\":[\"任务1\",\"任务2\"]},示例如下：\n" +
                "{\"health\":[\"喝一杯温水\",\"起来站5分钟\"],\"learning\":[\"看5分钟书\",\"学一个新单词\"]}")
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