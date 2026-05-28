package com.example.modeltest.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.ChallengeRepository
import com.example.modeltest.data.DateUtils
import com.example.modeltest.data.entity.CategoryCompletionCount
import com.example.modeltest.data.entity.DailyCompletionCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repo = ChallengeRepository(db.challengeDao())

    private val _dateRange = MutableStateFlow(DateUtils.currentMonthRange())
    val dateRange: StateFlow<Pair<String, String>> = _dateRange.asStateFlow()

    val dailyStats: StateFlow<List<DailyCompletionCount>> = _dateRange.flatMapLatest { range ->
        repo.getDailyStats(range.first, range.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryStats: StateFlow<List<CategoryCompletionCount>> = _dateRange.flatMapLatest { range ->
        repo.getCategoryStats(range.first, range.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRange(range: Pair<String, String>) {
        _dateRange.value = range
    }

    fun setWeekRange() {
        _dateRange.value = DateUtils.weekStartString() to DateUtils.weekEndString()
    }

    fun setMonthRange() {
        _dateRange.value = DateUtils.currentMonthRange()
    }

    fun setYearRange() {
        _dateRange.value = DateUtils.currentYearRange()
    }
}