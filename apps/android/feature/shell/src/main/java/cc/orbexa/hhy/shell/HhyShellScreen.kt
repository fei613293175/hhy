package cc.orbexa.hhy.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ExperienceApi
import cc.orbexa.hhy.network.HomeModuleItemSnapshot
import cc.orbexa.hhy.network.HomeModuleSnapshot
import cc.orbexa.hhy.network.HomeNavigationTargetSnapshot
import cc.orbexa.hhy.network.HomeSnapshot
import cc.orbexa.hhy.network.MembershipResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.RewardAccountResource
import cc.orbexa.hhy.network.UserSelfResource
import java.time.Instant

enum class HhyTopLevelDestination { HOME, REWARD, PUBLISH, MESSAGE, ME }

private data class NavigationItem(
    val destination: HhyTopLevelDestination,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
)
private data class HomeCategory(
    val title: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: (() -> Unit)?,
)

private val navigationItems = listOf(
    NavigationItem(HhyTopLevelDestination.HOME, "首页", HhyIcons.Home, isTopLevelDestinationEnabled(HhyTopLevelDestination.HOME)),
    NavigationItem(HhyTopLevelDestination.REWARD, "红包", HhyIcons.Reward, isTopLevelDestinationEnabled(HhyTopLevelDestination.REWARD)),
    NavigationItem(HhyTopLevelDestination.PUBLISH, "发布", HhyIcons.Publish, isTopLevelDestinationEnabled(HhyTopLevelDestination.PUBLISH)),
    NavigationItem(HhyTopLevelDestination.MESSAGE, "消息", HhyIcons.Message, isTopLevelDestinationEnabled(HhyTopLevelDestination.MESSAGE)),
    NavigationItem(HhyTopLevelDestination.ME, "我的", HhyIcons.Profile, isTopLevelDestinationEnabled(HhyTopLevelDestination.ME)),
)

