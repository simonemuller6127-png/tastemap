package com.tastemap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.tastemap.app.R

// 手写体：霞鹜文楷 v1.522（OFL 免费商用，D11）。R4 做子集化控体积（全量 ~25MB）。
val Handwritten = FontFamily(Font(R.font.lxgw_wenkai_regular))

// 纸面色板在 Palette.kt（D11 设计系统，色值唯一来源 = AGENTS.md 色值表）
private val PaperLightColors = lightColorScheme(
    primary = Palette.Spicy,        // 辣-暖红
    secondary = Palette.Umami,      // 鲜甜-青绿
    tertiary = Palette.Sweet,       // 甜-奶黄
    background = Palette.Paper,     // 米白纸底
    surface = Palette.PaperRaised,
    onPrimary = Palette.PaperRaised,
    onBackground = Palette.Ink,     // 墨色
    onSurface = Palette.Ink,
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
    // 深色模式 R1 随手绘小样一起评估（纸面风格可能固定浅色），当前固定纸感浅色
    MaterialTheme(
        colorScheme = PaperLightColors,
        typography = handwrittenTypography(),
        content = content,
    )
}
