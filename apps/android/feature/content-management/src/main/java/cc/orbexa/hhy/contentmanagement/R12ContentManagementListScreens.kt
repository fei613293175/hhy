package cc.orbexa.hhy.contentmanagement

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR12Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12ContentStatusRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class R12PendingAction { ONLINE, OFFLINE, DELETE }

private data class R12ActionIntent(
    val content: ContentResource,
    val action: R12PendingAction,
)

@Composable
fun R12MyContentsScreen(
    api: ContractR12Api,
    accessToken: String,
    identityVerified: Boolean,
    onBack: () -> Unit,
    onContentSelected: (String) -> Unit,
    onCreateContent: (String) -> Unit,
    onReviews: (String) -> Unit,
    onAnalytics: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionKeys = remember { R12ContentActionKeys() }
    var state by remember { mutableStateOf(R12ContentListState()) }
    var actionIntent by remember { mutableStateOf<R12ActionIntent?>(null) }
    var publishTypeDialogOpen by remember { mutableStateOf(false) }

    fun load(refresh: Boolean = false, append: Boolean = false) {
        if (state.refreshing || state.appending || state.actionContentId != null) return
        val current = state
        state = state.loading(refresh, append)
        scope.launch {
            val result = api.contents(
                accessToken = accessToken,
                page = if (append) ((current.page?.page ?: 1) + 1).toInt() else 1,
                cursor = if (append) current.page?.nextCursor else null,
                status = current.selectedStatus,
                sort = "updatedAt:desc",
            )
            when (result) {
                is R07CallResult.Success -> state = state.loaded(result.data, append)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.failed(result)
                }
            }
        }
    }

    fun confirmAction() {
        val intent = actionIntent ?: return
        actionIntent = null
        val actionName = intent.action.name.lowercase()
        val key = actionKeys.forRequest(actionName, intent.content.id, intent.content.version)
        state = state.actionStarted(intent.content.id)
        scope.launch {
            val result = when (intent.action) {
                R12PendingAction.ONLINE -> api.online(
                    accessToken,
                    intent.content.id,
                    key,
                    R12ContentStatusRequest(intent.content.version),
                )
                R12PendingAction.OFFLINE -> api.offline(
                    accessToken,
                    intent.content.id,
                    key,
                    R12ContentStatusRequest(intent.content.version),
                )
                R12PendingAction.DELETE -> error("删除仅在草稿箱执行")
            }
            when (result) {
                is R07CallResult.Success -> {
                    actionKeys.consume(actionName, intent.content.id)
                    state = state.actionSucceeded(
                        intent.content.id,
                        if (intent.action == R12PendingAction.ONLINE) "内容已上架" else "内容已下架",
                        remove = false,
                    )
                    state = state.loading(refresh = true)
                    when (val refreshed = api.contents(
                        accessToken = accessToken,
                        status = state.selectedStatus,
                        sort = "updatedAt:desc",
                    )) {
                        is R07CallResult.Success -> state = state.loaded(refreshed.data).copy(
                            notice = if (intent.action == R12PendingAction.ONLINE) "内容已上架" else "内容已下架",
                        )
                        is R07CallResult.Failure -> state = state.actionFailed(refreshed)
                    }
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.actionFailed(result)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }
    R12ContentListScaffold(
        screenTag = "hhy.screen.r12.my-contents",
        title = "我的发布",
        subtitle = "管理已发布内容与审核状态",
        kind = R12ContentListKind.CONTENTS,
        state = state,
        onBack = onBack,
        onRefresh = { load(refresh = true) },
        onAppend = { load(append = true) },
        primaryAction = "发布内容" to { publishTypeDialogOpen = true },
        statusOptions = listOf(
            null to "全部",
            "PENDING_REVIEW" to "审核中",
            "ONLINE" to "已上架",
            "OFFLINE_BY_OWNER" to "已下架",
            "REJECTED" to "未通过",
        ),
        onStatusSelected = { selected ->
            state = state.copy(selectedStatus = selected)
            load(refresh = true)
        },
        onContentSelected = onContentSelected,
        onEdit = null,
        onReviews = onReviews,
        onAnalytics = onAnalytics,
        onOnline = { actionIntent = R12ActionIntent(it, R12PendingAction.ONLINE) },
        onOffline = { actionIntent = R12ActionIntent(it, R12PendingAction.OFFLINE) },
        onDelete = null,
        identityVerified = identityVerified,
    )
    actionIntent?.let { intent ->
        R12ActionDialog(intent, onDismiss = { actionIntent = null }, onConfirm = ::confirmAction)
    }
    if (publishTypeDialogOpen) {
        R12PublishTypeDialog(
            onDismiss = { publishTypeDialogOpen = false },
            onSelected = { type ->
                publishTypeDialogOpen = false
                onCreateContent(type)
            },
        )
    }
}

@Composable
fun R12DraftsScreen(
    api: ContractR12Api,
    accessToken: String,
    onBack: () -> Unit,
    onContentSelected: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionKeys = remember { R12ContentActionKeys() }
    var state by remember { mutableStateOf(R12ContentListState()) }
    var actionIntent by remember { mutableStateOf<R12ActionIntent?>(null) }

    fun load(refresh: Boolean = false, append: Boolean = false) {
        if (state.refreshing || state.appending || state.actionContentId != null) return
        val current = state
        state = state.loading(refresh, append)
        scope.launch {
            val result = api.drafts(
                accessToken = accessToken,
                page = if (append) ((current.page?.page ?: 1) + 1).toInt() else 1,
                cursor = if (append) current.page?.nextCursor else null,
                sort = "updatedAt:desc",
            )
            when (result) {
                is R07CallResult.Success -> state = state.loaded(result.data, append)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.failed(result)
                }
            }
        }
    }

    fun deleteDraft() {
        val intent = actionIntent ?: return
        actionIntent = null
        val key = actionKeys.forRequest("delete", intent.content.id, intent.content.version)
        state = state.actionStarted(intent.content.id)
        scope.launch {
            when (val result = api.delete(accessToken, intent.content.id, intent.content.version, key)) {
                is R07CallResult.Success -> {
                    actionKeys.consume("delete", intent.content.id)
                    state = state.actionSucceeded(intent.content.id, "草稿已删除", remove = true)
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.actionFailed(result)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }
    R12ContentListScaffold(
        screenTag = "hhy.screen.r12.drafts",
        title = "草稿箱",
        subtitle = "继续完善尚未提交的内容",
        kind = R12ContentListKind.DRAFTS,
        state = state,
        onBack = onBack,
        onRefresh = { load(refresh = true) },
        onAppend = { load(append = true) },
        primaryAction = null,
        statusOptions = emptyList(),
        onStatusSelected = {},
        onContentSelected = onContentSelected,
        onEdit = { content -> onEdit(content.contentType, content.id) },
        onReviews = null,
        onAnalytics = null,
        onOnline = null,
        onOffline = null,
        onDelete = { actionIntent = R12ActionIntent(it, R12PendingAction.DELETE) },
        identityVerified = false,
    )
    actionIntent?.let { intent ->
        R12ActionDialog(intent, onDismiss = { actionIntent = null }, onConfirm = ::deleteDraft)
    }
}

@Composable
fun R12ContentReviewsScreen(
    api: ContractR12Api,
    accessToken: String,
    contentId: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshRequest by remember(contentId) { mutableStateOf(0) }
    var state by remember(contentId) { mutableStateOf(R12ReviewHistoryState(contentId)) }

    LaunchedEffect(contentId, refreshRequest) {
        val requestGeneration = state.generation + 1
        val refresh = state.summary != null
        state = state.reloadStarted(requestGeneration, refresh)
        coroutineScope {
            val summaryRequest = async { api.content(accessToken, contentId) }
            val timelineRequest = async {
                api.reviews(
                    accessToken = accessToken,
                    id = contentId,
                    page = 1,
                    cursor = null,
                    sort = "updatedAt:desc",
                )
            }
            var sessionExpired = false
            when (val result = summaryRequest.await()) {
                is R07CallResult.Success -> if (state.accepts(contentId, requestGeneration)) {
                    state = state.summaryLoaded(result.data)
                }
                is R07CallResult.Failure -> if (state.accepts(contentId, requestGeneration)) {
                    sessionExpired = result.statusCode == 401
                    state = state.summaryFailed(result)
                }
            }
            when (val result = timelineRequest.await()) {
                is R07CallResult.Success -> if (state.accepts(contentId, requestGeneration)) {
                    state = state.timelineLoaded(result.data)
                }
                is R07CallResult.Failure -> if (state.accepts(contentId, requestGeneration)) {
                    sessionExpired = sessionExpired || result.statusCode == 401
                    state = state.timelineFailed(result)
                }
            }
            if (sessionExpired && state.accepts(contentId, requestGeneration)) onSessionExpired()
        }
    }

    fun loadMore() {
        if (!state.canLoadMore) return
        val requestState = state
        state = state.appendStarted()
        scope.launch {
            val result = api.reviews(
                accessToken = accessToken,
                id = contentId,
                page = ((requestState.page?.page ?: 1) + 1).toInt(),
                cursor = requestState.page?.nextCursor,
                sort = "updatedAt:desc",
            )
            if (!state.accepts(contentId, requestState.generation)) return@launch
            when (result) {
                is R07CallResult.Success -> state = state.timelineLoaded(result.data, append = true)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.timelineFailed(result)
                }
            }
        }
    }

    R12ReviewHistoryScaffold(
        state = state,
        onBack = onBack,
        onRefresh = {
            if (!state.refreshing && !state.appending && !state.summaryLoading && !state.timelineLoading) {
                refreshRequest += 1
            }
        },
        onAppend = ::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R12ReviewHistoryScaffold(
    state: R12ReviewHistoryState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r12.content-reviews"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("审核记录", fontWeight = FontWeight.SemiBold)
                        Text("按更新时间查看真实审核状态", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                    }
                },
                navigationIcon = { HhyBackButton(onBack) },
            )
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (state.phase) {
            R12ContentListPhase.LOADING -> R12ListSkeleton(padding)
            R12ContentListPhase.CONTENT -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                state.summary?.let { summary ->
                    item { R12ReviewContextCard(summary) }
                }
                if (state.summaryFailure != null) {
                    item { R12Notice("当前内容暂未更新，正在展示上次成功加载的信息", true) }
                }
                if (state.timelineFailure != null) {
                    item { R12Notice("部分审核记录暂未更新，当前内容仍可查看", true) }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("处理进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("仅展示平台真实处理记录", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !state.refreshing && !state.appending && !state.summaryLoading && !state.timelineLoading,
                        ) {
                            HhyIcon(HhyIcons.Refresh, "刷新")
                            Text("刷新")
                        }
                    }
                }
                if (state.timelineLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(HhySize.StandardProgress))
                        }
                    }
                } else if (state.entries.isEmpty()) {
                    item { R12ReviewTimelineEmpty() }
                } else {
                    items(state.entries, key = R12ReviewTimelineEntry::reviewId) { entry ->
                        R12ReviewTimelineItem(entry)
                    }
                }
                if (state.appending) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(HhySize.StandardProgress))
                        }
                    }
                } else if (state.canLoadMore) {
                    item { OutlinedButton(onClick = onAppend, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
                }
            }
            else -> R12ListFailure(state.phase, padding, onBack, onRefresh)
        }
    }
}

