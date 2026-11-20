package com.coolapk.wear.di

import android.content.Context
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.api.DeviceProfile
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.data.util.ImageSaver
import okhttp3.OkHttpClient

/**
 * 轻量级应用级依赖容器（Application 生命周期内单例共享）。
 * 统一管理网络客户端、鉴权仓储、历史记录、动态事件总线与图片保存器，
 * 避免多处自行实例化导致状态割裂（如 BroadcastReceiver 导入凭据不同步）或连接池泄漏。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val deviceProfile: DeviceProfile by lazy { DeviceProfile(appContext) }

    val okHttpClient: OkHttpClient by lazy { CoolapkApi.defaultOkHttpClient() }

    val authRepository: AuthRepository by lazy { AuthRepository(appContext) }

    val browsingHistoryRepository: BrowsingHistoryRepository by lazy {
        BrowsingHistoryRepository(appContext)
    }

    val feedUpdateBus: FeedUpdateBus by lazy { FeedUpdateBus() }

    val api: CoolapkApi by lazy {
        CoolapkApi(
            deviceProfile = deviceProfile,
            authRepository = authRepository,
            okHttpClient = okHttpClient
        )
    }

    val imageSaver: ImageSaver by lazy {
        ImageSaver(
            context = appContext,
            okHttpClient = okHttpClient,
            userAgent = deviceProfile.userAgent
        )
    }
}
