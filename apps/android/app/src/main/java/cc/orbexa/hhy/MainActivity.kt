package cc.orbexa.hhy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import cc.orbexa.hhy.auth.AuthScreen
import cc.orbexa.hhy.auth.ChangeLoginPasswordScreen
import cc.orbexa.hhy.auth.AccountBlockedScreen
import cc.orbexa.hhy.auth.AccountCancellationScreen
import cc.orbexa.hhy.AboutScreen
import cc.orbexa.hhy.auth.LoginDevicesScreen
import cc.orbexa.hhy.chat.R14ChatDetailScreen
import cc.orbexa.hhy.chat.R14ConversationListScreen
import cc.orbexa.hhy.chat.R14AuthoritativeRefreshResult
import cc.orbexa.hhy.chat.refreshR14ChatAuthoritatively
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
import cc.orbexa.hhy.network.ContractR13Api
import cc.orbexa.hhy.network.ChatContentCardPayload
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.PublisherSummaryResource
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
import cc.orbexa.hhy.network.UrlConnectionContractR12Api
import cc.orbexa.hhy.network.UrlConnectionContractR12MeApi
import cc.orbexa.hhy.network.UrlConnectionContractR12ProfileApi
import cc.orbexa.hhy.network.UrlConnectionContractR13Api
import cc.orbexa.hhy.network.UrlConnectionContractR14Api
import cc.orbexa.hhy.network.UrlConnectionContractR15Api
import cc.orbexa.hhy.network.UrlConnectionContractR16Api
import cc.orbexa.hhy.network.UrlConnectionContractR18MembershipApi
import cc.orbexa.hhy.network.UrlConnectionContractR19PropApi
import cc.orbexa.hhy.network.UrlConnectionContractR20RedPacketApi
import cc.orbexa.hhy.network.OkHttpR14RealtimeClient
import cc.orbexa.hhy.network.R14RealtimeEvent
import cc.orbexa.hhy.network.R14RealtimeScope
import cc.orbexa.hhy.network.sessionOrNull
import cc.orbexa.hhy.network.userSelfOrNull
import java.net.URI
import cc.orbexa.hhy.project.R08ProjectDetailScreen
import cc.orbexa.hhy.activity.R13ContentActionSheet
import cc.orbexa.hhy.activity.R13ContentSheet
import cc.orbexa.hhy.activity.R13FavoritesScreen
import cc.orbexa.hhy.activity.R13HistoryScreen
import cc.orbexa.hhy.project.R08ProjectEditorScreen
import cc.orbexa.hhy.project.R08ProjectListScreen
import cc.orbexa.hhy.apppromotion.R09AppDetailScreen
import cc.orbexa.hhy.apppromotion.R09AppEditorScreen
import cc.orbexa.hhy.apppromotion.R09AppListScreen
import cc.orbexa.hhy.grouppromotion.R10GroupDetailScreen
import cc.orbexa.hhy.grouppromotion.R10GroupEditorScreen
import cc.orbexa.hhy.grouppromotion.R10GroupListScreen
import cc.orbexa.hhy.teamleader.R11TeamLeaderListScreen
import cc.orbexa.hhy.teamleader.R11TeamLeaderDetailScreen
import cc.orbexa.hhy.teamleader.R11TeamLeaderEditorScreen
import cc.orbexa.hhy.contentmanagement.R12ContentManagementDetailScreen
import cc.orbexa.hhy.contentmanagement.R12ContentAnalyticsScreen
import cc.orbexa.hhy.contentmanagement.R12ContentReviewsScreen
import cc.orbexa.hhy.contentmanagement.R12DraftsScreen
import cc.orbexa.hhy.contentmanagement.R12MyContentsScreen
import cc.orbexa.hhy.contentmanagement.R12PublishCenterScreen
import cc.orbexa.hhy.contentmanagement.R12PublishPreviewScreen
import cc.orbexa.hhy.contentmanagement.R12PublishResultScreen
import cc.orbexa.hhy.shell.HhyShellScreen
import cc.orbexa.hhy.shell.HhyTopLevelDestination
import cc.orbexa.hhy.shell.R12ProfileScreen
import cc.orbexa.hhy.startup.StartupGateScreen
import cc.orbexa.hhy.support.R15CreateTicketScreen
import cc.orbexa.hhy.support.R15NotificationDetailScreen
import cc.orbexa.hhy.support.R15NotificationListScreen
import cc.orbexa.hhy.support.R15ReportContentSheet
import cc.orbexa.hhy.support.R15SupportDetailScreen
import cc.orbexa.hhy.support.R15SupportHomeScreen
import cc.orbexa.hhy.support.R15SupportListScreen
import cc.orbexa.hhy.order.R16OrderDetailScreen
import cc.orbexa.hhy.order.R16OrderListScreen
import cc.orbexa.hhy.membership.R18MembershipBenefitsScreen
import cc.orbexa.hhy.membership.R18MembershipCenterScreen
import cc.orbexa.hhy.membership.R18MembershipPurchaseScreen
import cc.orbexa.hhy.membership.R18MembershipUpgradeScreen
import cc.orbexa.hhy.prop.R19MyPropsScreen
import cc.orbexa.hhy.prop.R19PropStoreScreen
import cc.orbexa.hhy.prop.R19PropUseScreen
import cc.orbexa.hhy.redpacket.R20RedPacketCampaignListScreen
import cc.orbexa.hhy.redpacket.R20RedPacketCreateScreen
import cc.orbexa.hhy.redpacket.R20RedPacketQuoteScreen
import cc.orbexa.hhy.redpacket.R20RedPacketReviewResultScreen
import cc.orbexa.hhy.redpacket.R21RedPacketDetailScreen
import cc.orbexa.hhy.redpacket.R21RedPacketIncreaseAmountScreen
import cc.orbexa.hhy.redpacket.R22RedPacketHomeScreen
import cc.orbexa.hhy.redpacket.R22RedPacketEligibilityScreen
import cc.orbexa.hhy.redpacket.R22RedPacketTaskScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
                                onUserUpdated = { user ->
                                    sessionState = authenticated.copy(user = user)
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
internal sealed interface AuthenticatedRoute {
    @Serializable data object Home : AuthenticatedRoute
    @Serializable data object Messages : AuthenticatedRoute
    @Serializable data object ChatList : AuthenticatedRoute
    @Serializable data object Notifications : AuthenticatedRoute
    @Serializable data class NotificationDetail(val notificationId: String) : AuthenticatedRoute
    @Serializable data object Announcements : AuthenticatedRoute
    @Serializable data class AnnouncementDetail(val announcementId: String) : AuthenticatedRoute
    @Serializable data object Support : AuthenticatedRoute
    @Serializable data object HelpArticles : AuthenticatedRoute
    @Serializable data class HelpArticleDetail(val articleId: String) : AuthenticatedRoute
    @Serializable data object SupportTickets : AuthenticatedRoute
    @Serializable data object CreateSupportTicket : AuthenticatedRoute
    @Serializable data class SupportTicketDetail(val ticketId: String) : AuthenticatedRoute
    @Serializable data object Orders : AuthenticatedRoute
    @Serializable data class OrderDetail(val orderNo: String) : AuthenticatedRoute
    @Serializable data object Membership : AuthenticatedRoute
    @Serializable data class MembershipPurchase(val skuId: String) : AuthenticatedRoute
    @Serializable data object MembershipUpgrade : AuthenticatedRoute
    @Serializable data object MembershipBenefits : AuthenticatedRoute
    @Serializable data object PropStore : AuthenticatedRoute
    @Serializable data object MyProps : AuthenticatedRoute
    @Serializable data class PropUse(val inventoryId: String) : AuthenticatedRoute
    @Serializable data object RedPacketCampaigns : AuthenticatedRoute
    @Serializable data class RedPacketEditor(val campaignId: String? = null) : AuthenticatedRoute
    @Serializable data class RedPacketReview(val campaignId: String) : AuthenticatedRoute
    @Serializable data class RedPacketQuote(val campaignId: String) : AuthenticatedRoute
    @Serializable data class RedPacketDetail(val campaignId: String) : AuthenticatedRoute
    @Serializable data class RedPacketIncrease(val campaignId: String) : AuthenticatedRoute
    @Serializable data class RedPacketEligibility(val campaignId: String) : AuthenticatedRoute
    @Serializable data class RedPacketTask(val campaignId: String) : AuthenticatedRoute
    @Serializable data object Me : AuthenticatedRoute
    @Serializable data object Profile : AuthenticatedRoute
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
    @Serializable data class TeamLeaderDetail(val teamLeaderId: String) : AuthenticatedRoute
    @Serializable data class TeamLeaderEditor(val teamLeaderId: String? = null) : AuthenticatedRoute
    @Serializable data class ContentManagementDetail(val contentId: String) : AuthenticatedRoute
    @Serializable data object MyContents : AuthenticatedRoute
    @Serializable data object MyDrafts : AuthenticatedRoute
    @Serializable data object Favorites : AuthenticatedRoute
    @Serializable data object History : AuthenticatedRoute
    @Serializable
    data class ChatDetail(
        val conversationId: String,
        val conversationVersion: Long? = null,
        val peerUserId: String? = null,
        val peerNickname: String? = null,
        val peerAvatarUrl: String? = null,
        val peerVerified: Boolean = false,
        val sourceContentId: String? = null,
        val sourceContentType: String? = null,
        val sourceTitle: String? = null,
        val sourceCoverUrl: String? = null,
    ) : AuthenticatedRoute
    @Serializable data class ContentReviews(val contentId: String) : AuthenticatedRoute
    @Serializable data class ContentAnalytics(val contentId: String) : AuthenticatedRoute
    @Serializable data object PublishCenter : AuthenticatedRoute
    @Serializable data class PublishPreview(val contentId: String) : AuthenticatedRoute
    @Serializable
    data class PublishResult(
        val contentId: String,
        val expectedVersion: Long,
        val idempotencyKey: String,
        val title: String,
        val contentType: String,
        val reason: String? = null,
    ) : AuthenticatedRoute
}

@androidx.compose.runtime.Composable
private fun AuthenticatedNavHost(
    authenticated: SessionState.Authenticated,
    authApi: ContractAuthApi,
    identityApi: ContractIdentityApi,
    onSessionInvalidated: () -> Unit,
    onUserUpdated: (UserSelfResource) -> Unit,
) {
    val navController = rememberNavController()
    val experienceApi = remember { UrlConnectionExperienceApi(BuildConfig.API_BASE_URL) }
    val r07Api = remember { UrlConnectionContractR07Api(BuildConfig.API_BASE_URL) }
    val r08Api = remember { UrlConnectionContractR08Api(BuildConfig.API_BASE_URL) }
    val r09Api = remember { UrlConnectionContractR09Api(BuildConfig.API_BASE_URL) }
    val r10Api = remember { UrlConnectionContractR10Api(BuildConfig.API_BASE_URL) }
    val r11Api = remember { UrlConnectionContractR11Api(BuildConfig.API_BASE_URL) }
    val r12Api = remember { UrlConnectionContractR12Api(BuildConfig.API_BASE_URL) }
    val r12MeApi = remember { UrlConnectionContractR12MeApi(BuildConfig.API_BASE_URL) }
    val r12ProfileApi = remember { UrlConnectionContractR12ProfileApi(BuildConfig.API_BASE_URL) }
    val r13Api = remember { UrlConnectionContractR13Api(BuildConfig.API_BASE_URL) }
    val r14Api = remember { UrlConnectionContractR14Api(BuildConfig.API_BASE_URL) }
    val r15Api = remember { UrlConnectionContractR15Api(BuildConfig.API_BASE_URL) }
    val r16Api = remember { UrlConnectionContractR16Api(BuildConfig.API_BASE_URL) }
    val r18MembershipApi = remember { UrlConnectionContractR18MembershipApi(BuildConfig.API_BASE_URL) }
    val r19PropApi = remember { UrlConnectionContractR19PropApi(BuildConfig.API_BASE_URL) }
    val r20RedPacketApi = remember { UrlConnectionContractR20RedPacketApi(BuildConfig.API_BASE_URL) }
    val r14Realtime = remember { OkHttpR14RealtimeClient(BuildConfig.WS_BASE_URL) }
    val mediaApi = remember { UrlConnectionContractMediaApi(BuildConfig.API_BASE_URL) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedTopLevel = when {
        currentBackStackEntry?.destination?.hasRoute<AuthenticatedRoute.Messages>() == true -> HhyTopLevelDestination.MESSAGE
        currentBackStackEntry?.destination?.hasRoute<AuthenticatedRoute.Me>() == true -> HhyTopLevelDestination.ME
        currentBackStackEntry?.destination?.hasRoute<AuthenticatedRoute.RedPacketCampaigns>() == true -> HhyTopLevelDestination.REWARD
        else -> HhyTopLevelDestination.HOME
    }
    DisposableEffect(r14Realtime) {
        onDispose { r14Realtime.close() }
    }
    LaunchedEffect(r14Realtime, authenticated.session.accessToken) {
        val eventCollector = launch {
            r14Realtime.events.collect { event ->
                when (event) {
                    R14RealtimeEvent.SessionInvalidated -> onSessionInvalidated()
                    is R14RealtimeEvent.GapFillRequired -> {
                        if (event.affectedScopes != setOf(R14RealtimeScope.CHAT)) return@collect
                        var retryDelayMillis = 1_000L
                        while (isActive) {
                            when (val result = refreshR14ChatAuthoritatively(
                                api = r14Api,
                                accessToken = authenticated.session.accessToken,
                            )) {
                                R14AuthoritativeRefreshResult.Success -> {
                                    r14Realtime.completeGapFill(
                                        event.resumeFromServerSequence,
                                        setOf(R14RealtimeScope.CHAT),
                                    )
                                    break
                                }
                                is R14AuthoritativeRefreshResult.Failure -> {
                                    if (result.statusCode == 401) {
                                        onSessionInvalidated()
                                        break
                                    }
                                    delay(retryDelayMillis)
                                    retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(30_000L)
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        runCatching { r14Realtime.connect(authenticated.session.accessToken) }
            .onFailure { onSessionInvalidated() }
        try {
            awaitCancellation()
        } finally {
            eventCollector.cancel()
            r14Realtime.disconnect()
        }
    }
    val rootContent: @androidx.compose.runtime.Composable () -> Unit = {
        HhyShellScreen(
            user = authenticated.user,
            selectedDestination = selectedTopLevel,
            onTopLevelSelected = { destination ->
                when (destination) {
                    HhyTopLevelDestination.HOME -> navController.navigate(AuthenticatedRoute.Home) {
                        popUpTo<AuthenticatedRoute.Home> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    HhyTopLevelDestination.ME -> navController.navigate(AuthenticatedRoute.Me) {
                        popUpTo<AuthenticatedRoute.Home> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    HhyTopLevelDestination.MESSAGE -> navController.navigate(AuthenticatedRoute.Messages) {
                        popUpTo<AuthenticatedRoute.Home> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    HhyTopLevelDestination.REWARD -> navController.navigate(AuthenticatedRoute.RedPacketCampaigns) {
                        popUpTo<AuthenticatedRoute.Home> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    else -> Unit
                }
            },
            onOpenSearch = { navController.navigate(AuthenticatedRoute.Search) },
            onOpenProjects = { navController.navigate(AuthenticatedRoute.Projects) },
            onOpenApps = { navController.navigate(AuthenticatedRoute.Apps) },
            onOpenGroups = { navController.navigate(AuthenticatedRoute.Groups) },
            onOpenTeamLeaders = { navController.navigate(AuthenticatedRoute.TeamLeaders) },
            onOpenPublish = { navController.navigate(AuthenticatedRoute.PublishCenter) },
            messageContent = { padding ->
                R14ConversationListScreen(
                    api = r14Api,
                    accessToken = authenticated.session.accessToken,
                    realtimeEvents = r14Realtime.events,
                    contentPadding = padding,
                    onOpenNotifications = { navController.navigate(AuthenticatedRoute.Notifications) },
                    onOpenAnnouncements = { navController.navigate(AuthenticatedRoute.Announcements) },
                    onConversationSelected = { conversation ->
                        chatDetailRoute(conversation)?.let(navController::navigate)
                    },
                    onSessionExpired = onSessionInvalidated,
                )
            },
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
                    target.targetType == "IN_APP_ROUTE" && route.startsWith("/content/team-leader/") ->
                        navController.navigate(AuthenticatedRoute.TeamLeaderDetail(route.substringAfterLast('/')))
                    target.targetType == "IN_APP_ROUTE" && route.startsWith("/messages/chat/") ->
                        chatDetailRoute(route.substringAfterLast('/'))?.let(navController::navigate)
                    target.targetType in setOf("H5_URL", "DOWNLOAD") && isSafeHomeUrl(target.url) ->
                        uriHandler.openUri(target.url.orEmpty())
                }
            },
            onOpenLoginDevices = { navController.navigate(AuthenticatedRoute.LoginDevices) },
            onOpenChangePassword = { navController.navigate(AuthenticatedRoute.ChangePassword) },
            onOpenCancellation = { navController.navigate(AuthenticatedRoute.Cancellation) },
            onOpenIdentity = { navController.navigate(AuthenticatedRoute.Identity) },
            onOpenAbout = { navController.navigate(AuthenticatedRoute.About) },
            onOpenSupport = { navController.navigate(AuthenticatedRoute.Support) },
            onOpenMyContents = { navController.navigate(AuthenticatedRoute.MyContents) },
            onOpenMyDrafts = { navController.navigate(AuthenticatedRoute.MyDrafts) },
            onOpenFavorites = { navController.navigate(AuthenticatedRoute.Favorites) },
            onOpenHistory = { navController.navigate(AuthenticatedRoute.History) },
            onOpenOrders = { navController.navigate(AuthenticatedRoute.Orders) },
            onOpenRedPacketCampaigns = { navController.navigate(AuthenticatedRoute.RedPacketCampaigns) },
            onOpenMembership = { navController.navigate(AuthenticatedRoute.Membership) },
            onOpenMyProps = { navController.navigate(AuthenticatedRoute.MyProps) },
            onOpenPropStore = { navController.navigate(AuthenticatedRoute.PropStore) },
            onOpenProfile = { navController.navigate(AuthenticatedRoute.Profile) },
            buildIdentityLabel = if (BuildConfig.DEBUG) "R22 红包验证包 · ${BuildConfig.VERSION_NAME}" else null,
            experienceApi = experienceApi,
            meApi = r12MeApi,
            accessToken = authenticated.session.accessToken,
            onUserUpdated = onUserUpdated,
            onSessionExpired = onSessionInvalidated,
        )
    }
    NavHost(
        navController = navController,
        startDestination = AuthenticatedRoute.Home,
        enterTransition = { HhyMotion.forwardEnter() },
        exitTransition = { HhyMotion.forwardExit() },
        popEnterTransition = { HhyMotion.backwardEnter() },
        popExitTransition = { HhyMotion.backwardExit() },
    ) {
        composable<AuthenticatedRoute.Home>(
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
        ) { rootContent() }
        composable<AuthenticatedRoute.Me>(
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
        ) { rootContent() }
        composable<AuthenticatedRoute.Messages>(
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
        ) { rootContent() }
        composable<AuthenticatedRoute.ChatList> {
            R14ConversationListScreen(
                api = r14Api,
                accessToken = authenticated.session.accessToken,
                realtimeEvents = r14Realtime.events,
                contentPadding = PaddingValues(),
                onConversationSelected = { conversation -> chatDetailRoute(conversation)?.let(navController::navigate) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Notifications> {
            R15NotificationListScreen(
                r15Api, authenticated.session.accessToken, false,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.NotificationDetail(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.NotificationDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.NotificationDetail>()
            R15NotificationDetailScreen(r15Api, authenticated.session.accessToken, route.notificationId, false, { navController.popBackStack() }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.Announcements> {
            R15NotificationListScreen(
                r15Api, authenticated.session.accessToken, true,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.AnnouncementDetail(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.AnnouncementDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.AnnouncementDetail>()
            R15NotificationDetailScreen(r15Api, authenticated.session.accessToken, route.announcementId, true, { navController.popBackStack() }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.Support> {
            R15SupportHomeScreen(
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.HelpArticles) },
                { navController.navigate(AuthenticatedRoute.SupportTickets) },
                { navController.navigate(AuthenticatedRoute.CreateSupportTicket) },
            )
        }
        composable<AuthenticatedRoute.HelpArticles> {
            R15SupportListScreen(
                r15Api, authenticated.session.accessToken, true,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.HelpArticleDetail(it)) },
                { navController.navigate(AuthenticatedRoute.CreateSupportTicket) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.HelpArticleDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.HelpArticleDetail>()
            R15SupportDetailScreen(r15Api, authenticated.session.accessToken, route.articleId, true, { navController.popBackStack() }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.SupportTickets> {
            R15SupportListScreen(
                r15Api, authenticated.session.accessToken, false,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.SupportTicketDetail(it)) },
                { navController.navigate(AuthenticatedRoute.CreateSupportTicket) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.CreateSupportTicket> {
            R15CreateTicketScreen(
                r15Api, authenticated.session.accessToken,
                { navController.popBackStack() },
                { id -> navController.navigate(AuthenticatedRoute.SupportTicketDetail(id)) { popUpTo<AuthenticatedRoute.CreateSupportTicket> { inclusive = true } } },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.SupportTicketDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.SupportTicketDetail>()
            R15SupportDetailScreen(r15Api, authenticated.session.accessToken, route.ticketId, false, { navController.popBackStack() }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.Orders> {
            R16OrderListScreen(r16Api, authenticated.session.accessToken, { navController.popBackStack() }, { navController.navigate(AuthenticatedRoute.OrderDetail(it)) }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.OrderDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.OrderDetail>()
            R16OrderDetailScreen(r16Api, authenticated.session.accessToken, route.orderNo, { navController.popBackStack() }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.Membership> {
            R18MembershipCenterScreen(
                r18MembershipApi, authenticated.session.accessToken,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.MembershipPurchase(it)) },
                { navController.navigate(AuthenticatedRoute.MembershipUpgrade) },
                { navController.navigate(AuthenticatedRoute.MembershipBenefits) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MembershipPurchase> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.MembershipPurchase>()
            R18MembershipPurchaseScreen(
                r18MembershipApi, authenticated.session.accessToken, route.skuId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.Orders) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MembershipUpgrade> {
            R18MembershipUpgradeScreen(
                r18MembershipApi, authenticated.session.accessToken,
                { navController.popBackStack() }, onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MembershipBenefits> {
            R18MembershipBenefitsScreen(
                r18MembershipApi, authenticated.session.accessToken,
                { navController.popBackStack() }, onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.PropStore> {
            R19PropStoreScreen(
                r19PropApi, authenticated.session.accessToken,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.MyProps) },
                { navController.navigate(AuthenticatedRoute.Orders) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MyProps> {
            R19MyPropsScreen(
                r19PropApi, authenticated.session.accessToken,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.PropStore) },
                { navController.navigate(AuthenticatedRoute.PropUse(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.PropUse> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.PropUse>()
            R19PropUseScreen(
                r19PropApi, r12Api, authenticated.session.accessToken, route.inventoryId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.MyProps) {
                    popUpTo<AuthenticatedRoute.MyProps> { inclusive = true }
                } },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketCampaigns> {
            R22RedPacketHomeScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.RedPacketEligibility(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketEligibility> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketEligibility>()
            R22RedPacketEligibilityScreen(
                r20RedPacketApi, authenticated.session.accessToken, route.campaignId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.RedPacketTask(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketTask> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketTask>()
            R22RedPacketTaskScreen(
                r20RedPacketApi, r12MeApi, authenticated.session.accessToken, route.campaignId,
                { navController.popBackStack() },
                { navController.popBackStack<AuthenticatedRoute.RedPacketCampaigns>(false) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketDetail> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketDetail>()
            R21RedPacketDetailScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                route.campaignId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.RedPacketIncrease(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketIncrease> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketIncrease>()
            R21RedPacketIncreaseAmountScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                route.campaignId,
                { navController.popBackStack() },
                { navController.popBackStack() },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketEditor> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketEditor>()
            R20RedPacketCreateScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                route.campaignId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.RedPacketReview(it)) {
                    popUpTo<AuthenticatedRoute.RedPacketCampaigns>()
                } },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketReview> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketReview>()
            R20RedPacketReviewResultScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                route.campaignId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.RedPacketQuote(it)) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.RedPacketQuote> { entry ->
            val route = entry.toRoute<AuthenticatedRoute.RedPacketQuote>()
            R20RedPacketQuoteScreen(
                r20RedPacketApi,
                authenticated.session.accessToken,
                route.campaignId,
                { navController.popBackStack() },
                { navController.navigate(AuthenticatedRoute.Orders) },
                onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Profile> {
            R12ProfileScreen(
                api = r12ProfileApi,
                mediaApi = mediaApi,
                accessToken = authenticated.session.accessToken,
                initialUser = authenticated.user,
                onBack = { navController.popBackStack() },
                onUserUpdated = onUserUpdated,
                onSessionExpired = onSessionInvalidated,
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
        composable<AuthenticatedRoute.ChatDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ChatDetail>()
            if (!isValidChatConversationId(route.conversationId)) {
                LaunchedEffect(route.conversationId) {
                    if (!navController.popBackStack()) navController.navigate(AuthenticatedRoute.Home)
                }
            } else {
                val peer = route.peerUserId?.let { userId ->
                    route.peerNickname?.let { nickname ->
                        PublisherSummaryResource(
                            userId = userId,
                            nickname = nickname,
                            avatarUrl = route.peerAvatarUrl,
                            verified = route.peerVerified,
                        )
                    }
                }
                val content = runCatching {
                    val id = requireNotNull(route.sourceContentId)
                    val type = requireNotNull(route.sourceContentType)
                    val title = requireNotNull(route.sourceTitle)
                    ChatContentCardPayload(id, type, title, route.sourceCoverUrl)
                }.getOrNull()
                R14ChatDetailScreen(
                    api = r14Api,
                    mediaApi = mediaApi,
                    accessToken = authenticated.session.accessToken,
                    conversationId = route.conversationId,
                    conversationVersion = route.conversationVersion?.takeIf { it >= 0 },
                    currentUserId = authenticated.user.id,
                    initialPeer = peer,
                    initialContentCard = content,
                    realtimeEvents = r14Realtime.events,
                    onBack = { navController.popBackStack() },
                    onOpenContent = { contentType, contentId ->
                        when (contentType) {
                            "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectDetail(contentId))
                            "APP" -> navController.navigate(AuthenticatedRoute.AppDetail(contentId))
                            "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupDetail(contentId))
                            "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderDetail(contentId))
                        }
                    },
                    onSessionExpired = onSessionInvalidated,
                    onConversationUnavailable = {
                        if (!navController.popBackStack()) navController.navigate(AuthenticatedRoute.Home)
                    },
                )
            }
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
            var actionSheet by remember(route.projectId) { mutableStateOf<R13ContentSheet?>(null) }
            var actionTitle by remember(route.projectId) { mutableStateOf("") }
            var reportVersion by remember(route.projectId) { mutableStateOf<Long?>(null) }
            var feedbackChannels by remember(route.projectId) { mutableStateOf(emptyList<String>()) }
            R08ProjectDetailScreen(
                api = r08Api,
                accessToken = authenticated.session.accessToken,
                projectId = route.projectId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.ProjectEditor(it)) },
                onConversationReady = { conversationId, conversationVersion, content ->
                    chatDetailRoute(conversationId, content, conversationVersion)?.let(navController::navigate)
                },
                onShare = { actionTitle = it; actionSheet = R13ContentSheet.SHARE },
                onInvalidFeedback = { title, channels -> actionTitle = title; feedbackChannels = channels; actionSheet = R13ContentSheet.INVALID_FEEDBACK },
                onReport = { title, version -> actionTitle = title; reportVersion = version; actionSheet = R13ContentSheet.REPORT },
                onSessionExpired = onSessionInvalidated,
            )
            R13DetailActionHost(actionSheet, r13Api, r15Api, authenticated.session.accessToken, route.projectId, actionTitle, feedbackChannels, reportVersion, { actionSheet = null }, onSessionInvalidated)
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
                    navController.navigate(AuthenticatedRoute.PublishPreview(id))
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
            var actionSheet by remember(route.appId) { mutableStateOf<R13ContentSheet?>(null) }
            var actionTitle by remember(route.appId) { mutableStateOf("") }
            var reportVersion by remember(route.appId) { mutableStateOf<Long?>(null) }
            var feedbackChannels by remember(route.appId) { mutableStateOf(emptyList<String>()) }
            R09AppDetailScreen(
                api = r09Api,
                accessToken = authenticated.session.accessToken,
                appId = route.appId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.AppEditor(it)) },
                onConversationReady = { conversationId, conversationVersion, content ->
                    chatDetailRoute(conversationId, content, conversationVersion)?.let(navController::navigate)
                },
                onShare = { actionTitle = it; actionSheet = R13ContentSheet.SHARE },
                onInvalidFeedback = { title, channels -> actionTitle = title; feedbackChannels = channels; actionSheet = R13ContentSheet.INVALID_FEEDBACK },
                onReport = { title, version -> actionTitle = title; reportVersion = version; actionSheet = R13ContentSheet.REPORT },
                onSessionExpired = onSessionInvalidated,
            )
            R13DetailActionHost(actionSheet, r13Api, r15Api, authenticated.session.accessToken, route.appId, actionTitle, feedbackChannels, reportVersion, { actionSheet = null }, onSessionInvalidated)
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
                    navController.navigate(AuthenticatedRoute.PublishPreview(id))
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
            var actionSheet by remember(route.groupId) { mutableStateOf<R13ContentSheet?>(null) }
            var actionTitle by remember(route.groupId) { mutableStateOf("") }
            var reportVersion by remember(route.groupId) { mutableStateOf<Long?>(null) }
            var feedbackChannels by remember(route.groupId) { mutableStateOf(emptyList<String>()) }
            R10GroupDetailScreen(
                api = r10Api,
                accessToken = authenticated.session.accessToken,
                groupId = route.groupId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.GroupEditor(it)) },
                onConversationReady = { conversationId, conversationVersion, content ->
                    chatDetailRoute(conversationId, content, conversationVersion)?.let(navController::navigate)
                },
                onShare = { actionTitle = it; actionSheet = R13ContentSheet.SHARE },
                onInvalidFeedback = { title, channels -> actionTitle = title; feedbackChannels = channels; actionSheet = R13ContentSheet.INVALID_FEEDBACK },
                onReport = { title, version -> actionTitle = title; reportVersion = version; actionSheet = R13ContentSheet.REPORT },
                onSessionExpired = onSessionInvalidated,
            )
            R13DetailActionHost(actionSheet, r13Api, r15Api, authenticated.session.accessToken, route.groupId, actionTitle, feedbackChannels, reportVersion, { actionSheet = null }, onSessionInvalidated)
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
                    navController.navigate(AuthenticatedRoute.PublishPreview(id))
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.TeamLeaders> {
            R11TeamLeaderListScreen(
                api = r11Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onTeamLeaderSelected = { id -> navController.navigate(AuthenticatedRoute.TeamLeaderDetail(id)) },
                onCreateTeamLeader = { navController.navigate(AuthenticatedRoute.TeamLeaderEditor()) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.TeamLeaderDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.TeamLeaderDetail>()
            var actionSheet by remember(route.teamLeaderId) { mutableStateOf<R13ContentSheet?>(null) }
            var actionTitle by remember(route.teamLeaderId) { mutableStateOf("") }
            var reportVersion by remember(route.teamLeaderId) { mutableStateOf<Long?>(null) }
            var feedbackChannels by remember(route.teamLeaderId) { mutableStateOf(emptyList<String>()) }
            R11TeamLeaderDetailScreen(
                api = r11Api,
                accessToken = authenticated.session.accessToken,
                teamLeaderId = route.teamLeaderId,
                currentUserId = authenticated.user.id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(AuthenticatedRoute.TeamLeaderEditor(it)) },
                onConversationReady = { conversationId, conversationVersion, content ->
                    chatDetailRoute(conversationId, content, conversationVersion)?.let(navController::navigate)
                },
                onShare = { actionTitle = it; actionSheet = R13ContentSheet.SHARE },
                onInvalidFeedback = { title, channels -> actionTitle = title; feedbackChannels = channels; actionSheet = R13ContentSheet.INVALID_FEEDBACK },
                onReport = { title, version -> actionTitle = title; reportVersion = version; actionSheet = R13ContentSheet.REPORT },
                onSessionExpired = onSessionInvalidated,
            )
            R13DetailActionHost(actionSheet, r13Api, r15Api, authenticated.session.accessToken, route.teamLeaderId, actionTitle, feedbackChannels, reportVersion, { actionSheet = null }, onSessionInvalidated)
        }
        composable<AuthenticatedRoute.TeamLeaderEditor> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.TeamLeaderEditor>()
            R11TeamLeaderEditorScreen(
                api = r11Api,
                mediaApi = mediaApi,
                accessToken = authenticated.session.accessToken,
                teamLeaderId = route.teamLeaderId,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(AuthenticatedRoute.PublishPreview(id))
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.ContentManagementDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ContentManagementDetail>()
            R12ContentManagementDetailScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                contentId = route.contentId,
                currentUserId = authenticated.user.id,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onDraftCreated = { draftId ->
                    navController.navigate(AuthenticatedRoute.ContentManagementDetail(draftId)) {
                        popUpTo<AuthenticatedRoute.ContentManagementDetail> { inclusive = true }
                    }
                },
                onPreview = { navController.navigate(AuthenticatedRoute.PublishPreview(it)) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.PublishCenter> {
            R12PublishCenterScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                user = authenticated.user,
                onBack = { navController.popBackStack() },
                onOpenIdentity = { navController.navigate(AuthenticatedRoute.Identity) },
                onOpenMyContents = { navController.navigate(AuthenticatedRoute.MyContents) },
                onCreateContent = { contentType ->
                    when (contentType) {
                        "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectEditor())
                        "APP" -> navController.navigate(AuthenticatedRoute.AppEditor())
                        "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupEditor())
                        "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderEditor())
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.PublishPreview> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.PublishPreview>()
            R12PublishPreviewScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                contentId = route.contentId,
                user = authenticated.user,
                onBack = { navController.popBackStack() },
                onEdit = { contentType, contentId ->
                    when (contentType) {
                        "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectEditor(contentId))
                        "APP" -> navController.navigate(AuthenticatedRoute.AppEditor(contentId))
                        "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupEditor(contentId))
                        "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderEditor(contentId))
                    }
                },
                onConfirmSubmit = { content, idempotencyKey ->
                    navController.navigate(
                        AuthenticatedRoute.PublishResult(
                            contentId = content.id,
                            expectedVersion = content.version,
                            idempotencyKey = idempotencyKey,
                            title = content.title,
                            contentType = content.contentType,
                        ),
                    )
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.PublishResult> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.PublishResult>()
            R12PublishResultScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                contentId = route.contentId,
                expectedVersion = route.expectedVersion,
                idempotencyKey = route.idempotencyKey,
                initialTitle = route.title,
                initialContentType = route.contentType,
                reason = route.reason,
                onBack = { navController.popBackStack() },
                onReturnPreview = {
                    navController.navigate(AuthenticatedRoute.PublishPreview(route.contentId)) {
                        popUpTo<AuthenticatedRoute.PublishResult> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenMyContents = {
                    navController.navigate(AuthenticatedRoute.MyContents) {
                        launchSingleTop = true
                    }
                },
                onBackToPublishCenter = {
                    navController.navigate(AuthenticatedRoute.PublishCenter) {
                        launchSingleTop = true
                    }
                },
                onEdit = { contentType, contentId ->
                    when (contentType) {
                        "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectEditor(contentId))
                        "APP" -> navController.navigate(AuthenticatedRoute.AppEditor(contentId))
                        "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupEditor(contentId))
                        "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderEditor(contentId))
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MyContents> {
            R12MyContentsScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                identityVerified = authenticated.user.identityStatus == "VERIFIED",
                onBack = { navController.popBackStack() },
                onContentSelected = { navController.navigate(AuthenticatedRoute.ContentManagementDetail(it)) },
                onCreateContent = { contentType ->
                    when (contentType) {
                        "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectEditor())
                        "APP" -> navController.navigate(AuthenticatedRoute.AppEditor())
                        "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupEditor())
                        "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderEditor())
                    }
                },
                onReviews = { navController.navigate(AuthenticatedRoute.ContentReviews(it)) },
                onAnalytics = { navController.navigate(AuthenticatedRoute.ContentAnalytics(it)) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.MyDrafts> {
            R12DraftsScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onContentSelected = { navController.navigate(AuthenticatedRoute.ContentManagementDetail(it)) },
                onEdit = { contentType, contentId ->
                    when (contentType) {
                        "PROJECT" -> navController.navigate(AuthenticatedRoute.ProjectEditor(contentId))
                        "APP" -> navController.navigate(AuthenticatedRoute.AppEditor(contentId))
                        "GROUP_CHAT" -> navController.navigate(AuthenticatedRoute.GroupEditor(contentId))
                        "TEAM_LEADER" -> navController.navigate(AuthenticatedRoute.TeamLeaderEditor(contentId))
                    }
                },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.Favorites> {
            R13FavoritesScreen(
                api = r13Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onContentSelected = { item -> navController.openR13Content(item.contentType, item.id) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.History> {
            R13HistoryScreen(
                api = r13Api,
                accessToken = authenticated.session.accessToken,
                onBack = { navController.popBackStack() },
                onContentSelected = { item -> navController.openR13Content(item.contentType, item.id) },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.ContentReviews> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ContentReviews>()
            R12ContentReviewsScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                contentId = route.contentId,
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionInvalidated,
            )
        }
        composable<AuthenticatedRoute.ContentAnalytics> { backStackEntry ->
            val route = backStackEntry.toRoute<AuthenticatedRoute.ContentAnalytics>()
            R12ContentAnalyticsScreen(
                api = r12Api,
                accessToken = authenticated.session.accessToken,
                contentId = route.contentId,
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionInvalidated,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun R13DetailActionHost(
    sheet: R13ContentSheet?,
    api: ContractR13Api,
    r15Api: cc.orbexa.hhy.network.ContractR15Api,
    accessToken: String,
    contentId: String,
    contentTitle: String,
    feedbackChannels: List<String>,
    expectedVersion: Long?,
    dismiss: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var completionNotice by remember(contentId) { mutableStateOf<String?>(null) }
    if (sheet == R13ContentSheet.REPORT) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = dismiss) {
            R15ReportContentSheet(
                api = r15Api,
                accessToken = accessToken,
                contentId = contentId,
                expectedVersion = expectedVersion,
                onDismiss = dismiss,
                onCompleted = { completionNotice = "举报已提交"; dismiss() },
                onSessionExpired = onSessionExpired,
            )
        }
    } else sheet?.let {
        R13ContentActionSheet(
            sheet = it,
            api = api,
            accessToken = accessToken,
            contentId = contentId,
            contentTitle = contentTitle,
            feedbackChannels = feedbackChannels,
            onDismiss = dismiss,
            onCompleted = { message -> completionNotice = message; dismiss() },
            onSessionExpired = onSessionExpired,
        )
    }
    completionNotice?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { completionNotice = null },
            title = { Text("操作成功") },
            text = { Text(message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { completionNotice = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

private fun NavHostController.openR13Content(contentType: String, contentId: String) {
    when (contentType) {
        "PROJECT" -> navigate(AuthenticatedRoute.ProjectDetail(contentId))
        "APP" -> navigate(AuthenticatedRoute.AppDetail(contentId))
        "GROUP_CHAT" -> navigate(AuthenticatedRoute.GroupDetail(contentId))
        "TEAM_LEADER" -> navigate(AuthenticatedRoute.TeamLeaderDetail(contentId))
    }
}

private fun canOpenHomeTarget(target: HomeNavigationTargetSnapshot): Boolean {
    val route = target.route.orEmpty()
    return when (target.targetType) {
        "IN_APP_ROUTE" -> route in setOf("/search", "/content/projects", "/content/apps", "/content/groups", "/content/team-leaders") ||
            (route.startsWith("/content/project/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/content/app/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/content/group/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/content/team-leader/") && route.substringAfterLast('/').isNotBlank()) ||
            (route.startsWith("/messages/chat/") && isValidChatConversationId(route.substringAfterLast('/')))
        "H5_URL", "DOWNLOAD" -> isSafeHomeUrl(target.url)
        else -> false
    }
}

internal fun chatDetailRoute(
    conversationId: String,
    content: ContentResource? = null,
    conversationVersion: Long? = null,
): AuthenticatedRoute.ChatDetail? {
    if (!isValidChatConversationId(conversationId)) return null
    val publisher = content?.publisher
    val cover = content?.media?.firstOrNull()?.let { it.thumbnailUrl ?: it.url }?.takeIf(::isSafeHomeUrl)
    return AuthenticatedRoute.ChatDetail(
        conversationId = conversationId,
        conversationVersion = conversationVersion?.takeIf { it >= 0 },
        peerUserId = publisher?.userId,
        peerNickname = publisher?.nickname,
        peerAvatarUrl = publisher?.avatarUrl?.takeIf(::isSafeHomeUrl),
        peerVerified = publisher?.verified ?: false,
        sourceContentId = content?.id,
        sourceContentType = content?.contentType,
        sourceTitle = content?.title?.take(255),
        sourceCoverUrl = cover,
    )
}

internal fun chatDetailRoute(conversation: ChatConversationResource): AuthenticatedRoute.ChatDetail? {
    val peer = conversation.peer ?: return null
    if (!isValidChatConversationId(conversation.id)) return null
    return AuthenticatedRoute.ChatDetail(
        conversationId = conversation.id,
        conversationVersion = conversation.version.takeIf { it >= 0 },
        peerUserId = peer.userId,
        peerNickname = peer.nickname,
        peerAvatarUrl = peer.avatarUrl?.takeIf(::isSafeHomeUrl),
        peerVerified = peer.verified,
    )
}

internal fun isValidChatConversationId(value: String): Boolean =
    Regex("^[A-Za-z0-9_-]{1,64}$").matches(value)

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
