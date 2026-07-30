package cc.orbexa.hhy.shell

import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R12MeHomeStateTest {
    @Test fun independentFailureKeepsAnotherModulesSuccessfulValue() {
        val user = R12MeModuleState<String>().loaded("真实用户", null, "2026-07-25T15:00:00Z")
        val membership = R12MeModuleState<String>().failed(
            R12MeModuleKind.MEMBERSHIP,
            R07CallResult.Failure(statusCode = 404),
        )

        assertEquals("真实用户", user.value)
        assertTrue(user.hasReliableContent)
        assertEquals(R12MeModuleStatus.EMPTY, membership.status)
        assertFalse(membership.hasVisibleContent)
    }

    @Test fun staleCacheIsRetainedAcrossOfflineAndServerFailure() {
        val cached = R12MeModuleState.cached("旧值")
        val offline = cached.failed(R12MeModuleKind.USER, R07CallResult.Failure(statusCode = null))
        val server = cached.failed(R12MeModuleKind.USER, R07CallResult.Failure(statusCode = 503))

        assertEquals("旧值", offline.value)
        assertEquals(R12MeModuleStatus.STALE, offline.status)
        assertEquals(R12MeModuleStatus.STALE, server.status)
    }

    @Test fun rewardIdentityAndRiskStatesRemainLocal() {
        val identity = R12MeModuleState<String>().failed(
            R12MeModuleKind.REWARD,
            R07CallResult.Failure(422, errorCode = "IDENTITY-422-NOT_VERIFIED"),
        )
        val risk = R12MeModuleState<String>().failed(
            R12MeModuleKind.REWARD,
            R07CallResult.Failure(423, errorCode = "REWARD-423-RISK_FROZEN"),
        )

        assertEquals(R12MeModuleStatus.IDENTITY_REQUIRED, identity.status)
        assertEquals(R12MeModuleStatus.RISK_FROZEN, risk.status)
    }

    @Test fun rateLimitRetainsValueAndRetryWindow() {
        val limited = R12MeModuleState.cached("余额").failed(
            R12MeModuleKind.REWARD,
            R07CallResult.Failure(429, retryAfterSeconds = 18),
        )

        assertEquals("余额", limited.value)
        assertEquals(R12MeModuleStatus.RATE_LIMITED, limited.status)
        assertEquals(18L, limited.retryAfterSeconds)
    }

    @Test fun responseTimestampWinsAndReceiveTimeIsTheExplicitFallback() {
        val server = R12MeModuleState<String>().loaded(
            "数据", "2026-07-25T15:01:00Z", "2026-07-25T15:02:00Z",
        )
        val fallback = R12MeModuleState<String>().loaded(
            "数据", null, "2026-07-25T15:02:00Z",
        )

        assertEquals("2026-07-25T15:01:00Z", server.updatedAt)
        assertEquals("2026-07-25T15:02:00Z", fallback.updatedAt)
    }

    @Test fun integerCentFormattingNeverUsesFloatingPoint() {
        assertEquals("0.00", formatR12Cent(0))
        assertEquals("0.01", formatR12Cent(1))
        assertEquals("1,268.88", formatR12Cent(126_888))
        assertEquals("4,294,967,296.00", formatR12Cent(429_496_729_600L))
    }

    @Test fun businessTimeNeverLeaksRawIsoText() {
        assertEquals("2026-07-26 06:00", "2026-07-25T22:00:00Z".r12MeBusinessTimeLabel())
        assertEquals("时间待同步", "not-a-time".r12MeBusinessTimeLabel())
    }
}
