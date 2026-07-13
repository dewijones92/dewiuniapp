package com.dewijones92.uniapp.innertube.comments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsResponseParserTest {

    private fun res(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/$name")) { "fixture $name missing" }
            .bufferedReader().readText()

    @Test
    fun `finds the comment section continuation token`() {
        assertEquals(
            "COMMENT_TOKEN_123456789012",
            CommentsResponseParser.findCommentsContinuation(res("comments_section_sample.json")),
        )
    }

    @Test
    fun `no comment section means no token`() {
        assertNull(CommentsResponseParser.findCommentsContinuation("""{"contents":{}}"""))
    }

    @Test
    fun `parses top-level comments and drops replies`() {
        val comments = (
            CommentsResponseParser.parseComments(res("comments_page_sample.json"))
                as CommentsResult.Success
            ).comments

        assertEquals(listOf("c1", "c2"), comments.map { it.id })
    }

    @Test
    fun `maps text, author, avatar, time, counts and badges`() {
        val first = (
            CommentsResponseParser.parseComments(res("comments_page_sample.json"))
                as CommentsResult.Success
            ).comments.first()

        assertEquals("Alice", first.author)
        assertEquals("First comment", first.text)
        assertEquals("2 days ago", first.publishedTime)
        assertEquals("393", first.likeCount)
        assertEquals(21, first.replyCount)
        assertTrue(first.isVerified)
        assertFalse(first.isCreator)
        assertEquals("https://yt3.example/Alice.jpg", first.authorAvatarUrl?.value)
    }

    @Test
    fun `creator badge is read`() {
        val creator = (
            CommentsResponseParser.parseComments(res("comments_page_sample.json"))
                as CommentsResult.Success
            ).comments.first { it.id == "c2" }
        assertTrue(creator.isCreator)
    }

    @Test
    fun `a response with no comment entities is empty, not an error`() {
        val result = CommentsResponseParser.parseComments("""{"frameworkUpdates":{}}""")
        assertEquals(emptyList<Comment>(), (result as CommentsResult.Success).comments)
    }

    @Test
    fun `unparseable json is a failure`() {
        assertTrue(CommentsResponseParser.parseComments("nope") is CommentsResult.Failure)
    }
}
