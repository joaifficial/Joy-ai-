package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.permission.PermissionManager
import com.example.ui.theme.JoyCyan
import com.example.ui.theme.JoyElectricBlue
import com.example.ui.theme.JoyNeonGreen
import com.example.ui.theme.JoyObsidianNavy
import com.example.ui.theme.JoySlateBorder
import com.example.ui.theme.JoySlateSurface
import com.example.ui.theme.JoyTextMuted
import com.example.ui.theme.JoyTextPrimary
import com.example.ui.theme.JoyTextSecondary
import com.example.ui.theme.JoyVoidBlack

@Composable
fun PermissionOnboardingScreen(
    onPermissionsGranted: () -> Unit,
    onSkipForNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Check if any was updated
        onPermissionsGranted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JoyVoidBlack, JoyObsidianNavy, JoyVoidBlack)
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing AI Shield Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(JoySlateSurface)
                    .border(2.dp, JoyCyan, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security & Permissions",
                    tint = JoyCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to Joy",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = JoyTextPrimary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your Smart Real-Time Personal Assistant",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = JoyCyan,
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Friendly Hinglish Persona Quote Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JoySlateSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JoySlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "JOY SAYS:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = JoyNeonGreen,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "“Bhai, is kaam ke liye mujhe permission chahiye. Permission on kar do, phir main kar deta hoon.”",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = JoyTextPrimary,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Permission Items List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionFeatureItem(
                    icon = Icons.Default.Mic,
                    title = "Microphone (RECORD_AUDIO)",
                    description = "For continuous bi-directional voice chat and wake-word \"Joy\" detection."
                )

                PermissionFeatureItem(
                    icon = Icons.Default.Contacts,
                    title = "Contacts (READ_CONTACTS)",
                    description = "To find family & friends when you say \"Mummy ko call karo\"."
                )

                PermissionFeatureItem(
                    icon = Icons.Default.Call,
                    title = "Phone Calls (CALL_PHONE)",
                    description = "To dial and initiate phone calls hands-free."
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionFeatureItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Runs persistently in the background so Joy is always ready."
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Grant Action Button
            Button(
                onClick = {
                    permissionLauncher.launch(PermissionManager.REQUIRED_PERMISSIONS)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JoyCyan,
                    contentColor = JoyVoidBlack
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("grant_permissions_button")
            ) {
                Text(
                    text = "Permission On Kar Do",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSkipForNow,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JoyTextSecondary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("skip_permissions_button")
            ) {
                Text(
                    text = "Proceed to App",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun PermissionFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JoySlateSurface.copy(alpha = 0.65f))
            .border(1.dp, JoySlateBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(JoyElectricBlue.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JoyCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = JoyTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = JoyTextMuted,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
