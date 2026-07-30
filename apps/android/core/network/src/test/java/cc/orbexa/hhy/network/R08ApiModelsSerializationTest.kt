package cc.orbexa.hhy.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class R08ApiModelsSerializationTest {
    @Test
    fun `create project request uses frozen contract fields`() {
        val request = R08CreateProjectRequest(
            title = "智慧社区生活服务平台",
            summary = "社区服务项目",
            description = "连接社区服务供需",
            categoryCode = "COMMUNITY_SERVICE",
            regionCode = "CN-33-01",
            contacts = listOf(R08ContactInput("WECHAT", "project_owner")),
        )

        val encoded = HhyNetworkJson.value.encodeToString(request)
        val restored = HhyNetworkJson.value.decodeFromString<R08CreateProjectRequest>(encoded)

        assertEquals("PROJECT", restored.contentType)
        assertEquals("COMMUNITY_SERVICE", restored.categoryCode)
        assertEquals("WECHAT", restored.contacts.single().channel)
    }

    @Test
    fun `detail envelope preserves version and project statistics`() {
        val envelope = HhyNetworkJson.value.decodeFromString<ApiEnvelope<ContentResource>>(
            """{"success":true,"data":{"id":"project_1","contentType":"PROJECT","title":"项目","status":"ONLINE","statistics":{"viewCount":12,"favoriteCount":2,"shareCount":1,"contactAccessCount":0},"version":3},"requestId":"req_1","timestamp":"2026-07-22T00:00:00Z"}""",
        )

        assertEquals(3L, envelope.data.version)
        assertEquals(12L, envelope.data.statistics?.viewCount)
    }

    @Test
    fun `unknown response fields remain a contract violation`() {
        try {
            HhyNetworkJson.value.decodeFromString<R08ShareResource>(
                """{"contentId":"project_1","channel":"COPY_LINK","url":"https://h5.orbexa.cc/p/project_1","acceptedAt":"2026-07-22T00:00:00Z","inventedMetric":99}""",
            )
            fail("unknown fields must fail closed")
        } catch (_: Exception) {
            // Expected: HhyNetworkJson intentionally rejects contract drift.
        }
    }
}