internal fun isTopLevelDestinationEnabled(destination: HhyTopLevelDestination): Boolean =
    destination in setOf(
        HhyTopLevelDestination.HOME,
        HhyTopLevelDestination.PUBLISH,
        HhyTopLevelDestination.MESSAGE,
        HhyTopLevelDestination.ME,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HhyShellScreen(
    user: UserSelfResource,
    selectedDestination: HhyTopLevelDestination = HhyTopLevelDestination.HOME,
    onTopLevelSelected: (HhyTopLevelDestination) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenProjects: (() -> Unit)? = null,
    onOpenApps: (() -> Unit)? = null,
    onOpenGroups: (() -> Unit)? = null,
    onOpenTeamLeaders: (() -> Unit)? = null,
    onOpenPublish: () -> Unit = {},
    messageContent: @Composable (PaddingValues) -> Unit = {},
    canOpenHomeTarget: (HomeNavigationTargetSnapshot) -> Boolean = { false },
    onOpenHomeTarget: (HomeNavigationTargetSnapshot) -> Unit = {},
    onOpenLoginDevices: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    onOpenCancellation: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenMyContents: () -> Unit = {},
    onOpenMyDrafts: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenOrders: () -> Unit = {},
    onOpenMembership: () -> Unit = {},
    onOpenMyProps: () -> Unit = {},
    onOpenPropStore: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    experienceApi: ExperienceApi? = null,
    meApi: ContractR12MeApi? = null,
    accessToken: String = "",
    onUserUpdated: (UserSelfResource) -> Unit = {},
    onSessionExpired: () -> Unit = {},
) {
    var home by androidx.compose.runtime.remember { mutableStateOf<HomeSnapshot?>(null) }
    var homeError by androidx.compose.runtime.remember { mutableStateOf(false) }
    var refreshing by androidx.compose.runtime.remember { mutableStateOf(false) }
    var homeRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var userState by androidx.compose.runtime.remember(user.id) {
        mutableStateOf(R12MeModuleState.cached(user))
    }
    var membershipState by androidx.compose.runtime.remember(user.id) {
        mutableStateOf(R12MeModuleState<MembershipResource>())
    }
    var rewardState by androidx.compose.runtime.remember(user.id) {
        mutableStateOf(R12MeModuleState<RewardAccountResource>())
    }
    var userRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var membershipRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var rewardRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var sessionExpiredHandled by androidx.compose.runtime.remember(user.id) { mutableStateOf(false) }

    LaunchedEffect(experienceApi, accessToken, homeRefreshKey, selectedDestination) {
        if (selectedDestination != HhyTopLevelDestination.HOME) return@LaunchedEffect
        val api = experienceApi ?: return@LaunchedEffect
        refreshing = true
        api.home(accessToken)
            .onSuccess { home = it; homeError = false }
            .onFailure { homeError = true }
        refreshing = false
    }

    LaunchedEffect(meApi, accessToken, userRefreshKey, selectedDestination) {
        if (selectedDestination != HhyTopLevelDestination.ME) return@LaunchedEffect
        val api = meApi ?: return@LaunchedEffect
        userState = userState.refreshing()
        val first = api.user(accessToken)
        val result = if (first is R07CallResult.Failure && first.statusCode == 409) api.user(accessToken) else first
        when (result) {
            is R07CallResult.Success -> {
                userState = userState.loaded(result.data, result.timestamp, Instant.now().toString())
                onUserUpdated(result.data)
            }
            is R07CallResult.Failure -> if (result.statusCode == 401) {
                if (!sessionExpiredHandled) {
                    sessionExpiredHandled = true
                    onSessionExpired()
                }
            } else {
                userState = userState.failed(R12MeModuleKind.USER, result)
            }
        }
    }

    LaunchedEffect(meApi, accessToken, membershipRefreshKey, selectedDestination) {
        if (selectedDestination != HhyTopLevelDestination.ME) return@LaunchedEffect
        val api = meApi ?: return@LaunchedEffect
        membershipState = membershipState.refreshing()
        val first = api.membership(accessToken)
        val result = if (first is R07CallResult.Failure && first.statusCode == 409) api.membership(accessToken) else first
        when (result) {
            is R07CallResult.Success -> membershipState = membershipState.loaded(
                result.data,
                result.timestamp,
                Instant.now().toString(),
            )
            is R07CallResult.Failure -> if (result.statusCode == 401) {
                if (!sessionExpiredHandled) {
                    sessionExpiredHandled = true
                    onSessionExpired()
                }
            } else {
                membershipState = membershipState.failed(R12MeModuleKind.MEMBERSHIP, result)
            }
        }
    }

    LaunchedEffect(meApi, accessToken, rewardRefreshKey, selectedDestination) {
        if (selectedDestination != HhyTopLevelDestination.ME) return@LaunchedEffect
        val api = meApi ?: return@LaunchedEffect
        rewardState = rewardState.refreshing()
        val first = api.rewardAccount(accessToken)
        val result = if (first is R07CallResult.Failure && first.statusCode == 409) api.rewardAccount(accessToken) else first
        when (result) {
            is R07CallResult.Success -> rewardState = rewardState.loaded(
                result.data,
                result.timestamp,
                Instant.now().toString(),
            )
            is R07CallResult.Failure -> if (result.statusCode == 401) {
                if (!sessionExpiredHandled) {
                    sessionExpiredHandled = true
                    onSessionExpired()
                }
            } else {
                rewardState = rewardState.failed(R12MeModuleKind.REWARD, result)
            }
        }
    }

    val screenMarker = when {
        selectedDestination == HhyTopLevelDestination.HOME && home != null -> "hhy.screen.r06.home.loaded"
        selectedDestination == HhyTopLevelDestination.HOME && homeError -> "hhy.screen.r06.home.error"
        selectedDestination == HhyTopLevelDestination.HOME -> "hhy.screen.r06.home.loading"
        selectedDestination == HhyTopLevelDestination.MESSAGE -> "hhy.screen.r14.messages"
        else -> "hhy.screen.r12.me"
    }
    val categories = listOf(
        HomeCategory("项目", HhyIcons.Projects, "home.category.project", onOpenProjects),
        HomeCategory("App", HhyIcons.Applications, "home.category.app", onOpenApps),
        HomeCategory("群聊", HhyIcons.Groups, "home.category.group", onOpenGroups),
        HomeCategory("团队长", HhyIcons.Profile, "home.category.team-leader", onOpenTeamLeaders),
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("hhy.shell.authenticated"),
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(screenMarker),
        topBar = {
            if (selectedDestination == HhyTopLevelDestination.HOME) TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                            HhyIcon(
                                HhyIcons.Groups,
                                contentDescription = null,
                                modifier = Modifier.padding(HhySpacing.Sm),
                                tint = HhyColors.BrandPrimary,
                            )
                        }
                        Text(
                            "合伙云 Pro",
                            modifier = Modifier.padding(start = HhySpacing.Sm),
                            color = HhyColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f)
                            .testTag("shell.navigation.${item.destination.name.lowercase()}"),
                        selected = selectedDestination == item.destination,
                        enabled = item.enabled,
                        onClick = {
                            if (item.destination == HhyTopLevelDestination.PUBLISH) {
                                onOpenPublish()
                            } else {
                                onTopLevelSelected(item.destination)
                            }
                        },
                        icon = { HhyIcon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        AnimatedContent(
            targetState = selectedDestination,
            transitionSpec = { HhyMotion.peerContent() },
            label = "authenticated-top-level",
        ) { destination ->
            if (destination == HhyTopLevelDestination.HOME) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                item { HomeSearch(onOpenSearch) }

                val noticeModules = home?.modules.orEmpty().filter { it.type == "NOTICE" }
                noticeModules.forEach { module ->
                    item { HomeNoticeModule(module, canOpenHomeTarget, onOpenHomeTarget) }
                }

                val bannerModules = home?.modules.orEmpty().filter { it.type == "BANNER" }
                bannerModules.forEach { module ->
                    item { HomeBannerModule(module, canOpenHomeTarget, onOpenHomeTarget) }
                }

                item { HomeCategoryGrid(categories) }

                val contentModules = home?.modules.orEmpty().filterNot { it.type in setOf("NOTICE", "BANNER") }
                if (contentModules.isNotEmpty()) {
                    contentModules.forEach { module ->
                        item { HomeContentModule(module, canOpenHomeTarget, onOpenHomeTarget) }
                    }
                } else {
                    item {
                        HomeEmptyState(
                            homeError = homeError,
                            refreshing = refreshing,
                            onRetry = { homeRefreshKey += 1 },
                        )
                    }
                }
                }
            } else if (destination == HhyTopLevelDestination.MESSAGE) {
                messageContent(padding)
            } else {
                R12MeHomeScreen(
                    user = userState,
                    membership = membershipState,
                    reward = rewardState,
                    contentPadding = padding,
                    onRefreshAll = {
                        userRefreshKey += 1
                        membershipRefreshKey += 1
                        rewardRefreshKey += 1
                    },
                    onRetryUser = { userRefreshKey += 1 },
                    onRetryMembership = { membershipRefreshKey += 1 },
                    onRetryReward = { rewardRefreshKey += 1 },
                    onOpenProfile = onOpenProfile,
                    onOpenMyContents = onOpenMyContents,
                    onOpenMyDrafts = onOpenMyDrafts,
                    onOpenFavorites = onOpenFavorites,
                    onOpenHistory = onOpenHistory,
                    onOpenOrders = onOpenOrders,
                    onOpenMembership = onOpenMembership,
                    onOpenMyProps = onOpenMyProps,
                    onOpenPropStore = onOpenPropStore,
                    onOpenIdentity = onOpenIdentity,
                    onOpenLoginDevices = onOpenLoginDevices,
                    onOpenChangePassword = onOpenChangePassword,
                    onOpenAbout = onOpenAbout,
                    onOpenSupport = onOpenSupport,
                    onOpenCancellation = onOpenCancellation,
                )
            }
        }
    }
    }
}

