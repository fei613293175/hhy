package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cc.orbexa.hhy.auth.AuthVisualAuditMode
import cc.orbexa.hhy.auth.AuthVisualAuditScreen
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.identity.IdentityVisualAuditMode
import cc.orbexa.hhy.identity.IdentityVisualAuditScreen
import cc.orbexa.hhy.network.AccountCancellationRequest
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.HhyNetworkJson
import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentityConsentCallResult
import cc.orbexa.hhy.network.IdentityConsentResource
import cc.orbexa.hhy.network.IdentityCreateLivenessTokenRequest
import cc.orbexa.hhy.network.IdentityCreateSessionRequest
import cc.orbexa.hhy.network.IdentityOverviewCallResult
import cc.orbexa.hhy.network.IdentityOverviewResource
import cc.orbexa.hhy.network.IdentityRetrySessionRequest
import cc.orbexa.hhy.network.UserSecuritySessionPageMeta
import cc.orbexa.hhy.network.UserSecuritySessionPageResource
import cc.orbexa.hhy.network.UserSecuritySessionResource
import cc.orbexa.hhy.startup.StartupVisualAuditMode
import cc.orbexa.hhy.startup.StartupVisualAuditScreen
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
    private var screen by mutableStateOf(AuditScreen.STARTUP_LOADING)
    private var previousScreenDigest: String? = null
    private val screenshotDirectory = "Pictures/hhy-ci-screenshots"
    private val authApi = VisualAuthApi()
    private val identityApi = VisualIdentityApi()

    @Before
    fun prepareVisualHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        target = instrumentation.targetContext
        composeRule.setContent {
            HhyTheme {
                key(screen) {
                    when (screen) {
                        AuditScreen.STARTUP_LOADING,
                        AuditScreen.STARTUP_MAINTENANCE,
                        AuditScreen.STARTUP_UPDATE,
                        -> StartupVisualAuditScreen(
                            when (screen) {
                                AuditScreen.STARTUP_LOADING -> StartupVisualAuditMode.LOADING
                                AuditScreen.STARTUP_MAINTENANCE -> StartupVisualAuditMode.MAINTENANCE
                                AuditScreen.STARTUP_UPDATE -> StartupVisualAuditMode.UPDATE
                                else -> error("Unexpected startup visual mode")
                            },
                        )
                        AuditScreen.AUTH_LOGIN -> AuthVisualAuditScreen(AuthVisualAuditMode.LOGIN, authApi)
                        AuditScreen.AUTH_BLOCKED -> AuthVisualAuditScreen(AuthVisualAuditMode.ACCOUNT_BLOCKED, authApi)
                        AuditScreen.AUTH_DEVICES -> AuthVisualAuditScreen(AuthVisualAuditMode.LOGIN_DEVICES, authApi)
                        AuditScreen.AUTH_CHANGE_PASSWORD -> AuthVisualAuditScreen(AuthVisualAuditMode.CHANGE_PASSWORD, authApi)
                        AuditScreen.AUTH_CANCELLATION -> AuthVisualAuditScreen(AuthVisualAuditMode.ACCOUNT_CANCELLATION, authApi)
                        AuditScreen.IDENTITY_HOME -> IdentityVisualAuditScreen(IdentityVisualAuditMode.HOME, identityApi)
                        AuditScreen.IDENTITY_FORM -> IdentityVisualAuditScreen(IdentityVisualAuditMode.FORM, identityApi)
                        AuditScreen.IDENTITY_LIVENESS -> IdentityVisualAuditScreen(IdentityVisualAuditMode.LIVENESS, identityApi)
                        AuditScreen.IDENTITY_RESULT -> IdentityVisualAuditScreen(IdentityVisualAuditMode.RESULT, identityApi)
                    }
                }
            }
        }
    }

    @Test
    fun historicalProductionSurfacesProduceBoundVisualEvidence() {
        show(AuditScreen.STARTUP_LOADING, "正在启动")
        captureStable("10-r02-startup.png")
        show(AuditScreen.STARTUP_MAINTENANCE, "系统维护中")
        captureStable("11-r02-maintenance.png")
        show(AuditScreen.STARTUP_UPDATE, "发现新版本 1.2.3")
        captureStable("12-r02-update.png")

        show(AuditScreen.AUTH_LOGIN, "安全登录，开启协作")
        captureStable("13-r02-password-login.png")
        composeRule.onNodeWithText("验证码登录").performClick()
        waitForText("短信验证码")
        captureStable("14-r02-sms-login.png")
        composeRule.onNodeWithText("注册账号").performClick()
        waitForText("创建账号，加入可信协作")
        captureStable("15-r02-register.png")
        composeRule.onNodeWithContentDescription("返回").performClick()
        waitForText("安全登录，开启协作")
        composeRule.onNodeWithText("忘记密码").performClick()
        waitForText("验证身份，重置登录密码")
        captureStable("16-r02-reset-password.png")

        show(AuditScreen.AUTH_BLOCKED, "账号已受限")
        captureStable("17-r02-account-blocked.png")
        show(AuditScreen.AUTH_DEVICES, "登录设备")
        composeRule.onNodeWithText("加载登录设备").performClick()
        waitForText("当前设备：Pixel 7 测试设备")
        captureStable("18-r02-login-devices.png")
        show(AuditScreen.AUTH_CHANGE_PASSWORD, "修改登录密码")
        captureStable("19-r02-change-password.png")
        show(AuditScreen.AUTH_CANCELLATION, "注销账号")
        captureStable("20-r02-account-cancellation.png")

        show(AuditScreen.IDENTITY_HOME, "尚未完成实名认证")
        captureStable("22-r05-identity-home.png")
        show(AuditScreen.IDENTITY_FORM, "请填写本人真实信息")
        waitForText("提交并开始活体检测")
        captureStable("23-r05-identity-form.png")
        show(AuditScreen.IDENTITY_LIVENESS, "活体检测")
        waitForAnyText("准备开始活体检测", "已准备好开始检测")
        captureStable("24-r05-identity-liveness.png")
        show(AuditScreen.IDENTITY_RESULT, "实名认证已完成")
        captureStable("25-r05-identity-result.png")
    }

    private fun show(next: AuditScreen, expectedText: String) {
        composeRule.runOnUiThread { screen = next }
        waitForText(expectedText)
    }

    private fun waitForText(text: String) {
        assertTrue("Visual audit surface did not show: $text", device.wait(Until.hasObject(By.text(text)), 15_000))
        device.waitForIdle(2_000)
    }

    private fun waitForAnyText(first: String, second: String) {
        assertTrue(
            "Visual audit surface did not show either expected state",
            device.wait(Until.hasObject(By.text(first)), 10_000) || device.wait(Until.hasObject(By.text(second)), 10_000),
        )
        device.waitForIdle(2_000)
    }

    private fun captureStable(name: String) {
        var priorSample: String? = null
        var stableMatches = 0
        repeat(20) { sample ->
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

    private enum class AuditScreen {
        STARTUP_LOADING,
        STARTUP_MAINTENANCE,
        STARTUP_UPDATE,
        AUTH_LOGIN,
        AUTH_BLOCKED,
        AUTH_DEVICES,
        AUTH_CHANGE_PASSWORD,
        AUTH_CANCELLATION,
        IDENTITY_HOME,
        IDENTITY_FORM,
        IDENTITY_LIVENESS,
        IDENTITY_RESULT,
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
