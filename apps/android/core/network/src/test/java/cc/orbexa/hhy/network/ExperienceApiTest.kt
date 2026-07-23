package cc.orbexa.hhy.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceApiTest {
    @Test
    fun `home parser preserves the complete frozen module contract`() {
        val snapshot = parseHomeSnapshot(
            Json.parseToJsonElement(
                """
                {
                  "serverTime": "2026-07-23T03:00:00Z",
                  "modules": [{
                    "moduleId": "home-banner",
                    "moduleType": "BANNER",
                    "title": "今日推荐",
                    "subtitle": "真实运营配置",
                    "layoutType": "carousel",
                    "items": [{
                      "id": "banner-1",
                      "itemType": "BANNER",
                      "title": "真实活动",
                      "subtitle": "由服务端返回",
                      "coverUrl": "https://cdn.orbexa.cc/home/banner-1.png",
                      "badges": ["置顶", "Pro"],
                      "target": {
                        "targetType": "IN_APP_ROUTE",
                        "route": "/content/project/42",
                        "requiresLogin": true
                      },
                      "trackingContext": {
                        "pageCode": "SCR-HOME-001",
                        "source": "home-banner",
                        "contentId": "42",
                        "requestId": "internal-only",
                        "experimentAssignments": [{
                          "experimentKey": "home-layout",
                          "variant": "b"
                        }]
                      }
                    }],
                    "moreTarget": {
                      "targetType": "IN_APP_ROUTE",
                      "route": "/content/projects",
                      "requiresLogin": true
                    },
                    "trackingContext": {
                      "pageCode": "SCR-HOME-001",
                      "source": "home-banner",
                      "experimentAssignments": []
                    },
                    "startAt": "2026-07-23T00:00:00Z",
                    "endAt": "2026-07-31T00:00:00Z"
                  }],
                  "featureFlags": [{
                    "key": "home.banner.enabled",
                    "enabled": true,
                    "variant": "carousel",
                    "reason": "cms"
                  }],
                  "trackingContext": {
                    "pageCode": "SCR-HOME-001",
                    "source": "home",
                    "experimentAssignments": []
                  }
                }
                """.trimIndent(),
            ).jsonObject,
        )

        assertEquals("2026-07-23T03:00:00Z", snapshot.serverTime)
        assertEquals(1, snapshot.modules.size)
        val module = snapshot.modules.single()
        assertEquals("BANNER", module.type)
        assertEquals("carousel", module.layoutType)
        assertEquals("/content/projects", module.moreTarget?.route)
        assertEquals("2026-07-23T00:00:00Z", module.startAt)
        assertEquals("2026-07-31T00:00:00Z", module.endAt)

        val item = module.items.single()
        assertEquals("BANNER", item.itemType)
        assertEquals("https://cdn.orbexa.cc/home/banner-1.png", item.coverUrl)
        assertEquals(listOf("置顶", "Pro"), item.badges)
        assertEquals("/content/project/42", item.target.route)
        assertTrue(item.target.requiresLogin)
        assertEquals("42", item.trackingContext?.contentId)
        assertEquals("internal-only", item.trackingContext?.requestId)
        assertEquals("home-layout", item.trackingContext?.experimentAssignments?.single()?.experimentKey)

        assertTrue(snapshot.featureFlags.single().enabled)
        assertEquals("carousel", snapshot.featureFlags.single().variant)
        assertEquals("home", snapshot.trackingContext?.source)
    }

    @Test
    fun `missing optional home fields remain neutral instead of invented`() {
        val snapshot = parseHomeSnapshot(
            Json.parseToJsonElement(
                """{"serverTime":"2026-07-23T03:00:00Z","modules":[{"moduleId":"empty","moduleType":"NOTICE","layoutType":"notice","items":[{"id":"n1","itemType":"NOTICE","title":"公告","target":{"targetType":"NONE","requiresLogin":false}}]}]}""",
            ).jsonObject,
        )

        val module = snapshot.modules.single()
        val item = module.items.single()
        assertNull(module.title)
        assertNull(item.coverUrl)
        assertTrue(item.badges.isEmpty())
        assertEquals("NONE", item.target.targetType)
        assertFalse(item.target.requiresLogin)
        assertTrue(snapshot.featureFlags.isEmpty())
        assertNull(snapshot.trackingContext)
    }
}
