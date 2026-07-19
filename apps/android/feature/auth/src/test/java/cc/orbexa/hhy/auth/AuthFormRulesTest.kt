package cc.orbexa.hhy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormRulesTest {
    @Test fun passwordLoginRequiresPhoneAndPasswordBeforeOpeningChallenge() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "bad", "password1", "", "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "13800000000", "password1", "", "", ""))
    }

    @Test fun registrationRequiresOnlyPhoneMatchingPasswordsAndInviteBeforeChallenge() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "different", "", "INVITE"))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "", "INVITE"))
    }

    @Test fun onlyDedicatedChallengeErrorRefreshesTheChallenge() {
        assertTrue(AuthFormRules.challengeRejected("AUTH-422-SECURITY_CHALLENGE_INVALID"))
        assertFalse(AuthFormRules.challengeRejected("COMMON-422-BUSINESS_RULE"))
        assertFalse(AuthFormRules.challengeRejected("COMMON-409-VERSION_CONFLICT"))
    }

    @Test fun downstreamBusinessFailuresAreNotReportedAsChallengeFailures() {
        assertEquals(
            "手机号或密码不正确",
            AuthFormRules.challengeBusinessFailure(
                AuthRoute.PASSWORD, 401, "COMMON-401-UNAUTHENTICATED", null,
            ),
        )
        assertEquals(
            "注册信息未通过，请检查手机号、密码和邀请码",
            AuthFormRules.challengeBusinessFailure(
                AuthRoute.REGISTER, 422, "COMMON-422-BUSINESS_RULE", null,
            ),
        )
        assertEquals(
            "短信服务暂时不可用，请稍后再试",
            AuthFormRules.challengeBusinessFailure(AuthRoute.RESET, 500, "COMMON-500-INTERNAL", null),
        )
        assertEquals(
            "网络连接失败，请检查网络后重试",
            AuthFormRules.challengeBusinessFailure(AuthRoute.SMS, null, null, null),
        )
    }
}
