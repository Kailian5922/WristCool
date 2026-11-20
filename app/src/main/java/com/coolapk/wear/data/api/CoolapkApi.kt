package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.AuthCredentials
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
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
 * 酷安 REST API 网络客户端。
 * 统一封装 OkHttp 响应关闭（避免连接泄漏）、协程取消穿透、酷安业务错误校验与多路降级策略。
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

    val currentUid: String
        get() = credentialsProvider().uid

    private fun buildRequest(path: String, queryParams: Map<String, String> = emptyMap()): Request.Builder {
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
            .header("X-Api-Version", "16")
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
        formParams: Map<String, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()
            for ((k, v) in formParams) {
                formBuilder.add(k, v)
            }
            val req = buildRequest(path).post(formBuilder.build()).build()
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

    suspend fun getTimelineFeeds(page: Int): Result<List<FeedItem>> {
        val endpoints = listOf(
            "/v6/page/dataList" to mapOf("url" to "V15_HOME_TAB_FOLLOW", "title" to "关注", "page" to page.toString()),
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

    private suspend fun executeFeedFallbackEndpoints(
        endpoints: List<Pair<String, Map<String, String>>>,
        page: Int,
        preferredIndex: Int,
        onPreferredIndexUpdated: (Int) -> Unit
    ): Result<List<FeedItem>> {
        val order = if (page > 1 && preferredIndex in endpoints.indices) {
            // 第 2 页起优先使用第 1 页成功命中的同一接口，避免分页混流
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
            mapOf("url" to "#/feed/pictureList", "title" to "酷图", "page" to page.toString())
        ).mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

    suspend fun getMyFavoriteFeeds(page: Int): Result<List<FeedItem>> {
        return executeGet("/v6/favorite/list", mapOf("page" to page.toString(), "type" to "feed"))
            .mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
    }

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

    /**
     * 点赞或取消点赞。修复原先无论状态都调用 `/v6/feed/like` 导致无法取消点赞的 Bug。
     */
    suspend fun toggleLike(feedId: String, like: Boolean): Result<Boolean> {
        val path = if (like) "/v6/feed/like" else "/v6/feed/unlike"
        return executePost(path, mapOf("id" to feedId, "detail" to "0")).map { true }
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

    suspend fun replyFeed(feedId: String, message: String): Result<String> {
        return executePost("/v6/feed/reply", mapOf("id" to feedId, "message" to message)).map { body ->
            try {
                val json = JSONObject(body)
                CoolapkJsonParser.optStringClean(json, "message").ifBlank { "回复成功" }
            } catch (_: Exception) {
                "回复成功"
            }
        }
    }

    suspend fun searchFeeds(query: String, page: Int): Result<List<FeedItem>> {
        return executeGet(
            "/v6/search",
            mapOf("type" to "feed", "searchValue" to query, "page" to page.toString())
        ).mapCatching { body -> parseAndCacheFeeds(body).getOrThrow() }
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

    suspend fun getTopicFeeds(tag: String, page: Int): Result<List<FeedItem>> {
        val cleanTag = tag.trim().removeSurrounding("#")
        val primary = executeGet(
            "/v6/topic/tagFeedList",
            mapOf("tag" to cleanTag, "page" to page.toString())
        )
        primary.onSuccess { body ->
            val parsed = parseAndCacheFeeds(body).getOrNull().orEmpty()
            if (parsed.isNotEmpty() || page > 1) {
                return Result.success(parsed)
            }
        }
        return searchFeeds("#$cleanTag#", page)
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
