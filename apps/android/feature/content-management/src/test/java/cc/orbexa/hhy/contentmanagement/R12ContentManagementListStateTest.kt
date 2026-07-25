package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
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

    private fun page(
        items: List<ContentResource>,
        page: Long = 1,
        hasMore: String = "false",
    ) = ContentPageResource(items, R07PageMeta(page = page, pageSize = 20, hasMore = hasMore))
}
