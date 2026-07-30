package cc.orbexa.hhy.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R09ApiModelsSerializationTest {
    @Test
    fun `create App request uses frozen content and attribute fields`() {
        val encoded = HhyNetworkJson.value.encodeToString(
            R09CreateAppRequest(
                contentType = "APP",
                title = "协作工具",
                description = "帮助团队管理事项",
                categoryCode = "TOOLS",
                attributes = buildJsonObject {
                    put("appName", "协作工具")
                    put("downloadUrl", "https://download.example.com/app")
                },
            ),
        )
        assertTrue(encoded.contains("\"contentType\":\"APP\""))
        assertTrue(encoded.contains("\"appName\":\"协作工具\""))
        assertFalse(encoded.contains("apk"))
    }
}
