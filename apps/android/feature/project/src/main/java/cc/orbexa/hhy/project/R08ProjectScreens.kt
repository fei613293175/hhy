package cc.orbexa.hhy.project

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR08Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08ContactInput
import cc.orbexa.hhy.network.R08CreateProjectRequest
import cc.orbexa.hhy.network.R08DirectConversationRequest
import cc.orbexa.hhy.network.R08FavoriteRequest
import cc.orbexa.hhy.network.R08PatchProjectRequest
import cc.orbexa.hhy.media.MediaUploadSheet
import kotlinx.coroutines.launch

private val projectSorts = listOf("createdAt:desc" to "最新发布", "updatedAt:desc" to "最近更新")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R08ProjectListScreen(
    api: ContractR08Api,
    accessToken: String,
    onBack: () -> Unit,
    onProjectSelected: (String) -> Unit,
    onCreateProject: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val projects = remember { mutableStateListOf<ContentResource>() }
    var sort by rememberSaveable { mutableStateOf(projectSorts.first().first) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(R08ProjectPhase.LOADING) }
    var failure by remember { mutableStateOf<R08ProjectFailure?>(null) }

    fun load(reset: Boolean) {
        scope.launch {
            phase = R08ProjectPhase.LOADING
            failure = null
            when (val result = api.projects(accessToken, cursor = if (reset) null else nextCursor, sort = sort)) {
                is R07CallResult.Success -> {
                    if (reset) projects.clear()
                    val known = projects.mapTo(mutableSetOf()) { it.id }
                    projects.addAll(result.data.items.filter { it.contentType == "PROJECT" && known.add(it.id) })
                    nextCursor = result.data.page.nextCursor
                    hasMore = result.data.page.canLoadMore()
                    phase = if (projects.isEmpty()) R08ProjectPhase.EMPTY else R08ProjectPhase.CONTENT
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    failure = result.toR08Failure()
                    phase = failure!!.phase
                }
            }
        }
    }

    LaunchedEffect(sort) { load(true) }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r08.project.list.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text("项目") }, navigationIcon = { HhyBackButton(onBack) }) },
        floatingActionButton = {
            Button(modifier = Modifier.testTag("r08.project.create"), onClick = onCreateProject) {
                Text("发布项目")
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = HhySpacing.Lg,
                end = HhySpacing.Lg,
                top = HhySpacing.Md,
                bottom = HhySize.TopAppBarHeight + HhySpacing.Xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Text("发现值得合作的项目", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("页面内容均来自已上线项目", color = HhyColors.TextSecondary)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        projectSorts.forEach { (value, label) ->
                            FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(label) })
                        }
                    }
                }
            }
            when (phase) {
                R08ProjectPhase.LOADING -> items(3) { ProjectSkeleton() }
                R08ProjectPhase.EMPTY -> item { ProjectStateCard("暂无项目", "当前筛选下还没有已上线项目", "重新加载") { load(true) } }
                R08ProjectPhase.CONTENT -> {
                    items(projects, key = { it.id }) { project -> ProjectListCard(project) { onProjectSelected(project.id) } }
                    if (hasMore) item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { load(false) }) { Text("加载更多") } }
                }
                else -> item { FailureCard(failure, onBack) { load(true) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R08ProjectDetailScreen(
    api: ContractR08Api,
    accessToken: String,
    projectId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onConversationReady: (String, Long, ContentResource) -> Unit = { _, _, _ -> },
    onShare: (String) -> Unit = {},
    onInvalidFeedback: (String, List<String>) -> Unit = { _, _ -> },
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { R08IntentKeys() }
    var project by remember { mutableStateOf<ContentResource?>(null) }
    var phase by remember { mutableStateOf(R08ProjectPhase.LOADING) }
    var failure by remember { mutableStateOf<R08ProjectFailure?>(null) }
    var contact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun accept(result: R07CallResult.Failure) {
        if (result.statusCode == 401) onSessionExpired()
        failure = result.toR08Failure()
        phase = failure!!.phase
    }

    fun load() {
        scope.launch {
            phase = R08ProjectPhase.LOADING
            when (val result = api.project(accessToken, projectId)) {
                is R07CallResult.Success -> { project = result.data; failure = null; phase = R08ProjectPhase.CONTENT }
                is R07CallResult.Failure -> accept(result)
            }
        }
    }

    LaunchedEffect(projectId) { load() }
    contact?.let { (channel, value) ->
        R08SecureWindowEffect()
        ModalBottomSheet(onDismissRequest = { contact = null }) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Text("获取联系方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(contactChannelLabel(channel), color = HhyColors.BrandPrimary)
                Text(value, style = MaterialTheme.typography.titleMedium)
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    copyText(context, "联系方式", value)
                    message = "联系方式已复制"
                    contact = null
                }) { Text("复制联系方式") }
                Text("请安全使用联系方式，谨防诈骗", color = HhyColors.TextSecondary)
            }
        }
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r08.project.detail.${phase.name.lowercase()}"),
        topBar = {
            TopAppBar(
                title = { Text("项目详情") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    TextButton(
                        modifier = Modifier.testTag("r13.action.project.invalid-feedback"),
                        enabled = project?.contactsMasked?.isNotEmpty() == true,
                        onClick = { project?.let { onInvalidFeedback(it.title, it.contactsMasked.map { contact -> contact.channel }) } },
                    ) { Text("反馈") }
                    TextButton(
                        modifier = Modifier.testTag("r13.action.project.share"),
                        enabled = project != null,
                        onClick = { project?.title?.let(onShare) },
                    ) { Text("分享") }
                },
            )
        },
        bottomBar = {
            project?.let { item ->
                Surface(shadowElevation = HhyElevation.Dialog) {
                    Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            val publisherId = item.publisher?.userId ?: return@OutlinedButton
                            val body = "$publisherId:${item.id}"
                            scope.launch {
                                when (val result = api.direct(accessToken, keys.forBody("direct", body), R08DirectConversationRequest(publisherId, item.id))) {
                                    is R07CallResult.Success -> {
                                        keys.consume("direct", body)
                                        onConversationReady(result.data.id, result.data.version, item)
                                    }
                                    is R07CallResult.Failure -> accept(result)
                                }
                            }
                        }, enabled = item.publisher?.userId != null && item.publisher?.userId != currentUserId) { Text("在线私聊") }
                        Button(modifier = Modifier.weight(1f).testTag("r08.project.contact"), onClick = {
                            val channel = item.contactsMasked.firstOrNull { it.available }?.channel ?: return@Button
                            scope.launch {
                                when (val result = api.accessContact(accessToken, item.id, channel, keys.forBody("contact", "${item.id}:$channel"))) {
                                    is R07CallResult.Success -> {
                                        keys.consume("contact", "${item.id}:$channel")
                                        contact = result.data.channel to result.data.value
                                    }
                                    is R07CallResult.Failure -> accept(result)
                                }
                            }
                        }, enabled = item.contactsMasked.any { it.available }) { Text("获取联系方式") }
                    }
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (phase) {
            R08ProjectPhase.LOADING -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            R08ProjectPhase.CONTENT -> project?.let { item ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    item { ProjectHero(item) }
                    message?.let { text -> item { Surface(color = HhyColors.SuccessSoft, shape = RoundedCornerShape(HhyRadius.NormalCard)) { Text(text, Modifier.fillMaxWidth().padding(HhySpacing.Md), color = HhyColors.Success) } } }
                    item { ProjectSection("项目简介") { Text(item.description.orEmpty().ifBlank { item.summary.orEmpty() }) } }
                    if (item.publisher != null) item { PublisherSection(item) }
                    item { StatisticsSection(item) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                val body = "${item.id}:${item.version}"
                                scope.launch {
                                    when (val result = api.favorite(accessToken, item.id, keys.forBody("favorite", body), R08FavoriteRequest(expectedVersion = item.version))) {
                                        is R07CallResult.Success -> { project = result.data; keys.consume("favorite", body); message = "已收藏" }
                                        is R07CallResult.Failure -> accept(result)
                                    }
                                }
                            }) { Text("收藏") }
                            if (item.publisher?.userId == currentUserId) OutlinedButton(
                                modifier = Modifier.weight(1f).testTag("r08.project.edit"),
                                onClick = { onEdit(item.id) },
                            ) { Text("编辑项目") }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg)) { FailureCard(failure, onBack, ::load) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R08ProjectEditorScreen(
    api: ContractR08Api,
    mediaApi: ContractMediaApi,
    accessToken: String,
    projectId: String?,
    identityVerified: Boolean,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keys = remember { R08IntentKeys() }
    var form by remember { mutableStateOf(R08ProjectForm()) }
    var phase by remember { mutableStateOf(if (projectId == null) R08ProjectPhase.CONTENT else R08ProjectPhase.LOADING) }
    var failure by remember { mutableStateOf<R08ProjectFailure?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirm by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var showMediaUpload by remember { mutableStateOf(false) }
    var selectedMediaNames by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(projectId) {
        if (projectId == null) return@LaunchedEffect
        when (val result = api.project(accessToken, projectId)) {
            is R07CallResult.Success -> { form = R08ProjectForm.from(result.data); phase = R08ProjectPhase.CONTENT }
            is R07CallResult.Failure -> {
                if (result.statusCode == 401) onSessionExpired()
                failure = result.toR08Failure(); phase = failure!!.phase
            }
        }
    }

    fun submit() {
        errors = form.validate()
        if (errors.isNotEmpty()) return
        confirm = false
        phase = R08ProjectPhase.SUBMITTING
        val fingerprint = listOf(form.title, form.summary, form.description, form.categoryCode, form.regionCode, form.contactChannel, form.contactValue, form.mediaIds.joinToString(","), form.expectedVersion).joinToString("|")
        scope.launch {
            val contacts = form.contactValue.takeIf { it.isNotBlank() }?.let { listOf(R08ContactInput(form.contactChannel, it)) }.orEmpty()
            val result = if (projectId == null) {
                api.create(accessToken, keys.forBody("create", fingerprint), R08CreateProjectRequest(
                    title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(),
                    categoryCode = form.categoryCode.trim(), regionCode = form.regionCode.trim().ifBlank { null }, mediaIds = form.mediaIds, contacts = contacts,
                ))
            } else {
                api.patch(accessToken, projectId, keys.forBody("patch", fingerprint), R08PatchProjectRequest(
                    title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(),
                    categoryCode = form.categoryCode.trim(), regionCode = form.regionCode.trim().ifBlank { null }, mediaIds = form.mediaIds, contacts = contacts,
                    expectedVersion = requireNotNull(form.expectedVersion),
                ))
            }
            when (result) {
                is R07CallResult.Success -> { keys.consume(if (projectId == null) "create" else "patch", fingerprint); dirty = false; phase = R08ProjectPhase.SUCCESS; onSaved(result.data.id) }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    failure = result.toR08Failure(); errors = result.fieldErrors; phase = failure!!.phase
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(if (projectId == null) "确认创建项目" else "确认更新项目") },
        text = { Text("将提交当前项目资料；服务端会按实名、所有者权限和资源版本再次校验。") },
        confirmButton = { TextButton(onClick = ::submit) { Text("确认提交") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续编辑") } },
    )

    if (showMediaUpload) MediaUploadSheet(
        api = mediaApi,
        accessToken = accessToken,
        purpose = "CONTENT_PROJECT",
        maxConcurrentUploads = 2,
        acceptedTypes = arrayOf("image/*"),
        onAuthenticationRequired = onSessionExpired,
        onCompleted = { selections ->
            form = form.copy(mediaIds = selections.map { it.mediaId })
            selectedMediaNames = selections.map { it.displayName }
            dirty = true
            showMediaUpload = false
        },
        onDismiss = { showMediaUpload = false },
    )

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r08.project.editor.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text(if (projectId == null) "发布项目" else "编辑项目") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = {
            Surface(shadowElevation = HhyElevation.Dialog) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
                    enabled = identityVerified && phase != R08ProjectPhase.SUBMITTING && phase != R08ProjectPhase.LOADING,
                    onClick = { errors = form.validate(); if (errors.isEmpty()) confirm = true },
                ) { Text(if (phase == R08ProjectPhase.SUBMITTING) "正在提交" else if (projectId == null) "创建项目" else "保存修改") }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        if (phase == R08ProjectPhase.LOADING) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            if (!identityVerified) item { ProjectStateCard("需要完成实名认证", "项目发布和编辑要求账号已实名认证", "返回") { onBack() } }
            if (phase == R08ProjectPhase.CONFLICT) item { ProjectStateCard("项目资料已变化", "请返回详情刷新最新版本后再编辑，系统不会覆盖新数据", "返回详情") { onBack() } }
            item { Text(if (projectId == null) "填写项目资料" else "更新项目资料", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { FormField("项目标题", form.title, errors["title"], singleLine = true) { form = form.copy(title = it); dirty = true } }
            item { FormField("项目摘要", form.summary, errors["summary"]) { form = form.copy(summary = it); dirty = true } }
            item { FormField("详细说明", form.description, errors["description"], minLines = 4) { form = form.copy(description = it); dirty = true } }
            item {
                ProjectSection("分类与地区") {
                    FormField("项目分类", form.categoryInput, errors["categoryCode"], singleLine = true) { form = form.withCategoryInput(it); dirty = true }
                    Spacer(Modifier.height(HhySpacing.Sm))
                    FormField("所在地区（选填）", form.regionInput, errors["regionCode"], singleLine = true) { form = form.withRegionInput(it); dirty = true }
                }
            }
            item {
                ProjectSection("联系方式（选填）") {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        R08ProjectForm.CONTACT_CHANNELS.forEach { channel ->
                            FilterChip(selected = form.contactChannel == channel, onClick = { form = form.copy(contactChannel = channel); dirty = true }, label = { Text(contactChannelLabel(channel)) })
                        }
                    }
                    Spacer(Modifier.height(HhySpacing.Sm))
                    FormField("联系方式", form.contactValue, errors["contactValue"], singleLine = true) { form = form.copy(contactValue = it); dirty = true }
                    Text("提交后详情页只展示脱敏值，用户显式获取时才返回原值", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                ProjectSection("项目媒体") {
                    Text(
                        when {
                            selectedMediaNames.isNotEmpty() -> "已选择 ${selectedMediaNames.size} 张图片：${selectedMediaNames.joinToString("、")}"
                            form.mediaIds.isNotEmpty() -> "已绑定 ${form.mediaIds.size} 张项目图片"
                            else -> "当前未绑定媒体；页面不会用虚构图片代替真实项目资料。"
                        },
                        color = HhyColors.TextSecondary,
                    )
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showMediaUpload = true }) {
                        Text(if (form.mediaIds.isEmpty()) "选择项目图片" else "重新选择项目图片")
                    }
                    errors["mediaIds"]?.let { Text(it, color = HhyColors.Error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            failure?.let { item { FailureCard(it, onBack) { confirm = true } } }
            if (dirty) item { Text("存在未保存的修改", color = HhyColors.Warning) }
        }
    }
}

@Composable
private fun ProjectListCard(item: ContentResource, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("r08.project.card.${item.id}").clickable(onClick = onClick),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column {
            if (item.media.isNotEmpty()) Box(
                Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 2)
                    .background(Brush.linearGradient(listOf(HhyColors.BrandPrimary, HhyColors.BrandGradientEnd))),
                contentAlignment = Alignment.CenterStart,
            ) { Text(item.media.first().altText ?: "项目媒体", Modifier.padding(HhySpacing.Lg), color = HhyColors.TextInverse, fontWeight = FontWeight.SemiBold) }
            Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(item.status)
                }
                item.summary?.takeIf { it.isNotBlank() }?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    item.categoryCode?.let { Tag(projectCategoryLabel(it)) }
                    item.regionCode?.let { Tag(projectRegionLabel(it)) }
                }
                HorizontalDivider(color = HhyColors.Border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.publisher?.nickname ?: "发布者信息未提供", Modifier.weight(1f), color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    item.statistics?.let { Text("浏览 ${it.viewCount}  ·  收藏 ${it.favoriteCount}", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable private fun ProjectHero(item: ContentResource) = Card(shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) {
    Column {
        Box(
            Modifier.fillMaxWidth().height(
                if (item.media.isEmpty()) {
                    HhySize.TopAppBarHeight + HhySpacing.Xxxl + HhySpacing.Xs
                } else {
                    HhySize.TopAppBarHeight * 2 + HhySize.PrimaryButtonHeight
                },
            ).background(Brush.linearGradient(listOf(HhyColors.BrandPrimary, HhyColors.BrandGradientEnd))),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(item.media.firstOrNull()?.altText ?: "项目详情", Modifier.padding(HhySpacing.Lg), color = HhyColors.TextInverse, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Row { Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); StatusPill(item.status) }
            item.summary?.let { Text(it, color = HhyColors.TextSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { item.categoryCode?.let { Tag(projectCategoryLabel(it)) }; item.regionCode?.let { Tag(projectRegionLabel(it)) } }
        }
    }
}

@Composable private fun PublisherSection(item: ContentResource) = ProjectSection("发布者") {
    val publisher = requireNotNull(item.publisher)
    Text(publisher.nickname, fontWeight = FontWeight.SemiBold)
    publisher.bio?.let { Text(it, color = HhyColors.TextSecondary) }
    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { if (publisher.verified) Tag("已认证"); publisher.memberBadge?.let { Tag(it) } }
}

@Composable private fun StatisticsSection(item: ContentResource) = ProjectSection("项目数据") {
    val statistics = item.statistics
    if (statistics == null) Text("暂无统计数据", color = HhyColors.TextSecondary) else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("浏览", statistics.viewCount); Stat("收藏", statistics.favoriteCount); Stat("分享", statistics.shareCount); Stat("联系", statistics.contactAccessCount)
    }
}

@Composable private fun Stat(label: String, value: Long) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), fontWeight = FontWeight.Bold); Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }

@Composable private fun ProjectSection(title: String, content: @Composable () -> Unit) = Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), colors = CardDefaults.cardColors(HhyColors.Surface)) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() }
}

@Composable private fun StatusPill(status: String) = Surface(color = if (status == "ONLINE") HhyColors.SuccessSoft else HhyColors.WarningSoft, shape = RoundedCornerShape(HhyRadius.Pill)) { Text(if (status == "ONLINE") "已上线" else "状态更新中", Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = if (status == "ONLINE") HhyColors.Success else HhyColors.Warning, style = MaterialTheme.typography.labelSmall) }
@Composable private fun Tag(text: String) = Surface(color = HhyColors.SoftBlue, shape = RoundedCornerShape(HhyRadius.Tag)) { Text(text, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = HhyColors.BrandPrimary, style = MaterialTheme.typography.labelSmall) }

@Composable
private fun ProjectSkeleton() = Card(
    Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 3),
    shape = RoundedCornerShape(HhyRadius.LargeCard),
    colors = CardDefaults.cardColors(HhyColors.Surface),
) {
    Column(
        Modifier.padding(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Box(
            Modifier.fillMaxWidth(0.65f).height(HhySpacing.Xl)
                .background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag)),
        )
        Box(
            Modifier.fillMaxWidth().height(HhySize.InputHeight)
                .background(HhyColors.PageBackground, RoundedCornerShape(HhyRadius.Tag)),
        )
        Box(
            Modifier.fillMaxWidth(0.45f).height(HhySpacing.Lg)
                .background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag)),
        )
    }
}

