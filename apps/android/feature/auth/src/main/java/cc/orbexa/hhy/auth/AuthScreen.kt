package cc.orbexa.hhy.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.registrationConfigOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

internal enum class AuthRoute(val title: String, val scene: String) {
    PASSWORD("密码登录", "LOGIN"),
    SMS("短信验证码登录", "LOGIN"),
    REGISTER("注册账号", "REGISTER"),
    RESET("忘记密码", "RESET_PASSWORD"),
}

private sealed interface AuthUiState {
    data object Editing : AuthUiState
    data object Submitting : AuthUiState
    data class Message(val text: String, val requestId: String? = null) : AuthUiState
}

/** R02 authentication routes; all requests are live frozen-contract operations, never mocks. */
@Composable
fun AuthScreen(apiBaseUrl: String, onAuthenticated: (AuthSessionResource) -> Boolean) {
    val context = LocalContext.current
    val api = remember(apiBaseUrl, context.applicationContext) {
        UrlConnectionContractAuthApi(apiBaseUrl, context.applicationContext)
    }
    AuthScreen(api, onAuthenticated)
}

@Composable
internal fun AuthScreen(api: ContractAuthApi, onAuthenticated: (AuthSessionResource) -> Boolean) {
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf(AuthRoute.PASSWORD) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var validatedInviteCode by remember { mutableStateOf("") }
    var agreementVersionIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var agreementCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var agreementsAccepted by remember { mutableStateOf(false) }
    var challengeId by remember { mutableStateOf("") }
    var challengeImageBase64 by remember { mutableStateOf("") }
    var challengeProof by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }

    val submitting = state is AuthUiState.Submitting
    fun launchCall(call: suspend () -> AuthCallResult, success: (AuthCallResult.Success) -> Unit = {}) {
        if (submitting) return
        scope.launch {
            state = AuthUiState.Submitting
            when (val result = call()) {
                is AuthCallResult.Success -> {
                    success(result)
                    if (state is AuthUiState.Submitting) state = AuthUiState.Message("操作成功", result.requestId)
                }
                is AuthCallResult.Failure -> state = AuthUiState.Message(
                    text = result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                        ?: "网络不可用，请检查连接后重试",
                    requestId = result.requestId,
                )
            }
        }
    }
    fun completeAuthentication(result: AuthCallResult.Success) {
        val session = result.sessionOrNull()
        if (session != null && onAuthenticated(session)) {
            state = AuthUiState.Message("登录成功", result.requestId)
        } else {
            state = AuthUiState.Message("无法安全保存登录会话，请重新登录", result.requestId)
        }
    }

    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Text("合伙云 Pro", style = MaterialTheme.typography.headlineSmall)
            Text(route.title, style = MaterialTheme.typography.titleLarge)
            RouteSelector(route, enabled = !submitting) {
                route = it
                smsCode = ""
                challengeId = ""
                challengeImageBase64 = ""
                challengeProof = ""
                agreementVersionIds = emptyList()
                agreementCodes = emptyList()
                agreementsAccepted = false
                state = AuthUiState.Editing
            }

            if (state is AuthUiState.Message) {
                val message = state as AuthUiState.Message
                Text(message.text, color = HhyColors.Warning)
                message.requestId?.let { Text("请求编号：$it", color = HhyColors.TextSecondary) }
            }
            if (submitting) CircularProgressIndicator()

            PhoneField(phone, enabled = !submitting) { phone = it; smsCode = ""; state = AuthUiState.Editing }
            if (route == AuthRoute.PASSWORD || route == AuthRoute.REGISTER || route == AuthRoute.RESET) {
                SecretField(if (route == AuthRoute.RESET) "新密码" else "密码", password, enabled = !submitting) { password = it; state = AuthUiState.Editing }
            }
            if (route == AuthRoute.REGISTER) {
                SecretField("确认密码", passwordAgain, enabled = !submitting) { passwordAgain = it; state = AuthUiState.Editing }
                TextField("邀请码", inviteCode, {
                    inviteCode = it
                    validatedInviteCode = ""
                    state = AuthUiState.Editing
                }, false, enabled = !submitting)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting,
                    onClick = {
                        launchCall({ api.registrationConfig() }) { result ->
                            val config = result.registrationConfigOrNull()
                            if (config == null || config.agreementVersions.isEmpty()) {
                                agreementVersionIds = emptyList()
                                agreementCodes = emptyList()
                                agreementsAccepted = false
                                state = AuthUiState.Message("当前没有可用于注册的协议，请稍后重试", result.requestId)
                            } else {
                                agreementVersionIds = config.agreementVersions.map { it.versionId }
                                agreementCodes = config.agreementVersions.map { it.code }
                                agreementsAccepted = false
                                state = AuthUiState.Message("已加载当前注册协议", result.requestId)
                            }
                        }
                    },
                ) { Text(if (agreementVersionIds.isEmpty()) "加载当前注册协议" else "已加载 ${agreementVersionIds.size} 项注册协议") }
                if (agreementVersionIds.isNotEmpty()) {
                    Row {
                        Checkbox(checked = agreementsAccepted, enabled = !submitting, onCheckedChange = {
                            agreementsAccepted = it
                            state = AuthUiState.Editing
                        })
                        Text("我已阅读并同意当前协议（${agreementCodes.joinToString("、")}）")
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting && inviteCode.isNotBlank(),
                    onClick = {
                        val inviteCodeSnapshot = inviteCode
                        launchCall({ api.validateInviteCode(inviteCodeSnapshot) }) { result ->
                            if (AuthFormRules.isInviteValidationForCurrentInput(inviteCodeSnapshot, inviteCode)) {
                                validatedInviteCode = inviteCodeSnapshot
                                state = AuthUiState.Message("邀请码校验通过", result.requestId)
                            }
                        }
                    },
                ) { Text("校验邀请码") }
            }
            if (route != AuthRoute.PASSWORD) {
                SecretField("短信验证码", smsCode, enabled = !submitting) { smsCode = it; state = AuthUiState.Editing }
            }

            if (route == AuthRoute.PASSWORD || route == AuthRoute.SMS || route == AuthRoute.REGISTER || route == AuthRoute.RESET) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting,
                    onClick = {
                        launchCall({ api.securityChallenge(route.scene) }) { result ->
                            challengeId = result.data["challengeId"]?.jsonPrimitive?.content.orEmpty()
                            challengeImageBase64 = result.data["imageBase64"]?.jsonPrimitive?.content.orEmpty()
                            state = AuthUiState.Message("安全验证已创建，请完成验证", result.requestId)
                        }
                    },
                ) { Text("创建安全验证") }
                if (challengeId.isNotBlank()) {
                    ChallengeImage(challengeImageBase64)
                    SecretField("安全验证结果", challengeProof, enabled = !submitting) { challengeProof = it; state = AuthUiState.Editing }
                }
            }
            if (route != AuthRoute.PASSWORD) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting && AuthFormRules.validPhone(phone) && challengeId.isNotBlank() && challengeProof.isNotBlank(),
                    onClick = { launchCall({ api.sendSms(phone, route.scene, challengeId, challengeProof) }) },
                ) { Text("发送验证码") }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && AuthFormRules.canSubmit(
                    route, phone, password, passwordAgain, smsCode, inviteCode, agreementVersionIds, agreementsAccepted, challengeId, challengeProof,
                ) && (route != AuthRoute.REGISTER || AuthFormRules.hasValidatedInvite(inviteCode, validatedInviteCode)),
                onClick = {
                    when (route) {
                        AuthRoute.PASSWORD -> launchCall({ api.passwordLogin(phone, password, challengeId, challengeProof) }) {
                            password = ""; challengeProof = ""; completeAuthentication(it)
                        }
                        AuthRoute.SMS -> launchCall({ api.smsLogin(phone, smsCode) }) {
                            smsCode = ""; completeAuthentication(it)
                        }
                        AuthRoute.REGISTER -> launchCall({
                            api.register(phone, smsCode, password, inviteCode, agreementVersionIds)
                        }) {
                            password = ""; passwordAgain = ""; smsCode = ""; challengeProof = ""; validatedInviteCode = ""
                            agreementVersionIds = emptyList(); agreementCodes = emptyList(); agreementsAccepted = false
                            completeAuthentication(it)
                        }
                        AuthRoute.RESET -> launchCall({ api.resetPassword(phone, smsCode, password) }) {
                            password = ""; passwordAgain = ""; smsCode = ""; challengeProof = ""
                        }
                    }
                },
            ) { Text(primaryAction(route)) }
            Text("敏感信息仅用于本次认证，不会展示或写入日志。", color = HhyColors.TextSecondary)
            Spacer(Modifier.height(HhySpacing.Lg))
        }
    }
}

