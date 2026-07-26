package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.HhyNetworkJson
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCandidateSmokeTest {
    private lateinit var device: UiDevice
    private lateinit var target: Context
    private lateinit var sessionJson: String
    private var previousScreenDigest: String? = null
    private val screenshotDirectory = "Pictures/hhy-ci-screenshots"

    private val submitTargetTitle = "R12候选发布预览项目"

    @Before
    fun prepareAuthenticatedCandidate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        target = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val bootstrapCode = requireNotNull(arguments.getString("hhyCiBootstrapCode")?.takeIf(String::isNotBlank)) {
            "Missing one-time CI bootstrap code"
        }
        val commit = requireNotNull(arguments.getString("hhyCiCommit")?.takeIf(String::isNotBlank)) {
            "Missing CI commit identity"
        }
        val runId = requireNotNull(arguments.getString("hhyCiRunId")?.takeIf(String::isNotBlank)) {
            "Missing CI run identity"
        }
        sessionJson = redeemSession(bootstrapCode, commit, runId)

        device.pressHome()
        val launchIntent = target.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: error("Candidate package has no launch intent")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launchIntent.putExtra(CI_SESSION_INTENT_EXTRA, sessionJson)
        target.startActivity(launchIntent)
        assertTrue(
            "Candidate app did not become visible",
            device.wait(Until.hasObject(By.pkg(target.packageName).depth(0)), 20_000),
        )
    }

    @Test
    fun authenticatedR12PagesProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))

        clickExactText("发布")
        assertTrue(
            "R12 publish center did not become visible",
            waitForScreen("hhy.screen.r12.publish.center.content", gone = "hhy.screen.r06.home.loaded"),
        )
        listOf("发布中心", "我的发布", "项目", "App", "群聊", "团队长").forEach { label ->
            assertTrue("R12 publish center missed: $label", device.hasObject(By.text(label)))
        }
        captureStable("01-publish-center.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue("R12 publish center did not return home", waitForScreen("hhy.screen.r06.home.loaded"))
        clickExactText("我的")
        assertTrue(
            "R12 me home did not become visible",
            waitForScreen("hhy.screen.r12.me", gone = "hhy.screen.r06.home.loaded"),
        )
        assertTrue("R12 me home missed its owner", device.wait(Until.hasObject(By.text("R12候选发布者")), 20_000))
        captureStable("02-me-home.png")
        assertNoForbiddenVisibleText()

        clickResource("mine.identity-header")
        assertTrue(
            "R12 profile did not become visible",
            waitForScreen("hhy.screen.r12.profile.content", gone = "hhy.screen.r12.me"),
        )
        assertTrue("R12 profile missed its title", device.hasObject(By.text("个人资料")))
        assertTrue("R12 profile missed its owner", device.hasObject(By.text("R12候选发布者")))
        captureStable("03-profile.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue("R12 profile did not return to me home", waitForScreen("hhy.screen.r12.me"))
        scrollUntilResource("mine.my-drafts")
        clickResource("mine.my-drafts")
        assertTrue(
            "R12 drafts did not become visible",
            waitForScreen("hhy.screen.r12.drafts", gone = "hhy.screen.r12.me"),
        )
        assertTrue("R12 submit target draft is missing", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        captureStable("04-drafts.png")
        assertNoForbiddenVisibleText()

        clickExactText(submitTargetTitle)
        assertTrue(
            "R12 content management detail did not become visible",
            waitForScreen("hhy.screen.r12.content_management.detail.content", gone = "hhy.screen.r12.drafts"),
        )
        assertTrue("R12 management detail missed its title", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        assertTrue("R12 management detail missed preview", device.hasObject(By.text("预览")))
        captureStable("05-content-management-detail.png")
        assertNoForbiddenVisibleText()

        clickExactText("预览")
        assertTrue(
            "R12 publish preview did not become visible",
            waitForScreen(
                "hhy.screen.r12.publish.preview.content",
                gone = "hhy.screen.r12.content_management.detail.content",
            ),
        )
        assertTrue("R12 publish preview missed its title", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        assertTrue("R12 publish preview missed submit", device.hasObject(By.text("确认提交")))
        captureStable("06-publish-preview.png")
        assertNoForbiddenVisibleText()

        clickExactText("确认提交")
        assertTrue(
            "R12 submit confirmation did not become visible",
            device.wait(Until.hasObject(By.text("确认提交审核")), 10_000),
        )
        clickLastExactText("确认提交")
        assertTrue(
            "R12 submit result did not become visible",
            waitForScreen("hhy.screen.r12.publish.result.success", gone = "hhy.screen.r12.publish.preview.content"),
        )
        assertTrue("R12 submit result missed its success action", device.hasObject(By.text("查看我的发布")))
        captureStable("07-submit-result.png")
        assertNoForbiddenVisibleText()

        clickExactText("查看我的发布")
        assertTrue(
            "R12 my contents did not become visible",
            waitForScreen("hhy.screen.r12.my-contents", gone = "hhy.screen.r12.publish.result.success"),
        )
        assertTrue("R12 submitted content is missing", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        captureStable("08-my-contents.png")
        assertNoForbiddenVisibleText()

        scrollUntilText("审核记录")
        clickExactText("审核记录")
        assertTrue(
            "R12 review history did not become visible",
            waitForScreen("hhy.screen.r12.content-reviews", gone = "hhy.screen.r12.my-contents"),
        )
        assertTrue("R12 review history missed its content", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        captureStable("09-content-reviews.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue("R12 review history did not return to my contents", waitForScreen("hhy.screen.r12.my-contents"))
        scrollUntilText("内容数据")
        clickExactText("内容数据")
        assertTrue(
            "R12 content analytics did not become visible",
            waitForScreen("hhy.screen.r12.content-analytics", gone = "hhy.screen.r12.my-contents"),
        )
        assertTrue("R12 content analytics missed its content", device.wait(Until.hasObject(By.text(submitTargetTitle)), 20_000))
        captureStable("10-content-analytics.png")
        assertNoForbiddenVisibleText()
        assertR12BusinessLabels()
        assertNoForbiddenVisibleText()
    }

    private fun waitForScreen(required: String, gone: String? = null): Boolean {
        if (!device.wait(Until.hasObject(By.res(required)), 30_000)) return false
        if (gone != null && !device.wait(Until.gone(By.res(gone)), 15_000)) return false
        device.waitForIdle(2_000)
        return true
    }

    private fun clickExactText(value: String) {
        val node = device.wait(Until.findObject(By.text(value)), 15_000)
            ?: error("Cannot find UI element: $value")
        node.click()
        device.waitForIdle(2_000)
    }

    private fun clickLastExactText(value: String) {
        val node = device.wait(Until.findObjects(By.text(value)), 15_000)
            .lastOrNull { it.isEnabled }
            ?: error("Cannot find enabled UI element: $value")
        node.click()
        device.waitForIdle(2_000)
    }

    private fun clickResource(value: String) {
        val node = device.wait(Until.findObject(By.res(value)), 15_000)
            ?: error("Cannot find UI resource: $value")
        node.click()
        device.waitForIdle(2_000)
    }

    private fun scrollUntilResource(value: String) {
        repeat(8) {
            if (device.hasObject(By.res(value))) return
            val scrollable = device.findObject(By.scrollable(true))
                ?: error("Cannot find a scrollable surface while looking for: $value")
            scrollable.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle(1_000)
        }
        error("Cannot find UI resource after scrolling: $value")
    }

    private fun scrollUntilText(value: String) {
        repeat(8) {
            if (device.hasObject(By.textContains(value))) return
            val scrollable = device.findObject(By.scrollable(true))
                ?: error("Cannot find a scrollable surface while looking for: $value")
            scrollable.scroll(Direction.DOWN, 0.7f)
            device.waitForIdle(1_000)
        }
        error("Cannot find UI text after scrolling: $value")
    }

    private fun captureStable(name: String) {
        var priorSample: String? = null
        var stableMatches = 0
        repeat(20) { sample ->
            dismissSystemAnrIfPresent()
            val temporary = File(target.cacheDir, "visual-sample-$sample.png")
            assertTrue("Cannot capture visual sample for $name", device.takeScreenshot(temporary))
            val digest = sha256(temporary)
            stableMatches = if (digest == priorSample) stableMatches + 1 else 1
            if (stableMatches >= 4 && digest != previousScreenDigest) {
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
        error("Screen pixels did not become stable and distinct for $name")
    }

    private fun dismissSystemAnrIfPresent() {
        val englishTitle = By.textContains("isn't responding")
        val chineseTitle = By.textContains("无响应")
        if (!device.hasObject(englishTitle) && !device.hasObject(chineseTitle)) return
        val waitAction = device.findObject(By.text("Wait")) ?: device.findObject(By.text("等待"))
            ?: error("System ANR dialog is covering the release candidate surface")
        waitAction.click()
        assertTrue(
            "English system ANR dialog did not close before candidate screenshot",
            device.wait(Until.gone(englishTitle), 5_000),
        )
        assertTrue(
            "Chinese system ANR dialog did not close before candidate screenshot",
            device.wait(Until.gone(chineseTitle), 5_000),
        )
        device.waitForIdle(1_000)
    }

    private fun publishScreenshot(output: File, name: String) {
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
    }

    private fun redeemSession(code: String, commit: String, runId: String): String {
        val deviceFingerprint = MessageDigest.getInstance("SHA-256")
            .digest("$runId:${UUID.randomUUID()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val request = JSONObject()
            .put("code", code)
            .put("commit", commit)
            .put("runId", runId)
            .put("device", JSONObject()
                .put("deviceFingerprint", deviceFingerprint)
                .put("model", Build.MODEL.take(64))
                .put("platform", "ANDROID")
                .put("osVersion", Build.VERSION.RELEASE.take(64))
                .put("appVersion", BuildConfig.VERSION_NAME.take(32)))
            .toString()
        val connection = (URL("${BuildConfig.API_BASE_URL}/internal-ci/v1/android/session").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(responseCode in 200..299) { "CI session redemption failed with HTTP $responseCode" }
        val data = JSONObject(body).getJSONObject("data").toString()
        HhyNetworkJson.value.decodeFromString<AuthSessionResource>(data)
        return data
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun assertNoForbiddenVisibleText() {
        assertFalse("Journey exposed connection failure", device.hasObject(By.textContains("暂时无法连接")))
        assertFalse("Journey exposed technical request number", device.hasObject(By.textContains("请求编号")))
        assertFalse("Journey exposed TraceId", device.hasObject(By.textContains("TraceId")))
    }

    private fun assertR12BusinessLabels() {
        listOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER", "WECHAT", "PHONE", "QQ", "EMAIL").forEach { technicalValue ->
            assertFalse("R12 exposed technical business code: $technicalValue", device.hasObject(By.text(technicalValue)))
        }
    }

}
