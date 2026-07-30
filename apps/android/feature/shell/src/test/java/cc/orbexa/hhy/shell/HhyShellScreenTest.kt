package cc.orbexa.hhy.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HhyShellScreenTest {
    @Test
    fun r14EnablesTheRealMessageDestinationWithoutUnlockingFutureRewardWork() {
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.HOME))
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.MESSAGE))
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.ME))
        assertFalse(isTopLevelDestinationEnabled(HhyTopLevelDestination.REWARD))
    }
}
