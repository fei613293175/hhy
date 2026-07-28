package cc.orbexa.hhy.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.DirectUploadResource
import cc.orbexa.hhy.network.MediaCallResult
import cc.orbexa.hhy.network.MediaCompleteUploadSessionRequest
import cc.orbexa.hhy.network.MediaCreateUploadSessionRequest
import cc.orbexa.hhy.network.MediaResource
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class R14ChatDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyConversationKeepsRealHeaderAndAccessibleComposerWithoutTechnicalFields() {
        composeRule.setContent {
            HhyTheme {
                R14ChatDetailScreen(
                    api = EmptyChatApi,
                    mediaApi = UnusedMediaApi,
                    accessToken = "token",
                    conversationId = "42",
                    currentUserId = "11",
                    initialPeer = PublisherSummaryResource("7", "真实发布者", verified = false),
                    onBack = {},
                    onSessionExpired = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("暂无消息")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("真实发布者").assertIsDisplayed()
        composeRule.onNodeWithText("暂无消息").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("发送图片").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("requestId")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("clientMessageId")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("expectedVersion")).fetchSemanticsNodes().size)
    }

    @Test
    fun persistedOwnBlockSurvivesDetailReentryAndExposesOnlyUnblock() {
        val store = object : R14BlockStateStore {
            override fun isBlockedByMe(currentUserId: String, peerId: String) = true
            override fun setBlockedByMe(currentUserId: String, peerId: String, blocked: Boolean) = true
        }
        composeRule.setContent {
            HhyTheme {
                R14ChatDetailScreen(
                    api = EmptyChatApi,
                    mediaApi = UnusedMediaApi,
                    accessToken = "token",
                    conversationId = "42",
                    currentUserId = "11",
                    initialPeer = PublisherSummaryResource("7", "真实发布者", verified = false),
                    blockStateStore = store,
                    onBack = {},
                    onSessionExpired = {},
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("当前无法发送消息")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, composeRule.onAllNodes(hasText("输入消息")).fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("会话操作").performClick()
        composeRule.onNodeWithText("解除拉黑").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("删除会话")).fetchSemanticsNodes().size)
    }

    private object EmptyChatApi : ContractR14Api {
        override suspend fun conversations(accessToken: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?): R07CallResult<ChatConversationPageResource> =
            R07CallResult.Failure(500)

        override suspend fun messages(accessToken: String, conversationId: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?) =
            R07CallResult.Success(ChatMessagePageResource(emptyList(), R07PageMeta(page = 1, pageSize = 20, total = "0", hasMore = "false")), "req")

        override suspend fun send(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatSendMessageRequest): R07CallResult<ChatMessageResource> =
            R07CallResult.Failure(500)

        override suspend fun read(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatPostConversationsByIdReadRequest): R07CallResult<CommandResultResource> =
            R07CallResult.Failure(500)
    }

    private object UnusedMediaApi : ContractMediaApi {
        override suspend fun createUploadSession(accessToken: String, idempotencyKey: String, request: MediaCreateUploadSessionRequest): MediaCallResult<MediaResource> = MediaCallResult.Failure(500)
        override suspend fun upload(uploadUrl: String, contentType: String, sizeBytes: Long, input: () -> InputStream, onProgress: (Long) -> Unit): MediaCallResult<DirectUploadResource> = MediaCallResult.Failure(500)
        override suspend fun completeUploadSession(accessToken: String, sessionId: String, idempotencyKey: String, request: MediaCompleteUploadSessionRequest): MediaCallResult<MediaResource> = MediaCallResult.Failure(500)
        override suspend fun deleteMedia(accessToken: String, mediaId: String, idempotencyKey: String): MediaCallResult<CommandResultResource> = MediaCallResult.Failure(500)
    }
}
