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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.identity.IdentityFlowScreen
import cc.orbexa.hhy.discovery.R07PublisherScreen
import cc.orbexa.hhy.discovery.R07SearchScreen
import cc.orbexa.hhy.network.AuthCallResult
import cc.orbexa.hhy.network.AuthSessionResource
import cc.orbexa.hhy.network.AuthSessionStore
import cc.orbexa.hhy.network.HhyNetworkJson
import cc.orbexa.hhy.network.HomeNavigationTargetSnapshot
import cc.orbexa.hhy.network.ContractAuthApi
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.UserSelfResource
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.UrlConnectionHhyPublicApi
import cc.orbexa.hhy.network.UrlConnectionContractAuthApi
import cc.orbexa.hhy.network.UrlConnectionContractIdentityApi
import cc.orbexa.hhy.network.UrlConnectionContractMediaApi
import cc.orbexa.hhy.network.UrlConnectionContractR07Api
import cc.orbexa.hhy.network.UrlConnectionExperienceApi
import cc.orbexa.hhy.network.UrlConnectionContractR08Api
import cc.orbexa.hhy.network.UrlConnectionContractR09Api
import cc.orbexa.hhy.network.UrlConnectionContractR10Api
import cc.orbexa.hhy.network.UrlConnectionContractR11Api
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.userSelfOrNull
import java.net.URI
import cc.orbexa.hhy.project.R08ProjectDetailScreen
import cc.orbexa.hhy.project.R08ProjectEditorScreen
import cc.orbexa.hhy.project.R08ProjectListScreen
import cc.orbexa.hhy.apppromotion.R09AppDetailScreen
import cc.orbexa.hhy.apppromotion.R09AppEditorScreen
import cc.orbexa.hhy.apppromotion.R09AppListScreen
import cc.orbexa.hhy.grouppromotion.R10GroupDetailScreen
import cc.orbexa.hhy.grouppromotion.R10GroupEditorScreen
import cc.orbexa.hhy.grouppromotion.R10GroupListScreen
import cc.orbexa.hhy.teamleader.R11TeamLeaderListScreen
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
    @Serializable data object Apps : AuthenticatedRoute
    @Serializable data class AppDetail(val appId: String) : AuthenticatedRoute
    @Serializable data class AppEditor(val appId: String? = null) : AuthenticatedRoute
    @Serializable data object Groups : AuthenticatedRoute
    @Serializable data class GroupDetail(val groupId: String) : AuthenticatedRoute
    @Serializable data class GroupEditor(val groupId: String? = null) : AuthenticatedRoute
    @Serializable data object TeamLeaders : AuthenticatedRoute
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
    val r09Api = remember { UrlConnectionContractR09Api(BuildConfig.API_BASE_URL) }
    val r10Api = remember { UrlConnectionContractR10Api(BuildConfig.API_BASE_URL) }
    val r11Api = remember { UrlConnectionContractR11Api(BuildConfig.API_BASE_URL) }
    val mediaApi = remember { UrlConnectionContractMediaApi(BuildConfig.API_BASE_URL) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
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
                onOpenApps = { navController.navigate(AuthenticatedRoute.Apps) },
                onOpenGroups = { navController.navigate(AuthenticatedRoute.Groups) },
                onOpenTeamLeaders = { navController.navigate(AuthenticatedRoute.TeamLeaders) },
                canOpenHomeTarget = { target -> canOpenHomeTarget(target) },
                onOpenHomeTarget = { target ->
                    val route = target.route.orEmpty()
                    when {
                        target.targetType == "IN_APP_ROUTE" && route == "/search" ->
                            navController.navigate(AuthenticatedRoute.Search)
                        target.targetType == "IN_APP_ROUTE" && route == "/content/projects" ->
                            navController.navigate(AuthenticatedRoute.Projects)
                        target.targetType == "IN_APP_ROUTE" && route == "/content/apps" ->
                            navController.navigate(AuthenticatedRoute.Apps)
                        target.targetType == "IN_APP_ROUTE" && route == "/content/groups" ->
                            navController.navigate(AuthenticatedRoute.Groups)
                        target.targetType == "IN_APP_ROUTE" && route == "/content/team-leaders" ->
                            navController.navigate(AuthenticatedRoute.TeamLeaders)
                        target.targetType == "IN_APP_ROUTE" && route.startsWith("/content/project/") ->
                            navController.navigate(AuthenticatedRoute.ProjectDetail(route.substringAfterLast('/')))
                        target.targetType == "IN_APP_ROUTE" && route.startsWith("/content/app/") ->
                            navController.navigate(AuthenticatedRoute.AppDetail(route.substringAfterLast('/')))
                        target.targetType == "IN_APP_ROUTE" && route.startsWith("/content/group/") ->
                            navController.navigate(AuthenticatedRoute.GroupDetail(route.substringAfterLast('/')))
                        target.targetType in setOf("H5_URL", "DOWNLOAD") && isSafeHomeUrl(target.url) ->
                            uriHandler.openUri(target.url.orEmpty())
                    }
                },
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
                mediaApi = mediaApi,
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
        composable<AuthenticatedRoute.Apps> {
            R09AppListScreen(
                api = r09Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onAppSelected = { navController.navigate(AuthenticatedRoute.AppDetail(it)) },
                onCreateApp = { navController.navigate(AuthenticatedRoute.AppEditor()) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.AppDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.AppDetail>()
            R09AppDetailScreen(
                api = r09Api,
                accessToken = authenticated.session.accessToken,
                appId = route.appId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.AppEditor(it)) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.AppEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.AppEditor>()
            R09AppEditorScreen(
                api = r09Api,
                mediaApi = mediaApi,
                accessToken = authenticated.session.accessToken,
                appId = route.appId,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(AuthenticatedRoute.AppDetail(id)) {
                        popUpTo<AuthenticatedRoute.Apps>() { inclusive = false }
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Groups> {
            R10GroupListScreen(
                api = r10Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onGroupSelected = { navController.navigate(AuthenticatedRoute.GroupDetail(it)) },
                onCreateGroup = { navController.navigate(AuthenticatedRoute.GroupEditor()) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.GroupDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.GroupDetail>()
            R10GroupDetailScreen(
                api = r10Api,
                accessToken = authenticated.session.accessToken,
                groupId = route.groupId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.GroupEditor(it)) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.GroupEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.GroupEditor>()
            R10GroupEditorScreen(
                api = r10Api,
                mediaApi = mediaApi,
                accessToken = authenticated.session.accessToken,
                groupId = route.groupId,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(AuthenticatedRoute.GroupDetail(id)) {
                        popUpTo<AuthenticatedRoute.Groups>() { inclusive = false }
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.TeamLeaders> {
            R11TeamLeaderListScreen(
                api = r11Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onTeamLeaderSelected = { },
                onSessionExpired = onSessionInvalidated,
            )
        }
    }
}

private fun canOpenHomeTarget(target: HomeNavigationTargetSnapshot): Boolean {
    val route = target.route.orEmpty()
    return when (target.targetType) {
        "IN_APP_ROUTE" -> route in setOf("/search", "/content/projects", "/content/apps", "/content/groups", "/content/team-leaders") ||
            (route.startsWith("/content/project/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/content/app/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/content/group/") && route.substringAfterLast('/').isNotBlank())
        "H5_URL", "DOWNLOAD" -> isSafeHomeUrl(target.url)
        else -> false
    }
}

private fun isSafeHomeUrl(value: String?): Boolean = runCatching {
    val uri = URI.create(value.orEmpty())
    uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null
}.getOrDefault(false)

@androidx.compose.runtime.Composable
private fun RestoringSessionScreen() {
    Surface(color = HhyColors.PageBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HhySpacing.Xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(HhyRadius.LargeCard),
                colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                        HhyIcon(
                            HhyIcons.Shield,
                            contentDescription = null,
                            modifier = Modifier.padding(HhySpacing.Lg),
                            tint = HhyColors.BrandPrimary,
                        )
                    }
                    Text("合伙云 Pro", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    Text("正在恢复安全会话", color = HhyColors.TextSecondary)
                    Spacer(Modifier.height(HhySpacing.Xs))
                    CircularProgressIndicator()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = HhyColors.PageBackground,
                        shape = RoundedCornerShape(HhyRadius.Tag),
                    ) {
                        Text(
                            "正在确认当前设备的登录状态，完成后将自动进入应用",
                            modifier = Modifier.padding(HhySpacing.Md),
                            color = HhyColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
