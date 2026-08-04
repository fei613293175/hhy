package cc.orbexa.hhy.prop

import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R19PropStateTest {
    @Test fun onlyAvailablePositiveInventoryCanBeUsed() {
        assertTrue(canUseProp("AVAILABLE", 1))
        assertFalse(canUseProp("AVAILABLE", 0))
        assertFalse(canUseProp("EXPIRED", 2))
    }

    @Test fun conflictAndOfflineFailuresHaveRecoveryCopy() {
        assertEquals(
            "数据已变化，请刷新后重新确认",
            propFailureMessage(R07CallResult.Failure(statusCode = 409)),
        )
        assertEquals(
            "网络不可用，保留当前输入后重试",
            propFailureMessage(R07CallResult.Failure(statusCode = null)),
        )
    }
}
