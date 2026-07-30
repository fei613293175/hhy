package cc.orbexa.hhy.network

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R07ApiModelsSerializationTest {
    @Test
    fun searchPageDecodesFrozenContract() {
        val json = """
            {"success":true,"requestId":"req-1","timestamp":"2026-07-21T00:00:00Z","data":{
              "items":[{"id":"9","contentType":"PROJECT","title":"合作项目","publisher":{"userId":"7","nickname":"发布者","verified":true},"badges":[]}],
              "page":{"page":1,"pageSize":20,"total":"1","nextCursor":null,"hasMore":"false"}}}
        """.trimIndent()

        val envelope = HhyNetworkJson.value.decodeFromString<ApiEnvelope<SearchResultPageResource>>(json)

        assertEquals("合作项目", envelope.data.items.single().title)
        assertEquals("7", envelope.data.items.single().publisher?.userId)
        assertFalse(envelope.data.page.canLoadMore())
    }

    @Test
    fun contactAccessContainsOnlyAuthorizedResponseFields() {
        val json = """{"success":true,"requestId":"req-2","timestamp":"2026-07-21T00:00:00Z","data":{"channel":"EMAIL","value":"owner@example.com","accessedAt":"2026-07-21T00:00:00Z"}}"""

        val envelope = HhyNetworkJson.value.decodeFromString<ApiEnvelope<ContactAccessResource>>(json)

        assertEquals("EMAIL", envelope.data.channel)
        assertEquals("owner@example.com", envelope.data.value)
        assertTrue(envelope.data.accessedAt.endsWith("Z"))
    }

    @Test
    fun publisherContractDoesNotExposePrivateAccountFields() {
        val publisher = PublisherSummaryResource("7", "发布者", verified = true)

        assertEquals("7", publisher.userId)
        assertNull(publisher.bio)
        assertNull(publisher.memberBadge)
    }
}
