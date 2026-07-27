package cc.orbexa.hhy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
import java.util.regex.Pattern
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCandidateSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var device: UiDevice
    private lateinit var target: Context
    private lateinit var sessionJson: String
    private var previousScreenDigest: String? = null
    private val screenshotDirectory = "Pictures/hhy-ci-screenshots"

    private val activityMediaTitles = listOf(
        "R13候选协作项目",
        "R13品牌联合增长计划",
        "R13产品共创伙伴招募",
    )
    private val activityTargetTitle = activityMediaTitles.first()

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
        composeRule.waitForIdle()
    }

    @Test
    fun authenticatedR13PagesProduceBoundVisualEvidence() {
        val authenticatedShellReady = waitForAuthenticatedShell()
        assertTrue(
            "Authenticated shell did not become actionable: ${authenticatedShellDiagnostics()}",
            authenticatedShellReady,
        )
        assertFalse("Cold start exposed connection failure", device.hasObject(By.text("暂时无法连接")))

        clickResource("shell.navigation.me")
        assertTrue(
            "R13 me home did not become visible",
            waitForScreen("hhy.screen.r12.me", gone = "hhy.screen.r06.home.loaded"),
        )
        navigateToR13Content(
            resource = "mine.favorites",
            mode = "favorites",
            source = "hhy.screen.r12.me",
        )
        listOf("我的收藏", "全部", "项目", "APP", "群聊", "团队长", activityTargetTitle).forEach { label ->
            assertTrue("R13 favorites missed: $label", device.wait(Until.hasObject(By.text(label)), 20_000))
        }
        prepareLoadedMediaForCapture(expectedCount = 3)
        captureStable("01-favorites.png")
        assertNoForbiddenVisibleText()

        device.pressBack()
        assertTrue("R13 favorites did not return to me home", waitForScreen("hhy.screen.r12.me"))
        navigateToR13Content(
            resource = "mine.history",
            mode = "history",
            source = "hhy.screen.r12.me",
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

    private fun waitForAuthenticatedShell(): Boolean {
        val shell = By.res("hhy.shell.authenticated")
        val me = By.res("shell.navigation.me")
        val deadline = SystemClock.uptimeMillis() + 30_000
        do {
            composeRule.waitForIdle()
            val meNode = device.findObject(me)
            val actionableMe = meNode?.let { node ->
                generateSequence(node) { current -> current.parent }
                    .firstOrNull { it.isClickable && it.isEnabled }
            }
            if (device.hasObject(shell) && actionableMe != null) {
                device.waitForIdle(2_000)
                return true
            }
            device.waitForIdle(250)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private fun authenticatedShellDiagnostics(): String {
        val homeStates = listOf(
            By.res("hhy.screen.r06.home.loaded"),
            By.res("hhy.screen.r06.home.error"),
            By.res("hhy.screen.r06.home.loading"),
        ).mapIndexedNotNull { index, selector ->
            if (device.hasObject(selector)) listOf("loaded", "error", "loading")[index] else null
        }
        val meNode = device.findObject(By.res("shell.navigation.me"))
        val meActionable = meNode?.let { node ->
            generateSequence(node) { current -> current.parent }
                .any { it.isClickable && it.isEnabled }
        } ?: false
        val visibleBlockers = listOf(
            "正在启动", "系统维护中", "暂时无法连接", "立即更新", "下载更新", "登录",
        ).filter { device.hasText(it) }
        return "shell=${device.hasObject(By.res("hhy.shell.authenticated"))} " +
            "home=${homeStates.ifEmpty { listOf("none") }.joinToString()} " +
            "meVisible=${meNode != null} meActionable=$meActionable " +
            "visibleBlockers=${visibleBlockers.ifEmpty { listOf("none") }.joinToString()}"
    }

    private fun UiDevice.hasText(value: String): Boolean = hasObject(By.text(value))

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

    private fun clickResource(
        value: String,
        timeoutMillis: Long = 15_000,
        settleMillis: Long = 2_000,
    ): String {
        val resourceNode = device.wait(Until.findObject(By.res(value)), timeoutMillis)
            ?: error("Cannot find UI resource: $value")
        val node = generateSequence(resourceNode) { current -> current.parent }
            .firstOrNull { it.isClickable && it.isEnabled }
            ?: error("Cannot find clickable UI ancestor for resource: $value")
        val diagnostic =
            "resource=$value resourceClass=${resourceNode.className} " +
                "resourceClickable=${resourceNode.isClickable} resourceEnabled=${resourceNode.isEnabled} " +
                "ancestorClass=${node.className} ancestorBounds=${node.visibleBounds}"
        node.click()
        if (settleMillis > 0) device.waitForIdle(settleMillis)
        return diagnostic
    }

    private fun navigateToR13Content(resource: String, mode: String, source: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        val clickDiagnostics = mutableListOf(
            clickResource(resource, timeoutMillis = remaining(deadline), settleMillis = 0),
        )
        var phase = waitForR13Phase(mode, timeoutMillis = minOf(3_000, remaining(deadline)))
        if (phase == null && device.hasObject(By.res(source))) {
            clickDiagnostics += clickResource(
                resource,
                timeoutMillis = remaining(deadline),
                settleMillis = 0,
            )
            phase = waitForR13Phase(mode, timeoutMillis = minOf(3_000, remaining(deadline)))
        }
        if (phase == null) {
            error(r13NavigationFailure(resource, mode, source, phase, clickDiagnostics))
        }
        while (phase in setOf("loading", "refreshing", "appending")) {
            phase = waitForR13Phase(
                mode = mode,
                timeoutMillis = remaining(deadline),
                excludedPhase = phase,
            ) ?: error(r13NavigationFailure(resource, mode, source, phase, clickDiagnostics))
        }
        if (phase != "content") {
            error(r13NavigationFailure(resource, mode, source, phase, clickDiagnostics))
        }
        if (!device.wait(Until.gone(By.res(source)), remaining(deadline))) {
            error(r13NavigationFailure(resource, mode, source, phase, clickDiagnostics))
        }
        device.waitForIdle(2_000)
    }

    private fun waitForR13Phase(mode: String, timeoutMillis: Long, excludedPhase: String? = null): String? {
        if (timeoutMillis <= 0) return r13Phase(mode)?.takeUnless { it == excludedPhase }
        val deadline = SystemClock.uptimeMillis() + timeoutMillis.coerceAtLeast(0)
        do {
            val phase = r13Phase(mode)
            if (phase != null && phase != excludedPhase) return phase
            device.waitForIdle(250)
        } while (SystemClock.uptimeMillis() < deadline)
        return r13Phase(mode)?.takeUnless { it == excludedPhase }
    }

    private fun r13Phase(mode: String): String? = R13_PHASES.firstOrNull { phase ->
        device.hasObject(By.res("hhy.screen.r13.$mode.$phase"))
    }

    private fun remaining(deadline: Long): Long =
        (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

    private fun r13NavigationFailure(
        resource: String,
        mode: String,
        source: String,
        phase: String?,
        clickDiagnostics: List<String>,
    ): String =
        "R13 $mode navigation failed: phase=${phase ?: "none"} " +
            "sourceVisible=${device.hasObject(By.res(source))} " +
            "resourceVisible=${device.hasObject(By.res(resource))} " +
            "clicks=${clickDiagnostics.size} diagnostics=${clickDiagnostics.joinToString(" | ")}"

    private companion object {
        val R13_PHASES = listOf(
            "loading", "content", "empty", "refreshing", "appending", "partial_error",
            "error", "offline", "forbidden", "not_found",
        )
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

    private fun prepareLoadedMediaForCapture(expectedCount: Int) {
        val loaded = By.desc(Pattern.compile("内容图片：.+"))
        val failed = By.desc(Pattern.compile("内容图片加载失败：.+"))
        val loading = By.desc(Pattern.compile("内容图片加载中：.+"))
        val deadline = SystemClock.uptimeMillis() + 30_000
        assertTrue(
            "Candidate media title contract drifted: expected=$expectedCount actual=${activityMediaTitles.size}",
            expectedCount == activityMediaTitles.size,
        )
        val activatedDescriptions = mutableSetOf<String>()
        activityMediaTitles.forEach { title ->
            composeRule.onNodeWithText(title).performScrollTo()
            composeRule.waitForIdle()
            while (SystemClock.uptimeMillis() < deadline) {
                val failedCount = device.findObjects(failed).size
                assertTrue("Candidate media failed to load: title=$title errors=$failedCount", failedCount == 0)
                activatedDescriptions += device.findObjects(loaded).mapNotNull { it.contentDescription }
                if (device.hasObject(By.desc("内容图片：$title"))) {
                    break
                }
                device.waitForIdle(250)
            }
            if (!device.hasObject(By.desc("内容图片：$title"))) {
                error(
                    "Candidate media activation did not finish: title=$title expected=$expectedCount " +
                        "observedSuccess=${activatedDescriptions.size} " +
                        "visibleSuccess=${device.findObjects(loaded).size} " +
                        "errors=${device.findObjects(failed).size} " +
                        "loading=${device.findObjects(loading).size}",
                )
            }
        }
        composeRule.onNodeWithText(activityMediaTitles.first()).performScrollTo()
        composeRule.waitForIdle()
        while (SystemClock.uptimeMillis() < deadline) {
            val failedCount = device.findObjects(failed).size
            assertTrue("Candidate media failed after returning to list start: errors=$failedCount", failedCount == 0)
            val loadedCount = device.findObjects(loaded).size
            val loadingCount = device.findObjects(loading).size
            if (loadedCount == expectedCount && loadingCount == 0) {
                device.waitForIdle(2_000)
                return
            }
            device.waitForIdle(250)
        }
        error(
            "Candidate media was not ready at list start: expected=$expectedCount " +
                "success=${device.findObjects(loaded).size} " +
                "errors=${device.findObjects(failed).size} " +
                "loading=${device.findObjects(loading).size}",
        )
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
