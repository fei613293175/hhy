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
    fun formRequiresOnlyFrozenMandatoryBusinessFacts() {
        assertEquals(setOf("appName", "title", "description", "categoryCode"), R09AppForm().validate().keys)
        assertTrue(R09AppForm(appName = "应用", title = "推广", description = "介绍", categoryCode = "TOOLS").validate().isEmpty())
    }

    @Test
    fun unsafeExternalLinksFailClosed() {
        assertFalse(secureHttps("http://example.com/app"))
        assertFalse(secureHttps("https://user@example.com/app"))
        assertTrue(secureHttps("https://example.com/app"))
    }

    @Test
    fun factsComeOnlyFromServerAttributes() {
        val resource = ContentResource(
            id = "app_1", contentType = "APP", title = "推广标题", status = "ONLINE", version = 2,
            attributes = buildJsonObject { put("appName", "真实应用"); put("platform", "ANDROID"); put("downloadUrl", "javascript:alert(1)") },
        )
        val facts = R09AppFacts.from(resource)
        assertEquals("真实应用", facts.appName)
        assertEquals("Android", appPlatformLabel(facts.platform))
        assertEquals(null, facts.downloadUrl)
    }

    @Test
    fun appCodesAlwaysRenderAsBusinessLabels() {
        assertEquals("实用工具", appCategoryLabel("TOOLS"))
        assertEquals("其他应用", appCategoryLabel("UNREGISTERED_APP_CATEGORY"))
        assertEquals("Android", appPlatformLabel("ANDROID"))
        assertEquals("其他平台", appPlatformLabel("UNREGISTERED_PLATFORM"))
    }

    @Test
    fun sameAppIntentReusesKeyAndChangedBodyRotatesKey() {
        val keys = R09IntentKeys()
        val first = keys.forBody("create", "body-a")
        assertEquals(first, keys.forBody("create", "body-a"))
        assertTrue(first != keys.forBody("create", "body-b"))
        keys.consume("create", "body-a")
        assertTrue(first != keys.forBody("create", "body-a"))
    }
}
