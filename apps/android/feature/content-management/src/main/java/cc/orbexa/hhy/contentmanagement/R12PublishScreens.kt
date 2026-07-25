package cc.orbexa.hhy.contentmanagement

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import cc.orbexa.hhy.network.R12SubmitContentRequest
import cc.orbexa.hhy.network.UserSelfResource
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R12PublishCenterScreen(
    api: ContractR12Api,
    accessToken: String,
    user: UserSelfResource,
    onBack: () -> Unit,
    onOpenIdentity: () -> Unit,
    onOpenMyContents: () -> Unit,
    onCreateContent: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val eligibility = remember(user) { r12PublishEligibility(user) }
    var phase by remember { mutableStateOf(R12PublishPhase.LOADING) }
    var page by remember { mutableStateOf<ContentPageResource?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun load() {
        if (refreshing) return
        refreshing = true
        if (page == null) phase = R12PublishPhase.LOADING
        scope.launch {
            when (val result = api.contents(accessToken = accessToken, pageSize = 100)) {
                is R07CallResult.Success -> {
                    page = result.data
                    phase = if (result.data.items.isEmpty()) R12PublishPhase.EMPTY else R12PublishPhase.CONTENT
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    if (page == null) phase = result.toR12PublishPhase()
                }
            }
            refreshing = false
        }
    }

    LaunchedEffect(api, accessToken) { load() }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r12.publish.center.${phase.name.lowercase()}"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("发布中心", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    TextButton(onClick = onOpenMyContents) { Text("我的发布") }
                },
            )
        },
    ) { padding ->
        when {
            phase == R12PublishPhase.LOADING && page == null -> PublishCenterLoading(padding)
            page == null -> PublishFailure(phase, padding, onBack, ::load)
            else -> PublishCenterContent(
                padding = padding,
                eligibility = eligibility,
                overview = r12PublishOverview(requireNotNull(page)),
                refreshing = refreshing,
                onRefresh = ::load,
                onOpenIdentity = onOpenIdentity,
                onCreateContent = onCreateContent,
            )
        }
    }
}