@Composable
private fun R12ReviewContextCard(content: ContentResource) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("hhy.r12.review-context"),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text("当前内容", style = MaterialTheme.typography.labelMedium, color = HhyColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                R12ContentMedia(content)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Text(content.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    content.summary?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        R12Tag(contentTypeLabel(content.contentType), HhyColors.SoftBlue, HhyColors.BrandPrimary)
                        R12Tag(contentStatusLabel(content.reviewStatus ?: content.status), R12StatusColor(content), HhyColors.TextPrimary)
                    }
                }
            }
            Text("最近更新 ${content.updatedAt.r12BusinessTimeLabel()}", color = HhyColors.TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun R12ReviewTimelineItem(entry: R12ReviewTimelineEntry) {
    val tone = r12ReviewDecisionTone(entry.decision)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("hhy.r12.review-entry.${entry.reviewId}"),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(modifier = Modifier.size(HhySpacing.Xl), shape = RoundedCornerShape(HhyRadius.Pill), color = tone) {}
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r12ReviewDecisionLabel(entry.decision), fontWeight = FontWeight.SemiBold)
                    Text(entry.createdAt.r12BusinessTimeLabel(), color = HhyColors.TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                entry.reason?.let { Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                Text(r12ReviewNextStep(entry.decision), color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun R12ReviewTimelineEmpty() {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), color = HhyColors.Surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            HhyIcon(HhyIcons.Pending, null, tint = HhyColors.BrandPrimary)
            Text("暂无人工审核记录", fontWeight = FontWeight.SemiBold)
            Text("平台处理后会在这里展示真实进度", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun r12ReviewDecisionLabel(value: String?): String = when (value) {
    "ASSIGN", "PENDING", "PENDING_REVIEW", "REVIEWING" -> "审核中"
    "APPROVE", "APPROVED" -> "已通过"
    "REJECT", "REJECTED" -> "未通过"
    "ESCALATE" -> "复核中"
    else -> "状态已更新"
}

private fun r12ReviewNextStep(value: String?): String = when (value) {
    "ASSIGN", "PENDING", "PENDING_REVIEW", "REVIEWING" -> "平台正在处理，请耐心等待"
    "APPROVE", "APPROVED" -> "审核已通过，无需处理"
    "REJECT", "REJECTED" -> "请按审核原因修改内容后重新提交"
    "ESCALATE" -> "内容已进入复核流程，请等待结果"
    else -> "请留意后续状态更新"
}

private fun r12ReviewDecisionTone(value: String?) = when (value) {
    "APPROVE", "APPROVED" -> HhyColors.SuccessSoft
    "REJECT", "REJECTED" -> HhyColors.ErrorSoft
    "ASSIGN", "PENDING", "PENDING_REVIEW", "REVIEWING", "ESCALATE" -> HhyColors.WarningSoft
    else -> HhyColors.SoftBlue
}

@Composable
fun R12ContentAnalyticsScreen(
    api: ContractR12Api,
    accessToken: String,
    contentId: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    R12ReadOnlyContentPage(
        screenTag = "hhy.screen.r12.content-analytics",
        title = "内容数据",
        subtitle = "数据来自平台真实统计",
        kind = R12ContentListKind.ANALYTICS,
        contentId = contentId,
        api = api,
        accessToken = accessToken,
        onBack = onBack,
        onSessionExpired = onSessionExpired,
    )
}

@Composable
private fun R12ReadOnlyContentPage(
    screenTag: String,
    title: String,
    subtitle: String,
    kind: R12ContentListKind,
    contentId: String,
    api: ContractR12Api,
    accessToken: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(contentId) { mutableStateOf(R12ContentListState()) }

    fun load(refresh: Boolean = false, append: Boolean = false) {
        if (state.refreshing || state.appending) return
        val current = state
        state = state.loading(refresh, append)
        scope.launch {
            val page = if (append) ((current.page?.page ?: 1) + 1).toInt() else 1
            val cursor = if (append) current.page?.nextCursor else null
            val result = when (kind) {
                R12ContentListKind.REVIEWS -> api.reviews(
                    accessToken = accessToken,
                    id = contentId,
                    page = page,
                    cursor = cursor,
                    sort = "updatedAt:desc",
                )
                R12ContentListKind.ANALYTICS -> api.analytics(
                    accessToken = accessToken,
                    id = contentId,
                    page = page,
                    cursor = cursor,
                    sort = "updatedAt:desc",
                )
                else -> error("只读详情页类型无效")
            }
            when (result) {
                is R07CallResult.Success -> state = state.loaded(result.data, append)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    state = state.failed(result)
                }
            }
        }
    }

    LaunchedEffect(contentId) { load() }
    if (kind == R12ContentListKind.ANALYTICS && state.phase == R12ContentListPhase.CONTENT) {
        R12AnalyticsScaffold(
            screenTag = screenTag,
            title = title,
            subtitle = subtitle,
            page = ContentPageResource(state.items, requireNotNull(state.page)),
            refreshing = state.refreshing,
            partialFailure = state.partialFailure,
            onBack = onBack,
            onRefresh = { load(refresh = true) },
        )
    } else {
        R12ContentListScaffold(
            screenTag = screenTag,
            title = title,
            subtitle = subtitle,
            kind = kind,
            state = state,
            onBack = onBack,
            onRefresh = { load(refresh = true) },
            onAppend = { load(append = true) },
            primaryAction = null,
            statusOptions = emptyList(),
            onStatusSelected = {},
            onContentSelected = {},
            onEdit = null,
            onReviews = null,
            onAnalytics = null,
            onOnline = null,
            onOffline = null,
            onDelete = null,
            identityVerified = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R12ContentListScaffold(
    screenTag: String,
    title: String,
    subtitle: String,
    kind: R12ContentListKind,
    state: R12ContentListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAppend: () -> Unit,
    primaryAction: Pair<String, () -> Unit>?,
    statusOptions: List<Pair<String?, String>>,
    onStatusSelected: (String?) -> Unit,
    onContentSelected: (String) -> Unit,
    onEdit: ((ContentResource) -> Unit)?,
    onReviews: ((String) -> Unit)?,
    onAnalytics: ((String) -> Unit)?,
    onOnline: ((ContentResource) -> Unit)?,
    onOffline: ((ContentResource) -> Unit)?,
    onDelete: ((ContentResource) -> Unit)?,
    identityVerified: Boolean,
) {
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(screenTag),
        topBar = {
            TopAppBar(
                title = { Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) } },
                navigationIcon = { HhyBackButton(onBack) },
            )
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (state.phase) {
            R12ContentListPhase.LOADING -> R12ListSkeleton(padding)
            R12ContentListPhase.CONTENT, R12ContentListPhase.EMPTY -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                item {
                    R12ListHeader(
                        resultCount = state.items.size,
                        refreshing = state.refreshing,
                        primaryAction = primaryAction,
                        onRefresh = onRefresh,
                    )
                }
                if (statusOptions.isNotEmpty()) {
                    item { R12StatusFilters(statusOptions, state.selectedStatus, onStatusSelected) }
                }
                state.notice?.let { notice -> item { R12Notice(notice, state.partialFailure) } }
                if (state.partialFailure && state.notice == null) {
                    item { R12Notice("部分内容未能更新，当前内容仍可查看", true) }
                }
                if (state.items.isEmpty()) {
                    item { R12EmptyCard(kind, primaryAction?.second) }
                } else {
                    items(state.items, key = ContentResource::id) { content ->
                        R12ContentCard(
                            content = content,
                            kind = kind,
                            actionInFlight = state.actionContentId == content.id,
                            onOpen = { onContentSelected(content.id) },
                            onEdit = onEdit,
                            onReviews = onReviews,
                            onAnalytics = onAnalytics,
                            onOnline = onOnline,
                            onOffline = onOffline,
                            onDelete = onDelete,
                            identityVerified = identityVerified,
                        )
                    }
                }
                if (state.appending) item { Box(Modifier.fillMaxWidth().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(HhySize.StandardProgress)) } }
                else if (state.canLoadMore) item { OutlinedButton(onClick = onAppend, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
            }
            else -> R12ListFailure(state.phase, padding, onBack, onRefresh)
        }
    }
}

@Composable
private fun R12ListHeader(
    resultCount: Int,
    refreshing: Boolean,
    primaryAction: Pair<String, () -> Unit>?,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("内容管理", color = HhyColors.TextInverse, fontWeight = FontWeight.SemiBold)
                Text("当前显示 $resultCount 条真实内容", color = HhyColors.TextInverse.copy(alpha = 0.84f), style = MaterialTheme.typography.bodySmall)
            }
            if (primaryAction != null) Button(onClick = primaryAction.second) { Text(primaryAction.first) }
            else R12InverseOutlinedButton(onClick = onRefresh, enabled = !refreshing)
        }
    }
}

@Composable
private fun R12StatusFilters(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        options.forEach { (value, label) ->
            AssistChip(
                onClick = { onSelected(value) },
                label = { Text(label, color = if (selected == value) HhyColors.TextInverse else HhyColors.TextPrimary) },
                leadingIcon = if (selected == value) ({ HhyIcon(HhyIcons.Check, null, tint = HhyColors.TextInverse) }) else null,
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = if (selected == value) HhyColors.BrandPrimary else HhyColors.Surface,
                ),
            )
        }
    }
}

