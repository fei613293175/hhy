package cc.orbexa.hhy.project

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
}
