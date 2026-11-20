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
}
