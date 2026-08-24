package com.tastemap.app

import com.tastemap.app.share.CardShareCodec
import com.tastemap.app.share.ShareCardPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F13/F14 卡片载荷编解码回归 */
class CardShareCodecTest {

    @Test
    fun `payload round trips and stays under 300 bytes`() {
        val payload = ShareCardPayload(
            name = "咸蛋黄大排档",
            lat = 30.61881,
            lng = 114.14337,
            tastes = listOf("咸", "鲜甜"),
            rating = 5,
            note = "流沙很香",
        )
        val text = CardShareCodec.encode(payload)
        assert(text.length < 300) { "二维码载荷超预算：${text.length}B" }
        assertEquals(payload, CardShareCodec.decode(text))
    }

    @Test
    fun `decode tolerates whitespace and rejects foreign payloads`() {
        val text = CardShareCodec.encode(ShareCardPayload(name = "老王甜品", lat = 1.0, lng = 2.0))
        assertEquals("老王甜品", CardShareCodec.decode("  $text\n")?.name)
        assertNull(CardShareCodec.decode("""{"app":"other","name":"x","lat":0,"lng":0}"""))
        assertNull(CardShareCodec.decode("not json at all"))
    }
}