@Composable private fun ProjectStateCard(title: String, body: String, action: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(body, color = HhyColors.TextSecondary); OutlinedButton(onClick = onClick) { Text(action) } } }

@Composable private fun FailureCard(failure: R08ProjectFailure?, onBack: () -> Unit, onRetry: () -> Unit) {
    val (title, body) = when (failure?.phase) {
        R08ProjectPhase.FORBIDDEN -> "无权访问" to "当前账号没有查看或操作该项目的权限"
        R08ProjectPhase.NOT_FOUND -> "项目不存在" to "项目可能已删除、下架或链接已失效"
        R08ProjectPhase.OFFLINE -> "网络不可用" to "请检查网络后重试，写操作不会离线提交"
        R08ProjectPhase.CONFLICT -> "数据已经变化" to "请重新加载服务端最新版本后继续"
        else -> "加载失败" to "暂时无法完成请求，请稍后重试"
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = HhyColors.TextSecondary); Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { OutlinedButton(onClick = onBack) { Text("返回") }; Button(onClick = onRetry) { Text("重试") } } } }
}

@Composable private fun FormField(label: String, value: String, error: String?, singleLine: Boolean = false, minLines: Int = 1, onValueChange: (String) -> Unit) = OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, isError = error != null, supportingText = error?.let { { Text(it) } })

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun R08SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
