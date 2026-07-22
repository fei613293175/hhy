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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cc.orbexa.hhy.auth.AuthScreen
import cc.orbexa.hhy.auth.ChangeLoginPasswordScreen
import cc.orbexa.hhy.auth.AccountBlockedScreen
import cc.orbexa.hhy.auth.AccountCancellationScreen
import cc.orbexa.hhy.AboutScreen
import cc.orbexa.hhy.auth.LoginDevicesScreen
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.identity.IdentityFlowScreen
import cc.orbexa.hhy.discovery.R07PublisherScreen
import cc.orbexa.hhy.discovery.R07SearchScreen
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.AuthSessionStore
import cc.orbexa.hhy.network.HhyNetworkJson
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.UserSelfResource
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.UrlConnectionHhyPublicApi
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.UrlConnectionContractIdentityApi
import cc.orbexa.hhy.network.UrlConnectionContractR07Api
import cc.orbexa.hhy.network.UrlConnectionExperienceApi
import cc.orbexa.hhy.network.UrlConnectionContractR08Api
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.userSelfOrNull
import cc.orbexa.hhy.project.R08ProjectDetailScreen
import cc.orbexa.hhy.project.R08ProjectEditorScreen
import cc.orbexa.hhy.project.R08ProjectListScreen
import cc.orbexa.hhy.shell.HhyShellScreen
import cc.orbexa.hhy.startup.StartupGateScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

internal const val CI_SESSION_INTENT_EXTRA = "cc.orbexa.hhy.extra.CI_SESSION"

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
                val injectedCiSession = remember {
                    if (!BuildConfig.DEBUG) null else intent.getStringExtra(CI_SESSION_INTENT_EXTRA)
                        ?.let { encoded -> runCatching { HhyNetworkJson.value.decodeFromString<AuthSessionResource>(encoded) }.getOrNull() }
                }
                val identityApi = remember { UrlConnectionContractIdentityApi(BuildConfig.API_BASE_URL) }
                val request = remember {
                    StartupGateRequest(
                        versionCode = BuildConfig.VERSION_CODE.toLong(),
                        versionName = BuildConfig.VERSION_NAME,
                        channel = BuildConfig.APP_CHANNEL,
                        environment = BuildConfig.APP_ENVIRONMENT,
                    )
                }
                var sessionState by remember {
                    mutableStateOf<SessionState>(injectedCiSession?.let(SessionState::Verifying) ?: SessionState.Restoring)
                }
                LaunchedEffect(authApi, sessionStore, injectedCiSession) {
                    if (injectedCiSession != null) return@LaunchedEffect
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
                                user.status == "ACTIVE" -> SessionState.Authenticated(verifying.session, user)
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
                            AuthenticatedNavHost(
                                authenticated = authenticated,
                                authApi = authApi,
                                identityApi = identityApi,
                                onSessionInvalidated = {
                                    sessionStore.clear()
                                    sessionState = SessionState.AuthenticationRequired
                                },
                            )
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
    data class Authenticated(val session: AuthSessionResource, val user: UserSelfResource) : SessionState
    data class Restricted(val session: AuthSessionResource, val user: UserSelfResource) : SessionState
}

@Serializable
private sealed interface AuthenticatedRoute {
    @Serializable data object Shell : AuthenticatedRoute
    @Serializable data object LoginDevices : AuthenticatedRoute
    @Serializable data object ChangePassword : AuthenticatedRoute
    @Serializable data object Cancellation : AuthenticatedRoute
    @Serializable data object Identity : AuthenticatedRoute
    @Serializable data object About : AuthenticatedRoute
    @Serializable data object Search : AuthenticatedRoute
    @Serializable data class Publisher(val publisherId: String) : AuthenticatedRoute
    @Serializable data object Projects : AuthenticatedRoute
    @Serializable data class ProjectDetail(val projectId: String) : AuthenticatedRoute
    @Serializable data class ProjectEditor(val projectId: String? = null) : AuthenticatedRoute
}

