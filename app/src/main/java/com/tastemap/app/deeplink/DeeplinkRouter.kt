package com.tastemap.app.deeplink

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * F10 跳转路由（D7）：高德/百度/腾讯导航 deeplink 逐个尝试，全失败复制店名坐标兜底；
 * 美团外卖只做"打开 App + 复制店名"（poiId 映射 M2/R3 实测后再接，预案 5）。
 * 不接任何收费 OpenAPI（硬约束 2）。
 */
object DeeplinkRouter {

    private data class Target(val uri: String) {
        fun toIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun navigationTargets(name: String, lat: Double, lng: Double) = listOf(
        // 高德（本项目坐标系即 GCJ02）
        Target("amapuri://route/plan/?sourceApplication=tastemap&dlat=$lat&dlon=$lng&dname=${Uri.encode(name)}&dev=0&t=0"),
        Target("androidamap://route?sourceApplication=tastemap&dlat=$lat&dlon=$lng&dname=${Uri.encode(name)}&dev=0&t=0"),
        // 百度
        Target("baidumap://map/direction?destination=name:${Uri.encode(name)}|latlng:$lat,$lng&coord_type=gcj02&mode=driving"),
        // 腾讯
        Target("qqmap://map/routeplan?to=${Uri.encode(name)}&tocoord=$lat,$lng&policy=1"),
    )

    /** 导航：任一地图 App 成功拉起即 true；全失败复制"店名 (lat,lng)"并返回 false */
    fun navigateToShop(context: Context, shopName: String, lat: Double, lng: Double): Boolean {
        for (target in navigationTargets(shopName, lat, lng)) {
            if (runCatching { context.startActivity(target.toIntent()) }.isSuccess) return true
        }
        copyToClipboard(context, "$shopName ($lat, $lng)")
        return false
    }

    /**
     * 美团外卖（F10）：poiId 未打通前 = 打开美团 + 复制店名（兜底策略本身就是产品预期）。
     * @return true 表示美团已拉起
     */
    fun openMeituanWithShopName(context: Context, shopName: String): Boolean {
        copyToClipboard(context, shopName)
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tastemap", text))
    }
}
