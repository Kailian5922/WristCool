package com.coolapk.wear.presentation

import com.coolapk.wear.data.api.CoolapkApi
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.presentation.detail.FeedDetailViewModel
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
}
