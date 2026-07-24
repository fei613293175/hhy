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

    private val fixtureTitle = "R11候选增长协作团队"

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
    fun authenticatedR11PagesProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))
        assertTrue("Home missed the search entry", device.hasObject(By.textContains("搜索项目")))
        assertTrue("Home missed the team leader category", device.hasObject(By.text("团队长")))
        captureStable("01-home.png")
        assertNoForbiddenVisibleText()

        clickResource("home.category.team-leader")
        assertTrue(
            "R11 team leader list did not become visible",
            waitForScreen("hhy.screen.r11.team-leader.list.content", gone = "hhy.screen.r06.home.loaded"),
        )
        assertTrue("R11 team leader fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R11 list missed its business heading", device.hasObject(By.text("发现真实团队与合作方向")))
        assertTrue("R11 list missed the team size", device.hasObject(By.text("10-50人")))
        captureStable("02-team-leader-list.png")
        assertNoForbiddenVisibleText()

        clickExactText(fixtureTitle)
        assertTrue(
            "R11 team leader detail did not become visible",
            waitForScreen("hhy.screen.r11.team_leader.detail.content", gone = "hhy.screen.r11.team-leader.list.content"),
        )
        assertTrue("R11 team leader detail fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R11 detail missed the localized capability", device.hasObject(By.text("品牌增长")))
        assertTrue("R11 detail missed the edit action", device.hasObject(By.text("编辑")))
        assertR11BusinessLabels()
        captureStable("03-team-leader-detail.png")
        assertNoForbiddenVisibleText()

        clickExactText("编辑")
        assertTrue(
            "R11 team leader editor did not become visible",
            waitForScreen("hhy.screen.r11.team-leader.editor.content", gone = "hhy.screen.r11.team_leader.detail.content"),
        )
        assertTrue("R11 editor missed its business title", device.hasObject(By.text("编辑团队长资料")))
        assertTrue("R11 editor missed the team identity", device.hasObject(By.text(fixtureTitle)))
        assertFalse("R11 verified owner was incorrectly blocked by identity", device.hasObject(By.text("需要实名认证")))
        assertR11BusinessLabels()
        captureStable("04-team-leader-editor.png")
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

    private fun assertR11BusinessLabels() {
        listOf("TEAM_LEADER", "WECHAT", "PHONE", "QQ", "EMAIL").forEach { technicalValue ->
            assertFalse("R11 exposed technical business code: $technicalValue", device.hasObject(By.text(technicalValue)))
        }
    }

}
