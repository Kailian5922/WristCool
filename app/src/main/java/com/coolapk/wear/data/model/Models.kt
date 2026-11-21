package com.coolapk.wear.data.model

import com.coolapk.wear.data.util.CookieParser

data class AuthCredentials(
    val cookie: String = "",
    val ddid: String = "",
    val uid: String = "",
    val username: String = "",
    val avatar: String = ""
) {
    val isLoggedIn: Boolean
        get() = CookieParser.hasValidAuth(cookie)
}

data class ForwardFeedInfo(
    val id: String,
    val uid: String = "",
    val username: String = "",
    val title: String = "",
    val message: String = "",
    val pic: String = ""
)

data class VoteOption(
    val id: String,
    val title: String,
    val voteNum: Int = 0,
    val colorHex: String = ""
)

data class VoteInfo(
    val id: String,
    val title: String = "投票",
    val totalVoteNum: Int = 0,
    val minSelect: Int = 1,
    val maxSelect: Int = 1,
    val isVoted: Boolean = false,
    val selectedOptionIds: List<String> = emptyList(),
    val options: List<VoteOption> = emptyList()
)

data class ArticleBlock(
    val type: String, // "text", "image", "shareUrl"
    val text: String = "",
    val imageUrl: String = "",
    val linkTitle: String = "",
    val linkUrl: String = ""
)

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
    val isFavorite: Boolean = false,
    val feedType: String = "feed",
    val forwardFeed: ForwardFeedInfo? = null,
    val vote: VoteInfo? = null,
    val articleBlocks: List<ArticleBlock> = emptyList(),
    val relationTopics: List<String> = emptyList(),
    val ratingScore: Float = 0f,
    val forwardNum: Int = 0,
    val favoriteNum: Int = 0,
    val targetProductId: String = "",
    val targetProductName: String = ""
)

data class SubReplyItem(
    val id: String,
    val uid: String = "",
    val username: String = "",
    val replyUsername: String = "",
    val message: String = "",
    val pic: String = "",
    val isAuthor: Boolean = false,
    val likeNum: Int = 0,
    val isLiked: Boolean = false,
    val dateString: String = ""
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
    val isSelf: Boolean = false,
    val isSpecialFollow: Boolean = false,
    val beLikeNum: Int = 0
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
    val followNum: Int = 0,
    val isFollow: Boolean = false,
    val hotNum: Int = 0
)

data class NotificationCount(
    val comment: Int = 0,
    val atMe: Int = 0,
    val atCommentMe: Int = 0,
    val feedLike: Int = 0,
    val follow: Int = 0,
    val message: Int = 0,
    val badge: Int = 0
) {
    val totalNotification: Int
        get() = comment + atMe + atCommentMe + feedLike + follow

    val totalUnread: Int
        get() = maxOf(badge, totalNotification + message)
}

data class NotificationItem(
    val id: String,
    val type: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromAvatar: String = "",
    val note: String = "",
    val message: String = "",
    val targetTitle: String = "",
    val feedId: String = "",
    val replyId: String = "",
    val dateline: Long = 0L,
    val dateString: String = "",
    val isNew: Boolean = false
)

data class MessageSession(
    val id: String,
    val ukey: String,
    val partnerUid: String,
    val partnerUsername: String,
    val partnerAvatar: String = "",
    val lastMessage: String = "",
    val dateline: Long = 0L,
    val dateString: String = "",
    val unreadNum: Int = 0,
    val isTop: Boolean = false
)

data class ChatMessage(
    val id: String,
    val ukey: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val fromAvatar: String = "",
    val message: String = "",
    val messagePic: String = "",
    val dateline: Long = 0L,
    val dateString: String = "",
    val isSelf: Boolean = false,
    val isSystemNotice: Boolean = false
)

data class CollectionItem(
    val id: String,
    val uid: String = "",
    val title: String,
    val description: String = "",
    val cover: String = "",
    val itemNum: Int = 0,
    val followNum: Int = 0,
    val isOpen: Boolean = true,
    val isDefault: Boolean = false
)

data class ProductSpecItem(
    val group: String = "",
    val name: String,
    val value: String
)

data class ProductItem(
    val id: String,
    val title: String,
    val logo: String = "",
    val categoryName: String = "",
    val score: Float = 0f,
    val ratingCount: Int = 0,
    val followNum: Int = 0,
    val feedNum: Int = 0,
    val priceText: String = "",
    val description: String = "",
    val isFollow: Boolean = false,
    val specs: List<ProductSpecItem> = emptyList()
)

data class AppItem(
    val id: String,
    val packageName: String = "",
    val title: String,
    val logo: String = "",
    val versionName: String = "",
    val score: Float = 0f,
    val downloadCount: String = "",
    val description: String = ""
)

data class MyReplyItem(
    val id: String,
    val feedId: String = "",
    val feedTitle: String = "",
    val message: String = "",
    val targetUsername: String = "",
    val dateString: String = "",
    val likeNum: Int = 0
)
