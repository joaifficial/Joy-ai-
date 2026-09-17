package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JoyDarkColorScheme = darkColorScheme(
  primary = JoyCyan,
  onPrimary = JoyVoidBlack,
  primaryContainer = JoySlateSurface,
  onPrimaryContainer = JoyCyan,
  secondary = JoyElectricBlue,
  onSecondary = JoyVoidBlack,
  tertiary = JoyNeonGreen,
  background = JoyVoidBlack,
  onBackground = JoyTextPrimary,
  surface = JoyObsidianNavy,
  onSurface = JoyTextPrimary,
  surfaceVariant = JoySlateSurface,
  onSurfaceVariant = JoyTextSecondary,
  outline = JoySlateBorder
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      window?.let {
        it.statusBarColor = JoyVoidBlack.toArgb()
        it.navigationBarColor = JoyVoidBlack.toArgb()
        val controller = WindowCompat.getInsetsController(it, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
      }
    }
  }

  MaterialTheme(
    colorScheme = JoyDarkColorScheme,
    typography = Typography,
    content = content
  )
}

