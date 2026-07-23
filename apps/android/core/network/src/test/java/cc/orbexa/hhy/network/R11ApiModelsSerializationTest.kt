package cc.orbexa.hhy.network

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R11ApiModelsSerializationTest {
    @Test
    fun teamLeaderPageUsesFrozenContentResourceShape() {
        val page = HhyNetworkJson.value.decodeFromString<ApiEnvelope<ContentPageResource>>(
            """{"success":true,"data":{"items":[{"id":"71","contentType":"TEAM_LEADER","title":"合伙团队","status":"ONLINE","version":2,"attributes":{"teamName":"合伙团队","sizeRange":"10-20"}}],"page":{"pageSize":20,"nextCursor":null,"hasMore":"false"}},"requestId":"r11-model-001","timestamp":"2026-07-24T00:00:00Z"}""",
        )
        assertEquals("TEAM_LEADER", page.data.items.single().contentType)
        assertEquals("合伙团队", page.data.items.single().title)
    }

    @Test
    fun teamLeaderDetailUsesFrozenContentAndOperationModels() {
        val detail = HhyNetworkJson.value.decodeFromString<ApiEnvelope<ContentResource>>(
            """{"success":true,"data":{"id":"71","contentType":"TEAM_LEADER","title":"合伙团队","status":"ONLINE","version":2,"contactsMasked":[{"channel":"WECHAT","maskedValue":"wx***","available":true,"accessPolicy":"LOGIN","accessed":false}],"attributes":{"teamName":"合伙团队","pastCases":["案例A"]}},"requestId":"r11-detail-001","timestamp":"2026-07-24T00:00:00Z"}""",
        )
        assertEquals("WECHAT", detail.data.contactsMasked.single().channel)
        assertEquals(2L, detail.data.version)
    }

    @Test
    fun apiRejectsUnsafeBaseUrlBeforeNetworkAccess() {
        assertTrue(runCatching { UrlConnectionContractR11Api("http://api.orbexa.cc") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { UrlConnectionContractR11Api("https://api.invalid") }.exceptionOrNull() is IllegalArgumentException)
    }
}
