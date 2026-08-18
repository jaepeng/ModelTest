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
        private const val MAX_GEN_RETRY = 3
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

                // Step 4: Generate with retry on missing categories.
                // Small model often emits fewer categories than requested; retry up to MAX_GEN_RETRY.
                var parsed: Map<String, List<String>> = emptyMap()
                var lastOutput = ""
                for (attempt in 1..MAX_GEN_RETRY) {
                    Log.d(TAG, "Step 4: Calling LLM streaming generate (attempt $attempt/$MAX_GEN_RETRY)...")
                    val fullOutput = StringBuilder()
                    llmService.generateStreaming(prompt).collect { token ->
                        fullOutput.append(token)
                        _thinkingText.value = fullOutput.toString()
                        Log.d(TAG, "Token: $token")
                    }
                    lastOutput = fullOutput.toString()
                    Log.d(TAG, "Step 4: LLM output complete, length=${lastOutput.length}")

                    Log.d(TAG, "Step 5: Parsing output...")
                    parsed = ChallengeParser.parse(lastOutput)
                    Log.d(TAG, "Step 5: parsed=$parsed")

                    val missing = defaultCategories.filter { it !in parsed.keys }
                    if (missing.isEmpty()) break  // all categories present, done
                    Log.w(TAG, "Attempt $attempt: missing categories $missing (got ${parsed.size}/${defaultCategories.size}), retrying")
                }

                if (parsed.isNotEmpty()) {
                    val expectedPerCategory = defaultCategories.mapIndexed { index, cat ->
                        cat to if (index < remainder) baseCount + 1 else baseCount
                    }.toMap()

                    // Trim each category to its expected count (model may over-generate).
                    parsed = parsed.mapValues { (cat, list) ->
                        val expected = expectedPerCategory[cat] ?: 1
                        if (list.size > expected) {
                            Log.w(TAG, "Category '$cat' over-generated ${list.size}, trimming to $expected")
                            list.take(expected)
                        } else {
                            list
                        }
                    }

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
                    Log.w(TAG, "Step 5: parsed is empty after $MAX_GEN_RETRY attempts - no challenges generated")
                    Log.w(TAG, "Step 5: last raw output: $lastOutput")
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
        val list = categories.mapIndexed { index, cat ->
            val count = if (index < remainder) baseCount + 1 else baseCount
            "${index + 1}. $cat: 恰好 $count 个"
        }.joinToString("\n")
        // Build a sample that matches the requested per-category count so the model
        // doesn't mimic a fixed 2-item example and over-generate.
        val sampleHealth = if (baseCount >= 2) "\"喝一杯温水\",\"起来站5分钟\"" else "\"喝一杯温水\""
        val sampleLearn = if (baseCount >= 2) "\"看5分钟书\",\"学一个新单词\"" else "\"看5分钟书\""
        return buildTextPrompt("你是每日挑战生成助手。\n" +
                "严格数量要求（多一个少一个都不行）：\n" +
                "$list\n" +
                "总计恰好 ${categories.sumOf { if (categories.indexOf(it) < remainder) baseCount + 1 else baseCount }} 个挑战。\n" +
                "输出JSON必须包含上面全部 ${categories.size} 个分类的key，不能少。\n" +
                "挑战设计核心要求：\n" +
                "- 必须是单一、具体、可在5分钟内完成的动作，有明确的开始和结束\n" +
                "- 必须可直接执行，不需要准备、不需要坚持、不是习惯\n" +
                "- 优先日常小动作：喝一杯水、深蹲5次、深呼吸3次、看一页书、写一句话、站起走动2分钟\n" +
                "禁止以下类型（违反则不合格）：\n" +
                "- 空泛描述：保持XX、坚持XX、养成XX、注意XX、培养XX\n" +
                "- 理念口号：健康饮食、积极心态、规律作息、良好习惯\n" +
                "- 概括性语句：改善XX、提升XX、加强XX\n" +
                "- 不可量化：多喝水、多运动、少熬夜、好好吃饭\n" +
                "- 需要长期持续的：每天XX、每周XX\n" +
                "每条15字以内，必须是中文。\n" +
                "直接输出JSON，禁止任何解释、思考、说明文字。\n" +
                "- 第一个字符必须是{，最后一个字符必须是}\n" +
                "- 禁止输出\"首先\"\"我需要\"\"理解\"等思考过程\n" +
                "- 不要复述指令，不要说明每类要几个\n" +
                "\n" +
                "好示例：{\"health\":[$sampleHealth],\"learning\":[$sampleLearn]}\n" +
                "坏示例（禁止）：{\"health\":[\"保持健康饮食\",\"多运动\"],\"learning\":[\"养成阅读习惯\"]}")
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