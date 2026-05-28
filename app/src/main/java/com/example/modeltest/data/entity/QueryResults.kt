package com.example.modeltest.data.entity

// Query result for home screen: challenge + category + completion status
data class ChallengeWithCategoryAndCompletion(
    val id: Long,
    val categoryId: Long,
    val text: String,
    val date: String,
    val categoryName: String,
    val categoryDisplayName: String,
    val categoryEmoji: String,
    val completionId: Long?,  // null = not completed
    val completedAt: Long?
)

// Query result for daily stats
data class DailyCompletionCount(
    val date: String,
    val count: Int
)

// Query result for category breakdown
data class CategoryCompletionCount(
    val categoryName: String,
    val emoji: String,
    val count: Int
)
