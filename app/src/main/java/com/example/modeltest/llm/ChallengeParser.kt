package com.example.modeltest.llm

import android.util.Log
import org.json.JSONObject

/**
 * Parses LLM output into a map of category name -> list of challenge texts.
 * Handles cases where LLM wraps JSON in extra text or emits malformed JSON
 * (truncated arrays, missing brackets, etc.) common with small on-device models.
 */
object ChallengeParser {

    private const val TAG = "ChallengeParser"

    /**
     * Parse LLM output into category -> challenges map.
     * Returns empty map on failure.
     *
     * Expected input format (possibly with extra text around it):
     * {"health":["喝一杯水","起来站5分钟","深呼吸3次"],"mindfulness":[...]}
     */
    fun parse(rawOutput: String): Map<String, List<String>> {
        // Strip <think>...</think> blocks (model reasoning) before parsing
        val cleaned = rawOutput.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        Log.d(TAG, "Cleaned output (after removing <think>): $cleaned")

        // First try strict parse of the best JSON candidate.
        val jsonStr = extractJson(cleaned)
        if (jsonStr.isEmpty()) {
            Log.e(TAG, "No JSON found in output: $rawOutput")
            return emptyMap()
        }

        strictParse(jsonStr)?.let { return it }

        // Strict parse failed (truncated/malformed). Try to salvage individual
        // string items via regex so a single bad bracket doesn't lose all challenges.
        Log.w(TAG, "Strict parse failed, trying salvage: $jsonStr")
        return salvageParse(jsonStr)
    }

    private fun strictParse(jsonStr: String): Map<String, List<String>>? {
        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, List<String>>()
            for (key in json.keys()) {
                // Tolerate value being a single string instead of an array (small model
                // often emits {"cat":"text"} instead of {"cat":["text"]} when count=1).
                val challenges: List<String> = when (val v = json.get(key)) {
                    is org.json.JSONArray -> (0 until v.length()).map { v.getString(it) }
                        .filter { it.isNotBlank() }
                    is String -> if (v.isNotBlank()) listOf(v) else emptyList()
                    else -> emptyList()
                }
                if (challenges.isNotEmpty()) {
                    result[key] = challenges
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Strict parse error: ${e.message}")
            null
        }
    }

    /**
     * Salvage parse: when JSON is truncated like {"nature":["快速整理物品"}
     * (missing closing ]}), extract what we can with regex.
     * Strategy: find the key, then grab all quoted strings after it until structure breaks.
     */
    private fun salvageParse(jsonStr: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        // Match: "key" : [ "item1" , "item2" ... (array may be unterminated)
        val pattern = Regex("\"([a-zA-Z]+)\"\\s*:\\s*\\[?([^\\]}]*)")
        for (m in pattern.findAll(jsonStr)) {
            val key = m.groupValues[1]
            val rest = m.groupValues[2]
            // Extract all quoted strings from the value portion.
            val items = Regex("\"([^\"]+)\"").findAll(rest)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (items.isNotEmpty() && key !in result) {
                result[key] = items
                Log.w(TAG, "Salvaged $key: $items")
            }
        }
        if (result.isEmpty()) {
            Log.e(TAG, "Salvage failed for: $jsonStr")
        }
        return result
    }

    /**
     * Extract JSON object from LLM output that may contain extra text.
     * Finds the first '{' and last '}' to extract the JSON object.
     */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return ""
        return text.substring(start, end + 1)
    }
}
