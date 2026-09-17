package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JoySessionStatus
import com.example.model.JoyUiState
import com.example.ui.theme.JoyAmber
import com.example.ui.theme.JoyCyan
import com.example.ui.theme.JoyElectricBlue
import com.example.ui.theme.JoyNeonGreen
import com.example.ui.theme.JoyObsidianNavy
import com.example.ui.theme.JoyRed
import com.example.ui.theme.JoySlateBorder
import com.example.ui.theme.JoySlateSurface
import com.example.ui.theme.JoyTextMuted
import com.example.ui.theme.JoyTextPrimary
import com.example.ui.theme.JoyTextSecondary
import com.example.ui.theme.JoyVoidBlack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoyMainScreen(
    status: JoySessionStatus,
    onOrbClick: () -> Unit,
    onCommandSelected: (String) -> Unit,
    onToggleBackgroundService: () -> Unit,
    onInterruptAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateAccentColor = when (status.state) {
        JoyUiState.IDLE -> JoyCyan
        JoyUiState.LISTENING -> JoyNeonGreen
        JoyUiState.THINKING -> JoyAmber
        JoyUiState.SPEAKING -> JoyElectricBlue
    }

    val stateBadgeText = when (status.state) {
        JoyUiState.IDLE -> "JOY • READY"
        JoyUiState.LISTENING -> "JOY • LISTENING"
        JoyUiState.THINKING -> "JOY • THINKING"
        JoyUiState.SPEAKING -> "JOY • SPEAKING"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JoyVoidBlack, JoyObsidianNavy, JoyVoidBlack)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER: Branding & Background Service Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(JoySlateSurface.copy(alpha = 0.7f))
                    .border(1.dp, JoySlateBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Joy Identity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stateAccentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "JOY ASSISTANT",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                color = JoyTextPrimary
                            )
                        )
                        Text(
                            text = "Gemini Live Voice • Background Wake",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = JoyCyan,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Right: Background Service Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (status.isBackgroundServiceRunning)
                        JoyNeonGreen.copy(alpha = 0.15f)
                    else
                        JoySlateBorder.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable(onClick = onToggleBackgroundService)
                        .testTag("toggle_background_service")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Background Service",
                            tint = if (status.isBackgroundServiceRunning) JoyNeonGreen else JoyTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (status.isBackgroundServiceRunning) "BG ON" else "BG OFF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (status.isBackgroundServiceRunning) JoyNeonGreen else JoyTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CENTER STAGE: Central Animated Orb & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Status Badge Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = stateAccentColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, stateAccentColor.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stateAccentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stateBadgeText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = stateAccentColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }

                // Large Central Animated Orb
                JoyOrbVisualizer(
                    state = status.state,
                    audioLevel = status.audioLevel,
                    onClick = onOrbClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Real-time dynamic status subtitle
                Text(
                    text = status.statusMessage,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = JoyTextPrimary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Interrupt button when assistant is actively speaking
                AnimatedVisibility(
                    visible = status.state == JoyUiState.SPEAKING,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JoyRed.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JoyRed),
                        modifier = Modifier
                            .clickable(onClick = onInterruptAssistant)
                            .testTag("interrupt_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Interrupt",
                                tint = JoyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bhai, ruko (Interrupt)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = JoyRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // BOTTOM PANEL: Persona Action Confirmations & Voice Suggestion Chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tool confirmation banner (if an action was recently performed)
                if (!status.toolResultFeedback.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JoySlateSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JoyCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = JoyCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "JOY CONFIRMATION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = JoyCyan,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = "“${status.toolResultFeedback}”",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = JoyTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Voice Command Inspirations (Chips)
                Text(
                    text = "SAY \"JOY\" OR TRY THESE COMMANDS:",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = JoyTextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val suggestions = listOf(
                        "Who is your developer?",
                        "Joy, YouTube kholo",
                        "Mummy ko call karo",
                        "Calculator kholo",
                        "WhatsApp Mummy ko message bhejo",
                        "Gmail draft karo"
                    )

                    for (cmd in suggestions) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = JoySlateSurface.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, JoySlateBorder),
                            modifier = Modifier
                                .clickable { onCommandSelected(cmd) }
                                .testTag("command_chip_${cmd.replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = JoyCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = JoyTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Zero-Touch Pure Voice Interface • Always Powered by Joy",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = JoyTextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
