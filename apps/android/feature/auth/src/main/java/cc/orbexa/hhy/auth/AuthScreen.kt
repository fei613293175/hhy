package cc.orbexa.hhy.auth

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.designsystem.HhyOpacity
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.UserSecuritySessionResource
import cc.orbexa.hhy.network.UserSelfResource
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.securitySessionsOrNull
import cc.orbexa.hhy.network.supportTicketOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant

internal enum class AuthRoute(val title: String, val scene: String) {
    PASSWORD("密码登录", "LOGIN"),
    SMS("短信验证码登录", "LOGIN"),
    REGISTER("注册账号", "REGISTER"),
    RESET("忘记密码", "RESET_PASSWORD"),
}

@Serializable
private sealed interface AuthDestination {
    @Serializable data object Login : AuthDestination
    @Serializable data object Register : AuthDestination
    @Serializable data object Reset : AuthDestination
}

private sealed interface AuthUiState {
    data object Editing : AuthUiState
    data object Submitting : AuthUiState
    data class Message(val text: String, val requestId: String? = null) : AuthUiState
}

private enum class PendingAuthIntent(val scene: String) {
    PASSWORD_LOGIN("LOGIN"),
    SMS_CODE("LOGIN"),
    REGISTER("REGISTER"),
    RESET_CODE("RESET_PASSWORD"),
}

private enum class ChallengePhase { REQUESTING, READY, SUBMITTING, NETWORK_ERROR, RATE_LIMITED, SUCCESS }

