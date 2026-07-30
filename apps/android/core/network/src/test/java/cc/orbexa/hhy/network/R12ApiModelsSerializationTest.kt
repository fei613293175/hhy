package cc.orbexa.hhy.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R12ApiModelsSerializationTest {
    @Test
    fun copyRequestUsesFrozenExpectedVersionField() {
        val encoded = HhyNetworkJson.value.encodeToString(R12CopyContentRequest(expectedVersion = 7))
        assertEquals("{\"expectedVersion\":7}", encoded)
        assertFalse(encoded.contains("requestId"))
    }

    @Test
    fun copyUnionDecodesCurrentCommandResponse() {
        val result = decodeR12CopyData(buildJsonObject {
            put("resourceId", "draft-2")
            put("status", "DRAFT")
            put("version", 1)
            put("acceptedAt", "2026-07-25T00:00:00Z")
        })
        assertTrue(result is R12CopyContentResult.Command)
        assertEquals("draft-2", (result as R12CopyContentResult.Command).command.resourceId)
    }

    @Test
    fun copyUnionAlsoAcceptsFrozenContentResponse() {
        val result = decodeR12CopyData(buildJsonObject {
            put("id", "draft-3")
            put("contentType", "PROJECT")
            put("title", "新草稿")
            put("status", "DRAFT")
            put("version", 1)
        })
        assertTrue(result is R12CopyContentResult.Content)
        assertEquals("draft-3", (result as R12CopyContentResult.Content).resource.id)
    }
}
