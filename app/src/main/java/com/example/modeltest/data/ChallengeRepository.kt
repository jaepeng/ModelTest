package com.example.modeltest.data

import com.example.modeltest.data.dao.ChallengeDao
import com.example.modeltest.data.entity.Challenge
import com.example.modeltest.data.entity.ChallengeCompletion
import kotlinx.coroutines.flow.Flow

class ChallengeRepository(private val dao: ChallengeDao) {

    fun getTodayChallenges() = dao.getChallengesForDate(DateUtils.todayString())

    fun getChallengesForDate(date: String) = dao.getChallengesForDate(date)

    suspend fun todayHasChallenges(): Boolean =
        dao.getChallengeCountForDate(DateUtils.todayString()) > 0

    suspend fun insertChallenges(challenges: List<Challenge>) =
        dao.insertChallenges(challenges)

    suspend fun toggleCompletion(challengeId: Long): Boolean {
        val completed = dao.isChallengeCompleted(challengeId) > 0
        if (completed) {
            dao.removeCompletion(challengeId)
            return false
        } else {
            dao.insertCompletion(ChallengeCompletion(challengeId = challengeId))
            return true
        }
    }

    fun getDailyStats(startDate: String, endDate: String) =
        dao.getDailyCompletionCounts(startDate, endDate)

    fun getCategoryStats(startDate: String, endDate: String) =
        dao.getCategoryBreakdown(startDate, endDate)

    /**
     * Prepare for new generation: archive completed challenges, delete incomplete ones.
     */
    suspend fun archiveAndCleanupToday() {
        val today = DateUtils.todayString()
        dao.archiveCompletedChallenges(today)
        dao.deleteIncompleteChallenges(today)
    }
}
