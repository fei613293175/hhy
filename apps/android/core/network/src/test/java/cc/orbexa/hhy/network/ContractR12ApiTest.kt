package cc.orbexa.hhy.network

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR12ApiTest {
    @Test
    fun statusRequestUsesFrozenExpectedVersionAndReason() {
        val encoded = HhyNetworkJson.value.encodeToString(R12ContentStatusRequest(12, "计划下架"))
        assertEquals("{\"expectedVersion\":12,\"reason\":\"计划下架\"}", encoded)
        assertFalse(encoded.contains("requestId"))
    }

    @Test
    fun pageRouteEncodesFrozenFilters() {
        val route = buildR12PageRoute(
            path = "/api/v1/me/contents",
            page = 2,
            pageSize = 20,
            cursor = "next/value",
            status = "PENDING_REVIEW",
            keyword = "合作 App",
            sort = "updatedAt:desc",
            contentType = "APP",
            categoryCode = "tool/service",
            regionCode = "CN-11",
        )
        assertTrue(route.startsWith("/api/v1/me/contents?page=2&pageSize=20"))
        assertTrue(route.contains("cursor=next%2Fvalue"))
        assertTrue(route.contains("keyword=%E5%90%88%E4%BD%9C%20App"))
        assertTrue(route.contains("sort=updatedAt%3Adesc"))
        assertTrue(route.contains("contentType=APP"))
        assertTrue(route.contains("categoryCode=tool%2Fservice"))
    }

    @Test
    fun pageRouteRejectsInvalidBoundsAndType() {
        assertTrue(runCatching { buildR12PageRoute("/api/v1/me/contents", 0, 20) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { buildR12PageRoute("/api/v1/me/contents", 1, 101) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { buildR12PageRoute("/api/v1/me/contents", 1, 20, contentType = "OTHER") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun deleteRouteCarriesOptimisticVersion() {
        assertEquals("/api/v1/contents/draft-1?expectedVersion=9", buildR12DeleteRoute("draft-1", 9))
        assertTrue(runCatching { buildR12DeleteRoute("../draft", 9) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { buildR12DeleteRoute("draft-1", -1) }.exceptionOrNull() is IllegalArgumentException)
    }
}
