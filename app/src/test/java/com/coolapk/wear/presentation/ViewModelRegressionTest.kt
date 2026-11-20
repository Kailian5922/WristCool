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
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
import com.coolapk.wear.presentation.inbox.ChatViewModel
import com.coolapk.wear.presentation.me.FavoritesViewModel
import com.coolapk.wear.presentation.search.SearchViewModel
import com.coolapk.wear.presentation.user.UserProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Test
    fun `UserProfileViewModel does not re-fetch when hasMore is false`() = runTest(testDispatcher) {
        // init triggers getUserProfile + getUserFeeds(page=1)
        server.enqueue(MockResponse().setBody("""{"data":{"uid":"2","username":"Bob"}}"""))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val vm = UserProfileViewModel(api, uid = "2")
        // Wait for IO requests to settle
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }

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
        // init parallel calls: detail + replies
        server.enqueue(
            MockResponse().setBody(
                """{"data":{"entityType":"feed","id":"500","uid":"2","username":"Author","message":"Body","likenum":10,"is_like":0,"is_favorite":1}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val vm = FeedDetailViewModel(api, feedId = "500", initialFeed = initial)
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }

        // Toggle like -> server returns business error
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"请先登录"}"""))
        vm.toggleLike()
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }

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
        server.enqueue(MockResponse().setResponseCode(500)) // keep initialFeed with vote
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"c1","uid":"9","username":"Tester","message":"好贴","likenum":3,"is_like":0}]}"""
            )
        )

        val vm = FeedDetailViewModel(api, feedId = "600", initialFeed = initial)
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }
        assertEquals(1, vm.uiState.value.comments.size)

        // Comment like fails -> should rollback
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"未登录"}"""))
        vm.toggleCommentLike(vm.uiState.value.comments.first())
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }
        assertFalse(vm.uiState.value.comments.first().isLiked)
        assertEquals(3, vm.uiState.value.comments.first().likeNum)

        // Vote succeeds -> increments option and total count
        server.enqueue(MockResponse().setBody("""{"data":1}"""))
        vm.submitVote(listOf("o1"))
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }
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
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }
        assertEquals(2, vm.uiState.value.items.size)

        vm.enterMultiSelectMode("f1")
        vm.toggleSelection("f2")

        // f1 succeeds, f2 fails on both /v6/feed/unFavorite and /v6/feed/unfavorite
        server.enqueue(MockResponse().setBody("""{"data":1}"""))
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"失败"}"""))
        server.enqueue(MockResponse().setBody("""{"status":-1,"message":"失败"}"""))

        var resultPair: Pair<Int, Int>? = null
        vm.batchDeleteSelected { ok, fail -> resultPair = ok to fail }
        repeat(10) {
            Thread.sleep(30)
            advanceUntilIdle()
        }

        assertEquals(1 to 1, resultPair)
        assertEquals(listOf("f2"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `SearchViewModel resets hasSearched when query cleared`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody("""{"data":["WearOS","酷安"]}"""))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))

        val vm = SearchViewModel(api)
        repeat(5) {
            Thread.sleep(20)
            advanceUntilIdle()
        }

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
}
