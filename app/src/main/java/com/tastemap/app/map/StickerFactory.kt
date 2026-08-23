package com.tastemap.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory

/**
 * 照片贴纸渲染引擎（D17/D12，R0 地基）。
 * 纯计算逻辑抽在 [StickerMath]（可单测）；位图合成是薄层，R2 工坊切片只调用不重写。
 */
object StickerMath {
    /** 每店固定 ±3° 随机旋转（seed=shopId），重绘稳定不闪变；负 seed 用规范取模保证仍在 -3..3 */
    fun rotationDegreesFor(seed: Long): Int {
        val m = ((seed % 7L) + 7L) % 7L // 0..6
        return (m - 3L).toInt()
    }

    /** D12 分档：zoom 越小贴纸越大（反直觉缩放 F18b） */
    fun tierForZoom(zoom: Double): Int = when {
        zoom >= 16.0 -> 0 // 常规贴纸
        zoom >= 14.0 -> 1 // 中档
        else -> 2         // 大贴纸（视口内评分前列，每屏 ≤12，由调用方筛选）
    }

    fun tierPixelSize(tier: Int): Int = when (tier) {
        0 -> 48
        1 -> 96
        else -> 192
    }
}

/**
 * 贴纸合成：白边 + 口味色描边 + 圆角方形照片 + 种子旋转 + 投影。
 * 无照片店铺给统一的手绘占位贴纸（D19 补图引导的地图端形态）。
 * 位图按 "shopId/tier" 缓存（D12：同档复用，防内存抖动）。
 */
class StickerFactory(private val density: Float) {

    private val cache = LruCache<String, BitmapDescriptor>(48)

    fun photoSticker(shopId: Long, tier: Int, colorHex: String, source: Bitmap?): BitmapDescriptor {
        val key = "$shopId/$tier/${source != null}"
        cache.get(key)?.let { return it }

        val sizePx = (StickerMath.tierPixelSize(tier) * density).toInt().coerceAtLeast(32)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rotation = StickerMath.rotationDegreesFor(shopId)

        canvas.save()
        canvas.rotate(rotation.toFloat(), sizePx / 2f, sizePx / 2f)

        val inset = sizePx * 0.06f
        val outer = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val radius = sizePx * 0.16f

        // 投影（先画一个错位的深色圆角块）
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 59, 52, 43) }
        canvas.drawRoundRect(RectF(outer.left + sizePx * 0.02f, outer.top + sizePx * 0.03f, outer.right + sizePx * 0.02f, outer.bottom + sizePx * 0.03f), radius, radius, shadowPaint)

        // 白边纸片
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 249, 241) }
        canvas.drawRoundRect(outer, radius, radius, paperPaint)

        // 照片区（内缩出白边）
        val photoInset = sizePx * 0.13f
        val photoRect = RectF(outer.left + photoInset, outer.top + photoInset, outer.right - photoInset, outer.bottom - photoInset)
        if (source != null && !source.isRecycled) {
            val clip = Path().apply { addRoundRect(photoRect, radius * 0.7f, radius * 0.7f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            // 中心裁剪铺满
            val scale = maxOf(photoRect.width() / source.width, photoRect.height() / source.height)
            val dw = source.width * scale
            val dh = source.height * scale
            val dx = photoRect.left + (photoRect.width() - dw) / 2f
            val dy = photoRect.top + (photoRect.height() - dh) / 2f
            canvas.drawBitmap(source, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            // 占位贴纸：口味色圆点 + 细虚线框，提示"去补图"
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.GRAY) }
            canvas.drawCircle(photoRect.centerX(), photoRect.centerY(), photoRect.width() * 0.28f, dot)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 74, 66, 56)
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.012f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(sizePx * 0.04f, sizePx * 0.03f), 0f)
            }
            canvas.drawRoundRect(photoRect, radius * 0.7f, radius * 0.7f, stroke)
        }

        // 口味色描边（贴纸的主视觉身份，F18：口味着色载体）
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.GRAY)
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.035f
        }
        canvas.drawRoundRect(outer, radius, radius, border)
        canvas.restore()

        return BitmapDescriptorFactory.fromBitmap(bmp).also { cache.put(key, it) }
    }
}
