package cc.orbexa.hhy.apppromotion

import cc.orbexa.hhy.network.ContentResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R09AppStateTest {
    @Test
    fun `form requires only frozen mandatory business facts`() {
        assertEquals(setOf("appName", "title", "description", "categoryCode"), R09AppForm().validate().keys)
        assertTrue(R09AppForm(appName = "应用", title = "推广", description = "介绍", categoryCode = "TOOLS").validate().isEmpty())
    }

    @Test
    fun `unsafe external links fail closed`() {
        assertFalse(secureHttps("http://example.com/app"))
        assertFalse(secureHttps("https://user@example.com/app"))
        assertTrue(secureHttps("https://example.com/app"))
    }

    @Test
    fun `facts come only from server attributes`() {
        val resource = ContentResource(
            id = "app_1", contentType = "APP", title = "推广标题", status = "ONLINE", version = 2,
            attributes = buildJsonObject { put("appName", "真实应用"); put("platform", "ANDROID"); put("downloadUrl", "javascript:alert(1)") },
        )
        val facts = R09AppFacts.from(resource)
        assertEquals("真实应用", facts.appName)
        assertEquals("Android", appPlatformLabel(facts.platform))
        assertEquals(null, facts.downloadUrl)
    }
}