@Composable
private fun PublishCenterContent(
    padding: PaddingValues,
    eligibility: R12PublishEligibility,
    overview: R12PublishOverview,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenIdentity: () -> Unit,
    onCreateContent: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
    ) {
        item { PublishSummary(eligibility, overview, refreshing, onRefresh, onOpenIdentity) }
        item { Text("选择发布类型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                r12PublishOptions().chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                        row.forEach { option ->
                            PublishOptionCard(
                                option = option,
                                enabled = eligibility.canPublish,
                                modifier = Modifier.weight(1f),
                                onClick = { onCreateContent(option.contentType) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Surface(
                color = HhyColors.Surface,
                shape = RoundedCornerShape(HhyRadius.NormalCard),
                tonalElevation = HhyElevation.Card,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    verticalAlignment = Alignment.Top,
                ) {
                    HhyIcon(HhyIcons.Information, null, tint = HhyColors.BrandPrimary)
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        Text("发布须知", fontWeight = FontWeight.SemiBold)
                        Text("内容提交后由平台审核，发布数量和可用状态以服务端实时校验为准。", color = HhyColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishSummary(
    eligibility: R12PublishEligibility,
    overview: R12PublishOverview,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenIdentity: () -> Unit,
) = Surface(
    color = HhyColors.BrandPrimary,
    shape = RoundedCornerShape(HhyRadius.LargeCard),
) {
    Column(
        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Surface(shape = CircleShape, color = HhyColors.Surface.copy(alpha = 0.18f)) {
                HhyIcon(HhyIcons.Publish, null, Modifier.padding(HhySpacing.Md), HhyColors.TextInverse)
            }
            Column(Modifier.weight(1f)) {
                Text(eligibility.title, color = HhyColors.TextInverse, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(eligibility.detail, color = HhyColors.TextInverse.copy(alpha = 0.84f), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = if (eligibility.canPublish) onRefresh else onOpenIdentity) {
                Text(if (eligibility.canPublish) "刷新" else "去认证", color = HhyColors.TextInverse)
            }
        }
        HorizontalDivider(color = HhyColors.TextInverse.copy(alpha = 0.24f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PublishMetric("全部", overview.total.toString())
            PublishMetric("已上架", overview.online.toString())
            PublishMetric("审核中", overview.pending.toString())
            PublishMetric("待完善", overview.drafts.toString())
        }
        if (refreshing) CircularProgressIndicator(
            modifier = Modifier.size(HhySpacing.Xl).align(Alignment.End),
            color = HhyColors.TextInverse,
            strokeWidth = HhySize.Hairline,
        )
    }
}

@Composable
private fun PublishMetric(label: String, value: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, color = HhyColors.TextInverse, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text(label, color = HhyColors.TextInverse.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PublishOptionCard(
    option: R12PublishOption,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val (icon, accent, background) = publishOptionStyle(option.contentType)
    Card(
        modifier = modifier.heightIn(min = 142.dp).clickable(enabled = enabled, onClick = onClick)
            .testTag("publish.center.${option.contentType.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Surface(shape = RoundedCornerShape(HhyRadius.Tag), color = background) {
                HhyIcon(icon, null, Modifier.padding(HhySpacing.Sm), if (enabled) accent else HhyColors.TextTertiary)
            }
            Text(option.title, fontWeight = FontWeight.Bold, color = if (enabled) HhyColors.TextPrimary else HhyColors.TextTertiary)
            Text(
                option.description,
                color = HhyColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun publishOptionStyle(contentType: String): Triple<ImageVector, Color, Color> = when (contentType) {
    "PROJECT" -> Triple(HhyIcons.Projects, HhyColors.RewardOrange, HhyColors.WarningSoft)
    "APP" -> Triple(HhyIcons.Applications, HhyColors.BrandPrimary, HhyColors.SoftBlue)
    "GROUP_CHAT" -> Triple(HhyIcons.Groups, HhyColors.Success, HhyColors.SuccessSoft)
    else -> Triple(HhyIcons.Profile, HhyColors.BrandTertiary, HhyColors.SoftBlue)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R12PublishPreviewScreen(
    api: ContractR12Api,
    accessToken: String,
    contentId: String,
    user: UserSelfResource,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onConfirmSubmit: (ContentResource, String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember(contentId) { mutableStateOf(R12PublishPhase.LOADING) }
    var content by remember(contentId) { mutableStateOf<ContentResource?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var submitCandidate by remember(contentId) { mutableStateOf<ContentResource?>(null) }

    fun load() {
        if (refreshing) return
        refreshing = true
        if (content == null) phase = R12PublishPhase.LOADING
        scope.launch {
            when (val result = api.content(accessToken, contentId)) {
                is R07CallResult.Success -> {
                    content = result.data
                    phase = if (result.data.publisher?.userId == user.id) R12PublishPhase.CONTENT else R12PublishPhase.FORBIDDEN
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    if (content == null) phase = result.toR12PublishPhase()
                }
            }
            refreshing = false
        }
    }

    LaunchedEffect(api, accessToken, contentId) { load() }
    val current = content?.takeIf { phase == R12PublishPhase.CONTENT }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r12.publish.preview.${phase.name.lowercase()}"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("发布预览", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { TextButton(onClick = ::load, enabled = !refreshing) { Text("刷新") } },
            )
        },
        bottomBar = {
            current?.let { item ->
                PublishPreviewActions(
                    editEnabled = !refreshing,
                    submitEnabled = !refreshing && item.canSubmitFromPreview(user),
                    onEdit = { onEdit(item.contentType, item.id) },
                    onSubmit = { submitCandidate = item },
                )
            }
        },
    ) { padding ->
        when {
            phase == R12PublishPhase.LOADING && content == null -> PublishCenterLoading(padding)
            current == null -> PublishFailure(phase, padding, onBack, ::load)
            else -> PublishPreviewContent(current, padding)
        }
    }

    submitCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { submitCandidate = null },
            title = { Text("确认提交审核") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Text("${r12ContentTypeLabel(item.contentType)}「${item.title}」将进入平台审核。")
                    Text(
                        "提交后需等待平台处理，期间不能直接上架。本次未填写补充原因，审核状态变化时可能收到通知。",
                        color = HhyColors.TextSecondary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idempotencyKey = r12SubmitIdempotencyKey(item.id, item.version)
                        submitCandidate = null
                        onConfirmSubmit(item, idempotencyKey)
                    },
                ) { Text("确认提交") }
            },
            dismissButton = {
                TextButton(onClick = { submitCandidate = null }) { Text("继续检查") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R12PublishResultScreen(
    api: ContractR12Api,
    accessToken: String,
    contentId: String,
    expectedVersion: Long,
    idempotencyKey: String,
    initialTitle: String,
    initialContentType: String,
    reason: String? = null,
    onBack: () -> Unit,
    onReturnPreview: () -> Unit,
    onOpenMyContents: () -> Unit,
    onBackToPublishCenter: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember(contentId, expectedVersion, idempotencyKey) { mutableStateOf(R12SubmitPhase.SUBMITTING) }
    var title by remember(contentId) { mutableStateOf(initialTitle) }
    var contentType by remember(contentId) { mutableStateOf(initialContentType) }
    var contentStatus by remember(contentId) { mutableStateOf<String?>(null) }
    var retryAfterSeconds by remember(contentId) { mutableStateOf<Long?>(null) }
    var writing by remember(contentId) { mutableStateOf(false) }
    var checking by remember(contentId) { mutableStateOf(false) }
    var started by remember(contentId, expectedVersion, idempotencyKey) { mutableStateOf(false) }

    fun applyLatestContent(item: ContentResource, keepConflict: Boolean) {
        title = item.title
        contentType = item.contentType
        contentStatus = item.status
        phase = if (keepConflict) R12SubmitPhase.CONFLICT else r12SubmitPhaseFromContent(item.status)
    }

    suspend fun readLatest(keepConflict: Boolean) {
        when (val latest = api.content(accessToken, contentId)) {
            is R07CallResult.Success -> applyLatestContent(latest.data, keepConflict)
            is R07CallResult.Failure -> when (latest.statusCode) {
                401 -> onSessionExpired()
                403 -> phase = R12SubmitPhase.FORBIDDEN
                404 -> phase = R12SubmitPhase.NOT_FOUND
                null -> if (!keepConflict) phase = R12SubmitPhase.OFFLINE
                else -> if (!keepConflict) phase = R12SubmitPhase.ERROR
            }
        }
    }

    fun queryLatest() {
        if (writing || checking) return
        checking = true
        scope.launch {
            readLatest(keepConflict = false)
            checking = false
        }
    }

    fun submit(retrying: Boolean) {
        if (writing || checking) return
        writing = true
        retryAfterSeconds = null
        phase = if (retrying) R12SubmitPhase.RETRYING else R12SubmitPhase.SUBMITTING
        scope.launch {
            when (
                val result = api.submit(
                    accessToken = accessToken,
                    id = contentId,
                    idempotencyKey = idempotencyKey,
                    request = R12SubmitContentRequest(expectedVersion = expectedVersion, reason = reason),
                )
            ) {
                is R07CallResult.Success -> {
                    contentStatus = result.data.submittedStatus()
                    when (val submitted = result.data) {
                        is cc.orbexa.hhy.network.R12CopyContentResult.Content -> {
                            title = submitted.resource.title
                            contentType = submitted.resource.contentType
                        }
                        is cc.orbexa.hhy.network.R12CopyContentResult.Command -> Unit
                    }
                    phase = R12SubmitPhase.SUCCESS
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) {
                        onSessionExpired()
                    } else {
                        phase = result.toR12SubmitPhase()
                        retryAfterSeconds = result.retryAfterSeconds
                        if (result.statusCode == 409) readLatest(keepConflict = true)
                    }
                }
            }
            writing = false
        }
    }

    LaunchedEffect(contentId, expectedVersion, idempotencyKey) {
        if (!started) {
            started = true
            submit(retrying = false)
        }
    }
    BackHandler(enabled = writing) {}

    val presentation = r12SubmitPresentation(phase, contentStatus, retryAfterSeconds)
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r12.publish.result.${phase.name.lowercase()}"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("提交结果", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { HhyBackButton(onBack, enabled = !writing) },
            )
        },
    ) { padding ->
        PublishResultContent(
            padding = padding,
            phase = phase,
            presentation = presentation,
            title = title,
            contentType = contentType,
            busy = writing || checking,
            onQueryLatest = ::queryLatest,
            onRetrySameRequest = { submit(retrying = true) },
            onReturnPreview = onReturnPreview,
            onOpenMyContents = onOpenMyContents,
            onBackToPublishCenter = onBackToPublishCenter,
            onEdit = { onEdit(contentType, contentId) },
        )
    }
}

@Composable
private fun PublishResultContent(
    padding: PaddingValues,
    phase: R12SubmitPhase,
    presentation: R12SubmitPresentation,
    title: String,
    contentType: String,
    busy: Boolean,
    onQueryLatest: () -> Unit,
    onRetrySameRequest: () -> Unit,
    onReturnPreview: () -> Unit,
    onOpenMyContents: () -> Unit,
    onBackToPublishCenter: () -> Unit,
    onEdit: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = HhySpacing.Xl, vertical = HhySpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                PublishResultIcon(phase)
                Text(
                    presentation.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = HhyColors.TextPrimary,
                )
                Text(
                    presentation.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HhyColors.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (phase == R12SubmitPhase.PENDING) item { PublishReviewTimeline() }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = HhyColors.Surface,
                shape = RoundedCornerShape(HhyRadius.NormalCard),
                tonalElevation = HhyElevation.Card,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SoftBlue) {
                        HhyIcon(
                            publishOptionStyle(contentType).first,
                            null,
                            Modifier.padding(HhySpacing.Md),
                            HhyColors.BrandPrimary,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        Text(
                            title,
                            fontWeight = FontWeight.SemiBold,
                            color = HhyColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            r12ContentTypeLabel(contentType),
                            style = MaterialTheme.typography.bodySmall,
                            color = HhyColors.TextSecondary,
                        )
                    }
                    PreviewTag(
                        presentation.statusLabel,
                        resultAccent(phase),
                        resultBackground(phase),
                    )
                }
            }
        }
        item {
            PublishResultActions(
                phase = phase,
                busy = busy,
                onQueryLatest = onQueryLatest,
                onRetrySameRequest = onRetrySameRequest,
                onReturnPreview = onReturnPreview,
                onOpenMyContents = onOpenMyContents,
                onBackToPublishCenter = onBackToPublishCenter,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun PublishResultIcon(phase: R12SubmitPhase) {
    val icon = when (phase) {
        R12SubmitPhase.SUCCESS -> HhyIcons.Check
        R12SubmitPhase.PENDING, R12SubmitPhase.SUBMITTING, R12SubmitPhase.RETRYING,
        R12SubmitPhase.UNKNOWN, R12SubmitPhase.OFFLINE -> HhyIcons.Pending
        R12SubmitPhase.CONFLICT -> HhyIcons.Information
        else -> HhyIcons.Error
    }
    Surface(shape = CircleShape, color = resultBackground(phase), modifier = Modifier.size(112.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = HhyColors.Surface, modifier = Modifier.size(82.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    HhyIcon(icon, null, Modifier.size(52.dp), resultAccent(phase))
                }
            }
        }
    }
}

@Composable
private fun PublishReviewTimeline() = Surface(
    modifier = Modifier.fillMaxWidth(),
    color = HhyColors.Surface,
    shape = RoundedCornerShape(HhyRadius.NormalCard),
) {
    Row(
        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        verticalAlignment = Alignment.Top,
    ) {
        ReviewStep("提交成功", active = true, Modifier.weight(1f))
        ReviewStep("平台审核", active = true, Modifier.weight(1f))
        ReviewStep("结果更新", active = false, Modifier.weight(1f))
    }
}

@Composable
private fun ReviewStep(label: String, active: Boolean, modifier: Modifier) = Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
) {
    Surface(
        shape = CircleShape,
        color = if (active) HhyColors.BrandPrimary else HhyColors.Border,
        modifier = Modifier.size(HhySpacing.Md),
    ) {}
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = if (active) HhyColors.BrandPrimary else HhyColors.TextTertiary,
    )
}

@Composable
private fun PublishResultActions(
    phase: R12SubmitPhase,
    busy: Boolean,
    onQueryLatest: () -> Unit,
    onRetrySameRequest: () -> Unit,
    onReturnPreview: () -> Unit,
    onOpenMyContents: () -> Unit,
    onBackToPublishCenter: () -> Unit,
    onEdit: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
) {
    when (phase) {
        R12SubmitPhase.SUBMITTING, R12SubmitPhase.RETRYING -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight)) {
                CircularProgressIndicator(Modifier.size(HhySpacing.Lg), color = HhyColors.TextInverse, strokeWidth = HhySize.Hairline)
                Spacer(Modifier.width(HhySpacing.Sm))
                Text("处理中")
            }
        }
        R12SubmitPhase.SUCCESS -> {
            PrimaryResultButton("查看我的发布", busy, onOpenMyContents)
            SecondaryResultButton("返回发布中心", busy, onBackToPublishCenter)
        }
        R12SubmitPhase.PENDING -> {
            PrimaryResultButton("刷新审核状态", busy, onQueryLatest)
            SecondaryResultButton("查看我的发布", busy, onOpenMyContents)
        }
        R12SubmitPhase.REJECTED, R12SubmitPhase.FAILED -> {
            PrimaryResultButton("修改后重新提交", busy, onEdit)
            SecondaryResultButton("查看我的发布", busy, onOpenMyContents)
        }
        R12SubmitPhase.CONFLICT -> {
            PrimaryResultButton("查看最新内容", busy, onReturnPreview)
            SecondaryResultButton("查看我的发布", busy, onOpenMyContents)
        }
        R12SubmitPhase.UNKNOWN, R12SubmitPhase.OFFLINE -> {
            PrimaryResultButton("查询最新状态", busy, onQueryLatest)
            SecondaryResultButton("查看我的发布", busy, onOpenMyContents)
        }
        R12SubmitPhase.ERROR -> {
            PrimaryResultButton("使用原请求继续", busy, onRetrySameRequest)
            SecondaryResultButton("返回发布预览", busy, onReturnPreview)
        }
        R12SubmitPhase.FORBIDDEN, R12SubmitPhase.NOT_FOUND -> {
            PrimaryResultButton("返回发布中心", busy, onBackToPublishCenter)
        }
    }
}

@Composable
private fun PrimaryResultButton(label: String, busy: Boolean, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = !busy,
    modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
) { Text(label) }

@Composable
private fun SecondaryResultButton(label: String, busy: Boolean, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick,
    enabled = !busy,
    modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
) { Text(label) }

private fun resultAccent(phase: R12SubmitPhase): Color = when (phase) {
    R12SubmitPhase.SUCCESS -> HhyColors.Success
    R12SubmitPhase.PENDING, R12SubmitPhase.SUBMITTING, R12SubmitPhase.RETRYING,
    R12SubmitPhase.UNKNOWN, R12SubmitPhase.OFFLINE -> HhyColors.BrandPrimary
    R12SubmitPhase.CONFLICT, R12SubmitPhase.ERROR -> HhyColors.Warning
    else -> HhyColors.Error
}

private fun resultBackground(phase: R12SubmitPhase): Color = when (phase) {
    R12SubmitPhase.SUCCESS -> HhyColors.SuccessSoft
    R12SubmitPhase.PENDING, R12SubmitPhase.SUBMITTING, R12SubmitPhase.RETRYING,
    R12SubmitPhase.UNKNOWN, R12SubmitPhase.OFFLINE -> HhyColors.SoftBlue
    R12SubmitPhase.CONFLICT, R12SubmitPhase.ERROR -> HhyColors.WarningSoft
    else -> HhyColors.ErrorSoft
}

@Composable
private fun PublishPreviewContent(content: ContentResource, padding: PaddingValues) {
    val images = remember(content) { content.securePreviewMedia() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                shape = RoundedCornerShape(HhyRadius.NormalCard),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (images.isNotEmpty()) {
                        AsyncImage(
                            model = images.first().url,
                            contentDescription = images.first().altText ?: "${content.title}内容图片",
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            PreviewTag(r12ContentTypeLabel(content.contentType), HhyColors.BrandPrimary, HhyColors.SoftBlue)
                            PreviewTag(r12ContentStatusLabel(content.status), statusColor(content.status), statusBackground(content.status))
                        }
                        Text(content.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HhyColors.TextPrimary)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                        ) {
                            content.categoryCode?.takeIf(String::isNotBlank)?.let { PreviewTag(it, HhyColors.BrandPrimary, HhyColors.SoftBlue) }
                            content.regionCode?.takeIf(String::isNotBlank)?.let { PreviewTag(it, HhyColors.Success, HhyColors.SuccessSoft) }
                        }
                        content.summary?.takeIf(String::isNotBlank)?.let {
                            Text(it, color = HhyColors.TextSecondary)
                        }
                    }
                }
            }
        }
        if (images.size > 1) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                ) {
                    images.drop(1).forEach { image ->
                        AsyncImage(
                            model = image.url,
                            contentDescription = image.altText ?: "${content.title}内容图片",
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(HhyRadius.Tag)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
        content.description?.takeIf(String::isNotBlank)?.let { description ->
            item { PreviewSection("内容介绍") { Text(description, color = HhyColors.TextPrimary) } }
        }
        item {
            PreviewSection("发布信息") {
                content.previewFacts().forEach { (label, value) -> PreviewFactRow(label, value) }
            }
        }
        if (content.contactsMasked.isNotEmpty()) {
            item {
                PreviewSection("联系方式") {
                    content.contactsMasked.forEach { contact ->
                        val value = contact.maskedValue ?: if (contact.available) "已配置" else "暂不可用"
                        PreviewFactRow(contactChannelName(contact.channel), value)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSection(title: String, content: @Composable () -> Unit) = Surface(
    color = HhyColors.Surface,
    shape = RoundedCornerShape(HhyRadius.NormalCard),
    tonalElevation = HhyElevation.Card,
) {
    Column(
        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun PreviewFactRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
    verticalAlignment = Alignment.Top,
) {
    Text(label, Modifier.width(76.dp), color = HhyColors.TextSecondary)
    Text(value, Modifier.weight(1f), color = HhyColors.TextPrimary)
}

@Composable
private fun PreviewTag(label: String, foreground: Color, background: Color) = Surface(
    color = background,
    shape = RoundedCornerShape(HhyRadius.Tag),
) {
    Text(label, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = foreground, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PublishPreviewActions(
    editEnabled: Boolean,
    submitEnabled: Boolean,
    onEdit: () -> Unit,
    onSubmit: () -> Unit,
) = Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(HhySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        OutlinedButton(
            onClick = onEdit,
            enabled = editEnabled,
            modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
        ) { Text("返回修改") }
        Button(
            onClick = onSubmit,
            enabled = submitEnabled,
            modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
        ) { Text("确认提交") }
    }
}

@Composable
private fun PublishCenterLoading(padding: PaddingValues) = Box(
    Modifier.fillMaxSize().padding(padding),
    contentAlignment = Alignment.Center,
) {
    CircularProgressIndicator(color = HhyColors.BrandPrimary)
}

@Composable
private fun PublishFailure(
    phase: R12PublishPhase,
    padding: PaddingValues,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val copy = when (phase) {
        R12PublishPhase.FORBIDDEN -> "无法访问" to "当前账号没有访问此内容的权限"
        R12PublishPhase.NOT_FOUND -> "内容不存在" to "内容可能已删除或链接已经失效"
        R12PublishPhase.OFFLINE -> "网络不可用" to "请检查网络连接后重试"
        else -> "加载失败" to "暂时无法完成请求，请稍后重试"
    }
    Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
            Column(
                Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                HhyIcon(if (phase == R12PublishPhase.OFFLINE) HhyIcons.Information else HhyIcons.Error, null, Modifier.size(HhySpacing.Xxl), HhyColors.BrandPrimary)
                Text(copy.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(copy.second, color = HhyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(onClick = onBack) { Text("返回") }
                    if (phase !in setOf(R12PublishPhase.FORBIDDEN, R12PublishPhase.NOT_FOUND)) {
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }
        }
    }
}

private fun contactChannelName(value: String): String = when (value) {
    "PHONE" -> "手机号"
    "WECHAT" -> "微信"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    else -> "联系方式"
}

private fun statusColor(value: String): Color = when (value) {
    "ONLINE", "APPROVED" -> HhyColors.Success
    "PENDING_REVIEW", "REVIEWING" -> HhyColors.Warning
    "REJECTED", "BANNED" -> HhyColors.Error
    else -> HhyColors.BrandPrimary
}

private fun statusBackground(value: String): Color = when (value) {
    "ONLINE", "APPROVED" -> HhyColors.SuccessSoft
    "PENDING_REVIEW", "REVIEWING" -> HhyColors.WarningSoft
    "REJECTED", "BANNED" -> HhyColors.ErrorSoft
    else -> HhyColors.SoftBlue
}