@Composable
private fun MineProfileHeader(user: UserSelfResource, onOpenProfile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).testTag("mine.profile"),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(HhySize.AppLogo).clip(CircleShape),
                shape = CircleShape,
                color = HhyColors.SoftBlue,
            ) {
                if (secureProfileAvatarUrl(user.avatarUrl) != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "个人头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        HhyIcon(HhyIcons.Profile, null, tint = HhyColors.BrandPrimary)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(
                    user.nickname?.takeIf(String::isNotBlank) ?: "未设置昵称",
                    fontWeight = FontWeight.SemiBold,
                    color = HhyColors.TextPrimary,
                )
                user.phoneMasked?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = HhyColors.TextSecondary)
                }
                user.bio?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = HhyColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
            }
            HhyIcon(HhyIcons.ChevronRight, contentDescription = "编辑个人资料", tint = HhyColors.TextTertiary)
        }
    }
}

@Composable
private fun MineContentManagementCard(
    onOpenMyContents: () -> Unit,
    onOpenMyDrafts: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text("发布管理", fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
            Text("查看发布状态、继续编辑草稿和跟进内容数据", color = HhyColors.TextSecondary)
            MineManagementRow(
                title = "我的发布",
                subtitle = "管理审核中、已上架与已下架内容",
                icon = HhyIcons.Publish,
                testTag = "mine.my-contents",
                onClick = onOpenMyContents,
            )
            MineManagementRow(
                title = "草稿箱",
                subtitle = "继续编辑或删除尚未提交的内容",
                icon = HhyIcons.Applications,
                testTag = "mine.my-drafts",
                onClick = onOpenMyDrafts,
            )
        }
    }
}

