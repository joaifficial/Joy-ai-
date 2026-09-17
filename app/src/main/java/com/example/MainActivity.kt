package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.BackgroundAudioService
import com.example.ui.JoyMainScreen
import com.example.ui.PermissionOnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.JoyViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: JoyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleWakeIntent(intent)

        setContent {
            MyApplicationTheme {
                val status by viewModel.uiState.collectAsStateWithLifecycle()
                var hasSkippedOnboarding by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    val showOnboarding = status.missingPermissions.isNotEmpty() && !hasSkippedOnboarding

                    if (showOnboarding) {
                        PermissionOnboardingScreen(
                            onPermissionsGranted = {
                                viewModel.refreshPermissions()
                            },
                            onSkipForNow = {
                                hasSkippedOnboarding = true
                            }
                        )
                    } else {
                        JoyMainScreen(
                            status = status,
                            onOrbClick = { viewModel.onOrbClicked() },
                            onCommandSelected = { command -> viewModel.onCommandSelected(command) },
                            onToggleBackgroundService = { viewModel.toggleBackgroundService() },
                            onInterruptAssistant = { viewModel.interruptSpeaking() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(BackgroundAudioService.ACTION_WAKE_TRIGGER, false) == true) {
            viewModel.handleWakeWordTriggered("Joy")
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.checkAndStartWakeWord()
    }
}

