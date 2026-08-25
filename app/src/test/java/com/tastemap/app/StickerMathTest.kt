package com.tastemap.app

import com.tastemap.app.map.StickerMath
import org.junit.Assert.assertEquals
import org.junit.Test

/** D12/D17 贴纸分档与种子旋转的纯逻辑回归 */
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
    fun `zoom tiers follow counter intuitive sizing`() {
        assertEquals(0, StickerMath.tierForZoom(17.5))
        assertEquals(0, StickerMath.tierForZoom(16.0))
        assertEquals(1, StickerMath.tierForZoom(14.0))
        assertEquals(1, StickerMath.tierForZoom(15.9))
        assertEquals(2, StickerMath.tierForZoom(11.0))
        assertEquals(2, StickerMath.tierForZoom(3.2))
    }

    @Test
    fun `tier pixel sizes are 40 72 128`() {
        assertEquals(40, StickerMath.tierPixelSize(0))
        assertEquals(72, StickerMath.tierPixelSize(1))
        assertEquals(128, StickerMath.tierPixelSize(2))
        assertEquals(128, StickerMath.tierPixelSize(99)) // 未知档兜底最大
    }

    @Test
    fun `low tier limits visible stickers`() {
        assertEquals(Int.MAX_VALUE, StickerMath.visibleLimitForTier(0))
        assertEquals(Int.MAX_VALUE, StickerMath.visibleLimitForTier(1))
        assertEquals(10, StickerMath.visibleLimitForTier(2))
    }
}
