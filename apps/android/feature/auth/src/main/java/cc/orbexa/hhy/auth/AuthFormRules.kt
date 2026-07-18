package cc.orbexa.hhy.auth

internal object AuthFormRules {
    fun validPhone(value: String) = Regex("^1[3-9]\\d{9}$").matches(value)
    fun validPassword(value: String) = value.length in 8..72
    fun validSms(value: String) = value.length in 4..10
    fun hasValidatedInvite(currentInvite: String, validatedInvite: String) =
        currentInvite.isNotBlank() && currentInvite == validatedInvite

    fun isInviteValidationForCurrentInput(requestedInvite: String, currentInvite: String) =
        requestedInvite.isNotBlank() && requestedInvite == currentInvite

    fun canSubmit(
        route: AuthRoute,
        phone: String,
        password: String,
        passwordAgain: String,
        smsCode: String,
        invite: String,
        agreementVersionIds: List<String>,
        agreementsAccepted: Boolean,
        challengeId: String,
        proof: String,
    ): Boolean = when (route) {
        AuthRoute.PASSWORD -> validPhone(phone) && validPassword(password) && challengeId.isNotBlank() && proof.isNotBlank()
        AuthRoute.SMS -> validPhone(phone) && validSms(smsCode)
        AuthRoute.REGISTER -> validPhone(phone) && validPassword(password) && password == passwordAgain && validSms(smsCode) && invite.isNotBlank() && agreementVersionIds.isNotEmpty() && agreementsAccepted
        AuthRoute.RESET -> validPhone(phone) && validPassword(password) && validSms(smsCode)
    }
}
