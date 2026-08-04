package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cc.orbexa.hhy.auth.AuthVisualAuditMode
import cc.orbexa.hhy.auth.AuthVisualAuditScreen
import cc.orbexa.hhy.activity.R13ContentActionSheet
import cc.orbexa.hhy.activity.R13ContentSheet
import cc.orbexa.hhy.activity.R13FavoritesScreen
import cc.orbexa.hhy.activity.R13HistoryScreen
import cc.orbexa.hhy.chat.R14BlockDialog
import cc.orbexa.hhy.chat.R14ChatDetailScreen
import cc.orbexa.hhy.chat.R14ContactSheet
import cc.orbexa.hhy.chat.R14ConversationListScreen
import cc.orbexa.hhy.chat.R14DeleteConversationDialog
import cc.orbexa.hhy.chat.R14ReportDraft
import cc.orbexa.hhy.chat.R14ReportReasonOption
import cc.orbexa.hhy.chat.R14ReportSheet
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.discovery.R07PublisherScreen
import cc.orbexa.hhy.discovery.R07SearchScreen
import cc.orbexa.hhy.identity.IdentityVisualAuditMode
import cc.orbexa.hhy.identity.IdentityVisualAuditScreen
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.network.AgreementSnapshot
import cc.orbexa.hhy.network.AccountCancellationRequest
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ChatLastMessageResource
import cc.orbexa.hhy.network.ChatMessagePageResource
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.ChatTextPayload
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContactAccessRequest
import cc.orbexa.hhy.network.ContactAccessResource
import cc.orbexa.hhy.network.ContactChannelSummaryResource
import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentPostContentsByIdInvalidFeedbackRequest
import cc.orbexa.hhy.network.ContentPostContentsByIdShareRequest
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR07Api
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ContractR13Api
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.ContractR12Api
import cc.orbexa.hhy.network.ContractR19PropApi
import cc.orbexa.hhy.network.DirectUploadResource
import cc.orbexa.hhy.network.ExperienceApi
import cc.orbexa.hhy.network.HhyNetworkJson
import cc.orbexa.hhy.network.HomeModuleSnapshot
import cc.orbexa.hhy.network.HomeModuleItemSnapshot
import cc.orbexa.hhy.network.HomeNavigationTargetSnapshot
import cc.orbexa.hhy.network.HomeSnapshot
import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentityConsentCallResult
import cc.orbexa.hhy.network.IdentityConsentResource
import cc.orbexa.hhy.network.IdentityCreateLivenessTokenRequest
import cc.orbexa.hhy.network.IdentityCreateSessionRequest
import cc.orbexa.hhy.network.IdentityOverviewCallResult
import cc.orbexa.hhy.network.IdentityOverviewResource
import cc.orbexa.hhy.network.IdentityRetrySessionRequest
import cc.orbexa.hhy.network.MediaCallResult
import cc.orbexa.hhy.network.MediaCompleteUploadSessionRequest
import cc.orbexa.hhy.network.MediaCreateUploadSessionRequest
import cc.orbexa.hhy.network.MediaResource
import cc.orbexa.hhy.network.MembershipBenefitResource
import cc.orbexa.hhy.network.MembershipResource
import cc.orbexa.hhy.network.ContractR18MembershipApi
import cc.orbexa.hhy.network.R18MembershipOrderRequest
import cc.orbexa.hhy.network.R18MembershipPage
import cc.orbexa.hhy.network.R18MembershipUpgradeOrderRequest
import cc.orbexa.hhy.network.R18MembershipUpgradeQuoteRequest
import cc.orbexa.hhy.network.R12ContentStatusRequest
import cc.orbexa.hhy.network.R12CopyContentRequest
import cc.orbexa.hhy.network.R12CopyContentResult
import cc.orbexa.hhy.network.R12SubmitContentRequest
import cc.orbexa.hhy.network.R19PropOrderRequest
import cc.orbexa.hhy.network.R19PropPage
import cc.orbexa.hhy.network.R19PropResource
import cc.orbexa.hhy.network.R19PropUseRequest
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.UserSelfResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import cc.orbexa.hhy.network.RewardAccountResource
import cc.orbexa.hhy.network.SearchResultPageResource
import cc.orbexa.hhy.network.SearchResultResource
import cc.orbexa.hhy.network.SearchTermPageResource
import cc.orbexa.hhy.network.SearchTermResource
import cc.orbexa.hhy.network.ShareResultResource
import cc.orbexa.hhy.network.UpdateType
import cc.orbexa.hhy.network.UserSecuritySessionPageMeta
import cc.orbexa.hhy.network.UserSecuritySessionPageResource
import cc.orbexa.hhy.network.UserSecuritySessionResource
import cc.orbexa.hhy.network.VersionCheckRequest
import cc.orbexa.hhy.network.VersionPolicy
import cc.orbexa.hhy.shell.HhyShellScreen
import cc.orbexa.hhy.shell.HhyTopLevelDestination
import cc.orbexa.hhy.membership.R18MembershipBenefitsScreen
import cc.orbexa.hhy.membership.R18MembershipCenterScreen
import cc.orbexa.hhy.membership.R18MembershipPurchaseScreen
import cc.orbexa.hhy.membership.R18MembershipUpgradeScreen
import cc.orbexa.hhy.prop.R19MyPropsScreen
import cc.orbexa.hhy.prop.R19PropStoreScreen
import cc.orbexa.hhy.prop.R19PropUseScreen
import cc.orbexa.hhy.startup.StartupVisualAuditMode
import cc.orbexa.hhy.startup.StartupVisualAuditScreen
import java.io.InputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R08 closure-only visual audit for historical Android surfaces.
 *
 * Debug wrappers select states but always render production composables. No wrapper or fixture is
 * present in the release variant and no credential, identity value, or production success is used.
 */
class HistoricalVisualAuditTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var device: UiDevice
    private lateinit var target: Context
    private var previousScreenDigest: String? = null
    private val screenshotDirectory = "Pictures/hhy-ci-screenshots"
    private val authApi = VisualAuthApi()
    private val identityApi = VisualIdentityApi()

    @Before
    fun prepareVisualHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        target = instrumentation.targetContext
    }

    @Test
    fun startupLoadingProducesBoundVisualEvidence() {
        setAuditContent { StartupVisualAuditScreen(StartupVisualAuditMode.LOADING) }
        waitForText("正在启动")
        captureStable("10-r02-startup.png")
    }

    @Test
    fun startupMaintenanceProducesBoundVisualEvidence() {
        setAuditContent { StartupVisualAuditScreen(StartupVisualAuditMode.MAINTENANCE) }
        waitForText("系统维护中")
        captureStable("11-r02-maintenance.png")
    }

    @Test
    fun startupUpdateProducesBoundVisualEvidence() {
        setAuditContent { StartupVisualAuditScreen(StartupVisualAuditMode.UPDATE) }
        waitForText("发现新版本 1.2.3")
        captureStable("12-r02-update.png")
    }

    @Test
    fun loginRegisterAndResetProduceBoundVisualEvidence() {
        setAuditContent { AuthVisualAuditScreen(AuthVisualAuditMode.LOGIN, authApi) }
        waitForText("安全登录，开启协作")
        captureStable("13-r02-password-login.png")
        composeRule.onNodeWithText("短信验证码登录").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("短信验证码")
        captureStable("14-r02-sms-login.png")
        composeRule.onNodeWithText("注册账号").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("创建账号，加入可信协作")
        captureStable("15-r02-register.png")
        composeRule.onNodeWithContentDescription("返回").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("安全登录，开启协作")
        composeRule.onNodeWithText("忘记密码").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("验证身份，重置登录密码")
        captureStable("16-r02-reset-password.png")
    }

    @Test
    fun blockedAccountProducesBoundVisualEvidence() {
        setAuditContent { AuthVisualAuditScreen(AuthVisualAuditMode.ACCOUNT_BLOCKED, authApi) }
        waitForText("账号已受限")
        captureStable("17-r02-account-blocked.png")
    }

    @Test
    fun loginDevicesProduceBoundVisualEvidence() {
        setAuditContent { AuthVisualAuditScreen(AuthVisualAuditMode.LOGIN_DEVICES, authApi) }
        waitForText("登录设备")
        composeRule.onNodeWithText("加载登录设备").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Pixel 7 测试设备")
        waitForText("当前设备")
        captureStable("18-r02-login-devices.png")
    }

    @Test
    fun changePasswordProducesBoundVisualEvidence() {
        setAuditContent { AuthVisualAuditScreen(AuthVisualAuditMode.CHANGE_PASSWORD, authApi) }
        waitForText("修改登录密码")
        captureStable("19-r02-change-password.png")
    }

    @Test
    fun accountCancellationProducesBoundVisualEvidence() {
        setAuditContent { AuthVisualAuditScreen(AuthVisualAuditMode.ACCOUNT_CANCELLATION, authApi) }
        waitForText("注销账号")
        captureStable("20-r02-account-cancellation.png")
    }

    @Test
    fun identityHomeProducesBoundVisualEvidence() {
        setAuditContent { IdentityVisualAuditScreen(IdentityVisualAuditMode.HOME, identityApi) }
        waitForText("尚未完成实名认证")
        captureStable("22-r05-identity-home.png")
    }

    @Test
    fun identityFormProducesBoundVisualEvidence() {
        setAuditContent { IdentityVisualAuditScreen(IdentityVisualAuditMode.FORM, identityApi) }
        waitForText("请填写本人真实信息")
        waitForText("提交并开始活体检测")
        captureStable("23-r05-identity-form.png")
    }

    @Test
    fun identityLivenessProducesBoundVisualEvidence() {
        setAuditContent { IdentityVisualAuditScreen(IdentityVisualAuditMode.LIVENESS, identityApi) }
        waitForText("活体检测")
        waitForAnyText("准备开始活体检测", "已准备好开始检测")
        captureStable("24-r05-identity-liveness.png")
    }

    @Test
    fun identityResultProducesBoundVisualEvidence() {
        setAuditContent { IdentityVisualAuditScreen(IdentityVisualAuditMode.RESULT, identityApi) }
        waitForText("实名认证已完成")
        captureStable("25-r05-identity-result.png")
    }

    @Test
    fun mediaUploadProducesBoundVisualEvidence() {
        setAuditContent {
            MediaUploadSheet(
                api = visualMediaApi,
                accessToken = "visual-audit-token",
                purpose = "PROJECT_IMAGE",
                onCompleted = {},
                onDismiss = {},
                maxConcurrentUploads = 2,
            )
        }
        waitForText("添加文件")
        waitForText("还没有选择文件")
        captureStable("21-r04-media-upload.png")
    }

    @Test
    fun homeProducesBoundVisualEvidence() {
        setAuditContent {
            HhyShellScreen(
                user = UserSelfResource(
                    id = "visual-user",
                    phoneMasked = "138****0000",
                    nickname = "合伙云用户",
                    avatarUrl = null,
                    bio = "寻找真实合作机会",
                    status = "ACTIVE",
                    identityStatus = "VERIFIED",
                    version = 1,
                ),
                experienceApi = experienceApi,
                accessToken = "visual-audit-token",
            )
        }
        waitForText("搜索项目 / App / 群聊 / 团队长")
        waitForText("四大分类")
        waitForText("公开合作推荐")
        captureStable("26-r06-home.png")
    }

    @Test
    fun meHomeProducesBoundVisualEvidence() {
        setAuditContent {
            HhyShellScreen(
                user = visualMeUser,
                selectedDestination = HhyTopLevelDestination.ME,
                meApi = visualMeApi,
                accessToken = "visual-audit-token",
            )
        }
        waitForText("奖励资产")
        waitForText("1,268.88")
        waitForText("常用功能")
        captureStable("41-r12-me-home.png")
    }

    @Test
    fun aboutProducesBoundVisualEvidence() {
        setAuditContent { AboutScreen(experienceApi, "visual-audit-token") {} }
        waitForText("关于与检查更新")
        waitForText("版本状态")
        captureStable("27-r06-about.png")
    }

    @Test
    fun searchProducesBoundVisualEvidence() {
        setAuditContent {
            R07SearchScreen(
                api = discoveryApi,
                accessToken = "visual-audit-token",
                onBack = {},
                onPublisherSelected = {},
                onSessionExpired = {},
            )
        }
        waitForText("找到下一次合作")
        waitForText("热门合作")
        captureStable("28-r07-search-landing.png")

        composeRule.onNodeWithTag("r07.search.input").performTextInput("合作")
        composeRule.onNodeWithTag("r07.search.submit").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("公开合作项目")
        captureStable("29-r07-search-results.png")
    }

    @Test
    fun publisherProducesBoundVisualEvidence() {
        setAuditContent {
            R07PublisherScreen(
                api = discoveryApi,
                accessToken = "visual-audit-token",
                publisherId = "publisher-visual",
                onBack = {},
                onSessionExpired = {},
            )
        }
        waitForText("合伙云内容团队")
        waitForText("公开内容")
        captureStable("30-r07-publisher.png")
    }

    @Test
    fun searchHistoryDialogProducesBoundVisualEvidence() {
        setAuditContent {
            R07SearchScreen(
                api = discoveryApi,
                accessToken = "visual-audit-token",
                onBack = {},
                onPublisherSelected = {},
                onSessionExpired = {},
            )
        }
        waitForText("最近合作")
        composeRule.onNodeWithText("清空").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("清空搜索历史？")
        captureStable("32-r07-clear-history-dialog.png")
    }

    @Test
    fun r13FavoritesProduceBoundVisualEvidence() {
        setAuditContent {
            R13FavoritesScreen(
                api = visualR13Api,
                accessToken = "visual-audit-token",
                onBack = {},
                onContentSelected = {},
                onSessionExpired = {},
            )
        }
        waitForText("我的收藏")
        waitForText("R13候选协作项目")
        waitForText("合伙云项目团队")
        captureStable("27-r13-favorites.png")
    }

    @Test
    fun r13HistoryProducesBoundVisualEvidence() {
        setAuditContent {
            R13HistoryScreen(
                api = visualR13Api,
                accessToken = "visual-audit-token",
                onBack = {},
                onContentSelected = {},
                onSessionExpired = {},
            )
        }
        waitForText("浏览记录")
        waitForText("R13品牌联合增长计划")
        waitForText("内容更新：2026-07-24", substring = true)
        composeRule.onNodeWithTag("r13.history.top-bar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        captureStable("28-r13-history.png")
    }

    @Test
    fun r13ShareSheetProducesBoundVisualEvidence() {
        setAuditContent {
            R13ContentActionSheet(
                sheet = R13ContentSheet.SHARE,
                api = visualR13Api,
                accessToken = "visual-audit-token",
                contentId = "r13-project-1",
                contentTitle = "R13候选协作项目",
                onDismiss = {},
                onCompleted = {},
                onSessionExpired = {},
            )
        }
        waitForText("分享")
        waitForText("微信")
        waitForText("继续分享")
        captureStable("29-r13-share-sheet.png")
    }

    @Test
    fun r13InvalidFeedbackSheetProducesBoundVisualEvidence() {
        setAuditContent {
            R13ContentActionSheet(
                sheet = R13ContentSheet.INVALID_FEEDBACK,
                api = visualR13Api,
                accessToken = "visual-audit-token",
                contentId = "r13-project-1",
                contentTitle = "R13候选协作项目",
                feedbackChannels = listOf("LINK", "WECHAT"),
                onDismiss = {},
                onCompleted = {},
                onSessionExpired = {},
            )
        }
        waitForText("联系方式失效反馈")
        waitForText("链接无法打开")
        waitForText("详细说明（选填）")
        composeRule.onNodeWithTag("r13.invalid.actions").assertIsDisplayed()
        composeRule.onNodeWithText("提交反馈").assertIsDisplayed()
        captureStable("30-r13-invalid-feedback-sheet.png")
    }

    @Test
    fun r14ConversationListProducesBoundVisualEvidence() {
        setAuditContent {
            R14ConversationListScreen(
                api = visualR14Api,
                accessToken = "visual-audit-token",
                contentPadding = PaddingValues(),
                onConversationSelected = {},
                onSessionExpired = {},
            )
        }
        waitForText("消息")
        waitForText("真实合作伙伴")
        waitForText("项目资料已经收到")
        captureStable("31-r14-conversations.png")
    }

    @Test
    fun r14ChatDetailProducesBoundVisualEvidence() {
        setAuditContent {
            R14ChatDetailScreen(
                api = visualR14Api,
                mediaApi = visualMediaApi,
                accessToken = "visual-audit-token",
                conversationId = "r14-conversation-1",
                conversationVersion = 4,
                currentUserId = visualR14Self.userId,
                initialPeer = visualR14Peer,
                onBack = {},
                onSessionExpired = {},
            )
        }
        waitForText("真实合作伙伴")
        waitForText("项目资料已经收到")
        waitForText("请查看最新合作范围")
        captureStable("32-r14-chat-detail.png")
    }

    @Test
    fun r14ContactSheetProducesBoundVisualEvidence() {
        setAuditContent {
            R14ContactSheet(
                peer = visualR14Peer,
                submitting = false,
                failure = null,
                onDismiss = {},
                onSubmit = { _, _ -> },
            )
        }
        waitForText("发送联系方式")
        waitForText("手机号")
        waitForText("备注（可选）")
        captureStable("33-r14-contact-sheet.png")
    }

    @Test
    fun r14ReportSheetProducesBoundVisualEvidence() {
        setAuditContent {
            R14ReportSheet(
                peer = visualR14Peer,
                reasons = listOf(
                    R14ReportReasonOption("HARASSMENT", "骚扰"),
                    R14ReportReasonOption("DUPLICATE_BULK_MESSAGE", "重复群发消息"),
                    R14ReportReasonOption("DANGEROUS_LINK", "危险链接"),
                ),
                messages = visualR14Messages,
                draft = R14ReportDraft(
                    reasonCode = "HARASSMENT",
                    messageIds = setOf("r14-message-peer"),
                ),
                versionAvailable = true,
                submitting = false,
                failure = null,
                onDismiss = {},
                onDraftChange = {},
                onAddEvidence = {},
                onSubmit = {},
            )
        }
        waitForText("举报聊天")
        waitForText("骚扰")
        waitForText("消息证据")
        composeRule.onNodeWithTag("r14.report.actions").assertIsDisplayed()
        composeRule.onNodeWithText("提交举报").assertIsDisplayed()
        captureStable("34-r14-report-sheet.png")
    }

    @Test
    fun r14BlockDialogProducesBoundVisualEvidence() {
        setAuditContent {
            R14BlockDialog(
                peer = visualR14Peer,
                unblock = false,
                submitting = false,
                failure = null,
                onDismiss = {},
                onConfirm = {},
            )
        }
        waitForText("确认拉黑")
        waitForText("拉黑原因（可选）")
        captureStable("35-r14-block-dialog.png")
    }

    @Test
    fun r14DeleteDialogProducesBoundVisualEvidence() {
        setAuditContent {
            R14DeleteConversationDialog(
                peer = visualR14Peer,
                submitting = false,
                failure = null,
                onDismiss = {},
                onConfirm = {},
            )
        }
        waitForText("删除会话")
        waitForText("不会删除对方的消息记录。", substring = true)
        captureStable("36-r14-delete-dialog.png")
    }

    @Test
    fun r18MembershipCenterProducesBoundVisualEvidence() {
        setAuditContent {
            R18MembershipCenterScreen(visualR18Api, "visual-r18-token", {}, {}, {}, {}, {})
        }
        waitForText("会员中心")
        waitForText("选择套餐")
        captureStable("40-r18-scr-member-001.png")
    }

    @Test
    fun r18MembershipPurchaseProducesBoundVisualEvidence() {
        setAuditContent {
            R18MembershipPurchaseScreen(visualR18Api, "visual-r18-token", "pro-monthly", {}, {}, {})
        }
        waitForText("确认开通 Pro")
        waitForText("应付金额：", substring = true)
        captureStable("41-r18-scr-member-002.png")
    }

    @Test
    fun r18MembershipUpgradeProducesBoundVisualEvidence() {
        setAuditContent {
            R18MembershipUpgradeScreen(visualR18Api, "visual-r18-token", {}, {})
        }
        waitForText("升级 Pro")
        waitForText("目标套餐")
        captureStable("42-r18-scr-member-003.png")
    }

    @Test
    fun r18MembershipBenefitsProducesBoundVisualEvidence() {
        setAuditContent {
            R18MembershipBenefitsScreen(visualR18Api, "visual-r18-token", {}, {})
        }
        waitForText("Pro 权益")
        waitForText("发布额度")
        captureStable("43-r18-scr-member-004.png")
    }

    @Test
    fun r19PropStoreProducesBoundVisualEvidence() {
        composeRule.setContent { HhyTheme { R19PropStoreScreen(visualR19Api, "visual-r19-token", {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("hhy.screen.scr-prop-001").assertIsDisplayed()
        composeRule.onNodeWithText("头条加速").assertIsDisplayed()
        captureStable("44-r19-scr-prop-001.png")
    }

    @Test
    fun r19MyPropsProducesBoundVisualEvidence() {
        composeRule.setContent { HhyTheme { R19MyPropsScreen(visualR19Api, "visual-r19-token", {}, {}, {}, {}) } }
        composeRule.onNodeWithTag("hhy.screen.scr-prop-002").assertIsDisplayed()
        composeRule.onNodeWithText("我的道具").assertIsDisplayed()
        captureStable("45-r19-scr-prop-002.png")
    }

    @Test
    fun r19PropUseProducesBoundVisualEvidence() {
        composeRule.setContent { HhyTheme { R19PropUseScreen(visualR19Api, visualR19ContentApi, "visual-r19-token", "1901", {}, {}, {}) } }
        composeRule.onNodeWithTag("hhy.screen.scr-prop-003").assertIsDisplayed()
        composeRule.onNodeWithText("品牌联合增长计划").assertIsDisplayed()
        captureStable("46-r19-scr-prop-003.png")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun setAuditContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            HhyTheme {
                val focusManager = LocalFocusManager.current
                val keyboardController = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                content()
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForAnyText(first: String, second: String) {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(first).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(second).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun captureStable(name: String) {
        var priorSample: String? = null
        var stableMatches = 0
        repeat(20) { sample ->
            dismissSystemAnrIfPresent()
            val temporary = File(target.cacheDir, "historical-visual-sample-$sample.png")
            assertTrue("Cannot capture historical visual sample for $name", device.takeScreenshot(temporary))
            val digest = sha256(temporary)
            stableMatches = if (digest == priorSample) stableMatches + 1 else 1
            if (stableMatches >= 2 && digest != previousScreenDigest) {
                val output = File(target.cacheDir, name)
                temporary.copyTo(output, overwrite = true)
                publishScreenshot(output, name)
                previousScreenDigest = digest
                temporary.delete()
                return
            }
            priorSample = digest
            temporary.delete()
            SystemClock.sleep(350)
        }
        error("Historical screen pixels did not become stable and distinct for $name")
    }

    private fun dismissSystemAnrIfPresent() {
        val englishTitle = By.textContains("isn't responding")
        val chineseTitle = By.textContains("无响应")
        if (!device.hasObject(englishTitle) && !device.hasObject(chineseTitle)) return
        val waitAction = device.findObject(By.text("Wait")) ?: device.findObject(By.text("等待"))
            ?: error("System ANR dialog is covering the historical visual surface")
        waitAction.click()
        check(device.wait(Until.gone(englishTitle), 5_000)) {
            "English system ANR dialog did not close before historical screenshot"
        }
        check(device.wait(Until.gone(chineseTitle), 5_000)) {
            "Chinese system ANR dialog did not close before historical screenshot"
        }
        device.waitForIdle(1_000)
    }

    private fun publishScreenshot(output: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, screenshotDirectory)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = target.contentResolver
        val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create shared historical screenshot: $name")
        resolver.openOutputStream(mediaUri)?.use { sink ->
            output.inputStream().use { source -> source.copyTo(sink) }
        } ?: error("Cannot write shared historical screenshot: $name")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(mediaUri, values, null, null)
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private val experienceApi = object : ExperienceApi {
        override suspend fun home(accessToken: String) = Result.success(
            HomeSnapshot(
                serverTime = "2026-07-22T16:00:00Z",
                modules = listOf(
                    HomeModuleSnapshot(
                        id = "visual-notice",
                        type = "NOTICE",
                        title = null,
                        subtitle = null,
                        layoutType = "notice",
                        items = listOf(
                            HomeModuleItemSnapshot(
                                id = "notice-1",
                                itemType = "NOTICE",
                                title = "平台服务持续更新中",
                                subtitle = null,
                                coverUrl = null,
                                badges = emptyList(),
                                target = HomeNavigationTargetSnapshot("NONE", null, null, true),
                                trackingContext = null,
                            ),
                        ),
                        moreTarget = null,
                        trackingContext = null,
                        startAt = null,
                        endAt = null,
                    ),
                    HomeModuleSnapshot(
                        id = "visual-banner",
                        type = "BANNER",
                        title = null,
                        subtitle = null,
                        layoutType = "carousel",
                        items = listOf(
                            HomeModuleItemSnapshot(
                                id = "banner-1",
                                itemType = "BANNER",
                                title = "连接真实项目与合作伙伴",
                                subtitle = "首页内容由平台运营模块统一配置",
                                coverUrl = null,
                                badges = emptyList(),
                                target = HomeNavigationTargetSnapshot("NONE", null, null, true),
                                trackingContext = null,
                            ),
                        ),
                        moreTarget = null,
                        trackingContext = null,
                        startAt = null,
                        endAt = null,
                    ),
                    HomeModuleSnapshot(
                        id = "visual-recommendations",
                        type = "VERTICAL_LIST",
                        title = "公开合作推荐",
                        subtitle = "由服务端配置的公开内容",
                        layoutType = "content-card",
                        items = listOf(
                            HomeModuleItemSnapshot(
                                id = "project-1",
                                itemType = "CONTENT",
                                title = "公开合作项目",
                                subtitle = "浏览平台已发布的真实项目内容",
                                coverUrl = null,
                                badges = listOf("项目"),
                                target = HomeNavigationTargetSnapshot("NONE", null, null, true),
                                trackingContext = null,
                            ),
                            HomeModuleItemSnapshot(
                                id = "app-1",
                                itemType = "CONTENT",
                                title = "团队协作应用",
                                subtitle = "查看公开应用资料与真实截图",
                                coverUrl = null,
                                badges = listOf("App"),
                                target = HomeNavigationTargetSnapshot("NONE", null, null, true),
                                trackingContext = null,
                            ),
                        ),
                        moreTarget = null,
                        trackingContext = null,
                        startAt = null,
                        endAt = null,
                    ),
                ),
            ),
        )

        override suspend fun agreement(code: String) = Result.success(
            AgreementSnapshot(
                code = code,
                title = "隐私政策",
                description = "了解个人信息保护与使用规则",
                content = listOf("合伙云 Pro 按照业务需要与隐私政策处理必要信息。"),
                version = 1,
            ),
        )

        override suspend fun checkVersion(request: VersionCheckRequest) = Result.success(
            VersionPolicy(
                platform = "ANDROID",
                latestVersionCode = request.versionCode,
                latestVersionName = "1.2.3",
                updateType = UpdateType.NONE,
                downloadUrl = "https://download.orbexa.cc/app/hhy-latest.apk",
                sha256 = "0".repeat(64),
                releaseNotes = "当前已是最新版本",
                minSupportedVersionCode = request.versionCode,
                serverTime = "2026-07-22T16:00:00Z",
            ),
        )
    }

    private val visualMediaApi = object : ContractMediaApi {
        private fun <T> unavailable(): MediaCallResult<T> = MediaCallResult.Failure(503)
        override suspend fun createUploadSession(
            accessToken: String,
            idempotencyKey: String,
            request: MediaCreateUploadSessionRequest,
        ): MediaCallResult<MediaResource> = unavailable()

        override suspend fun upload(
            uploadUrl: String,
            contentType: String,
            sizeBytes: Long,
            input: () -> InputStream,
            onProgress: (Long) -> Unit,
        ): MediaCallResult<DirectUploadResource> = unavailable()

        override suspend fun completeUploadSession(
            accessToken: String,
            sessionId: String,
            idempotencyKey: String,
            request: MediaCompleteUploadSessionRequest,
        ): MediaCallResult<MediaResource> = unavailable()

        override suspend fun deleteMedia(
            accessToken: String,
            mediaId: String,
            idempotencyKey: String,
        ): MediaCallResult<CommandResultResource> = unavailable()
    }

    private val visualMeUser = UserSelfResource(
        id = "visual-r12-user",
        phoneMasked = "138****0000",
        nickname = "合伙云用户",
        avatarUrl = null,
        bio = "寻找真实合作机会",
        status = "ACTIVE",
        identityStatus = "VERIFIED",
        membershipStatus = "ACTIVE",
        version = 3,
    )

    private val visualMeApi = object : ContractR12MeApi {
        override suspend fun user(accessToken: String) = R07CallResult.Success(
            visualMeUser,
            "visual-r12-user",
            "2026-07-25T15:00:00Z",
        )

        override suspend fun membership(accessToken: String) = R07CallResult.Success(
            MembershipResource(
                name = "Pro会员",
                status = "ACTIVE",
                expiresAt = "2027-07-25T15:00:00Z",
                benefits = listOf(
                    MembershipBenefitResource(
                        benefitCode = "PUBLISH_LIMIT",
                        name = "发布额度",
                        value = kotlinx.serialization.json.JsonPrimitive(12),
                        unit = "次",
                    ),
                ),
                version = 2,
            ),
            "visual-r12-membership",
            "2026-07-25T15:00:00Z",
        )

        override suspend fun rewardAccount(accessToken: String) = R07CallResult.Success(
            RewardAccountResource(
                userId = visualMeUser.id,
                pendingCent = 32_600,
                availableCent = 126_888,
                frozenCent = 0,
                withdrawnCent = 5_000,
                version = 4,
                updatedAt = "2026-07-25T15:00:00Z",
            ),
            "visual-r12-reward",
            "2026-07-25T15:00:00Z",
        )
    }

    private val visualR19Props = listOf(
        R19PropResource("1901", "HEADLINE", "头条加速", 2, "AVAILABLE", "2026-09-30T23:59:59Z", version = 3),
        R19PropResource("1902", "REFRESH", "内容焕新", 5, "AVAILABLE", "2026-12-31T23:59:59Z", version = 1),
        R19PropResource("1903", "COLOR", "主题变色", 1, "AVAILABLE", null, version = 2),
        R19PropResource("1904", "TOP", "列表置顶", 0, "ACTIVE", null, version = 1),
    )
    private val visualR19Api = object : ContractR19PropApi {
        override suspend fun store(accessToken: String, page: Int, pageSize: Int, status: String?, keyword: String?, sort: String) =
            R07CallResult.Success(R19PropPage(visualR19Props.map { it.copy(status = "ACTIVE", quantity = 0) }, R07PageMeta(1, 20, "4", hasMore = "false")), "visual-r19-store")
        override suspend fun mine(accessToken: String, page: Int, pageSize: Int, status: String?, keyword: String?, sort: String) =
            R07CallResult.Success(R19PropPage(visualR19Props.take(3), R07PageMeta(1, 20, "3", hasMore = "false")), "visual-r19-mine")
        override suspend fun order(accessToken: String, request: R19PropOrderRequest, idempotencyKey: String) =
            R07CallResult.Success(CommandResultResource(request.skuId, status = "ACCEPTED", acceptedAt = "2026-08-04T08:00:00Z"), "visual-r19-order")
        override suspend fun use(accessToken: String, inventoryId: String, request: R19PropUseRequest, idempotencyKey: String) =
            R07CallResult.Success(CommandResultResource(inventoryId, status = "ACCEPTED", acceptedAt = "2026-08-04T08:00:00Z"), "visual-r19-use")
    }
    private val visualR19ContentPage = ContentPageResource(
        listOf(
            ContentResource("19001", "PROJECT", "品牌联合增长计划", "寻找区域合作伙伴", status = "ONLINE", version = 3),
            ContentResource("19002", "APP", "企业效率工具合作", "面向已验证团队", status = "ONLINE", version = 2),
        ),
        R07PageMeta(1, 20, "2", hasMore = "false"),
    )
    private val visualR19ContentApi = object : ContractR12Api {
        override suspend fun content(accessToken: String, id: String) = R07CallResult.Success(visualR19ContentPage.items.first(), "visual-r19-content")
        override suspend fun contents(accessToken: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?, contentType: String?, categoryCode: String?, regionCode: String?) = R07CallResult.Success(visualR19ContentPage, "visual-r19-contents")
        override suspend fun drafts(accessToken: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?) = R07CallResult.Success(visualR19ContentPage, "visual-r19-drafts")
        override suspend fun copy(accessToken: String, id: String, idempotencyKey: String, request: R12CopyContentRequest): R07CallResult<R12CopyContentResult> = R07CallResult.Failure(503)
        override suspend fun submit(accessToken: String, id: String, idempotencyKey: String, request: R12SubmitContentRequest): R07CallResult<R12CopyContentResult> = R07CallResult.Failure(503)
        override suspend fun online(accessToken: String, id: String, idempotencyKey: String, request: R12ContentStatusRequest): R07CallResult<R12CopyContentResult> = R07CallResult.Failure(503)
        override suspend fun offline(accessToken: String, id: String, idempotencyKey: String, request: R12ContentStatusRequest): R07CallResult<R12CopyContentResult> = R07CallResult.Failure(503)
        override suspend fun delete(accessToken: String, id: String, expectedVersion: Long, idempotencyKey: String): R07CallResult<CommandResultResource> = R07CallResult.Failure(503)
        override suspend fun reviews(accessToken: String, id: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?, contentType: String?, categoryCode: String?, regionCode: String?) = R07CallResult.Success(visualR19ContentPage, "visual-r19-reviews")
        override suspend fun analytics(accessToken: String, id: String, page: Int, pageSize: Int, cursor: String?, status: String?, keyword: String?, sort: String?, contentType: String?, categoryCode: String?, regionCode: String?) = R07CallResult.Success(visualR19ContentPage, "visual-r19-analytics")
    }

    private val visualR18Member = MembershipResource(
        id = "visual-r18-membership",
        skuId = "pro-monthly",
        name = "Pro 月度",
        status = "ACTIVE",
        startsAt = "2026-08-01T00:00:00Z",
        expiresAt = "2026-09-01T00:00:00Z",
        benefits = listOf(
            MembershipBenefitResource("PUBLISH_LIMIT", "发布额度", kotlinx.serialization.json.JsonPrimitive(12), "次"),
            MembershipBenefitResource("CONTACT_UNLOCK", "联系解锁", kotlinx.serialization.json.JsonPrimitive(8), "次"),
            MembershipBenefitResource("WITHDRAW_FEE", "提现服务费", kotlinx.serialization.json.JsonPrimitive(0), "%"),
            MembershipBenefitResource("STORAGE", "素材空间", kotlinx.serialization.json.JsonPrimitive(20), "GB"),
        ),
        paidValueCent = 9900,
        remainingValueCent = 3300,
        version = 1,
    )

    private val visualR18Skus = listOf(
        visualR18Member,
        visualR18Member.copy(skuId = "pro-quarterly", name = "Pro 季度", paidValueCent = 24900),
        visualR18Member.copy(skuId = "pro-yearly", name = "Pro 年度", paidValueCent = 79900),
    )

    private val visualR18Api = object : ContractR18MembershipApi {
        private val page = R18MembershipPage(visualR18Skus, R07PageMeta(page = 1, pageSize = 100, total = "3", hasMore = "false"))

        override suspend fun skus(accessToken: String) = R07CallResult.Success(page, "visual-r18-skus")
        override suspend fun current(accessToken: String) = R07CallResult.Success(visualR18Member, "visual-r18-current")
        override suspend fun purchase(accessToken: String, request: R18MembershipOrderRequest, idempotencyKey: String) =
            R07CallResult.Success(CommandResultResource(resourceId = request.skuId, status = "ACCEPTED", acceptedAt = "2026-08-04T00:00:00Z"), "visual-r18-purchase")
        override suspend fun quote(accessToken: String, request: R18MembershipUpgradeQuoteRequest, idempotencyKey: String) =
            R07CallResult.Success(visualR18Member.copy(skuId = request.targetSkuId, paidValueCent = 15000), "visual-r18-quote")
        override suspend fun upgrade(accessToken: String, request: R18MembershipUpgradeOrderRequest, idempotencyKey: String) =
            R07CallResult.Success(CommandResultResource(resourceId = request.quoteId, status = "ACCEPTED", acceptedAt = "2026-08-04T00:00:00Z"), "visual-r18-upgrade")
    }

    private val discoveryPublisher = PublisherSummaryResource(
        userId = "publisher-visual",
        nickname = "合伙云内容团队",
        bio = "专注公开项目与合作机会的内容建设",
        verified = true,
        memberBadge = "认证成员",
    )
    private val discoveryPage = R07PageMeta(page = 1, pageSize = 20, total = "1", hasMore = "false")
    private val discoveryApi = object : ContractR07Api {
        override suspend fun search(
            accessToken: String,
            query: String,
            contentType: String?,
            categoryCode: String?,
            regionCode: String?,
            cursor: String?,
            pageSize: Int,
            sort: String,
        ) = R07CallResult.Success(
            SearchResultPageResource(
                items = listOf(
                    SearchResultResource(
                        id = "content-visual",
                        contentType = "PROJECT",
                        title = "公开合作项目",
                        summary = "面向真实业务协作的公开项目说明",
                        publisher = discoveryPublisher,
                        badges = listOf("公开内容", "已认证发布者"),
                    ),
                ),
                page = discoveryPage,
            ),
            "visual-audit",
        )

        override suspend fun hot(accessToken: String, pageSize: Int) = R07CallResult.Success(
            SearchTermPageResource(
                listOf(SearchTermResource("hot-visual", "热门合作")),
                discoveryPage,
            ),
            "visual-audit",
        )

        override suspend fun history(accessToken: String, pageSize: Int) = R07CallResult.Success(
            SearchTermPageResource(
                listOf(SearchTermResource("history-visual", "最近合作")),
                discoveryPage,
            ),
            "visual-audit",
        )

        override suspend fun clearHistory(accessToken: String, idempotencyKey: String) = R07CallResult.Success(
            CommandResultResource(status = "CLEARED", acceptedAt = "2026-07-22T16:00:00Z"),
            "visual-audit",
        )

        override suspend fun publisher(accessToken: String, publisherId: String) =
            R07CallResult.Success(discoveryPublisher, "visual-audit")

        override suspend fun contents(accessToken: String, publisherId: String, cursor: String?, pageSize: Int) =
            R07CallResult.Success(
                ContentPageResource(
                    items = listOf(
                        ContentResource(
                            id = "content-visual",
                            contentType = "PROJECT",
                            title = "公开合作项目",
                            summary = "面向真实业务协作的公开项目说明",
                            publisher = discoveryPublisher,
                            contactsMasked = listOf(
                                ContactChannelSummaryResource("WECHAT", "wx_****", true, "AUTHORIZED", false),
                            ),
                            status = "PUBLISHED",
                            version = 1,
                        ),
                    ),
                    page = discoveryPage,
                ),
                "visual-audit",
            )

        override suspend fun accessContact(
            accessToken: String,
            contentId: String,
            channel: String,
            idempotencyKey: String,
            request: ContactAccessRequest,
        ): R07CallResult<ContactAccessResource> = R07CallResult.Failure(403)
    }

    private val visualR13Page = ContentPageResource(
        items = listOf(
            ContentResource(
                id = "r13-project-1",
                contentType = "PROJECT",
                title = "R13候选协作项目",
                summary = "连接真实项目需求与可验证合作资源",
                publisher = PublisherSummaryResource(
                    userId = "r13-publisher-1",
                    nickname = "合伙云项目团队",
                    verified = true,
                    memberBadge = "认证团队",
                ),
                status = "PUBLISHED",
                createdAt = "2026-07-22T09:30:00Z",
                updatedAt = "2026-07-24T10:30:00Z",
                version = 3,
            ),
            ContentResource(
                id = "r13-app-1",
                contentType = "APP",
                title = "R13品牌联合增长计划",
                summary = "面向已验证团队的品牌增长协作",
                publisher = PublisherSummaryResource(
                    userId = "r13-publisher-2",
                    nickname = "品牌增长中心",
                    verified = true,
                    memberBadge = "认证成员",
                ),
                status = "PUBLISHED",
                createdAt = "2026-07-21T08:00:00Z",
                updatedAt = "2026-07-24T08:00:00Z",
                version = 2,
            ),
            ContentResource(
                id = "r13-group-1",
                contentType = "GROUP_CHAT",
                title = "R13产品共创伙伴招募",
                summary = "围绕真实产品场景建立长期共创关系",
                publisher = PublisherSummaryResource(
                    userId = "r13-publisher-3",
                    nickname = "产品共创社区",
                    verified = true,
                ),
                status = "PUBLISHED",
                createdAt = "2026-07-20T12:00:00Z",
                updatedAt = "2026-07-23T12:00:00Z",
                version = 4,
            ),
        ),
        page = R07PageMeta(page = 1, pageSize = 20, total = "3", hasMore = "false"),
    )

    private val visualR13Api = object : ContractR13Api {
        override suspend fun favorites(
            accessToken: String,
            cursor: String?,
            pageSize: Int,
            status: String?,
            keyword: String?,
            sort: String,
        ) = R07CallResult.Success(visualR13Page, "visual-r13-favorites")

        override suspend fun history(
            accessToken: String,
            cursor: String?,
            pageSize: Int,
            status: String?,
            keyword: String?,
            sort: String,
        ) = R07CallResult.Success(visualR13Page, "visual-r13-history")

        override suspend fun unfavorite(
            accessToken: String,
            id: String,
            idempotencyKey: String,
        ) = R07CallResult.Success(
            CommandResultResource(id, status = "UNFAVORITED", acceptedAt = "2026-07-30T04:00:00Z"),
            "visual-r13-unfavorite",
        )

        override suspend fun share(
            accessToken: String,
            id: String,
            idempotencyKey: String,
            request: ContentPostContentsByIdShareRequest,
        ) = R07CallResult.Success(
            ShareResultResource(
                contentId = id,
                channel = request.channel,
                url = "https://h5.orbexa.cc/contents/r13-project-1",
                acceptedAt = "2026-07-30T04:00:00Z",
            ),
            "visual-r13-share",
        )

        override suspend fun invalidFeedback(
            accessToken: String,
            id: String,
            idempotencyKey: String,
            request: ContentPostContentsByIdInvalidFeedbackRequest,
        ) = R07CallResult.Success(
            CommandResultResource(id, status = "ACCEPTED", acceptedAt = "2026-07-30T04:00:00Z"),
            "visual-r13-feedback",
        )
    }

    private val visualR14Self = PublisherSummaryResource(
        userId = "r14-visual-self",
        nickname = "当前用户",
        verified = true,
        memberBadge = "认证成员",
    )
    private val visualR14Peer = PublisherSummaryResource(
        userId = "r14-visual-peer",
        nickname = "真实合作伙伴",
        bio = "专注品牌增长与渠道共建",
        verified = true,
        memberBadge = "认证团队",
    )
    private val visualR14Messages = listOf(
        ChatMessageResource(
            id = "r14-message-peer",
            conversationId = "r14-conversation-1",
            sender = visualR14Peer,
            clientMessageId = "r14-client-peer",
            messageType = "TEXT",
            payload = ChatTextPayload("项目资料已经收到"),
            status = "SENT",
            serverSequence = 1,
            createdAt = "2026-07-30T04:10:00Z",
            readAt = "2026-07-30T04:11:00Z",
        ),
        ChatMessageResource(
            id = "r14-message-self",
            conversationId = "r14-conversation-1",
            sender = visualR14Self,
            clientMessageId = "r14-client-self",
            messageType = "TEXT",
            payload = ChatTextPayload("请查看最新合作范围"),
            status = "SENT",
            serverSequence = 2,
            createdAt = "2026-07-30T04:12:00Z",
            readAt = null,
        ),
    )
    private val visualR14Api = object : ContractR14Api {
        override suspend fun conversations(
            accessToken: String,
            page: Int,
            pageSize: Int,
            cursor: String?,
            status: String?,
            keyword: String?,
            sort: String?,
        ) = R07CallResult.Success(
            ChatConversationPageResource(
                items = listOf(
                    ChatConversationResource(
                        id = "r14-conversation-1",
                        peer = visualR14Peer,
                        lastMessage = ChatLastMessageResource(
                            messageId = "r14-message-peer",
                            messageType = "TEXT",
                            preview = "项目资料已经收到",
                            senderId = visualR14Peer.userId,
                            createdAt = "2026-07-30T04:10:00Z",
                        ),
                        unreadCount = 1,
                        updatedAt = "2026-07-30T04:12:00Z",
                        version = 4,
                    ),
                ),
                page = R07PageMeta(page = 1, pageSize = 20, total = "1", hasMore = "false"),
            ),
            "visual-r14-conversations",
        )

        override suspend fun messages(
            accessToken: String,
            conversationId: String,
            page: Int,
            pageSize: Int,
            cursor: String?,
            status: String?,
            keyword: String?,
            sort: String?,
        ) = R07CallResult.Success(
            ChatMessagePageResource(
                items = visualR14Messages,
                page = R07PageMeta(page = 1, pageSize = 20, total = "2", hasMore = "false"),
            ),
            "visual-r14-messages",
        )

        override suspend fun send(
            accessToken: String,
            conversationId: String,
            idempotencyKey: String,
            request: ChatSendMessageRequest,
        ): R07CallResult<ChatMessageResource> = R07CallResult.Failure(503)

        override suspend fun read(
            accessToken: String,
            conversationId: String,
            idempotencyKey: String,
            request: ChatPostConversationsByIdReadRequest,
        ) = R07CallResult.Success(
            CommandResultResource(
                resourceId = request.lastReadMessageId,
                status = "READ",
                acceptedAt = "2026-07-30T04:13:00Z",
            ),
            "visual-r14-read",
        )
    }

    private class VisualAuthApi : ContractAuthApi {
        private fun unavailable() = AuthCallResult.Failure(503, null)
        override suspend fun securityChallenge(scene: String) = unavailable()
        override suspend fun passwordLogin(phone: String, password: String, challengeId: String, challengeProof: String) = unavailable()
        override suspend fun sendSms(phone: String, scene: String, challengeId: String, challengeProof: String) = unavailable()
        override suspend fun smsLogin(phone: String, smsCode: String) = unavailable()
        override suspend fun validateInviteCode(inviteCode: String) = unavailable()
        override suspend fun registrationConfig() = unavailable()
        override suspend fun register(phone: String, password: String, inviteCode: String, challengeId: String, challengeProof: String) = unavailable()
        override suspend fun resetPassword(phone: String, smsCode: String, newPassword: String) = unavailable()
        override suspend fun refresh(refreshToken: String, deviceId: String) = unavailable()
        override suspend fun sessions(accessToken: String, page: Int, pageSize: Int): AuthCallResult {
            val resource = UserSecuritySessionPageResource(
                items = listOf(
                    UserSecuritySessionResource(
                        sessionId = "visual-current",
                        device = buildJsonObject { put("deviceName", kotlinx.serialization.json.JsonPrimitive("Pixel 7 测试设备")) },
                        createdAt = "今天 09:00",
                        expiresAt = "今天 10:00",
                        status = "ACTIVE",
                        current = true,
                    ),
                    UserSecuritySessionResource(
                        sessionId = "visual-other",
                        device = buildJsonObject { put("deviceName", kotlinx.serialization.json.JsonPrimitive("Android 备用设备")) },
                        createdAt = "昨天 18:30",
                        expiresAt = "明天 18:30",
                        status = "ACTIVE",
                        current = false,
                    ),
                ),
                page = UserSecuritySessionPageMeta(1, 20, 2, false),
            )
            return AuthCallResult.Success(
                HhyNetworkJson.value.encodeToJsonElement(UserSecuritySessionPageResource.serializer(), resource).jsonObject,
                "visual-audit",
            )
        }
        override suspend fun revokeSession(accessToken: String, sessionId: String) = unavailable()
        override suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String, smsCode: String?) = unavailable()
        override suspend fun self(accessToken: String) = unavailable()
        override suspend fun createSupportTicket(accessToken: String, category: String, subject: String, content: String) = unavailable()
        override suspend fun requestCancellation(accessToken: String, reason: String, smsCode: String, expectedVersion: Long) = unavailable()
    }

    private class VisualIdentityApi : ContractIdentityApi {
        override suspend fun overview(accessToken: String) = IdentityOverviewCallResult.Success(IdentityOverviewResource("UNVERIFIED"))
        override suspend fun consent(accessToken: String) = IdentityConsentCallResult.Success(
            IdentityConsentResource("visual-audit", "实名认证授权说明", "仅用于视觉复核的非生产授权说明"),
        )
        override suspend fun createSession(accessToken: String, idempotencyKey: String, request: IdentityCreateSessionRequest) =
            IdentityCallResult.Failure(503)
        override suspend fun createLivenessToken(accessToken: String, sessionId: String, idempotencyKey: String, request: IdentityCreateLivenessTokenRequest) =
            IdentityCallResult.Failure(503)
        override suspend fun session(accessToken: String, sessionId: String) = IdentityCallResult.Failure(503)
        override suspend fun retry(accessToken: String, sessionId: String, idempotencyKey: String, request: IdentityRetrySessionRequest) =
            IdentityCallResult.Failure(503)
    }
}
