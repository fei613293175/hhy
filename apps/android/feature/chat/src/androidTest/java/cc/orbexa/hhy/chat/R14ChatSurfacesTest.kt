package cc.orbexa.hhy.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.PublisherSummaryResource
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
                R14SafetySheet(peer, blocked = false, {}, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("举报聊天").assertIsDisplayed()
        composeRule.onNodeWithText("拉黑该用户").assertIsDisplayed()
        composeRule.onNodeWithText("删除会话").assertIsDisplayed()
    }

    @Test
    fun reportSheetDisablesSubmissionWithoutAuthoritativeReasonCatalog() {
        composeRule.setContent {
            HhyTheme {
                R14ReportSheet(
                    peer = peer,
                    reasons = emptyList(),
                    messages = emptyList(),
                    submitting = false,
                    failure = null,
                    onDismiss = {},
                    onSubmit = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("举报原因配置暂不可用，当前无法提交。").assertIsDisplayed()
        composeRule.onNodeWithText("提交举报").assertIsNotEnabled()
        composeRule.onNodeWithText("reasonCode").assertDoesNotExist()
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
        composeRule.onNodeWithText("requestId").assertDoesNotExist()
    }
}
