package cc.orbexa.hhy.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.network.PublisherSummaryResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class R14ChatSurfacesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val peer = PublisherSummaryResource("peer-7", "真实用户", verified = false)

    @Test
    fun contactSheetShowsRealReceiverAndFrozenContactTypes() {
        composeRule.setContent {
            HhyTheme {
                R14ContactSheet(
                    peer = peer,
                    submitting = false,
                    failure = null,
                    onDismiss = {},
                    onSubmit = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("发送给 真实用户。只会发送你主动选择的内容。").assertIsDisplayed()
        composeRule.onNodeWithText("手机号").assertIsDisplayed()
        composeRule.onNodeWithText("微信").assertIsDisplayed()
        composeRule.onNodeWithText("其他").assertIsDisplayed()
    }

    @Test
    fun safetySheetExposesOnlyFrozenActions() {
        composeRule.setContent {
            HhyTheme {
                R14SafetySheet(peer, blocked = false, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("举报聊天").assertIsDisplayed()
        composeRule.onNodeWithText("拉黑该用户").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("删除会话")).fetchSemanticsNodes().size)
    }

    @Test
    fun reportSheetDisablesSubmissionWithoutAuthoritativeReasonCatalog() {
        composeRule.setContent {
            HhyTheme {
                R14ReportSheet(
                    peer = peer,
                    reasons = emptyList(),
                    messages = emptyList(),
                    draft = R14ReportDraft(),
                    versionAvailable = true,
                    submitting = false,
                    failure = null,
                    onDismiss = {},
                    onDraftChange = {},
                    onAddEvidence = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("举报原因配置暂不可用，当前无法提交。").assertIsDisplayed()
        composeRule.onNodeWithText("提交举报").assertIsNotEnabled()
        assertEquals(0, composeRule.onAllNodes(hasText("reasonCode")).fetchSemanticsNodes().size)
    }

    @Test
    fun reportSheetDisablesEveryInputWithoutARealConversationVersion() {
        composeRule.setContent {
            HhyTheme {
                R14ReportSheet(
                    peer = peer,
                    reasons = listOf(R14ReportReasonOption("SPAM", "垃圾广告")),
                    messages = emptyList(),
                    draft = R14ReportDraft(),
                    versionAvailable = false,
                    submitting = false,
                    failure = null,
                    onDismiss = {},
                    onDraftChange = {},
                    onAddEvidence = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("会话状态需要刷新，当前无法提交举报。").assertIsDisplayed()
        composeRule.onNodeWithText("添加图片证据").assertIsNotEnabled()
        composeRule.onNodeWithText("提交举报").assertIsNotEnabled()
    }

    @Test
    fun reportConfirmationIncludesMessageAndImageEvidenceCounts() {
        val evidence = MediaUploadSelection("media_1", "evidence.jpg", "image/jpeg", 128, null)
        composeRule.setContent {
            HhyTheme {
                R14ReportSheet(
                    peer = peer,
                    reasons = listOf(R14ReportReasonOption("SPAM", "垃圾广告")),
                    messages = emptyList(),
                    draft = R14ReportDraft(reasonCode = "SPAM", messageIds = setOf("message_1"), evidence = listOf(evidence)),
                    versionAvailable = true,
                    submitting = false,
                    failure = null,
                    onDismiss = {},
                    onDraftChange = {},
                    onAddEvidence = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("图片证据 1").assertIsDisplayed()
        composeRule.onNodeWithText("提交举报").performClick()
        composeRule.onNodeWithText("将提交所选原因、补充说明、1 条消息证据和 1 张图片证据。").assertIsDisplayed()
    }

    @Test
    fun reportEvidenceMergeDeduplicatesAndCapsAtOneHundred() {
        val existing = (1..99).map { MediaUploadSelection("media_$it", "$it.jpg", "image/jpeg", 1, null) }
        val incoming = listOf(
            MediaUploadSelection("media_99", "duplicate.jpg", "image/jpeg", 1, null),
            MediaUploadSelection("media_100", "100.jpg", "image/jpeg", 1, null),
            MediaUploadSelection("media_101", "101.jpg", "image/jpeg", 1, null),
        )

        val merged = mergeR14Evidence(existing, incoming)

        assertEquals(100, merged.size)
        assertEquals(100, merged.map { it.mediaId }.distinct().size)
        assertEquals("media_100", merged.last().mediaId)
    }

    @Test
    fun destructiveDialogsExplainScopeWithoutTechnicalFields() {
        composeRule.setContent {
            HhyTheme {
                R14DeleteConversationDialog(peer, false, null, {}, {})
            }
        }

        composeRule.onNodeWithText("删除会话").assertIsDisplayed()
        composeRule.onNodeWithText("将从当前账号的会话列表中删除与 真实用户 的会话视图，不会删除对方的消息记录。").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText("requestId")).fetchSemanticsNodes().size)
    }
}
