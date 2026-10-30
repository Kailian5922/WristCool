package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.FeedMemoryCache
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CoolapkApi(
    private val deviceProfile: DeviceProfile,
    private val authRepository: AuthRepository
) {
    companion object {
        const val BASE_URL = "https://api.coolapk.com"
    }

    private val auth = CoolapkAuth(deviceProfile.deviceCode)

    val currentUid: String
        get() = authRepository.credentials.value.uid

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(path: String, queryParams: Map<String, String> = emptyMap()): Request.Builder {
        val urlBuilder = "$BASE_URL$path".toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $BASE_URL$path")

        for ((key, value) in queryParams) {
            urlBuilder.addQueryParameter(key, value)
        }

        val credentials = authRepository.credentials.value
        val token = auth.getAppToken()

        val builder = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", deviceProfile.userAgent)
            .header("X-App-Id", "com.coolapk.market")
            .header("X-App-Version", "16.2.0")
            .header("X-App-Code", "2604201")
            .header("X-Api-Version", "16")
            .header("X-App-Device", deviceProfile.deviceCode)
            .header("X-App-Token", token)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Dark-Mode", "1")

        if (credentials.isLoggedIn) {
            var cookie = credentials.cookie
            if (credentials.ddid.isNotBlank() && !cookie.contains("ddid=")) {
                cookie = "$cookie; ddid=${credentials.ddid}"
            }
            builder.header("Cookie", cookie)
        }

        return builder
    }

    suspend fun getHeadlineFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/main/headline", mapOf("page" to page.toString())).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val list = parseFeedList(body)
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
            getRecommendFeeds(page)
        } catch (_: Exception) {
            getRecommendFeeds(page)
        }
    }

    suspend fun getRecommendFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/main/indexV8", mapOf("page" to page.toString())).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseFeedList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHotFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        // 1. 优先请求官方客户端首页热榜 V9_HOME_TAB_RANKING
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "V9_HOME_TAB_RANKING",
                    "title" to "热榜",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val list = parseFeedList(response.body?.string() ?: "")
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
        } catch (_: Exception) {}

        // 2. 尝试 24小时热门榜单 #/feed/statHotList?period=24h
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "#/feed/statHotList?period=24h",
                    "title" to "热门",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val list = parseFeedList(response.body?.string() ?: "")
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
        } catch (_: Exception) {}

        // 3. 保底 7天点赞热榜 #/feed/statList?statType=7days&sortField=likenum
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "#/feed/statList?statType=7days&sortField=likenum",
                    "title" to "热榜",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseFeedList(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyFavoriteFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/favorite/list", mapOf("page" to page.toString(), "type" to "feed")).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseFeedList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeedDetail(feedId: String): Result<FeedItem> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/feed/detail", mapOf("id" to feedId)).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            if (data == null) {
                val serverMsg = json.optString("message", json.optString("error", ""))
                val errMsg = if (serverMsg.isNotBlank()) serverMsg else "动态不存在或已被删除"
                return@withContext Result.failure(IOException(errMsg))
            }
            val item = parseSingleFeed(data)
                ?: return@withContext Result.failure(IOException("解析动态内容失败"))
            FeedMemoryCache.put(item)
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeedReplies(
        feedId: String,
        page: Int,
        sortType: String = ""
    ): Result<List<CommentItem>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf(
                "id" to feedId,
                "page" to page.toString(),
                "listType" to if (sortType == "author") "" else sortType
            )
            if (sortType == "author") {
                params["isAuthor"] = "1"
            }
            val req = buildRequest("/v6/feed/replyList", params).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseCommentList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommentSubReplies(
        commentId: String,
        page: Int = 1
    ): Result<List<SubReplyItem>> = withContext(Dispatchers.IO) {
        try {
            val params = mapOf(
                "id" to commentId,
                "page" to page.toString(),
                "listType" to "",
                "discussMode" to "0",
                "feedType" to "feed_reply"
            )
            val req = buildRequest("/v6/feed/replyList", params).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseSubReplyList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(feedId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = buildRequest("/v6/feed/like")
            val form = FormBody.Builder().add("id", feedId).build()
            val req = reqBuilder.post(form).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(feedId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = buildRequest("/v6/feed/favorite")
            val form = FormBody.Builder().add("id", feedId).build()
            val req = reqBuilder.post(form).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unFavoriteFeed(feedId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("id", feedId)
                .add("targetType", "feed")
                .build()
            val req = buildRequest("/v6/feed/unFavorite").post(form).build()
            val response = okHttpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                val req2 = buildRequest("/v6/feed/unfavorite").post(form).build()
                val resp2 = okHttpClient.newCall(req2).execute()
                if (!resp2.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replyFeed(feedId: String, message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = buildRequest("/v6/feed/reply")
            val form = FormBody.Builder()
                .add("id", feedId)
                .add("message", message)
                .build()
            val req = reqBuilder.post(form).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val json = JSONObject(body)
            if (json.has("error")) {
                val err = json.optString("error")
                if (err.isNotBlank()) return@withContext Result.failure(IOException(err))
            }
            val msg = json.optString("message", "回复成功")
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchFeeds(query: String, page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/search",
                mapOf(
                    "type" to "feed",
                    "searchValue" to query,
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseFeedList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String, page: Int): Result<List<UserCardItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/search",
                mapOf(
                    "type" to "user",
                    "searchValue" to query,
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseUserList(body, isFollowList = false)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchTopics(query: String, page: Int): Result<List<TopicItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/search",
                mapOf(
                    "type" to "feedTopic",
                    "searchValue" to query,
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val list = parseTopicList(body)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHotSearchWords(): Result<List<String>> = withContext(Dispatchers.IO) {
        val fallback = listOf("HyperOS", "OriginOS", "iOS", "手表", "玩机技巧", "摄影", "酷安", "折叠屏", "澎湃", "WearOS")
        try {
            val req = buildRequest("/v6/search/hotSearchWords").build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.success(fallback)
            }
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val words = mutableListOf<String>()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.opt(i)
                    when (item) {
                        is String -> if (item.isNotBlank()) words.add(item)
                        is JSONObject -> {
                            val w = item.optString("title", item.optString("word", item.optString("keyword", "")))
                            if (w.isNotBlank()) words.add(w)
                        }
                    }
                }
                if (words.isNotEmpty()) {
                    return@withContext Result.success(words)
                }
            }
            Result.success(fallback)
        } catch (e: Exception) {
            Result.success(fallback)
        }
    }

    suspend fun getTimelineFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        // 1. 优先尝试移动端首页关注流 V15_HOME_TAB_FOLLOW
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "V15_HOME_TAB_FOLLOW",
                    "title" to "关注",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val list = parseFeedList(response.body?.string() ?: "")
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
        } catch (_: Exception) {}

        // 2. 尝试旧版 V9 首页关注流 /user/followFeedList
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "/user/followFeedList",
                    "title" to "关注",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val list = parseFeedList(response.body?.string() ?: "")
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
        } catch (_: Exception) {}

        // 3. 尝试 /v6/feed/followFeedList
        try {
            val req = buildRequest(
                "/v6/feed/followFeedList",
                mapOf("page" to page.toString())
            ).build()
            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val list = parseFeedList(response.body?.string() ?: "")
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }
        } catch (_: Exception) {}

        // 4. 保底主页关注参数 /v6/main/indexV8?type=follow
        try {
            val req = buildRequest(
                "/v6/main/indexV8",
                mapOf("type" to "follow", "page" to page.toString())
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseFeedList(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPictureFeeds(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/picture/list", mapOf("page" to page.toString())).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext getPictureFeedsFallback(page)
            }
            val list = parseFeedList(body)
            if (list.isEmpty()) {
                return@withContext getPictureFeedsFallback(page)
            }
            Result.success(list)
        } catch (e: Exception) {
            getPictureFeedsFallback(page)
        }
    }

    private suspend fun getPictureFeedsFallback(page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/page/dataList",
                mapOf(
                    "url" to "#/feed/pictureList",
                    "title" to "酷图",
                    "page" to page.toString()
                )
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseFeedList(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(uid: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/user/profile", mapOf("uid" to uid)).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
                ?: return@withContext Result.failure(IOException("未获取到用户资料"))
            Result.success(parseUserProfile(data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserFeeds(uid: String, page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/user/feedList",
                mapOf("uid" to uid, "page" to page.toString(), "isIncludeTop" to "1")
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseFeedList(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFollowUser(uid: String, follow: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val path = if (follow) "/v6/user/follow" else "/v6/user/unfollow"
            val reqBuilder = buildRequest(path)
            val form = FormBody.Builder().add("uid", uid).build()
            val req = reqBuilder.post(form).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserFollowList(uid: String, page: Int): Result<List<UserCardItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/user/followList",
                mapOf("uid" to uid, "page" to page.toString())
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseUserList(body, isFollowList = true, selfUid = uid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserFansList(uid: String, page: Int): Result<List<UserCardItem>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest(
                "/v6/user/fansList",
                mapOf("uid" to uid, "page" to page.toString(), "isIncludeTop" to "1")
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            Result.success(parseUserList(body, isFollowList = false, selfUid = uid))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopicFeeds(tag: String, page: Int): Result<List<FeedItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanTag = tag.trim().removeSurrounding("#")
            val req = buildRequest(
                "/v6/topic/tagFeedList",
                mapOf("tag" to cleanTag, "page" to page.toString())
            ).build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext searchFeeds("#$cleanTag#", page)
            }
            val list = parseFeedList(body)
            if (list.isEmpty()) {
                return@withContext searchFeeds("#$cleanTag#", page)
            }
            Result.success(list)
        } catch (e: Exception) {
            searchFeeds("#${tag.trim().removeSurrounding("#")}#", page)
        }
    }

    suspend fun checkLoginInfo(): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("/v6/account/checkLoginInfo").build()
            val response = okHttpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            if (data != null) {
                val username = data.optString("username", "")
                val avatar = data.optString("userAvatar", "")
                if (username.isNotEmpty()) {
                    authRepository.updateUserInfo(username, avatar)
                    return@withContext Result.success(Pair(username, avatar))
                }
            }
            Result.failure(IOException("未登录或 Token 失效"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFeedList(responseBody: String): List<FeedItem> {
        val result = mutableListOf<FeedItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            appendFeedRecursive(item, result)
        }
        FeedMemoryCache.putAll(result)
        return result
    }

    private fun appendFeedRecursive(obj: JSONObject, list: MutableList<FeedItem>) {
        // 如果卡片包含子 entities 动态列表，说明是容器卡片，仅递归解析内部真实动态，严禁将容器自身作为动态添加！
        val entities = obj.optJSONArray("entities")
        if (entities != null && entities.length() > 0) {
            for (j in 0 until entities.length()) {
                val sub = entities.optJSONObject(j) ?: continue
                appendFeedRecursive(sub, list)
            }
            return
        }

        // 仅当自身不是容器时，才尝试解析为单条动态
        val single = parseSingleFeed(obj)
        if (single != null) {
            list.add(single)
        }
    }

    private fun optStringClean(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.optString(key, "").trim()
            if (v.isNotEmpty() && !v.equals("null", ignoreCase = true) && !v.equals("undefined", ignoreCase = true)) {
                return v
            }
        }
        return ""
    }

    private fun parseSingleFeed(obj: JSONObject): FeedItem? {
        val entityType = optStringClean(obj, "entityType").lowercase(Locale.ROOT)
        // 过滤非动态的实体卡片类型（容器、推广、话题、产品、商品、应用推荐等）
        val nonFeedTypes = setOf(
            "card", "title", "iconlink", "iconlinks", "icongridcard", "iconminigridcard",
            "imagesquarecard", "imagetextcard", "textcard", "selectorcard", "banner",
            "apk", "app", "goods", "product", "topic"
        )
        if (nonFeedTypes.contains(entityType)) {
            return null
        }

        val id = optStringClean(obj, "id", "feed_id", "entityId")
        if (id.isEmpty()) return null

        val rawMessage = optStringClean(obj, "message", "detail", "message_raw_output", "description", "content")
        val rawTitle = optStringClean(obj, "title")
        if (rawMessage.isBlank() && rawTitle.isBlank()) {
            return null
        }

        val userInfo = obj.optJSONObject("userInfo") ?: obj.optJSONObject("user")
        val rawUsername = optStringClean(obj, "username", userInfo?.let { optStringClean(it, "username") } ?: "")
        // 如果既没有用户信息，也没有任何作者昵称，且不是动态，说明是无主卡片或广告，过滤掉
        if (userInfo == null && rawUsername.isBlank() && entityType != "feed" && entityType != "feedarticle" && entityType != "picture") {
            return null
        }

        val message = cleanHtmlTags(rawMessage)
        val username = rawUsername.ifBlank { "酷友" }

        // 彻底过滤酷安服务端对普通动态自动附加的重复作者名占位标题（如 "xxx的动态", "xxx的图文", "xxx的回答", "动态"）
        val isAutoTitle = rawTitle.isBlank() ||
            rawTitle == username ||
            rawTitle == "${username}的动态" ||
            rawTitle == "${username}的图文" ||
            rawTitle == "${username}的回答" ||
            rawTitle.endsWith("的动态") ||
            rawTitle.endsWith("的新动态") ||
            rawTitle == "动态" ||
            rawTitle == "图文"
        val title = if (isAutoTitle) "" else rawTitle
        val userAvatar = normalizeImageUrl(optStringClean(obj, "userAvatar", userInfo?.let { optStringClean(it, "userAvatar") } ?: ""))
        val uid = optStringClean(obj, "uid", userInfo?.let { optStringClean(it, "uid") } ?: "")

        val dateline = obj.optLong("dateline", 0L)
        val dateString = formatDate(dateline)
        val deviceTitle = optStringClean(obj, "device_title", "deviceTitle")

        val pics = mutableListOf<String>()
        val picArr = obj.optJSONArray("picArr")
        if (picArr != null) {
            for (p in 0 until picArr.length()) {
                val picUrl = picArr.optString(p, "").trim()
                if (picUrl.isNotBlank() && !picUrl.equals("null", ignoreCase = true)) {
                    pics.add(normalizeImageUrl(picUrl))
                }
            }
        } else {
            val singlePic = optStringClean(obj, "pic", "image")
            if (singlePic.isNotBlank()) pics.add(normalizeImageUrl(singlePic))
        }

        val likeNum = obj.optInt("likenum", obj.optInt("like_num", 0))
        val replyNum = obj.optInt("replynum", obj.optInt("reply_num", 0))
        val isLiked = obj.optInt("is_like", obj.optInt("isLike", 0)) == 1
        val isFavorite = obj.optInt("is_favorite", obj.optInt("isFavorite", 0)) == 1

        return FeedItem(
            id = id,
            uid = uid,
            username = username,
            userAvatar = userAvatar,
            dateline = dateline,
            dateString = dateString,
            deviceTitle = deviceTitle,
            title = title,
            message = message,
            picArr = pics,
            likeNum = likeNum,
            replyNum = replyNum,
            isLiked = isLiked,
            isFavorite = isFavorite
        )
    }

    private fun parseCommentList(responseBody: String): List<CommentItem> {
        val result = mutableListOf<CommentItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val id = optStringClean(obj, "id", "reply_id")
            if (id.isEmpty()) continue

            val userInfo = obj.optJSONObject("userInfo") ?: obj.optJSONObject("user")
            val fallbackName = userInfo?.optString("username", "酷友") ?: "酷友"
            val rawUsername = optStringClean(obj, "username", userInfo?.let { optStringClean(it, "username") } ?: "")
            val username = rawUsername.ifBlank { fallbackName }
            val userAvatar = normalizeImageUrl(optStringClean(obj, "userAvatar", userInfo?.let { optStringClean(it, "userAvatar") } ?: ""))
            val uid = optStringClean(obj, "uid", userInfo?.let { optStringClean(it, "uid") } ?: "")

            val dateline = obj.optLong("dateline", 0L)
            val dateString = formatDate(dateline)
            val rawMessage = optStringClean(obj, "message", "reply_message")
            val message = cleanHtmlTags(rawMessage)

            var pic = normalizeImageUrl(optStringClean(obj, "pic", "image", "extra_pic", "thumbnailPic"))
            if (pic.isBlank()) {
                val picArr = obj.optJSONArray("picArr") ?: obj.optJSONArray("extra_pics")
                if (picArr != null && picArr.length() > 0) {
                    pic = normalizeImageUrl(picArr.optString(0, ""))
                }
            }
            if (pic.isBlank()) {
                val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(rawMessage)
                if (imgMatch != null) {
                    pic = normalizeImageUrl(imgMatch.groupValues[1])
                } else {
                    val bbImgMatch = Regex("""\[(?:img|gif)\](https?://[^\s\[\]]+)\[/(?:img|gif)\]""", RegexOption.IGNORE_CASE).find(rawMessage)
                    if (bbImgMatch != null) {
                        pic = normalizeImageUrl(bbImgMatch.groupValues[1])
                    } else {
                        val gifMatch = Regex("""(https?://[^\s<>"'\[\]]+\.(?:gif|jpg|jpeg|png|webp)(\?[^\s<>"'\[\]]*)?)""", RegexOption.IGNORE_CASE).find(rawMessage)
                        if (gifMatch != null) {
                            pic = normalizeImageUrl(gifMatch.groupValues[1])
                        }
                    }
                }
            }
            val replyNum = obj.optInt("replynum", obj.optInt("reply_num", 0))
            val likeNum = obj.optInt("likenum", obj.optInt("like_num", 0))
            val isLiked = obj.optInt("is_like", obj.optInt("isLike", 0)) == 1
            val isAuthor = obj.optInt("is_author", obj.optInt("isAuthor", obj.optInt("is_feed_author", 0))) == 1
            val isTop = obj.optInt("is_top", obj.optInt("isTop", 0)) == 1

            // 楼中楼嵌套回复
            val subReplies = mutableListOf<SubReplyItem>()
            val replyRows = obj.optJSONArray("replyRows") ?: obj.optJSONArray("subRows")
            if (replyRows != null) {
                for (r in 0 until replyRows.length()) {
                    val rObj = replyRows.optJSONObject(r) ?: continue
                    val rId = optStringClean(rObj, "id")
                    val rUid = optStringClean(rObj, "uid")
                    val rName = optStringClean(rObj, "username")
                    val rToName = optStringClean(rObj, "rusername", "replyUsername")
                    val subRawMsg = optStringClean(rObj, "message")
                    val rMsg = cleanHtmlTags(subRawMsg)
                    var rPic = normalizeImageUrl(optStringClean(rObj, "pic", "image", "thumbnailPic"))
                    if (rPic.isBlank()) {
                        val subPicArr = rObj.optJSONArray("picArr") ?: rObj.optJSONArray("extra_pics")
                        if (subPicArr != null && subPicArr.length() > 0) {
                            rPic = normalizeImageUrl(subPicArr.optString(0, ""))
                        }
                    }
                    if (rPic.isBlank()) {
                        val subImgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(subRawMsg)
                        if (subImgMatch != null) {
                            rPic = normalizeImageUrl(subImgMatch.groupValues[1])
                        } else {
                            val subBbMatch = Regex("""\[(?:img|gif)\](https?://[^\s\[\]]+)\[/(?:img|gif)\]""", RegexOption.IGNORE_CASE).find(subRawMsg)
                            if (subBbMatch != null) {
                                rPic = normalizeImageUrl(subBbMatch.groupValues[1])
                            } else {
                                val subGifMatch = Regex("""(https?://[^\s<>"'\[\]]+\.(?:gif|jpg|jpeg|png|webp)(\?[^\s<>"'\[\]]*)?)""", RegexOption.IGNORE_CASE).find(subRawMsg)
                                if (subGifMatch != null) {
                                    rPic = normalizeImageUrl(subGifMatch.groupValues[1])
                                }
                            }
                        }
                    }
                    val rIsAuthor = rObj.optInt("is_author", rObj.optInt("isAuthor", 0)) == 1
                    if (rName.isNotBlank() && (rMsg.isNotBlank() || rPic.isNotBlank())) {
                        subReplies.add(
                            SubReplyItem(
                                id = rId,
                                uid = rUid,
                                username = rName,
                                replyUsername = rToName,
                                message = rMsg,
                                pic = rPic,
                                isAuthor = rIsAuthor
                            )
                        )
                    }
                }
            }

            result.add(
                CommentItem(
                    id = id,
                    uid = uid,
                    username = username,
                    userAvatar = userAvatar,
                    dateline = dateline,
                    dateString = dateString,
                    message = message,
                    pic = pic,
                    replyNum = replyNum,
                    likeNum = likeNum,
                    isLiked = isLiked,
                    isAuthor = isAuthor,
                    isTop = isTop,
                    subReplies = subReplies
                )
            )
        }
        return result
    }

    private fun parseSubReplyList(responseBody: String): List<SubReplyItem> {
        val result = mutableListOf<SubReplyItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val rObj = dataArray.optJSONObject(i) ?: continue
            val rId = optStringClean(rObj, "id", "reply_id")
            if (rId.isEmpty()) continue

            val userInfo = rObj.optJSONObject("userInfo") ?: rObj.optJSONObject("user")
            val rUid = optStringClean(rObj, "uid", userInfo?.let { optStringClean(it, "uid") } ?: "")
            val rawName = optStringClean(rObj, "username", userInfo?.let { optStringClean(it, "username") } ?: "")
            val rName = rawName.ifBlank { "酷友" }
            val rToName = optStringClean(rObj, "rusername", "replyUsername")
            val subRawMsg = optStringClean(rObj, "message", "reply_message")
            val rMsg = cleanHtmlTags(subRawMsg)

            var rPic = normalizeImageUrl(optStringClean(rObj, "pic", "image", "extra_pic", "thumbnailPic"))
            if (rPic.isBlank()) {
                val subPicArr = rObj.optJSONArray("picArr") ?: rObj.optJSONArray("extra_pics")
                if (subPicArr != null && subPicArr.length() > 0) {
                    rPic = normalizeImageUrl(subPicArr.optString(0, ""))
                }
            }
            if (rPic.isBlank()) {
                val subImgMatch = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(subRawMsg)
                if (subImgMatch != null) {
                    rPic = normalizeImageUrl(subImgMatch.groupValues[1])
                } else {
                    val subBbMatch = Regex("""\[(?:img|gif)\](https?://[^\s\[\]]+)\[/(?:img|gif)\]""", RegexOption.IGNORE_CASE).find(subRawMsg)
                    if (subBbMatch != null) {
                        rPic = normalizeImageUrl(subBbMatch.groupValues[1])
                    } else {
                        val subGifMatch = Regex("""(https?://[^\s<>"'\[\]]+\.(?:gif|jpg|jpeg|png|webp)(\?[^\s<>"'\[\]]*)?)""", RegexOption.IGNORE_CASE).find(subRawMsg)
                        if (subGifMatch != null) {
                            rPic = normalizeImageUrl(subGifMatch.groupValues[1])
                        }
                    }
                }
            }
            val rIsAuthor = rObj.optInt("is_author", rObj.optInt("isAuthor", rObj.optInt("is_feed_author", 0))) == 1
            if (rName.isNotBlank() && (rMsg.isNotBlank() || rPic.isNotBlank())) {
                result.add(
                    SubReplyItem(
                        id = rId,
                        uid = rUid,
                        username = rName,
                        replyUsername = rToName,
                        message = rMsg,
                        pic = rPic,
                        isAuthor = rIsAuthor
                    )
                )
            }
        }
        return result
    }

    private fun parseUserProfile(data: JSONObject): UserProfile {
        val uid = data.optString("uid", "")
        val username = data.optString("username", "酷友")
        val userAvatar = normalizeImageUrl(data.optString("userAvatar", ""))
        val bio = data.optString("bio", data.optString("user_bio", data.optString("subTitle", "")))
        val level = data.optInt("level", data.optInt("userLevel", 0))
        val followNum = data.optInt("follow", data.optInt("followNum", data.optInt("follow_num", 0)))
        val fansNum = data.optInt("fans", data.optInt("fansNum", data.optInt("fans_num", 0)))
        val feedNum = data.optInt("feed", data.optInt("feedNum", data.optInt("feed_num", 0)))
        val isFollow = data.optInt("isFollow", data.optInt("is_follow", 0)) == 1
        val verifyTitle = data.optString("verify_title", data.optString("verifyTitle", ""))
        val isSelf = (data.optInt("is_self", data.optInt("isSelf", 0)) == 1) || (uid.isNotBlank() && uid == currentUid)

        return UserProfile(
            uid = uid,
            username = username,
            userAvatar = userAvatar,
            bio = bio,
            level = level,
            followNum = followNum,
            fansNum = fansNum,
            feedNum = feedNum,
            isFollow = isFollow,
            verifyTitle = verifyTitle,
            isSelf = isSelf
        )
    }

    private fun parseTopicList(responseBody: String): List<TopicItem> {
        val result = mutableListOf<TopicItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val title = optStringClean(obj, "title", "tag", "tag_name").trim().removeSurrounding("#")
            if (title.isBlank()) continue
            val id = optStringClean(obj, "id", "tagId", "tag_id")
            val logo = normalizeImageUrl(optStringClean(obj, "logo", "icon", "pic"))
            val description = optStringClean(obj, "description", "intro", "subTitle")
            val feedNum = obj.optInt("feed_num", obj.optInt("feedNum", obj.optInt("num", 0)))
            val followNum = obj.optInt("follow_num", obj.optInt("followNum", 0))
            result.add(
                TopicItem(
                    id = id.ifBlank { title },
                    title = title,
                    logo = logo,
                    description = description,
                    feedNum = feedNum,
                    followNum = followNum
                )
            )
        }
        return result
    }

    private fun parseUserList(responseBody: String, isFollowList: Boolean = true, selfUid: String = ""): List<UserCardItem> {
        val result = mutableListOf<UserCardItem>()
        val json = JSONObject(responseBody)
        val arr = json.optJSONArray("data") ?: return emptyList()
        val normalizedSelfUid = selfUid.trim()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

            // 酷安服务端机制：
            // /v6/user/followList (关注列表)：
            //   - 真实被关注用户在 fUserInfo、fuid、fusername、fuserAvatar；顶层 uid/username/userAvatar 是请求者自己！
            // /v6/user/fansList (粉丝列表)：
            //   - 真实粉丝用户在 userInfo、uid、username、userAvatar；fUserInfo/fuid 是请求者自己！
            val targetInfo = if (isFollowList) {
                obj.optJSONObject("fUserInfo") ?: obj.optJSONObject("userInfo")
            } else {
                obj.optJSONObject("userInfo") ?: obj.optJSONObject("fUserInfo")
            }

            var uid = targetInfo?.let { optStringClean(it, "uid", "id") } ?: ""
            if (uid.isBlank()) {
                uid = if (isFollowList) {
                    optStringClean(obj, "fuid", "target_uid", "uid", "id")
                } else {
                    optStringClean(obj, "uid", "id", "fuid")
                }
            }

            // 严防出现只看到自己的情况：如果提取到的 UID 与当前登录者一致，尝试取对方的 UID
            if (normalizedSelfUid.isNotBlank() && uid == normalizedSelfUid) {
                val altUid = if (isFollowList) optStringClean(obj, "fuid") else optStringClean(obj, "uid")
                if (altUid.isNotBlank() && altUid != normalizedSelfUid) {
                    uid = altUid
                } else {
                    continue
                }
            }
            if (uid.isBlank()) continue

            var username = targetInfo?.let { optStringClean(it, "username", "displayUserName") } ?: ""
            if (username.isBlank()) {
                username = if (isFollowList) {
                    optStringClean(obj, "fusername", "username")
                } else {
                    optStringClean(obj, "username", "fusername")
                }
            }
            if (username.isBlank()) username = "酷友"

            var avatar = targetInfo?.let { optStringClean(it, "userAvatar", "avatar") } ?: ""
            if (avatar.isBlank()) {
                avatar = if (isFollowList) {
                    optStringClean(obj, "fuserAvatar", "fUserAvatar", "userAvatar", "avatar")
                } else {
                    optStringClean(obj, "userAvatar", "avatar", "fuserAvatar", "fUserAvatar")
                }
            }

            val bio = (targetInfo?.let { optStringClean(it, "bio", "sign", "signature") } ?: "")
                .ifBlank { optStringClean(obj, "bio", "sign", "verify_title", "subTitle") }

            val isFollow = obj.optInt("isFollow", obj.optInt("is_follow", if (isFollowList) 1 else 0)) == 1
            result.add(UserCardItem(uid, username, normalizeImageUrl(avatar), bio, isFollow))
        }
        return result
    }

    private fun normalizeImageUrl(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return ""
        // 彻底过滤包含 HTML 标签、换行、回车或空白的非 URL 脏数据
        if (u.contains('<') || u.contains('>') || u.contains('\n') || u.contains('\r') || u.contains(' ') || u.contains('\t')) {
            return ""
        }
        if (u.startsWith("http://")) {
            u = "https://" + u.substring(7)
        } else if (u.startsWith("//")) {
            u = "https:$u"
        }
        if (!u.startsWith("https://") && !u.startsWith("http://")) {
            return ""
        }
        return u
    }

    private fun cleanHtmlTags(html: String): String {
        return html
            .replace(Regex("<a[^>]*>(.*?)</a>"), "$1")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }

    private fun formatDate(seconds: Long): String {
        if (seconds <= 0) return ""
        val now = System.currentTimeMillis() / 1000L
        val diff = now - seconds
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            diff < 86400 * 30 -> "${diff / 86400}天前"
            else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(seconds * 1000L))
        }
    }
}
