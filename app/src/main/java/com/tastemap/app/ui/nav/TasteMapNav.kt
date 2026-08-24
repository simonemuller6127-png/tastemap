package com.tastemap.app.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tastemap.app.ui.MapHomeScreen
import com.tastemap.app.ui.card.CardComposerScreen
import com.tastemap.app.ui.record.RecordEditScreen
import com.tastemap.app.ui.review.ReviewFeedScreen
import com.tastemap.app.ui.schedule.ScheduleScreen
import com.tastemap.app.ui.search.SearchScreen
import com.tastemap.app.ui.settings.SettingsScreen
import com.tastemap.app.ui.shop.ShopDetailScreen
import com.tastemap.app.ui.wishlist.WishlistScreen

/**
 * 导航骨架（R0 定稿 + R2 扩展）：底部栏五个一级页 + 详情/编辑二级页。
 * 路由常量是并行切片的共享契约（SPEC §6），只加不改。
 */
object Routes {
    const val MAP_HOME = "map_home"                                  // F01/F18 地图主页
    const val SEARCH = "search"                                      // F05 搜索筛选（R2-③ 实现）
    const val REVIEW_FEED = "review_feed"                            // F07 回顾卡片流
    const val SCHEDULE = "schedule"                                  // F08 美食日程
    const val WISHLIST = "wishlist"                                  // F09 想吃清单
    const val RECORD_EDIT = "record_edit?lat={lat}&lng={lng}&name={name}&wid={wid}" // F02
    const val SHOP_DETAIL = "shop_detail/{shopId}"                   // F04
    const val CARD_COMPOSER = "card_composer/{shopId}"              // F13/F14（R2-④）
    const val STICKER_STUDIO = "sticker_studio"                      // F22-F24（R2-⑤）
    const val SETTINGS = "settings"                                  // 设置（口味管理 F03 入口）

    fun recordEdit(lat: Double, lng: Double, name: String = "", wishlistId: Long = 0L) =
        "record_edit?lat=$lat&lng=$lng&name=${Uri.encode(name)}&wid=$wishlistId"

    fun shopDetail(shopId: Long) = "shop_detail/$shopId"
    fun cardComposer(shopId: Long) = "card_composer/$shopId"
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

private val tabs = listOf(
    Tab(Routes.MAP_HOME, "地图") { Icon(Icons.Default.Map, contentDescription = null) },
    Tab(Routes.SEARCH, "搜索") { Icon(Icons.Default.Search, contentDescription = null) },
    Tab(Routes.REVIEW_FEED, "回顾") { Icon(Icons.Default.Style, contentDescription = null) },
    Tab(Routes.SCHEDULE, "日程") { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
    Tab(Routes.WISHLIST, "想吃") { Icon(Icons.Default.FavoriteBorder, contentDescription = null) },
)

@Composable
fun TasteMapNav(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.MAP_HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP_HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.MAP_HOME) {
                MapHomeScreen(
                    onCreateRecord = { lat, lng -> navController.navigate(Routes.recordEdit(lat, lng)) },
                    onOpenShop = { shopId -> navController.navigate(Routes.shopDetail(shopId)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenShop = { shopId -> navController.navigate(Routes.shopDetail(shopId)) })
            }
            composable(Routes.REVIEW_FEED) {
                ReviewFeedScreen(onOpenShop = { shopId -> navController.navigate(Routes.shopDetail(shopId)) })
            }
            composable(Routes.SCHEDULE) { ScheduleScreen() }
            composable(Routes.WISHLIST) {
                WishlistScreen(
                    onCheckIn = { item ->
                        navController.navigate(
                            Routes.recordEdit(
                                lat = item.latitude ?: 30.59276,
                                lng = item.longitude ?: 114.30525,
                                name = item.text,
                                wishlistId = item.id,
                            ),
                        )
                    },
                )
            }
            composable(
                Routes.RECORD_EDIT,
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lng") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                    navArgument("wid") { type = NavType.StringType; defaultValue = "0" },
                ),
            ) {
                RecordEditScreen(onDone = { navController.popBackStack() })
            }
            composable(
                Routes.SHOP_DETAIL,
                arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
            ) {
                ShopDetailScreen(
                    onBack = { navController.popBackStack() },
                    onComposeCard = { shopId -> navController.navigate(Routes.cardComposer(shopId)) },
                )
            }
            composable(
                Routes.CARD_COMPOSER,
                arguments = listOf(navArgument("shopId") { type = NavType.StringType }),
            ) {
                CardComposerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STICKER_STUDIO) { PlaceholderScreen("贴纸工坊 F22-F24（R2-⑤ 工坊切片）") }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
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
