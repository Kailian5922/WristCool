package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.util.TextSanitizer
import org.json.JSONObject
import java.util.Locale

/**
 * 酷安 REST API JSON 纯解析器（无 Android 依赖，便于完整单元测试）。
 */
object CoolapkJsonParser {

    private val NON_FEED_ENTITY_TYPES = setOf(
        "card", "title", "iconlink", "iconlinks", "icongridcard", "iconminigridcard",
        "imagesquarecard", "imagetextcard", "textcard", "selectorcard", "banner",
        "apk", "app", "goods", "product", "topic"
    )

    private val EXPLICIT_FEED_TYPES = setOf("feed", "feedarticle", "picture")

    /**
     * 检查酷安 JSON 业务错误。
     * 酷安接口在未登录、无权限或参数异常时常返回 HTTP 200 + `{"status": -1, "message": "..."}` 或 `{"error": "..."}`。
     */
    fun parseApiError(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return try {
            val json = JSONObject(responseBody)
            val err = optStringClean(json, "error")
            val msg = optStringClean(json, "message")
            if (err.isNotBlank() && err != "0") {
                return msg.ifBlank { err }
            }
            if (json.has("status") && !json.isNull("status")) {
                val status = json.optInt("status", 0)
                if (status < 0) {
                    return msg.ifBlank { "请求失败 (status=$status)" }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun parseFeedList(responseBody: String): List<FeedItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<FeedItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            appendFeedRecursive(item, result)
        }
        return result
    }

    private fun appendFeedRecursive(obj: JSONObject, list: MutableList<FeedItem>) {
        // 如果卡片包含子 entities 动态列表，说明是容器卡片，仅递归解析内部真实动态，严禁将容器自身作为动态添加
        val entities = obj.optJSONArray("entities")
        if (entities != null && entities.length() > 0) {
            for (j in 0 until entities.length()) {
                val sub = entities.optJSONObject(j) ?: continue
                appendFeedRecursive(sub, list)
            }
            return
        }

        parseSingleFeed(obj)?.let { list.add(it) }
    }

    fun parseSingleFeed(obj: JSONObject): FeedItem? {
        val entityType = optStringClean(obj, "entityType").lowercase(Locale.ROOT)
        if (NON_FEED_ENTITY_TYPES.contains(entityType)) {
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
        val rawUsername = optUserField(obj, userInfo, "username", "displayUserName")
        // 如果既没有用户信息，也没有作者昵称，且不是显式动态类型，说明是无主卡片或广告，过滤掉
        if (userInfo == null && rawUsername.isBlank() && !EXPLICIT_FEED_TYPES.contains(entityType)) {
            return null
        }

        val message = TextSanitizer.cleanHtmlTags(rawMessage)
        val username = rawUsername.ifBlank { "酷友" }
        val title = TextSanitizer.filterAutoTitle(rawTitle, username)
        val userAvatar = TextSanitizer.normalizeImageUrl(optUserField(obj, userInfo, "userAvatar", "avatar"))
        val uid = optUserField(obj, userInfo, "uid")

        val dateline = obj.optLong("dateline", 0L)
        val dateString = TextSanitizer.formatRelativeTime(dateline)
        val deviceTitle = optStringClean(obj, "device_title", "deviceTitle")

        val pics = mutableListOf<String>()
        val picArr = obj.optJSONArray("picArr")
        if (picArr != null) {
            for (p in 0 until picArr.length()) {
                val picUrl = picArr.optString(p, "").trim()
                if (picUrl.isNotBlank() && !picUrl.equals("null", ignoreCase = true)) {
                    val normalized = TextSanitizer.normalizeImageUrl(picUrl)
                    if (normalized.isNotBlank()) pics.add(normalized)
                }
            }
        }
        if (pics.isEmpty()) {
            val singlePic = TextSanitizer.normalizeImageUrl(optStringClean(obj, "pic", "image"))
            if (singlePic.isNotBlank()) pics.add(singlePic)
        }

        val likeNum = obj.optInt("likenum", obj.optInt("like_num", 0))
        val replyNum = obj.optInt("replynum", obj.optInt("reply_num", 0))
        val userAction = obj.optJSONObject("userAction")
        val isLiked = (obj.optInt("is_like", obj.optInt("isLike", 0)) == 1) ||
            (userAction?.optInt("like", 0) == 1)
        val isFavorite = (obj.optInt("is_favorite", obj.optInt("isFavorite", 0)) == 1) ||
            (userAction?.optInt("collect", 0) == 1) ||
            (userAction?.optInt("favorite", 0) == 1)

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

    fun parseCommentList(responseBody: String): List<CommentItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<CommentItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val id = optStringClean(obj, "id", "reply_id")
            if (id.isEmpty()) continue

            val userInfo = obj.optJSONObject("userInfo") ?: obj.optJSONObject("user")
            val username = optUserField(obj, userInfo, "username", "displayUserName").ifBlank { "酷友" }
            val userAvatar = TextSanitizer.normalizeImageUrl(optUserField(obj, userInfo, "userAvatar", "avatar"))
            val uid = optUserField(obj, userInfo, "uid")

            val dateline = obj.optLong("dateline", 0L)
            val dateString = TextSanitizer.formatRelativeTime(dateline)
            val rawMessage = optStringClean(obj, "message", "reply_message")
            val message = TextSanitizer.cleanHtmlTags(rawMessage)
            val pic = extractCommentPic(obj, rawMessage)

            val replyNum = obj.optInt("replynum", obj.optInt("reply_num", 0))
            val likeNum = obj.optInt("likenum", obj.optInt("like_num", 0))
            val userAction = obj.optJSONObject("userAction")
            val isLiked = (obj.optInt("is_like", obj.optInt("isLike", 0)) == 1) ||
                (userAction?.optInt("like", 0) == 1)
            val isAuthor = obj.optInt("is_author", obj.optInt("isAuthor", obj.optInt("is_feed_author", 0))) == 1
            val isTop = obj.optInt("is_top", obj.optInt("isTop", 0)) == 1

            val subReplies = mutableListOf<SubReplyItem>()
            val replyRows = obj.optJSONArray("replyRows") ?: obj.optJSONArray("subRows")
            if (replyRows != null) {
                for (r in 0 until replyRows.length()) {
                    val rObj = replyRows.optJSONObject(r) ?: continue
                    parseSubReplyObject(rObj, allowEmptyId = true)?.let { subReplies.add(it) }
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

    fun parseSubReplyList(responseBody: String): List<SubReplyItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<SubReplyItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val rObj = dataArray.optJSONObject(i) ?: continue
            parseSubReplyObject(rObj, allowEmptyId = false)?.let { result.add(it) }
        }
        return result
    }

    private fun parseSubReplyObject(rObj: JSONObject, allowEmptyId: Boolean): SubReplyItem? {
        val rId = optStringClean(rObj, "id", "reply_id")
        if (!allowEmptyId && rId.isEmpty()) return null

        val userInfo = rObj.optJSONObject("userInfo") ?: rObj.optJSONObject("user")
        val rUid = optUserField(rObj, userInfo, "uid")
        val rName = optUserField(rObj, userInfo, "username", "displayUserName").ifBlank {
            if (allowEmptyId) "" else "酷友"
        }
        val rToName = optStringClean(rObj, "rusername", "replyUsername")
        val subRawMsg = optStringClean(rObj, "message", "reply_message")
        val rMsg = TextSanitizer.cleanHtmlTags(subRawMsg)
        val rPic = extractCommentPic(rObj, subRawMsg)
        val rIsAuthor = rObj.optInt("is_author", rObj.optInt("isAuthor", rObj.optInt("is_feed_author", 0))) == 1

        if (rName.isBlank() || (rMsg.isBlank() && rPic.isBlank())) return null
        return SubReplyItem(
            id = rId.ifBlank { "${rUid}_${rName}_${rMsg.hashCode()}" },
            uid = rUid,
            username = rName,
            replyUsername = rToName,
            message = rMsg,
            pic = rPic,
            isAuthor = rIsAuthor
        )
    }

    private fun extractCommentPic(obj: JSONObject, rawMessage: String): String {
        var pic = TextSanitizer.normalizeImageUrl(optStringClean(obj, "pic", "image", "extra_pic", "thumbnailPic"))
        if (pic.isBlank()) {
            val picArr = obj.optJSONArray("picArr") ?: obj.optJSONArray("extra_pics")
            if (picArr != null && picArr.length() > 0) {
                pic = TextSanitizer.normalizeImageUrl(picArr.optString(0, ""))
            }
        }
        if (pic.isBlank()) {
            pic = TextSanitizer.extractImageFromMessage(rawMessage)
        }
        return pic
    }

    fun parseUserProfile(data: JSONObject, currentUid: String): UserProfile {
        val uid = optStringClean(data, "uid")
        val username = optStringClean(data, "username", "displayUserName").ifBlank { "酷友" }
        val userAvatar = TextSanitizer.normalizeImageUrl(optStringClean(data, "userAvatar", "avatar"))
        val bio = optStringClean(data, "bio", "user_bio", "subTitle")
        val level = data.optInt("level", data.optInt("userLevel", 0))
        val followNum = data.optInt("follow", data.optInt("followNum", data.optInt("follow_num", 0)))
        val fansNum = data.optInt("fans", data.optInt("fansNum", data.optInt("fans_num", 0)))
        val feedNum = data.optInt("feed", data.optInt("feedNum", data.optInt("feed_num", 0)))
        val userAction = data.optJSONObject("userAction")
        val isFollow = (data.optInt("isFollow", data.optInt("is_follow", 0)) == 1) ||
            (userAction?.optInt("follow", 0) == 1)
        val verifyTitle = optStringClean(data, "verify_title", "verifyTitle")
        val isSelf = (data.optInt("is_self", data.optInt("isSelf", 0)) == 1) ||
            (uid.isNotBlank() && uid == currentUid)

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

    fun parseTopicList(responseBody: String): List<TopicItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<TopicItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val title = optStringClean(obj, "title", "tag", "tag_name").trim().removeSurrounding("#")
            if (title.isBlank()) continue
            val id = optStringClean(obj, "id", "tagId", "tag_id")
            val logo = TextSanitizer.normalizeImageUrl(optStringClean(obj, "logo", "icon", "pic"))
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

    fun parseUserList(responseBody: String, isFollowList: Boolean = true, selfUid: String = ""): List<UserCardItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<UserCardItem>()
        val json = JSONObject(responseBody)
        val arr = json.optJSONArray("data") ?: return emptyList()
        val normalizedSelfUid = selfUid.trim()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

            // 酷安服务端机制：
            // /v6/user/followList: 被关注者在 fUserInfo / fuid / fusername / fuserAvatar
            // /v6/user/fansList:   粉丝在 userInfo / uid / username / userAvatar
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

            // 严防把请求者自身误解析进列表
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
            result.add(UserCardItem(uid, username, TextSanitizer.normalizeImageUrl(avatar), bio, isFollow))
        }
        return result
    }

    fun parseHotSearchWords(responseBody: String): List<String> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        val words = mutableListOf<String>()
        for (i in 0 until dataArray.length()) {
            when (val item = dataArray.opt(i)) {
                is String -> if (item.isNotBlank()) words.add(item.trim())
                is JSONObject -> {
                    val word = optStringClean(item, "title", "word", "keyword")
                    if (word.isNotBlank()) words.add(word)
                }
            }
        }
        return words
    }

    internal fun optStringClean(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (key.isEmpty() || !obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty() &&
                !value.equals("null", ignoreCase = true) &&
                !value.equals("undefined", ignoreCase = true)
            ) {
                return value
            }
        }
        return ""
    }

    private fun optUserField(obj: JSONObject, userInfo: JSONObject?, vararg keys: String): String {
        return optStringClean(obj, *keys).ifBlank {
            userInfo?.let { optStringClean(it, *keys) } ?: ""
        }
    }
}
