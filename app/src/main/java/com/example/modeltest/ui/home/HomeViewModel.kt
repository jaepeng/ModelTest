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
import kotlin.random.Random

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
                val activePeriod = userSettingRepo.getActivePeriod().first()
                val intensity = userSettingRepo.getIntensity().first()
                Log.d(TAG, "Step 2: categories=$defaultCategories, dailyCount=$dailyCount, period=$activePeriod, intensity=$intensity")

                // 方案B: dailyCount < 分类数时,用日期种子轮换选 dailyCount 个分类,每类 1 个。
                // 避免部分分类获得 0 个挑战。同一天种子固定,跨天轮换覆盖所有分类。
                val selectedCategories = if (dailyCount < defaultCategories.size) {
                    defaultCategories.shuffled(Random(today.hashCode())).take(dailyCount)
                } else {
                    defaultCategories
                }
                val baseCount = dailyCount / selectedCategories.size
                val remainder = dailyCount % selectedCategories.size
                Log.d(TAG, "Step 3: baseCount=$baseCount remainder=$remainder selected=${selectedCategories.size}/${defaultCategories.size}")

                // Build name->canonicalKey map so Chinese/English keys both resolve.
                val allCats = categoryDao.getCategoriesByNames(defaultCategories)
                val categoryMap = allCats.flatMap { cat ->
                    listOf(cat.name to cat.id, cat.displayName to cat.id)
                }.toMap()
                val nameToCanonical = allCats.flatMap { cat ->
                    listOf(cat.name to cat.name, cat.displayName to cat.name)
                }.toMap()
                val lowerToCanonical = defaultCategories.associateBy { it.lowercase() }
                fun canonicalKey(raw: String): String? =
                    nameToCanonical[raw] ?: lowerToCanonical[raw.lowercase()]

                // Step 4: Generate per-category (batch B). One LLM call per category,
                // each producing exactly one category's challenges. Avoids the small
                // model skipping categories or producing malformed multi-key JSON.
                val parsed = mutableMapOf<String, List<String>>()
                var lastOutput = ""
                selectedCategories.forEachIndexed { index, cat ->
                    val count = if (index < remainder) baseCount + 1 else baseCount
                    val accepted = mutableSetOf<String>()
                    var remaining = count
                    for (attempt in 1..MAX_GEN_RETRY) {
                        if (remaining <= 0) break
                        Log.d(TAG, "Step 4: Generating '$cat' x$remaining (attempt $attempt/$MAX_GEN_RETRY)...")
                        val fullOutput = StringBuilder()
                        llmService.generateStreaming(buildSingleCategoryPrompt(cat, remaining, activePeriod, intensity)).collect { token ->
                            fullOutput.append(token)
                            _thinkingText.value = "[$cat] $fullOutput"
                            Log.d(TAG, "Token: $token")
                        }
                        lastOutput = fullOutput.toString()
                        Log.d(TAG, "Step 4: '$cat' output length=${lastOutput.length}")

                        val rawParsed = ChallengeParser.parse(lastOutput)
                        val items = rawParsed.entries.firstOrNull()
                            ?.value
                            ?.filter { it.isNotBlank() && it !in accepted }
                            ?: emptyList()
                        if (items.isNotEmpty()) {
                            accepted.addAll(items)
                            Log.d(TAG, "Step 5: '$cat' attempt $attempt got ${items.size}, total ${accepted.size}/$count")
                        } else {
                            Log.w(TAG, "Attempt $attempt: '$cat' produced no new challenges, retrying")
                        }
                        remaining = count - accepted.size
                    }
                    if (accepted.isNotEmpty()) {
                        val trimmed = accepted.toList().take(count)
                        if (trimmed.size < count) {
                            Log.w(TAG, "'$cat' only got ${trimmed.size}/$count after $MAX_GEN_RETRY attempts, keeping what we have")
                        }
                        parsed[cat] = trimmed
                        Log.d(TAG, "Step 5: '$cat' final=${trimmed.size} challenges")
                    } else {
                        Log.w(TAG, "'$cat' failed after $MAX_GEN_RETRY attempts, skipping")
                    }
                }

                if (parsed.isNotEmpty()) {
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
                    Log.w(TAG, "Step 5: no challenges generated for any category")
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

    private fun buildSingleCategoryPrompt(
        category: String,
        count: Int,
        activePeriod: String,
        intensity: String
    ): String {
        val periodDesc = when (activePeriod) {
            "morning" -> "早晨时段（适合唤醒、拉伸、计划类动作）"
            "afternoon" -> "下午时段（适合提神、走动类动作）"
            "evening" -> "晚上时段（避免剧烈，适合放松、反思类动作）"
            else -> "全天时段（无限制）"
        }
        val intensityDesc = when (intensity) {
            "light" -> "轻松强度（动作轻柔低强度，适合恢复）"
            "hard" -> "挑战强度（有一定强度，突破舒适区）"
            else -> "适中强度（常规动作）"
        }
        return buildTextPrompt("你是每日挑战生成助手。\n" +
                "为分类 $category 生成恰好 $count 个挑战，数量不能多不能少。\n" +
                "用户偏好（必须遵守）：\n" +
                "- $periodDesc\n" +
                "- $intensityDesc\n" +
                "挑战设计核心要求：\n" +
                "- 必须是单一、具体、可在5分钟内完成的动作，有明确的开始和结束\n" +
                "- 必须可直接执行，不需要准备、不需要坚持、不是习惯\n" +
                "- 动作要有时长或次数，可量化\n" +
                "- 内容多样化，每次不要重复相同的动作\n" +
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
                "\n" +
                "输出格式（key必须是$category，value是该分类的挑战数组）：\n" +
                "格式：{\"$category\":[\"挑战1\",\"挑战2\"]}")
    }

    @Suppress("unused")
    private fun buildPromptWithRemainder(
        categories: List<String>,
        baseCount: Int,
        remainder: Int,
        missing: List<String> = emptyList()
    ): String {
        val list = categories.mapIndexed { index, cat ->
            val count = if (index < remainder) baseCount + 1 else baseCount
            "${index + 1}. $cat: 恰好 $count 个"
        }.joinToString("\n")
        val sampleA = if (baseCount >= 2) "\"喝一杯温水\",\"起来站5分钟\"" else "\"喝一杯温水\""
        val sampleB = if (baseCount >= 2) "\"看5分钟书\",\"学一个新单词\"" else "\"看5分钟书\""
        val retryHint = if (missing.isNotEmpty()) {
            "特别注意：上次漏了 ${missing.joinToString()}，这次必须包含，不能漏。\n"
        } else ""
        return buildTextPrompt("你是每日挑战生成助手。\n" +
                "$retryHint" +
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
                "好示例：{\"categoryA\":[$sampleA],\"categoryB\":[$sampleB]}\n" +
                "坏示例（禁止）：{\"categoryA\":[\"保持健康饮食\",\"多运动\"],\"categoryB\":[\"养成阅读习惯\"]}")
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