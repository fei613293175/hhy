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

    private val fixtureTitle = "R08候选联调项目"
    private val fixturePublisher = "R08候选发布者"
    private val historicalQuery = "R07候选联调"
    private val historicalTitle = "R07候选联调项目"
    private val historicalHotTerm = "R07热门合作"

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
    fun authenticatedHistoricalAndR08PagesProduceBoundVisualEvidence() {
        assertTrue(
            "Authenticated shell did not reach its loaded screen identity",
            waitForScreen("hhy.screen.r06.home.loaded"),
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))
        captureStable("26-r06-home.png")
        assertNoForbiddenVisibleText()

        clickExactText("我的")
        assertTrue("R06 mine did not become visible", waitForScreen("hhy.screen.r06.mine", gone = "hhy.screen.r06.home.loaded"))
        clickExactText("关于与检查更新")
        assertTrue("R06 About did not load", waitForScreen("hhy.screen.r06.about.loaded", gone = "hhy.screen.r06.mine"))
        assertTrue("R06 About missed version identity", device.hasObject(By.textContains("当前版本")))
        captureStable("27-r06-about.png")
        assertNoForbiddenVisibleText()
        device.pressBack()
        assertTrue("Back from About did not restore mine", waitForScreen("hhy.screen.r06.mine"))
        clickExactText("首页")
        assertTrue("R06 home did not restore", waitForScreen("hhy.screen.r06.home.loaded"))

        clickResource("r07.home.search")
        assertTrue("R07 search landing did not become visible", waitForScreen("hhy.screen.r07.search", gone = "hhy.screen.r06.home.loaded"))
        assertTrue("R07 historical hot term is missing", device.wait(Until.hasObject(By.text(historicalHotTerm)), 20_000))
        captureStable("28-r07-search-landing.png")
        assertNoForbiddenVisibleText()
        val searchInput = device.wait(Until.findObject(By.res("r07.search.input")), 10_000)
            ?: error("Cannot find R07 search input")
        searchInput.setText(historicalQuery)
        clickResource("r07.search.submit")
        assertTrue("R07 search results did not become visible", waitForScreen("hhy.screen.r07.search.results", gone = "hhy.screen.r07.search"))
        assertTrue("R07 historical result is missing", device.wait(Until.hasObject(By.text(historicalTitle)), 20_000))
        captureStable("29-r07-search-results.png")
        assertNoForbiddenVisibleText()
        clickExactText(fixturePublisher)
        assertTrue("R07 publisher did not become visible", waitForScreen("hhy.screen.r07.publisher", gone = "hhy.screen.r07.search.results"))
        assertTrue("R07 publisher identity is missing", device.wait(Until.hasObject(By.text(fixturePublisher)), 20_000))
        captureStable("30-r07-publisher.png")
        assertNoForbiddenVisibleText()
        clickVisibleTextContains("微信")
        assertTrue("R07 contact sheet marker is missing", device.wait(Until.hasObject(By.res("hhy.sheet.r07.contact")), 15_000))
        assertTrue("R07 contact sheet missed protected action", device.hasObject(By.text("获取联系方式")))
        assertSecureWindow()
        clickExactText("关闭")
        assertTrue("R07 contact sheet did not close", device.wait(Until.gone(By.res("hhy.sheet.r07.contact")), 10_000))
        device.pressBack()
        assertTrue("Back from publisher did not restore results", waitForScreen("hhy.screen.r07.search.results"))
        device.pressBack()
        assertTrue("Back from search did not restore home", waitForScreen("hhy.screen.r06.home.loaded"))
        clickResource("r07.home.search")
        assertTrue("R07 history did not load", device.wait(Until.hasObject(By.text(historicalQuery)), 20_000))
        clickExactText("清空")
        assertTrue("R07 clear dialog did not become visible", device.wait(Until.hasObject(By.res("hhy.dialog.r07.search-history-clear")), 10_000))
        captureStable("32-r07-clear-history-dialog.png")
        assertNoForbiddenVisibleText()
        clickExactText("确认清空")
        assertTrue("R07 clear dialog did not close", device.wait(Until.gone(By.res("hhy.dialog.r07.search-history-clear")), 15_000))
        device.pressBack()
        assertTrue("Back from R07 audit did not restore home", waitForScreen("hhy.screen.r06.home.loaded"))

        clickResource("r08.home.projects")
        assertTrue(
            "R08 project list did not become visible",
            waitForScreen("hhy.screen.r08.project.list.content", gone = "hhy.screen.r06.home.loaded"),
        )
        assertTrue("R08 project fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R08 list missed the real-data notice", device.hasObject(By.text("页面内容均来自已上线项目")))
        assertR08BusinessLabels()
        captureStable("01-project-list.png")
        assertNoForbiddenVisibleText()

        clickExactText(fixtureTitle)
        assertTrue(
            "R08 project detail did not become visible",
            waitForScreen("hhy.screen.r08.project.detail.content", gone = "hhy.screen.r08.project.list.content"),
        )
        assertTrue("R08 project detail fixture is missing", device.wait(Until.hasObject(By.text(fixtureTitle)), 20_000))
        assertTrue("R08 project publisher is missing", device.hasObject(By.text(fixturePublisher)))
        assertTrue("R08 project contact action is missing", device.hasObject(By.res("r08.project.contact")))
        assertTrue("R08 owner edit action is missing", device.hasObject(By.res("r08.project.edit")))
        assertFalse("R08 detail exposed fixture contact plaintext", device.hasObject(By.textContains("candidate@example")))
        assertR08BusinessLabels()
        captureStable("02-project-detail.png")
        assertNoForbiddenVisibleText()

        clickResource("r08.project.edit")
        assertTrue(
            "R08 project editor did not become visible",
            waitForScreen("hhy.screen.r08.project.editor.content", gone = "hhy.screen.r08.project.detail.content"),
        )
        assertTrue("R08 editor missed its business title", device.hasObject(By.text("编辑项目")))
        assertTrue("R08 editor missed the frozen project title", device.hasObject(By.text(fixtureTitle)))
        assertTrue("R08 editor missed the project description field", device.hasObject(By.text("详细说明")))
        assertFalse("R08 verified owner was incorrectly blocked by identity", device.hasObject(By.text("需要完成实名认证")))
        assertR08BusinessLabels(requireContactLabel = true)
        captureStable("03-project-editor.png")
        assertNoForbiddenVisibleText()
        clickVisibleTextContains("选择项目图片")
        assertTrue("R04 media upload sheet did not become visible", device.wait(Until.hasObject(By.text("添加文件")), 15_000))
        assertTrue("R04 media upload sheet missed empty state", device.hasObject(By.text("还没有选择文件")))
        captureStable("21-r04-media-upload.png")
        assertNoForbiddenVisibleText()
        clickExactText("取消")
        assertTrue("R04 media upload sheet did not close", device.wait(Until.gone(By.text("添加文件")), 10_000))
        device.pressBack()
        assertTrue("Back from editor did not restore project detail", waitForScreen("hhy.screen.r08.project.detail.content"))
        assertTrue("Restored R08 project detail is missing", device.hasObject(By.text(fixtureTitle)))
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

    private fun clickVisibleTextContains(value: String) {
        repeat(8) {
            device.findObject(By.textContains(value))?.let { node ->
                node.click()
                device.waitForIdle(2_000)
                return
            }
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                30,
            )
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

    private fun assertR08BusinessLabels(requireContactLabel: Boolean = false) {
        assertTrue("R08 project category was not localized", device.hasObject(By.text("合作项目")))
        assertTrue("R08 project region was not localized", device.hasObject(By.text("北京")))
        if (requireContactLabel) assertTrue("R08 contact channel was not localized", device.hasObject(By.text("微信")))
        listOf("COOPERATION", "CN-11", "WECHAT", "PHONE", "EMAIL", "QR_CODE").forEach { technicalValue ->
            assertFalse("R08 exposed technical business code: $technicalValue", device.hasObject(By.text(technicalValue)))
        }
    }

}
