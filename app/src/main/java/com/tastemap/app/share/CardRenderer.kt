package com.tastemap.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.tastemap.app.R
import java.io.File
import kotlin.random.Random

/**
 * F13 美食卡片渲染（D6）：纯 android.graphics 直绘，1080×1620 竖版。
 * 布局：纸底 + 照片区（无照片用口味色块）+ 店名/口味/星评 + 右下角二维码 + 品牌水印。
 * 字体用霞鹜文楷（D11 手写体贯穿分享物）。
 */
object CardRenderer {

    private const val W = 1080
    private const val H = 1620

    data class Input(
        val shopName: String,
        val tasteNames: List<String>,
        val tasteColorHex: String,      // 主导口味色
        val rating: Int,                // 0-5，0 不画
        val note: String,               // 一句话（可空）
        val photoFile: File?,           // 无则用色块
        val qrContent: String,
    )

    fun render(context: Context, input: Input): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val paper = Color.parseColor("#F7F2E7")
        canvas.drawColor(paper)

        val ink = Color.parseColor("#3B342B")
        val inkSoft = Color.parseColor("#6B6357")
        val tasteColor = runCatching { Color.parseColor(input.tasteColorHex) }.getOrDefault(Color.parseColor("#4A4238"))
        val font = ResourcesCompat.getFont(context, R.font.lxgw_wenkai_regular) ?: Typeface.DEFAULT

        // 纸纹噪点（固定种子，全局一致）
        val dot = Paint().apply { color = Color.argb(10, 138, 127, 106) }
        val rnd = Random(42)
        repeat(700) { canvas.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, rnd.nextFloat() * 2f + 0.5f, dot) }

        // 照片区：上 55%，内边距 48
        val photoRect = RectF(48f, 48f, W - 48f, H * 0.55f)
        val photo = input.photoFile?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        canvas.save()
        canvas.clipRect(photoRect)
        if (photo != null) {
            val scale = maxOf(photoRect.width() / photo.width, photoRect.height() / photo.height)
            val dw = photo.width * scale
            val dh = photo.height * scale
            canvas.drawBitmap(
                photo, null,
                RectF(photoRect.left + (photoRect.width() - dw) / 2, photoRect.top + (photoRect.height() - dh) / 2, 0f, 0f).let {
                    RectF(it.left, it.top, it.left + dw, it.top + dh)
                },
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        } else {
            canvas.drawColor(tasteColor)
            // 无照片：中央画大图钉剪影
            val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
            val cx = photoRect.centerX()
            val cy = photoRect.centerY() - 60
            canvas.drawCircle(cx, cy, 140f, pin)
            val tail = android.graphics.Path().apply {
                moveTo(cx - 70f, cy + 100f); lineTo(cx, cy + 260f); lineTo(cx + 70f, cy + 100f); close()
            }
            canvas.drawPath(tail, pin)
        }
        canvas.restore()
        // 照片描边（口味色）
        canvas.drawRoundRect(photoRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tasteColor; style = Paint.Style.STROKE; strokeWidth = 10f
        })

        var y = photoRect.bottom + 96f

        // 店名
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = font; textSize = 72f; letterSpacing = 0.02f
        }
        canvas.drawText(ellipsize(input.shopName, namePaint, W - 96f), 48f, y, namePaint)
        y += 88f

        // 口味 · 星评
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSoft; typeface = font; textSize = 44f
        }
        val meta = buildString {
            if (input.tasteNames.isNotEmpty()) append(input.tasteNames.joinToString(" · "))
            if (input.rating > 0) {
                if (isNotEmpty()) append("   ")
                append("★".repeat(input.rating))
            }
        }
        if (meta.isNotEmpty()) {
            canvas.drawText(ellipsize(meta, metaPaint, W - 96f), 48f, y, metaPaint)
            y += 70f
        }

        // 一句话评价
        if (input.note.isNotBlank()) {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSoft; typeface = font; textSize = 40f
            }
            canvas.drawText("“${ellipsize(input.note, notePaint, W - 96f - 360f)}”", 48f, y, notePaint)
        }

        // 二维码（右下）
        val qr = QrCodec.encode(input.qrContent, 300)
        canvas.drawBitmap(qr, null, RectF(W - 48f - 280f, H - 48f - 280f, W - 48f, H - 48f), Paint(Paint.ANTI_ALIAS_FLAG))

        // 品牌水印（左下）
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = font; textSize = 48f
        }
        canvas.drawText("味觉地图", 48f, H - 140f, brand)
        val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSoft; typeface = font; textSize = 30f
        }
        canvas.drawText("扫码把这家店收进你的地图", 48f, H - 90f, hint)

        return bmp
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun Rect.width() = right - left
    private fun RectF.width() = right - left
    private fun RectF.height() = bottom - top
}
