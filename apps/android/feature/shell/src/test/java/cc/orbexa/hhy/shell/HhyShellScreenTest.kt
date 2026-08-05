package cc.orbexa.hhy.shell

import org.junit.Assert.assertTrue
import org.junit.Test

class HhyShellScreenTest {
    @Test
    fun r20EnablesTheRealRedPacketDestinationAlongsideMessaging() {
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.HOME))
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.MESSAGE))
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.ME))
        assertTrue(isTopLevelDestinationEnabled(HhyTopLevelDestination.REWARD))
    }
}
