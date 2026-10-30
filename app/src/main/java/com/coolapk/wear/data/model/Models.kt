package com.coolapk.wear.data.model

data class AuthCredentials(
    val cookie: String = "",
    val ddid: String = "",
    val uid: String = "",
    val username: String = "",
    val avatar: String = ""
) {
    val isLoggedIn: Boolean
        get() = cookie.isNotBlank() && (cookie.contains("uid=") || cookie.contains("token="))
}

data class FeedItem(
    val id: String,
    val uid: String,
    val username: String,
    val userAvatar: String,
    val dateline: Long,
    val dateString: String,
    val deviceTitle: String,
    val title: String,
    val message: String,
    val picArr: List<String> = emptyList(),
    val likeNum: Int = 0,
    val replyNum: Int = 0,
    val isLiked: Boolean = false,
    val isFavorite: Boolean = false
)

data class SubReplyItem(
    val id: String,
    val uid: String = "",
    val username: String = "",
    val replyUsername: String = "",
    val message: String = "",
    val pic: String = "",
    val isAuthor: Boolean = false
)

data class CommentItem(
    val id: String,
    val uid: String,
    val username: String,
    val userAvatar: String,
    val dateline: Long,
    val dateString: String,
    val message: String,
    val pic: String = "",
    val replyNum: Int = 0,
    val likeNum: Int = 0,
    val isLiked: Boolean = false,
    val isAuthor: Boolean = false,
    val isTop: Boolean = false,
    val subReplies: List<SubReplyItem> = emptyList()
)

data class FeedDetail(
    val feed: FeedItem,
    val comments: List<CommentItem> = emptyList()
)

data class UserProfile(
    val uid: String,
    val username: String,
    val userAvatar: String,
    val bio: String = "",
    val level: Int = 0,
    val followNum: Int = 0,
    val fansNum: Int = 0,
    val feedNum: Int = 0,
    val isFollow: Boolean = false,
    val verifyTitle: String = "",
    val isSelf: Boolean = false
)

data class UserCardItem(
    val uid: String,
    val username: String,
    val userAvatar: String,
    val bio: String = "",
    val isFollow: Boolean = false
)

data class TopicItem(
    val id: String,
    val title: String,
    val logo: String = "",
    val description: String = "",
    val feedNum: Int = 0,
    val followNum: Int = 0
)
