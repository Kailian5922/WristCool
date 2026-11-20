package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.AppItem
import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.ChatMessage
import com.coolapk.wear.data.model.CollectionItem
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.data.model.MessageSession
import com.coolapk.wear.data.model.MyReplyItem
import com.coolapk.wear.data.model.NotificationCount
import com.coolapk.wear.data.model.NotificationItem
import com.coolapk.wear.data.model.ProductItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LoginCheckInfo(
    val uid: String,
    val username: String,
    val avatar: String
)

/**
 * 酷安 REST API v16 网络客户端。
 * 参考 coolapk-desktop 与 c001apk-Revived 完整实现动态流、回复/点赞/发布/转发/删除、
 * 投票、通知、私信、收藏单、数码产品、话题关注与实时搜索联想等手表高频接口。
 */
class CoolapkApi(
    private val deviceCodeProvider: () -> String,
    private val userAgent: String,
    private val credentialsProvider: () -> AuthCredentials,
    private val onUserInfoUpdated: (username: String, avatar: String, uid: String) -> Unit = { _, _, _ -> },
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val baseUrl: String = BASE_URL
) {
    constructor(
        deviceProfile: DeviceProfile,
        authRepository: AuthRepository,
        okHttpClient: OkHttpClient = defaultOkHttpClient()
    ) : this(
        deviceCodeProvider = { deviceProfile.deviceCode },
        userAgent = deviceProfile.userAgent,
        credentialsProvider = { authRepository.credentials.value },
        onUserInfoUpdated = { username, avatar, uid ->
            authRepository.updateUserInfo(username = username, avatar = avatar, uid = uid)
        },
        okHttpClient = okHttpClient
    )

    companion object {
        const val BASE_URL = "https://api.coolapk.com"
        private val HOT_FALLBACK_WORDS = listOf(
            "HyperOS", "OriginOS", "iOS", "手表", "玩机技巧", "摄影", "酷安", "折叠屏", "澎湃", "WearOS"
        )

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val auth by lazy { CoolapkAuth(deviceCodeProvider()) }

    @Volatile
    private var preferredHotIndex: Int = 0

    @Volatile
    private var preferredTimelineIndex: Int = 0

    @Volatile
    private var preferredDigitalIndex: Int = 0

    @Volatile
    private var preferredSecondHandIndex: Int = 0

    val currentUid: String
        get() = credentialsProvider().uid

    internal fun buildRequest(path: String, queryParams: Map<String, String> = emptyMap()): Request.Builder {
        val normalizedBase = baseUrl.trimEnd('/')
        val urlBuilder = "$normalizedBase$path".toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $normalizedBase$path")

        for ((key, value) in queryParams) {
            urlBuilder.addQueryParameter(key, value)
        }

        val deviceCode = deviceCodeProvider()
        val credentials = credentialsProvider()
        val token = auth.getAppToken()

        val builder = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", userAgent)
            .header("X-App-Id", "com.coolapk.market")
            .header("X-App-Version", "16.2.0")
            .header("X-App-Code", "2604201")
            .header("X-App-Supported", "2604201")
            .header("X-Api-Version", "16")
            .header("X-App-Mode", "universal")
            .header("X-App-Channel", "coolapk")
            .header("X-Sdk-Int", "34")
            .header("X-Sdk-Locale", "zh-CN")
            .header("X-App-Device", deviceCode)
            .header("X-App-Token", token)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Dark-Mode", "1")

        if (credentials.isLoggedIn) {
            var cookie = credentials.cookie
            if (credentials.ddid.isNotBlank() && !cookie.contains("ddid=", ignoreCase = true)) {
                cookie = "$cookie; ddid=${credentials.ddid}"
            }
            builder.header("Cookie", cookie)
        }

        return builder
    }

    private suspend fun executeGet(
        path: String,
        queryParams: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(path, queryParams).build()
            okHttpClient.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMsg = CoolapkJsonParser.parseApiError(body)
                    return@withContext Result.failure(
                        IOException(serverMsg ?: "HTTP ${response.code}")
                    )
                }
                Result.success(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executePost(
        path: String,
        formParams: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()
            for ((k, v) in formParams) {
                formBuilder.add(k, v)
            }
            val req = buildRequest(path, queryParams).post(formBuilder.build()).build()
            okHttpClient.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMsg = CoolapkJsonParser.parseApiError(body)
                    return@withContext Result.failure(
                        IOException(serverMsg ?: "HTTP ${response.code}")
                    )
                }
                CoolapkJsonParser.parseApiError(body)?.let { apiError ->
                    return@withContext Result.failure(IOException(apiError))
                }
                Result.success(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeMultipartPost(
        path: String,
        parts: Map<String, String>,
        queryParams: Map<String, String> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            for ((k, v) in parts) {
                bodyBuilder.addFormDataPart(k, v)
            }
            val req = buildRequest(path, queryParams).post(bodyBuilder.build()).build()
            okHttpClient.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMsg = CoolapkJsonParser.parseApiError(body)
                    return@withContext Result.failure(
                        IOException(serverMsg ?: "HTTP ${response.code}")
                    )
                }
                CoolapkJsonParser.parseApiError(body)?.let { apiError ->
                    return@withContext Result.failure(IOException(apiError))
                }
                Result.success(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAndCacheFeeds(body: String): Result<List<FeedItem>> {
        CoolapkJsonParser.parseApiError(body)?.let { apiError ->
            return Result.failure(IOException(apiError))
        }
        val list = CoolapkJsonParser.parseFeedList(body)
        FeedMemoryCache.putAll(list)
        return Result.success(list)
    }

    // ==================== 首页与频道动态流 ====================

    suspend fun getHeadlineFeeds(page: Int): Result<List<FeedItem>> {
        val primary = executeGet("/v6/main/headline", mapOf("page" to page.toString()))
        primary.onSuccess { body ->
            val parsed = parseAndCacheFeeds(body).getOrNull().orEmpty()
            if (parsed.isNotEmpty() || page > 1) {
                return Result.success(parsed)
            }
        }
        return getRecommendFeeds(page)
    }

    suspend fun getRecommendFeeds(page: Int): Result<List<FeedItem>> {
        return executeGet("/v6/main/indexV8", mapOf("page" to page.toString()))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getHotFeeds(page: Int): Result<List<FeedItem>> {
        val endpoints = listOf(
            "/v6/page/dataList" to mapOf("url" to "V9_HOME_TAB_RANKING", "title" to "热榜", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "#/feed/statHotList?period=24h", "title" to "热门", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "#/feed/statList?statType=7days&sortField=likenum", "title" to "热榜", "page" to page.toString())
        )
        return executeFeedFallbackEndpoints(
            endpoints = endpoints,
            page = page,
            preferredIndex = preferredHotIndex,
            onPreferredIndexUpdated = { preferredHotIndex = it }
        )
    }

    /**
     * 关注流支持分组筛选（全部 / circle / topic / product / apk，参考 c001apk-Revived 与 coolapk-desktop）。
     */
    suspend fun getTimelineFeeds(page: Int, group: String = ""): Result<List<FeedItem>> {
        val cleanGroup = group.trim()
        if (cleanGroup.isNotEmpty() && cleanGroup != "all") {
            val grouped = executeGet(
                "/v6/page/dataList",
                mapOf(
                    "url" to "V9_HOME_TAB_FOLLOW",
                    "type" to cleanGroup,
                    "title" to "关注",
                    "page" to page.toString()
                )
            )
            grouped.onSuccess { body ->
                val parsed = parseAndCacheFeeds(body)
                if (parsed.isSuccess) return parsed
            }
        }
        val endpoints = listOf(
            "/v6/page/dataList" to mapOf("url" to "V15_HOME_TAB_FOLLOW", "title" to "关注", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "V9_HOME_TAB_FOLLOW", "title" to "关注", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "/user/followFeedList", "title" to "关注", "page" to page.toString()),
            "/v6/feed/followFeedList" to mapOf("page" to page.toString()),
            "/v6/main/indexV8" to mapOf("type" to "follow", "page" to page.toString())
        )
        return executeFeedFallbackEndpoints(
            endpoints = endpoints,
            page = page,
            preferredIndex = preferredTimelineIndex,
            onPreferredIndexUpdated = { preferredTimelineIndex = it }
        )
    }

    suspend fun getPictureFeeds(page: Int): Result<List<FeedItem>> {
        val primary = executeGet("/v6/picture/list", mapOf("page" to page.toString()))
        primary.onSuccess { body ->
            val parsed = parseAndCacheFeeds(body).getOrNull().orEmpty()
            if (parsed.isNotEmpty() || page > 1) {
                return Result.success(parsed)
            }
        }
        return executeGet(
            "/v6/page/dataList",
            mapOf("url" to "V11_FIND_COOLPIC", "title" to "酷图", "page" to page.toString())
        ).mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getDigitalFeeds(page: Int): Result<List<FeedItem>> {
        val endpoints = listOf(
            "/v6/page/dataList" to mapOf("url" to "V11_FIND_PRODUCT", "title" to "数码", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "#/feed/productFeedList", "title" to "数码", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "V9_HOME_TAB_DIGITAL", "title" to "数码", "page" to page.toString())
        )
        return executeFeedFallbackEndpoints(
            endpoints = endpoints,
            page = page,
            preferredIndex = preferredDigitalIndex,
            onPreferredIndexUpdated = { preferredDigitalIndex = it }
        )
    }

    suspend fun getSecondHandFeeds(page: Int): Result<List<FeedItem>> {
        val endpoints = listOf(
            "/v6/page/dataList" to mapOf("url" to "V11_FIND_ERSHOU", "title" to "二手", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "#/feed/erShouList", "title" to "二手", "page" to page.toString()),
            "/v6/page/dataList" to mapOf("url" to "/feed/erShouList", "title" to "二手", "page" to page.toString())
        )
        return executeFeedFallbackEndpoints(
            endpoints = endpoints,
            page = page,
            preferredIndex = preferredSecondHandIndex,
            onPreferredIndexUpdated = { preferredSecondHandIndex = it }
        )
    }

    private suspend fun executeFeedFallbackEndpoints(
        endpoints: List<Pair<String, Map<String, String>>>,
        page: Int,
        preferredIndex: Int,
        onPreferredIndexUpdated: (Int) -> Unit
    ): Result<List<FeedItem>> {
        val order = if (page > 1 && preferredIndex in endpoints.indices) {
            listOf(preferredIndex)
        } else {
            val start = preferredIndex.coerceIn(0, endpoints.lastIndex)
            (start until endpoints.size) + (0 until start)
        }

        var lastError: Throwable? = null
        var hadEmptySuccess = false
        for (idx in order) {
            val (path, params) = endpoints[idx]
            val result = executeGet(path, params)
            result.onSuccess { body ->
                val parsedResult = parseAndCacheFeeds(body)
                parsedResult.onSuccess { list ->
                    if (list.isNotEmpty()) {
                        onPreferredIndexUpdated(idx)
                        return Result.success(list)
                    } else {
                        hadEmptySuccess = true
                    }
                }.onFailure { err ->
                    lastError = err
                }
            }.onFailure { err ->
                lastError = err
            }
        }
        return if (hadEmptySuccess) {
            Result.success(emptyList())
        } else {
            Result.failure(lastError ?: IOException("加载动态失败"))
        }
    }

    // ==================== 动态详情、评论、发布、转发、投票与互动 ====================

    suspend fun getFeedDetail(feedId: String): Result<FeedItem> {
        return executeGet("/v6/feed/detail", mapOf("id" to feedId)).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
                ?: throw IOException(
                    CoolapkJsonParser.optStringClean(json, "message", "error").ifBlank { "动态不存在或已被删除" }
                )
            val item = CoolapkJsonParser.parseSingleFeed(data)
                ?: throw IOException("解析动态内容失败")
            FeedMemoryCache.put(item)
            item
        }
    }

    suspend fun getFeedReplies(
        feedId: String,
        page: Int,
        sortType: String = ""
    ): Result<List<CommentItem>> {
        val params = mutableMapOf(
            "id" to feedId,
            "page" to page.toString(),
            "listType" to if (sortType == "author") "" else sortType
        )
        if (sortType == "author") {
            params["fromFeedAuthor"] = "1"
            params["isAuthor"] = "1"
        }
        return executeGet("/v6/feed/replyList", params).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseCommentList(body)
        }
    }

    suspend fun getCommentSubReplies(
        commentId: String,
        page: Int = 1
    ): Result<List<SubReplyItem>> {
        val params = mapOf(
            "id" to commentId,
            "page" to page.toString(),
            "listType" to "",
            "discussMode" to "0",
            "feedType" to "feed_reply"
        )
        return executeGet("/v6/feed/replyList", params).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseSubReplyList(body)
        }
    }

    suspend fun toggleLike(feedId: String, like: Boolean): Result<Boolean> {
        val path = if (like) "/v6/feed/like" else "/v6/feed/unlike"
        return executePost(path, mapOf("id" to feedId, "detail" to "0")).map { true }
    }

    /**
     * 点赞/取消点赞单条评论或楼中楼（对应 APK /v6/feed/likeReply & /v6/feed/unLikeReply）。
     */
    suspend fun toggleReplyLike(replyId: String, like: Boolean): Result<Boolean> {
        val path = if (like) "/v6/feed/likeReply" else "/v6/feed/unLikeReply"
        return executePost(
            path = path,
            formParams = mapOf("trace" to ""),
            queryParams = mapOf("id" to replyId, "detail" to "0")
        ).map { true }
    }

    suspend fun toggleFavorite(feedId: String, favorite: Boolean): Result<Boolean> {
        return if (favorite) {
            executePost("/v6/feed/favorite", mapOf("id" to feedId, "targetType" to "feed")).map { true }
        } else {
            unFavoriteFeed(feedId)
        }
    }

    suspend fun unFavoriteFeed(feedId: String): Result<Boolean> {
        val params = mapOf("id" to feedId, "targetType" to "feed")
        val primary = executePost("/v6/feed/unFavorite", params)
        if (primary.isSuccess) return Result.success(true)
        return executePost("/v6/feed/unfavorite", params).map { true }
    }

    /**
     * 回复动态或回复指定评论/楼中楼。
     * 按酷安官方 APK 契约：回复评论时 `id=replyId&type=reply`，直接回复动态时 `id=feedId&type=feed`。
     */
    suspend fun replyFeed(
        feedId: String,
        message: String,
        replyId: String? = null
    ): Result<String> {
        val cleanReplyId = replyId?.trim().orEmpty()
        val targetId = cleanReplyId.ifEmpty { feedId }
        val replyType = if (cleanReplyId.isNotEmpty()) "reply" else "feed"
        return executePost(
            path = "/v6/feed/reply",
            formParams = mapOf("message" to message),
            queryParams = mapOf("id" to targetId, "type" to replyType)
        ).map { body ->
            try {
                val json = JSONObject(body)
                CoolapkJsonParser.optStringClean(json, "message").ifBlank { "回复成功" }
            } catch (_: Exception) {
                "回复成功"
            }
        }
    }

    /**
     * 发布短动态（支持仅自己可见与自动附加 #话题#，参考 coolapk-desktop 与 c001apk-Revived）。
     */
    suspend fun createFeed(
        message: String,
        isPrivate: Boolean = false
    ): Result<String> {
        val form = buildCreateFeedForm(
            message = message,
            publishStatus = if (isPrivate) "1" else "0",
            forwardId = ""
        )
        return executePost("/v6/feed/createFeed", formParams = form).map { body ->
            try {
                val json = JSONObject(body)
                CoolapkJsonParser.optStringClean(json, "message").ifBlank { "动态发布成功" }
            } catch (_: Exception) {
                "动态发布成功"
            }
        }
    }

    /**
     * 转发动态（通过 /v6/feed/createFeed 携带 forwardid 字段）。
     */
    suspend fun forwardFeed(
        feedId: String,
        message: String
    ): Result<String> {
        val form = buildCreateFeedForm(
            message = message.ifBlank { "转发动态" },
            publishStatus = "0",
            forwardId = feedId
        )
        return executePost("/v6/feed/createFeed", formParams = form).map { body ->
            try {
                val json = JSONObject(body)
                CoolapkJsonParser.optStringClean(json, "message").ifBlank { "转发成功" }
            } catch (_: Exception) {
                "转发成功"
            }
        }
    }

    private fun buildCreateFeedForm(
        message: String,
        publishStatus: String,
        forwardId: String
    ): Map<String, String> = linkedMapOf(
        "id" to "",
        "message" to message,
        "type" to "feed",
        "pic" to "",
        "status" to "1",
        "publish_status" to publishStatus,
        "location" to "",
        "long_location" to "",
        "latitude" to "0.0",
        "longitude" to "0.0",
        "media_url" to "",
        "media_type" to "0",
        "media_pic" to "",
        "message_title" to "",
        "message_brief" to "",
        "extra_title" to "",
        "extra_url" to "",
        "extra_key" to "",
        "extra_pic" to "",
        "extra_info" to "",
        "message_cover" to "",
        "original_type" to "0",
        "is_editInDyh" to "0",
        "forwardid" to forwardId,
        "fid" to "",
        "dyhId" to "",
        "targetType" to "",
        "productId" to "",
        "targetId" to "",
        "location_city" to "",
        "location_country" to "",
        "disallow_reply" to "0",
        "vote_score" to "0",
        "replyWithForward" to "0",
        "media_info" to "",
        "insert_product_media" to "0",
        "is_ks_doc" to "0",
        "goods_list_id" to "",
        "is_html_article" to "0"
    )

    suspend fun deleteFeed(feedId: String): Result<Boolean> {
        return executePost("/v6/feed/deleteFeed", queryParams = mapOf("id" to feedId)).map { true }
    }

    suspend fun deleteReply(replyId: String): Result<Boolean> {
        return executePost("/v6/feed/deleteReply", queryParams = mapOf("id" to replyId)).map { true }
    }

    /**
     * 提交投票（POST /v6/vote/createUserVote）。
     */
    suspend fun submitVote(
        feedId: String,
        optionIds: List<String>,
        anonymous: Boolean = false
    ): Result<Boolean> {
        val form = linkedMapOf(
            "id" to feedId,
            "anonymous_status" to if (anonymous) "1" else "0"
        )
        optionIds.forEachIndexed { idx, optId ->
            form["select_option[$idx]"] = optId
        }
        return executePost("/v6/vote/createUserVote", formParams = form).map { true }
    }

    // ==================== 通知与私信（高频穿戴功能） ====================

    suspend fun getNotificationCount(): Result<NotificationCount> {
        return executeGet("/v6/notification/checkCount").mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseNotificationCount(body)
        }
    }

    suspend fun getNotifications(type: String, page: Int): Result<List<NotificationItem>> {
        val safeType = when (type) {
            "list", "atMeList", "atCommentMeList", "feedLikeList", "contactsFollowList" -> type
            else -> "list"
        }
        return executeGet("/v6/notification/$safeType", mapOf("page" to page.toString())).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseNotificationList(body)
        }
    }

    suspend fun clearNotificationCount(type: String): Result<Boolean> {
        return executePost("/v6/notification/clearCount", queryParams = mapOf("type" to type)).map { true }
    }

    suspend fun deleteNotification(notificationId: String): Result<Boolean> {
        return executePost("/v6/notification/delete", formParams = mapOf("id" to notificationId)).map { true }
    }

    suspend fun getMessageSessions(page: Int): Result<List<MessageSession>> {
        return executeGet("/v6/message/list", mapOf("page" to page.toString())).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseMessageSessionList(body, selfUid = currentUid)
        }
    }

    suspend fun getChatMessages(ukey: String, page: Int): Result<List<ChatMessage>> {
        return executeGet("/v6/message/chat", mapOf("ukey" to ukey, "page" to page.toString())).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseChatMessageList(body, selfUid = currentUid)
        }
    }

    suspend fun markMessageRead(ukey: String): Result<Boolean> {
        return executeGet("/v6/message/read", mapOf("ukey" to ukey)).map { true }
    }

    suspend fun sendPrivateMessage(uid: String, message: String): Result<Boolean> {
        return executeMultipartPost(
            path = "/v6/message/send",
            parts = linkedMapOf(
                "message" to message,
                "message_pic" to "",
                "message_extra" to ""
            ),
            queryParams = mapOf("uid" to uid, "quick_reply" to "1")
        ).map { true }
    }

    suspend fun deleteMessageChat(ukey: String): Result<Boolean> {
        return executeGet("/v6/message/deleteChat", mapOf("ukey" to ukey)).map { true }
    }

    // ==================== 收藏、收藏单、点赞、回复与云端历史 ====================

    suspend fun getMyFavoriteFeeds(page: Int): Result<List<FeedItem>> {
        return executeGet("/v6/favorite/list", mapOf("page" to page.toString(), "type" to "feed"))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getUserCollections(uid: String, page: Int): Result<List<CollectionItem>> {
        val targetUid = uid.ifBlank { currentUid }
        return executeGet(
            "/v6/collection/list",
            mapOf("uid" to targetUid, "showDefault" to "1", "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseCollectionList(body)
        }
    }

    suspend fun getCollectionItems(collectionId: String, page: Int): Result<List<FeedItem>> {
        return executeGet(
            "/v6/collection/itemlist",
            mapOf("id" to collectionId, "page" to page.toString(), "listType" to "allFeedType")
        ).mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getUserLikes(uid: String, page: Int): Result<List<FeedItem>> {
        val targetUid = uid.ifBlank { currentUid }
        return executeGet("/v6/user/likeList", mapOf("uid" to targetUid, "page" to page.toString()))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getUserReplies(uid: String, page: Int): Result<List<MyReplyItem>> {
        val targetUid = uid.ifBlank { currentUid }
        return executeGet("/v6/user/replyList", mapOf("uid" to targetUid, "page" to page.toString()))
            .mapCatching { body ->
                CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
                CoolapkJsonParser.parseMyReplyList(body)
            }
    }

    suspend fun getCloudHitHistory(page: Int): Result<List<FeedItem>> {
        return executeGet("/v6/user/hitHistoryList", mapOf("type" to "feed", "page" to page.toString()))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    // ==================== 搜索（动态/作者/话题/数码/应用 + 实时联想） ====================

    suspend fun searchFeeds(query: String, page: Int, sort: String = "default"): Result<List<FeedItem>> {
        val params = mutableMapOf(
            "type" to "feed",
            "searchValue" to query,
            "page" to page.toString()
        )
        if (sort.isNotBlank() && sort != "default") {
            params["sort"] = sort
        }
        return executeGet("/v6/search", params)
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun searchUsers(query: String, page: Int): Result<List<UserCardItem>> {
        return executeGet(
            "/v6/search",
            mapOf("type" to "user", "searchValue" to query, "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseUserList(body, isFollowList = false)
        }
    }

    suspend fun searchTopics(query: String, page: Int): Result<List<TopicItem>> {
        return executeGet(
            "/v6/search",
            mapOf("type" to "feedTopic", "searchValue" to query, "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseTopicList(body)
        }
    }

    suspend fun searchProducts(query: String, page: Int): Result<List<ProductItem>> {
        return executeGet(
            "/v6/search",
            mapOf("type" to "product", "searchValue" to query, "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseProductList(body)
        }
    }

    suspend fun searchApps(query: String, page: Int): Result<List<AppItem>> {
        return executeGet(
            "/v6/search",
            mapOf("type" to "apk", "searchValue" to query, "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseAppList(body)
        }
    }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> {
        if (query.isBlank()) return Result.success(emptyList())
        return executeGet(
            "/v6/search/suggestSearchWordsNew",
            mapOf("searchValue" to query, "type" to "app")
        ).mapCatching { body ->
            CoolapkJsonParser.parseHotSearchWords(body)
        }
    }

    suspend fun getHotSearchWords(): Result<List<String>> {
        val response = executeGet("/v6/search/hotSearchWords")
        response.onSuccess { body ->
            val words = runCatching { CoolapkJsonParser.parseHotSearchWords(body) }.getOrDefault(emptyList())
            if (words.isNotEmpty()) {
                return Result.success(words)
            }
        }
        return Result.success(HOT_FALLBACK_WORDS)
    }

    // ==================== 用户、话题与数码产品 ====================

    suspend fun getUserProfile(uid: String): Result<UserProfile> {
        return executeGet("/v6/user/profile", mapOf("uid" to uid)).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: throw IOException("未获取到用户资料")
            CoolapkJsonParser.parseUserProfile(data, currentUid)
        }
    }

    suspend fun getUserFeeds(uid: String, page: Int): Result<List<FeedItem>> {
        return executeGet(
            "/v6/user/feedList",
            mapOf("uid" to uid, "page" to page.toString(), "isIncludeTop" to "1")
        ).mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun toggleFollowUser(uid: String, follow: Boolean): Result<Boolean> {
        val path = if (follow) "/v6/user/follow" else "/v6/user/unfollow"
        return executePost(path, mapOf("uid" to uid)).map { true }
    }

    suspend fun setSpecialFollowUser(uid: String, special: Boolean): Result<Boolean> {
        return executePost(
            "/v6/user/specialFollowUser",
            queryParams = mapOf("uid" to uid, "special" to if (special) "1" else "0")
        ).map { true }
    }

    suspend fun getUserFollowList(uid: String, page: Int): Result<List<UserCardItem>> {
        return executeGet(
            "/v6/user/followList",
            mapOf("uid" to uid, "page" to page.toString())
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseUserList(body, isFollowList = true, selfUid = uid)
        }
    }

    suspend fun getUserFansList(uid: String, page: Int): Result<List<UserCardItem>> {
        return executeGet(
            "/v6/user/fansList",
            mapOf("uid" to uid, "page" to page.toString(), "isIncludeTop" to "1")
        ).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseUserList(body, isFollowList = false, selfUid = uid)
        }
    }

    suspend fun getTopicDetail(tag: String): Result<TopicItem> {
        val cleanTag = tag.trim().removeSurrounding("#")
        return executeGet("/v6/topic/newTagDetail", mapOf("tag" to cleanTag)).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseTopicDetail(body) ?: TopicItem(id = cleanTag, title = cleanTag)
        }
    }

    suspend fun toggleFollowTopic(tag: String, follow: Boolean): Result<Boolean> {
        val cleanTag = tag.trim().removeSurrounding("#")
        val path = if (follow) "/v6/feed/followTag" else "/v6/feed/unFollowTag"
        return executeGet(path, mapOf("tag" to cleanTag)).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            true
        }
    }

    suspend fun getTopicFeeds(tag: String, page: Int, listType: String = ""): Result<List<FeedItem>> {
        val cleanTag = tag.trim().removeSurrounding("#")
        val params = mutableMapOf("tag" to cleanTag, "page" to page.toString())
        if (listType.isNotBlank()) {
            params["listType"] = listType
        }
        val primary = executeGet("/v6/topic/tagFeedList", params)
        primary.onSuccess { body ->
            val parsed = parseAndCacheFeeds(body).getOrNull().orEmpty()
            if (parsed.isNotEmpty() || page > 1) {
                return Result.success(parsed)
            }
        }
        return searchFeeds("#$cleanTag#", page)
    }

    suspend fun getProductDetail(productId: String): Result<ProductItem> {
        return executeGet("/v6/product/detail", mapOf("id" to productId)).mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            CoolapkJsonParser.parseProductDetail(body) ?: throw IOException("未找到该数码产品信息")
        }
    }

    suspend fun getProductFeeds(productId: String, page: Int): Result<List<FeedItem>> {
        val primary = executeGet(
            "/v6/page/dataList",
            mapOf(
                "url" to "/page?url=/product/feedList",
                "id" to productId,
                "type" to "feed",
                "page" to page.toString()
            )
        )
        primary.onSuccess { body ->
            val parsed = parseAndCacheFeeds(body).getOrNull().orEmpty()
            if (parsed.isNotEmpty() || page > 1) {
                return Result.success(parsed)
            }
        }
        return executeGet("/v6/product/feedList", mapOf("id" to productId, "page" to page.toString()))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun toggleFollowProduct(productId: String, follow: Boolean): Result<Boolean> {
        return executePost(
            "/v6/product/changeFollowStatus",
            queryParams = mapOf("id" to productId, "status" to if (follow) "1" else "0")
        ).map { true }
    }

    suspend fun checkLoginInfo(): Result<LoginCheckInfo> {
        return executeGet("/v6/account/checkLoginInfo").mapCatching { body ->
            CoolapkJsonParser.parseApiError(body)?.let { throw IOException(it) }
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: throw IOException("未登录或 Token 失效")
            val username = CoolapkJsonParser.optStringClean(data, "username", "displayUserName")
            val avatar = CoolapkJsonParser.optStringClean(data, "userAvatar", "avatar")
            val uid = CoolapkJsonParser.optStringClean(data, "uid")
            if (username.isEmpty()) {
                throw IOException("未登录或 Token 失效")
            }
            onUserInfoUpdated(username, avatar, uid)
            LoginCheckInfo(uid = uid, username = username, avatar = avatar)
        }
    }
}
