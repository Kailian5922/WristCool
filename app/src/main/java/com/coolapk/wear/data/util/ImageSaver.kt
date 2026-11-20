package com.coolapk.wear.data.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 手表相册图片保存工具。
 * 复用全局 OkHttpClient 与 User-Agent，自动识别真实图片格式（GIF/PNG/WebP/JPG），
 * 并在写入异常时自动清理 MediaStore 中残余的 IS_PENDING 记录。
 */
class ImageSaver(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val userAgent: String
) {
    private val appContext = context.applicationContext
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveImageToGalleryAsync(imageUrl: String) {
        saveScope.launch {
            saveImageToGallery(imageUrl)
        }
    }

    suspend fun saveImageToGallery(imageUrl: String) = withContext(Dispatchers.IO) {
        var insertedUri: Uri? = null
        try {
            if (imageUrl.isBlank()) {
                showToast("图片地址无效")
                return@withContext
            }

            showToast("正在保存图片...")

            val req = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", userAgent)
                .build()

            val (bytes, contentType) = okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    showToast("下载失败: HTTP ${response.code}")
                    return@withContext
                }
                val data = response.body?.bytes()
                val mime = response.header("Content-Type").orEmpty()
                data to mime
            }

            if (bytes == null || bytes.isEmpty()) {
                showToast("图片数据为空")
                return@withContext
            }

            val format = detectImageFormat(imageUrl, contentType, bytes)
            val filename = "Coolapk_${System.currentTimeMillis()}.${format.extension}"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Coolapk")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                showToast("无法创建相册文件")
                return@withContext
            }
            insertedUri = uri

            val outputStream = resolver.openOutputStream(uri)
            if (outputStream == null) {
                resolver.delete(uri, null, null)
                showToast("无法写入相册文件")
                return@withContext
            }
            outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            insertedUri = null

            vibrateSaved()
            showToast("图片已保存至手表相册")
        } catch (e: Exception) {
            insertedUri?.let { uri ->
                runCatching { appContext.contentResolver.delete(uri, null, null) }
            }
            showToast("保存失败: ${e.message ?: "未知错误"}")
        }
    }

    private suspend fun showToast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun vibrateSaved() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    internal data class ImageFormat(val extension: String, val mimeType: String)

    companion object {
        internal fun detectImageFormat(url: String, contentType: String, bytes: ByteArray): ImageFormat {
            // 1. 优先通过文件头魔数判断真实格式，避免后缀与内容不符
            if (bytes.size >= 6) {
                val header6 = String(bytes, 0, 6, Charsets.US_ASCII)
                if (header6 == "GIF87a" || header6 == "GIF89a") {
                    return ImageFormat("gif", "image/gif")
                }
            }
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return ImageFormat("png", "image/png")
            }
            if (bytes.size >= 12 &&
                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            ) {
                return ImageFormat("webp", "image/webp")
            }

            // 2. 其次参考 Content-Type 响应头
            val lowerMime = contentType.substringBefore(';').trim().lowercase()
            when (lowerMime) {
                "image/gif" -> return ImageFormat("gif", "image/gif")
                "image/png" -> return ImageFormat("png", "image/png")
                "image/webp" -> return ImageFormat("webp", "image/webp")
                "image/jpeg", "image/jpg" -> return ImageFormat("jpg", "image/jpeg")
            }

            // 3. 再次参考 URL 路径扩展名
            val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                cleanPath.endsWith(".gif") -> ImageFormat("gif", "image/gif")
                cleanPath.endsWith(".png") -> ImageFormat("png", "image/png")
                cleanPath.endsWith(".webp") -> ImageFormat("webp", "image/webp")
                else -> ImageFormat("jpg", "image/jpeg")
            }
        }
    }
}
