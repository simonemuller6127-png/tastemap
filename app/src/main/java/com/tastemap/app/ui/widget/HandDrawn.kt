package com.tastemap.app.ui.widget

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tastemap.app.ui.theme.Palette
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 手绘设计系统核心组件（D11）。
 *
 * 稳定性原则：所有随机抖动都吃一个显式 seed（通常传控件稳定 id），
 * 同一 seed 每次渲染产生同一条折线——不闪变，这是 Rough.js 思路的移植关键。
 */

/** 纸质纹理背景：程序化生成 128dp 可平铺噪点，比贴图分辨率无关且零资源体积 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier) {
    val tile = remember { generatePaperTile() }
    Canvas(modifier) {
        val tileSize = 128.dp.toPx()
        val cols = (size.width / tileSize).roundToInt() + 1
        val rows = (size.height / tileSize).roundToInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawImage(tile, dstOffset = Offset(c * tileSize, r * tileSize).let { IntOffsetCompat(it) }, dstSize = IntSizeCompat(tileSize))
            }
        }
    }
}

// IntOffset/IntSize 兼容导入（集中放文件底部避免顶部噪音）
private fun IntOffsetCompat(o: Offset) = androidx.compose.ui.unit.IntOffset(o.x.roundToInt(), o.y.roundToInt())
private fun IntSizeCompat(px: Float) = androidx.compose.ui.unit.IntSize(px.roundToInt(), px.roundToInt())

private fun generatePaperTile(): ImageBitmap {
    val px = 128
    val bmp = androidx.compose.ui.graphics.ImageBitmap(px, px)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    val rnd = Random(42) // 固定种子：纹理全局一致
    val dot = Paint().apply { color = Color(0x0A8A7F6A) } // 极淡的纤维点
    repeat(140) {
        canvas.drawCircle(
            Offset(rnd.nextFloat() * px, rnd.nextFloat() * px),
            rnd.nextFloat() * 1.4f + 0.3f,
            dot,
        )
    }
    return bmp
}

/**
 * 地图纸面叠加层（F18）。真机实测（P9/EMUI8，3dmap 10.0.600）两个结论都写进实现：
 * 1. BlendMode.Multiply 的 drawImage 在部分设备渲染管线里会退化为 source-over——
 *    所以这里不做"米白不透明底"，改为把暖纸色调和纤维点以**低透明度直接烘焙进整屏位图**，
 *    source-over 一把画完，效果跨设备稳定，且每帧一次 drawImage（原先逐瓦片上百次）。
 * 2. 高德本地样式 JSON（setStyleData/setStyleDataPath，包裹与裸数组两格式）在该版本实测
 *    均不生效，底图换色/隐藏元素暂时只能靠这层叠加+控制台加密样式包（后续路线）。
 *
 * @param widthPx 视口宽（px），随窗口变化重建
 * @param heightPx 视口高（px）
 */
fun mapPaperOverlayBitmap(widthPx: Int, heightPx: Int): ImageBitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bmp = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    // 暖纸色罩：15% 让默认地图向纸色偏移但保持可读（50% 会把对比洗掉）
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = Color(0x26F7F2E7) })
    val rnd = Random(7) // 固定种子：纹理全局一致不闪变
    val fiber = Paint().apply { color = Color(0x14807560) }      // 深纤维点
    val fiber2 = Paint().apply { color = Color(0x0FFFFFFF) }     // 亮纤维点
    val dots = (w * h / 4500).coerceIn(90, 400)
    repeat(dots) {
        canvas.drawCircle(
            Offset(rnd.nextFloat() * w, rnd.nextFloat() * h),
            rnd.nextFloat() * 1.3f + 0.4f,
            if (rnd.nextBoolean()) fiber else fiber2,
        )
    }
    // 极淡的横向抄纸纹：几条 1px 水平细线，模拟手工纸帘痕
    val line = Paint().apply { color = Color(0x0A8A7F6A) }
    var y = rnd.nextInt(9)
    while (y < h) {
        canvas.drawRect(0f, y.toFloat(), w.toFloat(), (y + 1).toFloat(), line)
        y += 6 + rnd.nextInt(5)
    }
    return bmp
}

/**
 * 不规则手绘描边：沿圆角矩形周长采样，每个点做有界随机偏移，连成一条"抖"的闭合线。
 * @param seed 稳定种子（控件 id），保证重绘不闪变
 */
fun DrawScope.drawJitteredRoundRect(
    seed: Long,
    color: Color = Palette.SketchStroke,
    strokeWidth: Dp = 1.6.dp,
    cornerRadius: Dp = 10.dp,
    jitter: Dp = 1.5.dp,
    inset: Dp = 3.dp,
) {
    val rnd = Random(seed)
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = cornerRadius.toPx()
    val i = inset.toPx()
    val j = jitter.toPx()

    // 周长采样：每条边按长度取点，圆角处两点近似（手绘感本就不需要精确弧）
    val left = i; val top = i; val right = w - i; val bottom = h - i
    val step = 14.dp.toPx()
    val points = mutableListOf<Offset>()
    fun edge(x1: Float, y1: Float, x2: Float, y2: Float) {
        val len = kotlin.math.hypot(x2 - x1, y2 - y1)
        val n = (len / step).roundToInt().coerceAtLeast(2)
        for (k in 0 until n) {
            val t = k.toFloat() / n
            points += Offset(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)
        }
    }
    edge(left + r, top, right - r, top)
    edge(right, top + r, right, bottom - r)
    edge(right - r, bottom, left + r, bottom)
    edge(left, bottom - r, left, top + r)

    val path = Path()
    points.forEachIndexed { idx, p ->
        val jp = Offset(p.x + (rnd.nextFloat() - 0.5f) * 2 * j, p.y + (rnd.nextFloat() - 0.5f) * 2 * j)
        if (idx == 0) path.moveTo(jp.x, jp.y) else path.lineTo(jp.x, jp.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(strokeWidth.toPx()))
}
