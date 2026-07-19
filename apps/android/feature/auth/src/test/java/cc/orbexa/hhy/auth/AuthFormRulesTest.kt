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
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "12345678", "12345678", "", "INVITE"))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password", "password", "", "INVITE"))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "Password1234567890123", "Password1234567890123", "", "INVITE"))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "", "INVITE"))
    }

    @Test fun loginStillAcceptsExistingLongPasswordsWhileNewPasswordsUseFrozenPolicy() {
        assertTrue(AuthFormRules.validLoginPassword("Password12345678901234567890"))
        assertFalse(AuthFormRules.validNewPassword("Password12345678901234567890"))
        assertTrue(AuthFormRules.validNewPassword("Password9"))
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
            "该手机号已注册，请直接登录或找回密码",
            AuthFormRules.challengeBusinessFailure(
                AuthRoute.REGISTER, 409, "AUTH-409-PHONE_ALREADY_REGISTERED", null,
            ),
        )
        assertEquals(
            "邀请码无效或已失效，请检查后重试",
            AuthFormRules.challengeBusinessFailure(
                AuthRoute.REGISTER, 422, "AUTH-422-INVITE_CODE_INVALID", null,
            ),
        )
        assertEquals(
            "密码需为8–20位，并同时包含字母和数字",
            AuthFormRules.challengeBusinessFailure(
                AuthRoute.REGISTER, 422, "AUTH-422-PASSWORD_POLICY_INVALID", null,
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
