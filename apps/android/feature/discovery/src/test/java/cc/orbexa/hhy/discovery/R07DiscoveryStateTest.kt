package cc.orbexa.hhy.discovery

import cc.orbexa.hhy.network.ContactAccessResource
import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R07DiscoveryStateTest {
    @Test
    fun sameWriteIntentReusesKeyAndChangedPayloadRotatesIt() {
        var sequence = 0
        val keys = StableIntentKeys { "r07-test-${++sequence}-0000000000" }

        val first = keys.key("contact:1:EMAIL", "1|EMAIL")
        val retry = keys.key("contact:1:EMAIL", "1|EMAIL")
        val changed = keys.key("contact:1:EMAIL", "1|PHONE")

        assertEquals(first, retry)
        assertNotEquals(first, changed)
    }

    @Test
    fun successfulIntentRotatesNextKey() {
        var sequence = 0
        val keys = StableIntentKeys { "r07-test-${++sequence}-0000000000" }
        val first = keys.key("clear-history", "current-account")
        keys.complete("clear-history")

        assertNotEquals(first, keys.key("clear-history", "current-account"))
    }

    @Test
    fun closingContactPanelErasesAuthorizedPlaintext() {
        val visible = ContactPanelState(contact = ContactAccessResource("PHONE", "13800000000", "2026-07-21T00:00:00Z"))

        val closed = visible.close()

        assertNull(closed.contact)
        assertNull(closed.failure)
    }

    @Test
    fun permissionFailureMapsWithoutLeakingTechnicalDetails() {
        val failure = R07CallResult.Failure(statusCode = 403, errorCode = "INTERNAL_PERMISSION_RULE", requestId = "req-safe")

        val ui = failure.toUiFailure()

        assertEquals(R07LoadPhase.FORBIDDEN, ui.phase)
        assertEquals("暂时无法访问", ui.title)
        assertEquals("req-safe", ui.requestId)
    }
}
