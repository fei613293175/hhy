package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCandidateSmokeTest {
    private lateinit var device: UiDevice
    private lateinit var target: Context
    private val screenshotDirectory = "Pictures/hhy-ci-screenshots"

    @Before
    fun prepareFreshCandidate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        target = instrumentation.targetContext
        device.pressHome()
        val launchIntent = target.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: error("Candidate package has no launch intent")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        target.startActivity(launchIntent)
        assertTrue(
            "Candidate app did not become visible",
            device.wait(Until.hasObject(By.pkg(target.packageName).depth(0)), 20_000),
        )
    }

    @Test
    fun authenticationJourneysRemainReachableAndProduceVisualEvidence() {
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))
        assertTrue(
            "Password login did not become visually stable",
            waitForPage(By.text("密码"), gone = listOf(By.text("正在启动"))),
        )
        capture("01-password-login.png")
        assertNoForbiddenVisibleText()

        clickTextContains("验证码登录")
        assertTrue(
            "SMS login did not become visually stable",
            waitForPage(By.text("短信验证码"), gone = listOf(By.text("密码"))),
        )
        capture("02-sms-login.png")
        assertNoForbiddenVisibleText()

        clickTextContains("注册账号")
        assertTrue(
            "Registration page did not become visually stable",
            waitForPage(By.text("确认密码"), gone = listOf(By.text("短信验证码登录"))),
        )
        capture("03-register.png")
        assertNoForbiddenVisibleText()
        device.pressBack()
        assertTrue(
            "Registration back did not restore its real SMS-login source",
            waitForPage(By.text("短信验证码登录"), gone = listOf(By.text("确认密码"))),
        )

        clickTextContains("忘记密码")
        assertTrue(
            "Forgot-password page did not become visually stable",
            waitForPage(By.text("新密码"), gone = listOf(By.text("短信验证码登录"))),
        )
        capture("04-forgot-password.png")
        assertNoForbiddenVisibleText()
        device.pressBack()
        assertTrue(
            "Forgot-password back did not restore its real SMS-login source",
            waitForPage(By.text("短信验证码登录"), gone = listOf(By.text("新密码"))),
        )

        assertNoForbiddenVisibleText()
    }

    private fun waitForPage(required: BySelector, gone: List<BySelector>): Boolean {
        if (!device.wait(Until.hasObject(required), 15_000)) return false
        if (gone.any { selector -> !device.wait(Until.gone(selector), 15_000) }) return false
        device.waitForIdle(2_000)
        return true
    }

    private fun clickTextContains(value: String) {
        val node = device.wait(Until.findObject(By.textContains(value)), 15_000)
            ?: error("Cannot find UI element containing: $value")
        node.click()
        device.waitForIdle()
    }

    private fun capture(name: String) {
        val output = File(target.cacheDir, name)
        assertTrue("Cannot capture $name", device.takeScreenshot(output))
        assertTrue("Screenshot is empty: $name", output.length() > 0)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, screenshotDirectory)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = target.contentResolver
        val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create shared screenshot: $name")
        resolver.openOutputStream(mediaUri)?.use { sink ->
            output.inputStream().use { source -> source.copyTo(sink) }
        } ?: error("Cannot write shared screenshot: $name")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(mediaUri, values, null, null)
        assertTrue(
            "Shared screenshot is empty: $name",
            output.length() > 0,
        )
    }

    private fun assertNoForbiddenVisibleText() {
        assertFalse("Journey exposed connection failure", device.hasObject(By.textContains("暂时无法连接")))
        val requestNumberLabel = "请求" + "编号"
        assertFalse("Journey exposed technical request number", device.hasObject(By.textContains(requestNumberLabel)))
        assertFalse("Journey exposed TraceId", device.hasObject(By.textContains("TraceId")))
    }
}
