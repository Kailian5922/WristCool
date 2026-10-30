package com.coolapk.wear

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.api.DeviceProfile
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.data.repository.AuthRepository
import com.coolapk.wear.data.repository.BrowsingHistoryRepository
import com.coolapk.wear.presentation.components.RotaryDispatcher
import com.coolapk.wear.presentation.detail.FeedDetailScreen
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
import com.coolapk.wear.presentation.feeds.FeedsScreen
import com.coolapk.wear.presentation.feeds.FeedsViewModel
import com.coolapk.wear.presentation.theme.ThemeManager
import com.coolapk.wear.presentation.me.FavoritesScreen
import com.coolapk.wear.presentation.me.FavoritesViewModel
import com.coolapk.wear.presentation.me.HistoryScreen
import com.coolapk.wear.presentation.me.MeScreen
import com.coolapk.wear.presentation.me.MeViewModel
import com.coolapk.wear.presentation.search.SearchScreen
import com.coolapk.wear.presentation.search.SearchViewModel
import com.coolapk.wear.presentation.theme.CoolapkWearTheme
import com.coolapk.wear.presentation.topic.TopicScreen
import com.coolapk.wear.presentation.topic.TopicViewModel
import com.coolapk.wear.presentation.user.UserListScreen
import com.coolapk.wear.presentation.user.UserListType
import com.coolapk.wear.presentation.user.UserListViewModel
import com.coolapk.wear.presentation.user.UserProfileScreen
import com.coolapk.wear.presentation.user.UserProfileViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private lateinit var deviceProfile: DeviceProfile
    private lateinit var authRepository: AuthRepository
    private lateinit var browsingHistoryRepository: BrowsingHistoryRepository
    private lateinit var api: CoolapkApi

    private lateinit var feedsViewModel: FeedsViewModel
    private lateinit var meViewModel: MeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceProfile = DeviceProfile(applicationContext)
        authRepository = AuthRepository(applicationContext)
        browsingHistoryRepository = BrowsingHistoryRepository(applicationContext)
        api = CoolapkApi(deviceProfile, authRepository)
        ThemeManager.init(applicationContext)

        feedsViewModel = FeedsViewModel(api, authRepository)
        meViewModel = MeViewModel(authRepository, api, browsingHistoryRepository)

        val imageLoader = ImageLoader.Builder(applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .header("User-Agent", deviceProfile.userAgent)
                            .build()
                        chain.proceed(req)
                    }
                    .build()
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            CoolapkWearTheme {
                CoolapkWearApp(
                    api = api,
                    browsingHistoryRepository = browsingHistoryRepository,
                    feedsViewModel = feedsViewModel,
                    meViewModel = meViewModel
                )
            }
        }
    }

    /**
     * 完美拦截三星 Galaxy Watch 4/5/6/7 等 One UI Watch 设备旋转表圈（触摸表圈 / 物理表圈）事件。
     * 直接派发给当前前台界面的 ScalingLazyColumn 活跃滚动器，彻底解决 Samsung Watch 表圈不响应的问题。
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta != 0f) {
                RotaryDispatcher.triggerTick(this, window.decorView)
                val pixels = delta * 150f
                lifecycleScope.launch {
                    RotaryDispatcher.activeScrollBy?.invoke(pixels)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

@Composable
fun CoolapkWearApp(
    api: CoolapkApi,
    browsingHistoryRepository: BrowsingHistoryRepository,
    feedsViewModel: FeedsViewModel,
    meViewModel: MeViewModel
) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "feeds"
    ) {
        // 主页频道流（默认热榜，支持原生下拉刷新与点击 Tab 刷新）
        composable("feeds") {
            FeedsScreen(
                viewModel = feedsViewModel,
                onFeedClick = { feedId ->
                    navController.navigate("detail/$feedId")
                },
                onMeClick = {
                    navController.navigate("me")
                },
                onSearchClick = {
                    navController.navigate("search")
                },
                onUserClick = { uid ->
                    navController.navigate("user/$uid")
                },
                onTopicClick = { tag ->
                    navController.navigate("topic/${Uri.encode(tag)}")
                }
            )
        }

        // 搜索页面
        composable("search") {
            val searchViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                SearchViewModel(api)
            }
            SearchScreen(
                viewModel = searchViewModel,
                onFeedClick = { feedId ->
                    navController.navigate("detail/$feedId")
                },
                onUserClick = { uid ->
                    navController.navigate("user/$uid")
                },
                onTopicClick = { tag ->
                    navController.navigate("topic/${Uri.encode(tag)}")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 动态详情（带双击放大/双指手势、长按弹窗确认保存图片、话题胶囊点击、评论排序、按需图片、楼中楼）
        composable("detail/{feedId}") { backStackEntry ->
            val feedId = backStackEntry.arguments?.getString("feedId") ?: ""
            val initialFeed = FeedMemoryCache.get(feedId)
            val detailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "detail_$feedId"
            ) {
                FeedDetailViewModel(
                    api = api,
                    feedId = feedId,
                    initialFeed = initialFeed,
                    browsingHistoryRepository = browsingHistoryRepository
                )
            }
            FeedDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onUserClick = { uid ->
                    navController.navigate("user/$uid")
                },
                onTopicClick = { tag ->
                    navController.navigate("topic/${Uri.encode(tag)}")
                }
            )
        }

        // 用户主页与用户发布动态（可点击关注/粉丝进入列表）
        composable("user/{uid}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            val userViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "user_$uid"
            ) {
                UserProfileViewModel(api, uid)
            }
            UserProfileScreen(
                viewModel = userViewModel,
                onFeedClick = { feedId ->
                    navController.navigate("detail/$feedId")
                },
                onUserClick = { otherUid ->
                    if (otherUid != uid) {
                        navController.navigate("user/$otherUid")
                    }
                },
                onFollowClick = { targetUid ->
                    navController.navigate("userList/$targetUid/follow")
                },
                onFansClick = { targetUid ->
                    navController.navigate("userList/$targetUid/fans")
                },
                onTopicClick = { tag ->
                    navController.navigate("topic/${Uri.encode(tag)}")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // “我的”个人中心（支持点击关注/粉丝数或列表进入关注/粉丝列表）
        composable("me") {
            MeScreen(
                viewModel = meViewModel,
                onFavoritesClick = { navController.navigate("favorites") },
                onHistoryClick = { navController.navigate("history") },
                onUserClick = { uid -> navController.navigate("user/$uid") },
                onFollowClick = { targetUid -> navController.navigate("userList/$targetUid/follow") },
                onFansClick = { targetUid -> navController.navigate("userList/$targetUid/fans") },
                onBack = { navController.popBackStack() }
            )
        }

        // 兼容 settings 路由导航至 me
        composable("settings") {
            MeScreen(
                viewModel = meViewModel,
                onFavoritesClick = { navController.navigate("favorites") },
                onHistoryClick = { navController.navigate("history") },
                onUserClick = { uid -> navController.navigate("user/$uid") },
                onFollowClick = { targetUid -> navController.navigate("userList/$targetUid/follow") },
                onFansClick = { targetUid -> navController.navigate("userList/$targetUid/fans") },
                onBack = { navController.popBackStack() }
            )
        }

        // 我的收藏列表
        composable("favorites") {
            val favoritesViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                FavoritesViewModel(api)
            }
            FavoritesScreen(
                viewModel = favoritesViewModel,
                onFeedClick = { feedId -> navController.navigate("detail/$feedId") },
                onUserClick = { uid -> navController.navigate("user/$uid") },
                onBack = { navController.popBackStack() }
            )
        }

        // 浏览历史记录列表
        composable("history") {
            HistoryScreen(
                browsingHistoryRepository = browsingHistoryRepository,
                onFeedClick = { feedId -> navController.navigate("detail/$feedId") },
                onUserClick = { uid -> navController.navigate("user/$uid") },
                onBack = { navController.popBackStack() }
            )
        }

        // 关注列表与粉丝列表
        composable("userList/{uid}/{type}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            val typeStr = backStackEntry.arguments?.getString("type") ?: "follow"
            val type = if (typeStr == "fans") UserListType.FANS else UserListType.FOLLOW
            val userListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "userlist_${uid}_$typeStr") {
                UserListViewModel(api, uid, type)
            }
            UserListScreen(
                viewModel = userListViewModel,
                title = type.title,
                onUserClick = { targetUid -> navController.navigate("user/$targetUid") },
                onBack = { navController.popBackStack() }
            )
        }

        // 话题动态流
        composable("topic/{title}") { backStackEntry ->
            val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
            val tag = Uri.decode(rawTitle)
            val topicViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "topic_$tag") {
                TopicViewModel(api, tag)
            }
            TopicScreen(
                viewModel = topicViewModel,
                tag = tag,
                onFeedClick = { feedId -> navController.navigate("detail/$feedId") },
                onUserClick = { uid -> navController.navigate("user/$uid") },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
