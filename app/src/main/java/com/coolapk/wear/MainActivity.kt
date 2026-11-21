package com.coolapk.wear

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.di.AppContainer
import com.coolapk.wear.presentation.components.RotaryDispatcher
import com.coolapk.wear.presentation.components.rememberSingleStepPagerController
import com.coolapk.wear.presentation.components.rememberSingleStepTargetedFlingBehavior
import com.coolapk.wear.presentation.components.singleStepPagerTouchTracker
import com.coolapk.wear.presentation.detail.FeedDetailScreen
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
import com.coolapk.wear.presentation.feeds.FeedsScreen
import com.coolapk.wear.presentation.feeds.FeedsViewModel
import com.coolapk.wear.presentation.inbox.ChatScreen
import com.coolapk.wear.presentation.inbox.ChatViewModel
import com.coolapk.wear.presentation.inbox.InboxScreen
import com.coolapk.wear.presentation.inbox.InboxViewModel
import com.coolapk.wear.presentation.me.BlacklistScreen
import com.coolapk.wear.presentation.me.CollectionDetailScreen
import com.coolapk.wear.presentation.me.CollectionDetailViewModel
import com.coolapk.wear.presentation.me.CollectionsScreen
import com.coolapk.wear.presentation.me.CollectionsViewModel
import com.coolapk.wear.presentation.me.FavoritesScreen
import com.coolapk.wear.presentation.me.FavoritesViewModel
import com.coolapk.wear.presentation.me.HistoryScreen
import com.coolapk.wear.presentation.me.MeScreen
import com.coolapk.wear.presentation.me.MeViewModel
import com.coolapk.wear.presentation.me.MyActivityScreen
import com.coolapk.wear.presentation.me.MyActivityType
import com.coolapk.wear.presentation.me.MyActivityViewModel
import com.coolapk.wear.presentation.me.SettingsScreen
import com.coolapk.wear.presentation.product.ProductScreen
import com.coolapk.wear.presentation.product.ProductViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as WristCoolApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = appContainer
        RotaryDispatcher.hapticEnabled = container.settingsRepository.settings.value.hapticEnabled

        setContent {
            CoolapkWearTheme(settingsRepository = container.settingsRepository) {
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

/**
 * WristCool Wear OS Material 3 Expressive 根壳层。
 * 1. 全局单一 AppScaffold（遵循 Wear M3 & Horologist 规范，顶栏 TimeText 跨页稳态驻留）；
 * 2. 首页采用 3 页 HorizontalPagerScaffold（动态广场 / 消息与私信 / 个人中心），左右滑页与点按皆顺畅；
 * 3. 完整支持动态详情、用户主页、话题、数码产品、私信聊天、收藏单、点赞/回复记录、设置与屏蔽管理。
 */
@Composable
fun CoolapkWearApp(container: AppContainer) {
    val navController = rememberSwipeDismissableNavController()
    val settings by container.settingsRepository.settings.collectAsState()
    LaunchedEffect(settings.hapticEnabled) {
        RotaryDispatcher.hapticEnabled = settings.hapticEnabled
    }

    val feedsViewModel: FeedsViewModel = viewModel(key = "root_feeds") {
        FeedsViewModel(
            api = container.api,
            authRepository = container.authRepository,
            feedUpdateBus = container.feedUpdateBus,
            blacklistRepository = container.blacklistRepository,
            settingsRepository = container.settingsRepository
        )
    }
    val inboxViewModel: InboxViewModel = viewModel(key = "root_inbox") {
        InboxViewModel(
            api = container.api,
            authRepository = container.authRepository
        )
    }
    val meViewModel: MeViewModel = viewModel(key = "root_me") {
        MeViewModel(
            authRepository = container.authRepository,
            api = container.api,
            browsingHistoryRepository = container.browsingHistoryRepository
        )
    }

    val inboxUiState by inboxViewModel.uiState.collectAsState()

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

    fun navigateToProduct(productId: String) {
        val cleanId = productId.trim()
        if (cleanId.isNotEmpty()) {
            navController.navigate("product/${Uri.encode(cleanId)}")
        }
    }

    fun navigateToChat(ukey: String, partnerUid: String, partnerName: String) {
        if (ukey.isNotBlank()) {
            val safeUid = partnerUid.ifBlank { "0" }
            val safeName = partnerName.ifBlank { "私信" }
            navController.navigate(
                "chat/${Uri.encode(ukey)}/${Uri.encode(safeUid)}/${Uri.encode(safeName)}"
            )
        }
    }

    AppScaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "home"
        ) {
            // 根级 3 页 Horologist / M3 Expressive HorizontalPagerScaffold：动态 | 消息 | 我的
            composable("home") {
                val pagerState = rememberPagerState(initialPage = 0) { 3 }
                val singleStepController = rememberSingleStepPagerController(pagerState)
                val baseSnapFlingBehavior = PagerDefaults.snapFlingBehavior(
                    state = pagerState,
                    maxFlingPages = 0,
                    snapAnimationSpec = PagerDefaults.SnapAnimationSpec,
                    snapPositionalThreshold = 0.5f
                )
                val singleStepFlingBehavior = rememberSingleStepTargetedFlingBehavior(
                    pagerState = pagerState,
                    controller = singleStepController,
                    delegate = baseSnapFlingBehavior
                )
                val scope = rememberCoroutineScope()

                fun jumpToPage(target: Int) {
                    singleStepController.onProgrammaticJump(
                        targetPage = target,
                        nowMs = SystemClock.uptimeMillis()
                    )
                    scope.launch {
                        try {
                            pagerState.animateScrollToPage(target)
                        } catch (_: CancellationException) {
                            pagerState.scrollToPage(target)
                        }
                    }
                }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage == 1) {
                        inboxViewModel.refreshCounts()
                    }
                }

                HorizontalPagerScaffold(pagerState = pagerState) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.singleStepPagerTouchTracker(
                            pagerState = pagerState,
                            controller = singleStepController
                        ),
                        beyondViewportPageCount = 1,
                        rotaryScrollableBehavior = null,
                        flingBehavior = singleStepFlingBehavior,
                        key = { it }
                    ) { page ->
                        AnimatedPage(pageIndex = page, pagerState = pagerState) {
                            val isActive = pagerState.currentPage == page
                            when (page) {
                                0 -> FeedsScreen(
                                    viewModel = feedsViewModel,
                                    unreadCount = inboxUiState.counts.totalUnread,
                                    isActivePage = isActive,
                                    onFeedClick = ::navigateToFeed,
                                    onMeClick = { jumpToPage(2) },
                                    onInboxClick = { jumpToPage(1) },
                                    onSearchClick = { navController.navigate("search") },
                                    onUserClick = ::navigateToUser,
                                    onTopicClick = ::navigateToTopic,
                                    onProductClick = ::navigateToProduct
                                )
                                1 -> InboxScreen(
                                    viewModel = inboxViewModel,
                                    isActivePage = isActive,
                                    onFeedClick = ::navigateToFeed,
                                    onUserClick = ::navigateToUser,
                                    onOpenChat = ::navigateToChat,
                                    onGoLogin = { jumpToPage(2) }
                                )
                                else -> MeScreen(
                                    viewModel = meViewModel,
                                    isActivePage = isActive,
                                    onFavoritesClick = { navController.navigate("favorites") },
                                    onCollectionsClick = { uid ->
                                        navController.navigate("collections/${Uri.encode(uid.ifBlank { "self" })}")
                                    },
                                    onMyLikesClick = { uid ->
                                        navController.navigate("activity/${Uri.encode(uid.ifBlank { "self" })}/${MyActivityType.LIKES.routeKey}")
                                    },
                                    onMyRepliesClick = { uid ->
                                        navController.navigate("activity/${Uri.encode(uid.ifBlank { "self" })}/${MyActivityType.REPLIES.routeKey}")
                                    },
                                    onHistoryClick = { navController.navigate("history") },
                                    onSettingsClick = { navController.navigate("settings") },
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
                                    onBack = { jumpToPage(0) }
                                )
                            }
                        }
                    }
                }
            }

            composable("search") {
                val searchViewModel: SearchViewModel = viewModel {
                    SearchViewModel(
                        api = container.api,
                        feedUpdateBus = container.feedUpdateBus,
                        searchHistoryRepository = container.searchHistoryRepository,
                        blacklistRepository = container.blacklistRepository
                    )
                }
                SearchScreen(
                    viewModel = searchViewModel,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onProductClick = ::navigateToProduct,
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
                        feedUpdateBus = container.feedUpdateBus,
                        blacklistRepository = container.blacklistRepository,
                        settingsRepository = container.settingsRepository
                    )
                }
                FeedDetailScreen(
                    viewModel = detailViewModel,
                    imageSaver = container.imageSaver,
                    onBack = { navController.popBackStack() },
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onProductClick = ::navigateToProduct
                )
            }

            composable("user/{uid}") { backStackEntry ->
                val uid = backStackEntry.arguments?.getString("uid").orEmpty()
                val userViewModel: UserProfileViewModel = viewModel(key = "user_$uid") {
                    UserProfileViewModel(
                        api = container.api,
                        uid = uid,
                        feedUpdateBus = container.feedUpdateBus,
                        blacklistRepository = container.blacklistRepository
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
                    onProductClick = ::navigateToProduct,
                    onOpenChat = ::navigateToChat,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("chat/{ukey}/{partnerUid}/{partnerName}") { backStackEntry ->
                val ukey = backStackEntry.arguments?.getString("ukey").orEmpty()
                val partnerUid = backStackEntry.arguments?.getString("partnerUid").orEmpty().let {
                    if (it == "0") "" else it
                }
                val partnerName = backStackEntry.arguments?.getString("partnerName").orEmpty()
                val chatViewModel: ChatViewModel = viewModel(key = "chat_$ukey") {
                    ChatViewModel(
                        api = container.api,
                        ukey = ukey,
                        partnerUid = partnerUid,
                        partnerName = partnerName
                    )
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onUserClick = ::navigateToUser,
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

            composable("collections/{uid}") { backStackEntry ->
                val rawUid = backStackEntry.arguments?.getString("uid").orEmpty()
                val targetUid = if (rawUid == "self") "" else rawUid
                val collectionsVm: CollectionsViewModel = viewModel(key = "cols_$rawUid") {
                    CollectionsViewModel(api = container.api, uid = targetUid)
                }
                CollectionsScreen(
                    viewModel = collectionsVm,
                    onCollectionClick = { id, title, isDefault ->
                        val defaultFlag = if (isDefault) "1" else "0"
                        navController.navigate(
                            "collectionDetail/${Uri.encode(id)}/${Uri.encode(title.ifBlank { "我的收藏" })}/$defaultFlag"
                        )
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("collectionDetail/{collectionId}/{title}/{isDefault}") { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId").orEmpty()
                val title = backStackEntry.arguments?.getString("title").orEmpty()
                val isDefault = backStackEntry.arguments?.getString("isDefault") == "1"
                val detailVm: CollectionDetailViewModel = viewModel(key = "col_detail_${collectionId}_$isDefault") {
                    CollectionDetailViewModel(
                        api = container.api,
                        collectionId = collectionId,
                        isDefault = isDefault,
                        feedUpdateBus = container.feedUpdateBus
                    )
                }
                CollectionDetailScreen(
                    viewModel = detailVm,
                    title = title,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onProductClick = ::navigateToProduct,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("activity/{uid}/{type}") { backStackEntry ->
                val rawUid = backStackEntry.arguments?.getString("uid").orEmpty()
                val targetUid = if (rawUid == "self") "" else rawUid
                val typeStr = backStackEntry.arguments?.getString("type").orEmpty()
                val type = if (typeStr == MyActivityType.REPLIES.routeKey) MyActivityType.REPLIES else MyActivityType.LIKES
                val actVm: MyActivityViewModel = viewModel(key = "act_${rawUid}_${type.routeKey}") {
                    MyActivityViewModel(
                        api = container.api,
                        uid = targetUid,
                        type = type,
                        feedUpdateBus = container.feedUpdateBus
                    )
                }
                MyActivityScreen(
                    viewModel = actVm,
                    type = type,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onProductClick = ::navigateToProduct,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("history") {
                HistoryScreen(
                    browsingHistoryRepository = container.browsingHistoryRepository,
                    authRepository = container.authRepository,
                    api = container.api,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                val context = androidx.compose.ui.platform.LocalContext.current
                SettingsScreen(
                    settingsRepository = container.settingsRepository,
                    blacklistRepository = container.blacklistRepository,
                    onOpenBlacklist = { navController.navigate("blacklist") },
                    onClearImageCache = { meViewModel.clearCache(context) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("blacklist") {
                BlacklistScreen(
                    blacklistRepository = container.blacklistRepository,
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

            composable("topic/{title}") { backStackEntry ->
                val tag = backStackEntry.arguments?.getString("title").orEmpty()
                val topicViewModel: TopicViewModel = viewModel(key = "topic_$tag") {
                    TopicViewModel(
                        api = container.api,
                        tag = tag,
                        feedUpdateBus = container.feedUpdateBus,
                        blacklistRepository = container.blacklistRepository
                    )
                }
                TopicScreen(
                    viewModel = topicViewModel,
                    tag = tag,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onProductClick = ::navigateToProduct,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("product/{productId}") { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId").orEmpty()
                val productViewModel: ProductViewModel = viewModel(key = "product_$productId") {
                    ProductViewModel(
                        api = container.api,
                        productId = productId,
                        feedUpdateBus = container.feedUpdateBus,
                        blacklistRepository = container.blacklistRepository
                    )
                }
                ProductScreen(
                    viewModel = productViewModel,
                    onFeedClick = ::navigateToFeed,
                    onUserClick = ::navigateToUser,
                    onTopicClick = ::navigateToTopic,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
