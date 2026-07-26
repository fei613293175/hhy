package cc.orbexa.hhy.contentmanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cc.orbexa.hhy.network.ContractR12Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12CopyContentRequest
import cc.orbexa.hhy.network.R12CopyContentResult
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R12ContentManagementDetailScreen(
    api: ContractR12Api,
    accessToken: String,
    contentId: String,
    currentUserId: String,
    identityVerified: Boolean,
    onBack: () -> Unit,
    onDraftCreated: (String) -> Unit,
    onPreview: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val copyKeys = remember { R12CopyIntentKeys() }
    var state by remember(contentId) { mutableStateOf(R12ContentManagementState()) }
    var copyDialogOpen by remember { mutableStateOf(false) }
    var copyReason by remember { mutableStateOf("") }

    fun loadAnalytics() {
        if (state.analyticsLoading) return
        state = state.analyticsStarted()
        scope.launch {
            when (val result = api.analytics(accessToken, contentId)) {
                is R07CallResult.Success -> state = state.analyticsLoaded(result.data)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.analyticsFailed()
                }
            }
        }
    }

    fun load() {
        if (state.actionInFlight) return
        state = state.loading()
        scope.launch {
            when (val result = api.content(accessToken, contentId)) {
                is R07CallResult.Success -> {
                    state = state.loaded(result.data)
                    loadAnalytics()
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.loadFailed(result)
                }
            }
        }
    }

    fun copyAsDraft() {
        val content = state.content ?: return
        if (state.phase != R12ContentManagementPhase.CONTENT || state.actionInFlight) return
        val reason = copyReason.trim().takeIf(String::isNotEmpty)
        if (reason != null && reason.length > 2000) return
        val key = copyKeys.forRequest(content.id, content.version, reason)
        copyDialogOpen = false
        state = state.copy(actionInFlight = true, notice = null)
        scope.launch {
            when (val result = api.copy(accessToken, content.id, key, R12CopyContentRequest(content.version, reason))) {
                is R07CallResult.Success -> {
                    copyKeys.consume(content.id)
                    state = state.copy(actionInFlight = false, notice = "已复制为草稿")
                    val draftId = when (val copied = result.data) {
                        is R12CopyContentResult.Content -> copied.resource.id
                        is R12CopyContentResult.Command -> copied.command.resourceId
                    }
                    if (!draftId.isNullOrBlank()) onDraftCreated(draftId) else load()
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.copy(
                        actionInFlight = false,
                        phase = if (result.statusCode == 409) R12ContentManagementPhase.STALE_CACHE else state.phase,
                        notice = when (result.statusCode) {
                            403 -> "当前账号不能复制此内容"
                            409 -> "内容已更新，请刷新后再复制"
                            422 -> "当前内容暂不支持复制为草稿"
                            429 -> "操作较频繁，请稍后重试"
                            else -> "复制未完成，请稍后重试"
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(contentId) { load() }

    if (copyDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.actionInFlight) copyDialogOpen = false },
            title = { Text("复制为草稿") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                    Text("将根据当前最新内容创建一份独立草稿，原内容不会被修改。创建后会占用草稿额度。")
                    OutlinedTextField(
                        value = copyReason,
                        onValueChange = { if (it.length <= 2000) copyReason = it },
                        label = { Text("复制原因（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                    Text("确认前会再次校验实名、内容归属和最新版本。", color = HhyColors.TextSecondary)
                }
            },
            confirmButton = { Button(onClick = ::copyAsDraft) { Text("确认复制") } },
            dismissButton = { TextButton(onClick = { copyDialogOpen = false }) { Text("取消") } },
        )
    }

    val canCopy = state.content?.canCopy(currentUserId, identityVerified) == true &&
        state.phase == R12ContentManagementPhase.CONTENT && !state.actionInFlight
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r12.content_management.detail.${state.phase.name.lowercase()}"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("发布管理") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    TextButton(onClick = ::load, enabled = !state.actionInFlight) { Text("刷新") }
                },
            )
        },
        bottomBar = {
            state.content?.let {
                ManagementBottomActions(
                    dataEnabled = !state.analyticsLoading && !state.actionInFlight,
                    copyEnabled = canCopy,
                    copyInFlight = state.actionInFlight,
                    onLoadData = ::loadAnalytics,
                    onCopy = { copyDialogOpen = true },
                )
            }
        },
    ) { padding ->
        when (state.phase) {
            R12ContentManagementPhase.LOADING -> ManagementSkeleton(padding)
            R12ContentManagementPhase.CONTENT,
            R12ContentManagementPhase.STALE_CACHE -> state.content?.let { content ->
                ManagementContent(
                    content = content,
                    state = state,
                    padding = padding,
                    onRefresh = ::load,
                    onLoadData = ::loadAnalytics,
                    onPreview = { onPreview(content.id) },
                    onCopy = { copyDialogOpen = true },
                    canCopy = canCopy,
                )
            } ?: ManagementFailure(state.phase, padding, onBack, ::load)
            else -> ManagementFailure(state.phase, padding, onBack, ::load)
        }
    }
}

