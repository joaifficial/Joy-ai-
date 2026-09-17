package com.example.model

enum class JoyUiState {
    IDLE,       // Slow subtle breathing glow
    LISTENING,  // Active listening waveform responding to mic input
    THINKING,   // Pulsing neon ring processing
    SPEAKING    // Dynamic audio wave matching Joy's output stream
}

data class JoySessionStatus(
    val state: JoyUiState = JoyUiState.IDLE,
    val statusMessage: String = "Joy is ready. Say \"Joy\" or tap the orb.",
    val lastRecognizedText: String = "",
    val assistantUtterance: String = "",
    val activeToolName: String? = null,
    val toolResultFeedback: String? = null,
    val isLiveConnected: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val audioLevel: Float = 0f, // 0.0f to 1.0f amplitude
    val missingPermissions: List<String> = emptyList()
)
