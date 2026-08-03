package cc.orbexa.hhy.order

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.orbexa.hhy.network.ContractR16Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import cc.orbexa.hhy.network.R16NoRefundEvidenceResource
import cc.orbexa.hhy.network.R16OrderItemResource
import cc.orbexa.hhy.network.R16OrderPage
import cc.orbexa.hhy.network.R16OrderResource
import cc.orbexa.hhy.network.R16PriceSnapshotResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R16OrderScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun orderListShowsRealFieldsAndOpensDetail() {
        var opened = ""
        compose.setContent { R16OrderListScreen(FakeR16Api(), "token", {}, { opened = it }, {}) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("ORD-TEST-001")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ORD-TEST-001").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("ORD-TEST-001", opened) }
        capture("scr-order-001.png")
    }

    @Test
    fun orderDetailShowsPriceAndNoRefundEvidence() {
        compose.setContent { R16OrderDetailScreen(FakeR16Api(), "token", "ORD-TEST-001", {}, {}) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("价格明细")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("不退款证据").assertIsDisplayed()
        compose.onNodeWithText("用户已确认不可退款约定").assertIsDisplayed()
        capture("scr-order-002.png")
    }

    @Test
    fun statusFilterUsesServerStatus() {
        val api = FakeR16Api()
        compose.setContent { R16OrderListScreen(api, "token", {}, {}, {}) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("已支付")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hhy.order.filter.PAID").performClick()
        compose.waitUntil(5_000) { api.lastStatus == "PAID" }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uri = instrumentation.targetContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/hhy-r16-visual")
        }) ?: error("Unable to create visual evidence output")
        instrumentation.targetContext.contentResolver.openOutputStream(uri)?.use { output ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output))
        } ?: error("Unable to open visual evidence output")
    }
}

private class FakeR16Api : ContractR16Api {
    var lastStatus: String? = null
    private val order = R16OrderResource(
        orderNo = "ORD-TEST-001", userId = "user-test", orderType = "SERVICE", status = "PAID", currency = "CNY",
        items = listOf(R16OrderItemResource("sku-test", "测试服务", 1, 9900, 9900)),
        priceSnapshot = R16PriceSnapshotResource(10900, 1000, 0, 9900, listOf("test-rule")),
        noRefundEvidence = R16NoRefundEvidenceResource(true, "NR-TEST", "2026-08-03T00:00:00Z"),
        paidAmountCent = 9900, createdAt = "2026-08-03T00:00:00Z", paidAt = "2026-08-03T00:01:00Z", version = 1,
    )
    override suspend fun orders(accessToken: String, status: String?, page: Int, pageSize: Int): R07CallResult<R16OrderPage> {
        lastStatus = status
        return R07CallResult.Success(R16OrderPage(if (status == null || status == "PAID") listOf(order) else emptyList(), R07PageMeta(page.toLong(), pageSize.toLong(), "1", null, "false")), "r16-test")
    }
    override suspend fun order(accessToken: String, orderNo: String) = R07CallResult.Success(this.order, "r16-test")
}
