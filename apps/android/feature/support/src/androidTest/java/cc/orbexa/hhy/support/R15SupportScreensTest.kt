package cc.orbexa.hhy.support

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractR15Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import cc.orbexa.hhy.network.R15ContentReportRequest
import cc.orbexa.hhy.network.R15CreateTicketRequest
import cc.orbexa.hhy.network.R15NotificationPage
import cc.orbexa.hhy.network.R15NotificationResource
import cc.orbexa.hhy.network.R15SupportMessageRequest
import cc.orbexa.hhy.network.R15SupportTicketPage
import cc.orbexa.hhy.network.R15SupportTicketResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R15SupportScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun messageCenterExposesAllThreeDestinations() {
        var selected = ""
        compose.setContent {
            R15MessageCenterScreen(
                PaddingValues(),
                onOpenChats = { selected = "chat" },
                onOpenNotifications = { selected = "notification" },
                onOpenAnnouncements = { selected = "announcement" },
            )
        }
        compose.onNodeWithText("聊天消息").assertIsDisplayed()
        compose.onNodeWithText("系统通知").performClick()
        compose.runOnIdle { assertEquals("notification", selected) }
        compose.onNodeWithText("平台公告").performClick()
        compose.runOnIdle { assertEquals("announcement", selected) }
    }

    @Test
    fun notificationListShowsUnreadItemAndOpensDetail() {
        var selected = ""
        compose.setContent {
            R15NotificationListScreen(FakeR15Api(), "token", false, {}, { selected = it }, {})
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("审核结果")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("审核结果").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("n-1", selected) }
    }

    @Test
    fun supportHomeRoutesToCreateTicket() {
        var create = false
        compose.setContent { R15SupportHomeScreen({}, {}, {}, { create = true }) }
        compose.onNodeWithText("帮助中心").assertIsDisplayed()
        compose.onNodeWithText("创建工单").performClick()
        compose.runOnIdle { assertEquals(true, create) }
    }
}

private class FakeR15Api : ContractR15Api {
    private val page = R07PageMeta(1, 20, "1", null, "false")
    private val notification = R15NotificationResource("n-1", "SYSTEM", "审核结果", "你的内容已通过", null, null, "2026-08-03T00:00:00Z")
    private val ticket = R15SupportTicketResource("t-1", "TK-001", "ACCOUNT", "登录问题", "OPEN", null, null, "2026-08-03T00:00:00Z", 0)
    private fun <T> ok(value: T) = R07CallResult.Success(value, "test-request")

    override suspend fun notifications(accessToken: String, keyword: String?) = ok(R15NotificationPage(listOf(notification), page))
    override suspend fun readNotification(accessToken: String, id: String, key: String) = ok(notification.copy(readAt = "2026-08-03T00:01:00Z"))
    override suspend fun readAllNotifications(accessToken: String, key: String) = ok(CommandResultResource("n-1", null, "READ", 0, "2026-08-03T00:01:00Z"))
    override suspend fun announcements(accessToken: String, keyword: String?) = ok(R15NotificationPage(listOf(notification.copy(type = "ANNOUNCEMENT")), page))
    override suspend fun announcement(accessToken: String, id: String) = ok(notification)
    override suspend fun helpArticles(accessToken: String, keyword: String?) = ok(R15SupportTicketPage(listOf(ticket), page))
    override suspend fun helpArticle(accessToken: String, id: String) = ok(ticket)
    override suspend fun createTicket(accessToken: String, key: String, request: R15CreateTicketRequest) = ok(ticket)
    override suspend fun tickets(accessToken: String, status: String?) = ok(R15SupportTicketPage(listOf(ticket), page))
    override suspend fun ticket(accessToken: String, id: String) = ok(ticket)
    override suspend fun addMessage(accessToken: String, id: String, key: String, request: R15SupportMessageRequest) = ok(ticket)
    override suspend fun reportContent(accessToken: String, id: String, key: String, request: R15ContentReportRequest) = ok(CommandResultResource(id, null, "PENDING", 0, "2026-08-03T00:01:00Z"))
}