@Composable
private fun ManagementContent(
    content: ContentResource,
    state: R12ContentManagementState,
    padding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadData: () -> Unit,
    onPreview: () -> Unit,
    onCopy: () -> Unit,
    canCopy: Boolean,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        if (state.phase == R12ContentManagementPhase.STALE_CACHE) {
            item { StatusNotice("当前展示最近保存的内容，刷新成功前不能执行写操作。", HhyColors.WarningSoft, HhyColors.Warning) }
        }
        state.notice?.let { notice -> item { StatusNotice(notice, HhyColors.SuccessSoft, HhyColors.Success) } }
        item { ManagementHero(content) }
        item { StatisticsCard(state) }
        item { ManagementActionGrid(onRefresh, onLoadData, onPreview, onCopy, canCopy, state.analyticsLoading) }
        item {
            InformationSection("基本信息") {
                InformationRow("内容类型", contentTypeLabel(content.contentType))
                content.categoryCode?.takeIf(String::isNotBlank)?.let { InformationRow("分类", it) }
                content.regionCode?.takeIf(String::isNotBlank)?.let { InformationRow("地区", it) }
                content.publisher?.let { publisher ->
                    InformationRow("发布者", publisher.nickname + if (publisher.verified) " · 已认证" else "")
                }
            }
        }
        content.description?.takeIf(String::isNotBlank)?.let { description ->
            item { InformationSection("详细说明") { Text(description, color = HhyColors.TextPrimary) } }
        }
        if (content.contactsMasked.isNotEmpty()) {
            item {
                InformationSection("联系方式设置") {
                    content.contactsMasked.forEach { contact ->
                        InformationRow(contactChannelLabel(contact.channel), contact.maskedValue ?: if (contact.available) "已配置" else "不可用")
                    }
                }
            }
        }
        item {
            InformationSection("审核与状态") {
                InformationRow("发布状态", contentStatusLabel(content.status))
                content.reviewStatus?.let { InformationRow("审核状态", reviewStatusLabel(it)) }
                content.createdAt?.let { InformationRow("创建时间", it) }
                content.updatedAt?.let { InformationRow("最近更新", it.r12BusinessTimeLabel()) }
            }
        }
    }
}

@Composable
private fun ManagementHero(content: ContentResource) = Card(
    colors = CardDefaults.cardColors(HhyColors.Surface),
    elevation = CardDefaults.cardElevation(HhyElevation.Card),
    shape = RoundedCornerShape(HhyRadius.NormalCard),
) {
    Row(
        Modifier.fillMaxWidth().padding(HhySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(HhySize.AppLogo).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.SoftBlue),
            contentAlignment = Alignment.Center,
        ) {
            HhyIcon(contentTypeIcon(content.contentType), null, Modifier.size(HhySpacing.Xxl), HhyColors.BrandPrimary)
            content.secureMediaUrl()?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = content.media.firstOrNull()?.altText ?: "${content.title}内容图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                Text(content.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatusPill(contentStatusLabel(content.status), statusTone(content.status))
            }
            content.summary?.takeIf(String::isNotBlank)?.let {
                Text(it, color = HhyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                content.updatedAt?.let { "更新于 ${it.r12BusinessTimeLabel()}" }
                    ?: content.createdAt?.let { "创建于 ${it.r12BusinessTimeLabel()}" }.orEmpty(),
                color = HhyColors.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatisticsCard(state: R12ContentManagementState) = Card(
    colors = CardDefaults.cardColors(HhyColors.Surface),
    shape = RoundedCornerShape(HhyRadius.NormalCard),
) {
    Column(Modifier.fillMaxWidth().padding(vertical = HhySpacing.Lg)) {
        val statistics = state.statistics
        Row(Modifier.fillMaxWidth()) {
            StatisticCell("浏览量", statistics?.viewCount, Modifier.weight(1f))
            StatisticCell("收藏用户", statistics?.favoriteCount, Modifier.weight(1f))
            StatisticCell("联系人数", statistics?.contactAccessCount, Modifier.weight(1f))
            StatisticCell("分享次数", statistics?.shareCount, Modifier.weight(1f))
        }
        when {
            state.analyticsLoading -> Text("正在更新内容数据", Modifier.padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm), color = HhyColors.TextSecondary)
            state.analyticsUnavailable -> Text("内容数据暂未更新，详情信息仍可正常查看", Modifier.padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm), color = HhyColors.Warning)
        }
    }
}

@Composable
private fun StatisticCell(label: String, value: Long?, modifier: Modifier = Modifier) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
) {
    Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ManagementActionGrid(
    onRefresh: () -> Unit,
    onLoadData: () -> Unit,
    onPreview: () -> Unit,
    onCopy: () -> Unit,
    canCopy: Boolean,
    loadingData: Boolean,
) = Card(colors = CardDefaults.cardColors(HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
    Row(
        Modifier.fillMaxWidth().padding(HhySpacing.Md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ManagementAction(HhyIcons.Refresh, "刷新", true, onRefresh)
        ManagementAction(HhyIcons.Analytics, "数据", !loadingData, onLoadData)
        ManagementAction(HhyIcons.Applications, "预览", true, onPreview)
        ManagementAction(HhyIcons.Copy, "复制草稿", canCopy, onCopy)
    }
}

@Composable
private fun ManagementAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) = TextButton(
    onClick = onClick,
    enabled = enabled,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
        HhyIcon(icon, label)
        Text(label)
    }
}

@Composable
private fun InformationSection(title: String, content: @Composable () -> Unit) = Card(
    colors = CardDefaults.cardColors(HhyColors.Surface),
    shape = RoundedCornerShape(HhyRadius.NormalCard),
) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun InformationRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
    verticalAlignment = Alignment.Top,
) {
    Text(label, Modifier.weight(1f), color = HhyColors.TextSecondary)
    Text(value, Modifier.weight(2f), color = HhyColors.TextPrimary)
}

