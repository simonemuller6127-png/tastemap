package com.tastemap.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 手绘设计系统色板（D11）。唯一来源 = AGENTS.md 口味色值表，改色先改那张表再同步这里。
 * 纸面 = 米白底 + 墨色文字；口味色贯穿贴纸边框 / 地图 / UI 强调色。
 */
object Palette {
    // 纸面
    val Paper = Color(0xFFF7F2E7)       // 纸底
    val PaperRaised = Color(0xFFFCF9F1) // 卡片纸面
    val Ink = Color(0xFF3B342B)         // 墨色文字
    val InkSoft = Color(0xFF6B6357)     // 次级文字
    val InkFaint = Color(0xFF9C9384)    // 占位/禁用

    // 口味色（与 AGENTS.md 色值表逐项对应）
    val Spicy = Color(0xFFD9482B)       // 辣-暖红
    val Sweet = Color(0xFFE9C46A)       // 甜-奶黄
    val Salty = Color(0xFF4C90A8)       // 咸-湖蓝
    val Umami = Color(0xFF6BA292)       // 鲜甜-青绿
    val Sour = Color(0xFFC9B458)        // 酸
    val Light = Color(0xFFA8B5A2)       // 清淡
    val Crispy = Color(0xFFC77B4F)      // 酥脆
    val Neutral = Color(0xFF4A4238)     // 无记录（中性墨）

    /** 手绘描边默认用深墨，弱于主文字以保证层级 */
    val SketchStroke = Color(0xCC4A4238)
}
