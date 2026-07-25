package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R12ContentManagementListStateTest {
    @Test
    fun appendDeduplicatesContentAndRetainsOrder() {
        val first = ContentResource("a", "APP", "A", status = "ONLINE", version = 1)
        val second = ContentResource("b", "PROJECT", "B", status = "DRAFT", version = 2)
        val initial = R12ContentListState().loaded(page(listOf(first), page = 1, hasMore = "true"))
        val appended = initial.loading(append = true).loaded(page(listOf(first, second), page = 2), append = true)
        assertEquals(listOf("a", "b"), appended.items.map(ContentResource::id))
        assertFalse(appended.appending)
    }

    @Test
    fun refreshFailurePreservesExistingContentAsPartialFailure() {
        val content = ContentResource("a", "APP", "A", status = "ONLINE", version = 1)
        val state = R12ContentListState().loaded(page(listOf(content))).loading(refresh = true)
            .failed(R07CallResult.Failure(statusCode = 500))
        assertEquals(R12ContentListPhase.CONTENT, state.phase)
        assertEquals(listOf(content), state.items)
        assertTrue(state.partialFailure)
    }

    @Test
    fun initialFailuresUseCommercialPhasesWithoutTechnicalIdentifiers() {
        assertEquals(R12ContentListPhase.OFFLINE, R12ContentListState().failed(R07CallResult.Failure(null)).phase)
        assertEquals(R12ContentListPhase.FORBIDDEN, R12ContentListState().failed(R07CallResult.Failure(403, requestId = "hidden")).phase)
        assertEquals(R12ContentListPhase.NOT_FOUND, R12ContentListState().failed(R07CallResult.Failure(404)).phase)
        assertEquals(R12ContentListPhase.ERROR, R12ContentListState().failed(R07CallResult.Failure(409, errorCode = "hidden")).phase)
    }

    @Test
    fun intentKeyIsStableOnlyForSameBusinessIntent() {
        val keys = R12ContentActionKeys()
        val first = keys.forRequest("online", "content-1", 3)
        assertEquals(first, keys.forRequest("online", "content-1", 3))
        assertNotEquals(first, keys.forRequest("online", "content-1", 4))
        assertNotEquals(first, keys.forRequest("offline", "content-1", 3))
    }

    @Test
    fun actionRulesFollowFrozenContentStateMachine() {
        assertTrue(ContentResource("a", "APP", "A", status = "APPROVED", version = 1).canGoOnline(true))
        assertFalse(ContentResource("a", "APP", "A", status = "APPROVED", version = 1).canGoOnline(false))
        assertTrue(ContentResource("a", "APP", "A", status = "ONLINE", version = 1).canGoOffline())
        assertFalse(ContentResource("a", "APP", "A", status = "BANNED", version = 1).canDelete())
    }

    @Test
    fun publishEntryContainsExactlyFourFrozenContentTypes() {
        assertEquals(
            listOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER"),
            r12PublishContentTypes().map { it.first },
        )
    }

    @Test
    fun pendingContentRemainsVisibleWhenAdminHistoryIsEmpty() {
        val summary = ContentResource(
            id = "content-1",
            contentType = "PROJECT",
            title = "真实待审项目",
            status = "PENDING_REVIEW",
            reviewStatus = "PENDING_REVIEW",
            version = 2,
        )
        val state = R12ReviewHistoryState("content-1")
            .reloadStarted(requestGeneration = 1, refresh = false)
            .summaryLoaded(summary)
            .timelineLoaded(page(emptyList()))

        assertEquals(R12ContentListPhase.CONTENT, state.phase)
        assertEquals("真实待审项目", state.summary?.title)
        assertTrue(state.entries.isEmpty())
        assertFalse(state.partialFailure)
    }

    @Test
    fun reviewTimelineUsesReviewIdsAndKeepsMultipleRecordsForOneContent() {
        val firstPage = page(
            listOf(review("content-1", "review-1"), review("content-1", "review-2")),
            page = 1,
            hasMore = "true",
        )
        val secondPage = page(
            listOf(review("content-1", "review-2"), review("content-1", "review-3")),
            page = 2,
        )
        val state = R12ReviewHistoryState("content-1")
            .reloadStarted(requestGeneration = 1, refresh = false)
            .summaryLoaded(ContentResource("content-1", "PROJECT", "项目", status = "PENDING_REVIEW", version = 2))
            .timelineLoaded(firstPage)
            .timelineLoaded(secondPage, append = true)

        assertEquals(listOf("review-1", "review-2", "review-3"), state.entries.map(R12ReviewTimelineEntry::reviewId))
        assertEquals(3, state.entries.distinctBy(R12ReviewTimelineEntry::reviewId).size)
    }

    @Test
    fun missingReviewIdentityNeverFallsBackToContentId() {
        val summary = ContentResource("content-1", "PROJECT", "项目", status = "PENDING_REVIEW", version = 2)
        val invalid = summary.copy(attributes = buildJsonObject { put("source", "content") })
        val state = R12ReviewHistoryState("content-1")
            .reloadStarted(requestGeneration = 1, refresh = false)
            .summaryLoaded(summary)
            .timelineLoaded(page(listOf(invalid)))

        assertTrue(state.entries.isEmpty())
        assertTrue(state.partialFailure)
        assertEquals(R12ContentListPhase.ERROR, state.timelineFailure?.phase)
    }

    @Test
    fun refreshPreservesVisibleDataAndResetsHistoryPagination() {
        val summary = ContentResource("content-1", "PROJECT", "项目", status = "PENDING_REVIEW", version = 2)
        val loaded = R12ReviewHistoryState("content-1")
            .reloadStarted(requestGeneration = 1, refresh = false)
            .summaryLoaded(summary)
            .timelineLoaded(page(listOf(review("content-1", "review-1")), hasMore = "true"))
        val refreshing = loaded.reloadStarted(requestGeneration = 2, refresh = true)

        assertEquals(summary, refreshing.summary)
        assertEquals(listOf("review-1"), refreshing.entries.map(R12ReviewTimelineEntry::reviewId))
        assertEquals(null, refreshing.page)
        assertTrue(refreshing.refreshing)
        assertFalse(refreshing.canLoadMore)
    }

    @Test
    fun partialTimelineFailureKeepsCurrentContentAndStableRequestIdentity() {
        val summary = ContentResource("content-1", "PROJECT", "项目", status = "PENDING_REVIEW", version = 2)
        val state = R12ReviewHistoryState("content-1")
            .reloadStarted(requestGeneration = 4, refresh = false)
            .summaryLoaded(summary)
            .timelineFailed(R07CallResult.Failure(statusCode = 500))

        assertEquals(R12ContentListPhase.CONTENT, state.phase)
        assertEquals(summary, state.summary)
        assertTrue(state.partialFailure)
        assertTrue(state.accepts("content-1", 4))
        assertFalse(state.accepts("content-2", 4))
        assertFalse(state.accepts("content-1", 3))
    }

    @Test
    fun reviewTimesUseBusinessFormattingInsteadOfRawIsoText() {
        assertEquals("2026-07-26 06:00", "2026-07-25T22:00:00Z".r12BusinessTimeLabel())
        assertEquals("时间待同步", "not-a-time".r12BusinessTimeLabel())
    }

    private fun page(
        items: List<ContentResource>,
        page: Long = 1,
        hasMore: String = "false",
    ) = ContentPageResource(items, R07PageMeta(page = page, pageSize = 20, hasMore = hasMore))

    private fun review(contentId: String, reviewId: String) = ContentResource(
        id = contentId,
        contentType = "PROJECT",
        title = "项目",
        status = "PENDING_REVIEW",
        reviewStatus = "ASSIGN",
        updatedAt = "2026-07-25T22:00:00Z",
        version = 2,
        attributes = buildJsonObject {
            put("review", buildJsonObject {
                put("id", reviewId)
                put("decision", "ASSIGN")
                put("createdAt", "2026-07-25T22:00:00Z")
            })
        },
    )
}
