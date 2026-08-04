package cc.orbexa.hhy.membership

import cc.orbexa.hhy.network.MembershipResource
import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R18MembershipUiStateTest {
    @Test
    fun missingCurrentMembershipIsRenderedAsAnEmptyBusinessState() {
        assertTrue(isMissingMembership(R07CallResult.Failure(statusCode = 404)))
        assertFalse(isMissingMembership(R07CallResult.Failure(statusCode = 500)))
        assertFalse(isMissingMembership(R07CallResult.Success(MembershipResource(status = "ACTIVE", version = 1), "test-request")))
    }
}