@Composable
private fun R12ContentCard(
    content: ContentResource,
    kind: R12ContentListKind,
    actionInFlight: Boolean,
    onOpen: () -> Unit,
    onEdit: ((ContentResource) -> Unit)?,
    onReviews: ((String) -> Unit)?,
    onAnalytics: ((String) -> Unit)?,
    onOnline: ((ContentResource) -> Unit)?,
    onOffline: ((ContentResource) -> Unit)?,
    onDelete: ((ContentResource) -> Unit)?,
    identityVerified: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = kind != R12ContentListKind.REVIEWS, onClick = onOpen),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                R12ContentMedia(content)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                        R12Tag(contentTypeLabel(content.contentType), HhyColors.SoftBlue, HhyColors.BrandPrimary)
                        R12Tag(contentStatusLabel(content.reviewStatus ?: content.status), R12StatusColor(content), HhyColors.TextPrimary)
                    }
                    Text(content.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    content.summary?.takeIf(String::isNotBlank)?.let { Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(content.updatedTimeLabel(), color = HhyColors.TextTertiary, style = MaterialTheme.typography.labelSmall)
                }
                if (kind != R12ContentListKind.REVIEWS) HhyIcon(HhyIcons.ChevronRight, null, tint = HhyColors.TextTertiary)
            }
            if (kind == R12ContentListKind.CONTENTS) content.statistics?.let { R12CompactStatistics(it) }
            if (kind == R12ContentListKind.REVIEWS) {
                R12ReviewStatus(content)
            }
            if (actionInFlight) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    CircularProgressIndicator(Modifier.size(HhySize.StandardProgress))
                    Text("正在提交操作", color = HhyColors.TextSecondary)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                ) {
                    if (onEdit != null) OutlinedButton(onClick = { onEdit(content) }) { Text("继续编辑") }
                    if (onReviews != null) TextButton(onClick = { onReviews(content.id) }) { Text("审核记录") }
                    if (onAnalytics != null) TextButton(onClick = { onAnalytics(content.id) }) { Text("内容数据") }
                    if (onOnline != null && content.canGoOnline(identityVerified)) Button(onClick = { onOnline(content) }) { Text("上架") }
                    if (onOffline != null && content.canGoOffline()) OutlinedButton(onClick = { onOffline(content) }) { Text("下架") }
                    if (onDelete != null && content.canDelete()) TextButton(onClick = { onDelete(content) }) { HhyIcon(HhyIcons.Delete, null, tint = HhyColors.Error); Text("删除", color = HhyColors.Error) }
                }
            }
        }
    }
}

