package cc.orbexa.hhy.network

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractR13ApiTest {
    @Test
    fun frozenShareRequestUsesOnlyChannel() {
        val json = HhyNetworkJson.value.encodeToString(ContentPostContentsByIdShareRequest("COPY_LINK"))
        assertEquals("{\"channel\":\"COPY_LINK\"}", json)
        assertFalse(json.contains("url"))
    }

    @Test
    fun frozenInvalidFeedbackRequestOmitsAbsentDescription() {
        val json = HhyNetworkJson.value.encodeToString(
            ContentPostContentsByIdInvalidFeedbackRequest("LINK"),
        )
        assertEquals("{\"reasonCode\":\"LINK\"}", json)
    }

    @Test
    fun transportRejectsUnsafeApiRoots() {
        assertThrows(IllegalArgumentException::class.java) {
            UrlConnectionContractR13Api("http://api.orbexa.cc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UrlConnectionContractR13Api("https://api.invalid")
        }
    }
}
