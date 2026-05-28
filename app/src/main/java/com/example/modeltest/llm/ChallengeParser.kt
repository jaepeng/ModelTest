package com.example.modeltest.llm

import android.util.Log
import org.json.JSONObject

/**
 * Parses LLM output into a map of category name -> list of challenge texts.
 * Handles cases where LLM wraps JSON in extra text.
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
        val jsonStr = extractJson(rawOutput)
        if (jsonStr.isEmpty()) {
            Log.e(TAG, "No JSON found in output: $rawOutput")
            return emptyMap()
        }

        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, List<String>>()
            for (key in json.keys()) {
                val array = json.getJSONArray(key)
                val challenges = (0 until array.length()).map { array.getString(it) }
                if (challenges.isNotEmpty()) {
                    result[key] = challenges
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON: $jsonStr", e)
            emptyMap()
        }
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
