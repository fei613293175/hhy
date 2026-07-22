package cc.orbexa.hhy.project

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R08ProjectStateTest {
    @Test
    fun formRequiresOnlyFrozenMandatoryFields() {
        val errors = R08ProjectForm().validate()
        assertEquals(setOf("title", "description", "categoryCode"), errors.keys)
        assertTrue(R08ProjectForm(title = "项目", description = "说明", categoryCode = "SERVICE").validate().isEmpty())
    }

    @Test
    fun conflictAndOfflineHaveRecoverableVisibleStates() {
        assertEquals(R08ProjectPhase.CONFLICT, R07CallResult.Failure(409).toR08Failure().phase)
        assertEquals(R08ProjectPhase.OFFLINE, R07CallResult.Failure(null).toR08Failure().phase)
    }

    @Test
    fun sameIntentReusesKeyAndChangedBodyGetsNewKey() {
        val keys = R08IntentKeys()
        val first = keys.forBody("create", "body-a")
        assertEquals(first, keys.forBody("create", "body-a"))
        assertNotEquals(first, keys.forBody("create", "body-b"))
    }

    @Test
    fun businessCodesUseFriendlyLabelsWithoutChangingFrozenPayloadValues() {
        assertEquals("合作项目", projectCategoryLabel("COOPERATION"))
        assertEquals("北京", projectRegionLabel("CN-11"))
        assertEquals("微信", contactChannelLabel("WECHAT"))
        assertEquals("其他分类", projectCategoryLabel("UNRECOGNIZED_CODE"))

        val form = R08ProjectForm.from(
            ContentResource(
                id = "project-1",
                contentType = "PROJECT",
                title = "项目",
                categoryCode = "COOPERATION",
                regionCode = "CN-11",
                status = "ONLINE",
                reviewStatus = "APPROVED",
                version = 1,
            ),
        )
        assertEquals("COOPERATION", form.categoryCode)
        assertEquals("CN-11", form.regionCode)
        assertEquals("合作项目", form.categoryInput)
        assertEquals("北京", form.regionInput)
        assertEquals("新分类", form.withCategoryInput("新分类").categoryCode)
    }
}
