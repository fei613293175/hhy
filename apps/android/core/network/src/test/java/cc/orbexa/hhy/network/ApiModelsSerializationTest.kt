package cc.orbexa.hhy.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiModelsSerializationTest {
    private val json = HhyNetworkJson.value

    @Test
    fun versionCheckRequestUsesContractFieldNamesAndInt64() {
        val encoded = json.encodeToString(
            VersionCheckRequest(
                platform = "ANDROID",
                versionCode = 4_294_967_296L,
                channel = "official",
                environment = "PROD",
            ),
        )
        val fields = json.parseToJsonElement(encoded).jsonObject

        assertEquals("ANDROID", fields.getValue("platform").jsonPrimitive.content)
        assertEquals(4_294_967_296L, fields.getValue("versionCode").jsonPrimitive.content.toLong())
        assertEquals("official", fields.getValue("channel").jsonPrimitive.content)
        assertEquals("PROD", fields.getValue("environment").jsonPrimitive.content)
        assertFalse(fields.containsKey("versionName"))
    }

    @Test
    fun platformStatusDecodesTypedCapabilities() {
        val envelope = json.decodeFromString<ApiEnvelope<PlatformStatus>>(
            """
            {
              "success": true,
              "requestId": "request_1234",
              "timestamp": "2026-07-17T00:00:00Z",
              "data": {
                "maintenance": false,
                "maintenanceMessage": "",
                "capabilities": {
                  "registration": true,
                  "publishing": true,
                  "redPacket": false,
                  "withdrawal": false
                },
                "serverTime": "2026-07-17T00:00:00Z"
              }
            }
            """.trimIndent(),
        )

        assertEquals(true, envelope.data.capabilities.registration)
        assertEquals(false, envelope.data.capabilities.redPacket)
    }

    @Test
    fun versionPolicyDecodesSingleContractResource() {
        val envelope = json.decodeFromString<ApiEnvelope<VersionPolicy>>(
            """
            {
              "success": true,
              "requestId": "request_5678",
              "timestamp": "2026-07-17T00:00:00Z",
              "data": {
                "platform": "ANDROID",
                "latestVersionCode": 4294967296,
                "latestVersionName": "2.0.0",
                "updateType": "FORCED",
                "downloadUrl": "https://downloads.example.com/apps/hhy.apk",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "releaseNotes": "安全更新",
                "minSupportedVersionCode": 4000000000,
                "serverTime": "2026-07-17T00:00:00Z"
              }
            }
            """.trimIndent(),
        )

        assertEquals(4_294_967_296L, envelope.data.latestVersionCode)
        assertEquals(4_000_000_000L, envelope.data.minSupportedVersionCode)
        assertEquals(UpdateType.FORCED, envelope.data.updateType)
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            envelope.data.sha256,
        )
    }

    @Test
    fun unknownResponseFieldsAreRejected() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<PlatformCapabilities>(
                """{"registration":true,"publishing":true,"redPacket":false,"withdrawal":false,"extra":true}""",
            )
        }
    }
}
