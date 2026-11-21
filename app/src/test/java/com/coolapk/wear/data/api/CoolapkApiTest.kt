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
    fun `toggleLike calls like and unlike endpoints with query params and attaches real ddid only on like`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":10}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":9}}"""))

        val likeResult = api.toggleLike("1001", like = true)
        assertTrue(likeResult.isSuccess)
        val req1 = server.takeRequest()
        assertEquals("POST", req1.method)
        assertEquals("/v6/feed/like?id=1001&detail=0", req1.path)
        assertEquals("trace=", req1.body.readUtf8())
        assertTrue(req1.getHeader("X-App-Token").orEmpty().startsWith("v2"))
        assertTrue(req1.getHeader("Cookie").orEmpty().contains("ddid=ddid-999"))

        val unlikeResult = api.toggleLike("1001", like = false)
        assertTrue(unlikeResult.isSuccess)
        val req2 = server.takeRequest()
        assertEquals("POST", req2.method)
        assertEquals("/v6/feed/unlike?id=1001&detail=0", req2.path)
        assertTrue(!req2.getHeader("Cookie").orEmpty().contains("ddid="))
    }

    @Test
    fun `toggleLike strips fake companion UUID ddid from Cookie to prevent risk-control error`() = runTest {
        val fakeUuidApi = CoolapkApi(
            deviceCodeProvider = { "test-device-code" },
            userAgent = "TestUA/1.0",
            credentialsProvider = {
                AuthCredentials(
                    cookie = "uid=42; token=abc123; ddid=550e8400-e29b-41d4-a716-446655440000",
                    ddid = "550e8400-e29b-41d4-a716-446655440000",
                    uid = "42"
                )
            },
            baseUrl = server.url("/").toString()
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":10}}"""))

        assertTrue(fakeUuidApi.toggleLike("1001", like = true).isSuccess)
        val req = server.takeRequest()
        val cookie = req.getHeader("Cookie").orEmpty()
        assertTrue(cookie.contains("uid=42"))
        assertTrue(cookie.contains("token=abc123"))
        assertTrue(!cookie.contains("ddid="))
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
    fun `unFavoriteFeed falls back from GET unFavorite to GET lowercase unfavorite`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":1}"""))

        val result = api.unFavoriteFeed("9001")
        assertTrue(result.isSuccess)
        val req1 = server.takeRequest()
        assertEquals("GET", req1.method)
        assertEquals("/v6/feed/unFavorite?id=9001", req1.path)
        val req2 = server.takeRequest()
        assertEquals("GET", req2.method)
        assertEquals("/v6/feed/unfavorite?id=9001", req2.path)
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
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"9001","fromusername":"A","note":"赞了你"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"count":"删除成功"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"m9"}}"""))

        val countRes = api.getNotificationCount()
        assertTrue(countRes.isSuccess)
        assertEquals(3, countRes.getOrThrow().totalUnread)
        assertEquals("/v6/notification/checkCount", server.takeRequest().path)

        val listRes = api.getNotifications("feedLikeList", page = 1)
        assertTrue(listRes.isSuccess)
        assertEquals(1, listRes.getOrThrow().size)
        assertEquals("/v6/notification/feedLikeList?page=1", server.takeRequest().path)

        val delRes = api.deleteNotification("9001")
        assertTrue(delRes.isSuccess)
        val delReq = server.takeRequest()
        assertEquals("POST", delReq.method)
        assertEquals("/v6/notification/delete?id=9001", delReq.path)
        assertEquals("id=9001", delReq.body.readUtf8())

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

    @Test
    fun `getDigitalFeeds and getSecondHandFeeds use valid v16 channel urls with fallback`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"entityType":"feed","id":"d1","uid":"1","username":"Tech","message":"数码动态"}]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"entityType":"feed","feedType":"ershou","id":"e1","uid":"2","username":"Seller","ershou_product_title":"手表","ershou_price":"999"}]}"""
            )
        )

        val digitalRes = api.getDigitalFeeds(page = 1)
        assertTrue(digitalRes.isSuccess)
        assertEquals(listOf("d1"), digitalRes.getOrThrow().map { it.id })
        assertTrue(server.takeRequest().path.orEmpty().contains("url=V10_DIGITAL_HOME"))
        assertTrue(server.takeRequest().path.orEmpty().contains("url=V10_CHANNEL_SJB"))

        val ershouRes = api.getSecondHandFeeds(page = 1)
        assertTrue(ershouRes.isSuccess)
        assertEquals(listOf("e1"), ershouRes.getOrThrow().map { it.id })
        assertTrue(server.takeRequest().path.orEmpty().contains("ershouList"))
    }

    @Test
    fun `getCollectionItems falls back to favorite list for default collection and resolveFeedIdFromReply reads fid`() = runTest {
        // default collection: /v6/collection/itemList empty -> /v6/collection/itemlist empty -> /v6/favorite/list returns feeds
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"entityType":"feed","id":"fav1","uid":"5","username":"FavAuthor","message":"默认收藏内容"}]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"id":"777888","fid":"909090"}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"entityType":"history","id":"row1","title":"酷友A的动态","description":"feedCover","url":"/feed/556677"}]}"""
            )
        )

        val colRes = api.getCollectionItems("1001", page = 1, isDefault = true)
        assertTrue(colRes.isSuccess)
        assertEquals(listOf("fav1"), colRes.getOrThrow().map { it.id })
        assertEquals("/v6/collection/itemList?id=1001&page=1", server.takeRequest().path)
        assertEquals("/v6/collection/itemlist?id=1001&page=1&listType=allFeedType", server.takeRequest().path)
        assertEquals("/v6/favorite/list?type=feed&page=1", server.takeRequest().path)

        val resolvedFeedId = api.resolveFeedIdFromReply("777888")
        assertTrue(resolvedFeedId.isSuccess)
        assertEquals("909090", resolvedFeedId.getOrThrow())
        assertEquals("/v6/feed/replyDetail?id=777888", server.takeRequest().path)

        val histRes = api.getCloudHitHistory(page = 1)
        assertTrue(histRes.isSuccess)
        assertEquals(listOf("556677"), histRes.getOrThrow().map { it.id })
        assertEquals("/v6/user/hitHistoryList?type=feed&page=1", server.takeRequest().path)
    }

    @Test
    fun `getFeedReplies and getCommentSubReplies send official APK v16 query parameters and cursors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"c1","uid":"1","username":"U1","message":"好帖"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"c2","uid":"2","username":"U2","message":"第二页"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"sub2","uid":"3","username":"U3","message":"楼中楼"}]}"""))

        assertTrue(api.getFeedReplies(feedId = "12345", page = 1).isSuccess)
        assertEquals(
            "/v6/feed/replyList?id=12345&listType=lastupdate_desc&page=1&discussMode=1&feedType=feed&blockStatus=0&fromFeedAuthor=0",
            server.takeRequest().path
        )

        assertTrue(
            api.getFeedReplies(
                feedId = "12345",
                page = 2,
                sortType = "popular",
                firstItem = "c1",
                lastItem = "c10"
            ).isSuccess
        )
        assertEquals(
            "/v6/feed/replyList?id=12345&listType=popular&page=2&firstItem=c1&lastItem=c10&discussMode=1&feedType=feed&blockStatus=0&fromFeedAuthor=0",
            server.takeRequest().path
        )

        assertTrue(api.getFeedReplies(feedId = "12345", page = 1, sortType = "author", feedType = "feedArticle").isSuccess)
        val authorReq = server.takeRequest()
        assertEquals(
            "/v6/feed/replyList?id=12345&listType=&page=1&discussMode=1&feedType=feedArticle&blockStatus=0&fromFeedAuthor=1",
            authorReq.path
        )
        assertEquals("16.6.4", authorReq.getHeader("X-App-Version"))
        assertEquals("2609291", authorReq.getHeader("X-App-Code"))
        assertEquals("2609291", authorReq.getHeader("X-App-Supported"))
        assertEquals("16", authorReq.getHeader("X-Api-Version"))
        assertEquals("application/x-www-form-urlencoded", authorReq.getHeader("Content-Type"))
        assertTrue(authorReq.getHeader("User-Agent").orEmpty().contains("+CoolMarket/16.6.4-2609291-universal"))
        assertTrue(authorReq.getHeader("X-App-Token").orEmpty().startsWith("v2"))

        assertTrue(api.getCommentSubReplies(commentId = "c1", page = 2, lastItem = "sub1").isSuccess)
        val subReq = server.takeRequest()
        assertEquals(
            "/v6/feed/replyList?id=c1&listType=&page=2&discussMode=0&feedType=feed_reply&blockStatus=0&fromFeedAuthor=0&lastItem=sub1",
            subReq.path
        )
        assertEquals("16.6.4", subReq.getHeader("X-App-Version"))
        assertEquals("2609291", subReq.getHeader("X-App-Code"))
    }

    @Test
    fun `replyList routes to api2 host and strips web SESSID cookies matching c001apk-Revived`() = runTest {
        val api2Server = MockWebServer()
        api2Server.start()
        try {
            val splitApi = CoolapkApi(
                deviceCodeProvider = { "test-device-code" },
                userAgent = "Dalvik/2.1.0 (Linux; U; Android 14; 23113RKC6C Build/UKQ1.230804.001) +CoolMarket/16.2.0-2604201-universal",
                credentialsProvider = {
                    AuthCredentials(
                        cookie = "SESSID=web-sess-123; forward=https://www.coolapk.com; uid=42; username=%E9%85%B7%E5%8F%8B; token=abc123; displayVersion=v14",
                        uid = "42",
                        username = "酷友"
                    )
                },
                baseUrl = server.url("/").toString(),
                api2BaseUrl = api2Server.url("/").toString()
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"id":"5001","uid":"1","username":"Author","message":"正文"}}"""))
            api2Server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[{"id":"c1","uid":"2","username":"U2","message":"评论正常"}]}"""))

            assertTrue(splitApi.getFeedDetail("5001").isSuccess)
            val detailReq = server.takeRequest()
            assertEquals("16.2.0", detailReq.getHeader("X-App-Version"))
            assertEquals("2604201", detailReq.getHeader("X-App-Code"))
            assertEquals("uid=42; username=%E9%85%B7%E5%8F%8B; token=abc123", detailReq.getHeader("Cookie"))
            assertTrue(detailReq.getHeader("User-Agent").orEmpty().endsWith("+CoolMarket/16.2.0-2604201-universal"))

            assertTrue(splitApi.getFeedReplies("5001", page = 1).isSuccess)
            val replyReq = api2Server.takeRequest()
            assertEquals("16.6.4", replyReq.getHeader("X-App-Version"))
            assertEquals("2609291", replyReq.getHeader("X-App-Code"))
            assertEquals("2609291", replyReq.getHeader("X-App-Supported"))
            assertEquals("uid=42; username=%E9%85%B7%E5%8F%8B; token=abc123", replyReq.getHeader("Cookie"))
            assertTrue(replyReq.getHeader("User-Agent").orEmpty().endsWith("+CoolMarket/16.6.4-2609291-universal"))
            assertTrue(replyReq.getHeader("X-App-Token").orEmpty().startsWith("v2"))
        } finally {
            api2Server.shutdown()
        }
    }

    @Test
    fun `getFeedDetail and getFeedReplies throw CoolapkCaptchaRequiredException and fallback loops stop immediately on captcha`() = runTest {
        val captchaBody = """{"code":403,"messageStatus":"err_request_captcha_v2","message":"需要验证码","messageExtra":"{\"captchaId\":\"cap987654\",\"captchaField\":\"_v2_post_token\"}"}"""
        server.enqueue(MockResponse().setResponseCode(403).setBody(captchaBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(captchaBody))
        server.enqueue(MockResponse().setResponseCode(403).setBody(captchaBody))

        val detailBlocked = api.getFeedDetail("5001")
        assertTrue(detailBlocked.isFailure)
        val detailEx = detailBlocked.exceptionOrNull() as? CoolapkCaptchaRequiredException
        assertTrue(detailEx != null)
        assertEquals("cap987654", detailEx!!.challenge.captchaId)
        server.takeRequest()

        val repliesBlocked = api.getFeedReplies("5001", page = 1)
        assertTrue(repliesBlocked.isFailure)
        assertTrue(repliesBlocked.exceptionOrNull() is CoolapkCaptchaRequiredException)
        server.takeRequest()

        // 多路兜底频道遇到验证码时应立即中止，绝不连续轰炸后续 4 个备用接口
        val countBefore = server.requestCount
        val digitalBlocked = api.getDigitalFeeds(page = 1)
        assertTrue(digitalBlocked.isFailure)
        assertTrue(digitalBlocked.exceptionOrNull() is CoolapkCaptchaRequiredException)
        assertEquals(countBefore + 1, server.requestCount)
    }

    @Test
    fun `CoolapkApi dynamically signs X-App-Token with updated X-App-Device at runtime`() = runTest {
        var currentDeviceCode = DeviceProfile.generateOfficialDeviceCode("DUFirstDeviceId1234567890abcdef")
        val dynamicApi = CoolapkApi(
            deviceCodeProvider = { currentDeviceCode },
            userAgent = "Dalvik/2.1.0 +CoolMarket/16.2.0-2604201-universal",
            credentialsProvider = { AuthCredentials(cookie = "uid=42; token=abc123", uid = "42") },
            baseUrl = server.url("/").toString()
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        assertTrue(dynamicApi.getFeedReplies("5001", page = 1).isSuccess)
        val req1 = server.takeRequest()
        val firstCode = req1.getHeader("X-App-Device").orEmpty()
        val firstToken = req1.getHeader("X-App-Token").orEmpty()

        currentDeviceCode = DeviceProfile.generateOfficialDeviceCode("DUSecondDeviceId9876543210fedcba")
        assertTrue(dynamicApi.getFeedReplies("5001", page = 1).isSuccess)
        val req2 = server.takeRequest()
        val secondCode = req2.getHeader("X-App-Device").orEmpty()
        val secondToken = req2.getHeader("X-App-Token").orEmpty()

        assertTrue(firstCode != secondCode)
        assertEquals(currentDeviceCode, secondCode)
        assertTrue(firstToken.startsWith("v2"))
        assertTrue(secondToken.startsWith("v2"))
        assertTrue(firstToken != secondToken)
    }
}
