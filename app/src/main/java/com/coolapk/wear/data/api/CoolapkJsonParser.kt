package com.coolapk.wear.data.api

import com.coolapk.wear.data.model.AppItem
import com.coolapk.wear.data.model.ArticleBlock
import com.coolapk.wear.data.model.ChatMessage
import com.coolapk.wear.data.model.CollectionItem
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import com.coolapk.wear.data.model.ForwardFeedInfo
import com.coolapk.wear.data.model.MessageSession
import com.coolapk.wear.data.model.MyReplyItem
import com.coolapk.wear.data.model.NotificationCount
import com.coolapk.wear.data.model.NotificationItem
import com.coolapk.wear.data.model.ProductItem
import com.coolapk.wear.data.model.ProductSpecItem
import com.coolapk.wear.data.model.SubReplyItem
import com.coolapk.wear.data.model.TopicItem
import com.coolapk.wear.data.model.UserCardItem
import com.coolapk.wear.data.model.UserProfile
import com.coolapk.wear.data.model.VoteInfo
import com.coolapk.wear.data.model.VoteOption
import com.coolapk.wear.data.util.TextSanitizer
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 酷安 REST API JSON 纯解析器（无 Android 依赖，便于完整 JVM 单元测试）。
 * 支持动态、长图文、转发引用、投票、评论/楼中楼、通知、私信、收藏单、数码产品与应用等全量实体。
 */
object CoolapkJsonParser {

    private val NON_FEED_ENTITY_TYPES = setOf(
        "card", "title", "iconlink", "iconlinks", "icongridcard", "iconminigridcard",
        "imagesquarecard", "imagetextcard", "textcard", "selectorcard", "banner",
        "apk", "app", "goods", "product", "topic", "sponsorcard", "nomoredatacard"
    )

    private val EXPLICIT_FEED_TYPES = setOf("feed", "feedarticle", "picture", "article", "news")

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

        // 兼容云端足迹 (hitHistoryList) 将动态包在 inner target / feed 字段中的情况
        val effectiveObj = if (entityType == "history" || entityType == "recenthistory") {
            obj.optJSONObject("feed") ?: obj.optJSONObject("targetRow") ?: obj
        } else {
            obj
        }

        val id = optStringClean(effectiveObj, "id", "feed_id", "entityId")
        if (id.isEmpty()) return null

        val rawMessage = optStringClean(
            effectiveObj,
            "message", "detail", "description", "content"
        )
        val rawTitle = optStringClean(effectiveObj, "message_title", "title")
        val rawOutput = optStringClean(effectiveObj, "message_raw_output")
        val articleBlocks = parseArticleBlocks(rawOutput)

        if (rawMessage.isBlank() && rawTitle.isBlank() && articleBlocks.isEmpty()) {
            return null
        }

        val userInfo = effectiveObj.optJSONObject("userInfo") ?: effectiveObj.optJSONObject("user")
        val rawUsername = optUserField(effectiveObj, userInfo, "username", "displayUserName")
        if (userInfo == null && rawUsername.isBlank() && !EXPLICIT_FEED_TYPES.contains(entityType)) {
            return null
        }

        val message = if (rawMessage.isNotBlank()) {
            TextSanitizer.cleanHtmlTags(rawMessage)
        } else {
            articleBlocks.filter { it.type == "text" && it.text.isNotBlank() }
                .joinToString("\n") { it.text }
                .trim()
        }
        val username = rawUsername.ifBlank { "酷友" }
        val title = TextSanitizer.filterAutoTitle(rawTitle, username)
        val userAvatar = TextSanitizer.normalizeImageUrl(optUserField(effectiveObj, userInfo, "userAvatar", "avatar"))
        val uid = optUserField(effectiveObj, userInfo, "uid")

        val dateline = effectiveObj.optLong("dateline", effectiveObj.optLong("lastupdate", 0L))
        val dateString = TextSanitizer.formatRelativeTime(dateline)
        val deviceTitle = optStringClean(effectiveObj, "device_title", "deviceTitle")
        val feedType = optStringClean(effectiveObj, "feedType", "feed_type", "entityType").ifBlank { "feed" }

