package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.AuthCredentials
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoolapkApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CoolapkApi
    private var lastUpdatedUser: Triple<String, String, String>? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = CoolapkApi(
            deviceCodeProvider = { "test-device-code" },
            userAgent = "TestUA/1.0",
            credentialsProvider = {
                AuthCredentials(
                    cookie = "uid=42; token=abc123",
                    ddid = "ddid-999",
                    uid = "42"
                )
            },
            onUserInfoUpdated = { username, avatar, uid ->
                lastUpdatedUser = Triple(username, avatar, uid)
            },
            baseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `toggleLike calls like and unlike endpoints respectively`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":10}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":9}}"""))

        val likeResult = api.toggleLike("1001", like = true)
        assertTrue(likeResult.isSuccess)
        val req1 = server.takeRequest()
        assertEquals("/v6/feed/like", req1.path)
        assertTrue(req1.getHeader("X-App-Token").orEmpty().startsWith("v3"))
        assertTrue(req1.getHeader("Cookie").orEmpty().contains("ddid=ddid-999"))

        val unlikeResult = api.toggleLike("1001", like = false)
        assertTrue(unlikeResult.isSuccess)
        val req2 = server.takeRequest()
        assertEquals("/v6/feed/unlike", req2.path)
    }

    @Test
    fun `mutation returns failure when Coolapk returns HTTP 200 with negative status`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": -1, "message": "请先登录"}""")
        )

        val result = api.toggleLike("1001", like = true)
        assertTrue(result.isFailure)
        assertEquals("请先登录", result.exceptionOrNull()?.message)
    }

    @Test
    fun `unFavoriteFeed falls back from unFavorite to lowercase unfavorite`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":1}"""))

        val result = api.unFavoriteFeed("9001")
        assertTrue(result.isSuccess)
        assertEquals("/v6/feed/unFavorite", server.takeRequest().path)
        assertEquals("/v6/feed/unfavorite", server.takeRequest().path)
    }

    @Test
    fun `getHeadlineFeeds falls back to indexV8 on page 1 when headline is empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"entityType":"feed","id":"300","username":"U","uid":"1","message":"Rec"}]}"""
            )
        )

        val result = api.getHeadlineFeeds(page = 1)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("/v6/main/headline?page=1", server.takeRequest().path)
        assertEquals("/v6/main/indexV8?page=1", server.takeRequest().path)
    }

    @Test
    fun `checkLoginInfo parses uid username avatar and invokes callback`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"uid":"42","username":"CoolUser","userAvatar":"http://img.coolapk.com/u.png"}}"""
            )
        )

        val result = api.checkLoginInfo()
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals("42", info.uid)
        assertEquals("CoolUser", info.username)
        assertEquals(Triple("CoolUser", "http://img.coolapk.com/u.png", "42"), lastUpdatedUser)
    }
}
