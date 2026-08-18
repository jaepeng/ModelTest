package com.example.modeltest.llm

import android.content.Context
import android.util.Log
import com.example.modeltest.data.UserSettingRepository
import kotlinx.coroutines.flow.first

/**
 * Summarizes a user's free-text self-description into structured bullets
 * for injection into challenge-generation prompts.
 *
 * Flow: save raw → call LLM (non-streaming) → save summary.
 * On failure, falls back to a truncated raw so prompt injection still works.
 */
class SelfDescriptionService(private val context: Context) {

    companion object {
        private const val TAG = "SelfDescService"
        private const val MAX_RAW_LEN = 200
        private const val FALLBACK_SUMMARY_LEN = 100
    }

    private val llmService = com.example.modeltest.llm.LlmService(context)

    /**
     * Summarize raw self-description and persist both raw + summary.
     * Returns the summary (empty string if input was blank).
     */
    suspend fun summarizeAndSave(
        repo: UserSettingRepository,
        rawInput: String
    ): String {
        val trimmed = rawInput.trim().take(MAX_RAW_LEN)
        repo.setSelfDescriptionRaw(trimmed)

        if (trimmed.isBlank()) {
            repo.setSelfDescriptionSummary("")
            return ""
        }

        return try {
            llmService.initialize()
            val prompt = buildSummaryPrompt(trimmed)
            Log.d(TAG, "Summary prompt length=${prompt.length}")
            val output = llmService.generate(prompt)
            Log.d(TAG, "Summary output length=${output.length}")

            val cleaned = cleanSummary(output)
            if (cleaned.isBlank()) {
                Log.w(TAG, "Summary empty, falling back to truncated raw")
                val fallback = trimmed.take(FALLBACK_SUMMARY_LEN)
                repo.setSelfDescriptionSummary(fallback)
                fallback
            } else {
                repo.setSelfDescriptionSummary(cleaned)
                cleaned
            }
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed, using fallback", e)
            val fallback = trimmed.take(FALLBACK_SUMMARY_LEN)
            repo.setSelfDescriptionSummary(fallback)
            fallback
        }
    }

    /**
     * Read existing summary from repo (for prompt injection).
     */
    suspend fun getSummary(repo: UserSettingRepository): String =
        repo.getSelfDescriptionSummary().first()

    private fun buildSummaryPrompt(raw: String): String {
        return "<|im_start|>user\n" +
                "你是用户画像提取助手。\n" +
                "用户自我介绍如下：\n" +
                "---\n" +
                "$raw\n" +
                "---\n" +
                "提取对\"每日5分钟挑战生成\"有用的信息，输出结构化要点：\n" +
                "- 目标：用户想达成什么（减肥/减压/学习/睡眠…）\n" +
                "- 偏好：喜欢的活动类型（户外/室内/静坐/运动…）\n" +
                "- 限制：身体或时间约束（腰伤/久坐/只有早晨…）\n" +
                "- 兴趣：具体爱好（阅读/绘画/音乐…）\n" +
                "每条一行，≤15字。无相关信息则省略该行。\n" +
                "不要输出思考过程，不要解释，直接输出要点。<|im_end|>\n" +
                "<|im_start|>assistant\n"
    }

    /**
     * Clean model output: strip reasoning blocks, trim whitespace.
     */
    private fun cleanSummary(output: String): String {
        return output.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }
}
