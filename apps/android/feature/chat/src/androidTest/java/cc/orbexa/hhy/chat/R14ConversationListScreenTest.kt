package cc.orbexa.hhy.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ChatLastMessageResource
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class R14ConversationListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realConversationRowShowsOnlyFrozenBusinessFieldsAndOpensTheConversation() {
        var selected: String? = null
        composeRule.setContent {
            HhyTheme {
                R14ConversationListScreen(
                    api = ConversationApi,
                    accessToken = "token",
                    contentPadding = PaddingValues(),
                    onConversationSelected = { selected = it.id },
                    onSessionExpired = {},
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("真实联系人")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("消息").assertIsDisplayed()
        composeRule.onNodeWithText("真实联系人").assertIsDisplayed()
        composeRule.onNodeWithText("真实最后消息").assertIsDisplayed()
        composeRule.onNodeWithTag("r14.conversation.peer-name", useUnmergedTree = true)
            .assertTextEquals("真实联系人")
        composeRule.onNodeWithTag("r14.conversation.preview", useUnmergedTree = true)
            .assertTextEquals("真实最后消息")
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("与真实联系人的会话").performClick()
        assertEquals("conversation_42", selected)
        listOf("requestId", "lastReadMessageId", "version", "通知中心", "公告中心", "官方客服", "系统消息").forEach {
            assertEquals(0, composeRule.onAllNodes(hasText(it)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun messageHomeExposesB07QuickActions() {
        var selected = ""
        composeRule.setContent {
            HhyTheme {
                R14ConversationListScreen(
                    api = ConversationApi,
                    accessToken = "token",
                    onOpenNotifications = { selected = "notifications" },
                    onOpenAnnouncements = { selected = "announcements" },
                    onConversationSelected = {},
                    onSessionExpired = {},
                )
            }
        }

        composeRule.onNodeWithText("通知中心").assertIsDisplayed().performClick()
        assertEquals("notifications", selected)
        composeRule.onNodeWithText("公告中心").assertIsDisplayed().performClick()
        assertEquals("announcements", selected)
        composeRule.onNodeWithText("客服消息").assertIsDisplayed()
        composeRule.onNodeWithText("系统消息").assertIsDisplayed()
    }

    @Test
    fun longPressOwnsConversationDeletionAndRemovesConfirmedRow() {
        composeRule.setContent {
            HhyTheme {
                R14ConversationListScreen(
                    api = ConversationApi,
                    accessToken = "token",
                    onConversationSelected = {},
                    onSessionExpired = {},
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("真实联系人")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.conversations.search", useUnmergedTree = true)
            .performTextReplacement("真实联系人")
        composeRule.onNodeWithText("真实联系人").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("与真实联系人的会话").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除会话").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("将从当前账号的会话列表中删除与 真实联系人 的会话视图，不会删除对方的消息记录。")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("删除会话")[1].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("真实联系人")).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, composeRule.onAllNodes(hasText("真实联系人")).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("r14.conversations.empty-message", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("没有找到相关会话")
    }

    private object ConversationApi : ContractR14Api {
        override suspend fun conversations(accessToken: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?) =
            R07CallResult.Success(
                ChatConversationPageResource(
                    listOf(
                        ChatConversationResource(
                            id = "conversation_42",
                            peer = PublisherSummaryResource("user_7", "真实联系人", verified = false),
                            lastMessage = ChatLastMessageResource("message_1", "TEXT", "真实最后消息", createdAt = "2026-07-28T02:30:00Z"),
                            unreadCount = 3,
                            updatedAt = "2026-07-28T02:30:00Z",
                            version = 1,
                        ),
                    ),
                    R07PageMeta(page = 1, pageSize = 20, total = "1", hasMore = "false"),
                ),
                "req",
            )

        override suspend fun messages(accessToken: String, conversationId: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?): R07CallResult<ChatMessagePageResource> = R07CallResult.Failure(500)
        override suspend fun send(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatSendMessageRequest): R07CallResult<ChatMessageResource> = R07CallResult.Failure(500)
        override suspend fun read(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatPostConversationsByIdReadRequest): R07CallResult<CommandResultResource> = R07CallResult.Failure(500)
        override suspend fun deleteConversation(accessToken: String, conversationId: String, idempotencyKey: String) =
            R07CallResult.Success(
                CommandResultResource(
                    resourceId = conversationId,
                    status = "HIDDEN",
                    acceptedAt = "2026-07-28T02:31:00Z",
                ),
                "req",
            )
    }
}
