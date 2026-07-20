package cc.orbexa.hhy

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
    private lateinit var screenshotDirectory: File

    @Before
    fun prepareFreshCandidate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        target = instrumentation.targetContext
        screenshotDirectory = File(target.getExternalFilesDir(null), "ci-screenshots").apply {
            deleteRecursively()
            mkdirs()
        }
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
        assertTrue("Password login did not load", waitForTextContains("密码登录"))
        capture("01-password-login.png")
        assertNoForbiddenVisibleText()

        clickTextContains("验证码登录")
        assertTrue("SMS login did not load", waitForTextContains("发送验证码"))
        capture("02-sms-login.png")
        assertNoForbiddenVisibleText()

        clickTextContains("注册账号")
        assertTrue("Registration page did not load", waitForTextContains("创建账号"))
        capture("03-register.png")
        assertNoForbiddenVisibleText()
        device.pressBack()
        assertTrue("Registration back did not restore login", waitForTextContains("密码登录"))

        clickTextContains("忘记密码")
        assertTrue("Forgot-password page did not load", waitForTextContains("重置登录密码"))
        capture("04-forgot-password.png")
        assertNoForbiddenVisibleText()
        device.pressBack()
        assertTrue("Forgot-password back did not restore login", waitForTextContains("密码登录"))

        assertNoForbiddenVisibleText()
    }

    private fun waitForTextContains(value: String): Boolean =
        device.wait(Until.hasObject(By.textContains(value)), 15_000)

    private fun clickTextContains(value: String) {
        val node = device.wait(Until.findObject(By.textContains(value)), 15_000)
            ?: error("Cannot find UI element containing: $value")
        node.click()
        device.waitForIdle()
    }

    private fun capture(name: String) {
        val output = File(screenshotDirectory, name)
        assertTrue("Cannot capture $name", device.takeScreenshot(output))
        assertTrue("Screenshot is empty: $name", output.length() > 0)
    }

    private fun assertNoForbiddenVisibleText() {
        assertFalse("Journey exposed connection failure", device.hasObject(By.textContains("暂时无法连接")))
        val requestNumberLabel = "请求" + "编号"
        assertFalse("Journey exposed technical request number", device.hasObject(By.textContains(requestNumberLabel)))
        assertFalse("Journey exposed TraceId", device.hasObject(By.textContains("TraceId")))
    }
}
