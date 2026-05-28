package com.example.modeltest.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.UserSettingRepository
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repo = UserSettingRepository(db.userSettingDao())

    fun completeOnboarding(
        dailyCount: Int = 5,
        selectedCategories: List<String> = listOf("health", "mindfulness", "learning", "fitness")
    ) {
        viewModelScope.launch {
            repo.setDailyChallengeCount(dailyCount)
            repo.setDefaultCategories(selectedCategories)
            repo.setOnboardingCompleted()
        }
    }
}