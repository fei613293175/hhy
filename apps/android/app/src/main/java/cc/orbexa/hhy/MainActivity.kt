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
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.AuthSessionStore
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.UrlConnectionHhyPublicApi
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.sessionOrNull
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
                var sessionState by remember { mutableStateOf(SessionState.Restoring) }
                LaunchedEffect(authApi, sessionStore) {
                    val stored = sessionStore.load()
                    if (stored == null) {
                        sessionState = SessionState.AuthenticationRequired
                    } else {
                        sessionState = when (val result = authApi.refresh(stored.refreshToken, stored.deviceId)) {
                            is AuthCallResult.Success -> if (result.sessionOrNull()?.let(sessionStore::save) == true) {
                                SessionState.Authenticated
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
                StartupGateScreen(gate = gate, request = request) {
                    when (sessionState) {
                        SessionState.Restoring -> RestoringSessionScreen()
                        SessionState.Authenticated -> HhyShellScreen(
                            versionName = BuildConfig.VERSION_NAME,
                            buildType = BuildConfig.BUILD_TYPE,
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            contractVersion = BuildConfig.CONTRACT_VERSION,
                        )
                        SessionState.AuthenticationRequired -> AuthScreen(
                            apiBaseUrl = BuildConfig.API_BASE_URL,
                            onAuthenticated = { session ->
                                sessionStore.save(session).also { saved ->
                                    if (saved) sessionState = SessionState.Authenticated
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class SessionState { Restoring, AuthenticationRequired, Authenticated }

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
