package cc.orbexa.hhy.auth

internal object AuthFormRules {
    const val SECURITY_CHALLENGE_INVALID = "AUTH-422-SECURITY_CHALLENGE_INVALID"

    fun validPhone(value: String) = Regex("^1[3-9]\\d{9}$").matches(value)
    fun validPassword(value: String) = value.length in 8..72
    fun validSms(value: String) = value.length in 4..10
    fun canSubmit(
        route: AuthRoute,
        phone: String,
        password: String,
        passwordAgain: String,
        smsCode: String,
        invite: String,
    ): Boolean = when (route) {
        AuthRoute.PASSWORD -> validPhone(phone) && validPassword(password)
        AuthRoute.SMS -> validPhone(phone) && validSms(smsCode)
        AuthRoute.REGISTER -> validPhone(phone) && validPassword(password) && password == passwordAgain && invite.isNotBlank()
        AuthRoute.RESET -> validPhone(phone) && validPassword(password) && validSms(smsCode)
    }

    fun challengeRejected(errorCode: String?): Boolean = errorCode == SECURITY_CHALLENGE_INVALID

    fun challengeBusinessFailure(
        route: AuthRoute,
        statusCode: Int?,
        errorCode: String?,
        retryAfterSeconds: Long?,
    ): String = when {
        statusCode == null -> "网络连接失败，请检查网络后重试"
        statusCode == 429 && retryAfterSeconds != null -> "操作过于频繁，请在 $retryAfterSeconds 秒后重试"
        statusCode == 429 -> "操作过于频繁，请稍后重试"
        errorCode == "AUTH-423-ACCOUNT_RESTRICTED" -> "账号当前受限，请联系平台客服处理"
        route == AuthRoute.PASSWORD && statusCode == 401 -> "手机号或密码不正确"
        route == AuthRoute.REGISTER && statusCode in setOf(400, 409, 422) ->
            "注册信息未通过，请检查手机号、密码和邀请码"
        route in setOf(AuthRoute.SMS, AuthRoute.RESET) && statusCode >= 500 ->
            "短信服务暂时不可用，请稍后再试"
        route == AuthRoute.PASSWORD && statusCode >= 500 -> "登录服务暂时不可用，请稍后再试"
        route == AuthRoute.REGISTER && statusCode >= 500 -> "注册服务暂时不可用，请稍后再试"
        statusCode == 409 -> "请求状态已变化，请重新操作"
        statusCode in setOf(400, 422) -> "输入信息未通过，请检查后重试"
        else -> "服务暂时不可用，请稍后重试"
    }
}
