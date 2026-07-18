package cc.orbexa.hhy.auth

import org.junit.Assert.assertFalse
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
}
