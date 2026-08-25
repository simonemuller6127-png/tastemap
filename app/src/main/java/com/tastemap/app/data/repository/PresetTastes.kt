package com.tastemap.app.data.repository

import com.tastemap.app.data.db.TasteTag

/** 预置口味（PRD F03）。色值表同时登记在 AGENTS.md，是全局唯一来源。 */
object PresetTastes {
    const val NEUTRAL_PIN_COLOR = "#4A4238" // 无记录店铺的中性墨色图钉

    val ALL = listOf(
        TasteTag(name = "辣", colorHex = "#D9482B", isPreset = true, sortOrder = 1),
        TasteTag(name = "甜", colorHex = "#E9C46A", isPreset = true, sortOrder = 2),
        TasteTag(name = "咸", colorHex = "#4C90A8", isPreset = true, sortOrder = 3),
        TasteTag(name = "鲜甜", colorHex = "#6BA292", isPreset = true, sortOrder = 4),
        TasteTag(name = "酸", colorHex = "#C9B458", isPreset = true, sortOrder = 5),
        TasteTag(name = "清淡", colorHex = "#A8B5A2", isPreset = true, sortOrder = 6),
        TasteTag(name = "酥脆", colorHex = "#C77B4F", isPreset = true, sortOrder = 7),
    )

    /** 古风推荐配色（R3 反馈：自定义口味给推荐色，符合手绘/古风气质），名字即传统色名 */
    val GUOFENG_PALETTE = listOf(
        "胭脂" to "#9D2933",
        "朱砂" to "#C3272B",
        "缃色" to "#F0C239",
        "杏黄" to "#FFA631",
        "竹青" to "#789262",
        "青碧" to "#2C9678",
        "黛蓝" to "#425066",
        "绛紫" to "#8C4356",
        "藕荷" to "#E4C6D0",
        "赭石" to "#955539",
    )
}
