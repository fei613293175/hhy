package cc.orbexa.hhy.teamleader

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
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
        assertEquals(R11TeamLeaderPhase.NOT_FOUND, R07CallResult.Failure(404).toR11TeamLeaderFailure().phase)
        assertEquals(R11TeamLeaderPhase.CONFLICT, R07CallResult.Failure(409).toR11TeamLeaderFailure().phase)
    }

    @Test
    fun intentKeysAreStableForRetryAndChangeWithRequestBody() {
        val keys = R11IntentKeys()
        val first = keys.forBody("favorite", "content-1:2")
        assertEquals(first, keys.forBody("favorite", "content-1:2"))
        assertNotEquals(first, keys.forBody("favorite", "content-1:3"))
    }

    @Test
    fun detailSectionsOnlyContainRegisteredBusinessFacts() {
        val resource = resource(
            attributes = buildJsonObject {
                put("teamIntro", "真实团队介绍")
                put("cooperationRequirement", "长期合作")
                put("skills", "产品运营")
                put("unknownSection", "不应展示")
            },
        )
        val text = R11TeamLeaderFacts.from(resource).detailSections(resource).flatMap { it.second }
        assertEquals(listOf("真实团队介绍", "长期合作", "产品运营"), text)
        assertFalse(text.contains("不应展示"))
    }

    @Test
    fun editorFormValidatesRequiredFactsAndBuildsRegisteredAttributes() {
        val invalid = R11TeamLeaderForm()
        assertEquals(
            setOf("nickname", "personalIntro", "teamName", "teamIntro", "sizeRange", "categoryCode", "contactValue"),
            invalid.validate().keys,
        )
        val form = R11TeamLeaderForm(
            nickname = "负责人",
            personalIntro = "专注真实合作",
            teamName = "启航团队",
            teamIntro = "团队介绍",
            sizeRange = "10-50人",
            categoryCode = "产品运营",
            contactValue = "hhy_team",
            pastCases = "案例甲\n案例乙",
        )
        assertEquals(emptyMap<String, String>(), form.validate())
        assertEquals("10-50人", form.attributes()["sizeRange"]?.jsonPrimitive?.content)
        assertEquals(2, form.attributes()["pastCases"]?.jsonArray?.size)
        assertFalse(form.attributes().containsKey("contactValue"))
    }

    @Test
    fun editingPreservesExistingSensitiveContactUntilUserReplacesIt() {
        val form = R11TeamLeaderForm(
            nickname = "负责人",
            personalIntro = "个人介绍",
            teamName = "团队",
            teamIntro = "团队介绍",
            sizeRange = "10人以下",
            categoryCode = "运营",
            existingContactAvailable = true,
            expectedVersion = 3,
        )
        assertEquals(emptyMap<String, String>(), form.validate())
        assertNull(form.contacts())
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
