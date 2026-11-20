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

    @Test
    fun `replyFeed sets type=feed for feed reply and type=reply for comment reply`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"r1"},"message":"ok"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"r2"},"message":"ok"}"""))

        assertTrue(api.replyFeed(feedId = "100", message = "顶帖").isSuccess)
        val reqFeed = server.takeRequest()
        assertEquals("/v6/feed/reply?id=100&type=feed", reqFeed.path)
        assertTrue(reqFeed.body.readUtf8().contains("message="))

        assertTrue(api.replyFeed(feedId = "100", message = "赞同", replyId = "888").isSuccess)
        val reqReply = server.takeRequest()
        assertEquals("/v6/feed/reply?id=888&type=reply", reqReply.path)
    }

    @Test
    fun `toggleReplyLike calls likeReply and unLikeReply with detail=0`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":5}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":4}"""))

        assertTrue(api.toggleReplyLike("505", like = true).isSuccess)
        val req1 = server.takeRequest()
        assertEquals("/v6/feed/likeReply?id=505&detail=0", req1.path)

        assertTrue(api.toggleReplyLike("505", like = false).isSuccess)
        val req2 = server.takeRequest()
        assertEquals("/v6/feed/unLikeReply?id=505&detail=0", req2.path)
    }

    @Test
    fun `createFeed forwardFeed deleteFeed deleteReply and submitVote send expected form fields`() = runTest {
        repeat(5) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":1}"""))
        }

        assertTrue(api.createFeed("手表发帖测试", isPrivate = true).isSuccess)
        val reqCreate = server.takeRequest()
        assertEquals("/v6/feed/createFeed", reqCreate.path)
        val createBody = reqCreate.body.readUtf8()
        assertTrue(createBody.contains("publish_status=1"))
        assertTrue(createBody.contains("forwardid="))

        assertTrue(api.forwardFeed("777", "转发一下").isSuccess)
        val reqForward = server.takeRequest()
        assertEquals("/v6/feed/createFeed", reqForward.path)
        assertTrue(reqForward.body.readUtf8().contains("forwardid=777"))

        assertTrue(api.deleteFeed("777").isSuccess)
        assertEquals("/v6/feed/deleteFeed?id=777", server.takeRequest().path)

        assertTrue(api.deleteReply("888").isSuccess)
        assertEquals("/v6/feed/deleteReply?id=888", server.takeRequest().path)

        assertTrue(api.submitVote("999", listOf("opt1", "opt2")).isSuccess)
        val reqVote = server.takeRequest()
        assertEquals("/v6/vote/createUserVote", reqVote.path)
        val voteBody = reqVote.body.readUtf8()
        assertTrue(voteBody.contains("id=999"))
        assertTrue(voteBody.contains("select_option%5B0%5D=opt1"))
        assertTrue(voteBody.contains("select_option%5B1%5D=opt2"))
    }

    @Test
    fun `notifications and sendPrivateMessage use expected Coolapk v16 endpoints and multipart body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"commentme":2,"message":1}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"n1","fromusername":"A","note":"赞了你"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"m9"}}"""))

        val countRes = api.getNotificationCount()
        assertTrue(countRes.isSuccess)
        assertEquals(3, countRes.getOrThrow().totalUnread)
        assertEquals("/v6/notification/checkCount", server.takeRequest().path)

        val listRes = api.getNotifications("feedLikeList", page = 1)
        assertTrue(listRes.isSuccess)
        assertEquals(1, listRes.getOrThrow().size)
        assertEquals("/v6/notification/feedLikeList?page=1", server.takeRequest().path)

        val pmRes = api.sendPrivateMessage("888", "手表私信")
        assertTrue(pmRes.isSuccess)
        val pmReq = server.takeRequest()
        assertEquals("/v6/message/send?uid=888&quick_reply=1", pmReq.path)
        assertTrue(pmReq.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val pmBody = pmReq.body.readUtf8()
        assertTrue(pmBody.contains("name=\"message\""))
        assertTrue(pmBody.contains("手表私信"))
    }

    @Test
    fun `topic follow and product detail and search endpoints work`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":1}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"p1","title":"Watch Ultra"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"p1","title":"Watch Ultra"}]}"""))

        assertTrue(api.toggleFollowTopic("#WearOS#", follow = true).isSuccess)
        assertEquals("/v6/feed/followTag?tag=WearOS", server.takeRequest().path)

        val detail = api.getProductDetail("p1")
        assertTrue(detail.isSuccess)
        assertEquals("Watch Ultra", detail.getOrThrow().title)
        assertEquals("/v6/product/detail?id=p1", server.takeRequest().path)

        val searchRes = api.searchProducts("Watch", page = 1)
        assertTrue(searchRes.isSuccess)
        assertEquals(1, searchRes.getOrThrow().size)
        assertEquals("/v6/search?type=product&searchValue=Watch&page=1", server.takeRequest().path)
    }
}
