package com.coolapk.wear.data.repository

import android.content.Context
import com.coolapk.wear.data.model.CommentItem
import com.coolapk.wear.data.model.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class BlockedUserEntry(
    val uid: String,
    val username: String
)

data class BlacklistState(
    val blockedUsers: List<BlockedUserEntry> = emptyList(),
    val blockedKeywords: List<String> = emptyList()
) {
    val blockedUids: Set<String>
        get() = blockedUsers.mapTo(HashSet()) { it.uid }

    fun isUserBlocked(uid: String): Boolean = uid.isNotBlank() && blockedUsers.any { it.uid == uid }

    fun shouldFilterFeed(feed: FeedItem): Boolean {
        if (feed.uid.isNotBlank() && blockedUsers.any { it.uid == feed.uid }) return true
        val fwUid = feed.forwardFeed?.uid.orEmpty()
        if (fwUid.isNotBlank() && blockedUsers.any { it.uid == fwUid }) return true
        if (blockedKeywords.isEmpty()) return false
        val hay = buildString {
            append(feed.title).append(' ')
            append(feed.message).append(' ')
            append(feed.username).append(' ')
            append(feed.deviceTitle).append(' ')
            feed.relationTopics.forEach { append(it).append(' ') }
            feed.forwardFeed?.let { append(it.message).append(' ') }
        }
        return blockedKeywords.any { kw -> kw.isNotBlank() && hay.contains(kw, ignoreCase = true) }
    }

    fun shouldFilterComment(comment: CommentItem): Boolean {
        if (comment.uid.isNotBlank() && blockedUsers.any { it.uid == comment.uid }) return true
        if (blockedKeywords.isEmpty()) return false
        return blockedKeywords.any { kw ->
            kw.isNotBlank() && (
                comment.message.contains(kw, ignoreCase = true) ||
                    comment.username.contains(kw, ignoreCase = true)
                )
        }
    }
}

/**
 * 本地用户与关键词/话题屏蔽过滤仓储（参考 c001apk-Revived 与 coolapk-desktop）。
 */
class BlacklistRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BlacklistState> = _state.asStateFlow()

    fun blockUser(uid: String, username: String) {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty()) return
        val cleanName = username.trim().ifBlank { "UID:$cleanUid" }
        _state.update { current ->
            if (current.blockedUsers.any { it.uid == cleanUid }) return@update current
            val updated = current.copy(
                blockedUsers = listOf(BlockedUserEntry(cleanUid, cleanName)) + current.blockedUsers
            )
            persist(updated)
            updated
        }
    }

    fun unblockUser(uid: String) {
        val cleanUid = uid.trim()
        _state.update { current ->
            val updated = current.copy(
                blockedUsers = current.blockedUsers.filterNot { it.uid == cleanUid }
            )
            persist(updated)
            updated
        }
    }

    fun addKeyword(keyword: String) {
        val clean = keyword.trim().removeSurrounding("#").trim()
        if (clean.isEmpty()) return
        _state.update { current ->
            if (current.blockedKeywords.any { it.equals(clean, ignoreCase = true) }) return@update current
            val updated = current.copy(blockedKeywords = listOf(clean) + current.blockedKeywords)
            persist(updated)
            updated
        }
    }

    fun removeKeyword(keyword: String) {
        _state.update { current ->
            val updated = current.copy(
                blockedKeywords = current.blockedKeywords.filterNot { it.equals(keyword, ignoreCase = true) }
            )
            persist(updated)
            updated
        }
    }

    fun filterFeeds(feeds: List<FeedItem>): List<FeedItem> {
        val snapshot = _state.value
        if (snapshot.blockedUsers.isEmpty() && snapshot.blockedKeywords.isEmpty()) return feeds
        return feeds.filterNot { snapshot.shouldFilterFeed(it) }
    }

    fun filterComments(comments: List<CommentItem>): List<CommentItem> {
        val snapshot = _state.value
        if (snapshot.blockedUsers.isEmpty() && snapshot.blockedKeywords.isEmpty()) return comments
        return comments.filterNot { snapshot.shouldFilterComment(it) }
    }

    private fun load(): BlacklistState {
        val users = mutableListOf<BlockedUserEntry>()
        val keywords = mutableListOf<String>()
        runCatching {
            val usersJson = prefs.getString(KEY_USERS, null)
            if (!usersJson.isNullOrBlank()) {
                val arr = JSONArray(usersJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uid = obj.optString("uid", "").trim()
                    if (uid.isNotBlank()) {
                        users.add(BlockedUserEntry(uid, obj.optString("username", "UID:$uid")))
                    }
                }
            }
            val kwJson = prefs.getString(KEY_KEYWORDS, null)
            if (!kwJson.isNullOrBlank()) {
                val arr = JSONArray(kwJson)
                for (i in 0 until arr.length()) {
                    val kw = arr.optString(i, "").trim()
                    if (kw.isNotBlank()) keywords.add(kw)
                }
            }
        }
        return BlacklistState(users, keywords)
    }

    private fun persist(state: BlacklistState) {
        val usersArr = JSONArray()
        state.blockedUsers.forEach { entry ->
            usersArr.put(JSONObject().put("uid", entry.uid).put("username", entry.username))
        }
        val kwArr = JSONArray()
        state.blockedKeywords.forEach { kwArr.put(it) }
        prefs.edit()
            .putString(KEY_USERS, usersArr.toString())
            .putString(KEY_KEYWORDS, kwArr.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "coolapk_wear_blacklist"
        private const val KEY_USERS = "blocked_users_json"
        private const val KEY_KEYWORDS = "blocked_keywords_json"
    }
}