private data class ChallengeDialogState(
    val intent: PendingAuthIntent,
    val phase: ChallengePhase,
    val challengeId: String = "",
    val imageBase64: String = "",
    val proof: String = "",
    val message: String? = null,
    val expiresAtElapsedRealtime: Long = 0L,
    val retryAtElapsedRealtime: Long = 0L,
)

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
    val navController = rememberNavController()
    val smsFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    var loginRoute by rememberSaveable { mutableStateOf(AuthRoute.PASSWORD) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }
    var challengeDialog by remember { mutableStateOf<ChallengeDialogState?>(null) }
    var smsRetryAtElapsedRealtime by remember { mutableStateOf(0L) }
    var smsClockNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    val smsRetrySeconds = ((smsRetryAtElapsedRealtime - smsClockNow + 999L) / 1_000L).coerceAtLeast(0L)
    LaunchedEffect(smsRetryAtElapsedRealtime) {
        while (smsRetryAtElapsedRealtime > 0L && SystemClock.elapsedRealtime() < smsRetryAtElapsedRealtime) {
            delay(1_000L)
            smsClockNow = SystemClock.elapsedRealtime()
        }
    }

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
    fun requestChallenge(intent: PendingAuthIntent, message: String? = null) {
        if (challengeDialog?.phase in setOf(ChallengePhase.REQUESTING, ChallengePhase.SUBMITTING)) return
        challengeDialog = ChallengeDialogState(intent, ChallengePhase.REQUESTING, message = message)
        scope.launch {
            when (val result = api.securityChallenge(intent.scene)) {
                is AuthCallResult.Success -> {
                    val challengeId = result.data["challengeId"]?.jsonPrimitive?.content.orEmpty()
                    val imageBase64 = result.data["imageBase64"]?.jsonPrimitive?.content.orEmpty()
                    val expiresAt = result.data["expiresAt"]?.jsonPrimitive?.content.orEmpty()
                    val lifetimeMillis = runCatching {
                        Duration.between(Instant.now(), Instant.parse(expiresAt)).toMillis()
                    }.getOrDefault(120_000L).coerceAtLeast(1L)
                    challengeDialog = if (challengeId.isBlank() || !challengeImageDecodes(imageBase64)) {
                        ChallengeDialogState(intent, ChallengePhase.NETWORK_ERROR, message = "安全验证暂时无法加载，请重试")
                    } else {
                        ChallengeDialogState(
                            intent = intent,
                            phase = ChallengePhase.READY,
                            challengeId = challengeId,
                            imageBase64 = imageBase64,
                            message = message,
                            expiresAtElapsedRealtime = SystemClock.elapsedRealtime() + lifetimeMillis,
                        )
                    }
                }
                is AuthCallResult.Failure -> challengeDialog = ChallengeDialogState(
                    intent,
                    if (result.statusCode == 429) ChallengePhase.RATE_LIMITED else ChallengePhase.NETWORK_ERROR,
                    message = if (result.statusCode == 429) "操作过于频繁，请稍后再试" else "安全验证暂时无法加载，请重试",
                    retryAtElapsedRealtime = result.retryAfterSeconds
                        ?.coerceAtLeast(1L)
                        ?.let { SystemClock.elapsedRealtime() + it * 1_000L }
                        ?: 0L,
                )
            }
        }
    }
    fun submitChallenge() {
        val dialog = challengeDialog ?: return
        if (dialog.phase != ChallengePhase.READY || dialog.proof.isBlank()) return
        challengeDialog = dialog.copy(phase = ChallengePhase.SUBMITTING, message = null)
        scope.launch {
            val result = when (dialog.intent) {
                PendingAuthIntent.PASSWORD_LOGIN -> api.passwordLogin(phone, password, dialog.challengeId, dialog.proof)
                PendingAuthIntent.SMS_CODE -> api.sendSms(phone, "LOGIN", dialog.challengeId, dialog.proof)
                PendingAuthIntent.REGISTER -> api.register(phone, password, inviteCode, dialog.challengeId, dialog.proof)
                PendingAuthIntent.RESET_CODE -> api.sendSms(phone, "RESET_PASSWORD", dialog.challengeId, dialog.proof)
            }
            when (result) {
                is AuthCallResult.Success -> {
                    challengeDialog = dialog.copy(phase = ChallengePhase.SUCCESS, message = "验证通过")
                    delay(360)
                    challengeDialog = null
                    when (dialog.intent) {
                        PendingAuthIntent.PASSWORD_LOGIN -> {
                            password = "";
                            completeAuthentication(result)
                        }
                        PendingAuthIntent.SMS_CODE, PendingAuthIntent.RESET_CODE -> {
                            smsCode = ""
                            val retryAfterSeconds = result.data["retryAfterSeconds"]
                                ?.jsonPrimitive?.content?.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
                            smsRetryAtElapsedRealtime = SystemClock.elapsedRealtime() + retryAfterSeconds * 1_000L
                            smsClockNow = SystemClock.elapsedRealtime()
                            state = AuthUiState.Message("验证码已发送，请注意查收")
                            delay(120L)
                            smsFocusRequester.requestFocus()
                            softwareKeyboardController?.show()
                        }
                        PendingAuthIntent.REGISTER -> {
                            password = "";
                            passwordAgain = ""
                            inviteCode = ""
                            completeAuthentication(result)
                        }
                    }
                }
                is AuthCallResult.Failure -> {
                    when {
                        result.statusCode == 429 -> challengeDialog = dialog.copy(
                            phase = ChallengePhase.RATE_LIMITED,
                            proof = "",
                            message = "操作过于频繁，请稍后再试",
                            retryAtElapsedRealtime = result.retryAfterSeconds
                                ?.coerceAtLeast(1L)
                                ?.let { SystemClock.elapsedRealtime() + it * 1_000L }
                                ?: 0L,
                        )
                        result.statusCode == null -> challengeDialog = dialog.copy(
                            phase = ChallengePhase.NETWORK_ERROR,
                            message = "网络连接失败，请检查网络后重试",
                        )
                        AuthFormRules.challengeRejected(result.errorCode) -> {
                            challengeDialog = dialog.copy(phase = ChallengePhase.READY, proof = "")
                            requestChallenge(dialog.intent, "输入不正确，请根据新图片重新输入")
                        }
                        else -> {
                            challengeDialog = null
                            val route = when (dialog.intent) {
                                PendingAuthIntent.PASSWORD_LOGIN -> AuthRoute.PASSWORD
                                PendingAuthIntent.SMS_CODE -> AuthRoute.SMS
                                PendingAuthIntent.REGISTER -> AuthRoute.REGISTER
                                PendingAuthIntent.RESET_CODE -> AuthRoute.RESET
                            }
                            state = AuthUiState.Message(
                                AuthFormRules.challengeBusinessFailure(
                                    route,
                                    result.statusCode,
                                    result.errorCode,
                                    result.retryAfterSeconds,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    fun startRegistration() {
        if (submitting || challengeDialog != null) return
        scope.launch {
            state = AuthUiState.Submitting
            when (val result = api.validateInviteCode(inviteCode)) {
                is AuthCallResult.Success -> {
                    state = AuthUiState.Editing
                    requestChallenge(PendingAuthIntent.REGISTER)
                }
                is AuthCallResult.Failure -> state = AuthUiState.Message(
                    if (result.statusCode in setOf(400, 404, 422)) {
                        "邀请码无效或已失效，请检查后重试"
                    } else {
                        result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                            ?: "网络不可用，请检查连接后重试"
                    },
                )
            }
        }
    }
    fun clearRouteState() {
        phone = ""
        password = ""
        smsCode = ""
        passwordAgain = ""
        inviteCode = ""
        challengeDialog = null
        state = AuthUiState.Editing
    }
    fun selectLoginRoute(next: AuthRoute) {
        if (submitting || next !in setOf(AuthRoute.PASSWORD, AuthRoute.SMS) || loginRoute == next) return
        loginRoute = next
        clearRouteState()
    }
    fun openRoute(next: AuthRoute) {
        if (submitting || challengeDialog != null) return
        clearRouteState()
        when (next) {
            AuthRoute.REGISTER -> navController.navigate(AuthDestination.Register)
            AuthRoute.RESET -> navController.navigate(AuthDestination.Reset)
            AuthRoute.PASSWORD, AuthRoute.SMS -> selectLoginRoute(next)
        }
    }
    fun popToLogin() {
        if (submitting || challengeDialog != null) return
        clearRouteState()
        navController.popBackStack()
    }

    val routeContent: @Composable (AuthRoute, (AuthRoute) -> Unit, () -> Unit) -> Unit =
        { currentRoute, onSelect, onBack ->
            Surface(color = HhyColors.PageBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HhySpacing.Xxl, vertical = HhySpacing.Xxxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            BrandHeader(currentRoute)
            Spacer(Modifier.height(HhySpacing.Xl))
            if (currentRoute == AuthRoute.PASSWORD || currentRoute == AuthRoute.SMS) {
                LoginModeSelector(currentRoute, enabled = !submitting && challengeDialog == null, onSelect = onSelect)
            } else {
                BackRouteHeader(currentRoute, enabled = !submitting && challengeDialog == null, onBack = onBack)
            }
            Spacer(Modifier.height(HhySpacing.Lg))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = HhyColors.Surface,
                shape = RoundedCornerShape(HhyRadius.LargeCard),
            ) {
                Column(
                    modifier = Modifier.padding(HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    if (state is AuthUiState.Message) {
                        StatusMessage(state as AuthUiState.Message)
                    }
                    if (submitting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator() }
                    }

                    PhoneField(phone, enabled = !submitting && challengeDialog == null) { phone = it; smsCode = ""; state = AuthUiState.Editing }
            if (currentRoute == AuthRoute.PASSWORD || currentRoute == AuthRoute.REGISTER || currentRoute == AuthRoute.RESET) {
                SecretField(if (currentRoute == AuthRoute.RESET) "新密码" else "密码", password, enabled = !submitting && challengeDialog == null) {
                    password = it.take(if (currentRoute == AuthRoute.PASSWORD) 72 else 20)
                    state = AuthUiState.Editing
                }
                if (currentRoute == AuthRoute.REGISTER || currentRoute == AuthRoute.RESET) {
                    Text(
                        AuthFormRules.NEW_PASSWORD_REQUIREMENTS,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (password.isNotEmpty() && !AuthFormRules.validNewPassword(password)) HhyColors.Error else HhyColors.TextSecondary,
                        fontSize = HhyType.CaptionSize,
                        lineHeight = HhyType.CaptionLineHeight,
                    )
                }
            }
            if (currentRoute == AuthRoute.REGISTER) {
                SecretField("确认密码", passwordAgain, enabled = !submitting && challengeDialog == null) {
                    passwordAgain = it.take(20)
                    state = AuthUiState.Editing
                }
                if (passwordAgain.isNotEmpty() && password != passwordAgain) {
                    Text(
                        "两次输入的密码不一致",
                        modifier = Modifier.fillMaxWidth(),
                        color = HhyColors.Error,
                        fontSize = HhyType.CaptionSize,
                        lineHeight = HhyType.CaptionLineHeight,
                    )
                }
                TextField("邀请码", inviteCode, {
                    inviteCode = it
                    state = AuthUiState.Editing
                }, false, enabled = !submitting && challengeDialog == null)
            }
            if (currentRoute == AuthRoute.SMS || currentRoute == AuthRoute.RESET) {
                SecretField(
                    "短信验证码",
                    smsCode,
                    enabled = !submitting && challengeDialog == null,
                    modifier = Modifier.focusRequester(smsFocusRequester),
                ) { smsCode = it; state = AuthUiState.Editing }
            }
            if (currentRoute == AuthRoute.SMS || currentRoute == AuthRoute.RESET) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting && challengeDialog == null && AuthFormRules.validPhone(phone) && smsRetrySeconds == 0L,
                    onClick = { requestChallenge(if (currentRoute == AuthRoute.SMS) PendingAuthIntent.SMS_CODE else PendingAuthIntent.RESET_CODE) },
                ) { Text(if (smsRetrySeconds > 0L) "$smsRetrySeconds 秒后可重新发送" else "发送验证码") }
            }
            val canSubmitForm = AuthFormRules.canSubmit(
                currentRoute, phone, password, passwordAgain, smsCode, inviteCode,
            )
            Button(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                enabled = !submitting && challengeDialog == null && canSubmitForm,
                onClick = {
                    when (currentRoute) {
                        AuthRoute.PASSWORD -> requestChallenge(PendingAuthIntent.PASSWORD_LOGIN)
                        AuthRoute.SMS -> launchCall({ api.smsLogin(phone, smsCode) }) {
                            smsCode = ""; completeAuthentication(it)
                        }
                        AuthRoute.REGISTER -> startRegistration()
                        AuthRoute.RESET -> launchCall({ api.resetPassword(phone, smsCode, password) }) {
                            password = ""; passwordAgain = ""; smsCode = ""
                        }
                    }
                },
            ) { Text(primaryAction(currentRoute), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (currentRoute == AuthRoute.PASSWORD || currentRoute == AuthRoute.SMS) {
                AuxiliaryRoutes(enabled = !submitting && challengeDialog == null, onSelect = onSelect)
            }
            Spacer(Modifier.height(HhySpacing.Lg))
            }
        }
    }

    Surface(color = HhyColors.PageBackground) {
        NavHost(
            navController = navController,
            startDestination = AuthDestination.Login,
            enterTransition = { HhyMotion.forwardEnter() },
            exitTransition = { HhyMotion.forwardExit() },
            popEnterTransition = { HhyMotion.backwardEnter() },
            popExitTransition = { HhyMotion.backwardExit() },
        ) {
            composable<AuthDestination.Login> {
                AnimatedContent(
                    targetState = loginRoute,
                    transitionSpec = { HhyMotion.peerContent() },
                    label = "login-mode",
                ) { currentRoute ->
                    routeContent(currentRoute, ::openRoute) { }
                }
            }
            composable<AuthDestination.Register> {
                BackHandler(enabled = submitting || challengeDialog != null) { }
                routeContent(AuthRoute.REGISTER, ::openRoute, ::popToLogin)
            }
            composable<AuthDestination.Reset> {
                BackHandler(enabled = submitting || challengeDialog != null) { }
                routeContent(AuthRoute.RESET, ::openRoute, ::popToLogin)
            }
        }
    }
    challengeDialog?.let { dialog ->
        SecurityChallengeDialog(
            state = dialog,
            onProofChange = { value ->
                challengeDialog = dialog.copy(
                    proof = value.filter { it.isLetterOrDigit() }.take(32),
                    phase = ChallengePhase.READY,
                    message = null,
                )
            },
            onRefresh = { requestChallenge(dialog.intent) },
            onExpired = { requestChallenge(dialog.intent, "验证已过期，已为你换一张") },
            onCancel = { challengeDialog = null },
            onSubmit = ::submitChallenge,
        )
    }
}

@Composable
private fun SecurityChallengeDialog(
    state: ChallengeDialogState,
    onProofChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onExpired: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val locked = state.phase in setOf(ChallengePhase.SUBMITTING, ChallengePhase.SUCCESS)
    var monotonicNow by remember(state.challengeId, state.phase, state.retryAtElapsedRealtime) {
        mutableStateOf(SystemClock.elapsedRealtime())
    }
    val expiresInSeconds = ((state.expiresAtElapsedRealtime - monotonicNow + 999L) / 1_000L).coerceAtLeast(0L)
    val retryInSeconds = ((state.retryAtElapsedRealtime - monotonicNow + 999L) / 1_000L).coerceAtLeast(0L)
    LaunchedEffect(state.challengeId, state.phase, state.expiresAtElapsedRealtime, state.retryAtElapsedRealtime) {
        while ((state.phase == ChallengePhase.READY && state.expiresAtElapsedRealtime > 0L) ||
            (state.phase == ChallengePhase.RATE_LIMITED && state.retryAtElapsedRealtime > 0L)) {
            delay(1_000L)
            monotonicNow = SystemClock.elapsedRealtime()
            if (state.phase == ChallengePhase.READY && monotonicNow >= state.expiresAtElapsedRealtime) {
                onExpired()
                break
            }
            if (state.phase == ChallengePhase.RATE_LIMITED && monotonicNow >= state.retryAtElapsedRealtime) {
                onRefresh()
                break
            }
        }
    }
    Dialog(
        onDismissRequest = { if (!locked) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !locked,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(HhyColors.TextPrimary.copy(alpha = HhyOpacity.Scrim)).imePadding()
                .padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Xxl),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(min = HhySize.DialogMinWidth, max = HhySize.ChallengeDialogWidth)
                    .heightIn(max = HhySize.ChallengeDialogMaxHeight),
                color = HhyColors.Surface,
                shape = RoundedCornerShape(HhyRadius.Dialog),
                shadowElevation = HhyElevation.Dialog,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(
                        start = HhySpacing.Xxl, end = HhySpacing.Xxl, top = HhySpacing.Xxl, bottom = HhySpacing.Xl,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    Text("完成安全验证", color = HhyColors.TextPrimary, fontSize = HhyType.PageTitleSize, lineHeight = HhyType.PageTitleLineHeight)
                    Text(
                        when (state.intent) {
                            PendingAuthIntent.SMS_CODE, PendingAuthIntent.RESET_CODE -> "请输入图中字符，验证通过后将发送短信验证码"
                            PendingAuthIntent.REGISTER -> "请输入图中字符，验证通过后将继续注册"
                            PendingAuthIntent.PASSWORD_LOGIN -> "请输入图中字符，验证通过后将继续登录"
                        },
                        color = HhyColors.TextSecondary, fontSize = HhyType.SecondaryBodySize,
                        lineHeight = HhyType.SecondaryBodyLineHeight, textAlign = TextAlign.Center,
                    )
                    when (state.phase) {
                        ChallengePhase.REQUESTING -> {
                            Spacer(Modifier.height(HhySpacing.Xxl))
                            CircularProgressIndicator(color = HhyColors.BrandPrimary)
                            Text("请稍候…", color = HhyColors.TextSecondary, fontSize = HhyType.BodySize)
                            Spacer(Modifier.height(HhySpacing.Xxl))
                            OutlinedButton(
                                modifier = Modifier.width(HhySize.ChallengeCancelButtonWidth).height(HhySize.PrimaryButtonHeight),
                                onClick = onCancel,
                                shape = RoundedCornerShape(HhyRadius.Button),
                            ) { Text("取消", fontSize = HhyType.ButtonSize) }
                        }
                        ChallengePhase.SUCCESS -> {
                            HhyIcon(
                                imageVector = HhyIcons.Check,
                                contentDescription = "验证成功",
                                modifier = Modifier.size(HhySize.MinimumTouchTarget),
                                tint = HhyColors.Success,
                            )
                            Text("验证通过", color = HhyColors.TextPrimary, fontSize = HhyType.ButtonSize)
                            Text("正在继续操作…", color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
                        }
                        ChallengePhase.NETWORK_ERROR, ChallengePhase.RATE_LIMITED -> {
                            Text(
                                if (state.phase == ChallengePhase.RATE_LIMITED && state.retryAtElapsedRealtime > 0L) {
                                    "操作太频繁，请在 $retryInSeconds 秒后重试"
                                } else state.message.orEmpty(),
                                color = if (state.phase == ChallengePhase.RATE_LIMITED) HhyColors.Warning else HhyColors.Error,
                                fontSize = HhyType.SecondaryBodySize,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                                OutlinedButton(
                                    modifier = Modifier.width(HhySize.ChallengeCancelButtonWidth).height(HhySize.PrimaryButtonHeight),
                                    onClick = onCancel,
                                    shape = RoundedCornerShape(HhyRadius.Button),
                                ) { Text("取消", fontSize = HhyType.ButtonSize) }
                                Button(
                                    modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
                                    enabled = state.phase != ChallengePhase.RATE_LIMITED || state.retryAtElapsedRealtime == 0L || retryInSeconds == 0L,
                                    onClick = onRefresh,
                                    shape = RoundedCornerShape(HhyRadius.Button),
                                ) { Text(if (state.phase == ChallengePhase.RATE_LIMITED) "稍后重试" else "重新加载", fontSize = HhyType.ButtonSize) }
                            }
                        }
                        else -> {
                            ChallengeImage(state.imageBase64)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(enabled = state.phase != ChallengePhase.SUBMITTING, onClick = onRefresh) {
                                    val minutes = expiresInSeconds / 60
                                    val seconds = expiresInSeconds % 60
                                    Text("%02d:%02d 后失效 · 换一张".format(minutes, seconds))
                                }
                            }
                            OutlinedTextField(
                                value = state.proof,
                                onValueChange = onProofChange,
                                modifier = Modifier.fillMaxWidth().height(HhySize.InputHeight),
                                placeholder = { Text("请输入图中字符", fontSize = HhyType.BodySize) },
                                singleLine = true,
                                enabled = state.phase != ChallengePhase.SUBMITTING,
                                shape = RoundedCornerShape(HhyRadius.Input),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done,
                                ),
                            )
                            state.message?.let {
                                Text(it, modifier = Modifier.fillMaxWidth(), color = HhyColors.Error,
                                    fontSize = HhyType.CaptionSize, lineHeight = HhyType.CaptionLineHeight)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                                OutlinedButton(
                                    modifier = Modifier.width(HhySize.ChallengeCancelButtonWidth).height(HhySize.PrimaryButtonHeight),
                                    enabled = state.phase != ChallengePhase.SUBMITTING,
                                    onClick = onCancel,
                                    shape = RoundedCornerShape(HhyRadius.Button),
                                ) { Text("取消", fontSize = HhyType.ButtonSize) }
                                Button(
                                    modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
                                    enabled = state.proof.isNotBlank() && state.phase != ChallengePhase.SUBMITTING,
                                    onClick = onSubmit,
                                    shape = RoundedCornerShape(HhyRadius.Button),
                                ) { Text(if (state.phase == ChallengePhase.SUBMITTING) "验证中…" else "验证并继续", fontSize = HhyType.ButtonSize) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** SCR-AUTH-006: a credential-free list of the user's active login devices. */
@Composable
fun LoginDevicesScreen(api: ContractAuthApi, accessToken: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<UserSecuritySessionResource>>(emptyList()) }
    var pendingRevokeId by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }
    val submitting = state is AuthUiState.Submitting
    BackHandler(onBack = onBack)

    fun load() {
        if (submitting) return
        scope.launch {
            state = AuthUiState.Submitting
            when (val result = api.sessions(accessToken)) {
                is AuthCallResult.Success -> {
                    val page = result.securitySessionsOrNull()
                    if (page == null) state = AuthUiState.Message("设备信息格式无效，请稍后重试", result.requestId)
                    else {
                        sessions = page.items
                        state = AuthUiState.Editing
                    }
                }
                is AuthCallResult.Failure -> state = AuthUiState.Message(
                    result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                        ?: "网络不可用，请检查连接后重试", result.requestId,
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        AuthenticatedRouteHeader("登录设备", onBack)
        Text("仅显示当前有效会话，不展示任何登录令牌。", color = HhyColors.TextSecondary)
        if (state is AuthUiState.Message) {
            val message = state as AuthUiState.Message
            Text(message.text, color = HhyColors.Warning)
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !submitting, onClick = ::load) {
            Text(if (sessions.isEmpty()) "加载登录设备" else "刷新登录设备")
        }
        sessions.forEach { session ->
            val deviceName = session.device?.get("deviceName")?.jsonPrimitive?.content ?: "未知设备"
            Text(if (session.current) "当前设备：$deviceName" else "设备：$deviceName")
            Text("最近活跃：${session.createdAt}", color = HhyColors.TextSecondary)
            if (!session.current) {
                if (pendingRevokeId == session.sessionId) {
                    Text("确认下线后，该设备需要重新登录。", color = HhyColors.Warning)
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !submitting, onClick = {
                        scope.launch {
                            state = AuthUiState.Submitting
                            when (val result = api.revokeSession(accessToken, session.sessionId)) {
                                is AuthCallResult.Success -> {
                                    pendingRevokeId = null
                                    sessions = sessions.filterNot { it.sessionId == session.sessionId }
                                    state = AuthUiState.Message("设备已下线", result.requestId)
                                }
                                is AuthCallResult.Failure -> state = AuthUiState.Message(
                                    result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                                        ?: "网络不可用，请检查连接后重试", result.requestId,
                                )
                            }
                        }
                    }) { Text("确认下线") }
                } else {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !submitting,
                        onClick = { pendingRevokeId = session.sessionId }) { Text("下线此设备") }
                }
            }
        }
    }
}

/** SCR-AUTH-007: password change keeps both password values in composition memory only. */
@Composable
fun ChangeLoginPasswordScreen(
    api: ContractAuthApi,
    accessToken: String,
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }
    val submitting = state is AuthUiState.Submitting
    val canSubmit = currentPassword.length in 8..72 && newPassword.length in 8..72 && newPassword == confirmPassword
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        AuthenticatedRouteHeader("修改登录密码", onBack)
        Text("修改成功后，所有设备都需要重新登录。", color = HhyColors.TextSecondary)
        if (state is AuthUiState.Message) {
            val message = state as AuthUiState.Message
            Text(message.text, color = HhyColors.Warning)
        }
        SecretField("当前密码", currentPassword, enabled = !submitting) { currentPassword = it; state = AuthUiState.Editing }
        SecretField("新密码", newPassword, enabled = !submitting) { newPassword = it; state = AuthUiState.Editing }
        SecretField("确认新密码", confirmPassword, enabled = !submitting) { confirmPassword = it; state = AuthUiState.Editing }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !submitting && canSubmit, onClick = {
            scope.launch {
                state = AuthUiState.Submitting
                when (val result = api.changePassword(accessToken, currentPassword, newPassword)) {
                    is AuthCallResult.Success -> {
                        currentPassword = ""; newPassword = ""; confirmPassword = ""
                        state = AuthUiState.Message("登录密码已修改，请重新登录", result.requestId)
                        onPasswordChanged()
                    }
                    is AuthCallResult.Failure -> {
                        currentPassword = ""; newPassword = ""; confirmPassword = ""
                        state = AuthUiState.Message(
                            result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                                ?: "网络不可用，请检查连接后重试", result.requestId,
                        )
                    }
                }
            }
        }) { Text("修改密码") }
    }
}

/** SCR-AUTH-005: terminal account gate with the sole permitted recovery action. */
@Composable
fun AccountBlockedScreen(
    api: ContractAuthApi,
    accessToken: String,
    user: UserSelfResource,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appeal by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }
    val submitting = state is AuthUiState.Submitting

    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Text("账号已受限", style = MaterialTheme.typography.titleLarge)
            Text("当前账号无法使用业务功能，只能提交申诉或退出登录。", color = HhyColors.Warning)
            Text("账号：${user.phoneMasked ?: user.id}")
            Text("账号状态：${restrictedStatusLabel(user.status)}", color = HhyColors.TextSecondary)
            if (state is AuthUiState.Message) {
                val message = state as AuthUiState.Message
                Text(message.text, color = HhyColors.Warning)
            }
            OutlinedTextField(
                value = appeal,
                onValueChange = { appeal = it.take(2000); state = AuthUiState.Editing },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("申诉说明") },
                minLines = 5,
                enabled = !submitting,
            )
            Text("请说明需要复核的情况，不要填写密码或短信验证码。", color = HhyColors.TextSecondary)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && appeal.isNotBlank(),
                onClick = {
                    scope.launch {
                        state = AuthUiState.Submitting
                        when (val result = api.createSupportTicket(
                            accessToken, "ACCOUNT_APPEAL", "账号冻结申诉", appeal.trim(),
                        )) {
                            is AuthCallResult.Success -> {
                                val ticket = result.supportTicketOrNull()
                                appeal = ""
                                state = if (ticket == null) {
                                    AuthUiState.Message("申诉已提交，请留意后续处理结果", result.requestId)
                                } else {
                                    AuthUiState.Message("申诉已提交，工单号：${ticket.ticketNo}", result.requestId)
                                }
                            }
                            is AuthCallResult.Failure -> state = AuthUiState.Message(
                                result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                                    ?: "网络不可用，申诉内容已保留", result.requestId,
                            )
                        }
                    }
                },
            ) { Text("提交申诉") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !submitting, onClick = onSignOut) {
                Text("退出登录")
            }
        }
    }
}

private fun restrictedStatusLabel(status: String) = when (status) {
    "FROZEN" -> "已冻结"
    "RESTRICTED" -> "已受限"
    else -> "当前不可用"
}

/** SCR-AUTH-008: version-bound cancellation with in-memory OTP and explicit confirmation. */
@Composable
fun AccountCancellationScreen(
    api: ContractAuthApi,
    accessToken: String,
    user: UserSelfResource,
    onBack: () -> Unit,
    onReturnToLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var challengeId by remember { mutableStateOf("") }
    var challengeImageBase64 by remember { mutableStateOf("") }
    var challengeProof by remember { mutableStateOf("") }
    var confirmVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<AuthUiState>(AuthUiState.Editing) }
    val submitting = state is AuthUiState.Submitting
    val phoneMatches = AuthFormRules.validPhone(phone) && maskedPhone(phone) == user.phoneMasked
    BackHandler(enabled = !submitting && !confirmVisible, onBack = onBack)

    fun launchCall(call: suspend () -> AuthCallResult, success: (AuthCallResult.Success) -> Unit = {}) {
        if (submitting || submitted) return
        scope.launch {
            state = AuthUiState.Submitting
            when (val result = call()) {
                is AuthCallResult.Success -> {
                    success(result)
                    if (state is AuthUiState.Submitting) state = AuthUiState.Message("操作成功", result.requestId)
                }
                is AuthCallResult.Failure -> state = AuthUiState.Message(
                    result.statusCode?.let { errorForStatus(it, result.errorCode, result.retryAfterSeconds) }
                        ?: "网络不可用，请检查连接后重试", result.requestId,
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        AuthenticatedRouteHeader("注销账号", onBack, enabled = !submitting && !confirmVisible)
        Text("申请后账号将进入注销处理状态，当前设备和其他设备均需重新登录。", color = HhyColors.Warning)
        Text("当前账号：${user.phoneMasked ?: user.id}", color = HhyColors.TextSecondary)
        if (state is AuthUiState.Message) {
            val message = state as AuthUiState.Message
            Text(message.text, color = HhyColors.Warning)
        }
        if (submitted) {
            Text("注销申请已提交，安全会话已清理。")
            Button(modifier = Modifier.fillMaxWidth(), onClick = onReturnToLogin) { Text("返回登录") }
        } else {
            PhoneField(phone, enabled = !submitting) { phone = it; state = AuthUiState.Editing }
            if (phone.isNotBlank() && !phoneMatches) {
                Text("请输入与当前账号一致的完整手机号", color = HhyColors.Warning)
            }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(2000); state = AuthUiState.Editing },
                modifier = Modifier.fillMaxWidth(), label = { Text("注销原因") },
                minLines = 3, enabled = !submitting,
            )
            SecretField("短信验证码", smsCode, enabled = !submitting) { smsCode = it; state = AuthUiState.Editing }
            if (challengeId.isNotBlank()) {
                ChallengeImage(challengeImageBase64)
                SecretField("请输入图中字符", challengeProof, enabled = !submitting) {
                    challengeProof = it; state = AuthUiState.Editing
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && phoneMatches &&
                    (challengeId.isBlank() || challengeProof.isNotBlank()),
                onClick = {
                    if (challengeId.isBlank()) {
                        launchCall({ api.securityChallenge("SENSITIVE_OPERATION") }) { result ->
                            challengeId = result.data["challengeId"]?.jsonPrimitive?.content.orEmpty()
                            challengeImageBase64 = result.data["imageBase64"]?.jsonPrimitive?.content.orEmpty()
                            challengeProof = ""
                            state = AuthUiState.Message("请完成图形验证后继续", result.requestId)
                        }
                    } else {
                        launchCall({ api.sendSms(phone, "SENSITIVE_OPERATION", challengeId, challengeProof) })
                    }
                },
            ) { Text("发送短信验证码") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && phoneMatches && reason.isNotBlank() && smsCode.length in 4..10,
                onClick = { confirmVisible = true },
            ) { Text("申请注销") }
        }
    }
    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { if (!submitting) confirmVisible = false },
            title = { Text("确认申请注销？") },
            text = { Text("提交后账号将进入注销处理，所有已登录设备将立即失效。注销原因：${reason.trim()}") },
            dismissButton = { TextButton(enabled = !submitting, onClick = { confirmVisible = false }) { Text("取消") } },
            confirmButton = {
                TextButton(enabled = !submitting, onClick = {
                    confirmVisible = false
                    launchCall({ api.requestCancellation(accessToken, reason.trim(), smsCode, user.version) }) { result ->
                        phone = ""; smsCode = ""; challengeProof = ""; challengeId = ""; reason = ""
                        submitted = true
                        state = AuthUiState.Message("注销申请已提交", result.requestId)
                    }
                }) { Text("确认提交") }
            },
        )
    }
}

private fun maskedPhone(phone: String): String? = if (phone.length == 11) {
    phone.take(3) + "****" + phone.takeLast(4)
} else null

@Composable
private fun BrandHeader(route: AuthRoute) {
    Surface(
        modifier = Modifier.size(HhySize.AppLogo),
        color = HhyColors.BrandPrimary,
        shape = RoundedCornerShape(HhyRadius.LargeCard),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "合",
                color = HhyColors.TextInverse,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
    Spacer(Modifier.height(HhySpacing.Md))
    Text("合伙云 Pro", style = MaterialTheme.typography.headlineSmall)
    Text(
        when (route) {
            AuthRoute.PASSWORD, AuthRoute.SMS -> "安全登录，开启协作"
            AuthRoute.REGISTER -> "创建账号，加入可信协作"
            AuthRoute.RESET -> "验证身份，重置登录密码"
        },
        color = HhyColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LoginModeSelector(
    selected: AuthRoute,
    enabled: Boolean,
    onSelect: (AuthRoute) -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
) {
    listOf(AuthRoute.PASSWORD, AuthRoute.SMS).forEach { route ->
        val selectedRoute = route == selected
        Surface(
            modifier = Modifier.weight(1f).height(HhySize.TabHeight),
            color = if (selectedRoute) HhyColors.SoftBlue else HhyColors.Surface,
            shape = RoundedCornerShape(HhyRadius.Pill),
            border = BorderStroke(
                HhySize.Hairline,
                if (selectedRoute) HhyColors.BrandPrimary else HhyColors.Border,
            ),
            enabled = enabled,
            onClick = { onSelect(route) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    route.title,
                    color = if (selectedRoute) HhyColors.BrandPrimary else HhyColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedRouteHeader(
    title: String,
    onBack: () -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        HhyBackButton(onClick = onBack, enabled = enabled)
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun BackRouteHeader(route: AuthRoute, enabled: Boolean, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HhyBackButton(enabled = enabled, onClick = onBack)
        Text(
            route.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AuxiliaryRoutes(enabled: Boolean, onSelect: (AuthRoute) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HhySpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(enabled = enabled, onClick = { onSelect(AuthRoute.REGISTER) }) { Text("注册账号") }
        TextButton(enabled = enabled, onClick = { onSelect(AuthRoute.RESET) }) { Text("忘记密码") }
    }
}

@Composable
private fun StatusMessage(message: AuthUiState.Message) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HhyColors.SoftBlue,
        shape = RoundedCornerShape(HhyRadius.Input),
    ) {
        Column(
            modifier = Modifier.padding(HhySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
        ) {
            Text(message.text, color = HhyColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable private fun PhoneField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) = TextField("手机号", value, onValueChange, false, enabled)
@Composable private fun SecretField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) = TextField(label, value, onValueChange, true, enabled, modifier)
@Composable private fun TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) = OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = HhySize.InputHeight),
    label = { Text(label) },
    singleLine = true, enabled = enabled, visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    shape = RoundedCornerShape(HhyRadius.Input),
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
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "图形验证码图片，请根据图片输入字符",
            modifier = Modifier.width(HhySize.ChallengeImageWidth).height(HhySize.ChallengeImageHeight),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private fun challengeImageDecodes(encoded: String): Boolean = encoded.takeIf(String::isNotBlank)?.let { value ->
    runCatching {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
    }.getOrDefault(false)
} ?: false

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