@Composable
private fun MineManagementRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HhyRadius.Button))
            .clickable(onClick = onClick).testTag(testTag),
        color = HhyColors.PageBackground,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SoftBlue) {
                HhyIcon(icon, null, Modifier.padding(HhySpacing.Sm), HhyColors.BrandPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
            }
            HhyIcon(HhyIcons.ChevronRight, null, tint = HhyColors.TextTertiary)
        }
    }
}

@Composable
private fun HomeSearch(onSearch: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = HhySize.InputHeight)
            .clip(RoundedCornerShape(HhyRadius.Pill))
            .clickable(onClick = onSearch)
            .testTag("home.search"),
        color = HhyColors.Surface,
        shadowElevation = HhyElevation.Card,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhyIcon(HhyIcons.Search, contentDescription = null, tint = HhyColors.TextSecondary)
            Text(
                "搜索项目 / App / 群聊 / 团队长",
                modifier = Modifier.padding(start = HhySpacing.Sm),
                color = HhyColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HomeCategoryGrid(categories: List<HomeCategory>) {
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        HomeSectionHeading("四大分类", "发现真实合作内容")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HhyRadius.NormalCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                categories.forEach { category ->
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(HhyRadius.Button))
                            .clickable(enabled = category.onClick != null) { category.onClick?.invoke() }
                            .padding(vertical = HhySpacing.Sm)
                            .testTag(category.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        Surface(shape = RoundedCornerShape(HhyRadius.Button), color = HhyColors.SoftBlue) {
                            HhyIcon(
                                category.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(HhySpacing.Md),
                                tint = HhyColors.BrandPrimary,
                            )
                        }
                        Text(
                            category.title,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = HhyColors.TextPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNoticeModule(
    module: HomeModuleSnapshot,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    val item = module.items.firstOrNull() ?: return
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HhyRadius.Tag))
            .clickable(enabled = isHomeTargetEnabled(item.target, canOpenTarget)) { onOpenTarget(item.target) }
            .testTag("home.module.notice"),
        color = HhyColors.Surface,
        shadowElevation = HhyElevation.Card,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhyIcon(HhyIcons.Information, contentDescription = null, tint = HhyColors.BrandPrimary)
            Text(
                item.title,
                modifier = Modifier.weight(1f).padding(horizontal = HhySpacing.Sm),
                color = HhyColors.TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            if (isHomeTargetEnabled(item.target, canOpenTarget)) HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.TextTertiary)
        }
    }
}

