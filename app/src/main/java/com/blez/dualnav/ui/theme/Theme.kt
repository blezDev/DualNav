package com.blez.dualnav.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blez.dualnav.core.domain.model.AppThemeMode

/** True when the Bleach-inspired theme is active, so screens can swap in themed icons/decoration. */
val LocalIsBleachTheme = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val BleachDarkColorScheme = darkColorScheme(
    primary = BleachCrimson,
    onPrimary = BleachBone,
    primaryContainer = BleachCrimsonDeep,
    onPrimaryContainer = BleachBone,
    secondary = BleachAsh,
    onSecondary = BleachBlack,
    background = BleachBlack,
    onBackground = BleachBone,
    surface = BleachCharcoal,
    onSurface = BleachBone,
    surfaceVariant = BleachCharcoalVariant,
    onSurfaceVariant = BleachAsh,
    error = BleachEmber,
    onError = BleachBlack
)

private val BleachLightColorScheme = lightColorScheme(
    primary = BleachCrimsonLight,
    onPrimary = Color.White,
    primaryContainer = BleachIvoryVariant,
    onPrimaryContainer = BleachBlack,
    secondary = BleachAshDark,
    onSecondary = Color.White,
    background = BleachIvory,
    onBackground = BleachBlack,
    surface = Color.White,
    onSurface = BleachBlack,
    surfaceVariant = BleachIvoryVariant,
    onSurfaceVariant = BleachAshDark,
    error = BleachEmberDark,
    onError = Color.White
)

/** Sharp, angular corners instead of Material's soft rounding — reads closer to a blade's edge. */
private val BleachShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

private val BleachTypography = Typography.copy(
    headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
    titleLarge = Typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
    titleMedium = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = Typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
)

@Composable
fun DualNavTheme(
    themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but the Bleach theme always uses its own palette.
    dynamicColor: Boolean = themeMode == AppThemeMode.DEFAULT,
    content: @Composable () -> Unit
) {
    val isBleach = themeMode == AppThemeMode.BLEACH
    val colorScheme = when {
        isBleach -> if (darkTheme) BleachDarkColorScheme else BleachLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsBleachTheme provides isBleach) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (isBleach) BleachTypography else Typography,
            shapes = if (isBleach) BleachShapes else MaterialTheme.shapes,
            content = content
        )
    }
}
