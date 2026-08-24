package com.tastemap.app.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * F13/F14 卡片二维码载荷（预案 3）：只装店名+坐标+口味+评分，<300B；
 * 照片不进码——导入方拿到"店卡"而非对方照片，规避隐私与大文件。
 */
@Serializable
data class ShareCardPayload(
    val app: String = "tastemap",
    val v: Int = 1,
    val name: String,
    val lat: Double,
    val lng: Double,
    val tastes: List<String> = emptyList(),
    val rating: Int = 0,
    val note: String = "",
)

object CardShareCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(payload: ShareCardPayload): String = json.encodeToString(ShareCardPayload.serializer(), payload)

    fun decode(raw: String): ShareCardPayload? = runCatching {
        json.decodeFromString(ShareCardPayload.serializer(), raw.trim())
    }.getOrNull()?.takeIf { it.app == "tastemap" && it.name.isNotBlank() }
}

/** ZXing 二维码生成/识别（D6，纯本地） */
object QrCodec {

    fun encode(content: String, sizePx: Int = 480): Bitmap {
        val hints = mapOf(
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            com.google.zxing.EncodeHintType.MARGIN to 1,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565) // 二维码双色足够
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    /** 从相册图片识别（F14 v1：相册识别替代相机扫码，零相机权限） */
    fun decodeFromBitmap(bmp: Bitmap): String? = runCatching {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        reader.decodeWithState(bitmap).text
    }.getOrNull()
}
