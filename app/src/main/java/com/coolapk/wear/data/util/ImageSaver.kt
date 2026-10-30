package com.coolapk.wear.data.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object ImageSaver {

    private val okHttpClient = OkHttpClient()

    suspend fun saveImageToGallery(context: Context, imageUrl: String) = withContext(Dispatchers.IO) {
        try {
            if (imageUrl.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片地址无效", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "正在保存图片...", Toast.LENGTH_SHORT).show()
            }

            val req = Request.Builder().url(imageUrl).build()
            val response = okHttpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载失败: HTTP ${response.code}", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片数据为空", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val filename = "Coolapk_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Coolapk")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "无法创建相册文件", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            resolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // 震动反馈
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(50)
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "图片已保存至手表相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
