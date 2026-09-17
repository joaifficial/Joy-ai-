package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.LiveSessionManager
import com.example.model.JoySessionStatus
import com.example.model.JoyUiState
import com.example.permission.PermissionManager
import com.example.service.BackgroundAudioService
import com.example.tools.ToolExecutionEngine
import com.example.wakeword.WakeWordDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoyViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val permissionManager = PermissionManager(context)
    val toolExecutionEngine = ToolExecutionEngine(context, permissionManager)

    private val _uiState = MutableStateFlow(
        JoySessionStatus(
            missingPermissions = permissionManager.getMissingPermissions()
        )
    )
    val uiState: StateFlow<JoySessionStatus> = _uiState.asStateFlow()

    private var liveSessionManager: LiveSessionManager? = null
    private var inAppWakeWordDetector: WakeWordDetector? = null

    init {
        initLiveSession()
        observeBackgroundService()
        checkAndStartWakeWord()
    }

    private fun initLiveSession() {
        liveSessionManager = LiveSessionManager(
            context = context,
            toolExecutionEngine = toolExecutionEngine,
            onStateChanged = { newState, message ->
                _uiState.update { current ->
                    current.copy(
                        state = newState,
                        statusMessage = message,
                        isLiveConnected = liveSessionManager?.isConnected() ?: false
                    )
                }
            },
            onAudioLevel = { level ->
                _uiState.update { it.copy(audioLevel = level) }
            },
            onToolExecuted = { toolName, confirmation ->
                _uiState.update { current ->
                    current.copy(
                        activeToolName = toolName,
                        toolResultFeedback = confirmation,
                        assistantUtterance = confirmation
                    )
                }
            }
        )
    }

    private fun observeBackgroundService() {
        viewModelScope.launch {
            BackgroundAudioService.isRunning.collect { running ->
                _uiState.update { it.copy(isBackgroundServiceRunning = running) }
            }
        }

        viewModelScope.launch {
            BackgroundAudioService.wakeWordEvents.collect { word ->
                handleWakeWordTriggered(word)
            }
        }
    }

    fun checkAndStartWakeWord() {
        refreshPermissions()
        if (permissionManager.hasRecordAudioPermission()) {
            if (inAppWakeWordDetector == null) {
                inAppWakeWordDetector = WakeWordDetector(
                    context = context,
                    onWakeWordDetected = { word ->
                        handleWakeWordTriggered(word)
                    },
                    onAudioLevelChanged = { level ->
                        if (_uiState.value.state == JoyUiState.IDLE) {
                            _uiState.update { it.copy(audioLevel = level) }
                        }
                    }
                )
            }
            inAppWakeWordDetector?.startListening()
        }
    }

    fun handleWakeWordTriggered(word: String) {
        _uiState.update {
            it.copy(
                state = JoyUiState.LISTENING,
                statusMessage = "Haan bhai! Joy sun raha hai, bolo...",
                lastRecognizedText = word
            )
        }
        liveSessionManager?.startSession(initialGreeting = true)
    }

    /**
     * Toggles between listening and idle states upon tapping the central orb.
     */
    fun onOrbClicked() {
        val currentState = _uiState.value.state
        if (currentState == JoyUiState.SPEAKING) {
            liveSessionManager?.interruptAssistant()
            return
        }

        if (currentState == JoyUiState.LISTENING || currentState == JoyUiState.THINKING) {
            liveSessionManager?.stopSession()
            checkAndStartWakeWord()
        } else {
            inAppWakeWordDetector?.stopListening()
            liveSessionManager?.startSession(initialGreeting = true)
        }
    }

    fun onCommandSelected(command: String) {
        liveSessionManager?.interruptAssistant()
        _uiState.update {
            it.copy(
                lastRecognizedText = command,
                statusMessage = "Joy: \"Haan bhai!\""
            )
        }
        liveSessionManager?.processSpokenCommand(command)
    }

    fun refreshPermissions() {
        val missing = permissionManager.getMissingPermissions()
        _uiState.update { it.copy(missingPermissions = missing) }

        // Start background service if all required permissions are granted and not yet running
        if (missing.isEmpty() && !_uiState.value.isBackgroundServiceRunning) {
            try {
                BackgroundAudioService.start(context)
            } catch (e: Exception) {
                // Background start handling
            }
        }
    }

    fun toggleBackgroundService() {
        if (_uiState.value.isBackgroundServiceRunning) {
            BackgroundAudioService.stop(context)
        } else {
            BackgroundAudioService.start(context)
        }
    }

    fun interruptSpeaking() {
        liveSessionManager?.interruptAssistant()
    }

    override fun onCleared() {
        super.onCleared()
        inAppWakeWordDetector?.stopListening()
        inAppWakeWordDetector = null
        liveSessionManager?.release()
        liveSessionManager = null
    }
}
