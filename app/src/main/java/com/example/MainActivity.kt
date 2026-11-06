package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.data.repository.QuizPrefs
import com.example.ui.Startup
import com.example.ui.screens.MainNavigationHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  /** Where the nav graph starts; null while it is being worked out (the system splash stays up). */
  private var startRoute by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    val splash = installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    splash.setKeepOnScreenCondition { startRoute == null }

    val repository = (application as QuizApplication).repository
    lifecycleScope.launch {
      startRoute = Startup.resolveStartRoute(QuizPrefs(applicationContext)) {
        repository.userStats.first()
      }
    }

    setContent {
      startRoute?.let { MainNavigationHost(startDestination = it) }
    }
  }
}
