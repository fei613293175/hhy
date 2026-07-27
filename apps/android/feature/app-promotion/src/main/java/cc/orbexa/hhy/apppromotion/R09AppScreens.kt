package cc.orbexa.hhy.apppromotion

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR09Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08ContactInput
import cc.orbexa.hhy.network.R08DirectConversationRequest
import cc.orbexa.hhy.network.R08FavoriteRequest
import cc.orbexa.hhy.network.R09CreateAppRequest
import cc.orbexa.hhy.network.R09PatchAppRequest
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

private val appSorts = listOf("createdAt:desc" to "最新发布", "updatedAt:desc" to "最近更新")
private val appCategories = listOf(
    "TOOLS" to "实用工具",
    "SOCIAL" to "社交沟通",
    "BUSINESS" to "商务办公",
    "LIFESTYLE" to "生活服务",
    "EDUCATION" to "教育学习",
    "ENTERTAINMENT" to "影音娱乐",
)
private val appPlatforms = listOf(
    "ANDROID" to "Android",
    "IOS" to "iOS",
    "WEB" to "网页应用",
    "MULTI" to "多平台",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R09AppListScreen(
    api: ContractR09Api,
    accessToken: String,
    onBack: () -> Unit,
    onAppSelected: (String) -> Unit,
    onCreateApp: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val apps = remember { mutableStateListOf<ContentResource>() }
    var sort by rememberSaveable { mutableStateOf(appSorts.first().first) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(R09AppPhase.LOADING) }
    var failure by remember { mutableStateOf<R09AppFailure?>(null) }

    fun load(reset: Boolean) {
        scope.launch {
            phase = R09AppPhase.LOADING
            when (val result = api.apps(accessToken, if (reset) null else nextCursor, sort = sort)) {
                is R07CallResult.Success -> {
                    if (reset) apps.clear()
                    val known = apps.mapTo(mutableSetOf()) { it.id }
                    apps.addAll(result.data.items.filter { it.contentType == "APP" && known.add(it.id) })
                    nextCursor = result.data.page.nextCursor
                    hasMore = result.data.page.canLoadMore()
                    failure = null
                    phase = if (apps.isEmpty()) R09AppPhase.EMPTY else R09AppPhase.CONTENT
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    failure = result.toR09Failure()
                    phase = failure!!.phase
                }
            }
        }
    }

    LaunchedEffect(sort) { load(true) }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r09.app.list.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text("应用") }, navigationIcon = { HhyBackButton(onBack) }) },
        floatingActionButton = { Button(modifier = Modifier.testTag("r09.app.create"), onClick = onCreateApp) { Text("推广App") } },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = HhySpacing.Lg, end = HhySpacing.Lg, top = HhySpacing.Md, bottom = HhySize.TopAppBarHeight + HhySpacing.Xxxl),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(HhyColors.BrandPrimary),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(HhySize.AppLogo),
                            color = HhyColors.Surface,
                            shape = RoundedCornerShape(HhyRadius.NormalCard),
                        ) {
                            HhyIcon(
                                HhyIcons.Applications,
                                contentDescription = null,
                                modifier = Modifier.padding(HhySpacing.Lg),
                                tint = HhyColors.BrandPrimary,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                            Text("发现好用的应用", color = HhyColors.TextInverse, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("浏览审核通过的真实App推广内容", color = HhyColors.TextInverse.copy(alpha = 0.84f))
                        }
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    appSorts.forEach { (value, label) -> FilterChip(sort == value, { sort = value }, { Text(label) }) }
                }
            }
            when (phase) {
                R09AppPhase.LOADING -> items(3) { AppSkeleton() }
                R09AppPhase.EMPTY -> item { StateCard("暂无应用", "当前还没有已上线的App推广内容", "重新加载") { load(true) } }
                R09AppPhase.CONTENT -> {
                    items(apps, key = { it.id }) { app -> AppListCard(app) { onAppSelected(app.id) } }
                    if (hasMore) item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { load(false) }) { Text("加载更多") } }
                }
                else -> item { FailureCard(failure, onBack) { load(true) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R09AppDetailScreen(
    api: ContractR09Api,
    accessToken: String,
    appId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onConversationReady: (String, ContentResource) -> Unit = { _, _ -> },
    onShare: (String) -> Unit = {},
    onInvalidFeedback: (String, List<String>) -> Unit = { _, _ -> },
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { R09IntentKeys() }
    var app by remember { mutableStateOf<ContentResource?>(null) }
    var phase by remember { mutableStateOf(R09AppPhase.LOADING) }
    var failure by remember { mutableStateOf<R09AppFailure?>(null) }
    var contact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun fail(result: R07CallResult.Failure) {
        if (result.statusCode == 401) onSessionExpired()
        failure = result.toR09Failure()
        phase = failure!!.phase
    }
    fun load() {
        scope.launch {
            phase = R09AppPhase.LOADING
            when (val result = api.app(accessToken, appId)) {
                is R07CallResult.Success -> { app = result.data; failure = null; phase = R09AppPhase.CONTENT }
                is R07CallResult.Failure -> fail(result)
            }
        }
    }

    LaunchedEffect(appId) { load() }
    contact?.let { (channel, value) ->
        SecureWindowEffect()
        ModalBottomSheet(onDismissRequest = { contact = null }) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Text("获取联系方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(contactChannelLabel(channel), color = HhyColors.BrandPrimary)
                Text(value, style = MaterialTheme.typography.titleMedium)
                Button(modifier = Modifier.fillMaxWidth(), onClick = { copyText(context, "联系方式", value); contact = null; message = "联系方式已复制" }) { Text("复制联系方式") }
                Text("请安全使用联系方式，谨防诈骗", color = HhyColors.TextSecondary)
            }
        }
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r09.app.detail.${phase.name.lowercase()}"),
        topBar = {
            TopAppBar(title = { Text("App详情") }, navigationIcon = { HhyBackButton(onBack) }, actions = {
                TextButton(enabled = app?.contactsMasked?.isNotEmpty() == true, onClick = { app?.let { onInvalidFeedback(it.title, it.contactsMasked.map { contact -> contact.channel }) } }) { Text("反馈") }
                TextButton(enabled = app != null, onClick = { app?.title?.let(onShare) }) { Text("分享") }
            })
        },
        bottomBar = {
            app?.let { item ->
                val facts = R09AppFacts.from(item)
                Surface(shadowElevation = HhyElevation.Dialog) {
                    Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = facts.website != null || item.contactsMasked.any { it.available },
                            onClick = {
                                if (facts.website != null) openHttps(context, facts.website) else {
                                    val channel = item.contactsMasked.firstOrNull { it.available }?.channel ?: return@OutlinedButton
                                    scope.launch {
                                        when (val result = api.accessContact(accessToken, item.id, channel, keys.forBody("contact", "${item.id}:$channel"))) {
                                            is R07CallResult.Success -> { keys.consume("contact", "${item.id}:$channel"); contact = result.data.channel to result.data.value }
                                            is R07CallResult.Failure -> fail(result)
                                        }
                                    }
                                }
                            },
                        ) { Text(if (facts.website != null) "访问官网" else "联系发布者") }
                        Button(
                            modifier = Modifier.weight(1f).testTag("r09.app.download"),
                            enabled = facts.downloadUrl != null,
                            onClick = { facts.downloadUrl?.let { openHttps(context, it) } },
                        ) { Text("立即体验") }
                    }
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (phase) {
            R09AppPhase.LOADING -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            R09AppPhase.CONTENT -> app?.let { item ->
                val facts = R09AppFacts.from(item)
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                    item { AppHero(item, facts) }
                    message?.let { text -> item { Surface(color = HhyColors.SuccessSoft, shape = RoundedCornerShape(HhyRadius.NormalCard)) { Text(text, Modifier.fillMaxWidth().padding(HhySpacing.Md), color = HhyColors.Success) } } }
                    if (item.media.isNotEmpty()) item { AppMediaGallery(item) }
                    item { AppSection("应用介绍") { Text(item.description.orEmpty().ifBlank { item.summary.orEmpty() }) } }
                    item { AppPublisherSection(item) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                val body = "${item.id}:${item.version}"
                                scope.launch {
                                    when (val result = api.favorite(accessToken, item.id, keys.forBody("favorite", body), R08FavoriteRequest(expectedVersion = item.version))) {
                                        is R07CallResult.Success -> { app = result.data; keys.consume("favorite", body); message = "已收藏" }
                                        is R07CallResult.Failure -> fail(result)
                                    }
                                }
                            }) { Text("收藏") }
                            if (item.publisher?.userId == currentUserId) OutlinedButton(modifier = Modifier.weight(1f).testTag("r09.app.edit"), onClick = { onEdit(item.id) }) { Text("编辑推广") }
                            else OutlinedButton(modifier = Modifier.weight(1f), enabled = item.publisher?.userId != null, onClick = {
                                val publisher = item.publisher?.userId ?: return@OutlinedButton
                                val body = "$publisher:${item.id}"
                                scope.launch {
                                    when (val result = api.direct(accessToken, keys.forBody("direct", body), R08DirectConversationRequest(publisher, item.id))) {
                                        is R07CallResult.Success -> {
                                            keys.consume("direct", body)
                                            onConversationReady(result.data.id, item)
                                        }
                                        is R07CallResult.Failure -> fail(result)
                                    }
                                }
                            }) { Text("在线私聊") }
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
fun R09AppEditorScreen(
    api: ContractR09Api,
    mediaApi: ContractMediaApi,
    accessToken: String,
    appId: String?,
    identityVerified: Boolean,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keys = remember { R09IntentKeys() }
    var form by remember { mutableStateOf(R09AppForm()) }
    var phase by remember { mutableStateOf(if (appId == null) R09AppPhase.CONTENT else R09AppPhase.LOADING) }
    var failure by remember { mutableStateOf<R09AppFailure?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirm by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var showMediaUpload by remember { mutableStateOf(false) }
    var mediaPreviews by remember { mutableStateOf<List<AppMediaPreview>>(emptyList()) }

    LaunchedEffect(appId) {
        if (appId == null) return@LaunchedEffect
        when (val result = api.app(accessToken, appId)) {
            is R07CallResult.Success -> {
                form = R09AppForm.from(result.data)
                mediaPreviews = result.data.media.map(AppMediaPreview::from)
                phase = R09AppPhase.CONTENT
            }
            is R07CallResult.Failure -> { if (result.statusCode == 401) onSessionExpired(); failure = result.toR09Failure(); phase = failure!!.phase }
        }
    }

    fun submit() {
        errors = form.validate()
        if (errors.isNotEmpty()) return
        confirm = false
        phase = R09AppPhase.SUBMITTING
        val fingerprint = listOf(form.appName, form.title, form.description, form.categoryCode, form.platform, form.versionText, form.downloadUrl, form.website, form.mediaIds.joinToString(), form.expectedVersion).joinToString("|")
        scope.launch {
            val contacts = form.contactValue.takeIf(String::isNotBlank)?.let { listOf(R08ContactInput(form.contactChannel, it)) }.orEmpty()
            val result = if (appId == null) api.create(accessToken, keys.forBody("create", fingerprint), R09CreateAppRequest(
                contentType = "APP",
                title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(),
                categoryCode = form.categoryCode.trim(), mediaIds = form.mediaIds, contacts = contacts, attributes = form.attributes(),
            )) else api.patch(accessToken, appId, keys.forBody("patch", fingerprint), R09PatchAppRequest(
                title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(),
                categoryCode = form.categoryCode.trim(), mediaIds = form.mediaIds, contacts = contacts, attributes = form.attributes(),
                expectedVersion = requireNotNull(form.expectedVersion),
            ))
            when (result) {
                is R07CallResult.Success -> { keys.consume(if (appId == null) "create" else "patch", fingerprint); dirty = false; phase = R09AppPhase.SUCCESS; onSaved(result.data.id) }
                is R07CallResult.Failure -> { if (result.statusCode == 401) onSessionExpired(); failure = result.toR09Failure(); errors = result.fieldErrors; phase = failure!!.phase }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(if (appId == null) "确认创建App推广" else "确认更新App推广") },
        text = { Text("将提交当前资料；服务端会复核实名认证、内容所有权、资源版本和外部链接安全性。") },
        confirmButton = { TextButton(onClick = ::submit) { Text("确认提交") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续编辑") } },
    )

    if (showMediaUpload) MediaUploadSheet(
        api = mediaApi,
        accessToken = accessToken,
        purpose = "CONTENT_APP_PROMOTION",
        maxConcurrentUploads = 2,
        acceptedTypes = arrayOf("image/*"),
        onAuthenticationRequired = onSessionExpired,
        onCompleted = { selections ->
            form = form.copy(mediaIds = selections.map { it.mediaId })
            mediaPreviews = selections.map(AppMediaPreview::from)
            dirty = true
            showMediaUpload = false
        },
        onDismiss = { showMediaUpload = false },
    )

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r09.app.editor.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text(if (appId == null) "发布App推广" else "编辑App推广") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = { Surface(shadowElevation = HhyElevation.Dialog) { Button(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            enabled = identityVerified && phase !in setOf(R09AppPhase.SUBMITTING, R09AppPhase.LOADING),
            onClick = { errors = form.validate(); if (errors.isEmpty()) confirm = true },
        ) { Text(if (phase == R09AppPhase.SUBMITTING) "正在提交" else if (appId == null) "创建推广" else "保存修改") } } },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        if (phase == R09AppPhase.LOADING) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            if (!identityVerified) item { StateCard("需要完成实名认证", "App推广发布和编辑要求账号已实名认证", "返回", onBack) }
            if (phase == R09AppPhase.CONFLICT) item { StateCard("推广资料已变化", "请返回详情刷新最新版本，系统不会覆盖新数据", "返回详情", onBack) }
            item {
                Card(colors = CardDefaults.cardColors(HhyColors.BrandPrimary), shape = RoundedCornerShape(HhyRadius.LargeCard)) {
                    Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        Text(if (appId == null) "创建App推广" else "更新App推广", color = HhyColors.TextInverse, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("只填写真实资料；这里不上传APK安装包", color = HhyColors.TextInverse.copy(alpha = 0.84f))
                    }
                }
            }
            item { AppSection("基础信息") {
                FormField("App名称", form.appName, errors["appName"], true) { form = form.copy(appName = it); dirty = true }
                FormField("推广标题", form.title, errors["title"], true) { form = form.copy(title = it); dirty = true }
                FormField("推广摘要（选填）", form.summary, errors["summary"]) { form = form.copy(summary = it); dirty = true }
                Text("App分类", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    options = appCategories,
                    selected = form.categoryCode,
                    unknownLabel = ::appCategoryLabel,
                    onSelected = { form = form.copy(categoryCode = it); dirty = true },
                )
                errors["categoryCode"]?.let { Text(it, color = HhyColors.Error) }
            } }
            item { AppSection("应用介绍") { FormField("详细介绍", form.description, errors["description"], minLines = 4) { form = form.copy(description = it); dirty = true } } }
            item { AppSection("平台与版本（选填）") {
                Text("支持平台", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    options = appPlatforms,
                    selected = form.platform,
                    unknownLabel = { appPlatformLabel(it) ?: "其他平台" },
                    onSelected = { form = form.copy(platform = it); dirty = true },
                )
                errors["platform"]?.let { Text(it, color = HhyColors.Error) }
                FormField("版本说明", form.versionText, errors["versionText"], true) { form = form.copy(versionText = it); dirty = true }
            } }
            item { AppSection("访问方式（选填）") {
                FormField("HTTPS下载或邀请链接", form.downloadUrl, errors["downloadUrl"], true) { form = form.copy(downloadUrl = it); dirty = true }
                FormField("HTTPS官网链接", form.website, errors["website"], true) { form = form.copy(website = it); dirty = true }
                Text("仅接受HTTPS外部链接；不会把第三方链接包装成合伙云安装包。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            } }
            item { AppSection("应用图片") {
                Text(if (form.mediaIds.isEmpty()) "尚未选择真实图片；页面不会生成虚构App图标或截图。" else "已绑定 ${form.mediaIds.size} 张真实应用图片", color = HhyColors.TextSecondary)
                if (mediaPreviews.isNotEmpty()) AppEditorMediaPreviews(mediaPreviews, form.appName)
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { showMediaUpload = true }) { Text(if (form.mediaIds.isEmpty()) "选择App图标或截图" else "重新选择应用图片") }
                errors["mediaIds"]?.let { Text(it, color = HhyColors.Error) }
            } }
            item { AppSection("联系方式（选填）") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    R09AppForm.CONTACT_CHANNELS.forEach { channel -> FilterChip(form.contactChannel == channel, { form = form.copy(contactChannel = channel); dirty = true }, { Text(contactChannelLabel(channel)) }) }
                }
                FormField("联系方式", form.contactValue, errors["contactValue"], true) { form = form.copy(contactValue = it); dirty = true }
                Text("详情页只展示脱敏值，用户显式获取后才短暂显示原值。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            } }
            failure?.let { item { FailureCard(it, onBack) { confirm = true } } }
            if (dirty) item { Text("存在未保存的修改", color = HhyColors.Warning) }
        }
    }
}

@Composable
private fun AppListCard(item: ContentResource, onClick: () -> Unit) {
    val facts = R09AppFacts.from(item)
    Card(
        Modifier.fillMaxWidth().testTag("r09.app.card.${item.id}").clickable(onClick = onClick),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(HhyColors.Surface),
        elevation = CardDefaults.cardElevation(HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBrandMark(facts.appName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(facts.appName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(item.status)
                    }
                    Text(item.summary.orEmpty().ifBlank { item.description.orEmpty() }, color = HhyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        item.categoryCode?.let { Tag(appCategoryLabel(it)) }
                        appPlatformLabel(facts.platform)?.let { Tag(it) }
                        facts.versionText?.let { Tag(it) }
                    }
                }
            }
            if (item.media.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 2).testTag("r09.app.media.list"),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                ) {
                    items(item.media, key = { it.id }) { media ->
                        AppArtwork(
                            media = media,
                            appName = facts.appName,
                            modifier = Modifier.width(HhySize.TopAppBarHeight * 2 + HhySpacing.Xxl).height(HhySize.TopAppBarHeight * 2),
                            tag = "r09.app.media.list.preview.${media.id}",
                        )
                    }
                }
            }
            HorizontalDivider(color = HhyColors.Border)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                HhyIcon(HhyIcons.Profile, contentDescription = null, modifier = Modifier.size(HhySpacing.Xl), tint = HhyColors.TextSecondary)
                Text(item.publisher?.nickname ?: "发布者信息未提供", Modifier.weight(1f), color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                item.statistics?.let { Text("浏览 ${it.viewCount}", color = HhyColors.TextTertiary, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun AppBrandMark(appName: String) {
    Surface(
        modifier = Modifier.size(HhySize.AppLogo).testTag("r09.app.brand"),
        color = HhyColors.SoftBlue,
        shape = RoundedCornerShape(HhyRadius.NormalCard),
    ) {
        Box(contentAlignment = Alignment.Center) {
            HhyIcon(
                HhyIcons.Applications,
                contentDescription = "$appName 应用标识",
                modifier = Modifier.size(HhySize.MinimumTouchTarget),
                tint = HhyColors.BrandPrimary,
            )
        }
    }
}

@Composable
private fun AppHero(item: ContentResource, facts: R09AppFacts) = Card(
    shape = RoundedCornerShape(HhyRadius.LargeCard),
    colors = CardDefaults.cardColors(HhyColors.Surface),
) {
    Column {
        Box(Modifier.fillMaxWidth().height(HhySpacing.Sm).background(HhyColors.BrandPrimary))
        Row(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppArtwork(
                media = item.media.firstOrNull(),
                appName = facts.appName,
                modifier = Modifier.size(HhySize.AppLogo + HhySpacing.Xxl),
                tag = "r09.app.media.detail.cover",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(facts.appName, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    StatusPill(item.status)
                }
                item.summary?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    item.categoryCode?.let { Tag(appCategoryLabel(it)) }
                    appPlatformLabel(facts.platform)?.let { Tag(it) }
                    facts.versionText?.let { Tag(it) }
                }
            }
        }
    }
}

@Composable
private fun AppMediaGallery(item: ContentResource) = AppSection("应用截图") {
    Text("发布者上传的 ${item.media.size} 张真实应用图片", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 3).testTag("r09.app.media.detail"),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        items(item.media, key = { it.id }) { media ->
            AppArtwork(
                media = media,
                appName = R09AppFacts.from(item).appName,
                modifier = Modifier.width(HhySize.AppLogo + HhySpacing.Xxl).height(HhySize.TopAppBarHeight * 3),
                tag = "r09.app.media.detail.${media.id}",
            )
        }
    }
}

@Composable
private fun AppPublisherSection(item: ContentResource) = AppSection("发布信息") {
    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(HhySize.MinimumTouchTarget), color = HhyColors.SoftBlue, shape = CircleShape) {
            HhyIcon(HhyIcons.Profile, contentDescription = null, modifier = Modifier.padding(HhySpacing.Md), tint = HhyColors.BrandPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(item.publisher?.nickname ?: "发布者信息未提供", fontWeight = FontWeight.SemiBold)
            item.publisher?.bio?.let { Text(it, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        if (item.publisher?.verified == true) HhyIcon(HhyIcons.Verified, contentDescription = "已认证", tint = HhyColors.BrandPrimary)
    }
    item.statistics?.let { statistics ->
        HorizontalDivider(color = HhyColors.Border)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            AppStatistic("浏览", statistics.viewCount, Modifier.weight(1f))
            AppStatistic("收藏", statistics.favoriteCount, Modifier.weight(1f))
            AppStatistic("分享", statistics.shareCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppStatistic(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppArtwork(
    media: MediaItemResource?,
    appName: String,
    modifier: Modifier,
    tag: String,
) {
    val imageUrl = (media?.thumbnailUrl ?: media?.url)?.takeIf(::secureHttps)
    var imageState by remember(imageUrl) { mutableStateOf(if (imageUrl == null) "unavailable" else "loading") }
    Box(
        modifier = modifier.clip(RoundedCornerShape(HhyRadius.NormalCard)).background(HhyColors.SoftBlue).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        HhyIcon(
            HhyIcons.Applications,
            contentDescription = null,
            modifier = Modifier.padding(HhySpacing.Lg),
            tint = HhyColors.BrandPrimary,
        )
        imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = media?.altText ?: "$appName 应用图片",
                modifier = Modifier.fillMaxSize().testTag("$tag.$imageState"),
                contentScale = ContentScale.Crop,
                onSuccess = { imageState = "loaded" },
                onError = { imageState = "error" },
            )
        }
    }
}

private data class AppMediaPreview(
    val id: String,
    val url: String?,
    val altText: String?,
) {
    companion object {
        fun from(media: MediaItemResource) = AppMediaPreview(
            id = media.id,
            url = (media.thumbnailUrl ?: media.url).takeIf(::secureHttps),
            altText = media.altText,
        )

        fun from(selection: MediaUploadSelection) = AppMediaPreview(
            id = selection.mediaId,
            url = selection.readUrl?.takeIf(::secureHttps),
            altText = selection.displayName,
        )
    }
}

@Composable
private fun AppEditorMediaPreviews(previews: List<AppMediaPreview>, appName: String) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 2).testTag("r09.app.media.editor"),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        items(previews, key = { it.id }) { preview ->
            Box(
                modifier = Modifier.width(HhySize.AppLogo + HhySpacing.Xxl).height(HhySize.TopAppBarHeight * 2)
                    .clip(RoundedCornerShape(HhyRadius.NormalCard)).background(HhyColors.SoftBlue),
                contentAlignment = Alignment.Center,
            ) {
                HhyIcon(HhyIcons.Applications, contentDescription = null, tint = HhyColors.BrandPrimary)
                preview.url?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = preview.altText ?: "$appName 应用图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable private fun AppSection(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() } }
@Composable private fun StatusPill(status: String) = Surface(color = if (status == "ONLINE") HhyColors.SuccessSoft else HhyColors.WarningSoft, shape = RoundedCornerShape(HhyRadius.Pill)) { Text(if (status == "ONLINE") "已上线" else "状态更新中", Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = if (status == "ONLINE") HhyColors.Success else HhyColors.Warning, style = MaterialTheme.typography.labelSmall) }
@Composable private fun Tag(text: String) = Surface(color = HhyColors.SoftBlue, shape = RoundedCornerShape(HhyRadius.Tag)) { Text(text, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = HhyColors.BrandPrimary, style = MaterialTheme.typography.labelSmall) }
@Composable private fun AppSkeleton() = Card(Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 2), shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Box(Modifier.fillMaxWidth(0.6f).height(HhySpacing.Xl).background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag))); Box(Modifier.fillMaxWidth().height(HhySize.InputHeight).background(HhyColors.PageBackground, RoundedCornerShape(HhyRadius.Tag))) } }
@Composable private fun StateCard(title: String, body: String, action: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = HhyColors.TextSecondary); OutlinedButton(onClick = onClick) { Text(action) } } }

@Composable private fun FailureCard(failure: R09AppFailure?, onBack: () -> Unit, onRetry: () -> Unit) {
    val (title, body) = when (failure?.phase) {
        R09AppPhase.FORBIDDEN -> "无权访问" to "当前账号没有查看或操作该推广内容的权限"
        R09AppPhase.NOT_FOUND -> "内容不存在" to "App推广可能已删除、下架或链接失效"
        R09AppPhase.OFFLINE -> "网络不可用" to "请检查网络后重试，写操作不会离线提交"
        R09AppPhase.CONFLICT -> "数据已经变化" to "请加载服务端最新版本后继续"
        else -> "加载失败" to "暂时无法完成请求，请稍后重试"
    }
    StateCard(title, body, "重试", onRetry)
}

@Composable private fun FormField(label: String, value: String, error: String?, singleLine: Boolean = false, minLines: Int = 1, onValueChange: (String) -> Unit) = OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines, isError = error != null, supportingText = error?.let { { Text(it) } })

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<Pair<String, String>>,
    selected: String,
    unknownLabel: (String) -> String,
    onSelected: (String) -> Unit,
) {
    val visible = if (selected.isNotBlank() && options.none { it.first == selected }) {
        listOf(selected to unknownLabel(selected)) + options
    } else {
        options
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
        maxItemsInEachRow = 3,
    ) {
        visible.forEach { (value, label) ->
            FilterChip(selected == value, { onSelected(value) }, { Text(label) })
        }
    }
}

private fun copyText(context: Context, label: String, value: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun openHttps(context: Context, value: String) {
    if (!secureHttps(value)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable private fun SecureWindowEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
