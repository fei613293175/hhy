package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContentStatisticsResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R12ContentManagementStateTest {
    @Test
    fun refreshFailurePreservesLastContentAsStaleCache() {
        val content = resource()
        val failed = R12ContentManagementState().loaded(content).loadFailed(R07CallResult.Failure(null))
        assertEquals(R12ContentManagementPhase.STALE_CACHE, failed.phase)
        assertEquals(content, failed.content)
    }

    @Test
    fun firstLoadFailuresMapWithoutTechnicalFields() {
        assertEquals(R12ContentManagementPhase.OFFLINE, R12ContentManagementState().loadFailed(R07CallResult.Failure(null)).phase)
        assertEquals(R12ContentManagementPhase.FORBIDDEN, R12ContentManagementState().loadFailed(R07CallResult.Failure(403, requestId = "hidden")).phase)
        assertEquals(R12ContentManagementPhase.NOT_FOUND, R12ContentManagementState().loadFailed(R07CallResult.Failure(404)).phase)
        assertEquals(R12ContentManagementPhase.ERROR, R12ContentManagementState().loadFailed(R07CallResult.Failure(500, "INTERNAL")).phase)
    }

    @Test
    fun copyRequiresVerifiedOwner() {
        val content = resource()
        assertTrue(content.canCopy("owner-1", identityVerified = true))
        assertFalse(content.canCopy("other", identityVerified = true))
        assertFalse(content.canCopy("owner-1", identityVerified = false))
    }

    @Test
    fun copyKeyIsStableForRetryAndChangesWithVersion() {
        val keys = R12CopyIntentKeys()
        val first = keys.forRequest("content-1", 2, null)
        assertEquals(first, keys.forRequest("content-1", 2, null))
        assertNotEquals(first, keys.forRequest("content-1", 3, null))
        keys.consume("content-1")
        assertNotEquals(first, keys.forRequest("content-1", 2, null))
    }

    @Test
    fun analyticsOverridesSummaryAndFailureRemainsLocal() {
        val content = resource(statistics = ContentStatisticsResource(1, 2, 3, 4))
        val analyticsContent = resource(statistics = ContentStatisticsResource(10, 20, 30, 40))
        val page = ContentPageResource(listOf(analyticsContent), R07PageMeta(1, 20, "1", null, "false"))
        val loaded = R12ContentManagementState().loaded(content).analyticsStarted().analyticsLoaded(page)
        assertEquals(10L, loaded.statistics?.viewCount)
        val failed = loaded.analyticsStarted().analyticsFailed()
        assertEquals(R12ContentManagementPhase.CONTENT, failed.phase)
        assertTrue(failed.analyticsUnavailable)
    }

    @Test
    fun onlySecureRealMediaIsSelected() {
        val unsafe = resource(media = listOf(MediaItemResource("m1", "IMAGE", "http://cdn.orbexa.cc/a.png", null, null, null, null, null, 0)))
        assertNull(unsafe.secureMediaUrl())
        val secure = resource(media = listOf(MediaItemResource("m2", "IMAGE", "https://cdn.orbexa.cc/a.png", null, null, null, null, null, 0)))
        assertEquals("https://cdn.orbexa.cc/a.png", secure.secureMediaUrl())
    }

    private fun resource(
        statistics: ContentStatisticsResource? = null,
        media: List<MediaItemResource> = emptyList(),
    ) = ContentResource(
        id = "content-1",
        contentType = "PROJECT",
        title = "真实内容",
        media = media,
        publisher = PublisherSummaryResource("owner-1", "发布者", verified = true),
        status = "ONLINE",
        statistics = statistics,
        version = 2,
    )
}
