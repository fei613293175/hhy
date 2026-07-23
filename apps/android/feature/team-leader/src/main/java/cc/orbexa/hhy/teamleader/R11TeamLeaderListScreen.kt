package cc.orbexa.hhy.teamleader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR11Api
import cc.orbexa.hhy.network.R07CallResult
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R11TeamLeaderListScreen(
    api: ContractR11Api,
    accessToken: String,
    onBack: () -> Unit,
    onTeamLeaderSelected: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(R11TeamLeaderListState()) }

    fun load(reset: Boolean) {
        if (state.phase in setOf(R11TeamLeaderPhase.REFRESHING, R11TeamLeaderPhase.APPENDING)) return
        scope.launch {
            state = state.loading(reset)
            when (val result = api.teamLeaders(accessToken, cursor = if (reset) null else state.nextCursor)) {
                is R07CallResult.Success -> state = state.success(
                    result = result.data.items,
                    cursor = result.data.page.nextCursor,
                    canLoadMore = result.data.page.canLoadMore(),
                    reset = reset,
                )
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.failed(result)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load(reset = true) }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r11.team-leader.list.${state.phase.name.lowercase()}"),
        topBar = {
            TopAppBar(
                title = { Text("团队长") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    OutlinedButton(
                        onClick = { load(reset = true) },
                        enabled = state.phase !in setOf(R11TeamLeaderPhase.LOADING, R11TeamLeaderPhase.REFRESHING),
                        modifier = Modifier.padding(end = HhySpacing.Lg).testTag("r11.team-leader.refresh"),
                    ) { Text("刷新") }
                },
            )
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = HhySpacing.Lg,
                end = HhySpacing.Lg,
                top = HhySpacing.Md,
                bottom = HhySpacing.Xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item { TeamLeaderListHeading(state) }
            when (state.phase) {
                R11TeamLeaderPhase.LOADING -> items(3) { TeamLeaderSkeleton() }
                R11TeamLeaderPhase.EMPTY -> item {
                    TeamLeaderStateCard("暂时没有团队长资料", "平台有新的已上线团队资料后，会在这里展示。", "重新加载") { load(true) }
                }
                R11TeamLeaderPhase.ERROR -> item {
                    TeamLeaderStateCard("内容暂时无法加载", "请稍后重试，或返回上一页。", "重新加载") { load(true) }
                }
                R11TeamLeaderPhase.OFFLINE -> item {
                    TeamLeaderStateCard("网络连接不可用", "请检查网络连接后重新加载。", "重新加载") { load(true) }
                }
                R11TeamLeaderPhase.FORBIDDEN -> item {
                    TeamLeaderStateCard("暂时无法查看", "当前账号暂无访问权限。", "返回") { onBack() }
                }
                else -> {
                    items(state.items, key = ContentResource::id) { item ->
                        TeamLeaderCard(item) { onTeamLeaderSelected(item.id) }
                    }
                    when (state.phase) {
                        R11TeamLeaderPhase.REFRESHING -> item { InlineLoading("正在刷新团队长资料") }
                        R11TeamLeaderPhase.APPENDING -> item { InlineLoading("正在加载更多") }
                        R11TeamLeaderPhase.PARTIAL_ERROR -> item {
                            TeamLeaderStateCard("更多内容加载失败", "已加载的内容仍可继续查看。", "重试") { load(false) }
                        }
                        else -> if (state.hasMore) item {
                            OutlinedButton(
                                onClick = { load(false) },
                                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight)
                                    .testTag("r11.team-leader.load-more"),
                            ) { Text("加载更多") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamLeaderListHeading(state: R11TeamLeaderListState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text("最新发布", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HhyColors.TextPrimary)
            Text("发现真实团队与合作方向", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
        }
        if (state.items.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = HhyColors.SoftBlue) {
                Text(
                    "已加载 ${state.items.size} 条",
                    modifier = Modifier.padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Sm),
                    style = MaterialTheme.typography.labelMedium,
                    color = HhyColors.BrandPrimary,
                )
            }
        }
    }
}

@Composable
private fun TeamLeaderCard(item: ContentResource, onClick: () -> Unit) {
    val facts = R11TeamLeaderFacts.from(item)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("r11.team-leader.card.${item.id}"),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                TeamLeaderLogo(facts)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Text(
                        facts.teamName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HhyColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    facts.introduction?.let {
                        Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.TextTertiary)
            }
            if (facts.tags.isNotEmpty() || !item.regionCode.isNullOrBlank()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                ) {
                    facts.tags.forEach { TeamLeaderTag(it) }
                    item.regionCode?.trim()?.takeIf(String::isNotBlank)?.let { TeamLeaderTag(it) }
                }
            }
            HorizontalDivider(color = HhyColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                HhyIcon(HhyIcons.Profile, contentDescription = null, modifier = Modifier.size(HhySpacing.Xl), tint = HhyColors.TextSecondary)
                Text(
                    facts.nickname?.let { "发布者 $it" } ?: "发布者信息未提供",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = HhyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.statistics?.let {
                    Text("浏览 ${it.viewCount}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                    Text("收藏 ${it.favoriteCount}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TeamLeaderLogo(facts: R11TeamLeaderFacts) {
    Surface(modifier = Modifier.size(HhySize.AppLogo), shape = CircleShape, color = HhyColors.SoftBlue) {
        if (facts.logoUrl != null) {
            AsyncImage(
                model = facts.logoUrl,
                contentDescription = facts.teamName,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HhyIcon(HhyIcons.Profile, contentDescription = null, tint = HhyColors.BrandPrimary)
            }
        }
    }
}

@Composable
private fun TeamLeaderTag(value: String) {
    Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = HhyColors.SoftBlue) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Xs),
            style = MaterialTheme.typography.labelSmall,
            color = HhyColors.BrandPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TeamLeaderSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
    ) {
        Row(Modifier.padding(HhySpacing.Lg), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Box(Modifier.size(HhySize.AppLogo).clip(CircleShape).background(HhyColors.SoftBlue))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                Box(Modifier.fillMaxWidth(0.55f).height(HhySpacing.Xl).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.SoftBlue))
                Box(Modifier.fillMaxWidth().height(HhySpacing.Lg).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.PageBackground))
                Box(Modifier.fillMaxWidth(0.75f).height(HhySpacing.Lg).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.PageBackground))
            }
        }
    }
}

@Composable
private fun InlineLoading(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
        Text(label, modifier = Modifier.padding(start = HhySpacing.Sm), color = HhyColors.TextSecondary)
    }
}

@Composable
private fun TeamLeaderStateCard(title: String, message: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            HhyIcon(HhyIcons.Profile, contentDescription = null, tint = HhyColors.BrandPrimary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = HhyColors.TextSecondary)
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}