@Composable
private fun HomeBannerModule(
    module: HomeModuleSnapshot,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    val moreTarget = module.moreTarget?.takeIf { isHomeTargetEnabled(it, canOpenTarget) }
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        module.title?.let {
            HomeSectionHeading(
                it,
                module.subtitle.orEmpty(),
                moreTarget?.let { target -> { onOpenTarget(target) } },
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            contentPadding = PaddingValues(end = HhySpacing.Xxl),
            modifier = Modifier.testTag("home.module.banner"),
        ) {
            items(module.items.size) { index ->
                val item = module.items[index]
                Card(
                    modifier = Modifier.fillParentMaxWidth(0.92f).aspectRatio(2.25f)
                        .clickable(enabled = isHomeTargetEnabled(item.target, canOpenTarget)) { onOpenTarget(item.target) },
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary),
                    elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        item.coverUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(
                                        HhyColors.BrandPrimaryDark.copy(alpha = 0.08f),
                                        HhyColors.BrandPrimaryDark.copy(alpha = 0.88f),
                                    ),
                                ),
                            ),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(HhySpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
                        ) {
                            Text(item.title, color = HhyColors.TextInverse, fontWeight = FontWeight.SemiBold)
                            item.subtitle?.let {
                                Text(it, color = HhyColors.TextInverse.copy(alpha = 0.88f), maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContentModule(
    module: HomeModuleSnapshot,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    val moreTarget = module.moreTarget?.takeIf { isHomeTargetEnabled(it, canOpenTarget) }
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        HomeSectionHeading(
            module.title ?: homeModuleTitle(module.type),
            module.subtitle.orEmpty(),
            moreTarget?.let { target -> { onOpenTarget(target) } },
        )
        when (module.type) {
            "GRID", "QUICK_ACTIONS" -> HomeModuleGrid(module.items, canOpenTarget, onOpenTarget)
            "HORIZONTAL_LIST" -> HomeModuleHorizontalList(module.items, canOpenTarget, onOpenTarget)
            else -> Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                module.items.forEach { HomeContentCard(it, canOpenTarget, onOpenTarget) }
            }
        }
    }
}

@Composable
private fun HomeModuleGrid(
    items: List<HomeModuleItemSnapshot>,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    items.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            rowItems.forEach { item ->
                HomeCompactCard(item, Modifier.weight(1f), canOpenTarget, onOpenTarget)
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeModuleHorizontalList(
    items: List<HomeModuleItemSnapshot>,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        items(items.size) { index ->
            HomeCompactCard(items[index], Modifier.width(HhySize.ChallengeImageWidth), canOpenTarget, onOpenTarget)
        }
    }
}

@Composable
private fun HomeCompactCard(
    item: HomeModuleItemSnapshot,
    modifier: Modifier,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    Card(
        modifier = modifier.clickable(enabled = isHomeTargetEnabled(item.target, canOpenTarget)) { onOpenTarget(item.target) },
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            HomeMedia(item, Modifier.fillMaxWidth().aspectRatio(1.65f))
            Column(
                modifier = Modifier.padding(start = HhySpacing.Md, end = HhySpacing.Md, bottom = HhySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
            ) {
                HomeBadges(item)
                Text(item.title, color = HhyColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                item.subtitle?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun HomeContentCard(
    item: HomeModuleItemSnapshot,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
    onOpenTarget: (HomeNavigationTargetSnapshot) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = isHomeTargetEnabled(item.target, canOpenTarget)) { onOpenTarget(item.target) },
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeMedia(item, Modifier.size(HhySize.AppLogo))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                HomeBadges(item)
                Text(item.title, color = HhyColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                item.subtitle?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 2) }
            }
            if (isHomeTargetEnabled(item.target, canOpenTarget)) HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.TextTertiary)
        }
    }
}

@Composable
private fun HomeMedia(item: HomeModuleItemSnapshot, modifier: Modifier) {
    val url = item.coverUrl
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = item.title,
            modifier = modifier.clip(RoundedCornerShape(HhyRadius.Tag)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SoftBlue) {
            Box(contentAlignment = Alignment.Center) {
                HhyIcon(homeItemIcon(item.itemType), contentDescription = null, tint = HhyColors.BrandPrimary)
            }
        }
    }
}

@Composable
private fun HomeBadges(item: HomeModuleItemSnapshot) {
    if (item.badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
        item.badges.take(3).forEach { badge ->
            Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = HhyColors.SoftBlue) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
                    color = HhyColors.BrandPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, subtitle: String, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = HhyColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
            }
        }
        if (onMore != null) {
            TextButton(onClick = onMore, modifier = Modifier.testTag("home.module.more")) {
                Text("查看更多")
                HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.BrandPrimary)
            }
        }
    }
}

@Composable
private fun HomeEmptyState(homeError: Boolean, refreshing: Boolean, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        HomeSectionHeading("为你推荐", "来自平台的真实内容")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HhyRadius.LargeCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                if (refreshing) CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
                else HhyIcon(if (homeError) HhyIcons.Error else HhyIcons.Applications, contentDescription = null, tint = HhyColors.BrandPrimary)
                Text(
                    if (homeError) "首页内容暂时无法加载" else if (refreshing) "正在加载首页内容" else "暂时没有推荐内容",
                    color = HhyColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (homeError) "请检查网络后重试，四大分类入口仍可使用。" else "平台有新内容时会在这里展示。",
                    color = HhyColors.TextSecondary,
                )
                if (homeError) OutlinedButton(onClick = onRetry) { Text("重新加载") }
            }
        }
    }
}

@Composable
private fun MineSecurityCard(
    onOpenIdentity: () -> Unit,
    onOpenLoginDevices: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenCancellation: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Text("账号与安全", fontWeight = FontWeight.SemiBold)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenIdentity) { Text("实名认证") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenLoginDevices) { Text("登录设备") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenChangePassword) { Text("修改登录密码") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenCancellation) { Text("注销账号") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) { Text("关于与检查更新") }
        }
    }
}

private fun isHomeTargetEnabled(
    target: HomeNavigationTargetSnapshot,
    canOpenTarget: (HomeNavigationTargetSnapshot) -> Boolean,
): Boolean = target.targetType != "NONE" &&
    (!target.route.isNullOrBlank() || !target.url.isNullOrBlank()) &&
    canOpenTarget(target)

private fun homeModuleTitle(type: String): String = when (type) {
    "GRID", "QUICK_ACTIONS" -> "活动入口"
    "HORIZONTAL_LIST" -> "为你推荐"
    "VERTICAL_LIST" -> "最新发布"
    else -> "推荐内容"
}

private fun homeItemIcon(type: String): ImageVector = when (type) {
    "CATEGORY" -> HhyIcons.Applications
    "NOTICE" -> HhyIcons.Information
    "BANNER" -> HhyIcons.Reward
    "ACTION" -> HhyIcons.ChevronRight
    else -> HhyIcons.Projects
}
