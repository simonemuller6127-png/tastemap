package com.tastemap.app

import com.tastemap.app.map.StickerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D17 连续缩放 + 种子旋转的纯逻辑回归 */
class StickerMathTest {

    @Test
    fun `rotation is stable and bounded to plus minus 3 degrees`() {
        assertEquals(-3, StickerMath.rotationDegreesFor(0L))
        assertEquals(StickerMath.rotationDegreesFor(42L), StickerMath.rotationDegreesFor(42L))
        (-100L..100L).forEach { seed ->
            val r = StickerMath.rotationDegreesFor(seed)
            assert(r in -3..3) { "seed=$seed rotation=$r 超界" }
        }
    }

    @Test
    fun `size interpolates continuously between 28dp and 76dp`() {
        assertEquals(28f, StickerMath.sizeDpForZoom(21.0))
        assertEquals(76f, StickerMath.sizeDpForZoom(11.0))
        val mid = StickerMath.sizeDpForZoom(16.0)
        assertTrue("中段应在两端之间：$mid", mid in 28f..76f && mid != 28f && mid != 76f)
        // 越界截断
        assertEquals(28f, StickerMath.sizeDpForZoom(25.0))
        assertEquals(76f, StickerMath.sizeDpForZoom(3.0))
    }

    @Test
    fun `low zoom limits visible stickers`() {
        assertEquals(10, StickerMath.visibleLimitForZoom(10.0))
        assertEquals(10, StickerMath.visibleLimitForZoom(13.4))
        assertEquals(Int.MAX_VALUE, StickerMath.visibleLimitForZoom(13.5))
        assertEquals(Int.MAX_VALUE, StickerMath.visibleLimitForZoom(18.0))
    }
}
