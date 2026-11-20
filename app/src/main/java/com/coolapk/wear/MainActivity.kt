package com.coolapk.wear

import android.net.Uri
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.di.AppContainer
import com.coolapk.wear.presentation.components.RotaryDispatcher
import com.coolapk.wear.presentation.detail.FeedDetailScreen
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
import com.coolapk.wear.presentation.feeds.FeedsScreen
import com.coolapk.wear.presentation.feeds.FeedsViewModel
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

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as WristCoolApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = appContainer
        setContent {
            CoolapkWearTheme {
                CoolapkWearApp(container = container)
            }
        }
    }

    /**
     * 拦截三星 Galaxy Watch 4/5/6/7 等 One UI Watch 设备的旋转表圈事件。
     * 通过事件时间戳去重并与 [RotaryDispatcher] 栈顶消费者联动，
     * 既解决表圈失焦不响应的问题，也避免与 Compose onRotaryScrollEvent 双重触发或穿透模态浮层。
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null &&
            event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) &&
            RotaryDispatcher.activeScrollBy != null
        ) {
            val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta != 0f && RotaryDispatcher.tryClaimEvent(event.eventTime)) {
                RotaryDispatcher.triggerTick(this, window.decorView)
                RotaryDispatcher.dispatchScroll(lifecycleScope, delta * 150f)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

@Composable
fun CoolapkWearApp(container: AppContainer) {
    val navController = rememberSwipeDismissableNavController()

    // 托管于 Activity ViewModelStore，配置变更时不丢状态且销毁时自动清理协程
    val feedsViewModel: FeedsViewModel = viewModel(key = "root_feeds") {
        FeedsViewModel(
            api = container.api,
            authRepository = container.authRepository,
            feedUpdateBus = container.feedUpdateBus
        )
    }
    val meViewModel: MeViewModel = viewModel(key = "root_me") {
        MeViewModel(
            authRepository = container.authRepository,
            api = container.api,
            browsingHistoryRepository = container.browsingHistoryRepository
        )
    }

    fun navigateToFeed(feedId: String) {
        val cleanId = feedId.trim()
        if (cleanId.isNotEmpty()) {
            navController.navigate("detail/${Uri.encode(cleanId)}")
        }
    }

    fun navigateToUser(uid: String) {
        val cleanUid = uid.trim()
        if (cleanUid.isNotEmpty()) {
            navController.navigate("user/${Uri.encode(cleanUid)}")
        }
    }

    fun navigateToTopic(tag: String) {
        val cleanTag = tag.trim().removeSurrounding("#")
        if (cleanTag.isNotEmpty()) {
            navController.navigate("topic/${Uri.encode(cleanTag)}")
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "feeds"
    ) {
        composable("feeds") {
            FeedsScreen(
                viewModel = feedsViewModel,
                onFeedClick = ::navigateToFeed,
                onMeClick = { navController.navigate("me") },
                onSearchClick = { navController.navigate("search") },
                onUserClick = ::navigateToUser,
                onTopicClick = ::navigateToTopic
            )
        }

        composable("search") {
            val searchViewModel: SearchViewModel = viewModel {
                SearchViewModel(
                    api = container.api,
                    feedUpdateBus = container.feedUpdateBus
                )
            }
            SearchScreen(
                viewModel = searchViewModel,
                onFeedClick = ::navigateToFeed,
                onUserClick = ::navigateToUser,
                onTopicClick = ::navigateToTopic,
                onBack = { navController.popBackStack() }
            )
        }

        composable("detail/{feedId}") { backStackEntry ->
            val feedId = backStackEntry.arguments?.getString("feedId").orEmpty()
            val initialFeed = FeedMemoryCache.get(feedId)
            val detailViewModel: FeedDetailViewModel = viewModel(key = "detail_$feedId") {
                FeedDetailViewModel(
                    api = container.api,
                    feedId = feedId,
                    initialFeed = initialFeed,
                    browsingHistoryRepository = container.browsingHistoryRepository,
                    feedUpdateBus = container.feedUpdateBus
                )
            }
            FeedDetailScreen(
                viewModel = detailViewModel,
                imageSaver = container.imageSaver,
                onBack = { navController.popBackStack() },
                onUserClick = ::navigateToUser,
                onTopicClick = ::navigateToTopic
            )
        }

        composable("user/{uid}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            val userViewModel: UserProfileViewModel = viewModel(key = "user_$uid") {
                UserProfileViewModel(
                    api = container.api,
                    uid = uid,
                    feedUpdateBus = container.feedUpdateBus
                )
            }
            UserProfileScreen(
                viewModel = userViewModel,
                onFeedClick = ::navigateToFeed,
                onUserClick = { otherUid ->
                    if (otherUid.isNotBlank() && otherUid != uid) {
                        navigateToUser(otherUid)
                    }
                },
                onFollowClick = { targetUid ->
                    if (targetUid.isNotBlank()) {
                        navController.navigate("userList/${Uri.encode(targetUid)}/follow")
                    }
                },
                onFansClick = { targetUid ->
                    if (targetUid.isNotBlank()) {
                        navController.navigate("userList/${Uri.encode(targetUid)}/fans")
                    }
                },
                onTopicClick = ::navigateToTopic,
                onBack = { navController.popBackStack() }
            )
        }

        composable("me") {
            MeScreen(
                viewModel = meViewModel,
                onFavoritesClick = { navController.navigate("favorites") },
                onHistoryClick = { navController.navigate("history") },
                onUserClick = ::navigateToUser,
                onFollowClick = { targetUid ->
                    if (targetUid.isNotBlank()) {
                        navController.navigate("userList/${Uri.encode(targetUid)}/follow")
                    }
                },
                onFansClick = { targetUid ->
                    if (targetUid.isNotBlank()) {
                        navController.navigate("userList/${Uri.encode(targetUid)}/fans")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("favorites") {
            val favoritesViewModel: FavoritesViewModel = viewModel {
                FavoritesViewModel(
                    api = container.api,
                    feedUpdateBus = container.feedUpdateBus
                )
            }
            FavoritesScreen(
                viewModel = favoritesViewModel,
                onFeedClick = ::navigateToFeed,
                onUserClick = ::navigateToUser,
                onBack = { navController.popBackStack() }
            )
        }

        composable("history") {
            HistoryScreen(
                browsingHistoryRepository = container.browsingHistoryRepository,
                onFeedClick = ::navigateToFeed,
                onUserClick = ::navigateToUser,
                onBack = { navController.popBackStack() }
            )
        }

        composable("userList/{uid}/{type}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            val typeStr = backStackEntry.arguments?.getString("type") ?: "follow"
            val type = if (typeStr == "fans") UserListType.FANS else UserListType.FOLLOW
            val userListViewModel: UserListViewModel = viewModel(key = "userlist_${uid}_$typeStr") {
                UserListViewModel(container.api, uid, type)
            }
            UserListScreen(
                viewModel = userListViewModel,
                title = type.title,
                onUserClick = ::navigateToUser,
                onBack = { navController.popBackStack() }
            )
        }

        // Navigation Compose 会自动对路由参数执行一次 URL 解码，此处不再重复 Uri.decode，避免含 % 的话题名损坏
        composable("topic/{title}") { backStackEntry ->
            val tag = backStackEntry.arguments?.getString("title").orEmpty()
            val topicViewModel: TopicViewModel = viewModel(key = "topic_$tag") {
                TopicViewModel(
                    api = container.api,
                    tag = tag,
                    feedUpdateBus = container.feedUpdateBus
                )
            }
            TopicScreen(
                viewModel = topicViewModel,
                tag = tag,
                onFeedClick = ::navigateToFeed,
                onUserClick = ::navigateToUser,
                onTopicClick = ::navigateToTopic,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
