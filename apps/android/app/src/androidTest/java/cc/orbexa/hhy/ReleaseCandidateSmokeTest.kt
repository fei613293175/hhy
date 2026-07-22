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

    private val fixtureTitle = "R09候选联调App"
    private val fixturePublisher = "R09候选发布者"

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
    fun authenticatedR09PagesProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))

        clickResource("r09.home.apps")
        assertTrue(
            "R09 App list did not become visible",
            waitForScreen("hhy.screen.r09.app.list.content", gone = "hhy.screen.r06.home.loaded"),
        )
        assertTrue("R09 App fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R09 list missed the approved-content notice", device.hasObject(By.text("浏览审核通过的真实App推广内容")))
        assertR09BusinessLabels()
        captureStable("01-app-list.png")
        assertNoForbiddenVisibleText()

        clickExactText(fixtureTitle)
        assertTrue(
            "R09 App detail did not become visible",
            waitForScreen("hhy.screen.r09.app.detail.content", gone = "hhy.screen.r09.app.list.content"),
        )
        assertTrue("R09 App detail fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R09 App publisher is missing", device.hasObject(By.text(fixturePublisher)))
        assertTrue("R09 App experience action is missing", device.hasObject(By.res("r09.app.download")))
        assertTrue("R09 App owner edit action is missing", device.hasObject(By.res("r09.app.edit")))
        assertTrue("R09 App website action is missing", device.hasObject(By.text("访问官网")))
        assertR09BusinessLabels()
        captureStable("02-app-detail.png")
        assertNoForbiddenVisibleText()

        clickResource("r09.app.edit")
        assertTrue(
            "R09 App editor did not become visible",
            waitForScreen("hhy.screen.r09.app.editor.content", gone = "hhy.screen.r09.app.detail.content"),
        )
        assertTrue("R09 editor missed its business title", device.hasObject(By.text("编辑App推广")))
        assertTrue("R09 editor missed the frozen App title", device.hasObject(By.text(fixtureTitle)))
        assertTrue("R09 editor missed its real-data notice", device.hasObject(By.text("只填写真实资料；这里不上传APK安装包")))
        assertFalse("R09 verified owner was incorrectly blocked by identity", device.hasObject(By.text("需要完成实名认证")))
        assertR09BusinessLabels()
        captureStable("03-app-editor.png")
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

    private fun clickResource(value: String) {
        val node = device.wait(Until.findObject(By.res(value)), 15_000)
            ?: error("Cannot find UI resource: $value")
        node.click()
        device.waitForIdle(2_000)
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

    private fun assertR09BusinessLabels() {
        assertTrue("R09 App category was not localized", device.hasObject(By.text("实用工具")))
        assertTrue("R09 App platform was not localized", device.hasObject(By.text("Android")))
        listOf("TOOLS", "ANDROID", "WECHAT", "PHONE", "EMAIL", "QR_CODE").forEach { technicalValue ->
            assertFalse("R09 exposed technical business code: $technicalValue", device.hasObject(By.text(technicalValue)))
        }
    }

}
