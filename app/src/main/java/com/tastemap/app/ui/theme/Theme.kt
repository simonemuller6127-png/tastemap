package com.tastemap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// M0 占位主题：纸底 + 墨色 + 口味色（与 AGENTS.md 色值表同源）。
// M1 落地手绘设计系统（SPEC D11：霞鹜文楷、纸纹、抖动描边组件）时重写本文件。
private val PaperLightColors = lightColorScheme(
    primary = Color(0xFFD9482B),          // 辣-暖红
    secondary = Color(0xFF6BA292),        // 鲜甜-青绿
    tertiary = Color(0xFFE9C46A),         // 甜-奶黄
    background = Color(0xFFF7F2E7),       // 米白纸底
    surface = Color(0xFFFCF9F1),
    onPrimary = Color(0xFFFCF9F1),
    onBackground = Color(0xFF3B342B),     // 墨色
    onSurface = Color(0xFF3B342B),
)

@Composable
fun TasteMapTheme(content: @Composable () -> Unit) {
    // 深色模式 M1 随手绘系统一起做，M0 固定纸感浅色
    MaterialTheme(colorScheme = PaperLightColors, content = content)
}
