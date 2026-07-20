package cc.orbexa.hhy

import cc.orbexa.hhy.designsystem.HhyMotion
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun frozenMotionDurationsRemainCentralized() {
        assertEquals(120, HhyMotion.ButtonFeedbackMillis)
        assertEquals(200, HhyMotion.StandardMillis)
        assertEquals(300, HhyMotion.ResultEmphasisMillis)
    }
}