@Composable
private fun R12ContentMedia(content: ContentResource) {
    val url = content.secureMediaUrl()
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = content.title,
            modifier = Modifier.size(HhySize.AppLogo).clip(RoundedCornerShape(HhyRadius.Tag)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            modifier = Modifier.size(HhySize.AppLogo),
            shape = RoundedCornerShape(HhyRadius.Tag),
            color = HhyColors.SoftBlue,
        ) { Box(contentAlignment = Alignment.Center) { HhyIcon(HhyIcons.Applications, null, tint = HhyColors.BrandPrimary) } }
    }
}

@Composable
private fun R12CompactStatistics(value: cc.orbexa.hhy.network.ContentStatisticsResource) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        R12Metric("浏览", value.viewCount)
        R12Metric("收藏", value.favoriteCount)
        R12Metric("分享", value.shareCount)
        R12Metric("联系", value.contactAccessCount)
    }
}

@Composable
private fun R12ReviewStatus(content: ContentResource) {
    Surface(shape = RoundedCornerShape(HhyRadius.Tag), color = R12StatusColor(content)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhyIcon(HhyIcons.Pending, null, tint = HhyColors.BrandPrimary)
            Column(Modifier.weight(1f)) {
                Text(contentStatusLabel(content.reviewStatus ?: content.status), fontWeight = FontWeight.SemiBold)
                Text("状态更新时间：${content.updatedTimeLabel()}", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R12AnalyticsScaffold(
    screenTag: String,
    title: String,
    subtitle: String,
    page: ContentPageResource,
    refreshing: Boolean,
    partialFailure: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val content = page.items.firstOrNull()
    val statistics = content?.statistics
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(screenTag),
        topBar = { TopAppBar(title = { Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) } }, navigationIcon = { HhyBackButton(onBack) }) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary), shape = RoundedCornerShape(HhyRadius.LargeCard)) {
                    Row(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(content?.title ?: "内容数据", color = HhyColors.TextInverse, fontWeight = FontWeight.SemiBold)
                            Text("仅展示服务端已返回指标", color = HhyColors.TextInverse.copy(alpha = 0.84f), style = MaterialTheme.typography.bodySmall)
                        }
                        R12InverseOutlinedButton(onClick = onRefresh, enabled = !refreshing)
                    }
                }
            }
            if (partialFailure) item { R12Notice("部分数据未能更新，当前结果仍可查看", true) }
            if (statistics == null) {
                item { R12EmptyCard(R12ContentListKind.ANALYTICS, null) }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                            R12MetricCard("浏览次数", statistics.viewCount, Modifier.weight(1f))
                            R12MetricCard("收藏次数", statistics.favoriteCount, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                            R12MetricCard("分享次数", statistics.shareCount, Modifier.weight(1f))
                            R12MetricCard("联系查看", statistics.contactAccessCount, Modifier.weight(1f))
                        }
                        statistics.conversationCount?.let { R12MetricCard("发起会话", it, Modifier.fillMaxWidth()) }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
                        Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            HhyIcon(HhyIcons.Analytics, null, Modifier.size(HhySpacing.Xxl), HhyColors.BrandPrimary)
                            Text("暂无趋势序列", fontWeight = FontWeight.SemiBold)
                            Text("趋势数据暂未提供，请稍后查看。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun R12InverseOutlinedButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(HhySize.Hairline, HhyColors.TextInverse.copy(alpha = 0.78f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = HhyColors.TextInverse,
            disabledContentColor = HhyColors.TextInverse.copy(alpha = 0.48f),
        ),
    ) {
        HhyIcon(HhyIcons.Refresh, "刷新", tint = HhyColors.TextInverse)
        Text("刷新")
    }
}