        val pics = mutableListOf<String>()
        val picArr = effectiveObj.optJSONArray("picArr")
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
            val singlePic = TextSanitizer.normalizeImageUrl(
                optStringClean(effectiveObj, "pic", "message_cover", "image")
            )
            if (singlePic.isNotBlank()) pics.add(singlePic)
        }

        val likeNum = effectiveObj.optInt("likenum", effectiveObj.optInt("like_num", 0))
        val replyNum = effectiveObj.optInt("replynum", effectiveObj.optInt("reply_num", 0))
        val forwardNum = effectiveObj.optInt("forwardnum", effectiveObj.optInt("forward_num", 0))
        val favoriteNum = effectiveObj.optInt("favnum", effectiveObj.optInt("fav_num", 0))
        val ratingScore = effectiveObj.optDouble("rating_score", effectiveObj.optDouble("score", 0.0)).toFloat()

        val userAction = effectiveObj.optJSONObject("userAction")
        val isLiked = (effectiveObj.optInt("is_like", effectiveObj.optInt("isLike", 0)) == 1) ||
            (userAction?.optInt("like", 0) == 1)
        val isFavorite = (effectiveObj.optInt("is_favorite", effectiveObj.optInt("isFavorite", 0)) == 1) ||
            (userAction?.optInt("collect", 0) == 1) ||
            (userAction?.optInt("favorite", 0) == 1)

        val forwardFeed = parseForwardFeed(effectiveObj.optJSONObject("forwardSourceFeed"))
        val vote = parseVoteInfo(effectiveObj.optJSONObject("vote"), id)

        val relationTopics = mutableListOf<String>()
        var targetProductId = ""
        var targetProductName = ""
        val relationRows = effectiveObj.optJSONArray("relationRows")
        if (relationRows != null) {
            for (r in 0 until relationRows.length()) {
                val row = relationRows.optJSONObject(r) ?: continue
                val rowType = optStringClean(row, "entityType").lowercase(Locale.ROOT)
                val rowTitle = optStringClean(row, "title").removeSurrounding("#").trim()
                val rowId = optStringClean(row, "id", "entityId")
                if (rowType == "product" && rowId.isNotBlank() && targetProductId.isBlank()) {
                    targetProductId = rowId
                    targetProductName = rowTitle
                } else if (rowTitle.isNotBlank() && rowType != "apk" && rowType != "user") {
                    relationTopics.add(rowTitle)
                }
            }
        }
        val targetRow = effectiveObj.optJSONObject("targetRow")
        if (targetRow != null && targetProductId.isBlank()) {
            val tType = optStringClean(targetRow, "entityType").lowercase(Locale.ROOT)
            val tId = optStringClean(targetRow, "id", "entityId")
            val tTitle = optStringClean(targetRow, "title")
            if (tType == "product" && tId.isNotBlank()) {
                targetProductId = tId
                targetProductName = tTitle
            }
        }

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
            isFavorite = isFavorite,
            feedType = feedType,
            forwardFeed = forwardFeed,
            vote = vote,
            articleBlocks = articleBlocks,
            relationTopics = relationTopics.distinct(),
            ratingScore = ratingScore,
            forwardNum = forwardNum,
            favoriteNum = favoriteNum,
            targetProductId = targetProductId,
            targetProductName = targetProductName
        )
    }

    internal fun parseArticleBlocks(rawOutput: String): List<ArticleBlock> {
        val trimmed = rawOutput.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("[")) return emptyList()
        return try {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = optStringClean(obj, "type").lowercase(Locale.ROOT)
                    when (type) {
                        "text" -> {
                            val text = TextSanitizer.cleanHtmlTags(optStringClean(obj, "message"))
                            if (text.isNotBlank()) add(ArticleBlock(type = "text", text = text))
                        }
                        "image" -> {
                            val url = TextSanitizer.normalizeImageUrl(optStringClean(obj, "url"))
                            val desc = TextSanitizer.cleanHtmlTags(optStringClean(obj, "description"))
                            if (url.isNotBlank()) {
                                add(ArticleBlock(type = "image", text = desc, imageUrl = url))
                            }
                        }
                        "shareurl" -> {
                            val linkUrl = optStringClean(obj, "url")
                            val linkTitle = optStringClean(obj, "title", "description").ifBlank { linkUrl }
                            if (linkTitle.isNotBlank()) {
                                add(ArticleBlock(type = "shareUrl", linkTitle = linkTitle, linkUrl = linkUrl))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseForwardFeed(obj: JSONObject?): ForwardFeedInfo? {
        if (obj == null) return null
        val id = optStringClean(obj, "id", "entityId")
        val rawMsg = optStringClean(obj, "message", "description")
        val rawTitle = optStringClean(obj, "message_title", "title")
        if (id.isBlank() && rawMsg.isBlank() && rawTitle.isBlank()) return null

        val userInfo = obj.optJSONObject("userInfo")
        val username = optUserField(obj, userInfo, "username", "displayUserName").ifBlank { "酷友" }
        val uid = optUserField(obj, userInfo, "uid")
        val msg = TextSanitizer.cleanHtmlTags(rawMsg)
        val title = TextSanitizer.filterAutoTitle(rawTitle, username)
        var pic = TextSanitizer.normalizeImageUrl(optStringClean(obj, "pic"))
        if (pic.isBlank()) {
            val picArr = obj.optJSONArray("picArr")
            if (picArr != null && picArr.length() > 0) {
                pic = TextSanitizer.normalizeImageUrl(picArr.optString(0, ""))
            }
        }
        return ForwardFeedInfo(
            id = id,
            uid = uid,
            username = username,
            title = title,
            message = msg,
            pic = pic
        )
    }

    internal fun parseVoteInfo(obj: JSONObject?, fallbackFeedId: String): VoteInfo? {
        if (obj == null) return null
        val rawOptions = obj.optJSONArray("options")
            ?: obj.optJSONArray("voteOptions")
            ?: obj.optJSONArray("vote_options")
            ?: return null
        if (rawOptions.length() == 0) return null

        val indexedOptions = mutableListOf<Pair<Int, VoteOption>>()
        for (i in 0 until rawOptions.length()) {
            val opt = rawOptions.optJSONObject(i) ?: continue
            val optId = optStringClean(opt, "id", "option_id", "optionId", "value")
            val optTitle = optStringClean(opt, "title", "name", "text", "label").ifBlank { "选项 ${i + 1}" }
            if (optId.isBlank()) continue
            val count = opt.optInt("vote_num", opt.optInt("voteNum", opt.optInt("total_num", opt.optInt("count", 0))))
            val color = optStringClean(opt, "color")
            val order = opt.optInt("order", opt.optInt("sort", i))
            indexedOptions.add(order to VoteOption(id = optId, title = optTitle, voteNum = count, colorHex = color))
        }
        if (indexedOptions.isEmpty()) return null
        val options = indexedOptions.sortedBy { it.first }.map { it.second }

        val id = optStringClean(obj, "id").ifBlank { fallbackFeedId }
        val title = optStringClean(obj, "message_title", "title").ifBlank { "投票" }
        val totalVotes = obj.optInt("total_vote_num", obj.optInt("totalVoteNum", options.sumOf { it.voteNum }))
        val maxSelect = obj.optInt("max_select_num", obj.optInt("maxSelectNum", 1)).coerceIn(1, options.size)
        val minSelect = obj.optInt("min_select_num", obj.optInt("minSelectNum", 1)).coerceIn(1, maxSelect)

        val selectedIds = mutableListOf<String>()
        val userVoteArr = obj.optJSONArray("user_vote")
            ?: obj.optJSONArray("userVote")
            ?: obj.optJSONArray("selected_options")
            ?: obj.optJSONArray("selectedOptions")
        if (userVoteArr != null) {
            for (i in 0 until userVoteArr.length()) {
                when (val entry = userVoteArr.opt(i)) {
                    is JSONObject -> {
                        val oid = optStringClean(entry, "id", "option_id", "optionId", "value")
                        if (oid.isNotBlank()) selectedIds.add(oid)
                    }
                    is String, is Number -> {
                        val oid = entry.toString().trim()
                        if (oid.isNotBlank()) selectedIds.add(oid)
                    }
                }
            }
        }
        val isVoted = selectedIds.isNotEmpty() ||
            obj.optInt("is_vote", obj.optInt("isVote", 0)) == 1 ||
            obj.optBoolean("voted", false) ||
            (obj.optJSONObject("user_vote") != null)

        return VoteInfo(
            id = id,
            title = title,
            totalVoteNum = totalVotes,
            minSelect = minSelect,
            maxSelect = maxSelect,
            isVoted = isVoted,
            selectedOptionIds = selectedIds.distinct(),
            options = options
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
        val rLikeNum = rObj.optInt("likenum", rObj.optInt("like_num", 0))
        val userAction = rObj.optJSONObject("userAction")
        val rIsLiked = (rObj.optInt("is_like", rObj.optInt("isLike", 0)) == 1) ||
            (userAction?.optInt("like", 0) == 1)
        val rDate = TextSanitizer.formatRelativeTime(rObj.optLong("dateline", 0L))

        if (rName.isBlank() || (rMsg.isBlank() && rPic.isBlank())) return null
        return SubReplyItem(
            id = rId.ifBlank { "${rUid}_${rName}_${rMsg.hashCode()}" },
            uid = rUid,
            username = rName,
            replyUsername = rToName,
            message = rMsg,
            pic = rPic,
            isAuthor = rIsAuthor,
            likeNum = rLikeNum,
            isLiked = rIsLiked,
            dateString = rDate
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
        val beLikeNum = data.optInt("be_like_num", data.optInt("beLikeNum", 0))
        val userAction = data.optJSONObject("userAction")
        val isFollow = (data.optInt("isFollow", data.optInt("is_follow", 0)) == 1) ||
            (userAction?.optInt("follow", 0) == 1)
        val isSpecialFollow = (data.optInt("isSpecialFollow", data.optInt("is_special_follow", 0)) == 1) ||
            (userAction?.optInt("specialFollow", 0) == 1)
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
            isSelf = isSelf,
            isSpecialFollow = isSpecialFollow,
            beLikeNum = beLikeNum
        )
    }

    fun parseTopicDetail(responseBody: String): TopicItem? {
        if (responseBody.isBlank()) return null
        val json = JSONObject(responseBody)
        val obj = json.optJSONObject("data") ?: return null
        return parseTopicObject(obj)
    }

    fun parseTopicList(responseBody: String): List<TopicItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<TopicItem>()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.optJSONObject(i) ?: continue
            val entities = obj.optJSONArray("entities")
            if (entities != null && entities.length() > 0) {
                for (j in 0 until entities.length()) {
                    entities.optJSONObject(j)?.let(::parseTopicObject)?.let(result::add)
                }
            } else {
                parseTopicObject(obj)?.let(result::add)
            }
        }
        return result
    }

    private fun parseTopicObject(obj: JSONObject): TopicItem? {
        val title = optStringClean(obj, "title", "tag", "tag_name").trim().removeSurrounding("#")
        if (title.isBlank()) return null
        val id = optStringClean(obj, "id", "tagId", "tag_id")
        val logo = TextSanitizer.normalizeImageUrl(optStringClean(obj, "logo", "icon", "pic"))
        val description = TextSanitizer.cleanHtmlTags(optStringClean(obj, "description", "intro", "subTitle"))
        val feedNum = obj.optInt("feed_num", obj.optInt("feedNum", obj.optInt("commentnum", obj.optInt("num", 0))))
        val followNum = obj.optInt("follow_num", obj.optInt("followNum", obj.optInt("follownum", 0)))
        val hotNum = obj.optInt("hot_num", obj.optInt("hotNum", 0))
        val userAction = obj.optJSONObject("userAction")
        val isFollow = (obj.optInt("isFollow", obj.optInt("is_follow", 0)) == 1) ||
            (userAction?.optInt("follow", 0) == 1)
        return TopicItem(
            id = id.ifBlank { title },
            title = title,
            logo = logo,
            description = description,
            feedNum = feedNum,
            followNum = followNum,
            isFollow = isFollow,
            hotNum = hotNum
        )
    }

    fun parseUserList(responseBody: String, isFollowList: Boolean = true, selfUid: String = ""): List<UserCardItem> {
        if (responseBody.isBlank()) return emptyList()
        val result = mutableListOf<UserCardItem>()
        val json = JSONObject(responseBody)
        val arr = json.optJSONArray("data") ?: return emptyList()
        val normalizedSelfUid = selfUid.trim()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

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
        return words.distinct()
    }

    fun parseNotificationCount(responseBody: String): NotificationCount {
        if (responseBody.isBlank()) return NotificationCount()
        val json = JSONObject(responseBody)
        val data = json.optJSONObject("data") ?: return NotificationCount()
        return NotificationCount(
            comment = data.optInt("commentme", data.optInt("notification", 0)),
            atMe = data.optInt("atme", 0),
            atCommentMe = data.optInt("atcommentme", 0),
            feedLike = data.optInt("feedlike", 0),
            follow = data.optInt("contacts_follow", 0),
            message = data.optInt("message", 0),
            badge = data.optInt("badge", 0)
        )
    }

    fun parseNotificationList(responseBody: String): List<NotificationItem> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val id = optStringClean(obj, "id", "entityId").ifBlank { "n_$i" }
                val fromUserInfo = obj.optJSONObject("fromUserInfo")
                    ?: obj.optJSONObject("likeUserInfo")
                    ?: obj.optJSONObject("userInfo")
                val fromUid = optStringClean(obj, "fromuid", "likeUid", "uid")
                    .ifBlank { fromUserInfo?.let { optStringClean(it, "uid") }.orEmpty() }
                val fromUsername = optStringClean(obj, "fromusername", "likeUsername", "username")
                    .ifBlank { fromUserInfo?.let { optStringClean(it, "username", "displayUserName") }.orEmpty() }
                    .ifBlank { "酷友" }
                val fromAvatar = TextSanitizer.normalizeImageUrl(
                    optStringClean(obj, "fromUserAvatar", "likeAvatar", "userAvatar")
                        .ifBlank { fromUserInfo?.let { optStringClean(it, "userAvatar", "avatar") }.orEmpty() }
                )
                val note = TextSanitizer.cleanHtmlTags(optStringClean(obj, "note"))
                val message = TextSanitizer.cleanHtmlTags(optStringClean(obj, "message", "reply_message"))
                val feedObj = obj.optJSONObject("feed") ?: obj.optJSONObject("extra")
                val feedId = optStringClean(obj, "fid", "feed_id", "source_id")
                    .ifBlank { feedObj?.let { optStringClean(it, "id", "feed_id") }.orEmpty() }
                    .ifBlank { extractFeedIdFromUrl(optStringClean(obj, "url", "note")) }
                val targetTitle = TextSanitizer.cleanHtmlTags(
                    optStringClean(obj, "feedTitle", "title", "rtitle")
                        .ifBlank { feedObj?.let { optStringClean(it, "title", "message") }.orEmpty() }
                )
                val dateline = obj.optLong("likeTime", obj.optLong("dateline", 0L))
                val dateString = TextSanitizer.formatRelativeTime(dateline)
                val isNew = obj.optInt("isnew", 0) == 1
                val type = optStringClean(obj, "type", "entityType")

                if (note.isBlank() && message.isBlank() && targetTitle.isBlank()) continue
                add(
                    NotificationItem(
                        id = id,
                        type = type,
                        fromUid = fromUid,
                        fromUsername = fromUsername,
                        fromAvatar = fromAvatar,
                        note = note,
                        message = message,
                        targetTitle = targetTitle,
                        feedId = feedId,
                        dateline = dateline,
                        dateString = dateString,
                        isNew = isNew
                    )
                )
            }
        }
    }

    fun parseMessageSessionList(responseBody: String, selfUid: String = ""): List<MessageSession> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val id = optStringClean(obj, "id", "entityId")
                val ukey = optStringClean(obj, "ukey")
                if (ukey.isBlank() && id.isBlank()) continue

                val msgUserInfo = obj.optJSONObject("messageUserInfo")
                    ?: obj.optJSONObject("userInfo")
                    ?: obj.optJSONObject("fromUserInfo")
                var partnerUid = optStringClean(obj, "messageUid", "fromuid", "uid")
                    .ifBlank { msgUserInfo?.let { optStringClean(it, "uid") }.orEmpty() }
                if (partnerUid == selfUid && ukey.contains("_")) {
                    val parts = ukey.split("_")
                    partnerUid = parts.firstOrNull { it.isNotBlank() && it != selfUid } ?: partnerUid
                }
                val partnerUsername = optStringClean(obj, "messageUsername", "fromusername", "username")
                    .ifBlank { msgUserInfo?.let { optStringClean(it, "username", "displayUserName") }.orEmpty() }
                    .ifBlank { "酷友" }
                val partnerAvatar = TextSanitizer.normalizeImageUrl(
                    optStringClean(obj, "messageUserAvatar", "fromUserAvatar", "userAvatar")
                        .ifBlank { msgUserInfo?.let { optStringClean(it, "userAvatar", "avatar") }.orEmpty() }
                )
                val rawLastMsg = optStringClean(obj, "message")
                val lastMessage = TextSanitizer.cleanHtmlTags(rawLastMsg).ifBlank {
                    if (optStringClean(obj, "message_pic").isNotBlank()) "[图片]" else ""
                }
                val dateline = obj.optLong("dateline", 0L)
                val dateString = TextSanitizer.formatRelativeTime(dateline)
                val unreadNum = obj.optInt("unreadNum", obj.optInt("isnew", 0))
                val isTop = obj.optInt("is_top", obj.optInt("isTop", 0)) == 1

                add(
                    MessageSession(
                        id = id.ifBlank { ukey },
                        ukey = ukey.ifBlank { id },
                        partnerUid = partnerUid,
                        partnerUsername = partnerUsername,
                        partnerAvatar = partnerAvatar,
                        lastMessage = lastMessage,
                        dateline = dateline,
                        dateString = dateString,
                        unreadNum = unreadNum,
                        isTop = isTop
                    )
                )
            }
        }
    }

    fun parseChatMessageList(responseBody: String, selfUid: String): List<ChatMessage> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val entityType = optStringClean(obj, "entityType").lowercase(Locale.ROOT)
                val id = optStringClean(obj, "id", "entityId").ifBlank { "msg_$i" }
                val ukey = optStringClean(obj, "ukey")
                val dateline = obj.optLong("dateline", 0L)
                val dateString = TextSanitizer.formatRelativeTime(dateline)

                if (entityType == "messageextra") {
                    val notice = TextSanitizer.cleanHtmlTags(optStringClean(obj, "message", "title", "note"))
                    if (notice.isNotBlank()) {
                        add(
                            ChatMessage(
                                id = id,
                                ukey = ukey,
                                message = notice,
                                dateline = dateline,
                                dateString = dateString,
                                isSystemNotice = true
                            )
                        )
                    }
                    continue
                }

                val fromUserInfo = obj.optJSONObject("fromUserInfo") ?: obj.optJSONObject("userInfo")
                val fromUid = optStringClean(obj, "fromuid", "uid")
                    .ifBlank { fromUserInfo?.let { optStringClean(it, "uid") }.orEmpty() }
                val fromUsername = optStringClean(obj, "fromusername", "username")
                    .ifBlank { fromUserInfo?.let { optStringClean(it, "username", "displayUserName") }.orEmpty() }
                    .ifBlank { "酷友" }
                val fromAvatar = TextSanitizer.normalizeImageUrl(
                    optStringClean(obj, "fromUserAvatar", "userAvatar")
                        .ifBlank { fromUserInfo?.let { optStringClean(it, "userAvatar", "avatar") }.orEmpty() }
                )
                val message = TextSanitizer.cleanHtmlTags(optStringClean(obj, "message"))
                val messagePic = TextSanitizer.normalizeImageUrl(optStringClean(obj, "message_pic", "pic"))
                if (message.isBlank() && messagePic.isBlank()) continue

                val isSelf = selfUid.isNotBlank() && fromUid == selfUid
                add(
                    ChatMessage(
                        id = id,
                        ukey = ukey,
                        fromUid = fromUid,
                        fromUsername = fromUsername,
                        fromAvatar = fromAvatar,
                        message = message,
                        messagePic = messagePic,
                        dateline = dateline,
                        dateString = dateString,
                        isSelf = isSelf
                    )
                )
            }
        }
    }

    fun parseCollectionList(responseBody: String): List<CollectionItem> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val entityType = optStringClean(obj, "entityType").lowercase(Locale.ROOT)
                if (entityType.isNotBlank() && entityType != "collection") continue
                val id = optStringClean(obj, "id", "collectionId", "entityId")
                val title = optStringClean(obj, "title", "name")
                if (id.isBlank() || title.isBlank()) continue
                val rawCover = optStringClean(obj, "cover", "coverPic", "cover_pic", "pic", "logo")
                val cover = if (rawCover.startsWith("http", ignoreCase = true) || rawCover.startsWith("//")) {
                    TextSanitizer.normalizeImageUrl(rawCover)
                } else if (rawCover.isNotBlank()) {
                    "https://image.coolapk.com/${rawCover.trimStart('/')}"
                } else {
                    ""
                }
                add(
                    CollectionItem(
                        id = id,
                        uid = optStringClean(obj, "uid", "userId", "user_id"),
                        title = title,
                        description = TextSanitizer.cleanHtmlTags(optStringClean(obj, "description", "summary")),
                        cover = cover,
                        itemNum = obj.optInt("itemNum", obj.optInt("item_num", obj.optInt("itemnum", obj.optInt("feed_num", 0)))),
                        followNum = obj.optInt("follownum", obj.optInt("followNum", obj.optInt("follow_num", 0))),
                        isOpen = obj.optInt("isOpen", obj.optInt("is_open", 1)) != 0
                    )
                )
            }
        }
    }

    fun parseProductDetail(responseBody: String): ProductItem? {
        if (responseBody.isBlank()) return null
        val json = JSONObject(responseBody)
        val obj = json.optJSONObject("data") ?: return null
        return parseProductObject(obj)
    }

    fun parseProductList(responseBody: String): List<ProductItem> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val entities = obj.optJSONArray("entities")
                if (entities != null && entities.length() > 0) {
                    for (j in 0 until entities.length()) {
                        entities.optJSONObject(j)?.let(::parseProductObject)?.let(::add)
                    }
                } else {
                    parseProductObject(obj)?.let(::add)
                }
            }
        }
    }

    private fun parseProductObject(obj: JSONObject): ProductItem? {
        val id = optStringClean(obj, "id", "productId", "entityId")
        val title = optStringClean(obj, "title", "name")
        if (id.isBlank() || title.isBlank()) return null
        val logo = TextSanitizer.normalizeImageUrl(optStringClean(obj, "logo", "cover", "pic"))
        val categoryName = optStringClean(obj, "category_name", "brand_name", "subTitle")
        val score = obj.optDouble("rating_average_score", obj.optDouble("score", 0.0)).toFloat()
        val ratingCount = obj.optInt("rating_total_num", obj.optInt("vote_count", 0))
        val followNum = obj.optInt("follow_num", obj.optInt("followNum", 0))
        val feedNum = obj.optInt("feed_num", obj.optInt("feedNum", obj.optInt("feed_comment_num", 0)))
        val priceText = optStringClean(obj, "price_min", "price", "release_time")
        val description = TextSanitizer.cleanHtmlTags(optStringClean(obj, "description", "short_description", "subTitle"))
        val userAction = obj.optJSONObject("userAction")
        val isFollow = (obj.optInt("isFollow", obj.optInt("is_follow", 0)) == 1) ||
            (userAction?.optInt("follow", 0) == 1)

        val specs = mutableListOf<ProductSpecItem>()
        val configRows = obj.optJSONArray("configRows")
        if (configRows != null) {
            for (i in 0 until configRows.length()) {
                val cfg = configRows.optJSONObject(i) ?: continue
                val cfgTitle = optStringClean(cfg, "title", "name")
                val cfgPrice = optStringClean(cfg, "price")
                val cfgDesc = optStringClean(cfg, "config_data", "description", "subTitle")
                if (cfgTitle.isNotBlank()) {
                    specs.add(
                        ProductSpecItem(
                            group = "配置版本",
                            name = cfgTitle,
                            value = if (cfgPrice.isNotBlank() && cfgPrice != "0") "¥$cfgPrice" else cfgDesc
                        )
                    )
                }
            }
        }
        val tagArr = obj.optJSONArray("tagArr")
        if (tagArr != null && tagArr.length() > 0) {
            val tags = (0 until tagArr.length()).mapNotNull { tagArr.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
            if (tags.isNotEmpty()) {
                specs.add(ProductSpecItem(group = "亮点标签", name = "特性", value = tags.joinToString(" · ")))
            }
        }

        return ProductItem(
            id = id,
            title = title,
            logo = logo,
            categoryName = categoryName,
            score = score,
            ratingCount = ratingCount,
            followNum = followNum,
            feedNum = feedNum,
            priceText = priceText,
            description = description,
            isFollow = isFollow,
            specs = specs
        )
    }

    fun parseAppList(responseBody: String): List<AppItem> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val entityType = optStringClean(obj, "entityType").lowercase(Locale.ROOT)
                if (entityType.isNotBlank() && entityType != "apk" && entityType != "app") continue
                val id = optStringClean(obj, "id", "apkid", "entityId")
                val title = optStringClean(obj, "title", "shorttitle", "appname")
                if (title.isBlank()) continue
                add(
                    AppItem(
                        id = id.ifBlank { title },
                        packageName = optStringClean(obj, "apkname", "packageName"),
                        title = title,
                        logo = TextSanitizer.normalizeImageUrl(optStringClean(obj, "logo", "icon")),
                        versionName = optStringClean(obj, "apkversionname", "versionName"),
                        score = obj.optDouble("score", 0.0).toFloat(),
                        downloadCount = optStringClean(obj, "downnum", "apksize"),
                        description = TextSanitizer.cleanHtmlTags(optStringClean(obj, "shortlabel", "description", "subTitle"))
                    )
                )
            }
        }
    }

    fun parseMyReplyList(responseBody: String): List<MyReplyItem> {
        if (responseBody.isBlank()) return emptyList()
        val json = JSONObject(responseBody)
        val dataArray = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until dataArray.length()) {
                val obj = dataArray.optJSONObject(i) ?: continue
                val id = optStringClean(obj, "id", "reply_id", "entityId")
                val message = TextSanitizer.cleanHtmlTags(optStringClean(obj, "message", "reply_message"))
                if (id.isBlank() && message.isBlank()) continue
                val feedObj = obj.optJSONObject("feed")
                val feedId = optStringClean(obj, "fid", "feed_id")
                    .ifBlank { feedObj?.let { optStringClean(it, "id") }.orEmpty() }
                val feedTitle = TextSanitizer.cleanHtmlTags(
                    optStringClean(obj, "feedTitle", "rtitle")
                        .ifBlank { feedObj?.let { optStringClean(it, "title", "message") }.orEmpty() }
                )
                val targetUsername = optStringClean(obj, "rusername", "feedUsername")
                val dateline = obj.optLong("dateline", 0L)
                add(
                    MyReplyItem(
                        id = id.ifBlank { "r_$i" },
                        feedId = feedId,
                        feedTitle = feedTitle,
                        message = message,
                        targetUsername = targetUsername,
                        dateString = TextSanitizer.formatRelativeTime(dateline),
                        likeNum = obj.optInt("likenum", obj.optInt("like_num", 0))
                    )
                )
            }
        }
    }

    private val FEED_URL_ID_REGEX = Regex("""/feed/(\d+)""")

    private fun extractFeedIdFromUrl(text: String): String {
        if (text.isBlank()) return ""
        return FEED_URL_ID_REGEX.find(text)?.groupValues?.getOrNull(1).orEmpty()
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
