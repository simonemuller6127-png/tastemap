package com.tastemap.app.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.tastemap.app.R
import kotlin.random.Random

/**
 * 贴纸工坊 v1（F22-F24，D13 无 GMS 路线）：
 * 风格化用 ColorMatrix（端侧、零依赖、效果可预期）；抠图 ML Kit 在无 GMS 设备不可用，
 * v1 不抠主体、整图风格化（预案 8 降级链的第二链）；白边+种子旋转+手写标签合成透明底 PNG（F24）。
 * GPUImage/TFLite 若 R3 真机评估效果不足再引入（SPEC D13 登记）。
 */
object StickerFilters {

    enum class Style(val label: String) { ORIGIN("原色"), WATERCOLOR("淡彩水彩"), SKETCH("素描"), CRAYON("蜡笔") }

    fun apply(source: Bitmap, style: Style): Bitmap {
        if (style == Style.ORIGIN) return source.copy(Bitmap.Config.ARGB_8888, true)
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(
            when (style) {
                Style.WATERCOLOR -> ColorMatrix().apply {
                    // 降饱和 + 提亮 + 轻微偏暖：水彩淡彩感
                    setSaturation(0.55f)
                    setScale(1.08f, 1.06f, 1.02f, 1f)
                }
                Style.SKETCH -> ColorMatrix().apply {
                    // 去色 + 拉对比：铅笔稿的黑白灰
                    setSaturation(0f)
                    setScale(1.35f, 1.35f, 1.35f, 1f)
                }
                Style.CRAYON -> ColorMatrix().apply {
                    // 高饱和 + 压对比：蜡笔的浓烈粉感
                    setSaturation(1.6f)
                    setScale(0.96f, 0.92f, 0.94f, 1f)
                }
                Style.ORIGIN -> ColorMatrix()
            },
        )
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }
}

/** F24 贴纸合成：白边 + 种子随机旋转 + 手写标签，透明底 PNG */
object StickerComposer {

    fun compose(context: Context, styled: Bitmap, label: String, seed: Long): Bitmap {
        val pad = 28f          // 白边宽
        val labelH = if (label.isBlank()) 0f else 120f
        val inner = maxOf(styled.width, styled.height).coerceAtMost(1024)
        val scale = inner.toFloat() / maxOf(styled.width, styled.height)
        val contentW = (styled.width * scale).toInt()
        val contentH = (styled.height * scale).toInt()
        val w = (contentW + pad * 2).toInt()
        val h = (contentH + pad * 2 + labelH).toInt()

        val bmp = Bitmap.createBitmap((w * 1.1f).toInt(), (h * 1.1f).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.rotate(
            ((Random(seed).nextInt(7) - 3)).toFloat(),
            bmp.width / 2f,
            bmp.height / 2f,
        )

        // 白边纸片（圆角）
        val outer = RectF(
            (bmp.width - w) / 2f, (bmp.height - h) / 2f,
            (bmp.width + w) / 2f, (bmp.height + h) / 2f,
        )
        canvas.drawRoundRect(outer, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 249, 241) })

        // 照片
        val dst = RectF(outer.left + pad, outer.top + pad, outer.right - pad, outer.top + pad + contentH)
        canvas.drawBitmap(styled, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        // 手写标签（霞鹜文楷，D11）
        if (label.isNotBlank()) {
            val font = ResourcesCompat.getFont(context, R.font.lxgw_wenkai_regular) ?: Typeface.DEFAULT
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3B342B"); typeface = font; textSize = 64f
            }
            canvas.drawText(label, outer.left + pad, outer.bottom - pad / 2, textPaint)
        }
        return bmp
    }
}
