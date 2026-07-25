package cc.orbexa.hhy

import cc.orbexa.hhy.designsystem.HhyMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun frozenMotionDurationsRemainCentralized() {
        assertEquals(120, HhyMotion.ButtonFeedbackMillis)
        assertEquals(200, HhyMotion.StandardMillis)
        assertEquals(300, HhyMotion.ResultEmphasisMillis)
    }

    @Test
    fun publishResultRouteRetainsOneImmutableSubmitIntent() {
        val route: AuthenticatedRoute = AuthenticatedRoute.PublishResult(
            contentId = "draft-1",
            expectedVersion = 12,
            idempotencyKey = "r12-submit-1234567890abcdef",
            title = "真实项目",
            contentType = "PROJECT",
        )

        assertTrue(route is AuthenticatedRoute.PublishResult)
        route as AuthenticatedRoute.PublishResult
        assertEquals(12, route.expectedVersion)
        assertEquals("r12-submit-1234567890abcdef", route.idempotencyKey)
    }

    @Test
    fun profileUsesATypedChildRouteFromTheAuthenticatedRoot() {
        val route: AuthenticatedRoute = AuthenticatedRoute.Profile

        assertTrue(route is AuthenticatedRoute.Profile)
    }

    @Test
    fun homeAndMeAreDistinctTypedTopLevelDestinations() {
        val home: AuthenticatedRoute = AuthenticatedRoute.Home
        val me: AuthenticatedRoute = AuthenticatedRoute.Me

        assertTrue(home is AuthenticatedRoute.Home)
        assertTrue(me is AuthenticatedRoute.Me)
        assertTrue(home != me)
    }
}
