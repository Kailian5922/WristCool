package com.coolapk.wear.data.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolapkJsonParserTest {

    @Test
    fun `parseSingleFeed falls back to nested userInfo for username avatar and uid`() {
        val json = JSONObject(
            """
            {
              "entityType": "feed",
              "id": "12345",
              "title": "Alice的动态",
              "message": "Hello <br/><b>Coolapk</b> #WearOS#",
              "picArr": [],
              "pic": "http://image.coolapk.com/single.jpg",
              "likenum": 42,
              "replynum": 7,
              "userInfo": {
                "uid": "998877",
                "username": "Alice",
                "userAvatar": "//avatar.coolapk.com/a.png"
              },
              "userAction": {
                "like": 1,
                "collect": 1
              }
            }
            """.trimIndent()
        )

        val feed = CoolapkJsonParser.parseSingleFeed(json)
        assertNotNull(feed)
        assertEquals("12345", feed!!.id)
        assertEquals("998877", feed.uid)
        assertEquals("Alice", feed.username)
        assertEquals("https://avatar.coolapk.com/a.png", feed.userAvatar)
        assertEquals("", feed.title) // 过滤 "Alice的动态" 占位标题
        assertEquals("Hello \nCoolapk #WearOS#", feed.message)
        assertEquals(listOf("https://image.coolapk.com/single.jpg"), feed.picArr)
        assertTrue(feed.isLiked)
        assertTrue(feed.isFavorite)
    }

    @Test
    fun `parseFeedList flattens container entities and skips non-feed cards`() {
        val body = """
            {
              "data": [
                {
                  "entityType": "banner",
                  "id": "1",
                  "title": "Ad Banner",
                  "message": "Buy now"
                },
                {
                  "entityType": "card",
                  "id": "card_1",
                  "entities": [
                    {
                      "entityType": "feed",
                      "id": "2001",
                      "username": "Bob",
                      "uid": "100",
                      "message": "Nested real feed"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val feeds = CoolapkJsonParser.parseFeedList(body)
        assertEquals(1, feeds.size)
        assertEquals("2001", feeds[0].id)
        assertEquals("Bob", feeds[0].username)
    }

    @Test
    fun `parseCommentList resolves nested userInfo and extracts embedded gif`() {
        val body = """
            {
              "data": [
                {
                  "id": "c1",
                  "message": "Nice! <img src=\"http://img.coolapk.com/fun.gif\" />",
                  "replynum": 5,
                  "likenum": 3,
                  "is_feed_author": 1,
                  "userInfo": {
                    "uid": "555",
                    "username": "Commenter",
                    "userAvatar": "http://avatar.coolapk.com/c.jpg"
                  },
                  "replyRows": [
                    {
                      "id": "r1",
                      "userInfo": {
                        "uid": "666",
                        "username": "Replier"
                      },
                      "rusername": "Commenter",
                      "message": "Agreed [gif]http://img.coolapk.com/sub.gif[/gif]"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val comments = CoolapkJsonParser.parseCommentList(body)
        assertEquals(1, comments.size)
        val c = comments[0]
        assertEquals("555", c.uid)
        assertEquals("Commenter", c.username)
        assertEquals("https://avatar.coolapk.com/c.jpg", c.userAvatar)
        assertEquals("https://img.coolapk.com/fun.gif", c.pic)
        assertTrue(c.isAuthor)

        assertEquals(1, c.subReplies.size)
        assertEquals("666", c.subReplies[0].uid)
        assertEquals("Replier", c.subReplies[0].username)
        assertEquals("Commenter", c.subReplies[0].replyUsername)
        assertEquals("https://img.coolapk.com/sub.gif", c.subReplies[0].pic)
    }

    @Test
    fun `parseUserList extracts target users and filters out selfUid`() {
        val followBody = """
            {
              "data": [
                {
                  "uid": "1000",
                  "username": "Me",
                  "fUserInfo": {
                    "uid": "2000",
                    "username": "FollowedUser",
                    "userAvatar": "http://a.com/u2.png",
                    "bio": "Hello bio"
                  }
                },
                {
                  "uid": "1000",
                  "fuid": "1000",
                  "username": "Me"
                }
              ]
            }
        """.trimIndent()

        val users = CoolapkJsonParser.parseUserList(followBody, isFollowList = true, selfUid = "1000")
        assertEquals(1, users.size)
        assertEquals("2000", users[0].uid)
        assertEquals("FollowedUser", users[0].username)
        assertEquals("https://a.com/u2.png", users[0].userAvatar)
        assertEquals("Hello bio", users[0].bio)
    }

    @Test
    fun `parseUserProfile cleans null json fields and detects userAction follow`() {
        val data = JSONObject(
            """
            {
              "uid": "777",
              "username": "Tester",
              "bio": null,
              "verify_title": "null",
              "level": 9,
              "userAction": { "follow": 1 }
            }
            """.trimIndent()
        )
        val profile = CoolapkJsonParser.parseUserProfile(data, currentUid = "100")
        assertEquals("", profile.bio)
        assertEquals("", profile.verifyTitle)
        assertEquals(9, profile.level)
        assertTrue(profile.isFollow)
        assertFalse(profile.isSelf)
    }

    @Test
    fun `parseApiError detects negative status and error fields`() {
        assertEquals(
            "请先登录",
            CoolapkJsonParser.parseApiError("""{"status": -1, "message": "请先登录"}""")
        )
        assertEquals(
            "内容包含敏感词",
            CoolapkJsonParser.parseApiError("""{"error": "forbidden", "message": "内容包含敏感词"}""")
        )
        assertNull(CoolapkJsonParser.parseApiError("""{"data": {"likenum": 10}}"""))
    }

    @Test
    fun `parseSingleFeed extracts forwardSourceFeed vote articleBlocks and relationRows`() {
        val json = JSONObject(
            """
            {
              "entityType": "feed",
              "feedType": "feedArticle",
              "id": "88001",
              "uid": "101",
              "username": "GeekAuthor",
              "message": "",
              "message_title": "手表端体验评测",
              "message_raw_output": "[{\"type\":\"text\",\"message\":\"第一段<br/>正文\"},{\"type\":\"image\",\"url\":\"//img.coolapk.com/art1.jpg\",\"description\":\"图注1\"},{\"type\":\"shareUrl\",\"title\":\"原帖链接\",\"url\":\"https://www.coolapk.com/feed/1\"}]",
              "rating_score": 9.2,
              "forwardnum": 12,
              "favnum": 34,
              "forwardSourceFeed": {
                "id": "77001",
                "uid": "202",
                "username": "OrigUser",
                "message": "原动态内容",
                "picArr": ["http://img.coolapk.com/orig.png"]
              },
              "vote": {
                "id": "v99",
                "message_title": "你最喜欢的表盘尺寸？",
                "total_vote_num": 100,
                "min_select_num": 1,
                "max_select_num": 2,
                "user_vote": ["opt_b"],
                "options": [
                  {"id": "opt_b", "title": "44mm", "vote_num": 60, "order": 2, "color": "#0DAE62"},
                  {"id": "opt_a", "title": "40mm", "vote_num": 40, "order": 1}
                ]
              },
              "relationRows": [
                {"entityType": "product", "id": "p555", "title": "Galaxy Watch 7"},
                {"entityType": "topic", "id": "t1", "title": "#WearOS#"}
              ]
            }
            """.trimIndent()
        )

        val feed = CoolapkJsonParser.parseSingleFeed(json)
        assertNotNull(feed)
        feed!!
        assertEquals("手表端体验评测", feed.title)
        assertEquals("第一段\n正文", feed.message)
        assertEquals(3, feed.articleBlocks.size)
        assertEquals("text", feed.articleBlocks[0].type)
        assertEquals("https://img.coolapk.com/art1.jpg", feed.articleBlocks[1].imageUrl)
        assertEquals("图注1", feed.articleBlocks[1].text)
        assertEquals("shareUrl", feed.articleBlocks[2].type)
        assertEquals("原帖链接", feed.articleBlocks[2].linkTitle)

        assertNotNull(feed.forwardFeed)
        assertEquals("77001", feed.forwardFeed!!.id)
        assertEquals("OrigUser", feed.forwardFeed!!.username)
        assertEquals("https://img.coolapk.com/orig.png", feed.forwardFeed!!.pic)

        assertNotNull(feed.vote)
        val vote = feed.vote!!
        assertEquals("v99", vote.id)
        assertEquals(100, vote.totalVoteNum)
        assertEquals(2, vote.maxSelect)
        assertTrue(vote.isVoted)
        assertEquals(listOf("opt_b"), vote.selectedOptionIds)
        // Sorted by order: opt_a (1) before opt_b (2)
        assertEquals(listOf("opt_a", "opt_b"), vote.options.map { it.id })

        assertEquals("p555", feed.targetProductId)
        assertEquals("Galaxy Watch 7", feed.targetProductName)
        assertEquals(listOf("WearOS"), feed.relationTopics)
        assertEquals(9.2f, feed.ratingScore, 0.01f)
        assertEquals(12, feed.forwardNum)
        assertEquals(34, feed.favoriteNum)
    }

    @Test
    fun `parseFeedList unwraps cloud history wrapper entities`() {
        val body = """
            {
              "data": [
                {
                  "entityType": "history",
                  "id": "hist_1",
                  "feed": {
                    "entityType": "feed",
                    "id": "32100",
                    "uid": "88",
                    "username": "HistoryAuthor",
                    "message": "云端浏览历史动态"
                  }
                }
              ]
            }
        """.trimIndent()
        val feeds = CoolapkJsonParser.parseFeedList(body)
        assertEquals(1, feeds.size)
        assertEquals("32100", feeds[0].id)
        assertEquals("HistoryAuthor", feeds[0].username)
    }

    @Test
    fun `parseNotificationCount and parseNotificationList extract unread counts and feedId from url`() {
        val countBody = """
            {
              "data": {
                "commentme": 3,
                "atme": 1,
                "atcommentme": 2,
                "feedlike": 5,
                "contacts_follow": 4,
                "message": 6,
                "badge": 21
              }
            }
        """.trimIndent()
        val counts = CoolapkJsonParser.parseNotificationCount(countBody)
        assertEquals(3, counts.comment)
        assertEquals(1, counts.atMe)
        assertEquals(2, counts.atCommentMe)
        assertEquals(5, counts.feedLike)
        assertEquals(4, counts.follow)
        assertEquals(6, counts.message)
        assertEquals(21, counts.totalUnread)

        val listBody = """
            {
              "data": [
                {
                  "id": "n1",
                  "type": "comment",
                  "fromuid": "300",
                  "fromusername": "Notifier",
                  "note": "回复了你的动态 <a href=\"/feed/99881\">查看</a>",
                  "message": "太酷啦",
                  "isnew": 1,
                  "dateline": 1700000000
                }
              ]
            }
        """.trimIndent()
        val items = CoolapkJsonParser.parseNotificationList(listBody)
        assertEquals(1, items.size)
        assertEquals("99881", items[0].feedId)
        assertEquals("Notifier", items[0].fromUsername)
        assertTrue(items[0].isNew)
    }

    @Test
    fun `parseMessageSessionList and parseChatMessageList handle partner resolution and system notices`() {
        val sessionsBody = """
            {
              "data": [
                {
                  "id": "s1",
                  "ukey": "42_888",
                  "uid": "42",
                  "messageUserInfo": {
                    "uid": "888",
                    "username": "ChatPartner",
                    "userAvatar": "http://img.coolapk.com/p.png"
                  },
                  "message": "",
                  "message_pic": "http://img.coolapk.com/sent.jpg",
                  "unreadNum": 2,
                  "is_top": 1
                }
              ]
            }
        """.trimIndent()
        val sessions = CoolapkJsonParser.parseMessageSessionList(sessionsBody, selfUid = "42")
        assertEquals(1, sessions.size)
        assertEquals("888", sessions[0].partnerUid)
        assertEquals("ChatPartner", sessions[0].partnerUsername)
        assertEquals("[图片]", sessions[0].lastMessage)
        assertEquals(2, sessions[0].unreadNum)
        assertTrue(sessions[0].isTop)

        val chatBody = """
            {
              "data": [
                {
                  "entityType": "messageExtra",
                  "id": "sys_1",
                  "ukey": "42_888",
                  "message": "以上为历史消息"
                },
                {
                  "entityType": "message",
                  "id": "m_1",
                  "ukey": "42_888",
                  "fromuid": "42",
                  "fromusername": "Me",
                  "message": "你好手表端"
                },
                {
                  "entityType": "message",
                  "id": "m_2",
                  "ukey": "42_888",
                  "fromuid": "888",
                  "fromusername": "ChatPartner",
                  "message": "收到！",
                  "message_pic": "//img.coolapk.com/c.png"
                }
              ]
            }
        """.trimIndent()
        val messages = CoolapkJsonParser.parseChatMessageList(chatBody, selfUid = "42")
        assertEquals(3, messages.size)
        assertTrue(messages[0].isSystemNotice)
        assertEquals("以上为历史消息", messages[0].message)
        assertTrue(messages[1].isSelf)
        assertFalse(messages[2].isSelf)
        assertEquals("https://img.coolapk.com/c.png", messages[2].messagePic)
    }

    @Test
    fun `parseCollectionList parseProductDetail parseAppList and parseMyReplyList work accurately`() {
        val colBody = """
            {
              "data": [
                {
                  "entityType": "collection",
                  "id": "c10",
                  "uid": "42",
                  "title": "手表好物",
                  "cover": "cover/path.jpg",
                  "itemNum": 15,
                  "follownum": 4,
                  "isOpen": 0
                }
              ]
            }
        """.trimIndent()
        val cols = CoolapkJsonParser.parseCollectionList(colBody)
        assertEquals(1, cols.size)
        assertEquals("https://image.coolapk.com/cover/path.jpg", cols[0].cover)
        assertEquals(15, cols[0].itemNum)
        assertFalse(cols[0].isOpen)

        val prodBody = """
            {
              "data": {
                "id": "p100",
                "title": "Pixel Watch 3",
                "category_name": "智能手表",
                "rating_average_score": 8.9,
                "rating_total_num": 210,
                "follow_num": 5000,
                "price_min": "2499",
                "userAction": {"follow": 1},
                "configRows": [
                  {"title": "45mm 蓝牙版", "price": "2799"}
                ],
                "tagArr": ["OLED", "Wear OS 5"]
              }
            }
        """.trimIndent()
        val prod = CoolapkJsonParser.parseProductDetail(prodBody)
        assertNotNull(prod)
        prod!!
        assertEquals("Pixel Watch 3", prod.title)
        assertTrue(prod.isFollow)
        assertEquals(2, prod.specs.size)
        assertEquals("¥2799", prod.specs[0].value)
        assertEquals("OLED · Wear OS 5", prod.specs[1].value)

        val appBody = """
            {
              "data": [
                {
                  "entityType": "apk",
                  "id": "a1",
                  "apkname": "com.coolapk.wear",
                  "title": "WristCool",
                  "apkversionname": "2.0.0",
                  "score": 4.9,
                  "downnum": "10万+"
                }
              ]
            }
        """.trimIndent()
        val apps = CoolapkJsonParser.parseAppList(appBody)
        assertEquals(1, apps.size)
        assertEquals("com.coolapk.wear", apps[0].packageName)
        assertEquals("2.0.0", apps[0].versionName)

        val repliesBody = """
            {
              "data": [
                {
                  "id": "mr1",
                  "fid": "555",
                  "feedTitle": "酷安动态标题",
                  "rusername": "原作者",
                  "message": "我的精彩回复",
                  "likenum": 8
                }
              ]
            }
        """.trimIndent()
        val myReplies = CoolapkJsonParser.parseMyReplyList(repliesBody)
        assertEquals(1, myReplies.size)
        assertEquals("555", myReplies[0].feedId)
        assertEquals("原作者", myReplies[0].targetUsername)
        assertEquals(8, myReplies[0].likeNum)
    }
}
