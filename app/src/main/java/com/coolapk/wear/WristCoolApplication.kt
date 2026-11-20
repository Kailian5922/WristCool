package com.coolapk.wear

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.coolapk.wear.di.AppContainer
import com.coolapk.wear.presentation.theme.ThemeManager

/**
 * WristCool 应用入口。
 * 1. 持有全局唯一 AppContainer 单例；
 * 2. 实现 ImageLoaderFactory，避免每次 Activity 重建都重复创建 Coil ImageLoader 与 OkHttpClient。
 */
class WristCoolApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ThemeManager.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val imageClient = container.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", container.deviceProfile.userAgent)
                    .build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .okHttpClient(imageClient)
            .crossfade(true)
            .build()
    }
}
