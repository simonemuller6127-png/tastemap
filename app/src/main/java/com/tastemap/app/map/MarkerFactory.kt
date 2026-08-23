package com.tastemap.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory

/**
 * 口味着色图钉（F01/F03）。
 * M0 简版：圆头 + 墨色描边 + 尾巴 + 高光点，按「颜色+尺寸」缓存 BitmapDescriptor。
 * M3 按 SPEC D12 升级为分档位图（36/48/56/64-80dp 地标贴纸）。
 */
object MarkerFactory {

    private const val STROKE_COLOR = 0xFF4A4238.toInt()
    private val cache = LruCache<String, BitmapDescriptor>(24)

    fun parseColor(hex: String): Int =
        runCatching { Color.parseColor(hex) }.getOrDefault(Color.GRAY)

    fun descriptor(colorHex: String, sizeDp: Int, density: Float): BitmapDescriptor {
        val key = "$colorHex/$sizeDp"
        cache.get(key)?.let { return it }

        val w = (sizeDp * density).toInt().coerceAtLeast(16)
        val h = w * 4 / 3
        val cx = w / 2f
        val cy = w * 0.40f
        val r = w * 0.34f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(colorHex) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STROKE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = w * 0.05f
        }
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
        }

        val tail = Path().apply {
            moveTo(cx - r * 0.55f, cy + r * 0.70f)
            lineTo(cx, h - stroke.strokeWidth)
            lineTo(cx + r * 0.55f, cy + r * 0.70f)
            close()
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawPath(tail, fill)
            drawPath(tail, stroke)
            drawCircle(cx, cy, r, fill)
            drawCircle(cx, cy, r, stroke)
            drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.14f, highlight)
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap).also { cache.put(key, it) }
    }
}
