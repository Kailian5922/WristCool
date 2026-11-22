package com.coolapk.wear

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.coolapk.wear.di.AppContainer
import com.coolapk.wear.presentation.theme.ThemeManager
import okhttp3.Dispatcher

/**
 * WristCool 应用入口。
 * 1. 持有全局唯一 AppContainer 单例；
 * 2. 实现 ImageLoaderFactory，图床请求绝不附带登录 Cookie，并限制手表端图片并发连接数，避免刷爆酷安静态图床。
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
        val imageDispatcher = Dispatcher().apply {
            maxRequests = 12
            maxRequestsPerHost = 4
        }
        val imageClient = container.okHttpClient.newBuilder()
            .dispatcher(imageDispatcher)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .removeHeader("Cookie")
                    .header("User-Agent", IMAGE_CDN_USER_AGENT)
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

    companion object {
        private const val IMAGE_CDN_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}
