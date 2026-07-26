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

    private val activityTargetTitle = "R13候选协作项目"

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
    fun authenticatedR13PagesProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))

        clickExactText("我的")
        assertTrue(
            "R13 me home did not become visible",
            waitForScreen("hhy.screen.r12.me", gone = "hhy.screen.r06.home.loaded"),
        )
        clickResource("mine.favorites")
        assertTrue(
            "R13 favorites did not become visible",
            waitForScreen("hhy.screen.r13.favorites.content", gone = "hhy.screen.r12.me"),
        )
        listOf("我的收藏", "全部", "项目", "APP", "群聊", "团队长", activityTargetTitle).forEach { label ->
            assertTrue("R13 favorites missed: $label", device.wait(Until.hasObject(By.text(label)), 20_000))
        }
        captureStable("01-favorites.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue("R13 favorites did not return to me home", waitForScreen("hhy.screen.r12.me"))
        clickResource("mine.history")
        assertTrue(
            "R13 history did not become visible",
            waitForScreen("hhy.screen.r13.history.content", gone = "hhy.screen.r12.me"),
        )
        listOf("浏览记录", "全部", "项目", "APP", "群聊", "团队长", activityTargetTitle).forEach { label ->
            assertTrue("R13 history missed: $label", device.wait(Until.hasObject(By.text(label)), 20_000))
        }
        captureStable("02-history.png")
        assertNoForbiddenVisibleText()

        clickExactText(activityTargetTitle)
        assertTrue(
            "R13 candidate project detail did not become visible",
            waitForScreen("hhy.screen.r08.project.detail.content", gone = "hhy.screen.r13.history.content"),
        )
        clickResource("r13.action.project.share")
        assertTrue(
            "R13 share sheet did not become visible",
            waitForScreen("hhy.sheet.r13.share"),
        )
        listOf("分享", "微信", "朋友圈", "复制链接", "其他", "取消", "继续分享").forEach { label ->
            assertTrue("R13 share sheet missed: $label", device.hasObject(By.text(label)))
        }
        captureStable("03-share-sheet.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue(
            "R13 share sheet did not return to project detail",
            waitForScreen("hhy.screen.r08.project.detail.content", gone = "hhy.sheet.r13.share"),
        )
        clickResource("r13.action.project.invalid-feedback")
        assertTrue(
            "R13 invalid feedback sheet did not become visible",
            waitForScreen("hhy.sheet.r13.invalid-feedback"),
        )
        clickExactText("链接无法打开")
        listOf("联系方式失效反馈", "链接无法打开", "详细说明（选填）", "取消", "提交反馈").forEach { label ->
            assertTrue("R13 invalid feedback sheet missed: $label", device.hasObject(By.text(label)))
        }
        captureStable("04-invalid-feedback-sheet.png")
        assertNoForbiddenVisibleText()
        clickExactText("提交反馈")
        assertTrue(
            "R13 invalid feedback confirmation did not become visible",
            device.wait(Until.hasObject(By.text("确认提交反馈")), 10_000),
        )
        clickExactText("再检查一下")
        assertTrue(
            "R13 feedback confirmation did not close",
            device.wait(Until.gone(By.text("确认提交反馈")), 10_000),
        )
        assertR13BusinessLabels()
        assertNoForbiddenVisibleText()
    }

    private fun waitForScreen(required: String, gone: String? = null): Boolean {
        if (!device.wait(Until.hasObject(By.res(required)), 30_000)) return false
        if (gone != null && !device.wait(Until.gone(By.res(gone)), 15_000)) return false
        device.waitForIdle(2_000)
        return true
    }

    private fun clickExactText(value: String) {
        val textNode = device.wait(Until.findObject(By.text(value)), 15_000)
            ?: error("Cannot find UI element: $value")
        val node = generateSequence(textNode) { current -> current.parent }
            .firstOrNull { it.isClickable && it.isEnabled }
            ?: error("Cannot find clickable UI ancestor: $value")
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

    private fun assertR13BusinessLabels() {
        listOf(
            "PROJECT", "GROUP_CHAT", "TEAM_LEADER", "WECHAT", "WECHAT_MOMENTS", "COPY_LINK", "OTHER",
            "QR_CODE", "LINK", "PHONE", "EMAIL", "JOIN_PASSWORD",
        ).forEach { technicalValue ->
            assertFalse("R13 exposed technical business code: $technicalValue", device.hasObject(By.text(technicalValue)))
        }
    }

}
