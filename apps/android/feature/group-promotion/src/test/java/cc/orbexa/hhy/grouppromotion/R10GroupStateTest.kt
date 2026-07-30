package cc.orbexa.hhy.grouppromotion

import cc.orbexa.hhy.network.ContentResource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R10GroupStateTest {
    @Test fun frozenMandatoryFactsAreRequired() {
        assertEquals(setOf("title", "description", "categoryCode", "platform", "ownerContactValue", "joinChannel"), R10GroupForm().validate().keys)
    }

    @Test fun joinPasswordUsesDedicatedContactAndNeverAttributes() {
        val form = R10GroupForm(title = "行业交流群", description = "群聊说明", categoryCode = "COMMUNITY", platform = "WECHAT", ownerContactValue = "owner", joinPassword = "secret")
        assertTrue(form.validate().isEmpty())
        assertNull(form.attributes()["joinPassword"])
        assertEquals(listOf("WECHAT", "JOIN_PASSWORD"), form.contacts().map { it.channel })
    }

    @Test fun unsafeGroupLinksFailClosed() {
        assertFalse(secureGroupHttps("http://example.com/join"))
        assertFalse(secureGroupHttps("https://user@example.com/join"))
        assertTrue(secureGroupHttps("https://example.com/join"))
    }

    @Test fun factsUseOnlyServerAttributes() {
        val resource = ContentResource("g1", "GROUP_CHAT", "群聊", status = "ONLINE", version = 1, attributes = buildJsonObject {
            put("platform", "WECHAT"); put("groupNo", "9988"); put("groupLink", "javascript:alert(1)")
        })
        val facts = R10GroupFacts.from(resource)
        assertEquals("微信群", groupPlatformLabel(facts.platform))
        assertEquals("9988", facts.groupNo)
        assertNull(facts.groupLink)
    }
}
