package cc.orbexa.hhy.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.ChatTextMessageRequest
import cc.orbexa.hhy.network.ChatTextPayload
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
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            override fun setBlockedByMe(currentUserId: String, peerId: String, blocked: Boolean) = Unit
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

    @Test
    fun successfulBlockImmediatelyDisablesComposerAndPersistsOwnBlock() {
        val persisted = AtomicBoolean(false)
        val store = object : R14BlockStateStore {
            override fun isBlockedByMe(currentUserId: String, peerId: String) = persisted.get()
            override fun setBlockedByMe(currentUserId: String, peerId: String, blocked: Boolean) {
                persisted.set(blocked)
            }
        }
        composeRule.setContent {
            HhyTheme {
                R14ChatDetailScreen(
                    api = SuccessfulBlockApi,
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
            composeRule.onAllNodes(hasText("暂无消息")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.chat.composer").performClick().performTextInput("保留输入法焦点")
        composeRule.onNodeWithContentDescription("会话操作").performClick()
        composeRule.onNodeWithText("拉黑该用户").performClick()
        composeRule.onNodeWithText("确认拉黑").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("当前无法发送消息")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.chat.blocked").assertIsDisplayed()
        composeRule.onNodeWithTag("r14.chat.composer").assertDoesNotExist()
        assertTrue(persisted.get())
    }

    @Test
    fun successfulTextSendClearsComposer() {
        composeRule.setContent {
            HhyTheme {
                R14ChatDetailScreen(
                    api = SuccessfulSendApi,
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

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("暂无消息")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.chat.composer").performTextInput("发送后清空")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("r14.chat.composer").assertTextEquals("")
            }.isSuccess
        }
    }

    @Test
    fun failedTextSendKeepsComposerDraft() {
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

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("暂无消息")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.chat.composer").performTextInput("失败时保留")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("发送失败")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("r14.chat.composer").assertTextEquals("失败时保留")
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

    private object SuccessfulBlockApi : ContractR14Api by EmptyChatApi {
        override suspend fun block(
            accessToken: String,
            userId: String,
            idempotencyKey: String,
            request: cc.orbexa.hhy.network.ChatBlockRequest,
        ): R07CallResult<CommandResultResource> = R07CallResult.Success(
            CommandResultResource(userId, null, "BLOCKED", null, "2026-07-29T04:02:04Z"),
            "req-block",
        )
    }

    private object SuccessfulSendApi : ContractR14Api by EmptyChatApi {
        override suspend fun send(
            accessToken: String,
            conversationId: String,
            idempotencyKey: String,
            request: ChatSendMessageRequest,
        ): R07CallResult<ChatMessageResource> {
            require(request is ChatTextMessageRequest)
            return R07CallResult.Success(
                ChatMessageResource(
                    id = "message-1",
                    conversationId = conversationId,
                    sender = PublisherSummaryResource("11", "当前用户", verified = false),
                    clientMessageId = request.clientMessageId,
                    messageType = request.messageType,
                    payload = ChatTextPayload("发送后清空"),
                    status = "SENT",
                    serverSequence = 1,
                    createdAt = "2026-07-30T06:30:00Z",
                ),
                "req-send",
            )
        }
    }

    private object UnusedMediaApi : ContractMediaApi {
        override suspend fun createUploadSession(accessToken: String, idempotencyKey: String, request: MediaCreateUploadSessionRequest): MediaCallResult<MediaResource> = MediaCallResult.Failure(500)
        override suspend fun upload(uploadUrl: String, contentType: String, sizeBytes: Long, input: () -> InputStream, onProgress: (Long) -> Unit): MediaCallResult<DirectUploadResource> = MediaCallResult.Failure(500)
        override suspend fun completeUploadSession(accessToken: String, sessionId: String, idempotencyKey: String, request: MediaCompleteUploadSessionRequest): MediaCallResult<MediaResource> = MediaCallResult.Failure(500)
        override suspend fun deleteMedia(accessToken: String, mediaId: String, idempotencyKey: String): MediaCallResult<CommandResultResource> = MediaCallResult.Failure(500)
    }
}
