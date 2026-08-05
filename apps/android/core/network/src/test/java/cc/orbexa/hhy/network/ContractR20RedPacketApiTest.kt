package cc.orbexa.hhy.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR20RedPacketApiTest {
    @Test
    fun pageRouteEncodesFrozenFiltersWithoutChangingTheirMeaning() {
        assertEquals(
            "/api/v1/me/red-packet-campaigns?page=2&pageSize=20&status=PRE_REVIEWING&keyword=%E7%BA%A2%E5%8C%85%20A&sort=priority%3Adesc%2CcreatedAt%3Aasc",
            buildR20PageRoute(
                "/api/v1/me/red-packet-campaigns",
                2,
                20,
                "PRE_REVIEWING",
                "红包 A",
                "priority:desc,createdAt:asc",
            ),
        )
    }

    @Test
    fun clientUsesFrozenRoutesHeadersBodiesAndResponseModels() = runBlocking {
        val connections = mutableListOf<FakeConnection>()
        val api = UrlConnectionContractR20RedPacketApi("https://api.orbexa.cc") { uri ->
            FakeConnection(uri.toURL(), responseFor(uri)).also(connections::add)
        }
        val key = "r20-android-idem-00001"

        val page = api.campaigns("access", 2, 20, "PRE_REVIEWING", "红包 A", "priority:desc,createdAt:asc")
        val detail = api.campaign("access", "rp_20")
        api.create(
            "access",
            R20CreateRedPacketRequest(
                "content_20",
                20,
                100,
                "2026-08-05T00:00:00Z",
                "2026-08-06T00:00:00Z",
                buildJsonObject { put("region", "CN") },
            ),
            key,
        )
        api.patch("access", "rp_20", R20PatchRedPacketRequest(totalCount = 30, expectedVersion = 3), key)
        api.submitReview("access", "rp_20", R20SubmitReviewRequest(3, "资料已核对"), key)
        val quote = api.quote("access", "rp_20", R20QuoteRequest(30, 100, 4), key)
        val order = api.order("access", "rp_20", R20OrderRequest("quote_20", "ALIPAY", 4), key)

        assertEquals(
            listOf(
                "GET /api/v1/me/red-packet-campaigns?page=2&pageSize=20&status=PRE_REVIEWING&keyword=%E7%BA%A2%E5%8C%85%20A&sort=priority%3Adesc%2CcreatedAt%3Aasc",
                "GET /api/v1/red-packet-campaigns/rp_20",
                "POST /api/v1/red-packet-campaigns",
                "PATCH /api/v1/red-packet-campaigns/rp_20",
                "POST /api/v1/red-packet-campaigns/rp_20/submit-review",
                "POST /api/v1/red-packet-campaigns/rp_20/quote",
                "POST /api/v1/red-packet-campaigns/rp_20/orders",
            ),
            connections.map { "${it.requestMethod} ${it.url.file}" },
        )
        assertEquals("Bearer access", connections.first().getRequestProperty("Authorization"))
        connections.drop(2).forEach { assertEquals(key, it.getRequestProperty("X-Idempotency-Key")) }
        assertTrue(connections[2].requestBody().contains("\"contentId\":\"content_20\""))
        assertTrue(connections[3].requestBody().contains("\"expectedVersion\":3"))
        assertTrue(connections[6].requestBody().contains("\"quoteId\":\"quote_20\""))

        assertEquals("21", (page as R07CallResult.Success).data.page.total)
        assertEquals("rp_20", (detail as R07CallResult.Success).data.id)
        assertEquals("QUOTED", (quote as R07CallResult.Success).data.status)
        assertEquals("WAITING_PAYMENT", (order as R07CallResult.Success).data.status)
    }

    @Test
    fun clientRejectsUnsafeIdsKeysAndRootsBeforeOpeningAConnection() = runBlocking {
        var opened = false
        val api = UrlConnectionContractR20RedPacketApi("https://api.orbexa.cc") { uri ->
            opened = true
            FakeConnection(uri.toURL(), campaignEnvelope)
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { api.campaign("access", "../20") } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                api.submitReview("access", "rp_20", R20SubmitReviewRequest(1), "short")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            UrlConnectionContractR20RedPacketApi("http://api.orbexa.cc")
        }
        assertTrue(!opened)
    }

    private class FakeConnection(url: URL, private val response: String) : HttpURLConnection(url) {
        private val request = ByteArrayOutputStream()
        private var requestMethodValue = "GET"

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = request
        override fun getRequestMethod(): String = requestMethodValue
        override fun setRequestMethod(method: String) { requestMethodValue = method }

        fun requestBody(): String = request.toString(Charsets.UTF_8.name())
    }

    companion object {
        private val campaignEnvelope = """
            {"success":true,"requestId":"r20-campaign","timestamp":"2026-08-05T00:00:00Z","data":{"id":"rp_20","contentId":"content_20","ownerUserId":"7","status":"PRE_REVIEWING","totalCount":20,"remainingCount":20,"amountPerClaimCent":100,"principalCent":2000,"serviceFeeCent":40,"startAt":"2026-08-05T00:00:00Z","endAt":"2026-08-06T00:00:00Z","version":3}}
        """.trimIndent()
        private val pageEnvelope = """
            {"success":true,"requestId":"r20-page","timestamp":"2026-08-05T00:00:00Z","data":{"items":[{"id":"rp_20","contentId":"content_20","status":"PRE_REVIEWING","totalCount":20,"remainingCount":20,"amountPerClaimCent":100,"principalCent":2000,"serviceFeeCent":40,"version":3}],"page":{"page":2,"pageSize":20,"total":"21","hasMore":"true"}}}
        """.trimIndent()
        private val quoteEnvelope = """
            {"success":true,"requestId":"r20-quote","timestamp":"2026-08-05T00:00:00Z","data":{"resourceId":"quote_20","businessNo":"R20-QUOTE-20","status":"QUOTED","version":4,"acceptedAt":"2026-08-05T00:00:00Z"}}
        """.trimIndent()
        private val orderEnvelope = """
            {"success":true,"requestId":"r20-order","timestamp":"2026-08-05T00:00:00Z","data":{"resourceId":"order_20","businessNo":"R20-ORDER-20","status":"WAITING_PAYMENT","version":1,"acceptedAt":"2026-08-05T00:00:00Z"}}
        """.trimIndent()

        private fun responseFor(uri: URI): String = when {
            uri.rawQuery != null -> pageEnvelope
            uri.path.endsWith("/quote") -> quoteEnvelope
            uri.path.endsWith("/orders") -> orderEnvelope
            else -> campaignEnvelope
        }
    }
}
