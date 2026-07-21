package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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

    private val fixtureQuery = "R07候选联调"
    private val fixtureTitle = "R07候选联调项目"
    private val fixtureHotTerm = "R07热门合作"

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
    fun r07PagesLoadRealDataAndProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))
        clickResource("r07.home.search")
        assertTrue(
            "R07 search landing page did not become visible",
            waitForScreen("hhy.screen.r07.search", gone = "hhy.screen.r06.home.loaded"),
        )
        assertTrue("R07 hot-search fixture is missing", device.wait(Until.hasObject(By.text(fixtureHotTerm)), 20_000))
        captureStable("01-search-landing.png")
        assertNoForbiddenVisibleText()

        val input = device.wait(Until.findObject(By.res("r07.search.input")), 10_000)
            ?: error("Cannot find R07 search input")
        input.setText(fixtureQuery)
        clickResource("r07.search.submit")
        assertTrue(
            "R07 search results did not become visible",
            waitForScreen("hhy.screen.r07.search.results", gone = "hhy.screen.r07.search"),
        )
        assertTrue("R07 search result fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        captureStable("02-search-results.png")
        assertNoForbiddenVisibleText()

        clickContainsText("查看发布者：")
        assertTrue(
            "R07 publisher page did not become visible",
            waitForScreen("hhy.screen.r07.publisher", gone = "hhy.screen.r07.search.results"),
        )
        assertTrue("R07 publisher fixture is missing", device.wait(Until.hasObject(By.text("R07候选发布者")), 20_000))
        assertTrue("R07 publisher content is missing", device.hasObject(By.text(fixtureTitle)))
        captureStable("03-publisher.png")
        assertNoForbiddenVisibleText()

        clickVisibleTextContains("微信")
        assertTrue(
            "R07 contact sheet business content did not become visible",
            device.wait(Until.hasObject(By.text("联系方式")), 15_000),
        )
        assertTrue(
            "R07 contact sheet semantic marker did not become visible",
            device.wait(Until.hasObject(By.res("hhy.sheet.r07.contact")), 15_000),
        )
        assertTrue("Contact sheet missed protected action", device.hasObject(By.text("获取联系方式")))
        assertTrue("Contact sheet missed close action", device.hasObject(By.text("关闭")))
        assertSecureWindow()
        clickExactText("关闭")
        assertTrue("R07 contact sheet did not close", device.wait(Until.gone(By.res("hhy.sheet.r07.contact")), 10_000))

        device.pressBack()
        assertTrue("Back from publisher did not restore search results", waitForScreen("hhy.screen.r07.search.results"))
        assertTrue("Restored search result fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        device.pressBack()
        assertTrue("Back from search did not restore the shell", waitForScreen("hhy.screen.r06.home.loaded"))
        clickResource("r07.home.search")
        assertTrue("R07 search history did not load", device.wait(Until.hasObject(By.text(fixtureQuery)), 20_000))
        clickExactText("清空")
        assertTrue(
            "R07 clear-history dialog did not become visible",
            device.wait(Until.hasObject(By.res("hhy.dialog.r07.search-history-clear")), 10_000),
        )
        captureStable("04-clear-history-dialog.png")
        assertNoForbiddenVisibleText()
        clickExactText("确认清空")
        assertTrue(
            "R07 clear-history dialog did not close",
            device.wait(Until.gone(By.res("hhy.dialog.r07.search-history-clear")), 15_000),
        )
        assertFalse("R07 search history was not cleared", device.hasObject(By.text(fixtureQuery)))
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

    private fun clickContainsText(value: String) {
        val node = device.wait(Until.findObject(By.textContains(value)), 15_000)
            ?: error("Cannot find UI element containing: $value")
        node.click()
        device.waitForIdle(2_000)
    }

    private fun clickResource(value: String) {
        val node = device.wait(Until.findObject(By.res(value)), 15_000)
            ?: error("Cannot find UI resource: $value")
        node.click()
        device.waitForIdle(2_000)
    }

    private fun clickVisibleTextContains(value: String) {
        repeat(6) {
            device.findObject(By.textContains(value))?.let { node ->
                node.click()
                device.waitForIdle(2_000)
                return
            }
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 30)
            device.waitForIdle(1_000)
        }
        error("Cannot find visible UI element containing: $value")
    }

    private fun assertSecureWindow() {
        var secure = false
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
            secure = activity != null &&
                activity.window.attributes.flags.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        }
        assertTrue("Contact sheet must enable FLAG_SECURE", secure)
    }

    private fun captureStable(name: String) {
        var priorSample: String? = null
        var stableMatches = 0
        repeat(20) { sample ->
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

}
