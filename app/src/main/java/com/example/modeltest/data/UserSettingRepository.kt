package com.example.modeltest.data

import com.example.modeltest.data.dao.UserSettingDao
import com.example.modeltest.data.entity.SettingKeys
import com.example.modeltest.data.entity.UserSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserSettingRepository(private val dao: UserSettingDao) {

    fun getDailyChallengeCount(): Flow<Int> =
        dao.getSetting(SettingKeys.DAILY_CHALLENGE_COUNT).map { it?.toIntOrNull() ?: 5 }

    fun getRefreshMode(): Flow<String> =
        dao.getSetting(SettingKeys.REFRESH_MODE).map { it ?: "auto" }

    fun isOnboardingCompleted(): Flow<Boolean> =
        dao.getSetting(SettingKeys.ONBOARDING_COMPLETED).map { it == "true" }

    fun getDefaultCategories(): Flow<List<String>> =
        dao.getSetting(SettingKeys.DEFAULT_CATEGORIES).map {
            it?.split(",")?.filter { s -> s.isNotBlank() } ?: listOf("health", "mindfulness", "learning", "fitness")
        }

    suspend fun setDailyChallengeCount(count: Int) {
        dao.setSetting(UserSetting(SettingKeys.DAILY_CHALLENGE_COUNT, count.toString()))
    }

    suspend fun setRefreshMode(mode: String) {
        dao.setSetting(UserSetting(SettingKeys.REFRESH_MODE, mode))
    }

    suspend fun setOnboardingCompleted() {
        dao.setSetting(UserSetting(SettingKeys.ONBOARDING_COMPLETED, "true"))
    }

    suspend fun setDefaultCategories(categories: List<String>) {
        dao.setSetting(UserSetting(SettingKeys.DEFAULT_CATEGORIES, categories.joinToString(",")))
    }
}