package cc.orbexa.hhy.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsSerializationTest {

    @Test fun sessionResponseKeepsRefreshCredentialAndDeviceSessionBinding() {
        val session = HhyNetworkJson.value.decodeFromString<AuthSessionResource>(
            """{"accessToken":"access","refreshToken":"refresh","expiresAt":"2026-07-18T00:00:00Z","userId":"42","sessionId":"session-1","device":{"deviceId":"device-1"}}""",
        )

        assertEquals("refresh", session.refreshToken)
        assertEquals("device-1", session.device?.get("deviceId")?.jsonPrimitive?.content)
    }

    @Test fun errorEnvelopeDecodesStableCodeWithoutRelaxingUnknownFields() {
        val decoded = HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(
            """{"success":false,"requestId":"request_429","error":{"code":"COMMON-429-RATE_LIMITED","message":"请求过于频繁","retryable":true}}""",
        )

        assertFalse(decoded.success)
        assertEquals("COMMON-429-RATE_LIMITED", decoded.error.code)
        assertEquals(true, decoded.error.retryable)
    }

    @Test fun authRequestsKeepFrozenContractFieldNames() {
        val request = AuthPasswordLoginRequest(
            phone = "13800000000",
            password = "password1",
            challengeId = "challenge-1",
            challengeProof = "1234",
            device = AuthDevicePayload("fingerprint", "Pixel", "ANDROID", "15", "1.2.2-debug"),
        )

        val encoded = HhyNetworkJson.value.encodeToString(AuthPasswordLoginRequest.serializer(), request)
        val fields = HhyNetworkJson.value.parseToJsonElement(encoded).jsonObject
        assertTrue(fields.containsKey("challengeId"))
        assertTrue(fields.containsKey("challengeProof"))
        assertTrue(fields["device"]!!.jsonObject.containsKey("deviceFingerprint"))
        assertFalse(fields.containsKey("challenge_id"))
    }

    @Test fun securitySessionListDecodesWithoutCredentialFields() {
        val page = HhyNetworkJson.value.decodeFromString<UserSecuritySessionPageResource>(
            """{"items":[{"sessionId":"23","device":{"deviceId":"device-1","deviceName":"Pixel"},"createdAt":"2026-07-18T00:00:00Z","expiresAt":"2026-08-18T00:00:00Z","status":"ACTIVE","current":true}],"page":{"page":1,"pageSize":20,"total":1,"hasMore":false}}""",
        )

        val encoded = HhyNetworkJson.value.encodeToString(UserSecuritySessionPageResource.serializer(), page)
        assertEquals("23", page.items.single().sessionId)
        assertTrue(page.items.single().current)
        assertFalse(encoded.contains("accessToken"))
        assertFalse(encoded.contains("refreshToken"))
    }

    @Test fun passwordChangeRequestOmitsOptionalSmsCodeWhenAbsent() {
        val encoded = HhyNetworkJson.value.encodeToString(
            AuthPasswordChangeRequest("Current!234", "New!56789"),
        )
        val fields = HhyNetworkJson.value.parseToJsonElement(encoded).jsonObject

        assertTrue(fields.containsKey("currentPassword"))
        assertTrue(fields.containsKey("newPassword"))
        assertFalse(fields.containsKey("smsCode"))
    }

    @Test fun restrictedSelfAndAppealModelsKeepFrozenFieldNames() {
        val user = HhyNetworkJson.value.decodeFromString<UserSelfResource>(
            """{"id":"17","phoneMasked":"138****0000","status":"FROZEN","version":2}""",
        )
        val request = SupportTicketCreateRequest(
            category = "ACCOUNT_APPEAL", subject = "账号冻结申诉", content = "请复核账号状态",
        )
        val fields = HhyNetworkJson.value.parseToJsonElement(
            HhyNetworkJson.value.encodeToString(SupportTicketCreateRequest.serializer(), request),
        ).jsonObject

        assertEquals("FROZEN", user.status)
        assertEquals("138****0000", user.phoneMasked)
        assertTrue(fields.containsKey("category"))
        assertTrue(fields.containsKey("subject"))
        assertTrue(fields.containsKey("content"))
    }

    @Test fun cancellationRequestKeepsVersionAndNeverAddsPhone() {
        val encoded = HhyNetworkJson.value.encodeToString(
            AccountCancellationRequest("不再使用", "481516", 7L),
        )
        val fields = HhyNetworkJson.value.parseToJsonElement(encoded).jsonObject

        assertEquals(7L, fields.getValue("expectedVersion").jsonPrimitive.content.toLong())
        assertTrue(fields.containsKey("reason"))
        assertTrue(fields.containsKey("smsCode"))
        assertFalse(fields.containsKey("phone"))
    }
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
    fun latestAppDecodesFrozenPublicPageShape() {
        val envelope = json.decodeFromString<ApiEnvelope<PublicPage>>(
            """
            {
              "success": true,
              "requestId": "request_latest",
              "timestamp": "2026-07-17T00:00:00Z",
              "data": {
                "code": "app-latest",
                "title": "合伙云 Pro 1.2.3",
                "description": "安全更新",
                "download": {
                  "platform": "ANDROID",
                  "versionName": "1.2.3",
                  "versionCode": 10202,
                  "downloadUrl": "https://downloads.example.com/hhy.apk",
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                },
                "version": 10202
              }
            }
            """.trimIndent(),
        )

        assertEquals("app-latest", envelope.data.code)
        assertEquals(10202L, envelope.data.download?.versionCode)
        assertEquals(64, envelope.data.download?.sha256?.length)
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
