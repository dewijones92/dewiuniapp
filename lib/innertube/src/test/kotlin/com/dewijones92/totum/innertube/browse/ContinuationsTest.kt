package com.dewijones92.totum.innertube.browse

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuationsTest {

    private fun find(body: String) = Continuations.find(Json.parseToJsonElement(body))

    /** The shape the TV and WEB clients use today. */
    @Test
    fun `finds a continuationCommand token`() {
        val token = find(
            """
            {"contents":{"items":[
              {"tileRenderer":{}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"CBQ%3D"}}}}
            ]}}
            """.trimIndent(),
        )

        assertEquals("CBQ%3D", token?.value)
    }

    /**
     * YouTube has shipped at least three continuation shapes and still serves the older
     * ones on some feeds. Keying on one path means silently ceasing to paginate, which
     * looks exactly like reaching the end.
     */
    @Test
    fun `finds the older nextContinuationData shape`() {
        val token = find("""{"continuations":[{"nextContinuationData":{"continuation":"legacy-token"}}]}""")

        assertEquals("legacy-token", token?.value)
    }

    @Test
    fun `finds a reloadContinuationData shape`() {
        val token = find("""{"continuations":[{"reloadContinuationData":{"continuation":"reload-token"}}]}""")

        assertEquals("reload-token", token?.value)
    }

    @Test
    fun `no token means the last page`() {
        assertNull(find("""{"contents":{"items":[{"tileRenderer":{"contentId":"abc"}}]}}"""))
    }

    /**
     * A shelf inside a feed carries its own continuation ("more from this channel"),
     * which would page the shelf instead of the feed. The feed's own token comes last.
     */
    @Test
    fun `takes the last token, so a nested shelf does not win`() {
        val token = find(
            """
            {"contents":{"shelves":[
              {"shelf":{"continuations":[{"nextContinuationData":{"continuation":"shelf-token"}}]}}
            ],"footer":{"continuationItemRenderer":
              {"continuationEndpoint":{"continuationCommand":{"token":"feed-token"}}}}}}
            """.trimIndent(),
        )

        assertEquals("feed-token", token?.value)
    }

    @Test
    fun `a blank token is treated as absent rather than crashing`() {
        val body = """{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":""}}}}"""

        assertNull(find(body))
    }
}
