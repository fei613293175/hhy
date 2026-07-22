package cc.orbexa.hhy.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Text(
                    text = navigationItems[selectedIndex].label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(HhySpacing.Sm))
                Text(
                    text = when (selectedIndex) {
                        0 -> "发现值得合作的人和项目"
                        1 -> "参与活动，获得更多权益"
                        2 -> "分享你的项目与能力"
                        3 -> "与合作伙伴保持联系"
                        else -> "管理个人资料与账号安全"
                    },
                    color = HhyColors.TextSecondary,
                )
            }
            if (selectedIndex == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(HhyRadius.LargeCard),
                        colors = CardDefaults.cardColors(containerColor = HhyColors.SoftBlue),
                        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                        ) {
                            Text("快速发现", style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
                            Text("从真实公开内容中寻找项目、应用、群聊或团长", color = HhyColors.TextSecondary)
                            Button(
                                modifier = Modifier.fillMaxWidth().heightIn(min = HhySize.PrimaryButtonHeight).testTag("r07.home.search"),
                                onClick = onOpenSearch,
                            ) { Text("搜索合作内容") }
                            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).testTag("r08.home.projects"),
                                    onClick = onOpenProjects,
                                ) { Text("浏览项目") }
                                Button(
                                    modifier = Modifier.weight(1f).testTag("r08.home.project.create"),
                                    onClick = onCreateProject,
                                ) { Text("发布项目") }
                            }
                        }
                    }
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
                home!!.modules.forEach { module ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(HhyRadius.NormalCard),
                            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                                Text(module.title ?: "内容模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                module.subtitle?.let { Text(it, color = HhyColors.TextSecondary) }
                                module.items.take(5).forEach { item ->
                                    Surface(color = HhyColors.PageBackground, shape = RoundedCornerShape(HhyRadius.Tag)) {
                                        Text(item, modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md), color = HhyColors.TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedIndex != 4) item {
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
                        if (refreshing) CircularProgressIndicator()
                        Text(
                            if (homeError) "首页内容暂时无法加载" else if (refreshing) "正在加载首页内容" else "暂无推荐内容",
                            style = MaterialTheme.typography.titleMedium,
                            color = HhyColors.TextPrimary,
                        )
                        Text(
                            if (homeError) "网络恢复后可重新加载，已提供的搜索和项目入口仍可正常使用。" else if (refreshing) "正在获取服务端配置的真实首页模块。" else "服务端当前没有配置推荐模块，你仍可通过上方真实入口发现或发布项目。",
                            color = HhyColors.TextSecondary,
                        )
                        if (homeError) {
                            OutlinedButton(onClick = { homeRefreshKey += 1 }) { Text("重新加载") }
                        }
                    }
                }
            }
        }
    }
}