@Composable
private fun R12MetricCard(label: String, value: Long, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
        }
    }
}

@Composable
private fun R12Metric(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
        Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun R12Tag(label: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = background) {
        Text(label, modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun R12Notice(message: String, isError: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.Tag), color = if (isError) HhyColors.ErrorSoft else HhyColors.SuccessSoft) {
        Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            HhyIcon(if (isError) HhyIcons.Error else HhyIcons.Check, null, tint = if (isError) HhyColors.Error else HhyColors.Success)
            Text(message, color = HhyColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun R12EmptyCard(kind: R12ContentListKind, primaryAction: (() -> Unit)?) {
    val (title, message) = when (kind) {
        R12ContentListKind.CONTENTS -> "暂无发布内容" to "发布后可在这里查看审核状态和内容数据"
        R12ContentListKind.DRAFTS -> "草稿箱为空" to "尚未保存需要继续编辑的内容"
        R12ContentListKind.REVIEWS -> "暂无审核记录" to "内容进入审核流程后会显示真实状态"
        R12ContentListKind.ANALYTICS -> "暂无内容数据" to "平台产生真实统计后会在这里展示"
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.LargeCard)) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            HhyIcon(if (kind == R12ContentListKind.ANALYTICS) HhyIcons.Analytics else HhyIcons.Applications, null, tint = HhyColors.BrandPrimary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            primaryAction?.let { Button(onClick = it) { Text("立即发布") } }
        }
    }
}

@Composable
private fun R12ListSkeleton(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        repeat(4) {
            Surface(modifier = Modifier.fillMaxWidth().height(HhySize.AppLogo + HhySpacing.Xxl), shape = RoundedCornerShape(HhyRadius.NormalCard), color = HhyColors.Surface) {}
        }
    }
}

@Composable
private fun R12ListFailure(
    phase: R12ContentListPhase,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val (title, message) = when (phase) {
        R12ContentListPhase.FORBIDDEN -> "无法查看此页面" to "当前账号没有相应权限"
        R12ContentListPhase.NOT_FOUND -> "内容不存在" to "内容可能已删除或链接失效"
        R12ContentListPhase.OFFLINE -> "网络不可用" to "请检查网络连接后重试"
        else -> "页面暂时无法加载" to "请稍后重试"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HhyIcon(if (phase == R12ContentListPhase.OFFLINE) HhyIcons.Information else HhyIcons.Error, null, Modifier.size(HhySpacing.Xxl), HhyColors.BrandPrimary)
        Text(title, modifier = Modifier.padding(top = HhySpacing.Md), fontWeight = FontWeight.SemiBold)
        Text(message, modifier = Modifier.padding(top = HhySpacing.Xs), color = HhyColors.TextSecondary)
        if (phase !in setOf(R12ContentListPhase.FORBIDDEN, R12ContentListPhase.NOT_FOUND)) Button(onClick = onRetry, modifier = Modifier.padding(top = HhySpacing.Lg)) { Text("重新加载") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun R12ActionDialog(intent: R12ActionIntent, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val (title, message, confirm) = when (intent.action) {
        R12PendingAction.ONLINE -> Triple("确认上架", "上架后内容将重新向平台用户展示。", "确认上架")
        R12PendingAction.OFFLINE -> Triple("确认下架", "下架后内容将停止公开展示，关联曝光也会暂停。", "确认下架")
        R12PendingAction.DELETE -> Triple("确认删除草稿", "删除后草稿将无法继续编辑，此操作不可撤销。", "确认删除")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(intent.content.title, fontWeight = FontWeight.SemiBold); Text(message, color = HhyColors.TextSecondary) } },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun R12PublishTypeDialog(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择发布类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                Text("请选择要发布的内容类型", color = HhyColors.TextSecondary)
                r12PublishContentTypes().forEach { (type, label) ->
                    OutlinedButton(onClick = { onSelected(type) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun r12PublishContentTypes(): List<Pair<String, String>> = listOf(
    "PROJECT" to "项目",
    "APP" to "App",
    "GROUP_CHAT" to "群聊",
    "TEAM_LEADER" to "团队长",
)

private fun R12StatusColor(content: ContentResource) = when (content.reviewStatus ?: content.status) {
    "ONLINE", "APPROVED" -> HhyColors.SuccessSoft
    "REJECTED", "BANNED" -> HhyColors.ErrorSoft
    "PENDING_REVIEW", "REVIEWING" -> HhyColors.WarningSoft
    else -> HhyColors.SoftBlue
}
