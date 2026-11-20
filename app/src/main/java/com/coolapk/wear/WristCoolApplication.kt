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
 * 2. 实现 ImageLoaderFactory，自动为酷安接口域图片附加请求头与登录态。
 */
class WristCoolApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ThemeManager.init(this, container.settingsRepository)
    }

    override fun newImageLoader(): ImageLoader {
        val imageClient = container.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val reqBuilder = original.newBuilder()
                    .header("User-Agent", container.deviceProfile.userAgent)
                val host = original.url.host
                if (host.equals("api.coolapk.com", ignoreCase = true) || host.endsWith(".coolapk.com", ignoreCase = true)) {
                    val creds = container.authRepository.credentials.value
                    if (creds.isLoggedIn && original.header("Cookie").isNullOrBlank()) {
                        reqBuilder.header("Cookie", creds.cookie)
                    }
                }
                chain.proceed(reqBuilder.build())
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
