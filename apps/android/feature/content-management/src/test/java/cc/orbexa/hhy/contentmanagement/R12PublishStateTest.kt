package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import cc.orbexa.hhy.network.UserSelfResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R12PublishStateTest {
    @Test
    fun publishCenterContainsExactlyFourFrozenTypes() {
        assertEquals(
            listOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER"),
            r12PublishOptions().map(R12PublishOption::contentType),
        )
    }

    @Test
    fun publishEligibilityRequiresActiveVerifiedUser() {
        assertTrue(r12PublishEligibility(user()).canPublish)
        assertFalse(r12PublishEligibility(user(identityStatus = "PENDING")).canPublish)
        assertFalse(r12PublishEligibility(user(status = "BLOCKED")).canPublish)
    }

    @Test
    fun overviewUsesServerTotalAndCountsVisibleBusinessStates() {
        val page = ContentPageResource(
            items = listOf(
                content("one", "ONLINE"),
                content("two", "PENDING_REVIEW"),
                content("three", "REVIEWING"),
                content("four", "DRAFT"),
                content("five", "RECTIFICATION"),
            ),
            page = R07PageMeta(page = 1, pageSize = 100, total = "18", hasMore = "false"),
        )

        assertEquals(R12PublishOverview(total = 18, online = 1, pending = 2, drafts = 2), r12PublishOverview(page))
    }

    @Test
    fun failuresMapToCommercialRecoveryStates() {
        assertEquals(R12PublishPhase.OFFLINE, R07CallResult.Failure(null).toR12PublishPhase())
        assertEquals(R12PublishPhase.FORBIDDEN, R07CallResult.Failure(403).toR12PublishPhase())
        assertEquals(R12PublishPhase.NOT_FOUND, R07CallResult.Failure(404).toR12PublishPhase())
        assertEquals(R12PublishPhase.ERROR, R07CallResult.Failure(500).toR12PublishPhase())
    }

    @Test
    fun previewOnlyUsesSecureImageMedia() {
        val item = content("one", "DRAFT").copy(
            media = listOf(
                media("safe", "IMAGE", "https://cdn.orbexa.cc/a.jpg", 2),
                media("first", "IMAGE", "https://cdn.orbexa.cc/b.jpg", 1),
                media("http", "IMAGE", "http://cdn.orbexa.cc/c.jpg", 3),
                media("fragment", "IMAGE", "https://cdn.orbexa.cc/d.jpg#token", 4),
                media("video", "VIDEO", "https://cdn.orbexa.cc/e.mp4", 5),
            ),
        )

        assertEquals(listOf("first", "safe"), item.securePreviewMedia().map(MediaItemResource::id))
    }

    @Test
    fun submitEligibilityFollowsOwnerIdentityAndContentState() {
        val draft = content("one", "DRAFT")
        assertTrue(draft.canSubmitFromPreview(user()))
        assertTrue(draft.copy(status = "REJECTED").canSubmitFromPreview(user()))
        assertFalse(draft.copy(status = "ONLINE").canSubmitFromPreview(user()))
        assertFalse(draft.canSubmitFromPreview(user(identityStatus = "PENDING")))
        assertFalse(draft.copy(publisher = PublisherSummaryResource("other", "其他用户", verified = true)).canSubmitFromPreview(user()))
    }

    @Test
    fun previewFactsNeverExposeTechnicalIdentifiersOrVersion() {
        val facts = content("content-secret", "DRAFT").copy(version = 99).previewFacts().joinToString()
        assertFalse(facts.contains("content-secret"))
        assertFalse(facts.contains("99"))
        assertFalse(facts.contains("requestId", ignoreCase = true))
    }

    private fun user(status: String = "ACTIVE", identityStatus: String = "VERIFIED") = UserSelfResource(
        id = "owner",
        nickname = "发布者",
        status = status,
        identityStatus = identityStatus,
        version = 3,
    )

    private fun content(id: String, status: String) = ContentResource(
        id = id,
        contentType = "PROJECT",
        title = "真实项目",
        status = status,
        publisher = PublisherSummaryResource("owner", "发布者", verified = true),
        version = 7,
    )

    private fun media(id: String, type: String, url: String, order: Long) = MediaItemResource(
        id = id,
        mediaType = type,
        url = url,
        sortOrder = order,
    )
}
