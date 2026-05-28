package com.example.modeltest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.modeltest.data.entity.Challenge
import com.example.modeltest.data.entity.ChallengeCompletion
import com.example.modeltest.data.entity.CategoryCompletionCount
import com.example.modeltest.data.entity.ChallengeWithCategoryAndCompletion
import com.example.modeltest.data.entity.DailyCompletionCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    // Home screen: get today's challenges with completion status
    @Query("""
        SELECT c.id, c.categoryId, c.text, c.date,
               cat.name as categoryName, cat.displayName as categoryDisplayName, cat.emoji as categoryEmoji,
               comp.id as completionId, comp.completedAt
        FROM challenges c
        INNER JOIN categories cat ON c.categoryId = cat.id
        LEFT JOIN challenge_completions comp ON c.id = comp.challengeId
        WHERE c.date = :date
        ORDER BY cat.sortOrder
    """)
    fun getChallengesForDate(date: String): Flow<List<ChallengeWithCategoryAndCompletion>>

    // Stats: daily completion counts for date range
    @Query("""
        SELECT c.date, COUNT(comp.id) as count
        FROM challenges c
        LEFT JOIN challenge_completions comp ON c.id = comp.challengeId
        WHERE c.date BETWEEN :startDate AND :endDate
        GROUP BY c.date
    """)
    fun getDailyCompletionCounts(startDate: String, endDate: String): Flow<List<DailyCompletionCount>>

    // Stats: category breakdown for date range
    @Query("""
        SELECT cat.name as categoryName, cat.emoji as emoji, COUNT(comp.id) as count
        FROM challenges c
        INNER JOIN categories cat ON c.categoryId = cat.id
        LEFT JOIN challenge_completions comp ON c.id = comp.challengeId
        WHERE c.date BETWEEN :startDate AND :endDate
        GROUP BY cat.id
    """)
    fun getCategoryBreakdown(startDate: String, endDate: String): Flow<List<CategoryCompletionCount>>

    // Bulk insert challenges
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChallenges(challenges: List<Challenge>)

    // Insert completion
    @Insert
    suspend fun insertCompletion(completion: ChallengeCompletion)

    // Remove completion (undo)
    @Query("DELETE FROM challenge_completions WHERE challengeId = :challengeId")
    suspend fun removeCompletion(challengeId: Long)

    // Check if date has challenges
    @Query("SELECT COUNT(*) FROM challenges WHERE date = :date")
    suspend fun getChallengeCountForDate(date: String): Int

    // Check if challenge is completed
    @Query("SELECT COUNT(*) FROM challenge_completions WHERE challengeId = :challengeId")
    suspend fun isChallengeCompleted(challengeId: Long): Int
}
