package cc.orbexa.hhy.activity

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R13ActivityStateTest {
    @Test
    fun appendDeduplicatesAndKeepsExistingItems() {
        val loaded = R13ActivityListState().loaded(page(content("1"), content("2")))
        val appended = loaded.loadStarted(append = true).loaded(page(content("2"), content("3")), append = true)

        assertEquals(listOf("1", "2", "3"), appended.items.map { it.id })
        assertEquals(R13ListPhase.CONTENT, appended.phase)
    }

    @Test
    fun partialFailureRetainsVisibleContentAndRequestId() {
        val loaded = R13ActivityListState().loaded(page(content("1")))
        val failed = loaded.loadStarted(refresh = true).failed(
            R07CallResult.Failure(500, requestId = "req-r13"),
        )

        assertEquals(R13ListPhase.PARTIAL_ERROR, failed.phase)
        assertEquals(listOf("1"), failed.items.map { it.id })
        assertEquals("req-r13", failed.requestId)
    }

    @Test
    fun removingLastFavoriteTransitionsToEmptyWithoutNegativeTotal() {
        val loaded = R13ActivityListState().loaded(page(content("1"), total = "0"))
        val empty = loaded.remove("1")

        assertEquals(R13ListPhase.EMPTY, empty.phase)
        assertEquals("0", empty.total)
    }

    @Test
    fun idempotencyKeyIsStableUntilCompletion() {
        val keys = R13IntentKeys()
        val first = keys.key("share", "content-1:COPY_LINK")
        assertEquals(first, keys.key("share", "content-1:COPY_LINK"))
        keys.complete("share", "content-1:COPY_LINK")
        assertNotEquals(first, keys.key("share", "content-1:COPY_LINK"))
        assertTrue(first.length >= 16)
    }

    @Test
    fun historyGroupsOnlyFromRealServerTime() {
        val today = content("1", updatedAt = "2026-07-26T08:10:00Z")
        assertEquals(
            "今天",
            activityDateGroup(today, Instant.parse("2026-07-26T12:00:00Z"), ZoneId.of("UTC")),
        )
    }

    private fun page(vararg values: ContentResource, total: String = values.size.toString()) = ContentPageResource(
        items = values.toList(),
        page = R07PageMeta(page = 1, pageSize = 20, total = total, hasMore = "false"),
    )

    private fun content(id: String, updatedAt: String = "2026-07-26T08:00:00Z") = ContentResource(
        id = id,
        contentType = "PROJECT",
        title = "内容$id",
        status = "ONLINE",
        updatedAt = updatedAt,
        version = 1,
    )
}
