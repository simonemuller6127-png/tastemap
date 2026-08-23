package com.tastemap.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tastemap.app.ui.MapHomeScreen
import com.tastemap.app.ui.record.RecordEditScreen

/**
 * R0 导航骨架：10 页路由表一次定稿（SPEC §2 页面清单）。
 * 当前只有地图主页是实屏，其余为占位屏，由 R2 各切片按认领功能替换实现。
 * 约定：新增页面不改路由常量名，只换 composable 内容——路由表是并行切片的共享契约（SPEC §6）。
 */
object Routes {
    const val MAP_HOME = "map_home"                                  // F01/F18 地图主页
    const val RECORD_EDIT = "record_edit?lat={lat}&lng={lng}"        // F02 新建/编辑记录（长按地图带坐标进入）
    const val SHOP_DETAIL = "shop_detail/{shopId}"                   // F04 店铺详情+时间线
    const val REVIEW_FEED = "review_feed"                            // F07 回顾卡片流
    const val SCHEDULE = "schedule"                                  // F08 美食日程
    const val WISHLIST = "wishlist"                                  // F09 想吃清单
    const val SEARCH = "search"                                      // F05 搜索筛选
    const val CARD_COMPOSER = "card_composer"                        // F13/F14 卡片生成与分享
    const val STICKER_STUDIO = "sticker_studio"                      // F22-F24 贴纸工坊
    const val SETTINGS = "settings"                                  // 设置（口味管理 F03 入口等）

    fun recordEdit(lat: Double, lng: Double) = "record_edit?lat=$lat&lng=$lng"
    fun shopDetail(shopId: Long) = "shop_detail/$shopId"
}

@Composable
fun TasteMapNav(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MAP_HOME) {
        composable(Routes.MAP_HOME) {
            // 长按地图 → 完整记录页（R2 记录流切片，替代 M0 的底部快捷面板）
            MapHomeScreen(
                onCreateRecord = { lat, lng -> navController.navigate(Routes.recordEdit(lat, lng)) },
            )
        }
        composable(
            Routes.RECORD_EDIT,
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lng") { type = NavType.StringType },
            ),
        ) {
            RecordEditScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SHOP_DETAIL) { PlaceholderScreen("店铺详情 F04（R2 记录流切片）") }
        composable(Routes.REVIEW_FEED) { PlaceholderScreen("回顾卡片流 F07（R2 回顾日程切片）") }
        composable(Routes.SCHEDULE) { PlaceholderScreen("美食日程 F08（R2 回顾日程切片）") }
        composable(Routes.WISHLIST) { PlaceholderScreen("想吃清单 F09（R2 回顾日程切片）") }
        composable(Routes.SEARCH) { PlaceholderScreen("搜索筛选 F05（R2 搜索口味切片）") }
        composable(Routes.CARD_COMPOSER) { PlaceholderScreen("卡片生成 F13/F14（R2 分享闭环切片）") }
        composable(Routes.STICKER_STUDIO) { PlaceholderScreen("贴纸工坊 F22-F24（R2 工坊切片）") }
        composable(Routes.SETTINGS) { PlaceholderScreen("设置（R2 搜索口味切片：口味管理 F03）") }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "[$label]",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