@Composable
private fun ManagementBottomActions(
    dataEnabled: Boolean,
    copyEnabled: Boolean,
    copyInFlight: Boolean,
    onLoadData: () -> Unit,
    onCopy: () -> Unit,
) = Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(HhySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        OutlinedButton(onClick = onLoadData, enabled = dataEnabled, modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight)) {
            Text("更新数据")
        }
        Button(onClick = onCopy, enabled = copyEnabled, modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight)) {
            if (copyInFlight) CircularProgressIndicator(Modifier.size(HhySpacing.Xl), strokeWidth = HhySize.Hairline)
            else Text("复制为草稿")
        }
    }
}

@Composable
private fun ManagementSkeleton(padding: PaddingValues) = LazyColumn(
    Modifier.fillMaxSize().padding(padding),
    contentPadding = PaddingValues(HhySpacing.Lg),
    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
) {
    item { SkeletonBlock(HhySize.AppLogo * 1.4f) }
    item { SkeletonBlock(HhySize.TopAppBarHeight * 2) }
    item { SkeletonBlock(HhySize.TopAppBarHeight * 1.5f) }
    item { SkeletonBlock(HhySize.TopAppBarHeight * 2) }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) = Box(
    Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(HhyRadius.NormalCard)).background(HhyColors.Border),
)

@Composable
private fun ManagementFailure(
    phase: R12ContentManagementPhase,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val copy = when (phase) {
        R12ContentManagementPhase.NOT_FOUND -> "内容不存在" to "内容可能已删除、下架或链接失效"
        R12ContentManagementPhase.FORBIDDEN -> "无法查看此内容" to "当前账号没有访问权限"
        R12ContentManagementPhase.OFFLINE -> "网络不可用" to "请检查网络连接后重试"
        else -> "加载失败" to "暂时无法完成请求，请稍后重试"
    }
    Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.LargeCard)) {
            Column(
                Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                HhyIcon(if (phase == R12ContentManagementPhase.OFFLINE) HhyIcons.Information else HhyIcons.Error, null, Modifier.size(HhySpacing.Xxl), HhyColors.BrandPrimary)
                Text(copy.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(copy.second, color = HhyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(onClick = onBack) { Text("返回") }
                    if (phase !in setOf(R12ContentManagementPhase.NOT_FOUND, R12ContentManagementPhase.FORBIDDEN)) {
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(value: String, color: androidx.compose.ui.graphics.Color) = Surface(
    color = color.copy(alpha = 0.12f),
    shape = RoundedCornerShape(HhyRadius.Tag),
) {
    Text(value, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StatusNotice(value: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color) = Surface(
    color = background,
    shape = RoundedCornerShape(HhyRadius.NormalCard),
) {
    Text(value, Modifier.fillMaxWidth().padding(HhySpacing.Md), color = foreground)
}

private fun contentTypeIcon(value: String) = when (value) {
    "APP" -> HhyIcons.Applications
    "GROUP_CHAT" -> HhyIcons.Groups
    "TEAM_LEADER" -> HhyIcons.Profile
    else -> HhyIcons.Projects
}

private fun statusTone(value: String) = when (value) {
    "ONLINE", "PUBLISHED" -> HhyColors.Success
    "DRAFT" -> HhyColors.TextSecondary
    "REJECTED", "OFFLINE" -> HhyColors.Error
    else -> HhyColors.Warning
}

private fun contactChannelLabel(value: String): String = when (value) {
    "WECHAT" -> "微信"
    "PHONE" -> "手机号"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    else -> "联系方式"
}

private fun reviewStatusLabel(value: String): String = when (value) {
    "PENDING", "PENDING_REVIEW", "REVIEWING" -> "审核中"
    "APPROVED", "PASSED" -> "已通过"
    "REJECTED" -> "未通过"
    else -> "状态已更新"
}
