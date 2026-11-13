package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = SkyInkPrimary,
    onPrimary = OnSkyInkPrimary,
    primaryContainer = SkyInkContainer,
    onPrimaryContainer = SkyInkOnContainer,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralOnContainer,
    tertiary = MintTertiary,
    onTertiary = OnMintTertiary,
    tertiaryContainer = MintContainer,
    onTertiaryContainer = MintOnContainer,
    background = ObsidianBackground,
    onBackground = OffWhiteText,
    surface = ObsidianSurface,
    onSurface = OffWhiteText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = MutedGrayText,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    primaryContainer = InkPrimaryContainer,
    onPrimaryContainer = InkOnPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = SageTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SageContainer,
    onTertiaryContainer = OnSageContainer,
    background = PaperBackground,
    onBackground = CharcoalText,
    surface = LinenSurface,
    onSurface = CharcoalText,
    surfaceVariant = LinenSurfaceContainer,
    onSurfaceVariant = SlateMutedText,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
  )

val PostboxShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun PostboxTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = PostboxShapes,
    content = content,
  )
}
