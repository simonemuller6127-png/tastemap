package com.tastemap.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tastemap.app.R
import com.tastemap.app.util.Prefs

// 手写体：霞鹜文楷 v1.522（OFL 免费商用，D11）。R4 做子集化控体积（全量 ~25MB）。
val Handwritten = FontFamily(Font(R.font.lxgw_wenkai_regular))

/**
 * 纸面色板（D11，R3 反馈补全）：Material 组件的容器/描边色全部换成纸调，
 * 消灭默认淡紫灰（surfaceVariant 等）与手绘风的违和感。色值唯一来源 = AGENTS.md 色值表。
 */
private val PaperLightColors = lightColorScheme(
    primary = Palette.Spicy,            // 辣-暖红
    onPrimary = Palette.PaperRaised,
    primaryContainer = Color(0xFFF6DDD0),
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Umami,          // 鲜甜-青绿
    onSecondary = Palette.PaperRaised,
    secondaryContainer = Color(0xFFE2EAD9),
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Sweet,           // 甜-奶黄
    onTertiary = Palette.Ink,
    tertiaryContainer = Color(0xFFF6E7C8),
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,         // 米白纸底
    onBackground = Palette.Ink,
    surface = Palette.PaperRaised,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF1E9D6), // 纸调（替代默认淡紫）
    onSurfaceVariant = Palette.InkSoft,
    outline = Color(0xFFC9BFA8),
    outlineVariant = Color(0xFFE3D9C2),
    error = Color(0xFFB3261E),
    onError = Palette.PaperRaised,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF690005),
    inverseSurface = Color(0xFF3B342B),
    inverseOnSurface = Palette.Paper,
    surfaceContainer = Color(0xFFF3ECDC),
    surfaceContainerLow = Color(0xFFF6F0E1),
    surfaceContainerLowest = Color(0xFFFBF7EC),
    surfaceContainerHigh = Color(0xFFEFE7D5),
    surfaceContainerHighest = Color(0xFFEAE1CE),
)

/** 手绘风全局形状：大圆角，柔和有机（R3 反馈：官方直角/小圆角组件与手绘不搭） */
private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 全部字级换成手写体（material3 Typography 无统一 fontFamily 参数，逐级 copy） */
private fun handwrittenTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Handwritten),
        displayMedium = base.displayMedium.copy(fontFamily = Handwritten),
        displaySmall = base.displaySmall.copy(fontFamily = Handwritten),
        headlineLarge = base.headlineLarge.copy(fontFamily = Handwritten),
        headlineMedium = base.headlineMedium.copy(fontFamily = Handwritten),
        headlineSmall = base.headlineSmall.copy(fontFamily = Handwritten),
        titleLarge = base.titleLarge.copy(fontFamily = Handwritten),
        titleMedium = base.titleMedium.copy(fontFamily = Handwritten),
        titleSmall = base.titleSmall.copy(fontFamily = Handwritten),
        bodyLarge = base.bodyLarge.copy(fontFamily = Handwritten),
        bodyMedium = base.bodyMedium.copy(fontFamily = Handwritten),
        bodySmall = base.bodySmall.copy(fontFamily = Handwritten),
        labelLarge = base.labelLarge.copy(fontFamily = Handwritten),
        labelMedium = base.labelMedium.copy(fontFamily = Handwritten),
        labelSmall = base.labelSmall.copy(fontFamily = Handwritten),
    )
}

@Composable
fun TasteMapTheme(content: @Composable () -> Unit) {
    // R3 反馈：全局字号无级调节（85%–135%），经 Density.fontScale 生效
    val context = LocalContext.current
    val fontScale by remember { Prefs(context).fontScale }
        .collectAsStateWithLifecycle(initialValue = 1f)
    val current = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, current.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = PaperLightColors,
            typography = handwrittenTypography(),
            shapes = PaperShapes,
            content = content,
        )
    }
}
