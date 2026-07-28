package cc.orbexa.hhy

import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.PublisherSummaryResource
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
        val messages: AuthenticatedRoute = AuthenticatedRoute.Messages
        val me: AuthenticatedRoute = AuthenticatedRoute.Me

        assertTrue(home is AuthenticatedRoute.Home)
        assertTrue(messages is AuthenticatedRoute.Messages)
        assertTrue(me is AuthenticatedRoute.Me)
        assertTrue(home != me && home != messages && messages != me)
    }

    @Test
    fun chatRouteRejectsMissingOrUnsafeConversationIds() {
        assertEquals(null, chatDetailRoute(""))
        assertEquals(null, chatDetailRoute("../conversation"))
        assertTrue(chatDetailRoute("conversation_42") is AuthenticatedRoute.ChatDetail)
    }

    @Test
    fun realContentContextIsCarriedWithoutInventingChatData() {
        val content = ContentResource(
            id = "content_1",
            contentType = "PROJECT",
            title = "真实项目",
            status = "ONLINE",
            publisher = PublisherSummaryResource("user_7", "真实发布者", verified = true),
            version = 1,
        )
        val route = chatDetailRoute("conversation_42", content, conversationVersion = 8)!!

        assertEquals(8L, route.conversationVersion)
        assertEquals("真实发布者", route.peerNickname)
        assertEquals("真实项目", route.sourceTitle)
        assertEquals("PROJECT", route.sourceContentType)
    }

    @Test
    fun conversationListRouteCarriesOnlyTheRealPeerIntoChatDetail() {
        val conversation = ChatConversationResource(
            id = "conversation_42",
            peer = PublisherSummaryResource("user_7", "真实联系人", verified = false),
            unreadCount = 2,
            version = 1,
        )
        val route = chatDetailRoute(conversation)!!

        assertEquals("conversation_42", route.conversationId)
        assertEquals(1L, route.conversationVersion)
        assertEquals("真实联系人", route.peerNickname)
        assertEquals(null, route.sourceTitle)
    }

    @Test
    fun chatRouteNeverPropagatesAnInvalidConversationVersion() {
        val route = chatDetailRoute("conversation_42", conversationVersion = -1)!!

        assertEquals(null, route.conversationVersion)
    }
}