@Composable private fun RouteSelector(selected: AuthRoute, enabled: Boolean, onSelect: (AuthRoute) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
    AuthRoute.entries.forEach { route ->
        OutlinedButton(onClick = { onSelect(route) }, enabled = enabled && route != selected) { Text(route.title) }
    }
}

@Composable private fun PhoneField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) = TextField("手机号", value, onValueChange, false, enabled)
@Composable private fun SecretField(label: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) = TextField(label, value, onValueChange, true, enabled)
@Composable private fun TextField(label: String, value: String, onValueChange: (String) -> Unit, secret: Boolean, enabled: Boolean) = OutlinedTextField(
    value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
    singleLine = true, enabled = enabled, visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
)

@Composable
private fun ChallengeImage(encoded: String) {
    val bitmap = remember(encoded) {
        encoded.takeIf(String::isNotBlank)?.let { value ->
            runCatching {
                val bytes = Base64.decode(value, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
                .getOrNull()
        }
    }
    if (bitmap == null) {
        Text("安全验证已创建，请输入图中字符", color = HhyColors.TextSecondary)
    } else {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "安全验证图")
    }
}

private fun primaryAction(route: AuthRoute) = when (route) {
    AuthRoute.PASSWORD, AuthRoute.SMS -> "登录"
    AuthRoute.REGISTER -> "注册"
    AuthRoute.RESET -> "重置密码"
}
private fun errorForStatus(status: Int, errorCode: String?, retryAfterSeconds: Long?) = when {
    errorCode == "AUTH-423-ACCOUNT_RESTRICTED" -> "账号当前受限，请联系平台客服处理"
    status == 429 && retryAfterSeconds != null -> "操作过于频繁，请在 $retryAfterSeconds 秒后重试"
    else -> when (status) {
    400, 422 -> "输入或安全验证未通过，请检查后重试"
    401 -> "认证已失效，请重新开始"
    403 -> "当前操作不可用"
    404 -> "服务暂不可用，请稍后重试"
    409 -> "数据已变化，请重新获取安全验证后重试"
    429 -> "操作过于频繁，请稍后重试"
    else -> "服务暂时不可用，请稍后重试"
    }
}
