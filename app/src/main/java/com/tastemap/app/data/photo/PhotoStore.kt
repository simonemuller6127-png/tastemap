package com.tastemap.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** MealRecord.photos 列的编解码（schema v2 约定：JSON 数组字符串，相对 photos/ 的路径） */
object PhotoJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun encode(paths: List<String>): String = json.encodeToString(serializer, paths)

    fun decode(raw: String): List<String> = runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
}

/**
 * 照片落地（D4/D5，R2 记录流）：
 * 系统相册选图 → 压缩存储到 App 私有目录 photos/（最长边 1600px，质量 85），
 * 原文件不动；EXIF 判定"原图/疑似修图"（MVP 仅角标提示，D5）。
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class StoredPhoto(val relativePath: String, val isOriginal: Boolean)

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun fileOf(relativePath: String): File = File(context.filesDir, relativePath)

    suspend fun store(uri: Uri): StoredPhoto = withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")
        val raw = source.use { it.readBytes() }

        // EXIF 必须读原始字节（压缩后再读会丢）
        val isOriginal = looksLikeOriginal(raw)

        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("图片解码失败")
        val maxSide = 1600
        val scale = maxOf(decoded.width, decoded.height).let { if (it > maxSide) maxSide.toFloat() / it else 1f }
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded

        val name = "${UUID.randomUUID()}.jpg"
        val out = File(dir, name)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()

        StoredPhoto(relativePath = "photos/$name", isOriginal = isOriginal)
    }

    suspend fun delete(relativePath: String): Unit = withContext(Dispatchers.IO) {
        fileOf(relativePath).delete()
        Unit
    }

    companion object {
        /**
         * D5 原图判定：有拍摄时间 + 有相机/设备信息，且无编辑软件痕迹（Software 标签）。
         * 缺 EXIF（截图/下载图/聊天转存）也判为非原图——提示而非阻断（PRD 原图校验说明）。
         */
        fun looksLikeOriginal(jpegBytes: ByteArray): Boolean = try {
            val exif = ExifInterface(jpegBytes.inputStream())
            val hasTime = exif.dateTimeOriginal != null || exif.dateTime != null
            val hasCamera = !exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank()
            val hasEditSoftware = !exif.getAttribute(ExifInterface.TAG_SOFTWARE).isNullOrBlank()
            hasTime && hasCamera && !hasEditSoftware
        } catch (_: Exception) {
            false
        }
    }
}
