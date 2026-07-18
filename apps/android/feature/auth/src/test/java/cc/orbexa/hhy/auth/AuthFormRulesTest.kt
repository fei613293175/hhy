package cc.orbexa.hhy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormRulesTest {
    @Test fun passwordLoginRequiresPhonePasswordAndChallenge() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "13800000000", "password1", "", "", "", "", "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "13800000000", "password1", "", "", "", "", "challenge", "proof"))
    }

    @Test fun registrationRequiresMatchingPasswordAndAgreementVersion() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "different", "1234", "INVITE", "v1", "", ""))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "1234", "INVITE", "", "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "1234", "INVITE", "v1", "", ""))
    }
}

