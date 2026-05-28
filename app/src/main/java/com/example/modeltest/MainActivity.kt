package com.example.modeltest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.modeltest.data.AppDatabase
import com.example.modeltest.data.UserSettingRepository
import com.example.modeltest.ui.navigation.AppNavigation
import com.example.modeltest.ui.navigation.Screen
import com.example.modeltest.ui.theme.ModelTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val userSettingRepo = UserSettingRepository(db.userSettingDao())

        setContent {
            ModelTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingCompleted by userSettingRepo.isOnboardingCompleted().collectAsState(initial = null)

                    when (onboardingCompleted) {
                        null -> { /* Loading */ }
                        false -> {
                            AppNavigation(startDestination = "onboarding")
                        }
                        true -> {
                            AppNavigation(startDestination = Screen.Home.route)
                        }
                    }
                }
            }
        }
    }
}