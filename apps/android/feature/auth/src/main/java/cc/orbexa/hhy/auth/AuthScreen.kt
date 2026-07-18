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
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
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
fun AuthScreen(apiBaseUrl: String, onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val api = remember(apiBaseUrl, context.applicationContext) {
        UrlConnectionContractAuthApi(apiBaseUrl, context.applicationContext)
    }
    AuthScreen(api, onAuthenticated)
}

@Composable
internal fun AuthScreen(api: ContractAuthApi, onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf(AuthRoute.PASSWORD) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var agreementVersions by remember { mutableStateOf("") }
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
                    text = result.statusCode?.let(::errorForStatus) ?: "网络不可用，请检查连接后重试",
                    requestId = result.requestId,
                )
            }
        }
    }

    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Text("合伙云 Pro", style = MaterialTheme.typography.headlineSmall)
            Text(route.title, style = MaterialTheme.typography.titleLarge)
            RouteSelector(route) { route = it; state = AuthUiState.Editing }

            if (state is AuthUiState.Message) {
                val message = state as AuthUiState.Message
                Text(message.text, color = HhyColors.Warning)
                message.requestId?.let { Text("请求编号：$it", color = HhyColors.TextSecondary) }
            }
            if (submitting) CircularProgressIndicator()

            PhoneField(phone) { phone = it; state = AuthUiState.Editing }
            if (route == AuthRoute.PASSWORD || route == AuthRoute.REGISTER || route == AuthRoute.RESET) {
                SecretField(if (route == AuthRoute.RESET) "新密码" else "密码", password) { password = it; state = AuthUiState.Editing }
            }
            if (route == AuthRoute.REGISTER) {
                SecretField("确认密码", passwordAgain) { passwordAgain = it; state = AuthUiState.Editing }
                TextField("邀请码", inviteCode, { inviteCode = it; state = AuthUiState.Editing }, false)
                TextField("协议版本（逗号分隔）", agreementVersions, { agreementVersions = it; state = AuthUiState.Editing }, false)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting,
                    onClick = { launchCall({ api.validateInviteCode(inviteCode) }) },
                ) { Text("校验邀请码") }
            }
            if (route != AuthRoute.PASSWORD) {
                SecretField("短信验证码", smsCode) { smsCode = it; state = AuthUiState.Editing }
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
                    SecretField("安全验证结果", challengeProof) { challengeProof = it; state = AuthUiState.Editing }
                }
            }
            if (route != AuthRoute.PASSWORD) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(), enabled = !submitting && AuthFormRules.validPhone(phone) && challengeId.isNotBlank() && challengeProof.isNotBlank(),
                    onClick = { launchCall({ api.sendSms(phone, route.scene, challengeId, challengeProof) }) },
                ) { Text("发送验证码") }
            }

            Button(
                modifier = Modifier.fillMaxWidth(), enabled = !submitting && AuthFormRules.canSubmit(route, phone, password, passwordAgain, smsCode, inviteCode, agreementVersions, challengeId, challengeProof),
                onClick = {
                    when (route) {
                        AuthRoute.PASSWORD -> launchCall({ api.passwordLogin(phone, password, challengeId, challengeProof) }) {
                            password = ""; challengeProof = ""; onAuthenticated()
                        }
                        AuthRoute.SMS -> launchCall({ api.smsLogin(phone, smsCode) }) {
                            smsCode = ""; onAuthenticated()
                        }
                        AuthRoute.REGISTER -> launchCall({
                            api.register(phone, smsCode, password, inviteCode, agreementVersions.split(',').map(String::trim).filter(String::isNotEmpty))
                        }) { password = ""; passwordAgain = ""; smsCode = ""; challengeProof = ""; onAuthenticated() }
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

@Composable private fun RouteSelector(selected: AuthRoute, onSelect: (AuthRoute) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
    AuthRoute.entries.forEach { route ->
        OutlinedButton(onClick = { onSelect(route) }, enabled = route != selected) { Text(route.title) }
    }
}

@Composable private fun PhoneField(value: String, onValueChange: (String) -> Unit) = TextField("手机号", value, onValueChange, false)
@Composable private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit) = TextField(label, value, onValueChange, true)
@Composable private fun TextField(label: String, value: String, onValueChange: (String) -> Unit, secret: Boolean) = OutlinedTextField(
    value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
    singleLine = true, visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
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
private fun errorForStatus(status: Int) = when (status) {
    400, 422 -> "输入或安全验证未通过，请检查后重试"
    401 -> "认证已失效，请重新开始"
    403 -> "当前操作不可用"
    404 -> "服务暂不可用，请稍后重试"
    409 -> "数据已变化，请重新获取安全验证后重试"
    429 -> "操作过于频繁，请稍后重试"
    else -> "服务暂时不可用，请稍后重试"
}
