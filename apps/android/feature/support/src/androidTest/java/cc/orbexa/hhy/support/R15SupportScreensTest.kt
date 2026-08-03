package cc.orbexa.hhy.support

import androidx.activity.ComponentActivity
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        capture("b07-p05-notification-center.png")
    }

    @Test
    fun announcementListMatchesB07InformationHierarchy() {
        compose.setContent {
            R15NotificationListScreen(FakeR15Api(), "token", true, {}, {}, {})
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("合伙云 Pro 新版功能公告")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("官方公告").assertIsDisplayed()
        compose.onNodeWithText("合伙云 Pro 新版功能公告").assertIsDisplayed()
        capture("b07-p06-announcement-center.png")
    }

    @Test
    fun supportHomeRoutesToCreateTicket() {
        var create = false
        compose.setContent { R15SupportHomeScreen({}, {}, {}, { create = true }) }
        compose.onNodeWithText("帮助中心").assertIsDisplayed()
        compose.onNodeWithText("创建工单").performClick()
        compose.runOnIdle { assertEquals(true, create) }
        capture("r15-support-home.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/hhy-r15-visual")
            },
        ) ?: error("Unable to create visual evidence output")
        resolver.openOutputStream(uri)?.use { output ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output))
        } ?: error("Unable to open visual evidence output")
    }
}

private class FakeR15Api : ContractR15Api {
    private val notification = R15NotificationResource("n-1", "SYSTEM", "审核结果", "你的内容已通过", null, null, "2026-08-03T00:00:00Z")
    private val notifications = listOf(
        notification,
        R15NotificationResource("n-2", "ACTIVITY", "活动通知", "你报名的活动即将开始", null, null, "2026-08-03T01:00:00Z"),
        R15NotificationResource("n-3", "TRANSACTION", "交易通知", "订单状态已更新", null, "2026-08-03T02:30:00Z", "2026-08-03T02:00:00Z"),
        R15NotificationResource("n-4", "INTERACTION", "互动通知", "有人回复了你的消息", null, "2026-08-03T03:30:00Z", "2026-08-03T03:00:00Z"),
        R15NotificationResource("n-5", "SYSTEM", "账号安全提醒", "检测到新的登录设备", null, "2026-08-03T04:30:00Z", "2026-08-03T04:00:00Z"),
    )
    private val announcements = listOf(
        R15NotificationResource("a-1", "ANNOUNCEMENT", "合伙云 Pro 新版功能公告", "消息与客服能力已更新", null, null, "2026-08-03T04:00:00Z"),
        R15NotificationResource("a-2", "ANNOUNCEMENT", "关于平台服务协议更新", "请查看最新服务协议", null, "2026-08-02T04:00:00Z", "2026-08-02T04:00:00Z"),
        R15NotificationResource("a-3", "ANNOUNCEMENT", "平台服务维护公告", "维护期间部分功能可能短暂不可用", null, "2026-08-01T04:00:00Z", "2026-08-01T04:00:00Z"),
    )
    private val ticket = R15SupportTicketResource("t-1", "TK-001", "ACCOUNT", "登录问题", "OPEN", null, null, "2026-08-03T00:00:00Z", 0)
    private fun <T> ok(value: T) = R07CallResult.Success(value, "test-request")

    override suspend fun notifications(accessToken: String, keyword: String?) = ok(R15NotificationPage(notifications, page(notifications.size)))
    override suspend fun readNotification(accessToken: String, id: String, key: String) = ok(notification.copy(readAt = "2026-08-03T00:01:00Z"))
    override suspend fun readAllNotifications(accessToken: String, key: String) = ok(CommandResultResource("n-1", null, "READ", 0, "2026-08-03T00:01:00Z"))
    override suspend fun announcements(accessToken: String, keyword: String?) = ok(R15NotificationPage(announcements, page(announcements.size)))
    override suspend fun announcement(accessToken: String, id: String) = ok(notification)
    override suspend fun helpArticles(accessToken: String, keyword: String?) = ok(R15SupportTicketPage(listOf(ticket), page(1)))
    override suspend fun helpArticle(accessToken: String, id: String) = ok(ticket)
    override suspend fun createTicket(accessToken: String, key: String, request: R15CreateTicketRequest) = ok(ticket)
    override suspend fun tickets(accessToken: String, status: String?) = ok(R15SupportTicketPage(listOf(ticket), page(1)))
    override suspend fun ticket(accessToken: String, id: String) = ok(ticket)
    override suspend fun addMessage(accessToken: String, id: String, key: String, request: R15SupportMessageRequest) = ok(ticket)
    override suspend fun reportContent(accessToken: String, id: String, key: String, request: R15ContentReportRequest) = ok(CommandResultResource(id, null, "PENDING", 0, "2026-08-03T00:01:00Z"))

    private fun page(total: Int) = R07PageMeta(1, 20, total.toString(), null, "false")
}
