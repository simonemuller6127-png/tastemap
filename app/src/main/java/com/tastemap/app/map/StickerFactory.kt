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

    /**
     * R3 二次反馈：弃用三档跳变，改为**随 zoom 连续插值**（业界通行做法）。
     * zoom 21 → 28dp，zoom 11 → 76dp，线性；越界截断。
     */
    fun sizeDpForZoom(zoom: Double): Float {
        val t = ((21.0 - zoom) / 10.0).coerceIn(0.0, 1.0)
        return (28f + 48f * t.toFloat())
    }

    /** 低倍视野（zoom < 13.5）最多同屏贴纸数，其余淡出（F18b：避免贴纸糊满屏） */
    fun visibleLimitForZoom(zoom: Double): Int = if (zoom < 13.5) 10 else Int.MAX_VALUE
}

/**
 * 贴纸合成：白边 + 口味色描边 + 圆角方形照片 + 种子旋转 + 投影。
 * 无照片店铺给统一的手绘占位贴纸（D19 补图引导的地图端形态）。
 *
 * R3 二次反馈（连续缩放）：每店只渲染一张 160px 母版 [baseBitmap]，
 * 地图缩放时按目标尺寸缩放母版生成 descriptor（缩放位图开销极小），
 * 配合节流的相机回调实现贴纸随地图连续呼吸缩放。
 */
class StickerFactory(private val density: Float) {

    private val baseCache = LruCache<Long, Bitmap>(48)
    private val descriptorCache = LruCache<String, BitmapDescriptor>(96)

    /** 母版：固定 160px 渲染一次（含白边/描边/旋转/投影） */
    fun baseBitmap(shopId: Long, colorHex: String, source: Bitmap?): Bitmap {
        baseCache.get(shopId)?.let { return it }
        val sizePx = BASE_PX
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rotation = StickerMath.rotationDegreesFor(shopId)

        canvas.save()
        canvas.rotate(rotation.toFloat(), sizePx / 2f, sizePx / 2f)

        val inset = sizePx * 0.06f
        val outer = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val radius = sizePx * 0.16f

        // 投影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 59, 52, 43) }
        canvas.drawRoundRect(
            RectF(outer.left + sizePx * 0.02f, outer.top + sizePx * 0.03f, outer.right + sizePx * 0.02f, outer.bottom + sizePx * 0.03f),
            radius, radius, shadowPaint,
        )

        // 白边纸片
        canvas.drawRoundRect(outer, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 249, 241) })

        // 照片区
        val photoInset = sizePx * 0.13f
        val photoRect = RectF(outer.left + photoInset, outer.top + photoInset, outer.right - photoInset, outer.bottom - photoInset)
        if (source != null && !source.isRecycled) {
            val clip = Path().apply { addRoundRect(photoRect, radius * 0.7f, radius * 0.7f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            val scale = maxOf(photoRect.width() / source.width, photoRect.height() / source.height)
            val dw = source.width * scale
            val dh = source.height * scale
            val dx = photoRect.left + (photoRect.width() - dw) / 2f
            val dy = photoRect.top + (photoRect.height() - dh) / 2f
            canvas.drawBitmap(source, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            // 占位：口味色圆点 + 细虚线框（补图引导）
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

        // 口味色描边
        canvas.drawRoundRect(
            outer, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.GRAY)
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.035f
            },
        )
        canvas.restore()
        baseCache.put(shopId, bmp)
        return bmp
    }

    /** 目标尺寸的 descriptor：母版等比缩放（key=shopId/px，避免每帧重建） */
    fun descriptorAt(shopId: Long, colorHex: String, source: Bitmap?, targetPx: Int): BitmapDescriptor {
        val px = targetPx.coerceAtLeast(12)
        val key = "$shopId/$px"
        descriptorCache.get(key)?.let { return it }
        val base = baseBitmap(shopId, colorHex, source)
        val scaled = if (base.width == px) base else Bitmap.createScaledBitmap(base, px, px, true)
        return BitmapDescriptorFactory.fromBitmap(scaled).also { descriptorCache.put(key, it) }
    }

    companion object {
        const val BASE_PX = 160
    }
}
