package cc.orbexa.hhy.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ExperienceApi
import cc.orbexa.hhy.network.HomeSnapshot
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private data class NavigationItem(val label: String, val icon: ImageVector)

private val navigationItems = listOf(
    NavigationItem("首页", HhyIcons.Home),
    NavigationItem("红包", HhyIcons.Reward),
    NavigationItem("发布", HhyIcons.Publish),
    NavigationItem("消息", HhyIcons.Message),
    NavigationItem("我的", HhyIcons.Profile),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HhyShellScreen(
    onOpenSearch: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    onCreateApp: () -> Unit = {},
    onOpenLoginDevices: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    onOpenCancellation: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    experienceApi: ExperienceApi? = null,
    accessToken: String = "",
) {
    var selectedIndex by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var home by androidx.compose.runtime.remember { mutableStateOf<HomeSnapshot?>(null) }
    var homeError by androidx.compose.runtime.remember { mutableStateOf(false) }
    var refreshing by androidx.compose.runtime.remember { mutableStateOf(false) }
    var homeRefreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(experienceApi, accessToken, homeRefreshKey) {
        val api = experienceApi ?: return@LaunchedEffect
        refreshing = true
        api.home(accessToken).onSuccess { home = it; homeError = false }.onFailure { homeError = true }
        refreshing = false
    }
    val screenMarker = when {
        selectedIndex == 0 && home != null -> "hhy.screen.r06.home.loaded"
        selectedIndex == 0 && homeError -> "hhy.screen.r06.home.error"
        selectedIndex == 0 -> "hhy.screen.r06.home.loading"
        selectedIndex == 4 -> "hhy.screen.r06.mine"
        else -> "hhy.screen.shell.${navigationItems[selectedIndex].label}"
    }
    Scaffold(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(screenMarker),
        topBar = {
            TopAppBar(title = { Text("合伙云 Pro") })
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { HhyIcon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            if (selectedIndex == 0) {
                item {
                    HomeHero(onOpenSearch)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                        HomeSectionHeading("合作入口", "全部来自已上线的真实能力")
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            HomeQuickAction(
                                modifier = Modifier.weight(1f).testTag("r07.home.search"),
                                icon = HhyIcons.Search,
                                title = "全局搜索",
                                detail = "找项目与伙伴",
                                onClick = onOpenSearch,
                            )
                            HomeQuickAction(
                                modifier = Modifier.weight(1f).testTag("r08.home.projects"),
                                icon = HhyIcons.Projects,
                                title = "项目广场",
                                detail = "浏览公开项目",
                                onClick = onOpenProjects,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            HomeQuickAction(
                                modifier = Modifier.weight(1f).testTag("r08.home.project.create"),
                                icon = HhyIcons.Publish,
                                title = "发布项目",
                                detail = "展示合作需求",
                                onClick = onCreateProject,
                            )
                            HomeQuickAction(
                                modifier = Modifier.weight(1f).testTag("r09.home.apps"),
                                icon = HhyIcons.Applications,
                                title = "应用广场",
                                detail = "发现真实应用",
                                onClick = onOpenApps,
                            )
                            HomeQuickAction(
                                modifier = Modifier.weight(1f).testTag("r09.home.app.create"),
                                icon = HhyIcons.Publish,
                                title = "推广App",
                                detail = "发布应用资料",
                                onClick = onCreateApp,
                            )
                        }
                    }
                }
            }
            if (selectedIndex != 0) item {
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Text(
                        text = navigationItems[selectedIndex].label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when (selectedIndex) {
                            1 -> "参与活动，获得更多权益"
                            2 -> "分享你的项目与能力"
                            3 -> "与合作伙伴保持联系"
                            else -> "管理个人资料与账号安全"
                        },
                        color = HhyColors.TextSecondary,
                    )
                }
            }
            if (selectedIndex == 4) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(HhyRadius.LargeCard),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                        ) {
                            Text("账号与安全", fontWeight = FontWeight.SemiBold)
                            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenIdentity) {
                                Text("实名认证")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenLoginDevices) {
                                Text("登录设备")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenChangePassword) {
                                Text("修改登录密码")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenCancellation) {
                                Text("注销账号")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) { Text("关于与检查更新") }
                        }
                    }
                }
            }
            if (selectedIndex == 0 && home != null && home!!.modules.isNotEmpty()) {
                item { HomeSectionHeading("推荐内容", "由服务端实时配置") }
                home!!.modules.forEach { module ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(HhyRadius.NormalCard),
                            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                                    Surface(shape = RoundedCornerShape(HhyRadius.Button), color = HhyColors.SoftBlue) {
                                        HhyIcon(HhyIcons.Applications, contentDescription = null, modifier = Modifier.padding(HhySpacing.Sm), tint = HhyColors.BrandPrimary)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                        Text(module.title ?: "内容模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        module.subtitle?.let { Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                                module.items.take(5).forEach { item ->
                                    Surface(color = HhyColors.PageBackground, shape = RoundedCornerShape(HhyRadius.Tag)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(item, modifier = Modifier.weight(1f), color = HhyColors.TextPrimary)
                                            HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.TextTertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedIndex != 4) item {
                if (selectedIndex == 0) HomeSectionHeading("推荐内容", "由服务端实时配置")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                            Surface(shape = RoundedCornerShape(HhyRadius.Button), color = if (homeError) HhyColors.ErrorSoft else HhyColors.SoftBlue) {
                                if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(HhySpacing.Sm).size(HhySize.StandardProgress))
                                else HhyIcon(
                                    if (homeError) HhyIcons.Error else HhyIcons.Projects,
                                    contentDescription = null,
                                    modifier = Modifier.padding(HhySpacing.Md),
                                    tint = if (homeError) HhyColors.Error else HhyColors.BrandPrimary,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                Text(
                                    if (homeError) "首页内容暂时无法加载" else if (refreshing) "正在加载首页内容" else "暂无推荐内容",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HhyColors.TextPrimary,
                                )
                                Text(
                                    if (homeError) "网络恢复后可重新加载，搜索与项目入口仍可使用。" else if (refreshing) "正在获取服务端配置的真实首页模块。" else "当前没有推荐模块，可从上方入口继续发现或发布项目。",
                                    color = HhyColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (homeError) {
                            OutlinedButton(onClick = { homeRefreshKey += 1 }) { Text("重新加载") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(onSearch: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(HhyColors.BrandPrimaryDark, HhyColors.BrandGradientEnd)),
                shape = RoundedCornerShape(HhyRadius.LargeCard),
            )
            .padding(HhySpacing.Xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Surface(shape = RoundedCornerShape(HhyRadius.NormalCard), color = HhyColors.Surface.copy(alpha = 0.18f)) {
                    HhyIcon(HhyIcons.Home, contentDescription = null, modifier = Modifier.padding(HhySpacing.Md), tint = HhyColors.TextInverse)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Text("发现真实合作机会", style = MaterialTheme.typography.titleLarge, color = HhyColors.TextInverse)
                    Text("从项目、应用、群聊与团长内容中快速匹配", color = HhyColors.TextInverse.copy(alpha = 0.84f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = HhySize.PrimaryButtonHeight),
                onClick = onSearch,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = HhyColors.Surface,
                    contentColor = HhyColors.BrandPrimary,
                ),
            ) {
                HhyIcon(HhyIcons.Search, contentDescription = null)
                Text("搜索合作内容", modifier = Modifier.padding(start = HhySpacing.Sm))
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
        }
        Surface(modifier = Modifier.size(HhySpacing.Xxl, HhySpacing.Xs), color = HhyColors.BrandPrimary, shape = RoundedCornerShape(HhyRadius.Pill)) { }
    }
}

@Composable
private fun HomeQuickAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                HhyIcon(icon, contentDescription = null, modifier = Modifier.padding(HhySpacing.Sm), tint = HhyColors.BrandPrimary)
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = HhyColors.TextPrimary, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary, maxLines = 1)
        }
    }
}
