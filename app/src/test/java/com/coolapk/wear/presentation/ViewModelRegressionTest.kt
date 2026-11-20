package com.coolapk.wear.presentation

import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.ForwardFeedInfo
import com.coolapk.wear.data.model.VoteInfo
import com.coolapk.wear.data.model.VoteOption
import com.coolapk.wear.data.repository.BlacklistState
import com.coolapk.wear.data.repository.BlockedUserEntry
import com.coolapk.wear.data.repository.FeedUpdateBus
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
import com.coolapk.wear.presentation.feeds.FeedTab
import com.coolapk.wear.presentation.feeds.FeedsViewModel
import com.coolapk.wear.presentation.inbox.ChatViewModel
import com.coolapk.wear.presentation.me.FavoritesViewModel
import com.coolapk.wear.presentation.search.SearchViewModel
import com.coolapk.wear.presentation.user.UserProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var api: CoolapkApi

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()
        api = CoolapkApi(
            deviceCodeProvider = { "vm-test-device" },
            userAgent = "TestUA",
            credentialsProvider = { AuthCredentials(cookie = "uid=1; token=t1", uid = "1") },
            baseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun waitUntil(timeoutMs: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `UserProfileViewModel does not re-fetch when hasMore is false`() = runTest(testDispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v6/user/profile") -> MockResponse().setBody("""{"data":{"uid":"2","username":"Bob"}}""")
                    else -> MockResponse().setBody("""{"data":[]}""")
                }
            }
        }

        val vm = UserProfileViewModel(api, uid = "2")
        waitUntil { server.requestCount >= 2 && !vm.uiState.value.isLoadingProfile && !vm.uiState.value.isLoadingFeeds }

        val initialRequestCount = server.requestCount
        assertFalse("hasMore should be false after empty page", vm.uiState.value.hasMore)

        // Calling loadFeeds(false) repeatedly must NOT send new requests
        vm.loadFeeds(refresh = false)
        vm.loadFeeds(refresh = false)
        advanceUntilIdle()
        assertEquals(initialRequestCount, server.requestCount)
    }

    @Test
    fun `FeedDetailViewModel rolls back like on API error without losing state`() = runTest(testDispatcher) {
        val initial = FeedItem(
            id = "500",
            uid = "2",
            username = "Author",
            userAvatar = "",
            dateline = 0L,
            dateString = "刚刚",
            deviceTitle = "",
            title = "",
            message = "Body",
            likeNum = 10,
            isLiked = false,
            isFavorite = true
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v6/feed/detail") -> MockResponse().setBody(
                        """{"data":{"entityType":"feed","id":"500","uid":"2","username":"Author","message":"Body","likenum":10,"is_like":0,"is_favorite":1}}"""
                    )
                    path.startsWith("/v6/feed/replyList") -> MockResponse().setBody("""{"data":[]}""")
                    path.startsWith("/v6/feed/like") -> MockResponse().setBody("""{"status":-1,"message":"请先登录"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val vm = FeedDetailViewModel(api, feedId = "500", initialFeed = initial)
        waitUntil { server.requestCount >= 2 && !vm.uiState.value.isLoading && !vm.uiState.value.isLoadingComments }

        // Toggle like -> server returns business error
        vm.toggleLike()
        waitUntil { !vm.uiState.value.isTogglingLike && vm.uiState.value.actionMessage != null }

        assertFalse(vm.uiState.value.isLiked)
        assertEquals(10, vm.uiState.value.likeCount)
        assertTrue(vm.uiState.value.isFavorite)
        assertEquals("请先登录", vm.uiState.value.actionMessage)
    }

    @Test
    fun `FeedDetailViewModel rolls back comment like on failure and updates vote on success`() = runTest(testDispatcher) {
        val initialVote = VoteInfo(
            id = "v1",
            title = "选择",
            totalVoteNum = 10,
            minSelect = 1,
            maxSelect = 1,
            isVoted = false,
            options = listOf(
                VoteOption(id = "o1", title = "A", voteNum = 6),
                VoteOption(id = "o2", title = "B", voteNum = 4)
            )
        )
        val initial = FeedItem(
            id = "600",
            uid = "2",
            username = "Author",
            userAvatar = "",
            dateline = 0L,
            dateString = "刚刚",
            deviceTitle = "",
            title = "",
            message = "投票贴",
            vote = initialVote
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v6/feed/detail") -> MockResponse().setResponseCode(500)
                    path.startsWith("/v6/feed/replyList") -> MockResponse().setBody(
                        """{"data":[{"id":"c1","uid":"9","username":"Tester","message":"好贴","likenum":3,"is_like":0}]}"""
                    )
                    path.startsWith("/v6/feed/likeReply") -> MockResponse().setBody("""{"status":-1,"message":"未登录"}""")
                    path.startsWith("/v6/vote/createUserVote") -> MockResponse().setBody("""{"data":1}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val vm = FeedDetailViewModel(api, feedId = "600", initialFeed = initial)
        waitUntil { vm.uiState.value.comments.size == 1 }
        assertEquals(1, vm.uiState.value.comments.size)

        // Comment like fails -> should rollback
        vm.toggleCommentLike(vm.uiState.value.comments.first())
        waitUntil { vm.uiState.value.actionMessage == "未登录" }
        assertFalse(vm.uiState.value.comments.first().isLiked)
        assertEquals(3, vm.uiState.value.comments.first().likeNum)

        // Vote succeeds -> increments option and total count
        vm.submitVote(listOf("o1"))
        waitUntil { vm.uiState.value.feed?.vote?.isVoted == true }
        val updatedVote = vm.uiState.value.feed?.vote!!
        assertTrue(updatedVote.isVoted)
        assertEquals(11, updatedVote.totalVoteNum)
        assertEquals(7, updatedVote.options.first { it.id == "o1" }.voteNum)
        assertEquals(listOf("o1"), updatedVote.selectedOptionIds)
    }

    @Test
    fun `FavoritesViewModel batchDeleteSelected keeps failed items`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[
                    {"entityType":"feed","id":"f1","uid":"1","username":"A","message":"m1"},
                    {"entityType":"feed","id":"f2","uid":"1","username":"B","message":"m2"}
                ]}""".trimIndent()
            )
        )

        val vm = FavoritesViewModel(api)
        waitUntil { vm.uiState.value.items.size == 2 }
        assertEquals(2, vm.uiState.value.items.size)

        vm.enterMultiSelectMode("f1")
        vm.toggleSelection("f2")

        // f1 succeeds, f2 fails on both /v6/feed/unFavorite and /v6/feed/unfavorite
        server.enqueue(MockResponse().setBody("""{"data":1}"""))
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"失败"}"""))
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"失败"}"""))

        var resultPair: Pair<Int, Int>? = null
        vm.batchDeleteSelected { ok, fail -> resultPair = ok to fail }
        waitUntil { resultPair != null }

        assertEquals(1 to 1, resultPair)
        assertEquals(listOf("f2"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `SearchViewModel resets hasSearched when query cleared`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"data":["WearOS","酷安"]}"""))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val vm = SearchViewModel(api)
        waitUntil { vm.uiState.value.hotKeywords.isNotEmpty() }

        vm.search("WearOS")
        assertTrue(vm.uiState.value.hasSearched)

        vm.updateQuery("")
        assertFalse(vm.uiState.value.hasSearched)
        assertEquals("", vm.uiState.value.query)
    }

    @Test
    fun `BlacklistState filters blocked users forward authors keywords and comments`() {
        val state = BlacklistState(
            blockedUsers = listOf(BlockedUserEntry(uid = "999", username = "Spammer")),
            blockedKeywords = listOf("广告推广")
        )
        val normalFeed = FeedItem(
            id = "1", uid = "10", username = "Alice", userAvatar = "",
            dateline = 0L, dateString = "", deviceTitle = "", title = "", message = "手表很好用"
        )
        val blockedUserFeed = normalFeed.copy(id = "2", uid = "999")
        val blockedForwardFeed = normalFeed.copy(
            id = "3",
            forwardFeed = ForwardFeedInfo(id = "9", uid = "999", username = "Spammer", message = "原帖")
        )
        val blockedKeywordFeed = normalFeed.copy(id = "4", message = "这是一条广告推广内容")

        assertFalse(state.shouldFilterFeed(normalFeed))
        assertTrue(state.shouldFilterFeed(blockedUserFeed))
        assertTrue(state.shouldFilterFeed(blockedForwardFeed))
        assertTrue(state.shouldFilterFeed(blockedKeywordFeed))

        val normalComment = CommentItem(
            id = "c1", uid = "10", username = "Alice", userAvatar = "",
            dateline = 0L, dateString = "", message = "赞"
        )
        assertFalse(state.shouldFilterComment(normalComment))
        assertTrue(state.shouldFilterComment(normalComment.copy(uid = "999")))
        assertTrue(state.shouldFilterComment(normalComment.copy(message = "看看广告推广")))
    }

    @Test
    fun `ChatViewModel buildUkey orders numeric uids ascending`() {
        assertEquals("42_1000", ChatViewModel.buildUkey("1000", "42"))
        assertEquals("42_1000", ChatViewModel.buildUkey("42", "1000"))
    }

    @Test
    fun `FeedsViewModel caches loaded channels across switches and reloads only on explicit refresh`() = runTest(testDispatcher) {
        var headlineCalls = 0
        var hotCalls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/v6/main/headline") -> {
                        headlineCalls++
                        MockResponse().setBody(
                            """{"data":[{"entityType":"feed","id":"h$headlineCalls","uid":"10","username":"HeadUser","message":"头条第${headlineCalls}次"}]}"""
                        )
                    }
                    path.startsWith("/v6/page/dataList") -> {
                        hotCalls++
                        MockResponse().setBody(
                            """{"data":[{"entityType":"feed","id":"hot$hotCalls","uid":"20","username":"HotUser","message":"热榜第${hotCalls}次"}]}"""
                        )
                    }
                    else -> MockResponse().setBody("""{"data":[]}""")
                }
            }
        }

        val bus = FeedUpdateBus()
        val vm = FeedsViewModel(
            api = api,
            feedUpdateBus = bus,
            credentialsFlow = MutableStateFlow(AuthCredentials(cookie = "uid=1; token=t1", uid = "1"))
        )

        // 冷启动自动刷新头条一次
        waitUntil { !vm.uiState.value.isLoading && vm.uiState.value.items.isNotEmpty() }
        assertEquals(1, headlineCalls)
        assertEquals(listOf("h1"), vm.uiState.value.items.map { it.id })

        // 首次切换到热榜 -> 触发热榜加载一次
        vm.switchTab(FeedTab.HOT)
        waitUntil { vm.uiState.value.currentTab == FeedTab.HOT && !vm.uiState.value.isLoading && vm.uiState.value.items.isNotEmpty() }
        assertEquals(1, hotCalls)
        assertEquals(listOf("hot1"), vm.uiState.value.items.map { it.id })

        // 后台通过 FeedUpdateBus 更新已缓存头条的点赞状态
        bus.publish(
            FeedItem(
                id = "h1", uid = "10", username = "HeadUser", userAvatar = "",
                dateline = 0L, dateString = "", deviceTitle = "", title = "",
                message = "头条第1次", likeNum = 99, isLiked = true
            )
        )
        advanceUntilIdle()

        // 切回头条 -> 直接命中缓存，不发起新网络请求，且保留同步的点赞状态
        vm.switchTab(FeedTab.HEADLINE)
        advanceUntilIdle()
        assertEquals(1, headlineCalls)
        assertEquals(listOf("h1"), vm.uiState.value.items.map { it.id })
        assertEquals(99, vm.uiState.value.items.first().likeNum)
        assertTrue(vm.uiState.value.items.first().isLiked)

        // 再次点击当前已选中的头条 -> 不重复加载
        vm.switchTab(FeedTab.HEADLINE)
        advanceUntilIdle()
        assertEquals(1, headlineCalls)

        // 显式下拉刷新 -> 触发重新拉取并覆盖缓存
        vm.refresh()
        waitUntil { headlineCalls == 2 && vm.uiState.value.items.firstOrNull()?.id == "h2" }
        assertEquals(2, headlineCalls)
        assertEquals(listOf("h2"), vm.uiState.value.items.map { it.id })
    }
}
