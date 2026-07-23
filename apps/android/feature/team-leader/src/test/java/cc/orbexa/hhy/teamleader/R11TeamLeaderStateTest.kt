package cc.orbexa.hhy.teamleader

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class R11TeamLeaderStateTest {
    @Test
    fun factsUseOnlyRegisteredAttributesAndSecureLogo() {
        val facts = R11TeamLeaderFacts.from(
            resource(
                attributes = buildJsonObject {
                    put("teamName", "启航团队")
                    put("nickname", "负责人")
                    put("logoMediaId", "logo-1")
                    put("sizeRange", "10-20人")
                    put("skills", "产品运营")
                    put("cooperationTypes", "联合创业")
                    put("unknownBadge", "虚构标签")
                },
                media = listOf(MediaItemResource("logo-1", "IMAGE", "https://cdn.orbexa.cc/logo.png", null, null, null, null, null, 0)),
            ),
        )
        assertEquals("启航团队", facts.teamName)
        assertEquals("https://cdn.orbexa.cc/logo.png", facts.logoUrl)
        assertEquals(listOf("10-20人", "产品运营", "联合创业"), facts.tags)
        assertFalse(facts.tags.contains("虚构标签"))
    }

    @Test
    fun factsRejectUnsafeLogoAndTechnicalTagValues() {
        val facts = R11TeamLeaderFacts.from(
            resource(
                attributes = buildJsonObject {
                    put("sizeRange", "INTERNAL_ONLY")
                    put("skills", "OPS_CODE")
                },
                media = listOf(MediaItemResource("logo-1", "IMAGE", "http://cdn.orbexa.cc/logo.png", null, null, null, null, null, 0)),
            ),
        )
        assertNull(facts.logoUrl)
        assertEquals(emptyList<String>(), facts.tags)
    }

    @Test
    fun paginationDeduplicatesAndPreservesExistingContentOnFailure() {
        val first = resource(id = "1")
        val second = resource(id = "2")
        val loaded = R11TeamLeaderListState().success(listOf(first), "next", true, reset = true)
        val appended = loaded.loading(reset = false).success(listOf(first, second), null, false, reset = false)
        assertEquals(listOf("1", "2"), appended.items.map { it.id })
        val failed = appended.failed(R07CallResult.Failure(statusCode = null))
        assertEquals(R11TeamLeaderPhase.PARTIAL_ERROR, failed.phase)
        assertEquals(listOf("1", "2"), failed.items.map { it.id })
    }

    @Test
    fun firstLoadFailuresMapToUserFacingStates() {
        assertEquals(R11TeamLeaderPhase.OFFLINE, R11TeamLeaderListState().failed(R07CallResult.Failure(null)).phase)
        assertEquals(R11TeamLeaderPhase.FORBIDDEN, R11TeamLeaderListState().failed(R07CallResult.Failure(403)).phase)
        assertEquals(R11TeamLeaderPhase.ERROR, R11TeamLeaderListState().failed(R07CallResult.Failure(500)).phase)
    }

    private fun resource(
        id: String = "1",
        attributes: kotlinx.serialization.json.JsonObject? = null,
        media: List<MediaItemResource> = emptyList(),
    ) = ContentResource(
        id = id,
        contentType = "TEAM_LEADER",
        title = "团队",
        media = media,
        status = "ONLINE",
        version = 1,
        attributes = attributes,
    )
}
