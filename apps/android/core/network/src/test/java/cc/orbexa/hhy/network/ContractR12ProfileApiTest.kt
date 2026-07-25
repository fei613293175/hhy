package cc.orbexa.hhy.network

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR12ProfileApiTest {
    @Test
    fun patchCallNormalizesAndOnlySerializesChangedFields() {
        val call = buildR12ProfilePatchCall(
            idempotencyKey = "r12-profile-1234567890abcdef",
            request = R12ProfilePatchRequest(
                nickname = "  合伙人  ",
                bio = "",
                expectedVersion = 12,
            ),
        )

        assertEquals("/api/v1/me/profile", call.route)
        assertEquals("合伙人", call.request.nickname)
        assertEquals("", call.request.bio)
        assertFalse(call.body.contains("avatarMediaId"))
        assertEquals(
            "{\"nickname\":\"合伙人\",\"bio\":\"\",\"expectedVersion\":12}",
            call.body,
        )
    }

    @Test
    fun profileTextLimitCountsUnicodeCodePoints() {
        val supplementary = "\uD83D\uDE80"
        assertEquals(255, r12ProfileCodePointCount(supplementary.repeat(255)))
        assertTrue(
            runCatching {
                buildR12ProfilePatchCall(
                    "r12-profile-1234567890abcdef",
                    R12ProfilePatchRequest(
                        nickname = supplementary.repeat(256),
                        expectedVersion = 3,
                    ),
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
        buildR12ProfilePatchCall(
            "r12-profile-1234567890abcdef",
            R12ProfilePatchRequest(nickname = supplementary.repeat(255), expectedVersion = 3),
        )
    }

    @Test
    fun emptyAvatarClearsButInvalidMediaIdIsRejected() {
        val clear = buildR12ProfilePatchCall(
            "r12-profile-1234567890abcdef",
            R12ProfilePatchRequest(avatarMediaId = "", expectedVersion = 2),
        )
        assertTrue(clear.body.contains("\"avatarMediaId\":\"\""))

        assertTrue(
            runCatching {
                buildR12ProfilePatchCall(
                    "r12-profile-1234567890abcdef",
                    R12ProfilePatchRequest(avatarMediaId = "media-1", expectedVersion = 2),
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun responseUnionDecodesUserAndCommand() {
        val user = decodeR12ProfileData(buildJsonObject {
            put("id", "user-1")
            put("nickname", "合伙人")
            put("status", "ACTIVE")
            put("version", 13)
        })
        val command = decodeR12ProfileData(buildJsonObject {
            put("resourceId", "user-1")
            put("status", "ACCEPTED")
            put("version", 13)
            put("acceptedAt", "2026-07-25T14:00:00Z")
        })

        assertTrue(user is R12ProfilePatchResult.User)
        assertEquals("合伙人", (user as R12ProfilePatchResult.User).resource.nickname)
        assertTrue(command is R12ProfilePatchResult.Command)
        assertEquals("ACCEPTED", (command as R12ProfilePatchResult.Command).command.status)
    }

    @Test
    fun patchRequiresARealFieldAndStableKey() {
        assertTrue(
            runCatching {
                buildR12ProfilePatchCall(
                    "r12-profile-1234567890abcdef",
                    R12ProfilePatchRequest(expectedVersion = 1),
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                buildR12ProfilePatchCall(
                    "short",
                    R12ProfilePatchRequest(bio = "简介", expectedVersion = 1),
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }
}