@androidx.compose.runtime.Composable
private fun AuthenticatedNavHost(
    authenticated: SessionState.Authenticated,
    authApi: ContractAuthApi,
    identityApi: ContractIdentityApi,
    onSessionInvalidated: () -> Unit,
) {
    val navController = rememberNavController()
    val experienceApi = remember { UrlConnectionExperienceApi(BuildConfig.API_BASE_URL) }
    val r07Api = remember { UrlConnectionContractR07Api(BuildConfig.API_BASE_URL) }
    val r08Api = remember { UrlConnectionContractR08Api(BuildConfig.API_BASE_URL) }
    NavHost(
        navController = navController,
        startDestination = AuthenticatedRoute.Shell,
        enterTransition = { HhyMotion.forwardEnter() },
        exitTransition = { HhyMotion.forwardExit() },
        popEnterTransition = { HhyMotion.backwardEnter() },
        popExitTransition = { HhyMotion.backwardExit() },
    ) {
        composable<AuthenticatedRoute.Shell> {
            HhyShellScreen(
                onOpenSearch = { navController.navigate(AuthenticatedRoute.Search) },
                onOpenProjects = { navController.navigate(AuthenticatedRoute.Projects) },
                onCreateProject = { navController.navigate(AuthenticatedRoute.ProjectEditor()) },
                onOpenLoginDevices = { navController.navigate(AuthenticatedRoute.LoginDevices) },
                onOpenChangePassword = { navController.navigate(AuthenticatedRoute.ChangePassword) },
                onOpenCancellation = { navController.navigate(AuthenticatedRoute.Cancellation) },
                onOpenIdentity = { navController.navigate(AuthenticatedRoute.Identity) },
                onOpenAbout = { navController.navigate(AuthenticatedRoute.About) },
                experienceApi = experienceApi,
                accessToken = authenticated.session.accessToken,
            )
        }
        composable<AuthenticatedRoute.LoginDevices> {
            LoginDevicesScreen(
                api = authApi,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AuthenticatedRoute.ChangePassword> {
            ChangeLoginPasswordScreen(
                api = authApi,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onPasswordChanged = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Cancellation> {
            AccountCancellationScreen(
                api = authApi,
                accessToken = authenticated.session.accessToken,
                user = authenticated.user,
                onBack = { navController.popBackStack() },
                onReturnToLogin = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Identity> {
            IdentityFlowScreen(
                api = identityApi,
                accessToken = authenticated.session.accessToken,
                returnUrl = BuildConfig.IDENTITY_RETURN_URL,
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.About> {
            AboutScreen(experienceApi, authenticated.session.accessToken, onBack = { navController.popBackStack() })
        }
        composable<AuthenticatedRoute.Search> {
            R07SearchScreen(
                api = r07Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onPublisherSelected = { publisherId ->
                    navController.navigate(AuthenticatedRoute.Publisher(publisherId))
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Publisher> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.Publisher>()
            R07PublisherScreen(
                api = r07Api,
                accessToken = authenticated.session.accessToken,
                publisherId = route.publisherId,
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Projects> {
            R08ProjectListScreen(
                api = r08Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onProjectSelected = { navController.navigate(AuthenticatedRoute.ProjectDetail(it)) },
                onCreateProject = { navController.navigate(AuthenticatedRoute.ProjectEditor()) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.ProjectDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ProjectDetail>()
            R08ProjectDetailScreen(
                api = r08Api,
                accessToken = authenticated.session.accessToken,
                projectId = route.projectId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.ProjectEditor(it)) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.ProjectEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ProjectEditor>()
            R08ProjectEditorScreen(
                api = r08Api,
                accessToken = authenticated.session.accessToken,
                projectId = route.projectId,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(AuthenticatedRoute.ProjectDetail(id)) {
                        popUpTo<AuthenticatedRoute.Projects>() { inclusive = false }
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
    }
}

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
