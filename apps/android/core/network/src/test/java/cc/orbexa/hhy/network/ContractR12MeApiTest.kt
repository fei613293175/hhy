package cc.orbexa.hhy.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractR12MeApiTest {
    @Test fun aggregateRoutesMatchTheThreeFrozenOperations() {
        assertEquals("/api/v1/me", R12MeResource.USER.route)
        assertEquals("/api/v1/me/membership", R12MeResource.MEMBERSHIP.route)
        assertEquals("/api/v1/me/reward-account", R12MeResource.REWARD_ACCOUNT.route)
    }

    @Test fun membershipKeepsOptionalFieldsAndStructuredBenefitValue() {
        val resource = HhyNetworkJson.value.decodeFromString<MembershipResource>(
            """{"status":"ACTIVE","benefits":[{"benefitCode":"PUBLISH_LIMIT","name":"发布额度","value":12}],"version":3}""",
        )

        assertNull(resource.id)
        assertEquals("ACTIVE", resource.status)
        assertEquals(JsonPrimitive(12), resource.benefits.single().value)
        assertNull(resource.remainingValueCent)
    }

    @Test fun rewardBalancesRemainInt64AndOptionalWithdrawalStaysAbsent() {
        val resource = HhyNetworkJson.value.decodeFromString<RewardAccountResource>(
            """{"userId":"user-1","pendingCent":1,"availableCent":429496729600,"frozenCent":2,"version":9}""",
        )

        assertEquals(429_496_729_600L, resource.availableCent)
        assertNull(resource.withdrawnCent)
    }

    @Test fun compactErrorsMapKnownFieldsAndKeepDiagnosticsOutsideBusinessState() {
        val error = HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(
            """{"success":false,"requestId":"request-1","error":{"code":"IDENTITY-422-NOT_VERIFIED","message":"请先完成实名认证","details":[{"field":"identityStatus","code":"NOT_VERIFIED","message":"未实名"},{"code":"GLOBAL","message":"稍后重试"}]}}""",
        )

        assertEquals(mapOf("identityStatus" to "未实名"), error.error.fieldErrors())
        assertTrue(error.error.traceId == null)
    }
}
