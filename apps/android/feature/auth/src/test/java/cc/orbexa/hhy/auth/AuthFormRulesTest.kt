package cc.orbexa.hhy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFormRulesTest {
    @Test fun passwordLoginRequiresPhonePasswordAndChallenge() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "13800000000", "password1", "", "", "", emptyList(), false, "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.PASSWORD, "13800000000", "password1", "", "", "", emptyList(), false, "challenge", "proof"))
    }

    @Test fun registrationRequiresMatchingPasswordAndAgreementVersion() {
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "different", "1234", "INVITE", listOf("91"), true, "", ""))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "1234", "INVITE", emptyList(), true, "", ""))
        assertFalse(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "1234", "INVITE", listOf("91"), false, "", ""))
        assertTrue(AuthFormRules.canSubmit(AuthRoute.REGISTER, "13800000000", "password1", "password1", "1234", "INVITE", listOf("91"), true, "", ""))
    }

    @Test fun inviteMustBeValidatedAgainstTheCurrentValue() {
        assertFalse(AuthFormRules.hasValidatedInvite("INVITE-A", ""))
        assertFalse(AuthFormRules.hasValidatedInvite("INVITE-A", "INVITE-B"))
        assertTrue(AuthFormRules.hasValidatedInvite("INVITE-A", "INVITE-A"))
    }

    @Test fun inviteValidationResponseCanOnlyApplyToItsOriginalInputValue() {
        assertFalse(AuthFormRules.isInviteValidationForCurrentInput("", "INVITE-A"))
        assertFalse(AuthFormRules.isInviteValidationForCurrentInput("INVITE-A", "INVITE-B"))
        assertTrue(AuthFormRules.isInviteValidationForCurrentInput("INVITE-A", "INVITE-A"))
    }
}
