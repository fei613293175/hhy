package cc.orbexa.hhy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cc.orbexa.hhy.auth.AuthScreen
import cc.orbexa.hhy.auth.ChangeLoginPasswordScreen
import cc.orbexa.hhy.auth.AccountBlockedScreen
import cc.orbexa.hhy.auth.LoginDevicesScreen
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.AuthSessionStore
import cc.orbexa.hhy.network.UserSelfResource
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.UrlConnectionHhyPublicApi
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.userSelfOrNull
import cc.orbexa.hhy.shell.HhyShellScreen
import cc.orbexa.hhy.startup.StartupGateScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HhyTheme {
                val context = LocalContext.current
                val gate = remember {
                    StartupGate(UrlConnectionHhyPublicApi(BuildConfig.API_BASE_URL))
                }
                val authApi = remember(context.applicationContext) {
                    UrlConnectionContractAuthApi(BuildConfig.API_BASE_URL, context.applicationContext)
                }
                val sessionStore = remember(context.applicationContext) {
                    AuthSessionStore(context.applicationContext)
                }
                val request = remember {
                    StartupGateRequest(
                        versionCode = BuildConfig.VERSION_CODE.toLong(),
                        versionName = BuildConfig.VERSION_NAME,
                        channel = BuildConfig.APP_CHANNEL,
                        environment = BuildConfig.APP_ENVIRONMENT,
                    )
                }
                var sessionState by remember { mutableStateOf<SessionState>(SessionState.Restoring) }
                var securityDestination by remember { mutableStateOf(SecurityDestination.Shell) }
                LaunchedEffect(authApi, sessionStore) {
                    val stored = sessionStore.load()
                    if (stored == null) {
                        sessionState = SessionState.AuthenticationRequired
                    } else {
                        sessionState = when (val result = authApi.refresh(stored.refreshToken, stored.deviceId)) {
                            is AuthCallResult.Success -> if (result.sessionOrNull()?.let(sessionStore::save) == true) {
                                SessionState.Verifying(requireNotNull(result.sessionOrNull()))
                            } else {
                                sessionStore.clear()
                                SessionState.AuthenticationRequired
                            }
                            is AuthCallResult.Failure -> {
                                sessionStore.clear()
                                SessionState.AuthenticationRequired
                            }
                        }
                    }
                }
                LaunchedEffect(sessionState) {
                    val verifying = sessionState as? SessionState.Verifying ?: return@LaunchedEffect
                    sessionState = when (val result = authApi.self(verifying.session.accessToken)) {
                        is AuthCallResult.Success -> {
                            val user = result.userSelfOrNull()
                            when {
                                user == null -> SessionState.AuthenticationRequired
                                user.status == "ACTIVE" -> SessionState.Authenticated(verifying.session)
                                else -> SessionState.Restricted(verifying.session, user)
                            }
                        }
                        is AuthCallResult.Failure -> {
                            if (result.statusCode == 401) sessionStore.clear()
                            SessionState.AuthenticationRequired
                        }
                    }
                }
                StartupGateScreen(gate = gate, request = request) {
                    when (sessionState) {
                        SessionState.Restoring, is SessionState.Verifying -> RestoringSessionScreen()
                        is SessionState.Authenticated -> {
                            val authenticated = sessionState as SessionState.Authenticated
                            when (securityDestination) {
                                SecurityDestination.Shell -> HhyShellScreen(
                                    versionName = BuildConfig.VERSION_NAME,
                                    buildType = BuildConfig.BUILD_TYPE,
                                    apiBaseUrl = BuildConfig.API_BASE_URL,
                                    contractVersion = BuildConfig.CONTRACT_VERSION,
                                    onOpenLoginDevices = { securityDestination = SecurityDestination.LoginDevices },
                                    onOpenChangePassword = { securityDestination = SecurityDestination.ChangePassword },
                                )
                                SecurityDestination.LoginDevices -> LoginDevicesScreen(authApi, authenticated.session.accessToken)
                                SecurityDestination.ChangePassword -> ChangeLoginPasswordScreen(
                                    authApi, authenticated.session.accessToken,
                                ) {
                                    sessionStore.clear()
                                    securityDestination = SecurityDestination.Shell
                                    sessionState = SessionState.AuthenticationRequired
                                }
                            }
                        }
                        is SessionState.Restricted -> {
                            val restricted = sessionState as SessionState.Restricted
                            AccountBlockedScreen(authApi, restricted.session.accessToken, restricted.user) {
                                sessionStore.clear()
                                sessionState = SessionState.AuthenticationRequired
                            }
                        }
                        SessionState.AuthenticationRequired -> AuthScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onAuthenticated = { session ->
                                sessionStore.save(session).also { saved ->
                                    if (saved) sessionState = SessionState.Verifying(session)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SessionState {
    data object Restoring : SessionState
    data object AuthenticationRequired : SessionState
    data class Verifying(val session: AuthSessionResource) : SessionState
    data class Authenticated(val session: AuthSessionResource) : SessionState
    data class Restricted(val session: AuthSessionResource, val user: UserSelfResource) : SessionState
}

private enum class SecurityDestination { Shell, LoginDevices, ChangePassword }

@androidx.compose.runtime.Composable
private fun RestoringSessionScreen() {
    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text("正在恢复安全会话")
        }
    }
}
