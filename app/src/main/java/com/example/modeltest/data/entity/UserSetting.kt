package com.example.modeltest.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSetting(
    @PrimaryKey
    val key: String,
    val value: String
)

object SettingKeys {
    const val DAILY_CHALLENGE_COUNT = "daily_challenge_count"
    const val REFRESH_MODE = "refresh_mode"  // "auto" or "manual"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val DEFAULT_CATEGORIES = "default_categories"  // comma-separated category names
    const val ACTIVE_PERIOD = "active_period"  // morning/afternoon/evening/allday
    const val INTENSITY = "intensity"  // light/moderate/hard
    const val SELF_DESCRIPTION_RAW = "self_description_raw"  // user's free-text self-intro, ≤200 chars
    const val SELF_DESCRIPTION_SUMMARY = "self_description_summary"  // model-summarized structured bullets for prompt injection
}