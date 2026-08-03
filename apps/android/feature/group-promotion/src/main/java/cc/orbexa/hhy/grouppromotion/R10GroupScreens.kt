package cc.orbexa.hhy.grouppromotion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR10Api
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08DirectConversationRequest
import cc.orbexa.hhy.network.R08FavoriteRequest
import cc.orbexa.hhy.network.R10CreateGroupRequest
import cc.orbexa.hhy.network.R10PatchGroupRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val groupSorts = listOf("createdAt:desc" to "最新发布", "updatedAt:desc" to "最近更新")
private val groupPlatforms = listOf("WECHAT" to "微信群", "QQ" to "QQ群", "DINGTALK" to "钉钉群", "FEISHU" to "飞书群")
private val ownerChannels = listOf("WECHAT" to "微信", "PHONE" to "手机号", "QQ" to "QQ", "EMAIL" to "邮箱")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R10GroupListScreen(api: ContractR10Api, accessToken: String, onBack: () -> Unit, onGroupSelected: (String) -> Unit, onCreateGroup: () -> Unit, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    val groups = remember { mutableStateListOf<ContentResource>() }
    var sort by rememberSaveable { mutableStateOf(groupSorts.first().first) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(R10GroupPhase.LOADING) }
    var failure by remember { mutableStateOf<R10GroupFailure?>(null) }
    fun load(reset: Boolean) {
        scope.launch {
            phase = R10GroupPhase.LOADING
            when (val result = api.groups(accessToken, if (reset) null else cursor, sort = sort)) {
                is R07CallResult.Success -> {
                    if (reset) groups.clear()
                    val known = groups.mapTo(mutableSetOf()) { it.id }
                    groups.addAll(result.data.items.filter { it.contentType == "GROUP_CHAT" && known.add(it.id) })
                    cursor = result.data.page.nextCursor
                    hasMore = !cursor.isNullOrBlank()
                    failure = null
                    phase = if (groups.isEmpty()) R10GroupPhase.EMPTY else R10GroupPhase.CONTENT
                }
                is R07CallResult.Failure -> { if (result.statusCode == 401) onSessionExpired(); failure = result.toR10Failure(); phase = failure!!.phase }
            }
        }
    }
    LaunchedEffect(sort) { load(true) }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r10.group.list.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text("群聊") }, navigationIcon = { HhyBackButton(onBack) }) },
        floatingActionButton = { Button(modifier = Modifier.testTag("r10.group.create"), onClick = onCreateGroup) { Text("发布群聊") } },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item { GroupHeaderCard() }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { groupSorts.forEach { (value, label) -> FilterChip(sort == value, { sort = value }, { Text(label) }) } } }
            when (phase) {
                R10GroupPhase.LOADING -> items(3) { GroupSkeleton() }
                R10GroupPhase.EMPTY -> item { StateCard("暂无群聊", "当前还没有已上线的群聊推广内容", "重新加载") { load(true) } }
                R10GroupPhase.CONTENT -> {
                    items(groups, key = { it.id }) { group -> GroupListCard(group) { onGroupSelected(group.id) } }
                    if (hasMore) item { OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { load(false) }) { Text("加载更多") } }
                }
                else -> item { FailureCard(failure) { load(true) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R10GroupDetailScreen(api: ContractR10Api, accessToken: String, groupId: String, currentUserId: String, onBack: () -> Unit, onEdit: (String) -> Unit, onConversationReady: (String, Long, ContentResource) -> Unit = { _, _, _ -> }, onShare: (String) -> Unit = {}, onInvalidFeedback: (String, List<String>) -> Unit = { _, _ -> }, onReport: (String, Long) -> Unit = { _, _ -> }, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { R10IntentKeys() }
    var group by remember { mutableStateOf<ContentResource?>(null) }
    var phase by remember { mutableStateOf(R10GroupPhase.LOADING) }
    var failure by remember { mutableStateOf<R10GroupFailure?>(null) }
    var revealed by remember { mutableStateOf<Pair<String, String>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun fail(result: R07CallResult.Failure) { if (result.statusCode == 401) onSessionExpired(); failure = result.toR10Failure(); phase = failure!!.phase }
    fun load() { scope.launch { phase = R10GroupPhase.LOADING; when (val result = api.group(accessToken, groupId)) { is R07CallResult.Success -> { group = result.data; phase = R10GroupPhase.CONTENT }; is R07CallResult.Failure -> fail(result) } } }
    fun reveal(channel: String) { scope.launch { val body = "$groupId:$channel"; when (val result = api.accessContact(accessToken, groupId, channel, keys.forBody("contact", body))) { is R07CallResult.Success -> { keys.consume("contact", body); revealed = result.data.channel to result.data.value }; is R07CallResult.Failure -> fail(result) } } }
    LaunchedEffect(groupId) { load() }
    revealed?.let { (channel, value) -> ModalBottomSheet(onDismissRequest = { revealed = null }) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(if (channel == "JOIN_PASSWORD") "入群口令" else "群主联系方式", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.titleMedium); Button(modifier = Modifier.fillMaxWidth(), onClick = { copyText(context, value); revealed = null; message = "已复制" }) { Text("复制") }; Text("仅在你主动获取后显示，请勿向无关人员泄露", color = HhyColors.TextSecondary) } } }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r10.group.detail.${phase.name.lowercase()}"),
        topBar = { TopAppBar(title = { Text("群聊详情") }, navigationIcon = { HhyBackButton(onBack) }, actions = { TextButton(enabled = group != null, onClick = { group?.let { onReport(it.title, it.version) } }) { Text("举报") }; TextButton(enabled = group?.contactsMasked?.isNotEmpty() == true, onClick = { group?.let { onInvalidFeedback(it.title, it.contactsMasked.map { contact -> contact.channel }) } }) { Text("反馈") }; TextButton(enabled = group != null, onClick = { group?.title?.let(onShare) }) { Text("分享") } }) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (phase) {
            R10GroupPhase.LOADING -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            R10GroupPhase.CONTENT -> group?.let { item ->
                val facts = R10GroupFacts.from(item)
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                    item { GroupHero(item, facts) }
                    message?.let { value -> item { Surface(color = HhyColors.SuccessSoft, shape = RoundedCornerShape(HhyRadius.NormalCard)) { Text(value, Modifier.fillMaxWidth().padding(HhySpacing.Md), color = HhyColors.Success) } } }
                    if (item.media.isNotEmpty()) item { GroupMediaGallery(item) }
                    item { GroupSection("群聊介绍") { Text(item.description.orEmpty().ifBlank { item.summary.orEmpty() }); facts.joinRequirement?.let { Text("入群要求：$it", color = HhyColors.TextSecondary) } } }
                    item { GroupSection("入群方式") { facts.groupNo?.let { KeyValue("群号", it) }; item.contactsMasked.filter { it.available }.forEach { contact -> OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { reveal(contact.channel) }) { Text(if (contact.channel == "JOIN_PASSWORD") "获取入群口令 ${contact.maskedValue.orEmpty()}" else "联系群主 ${contact.maskedValue.orEmpty()}") } }; if (facts.groupNo == null && item.contactsMasked.none { it.available }) Text("发布者暂未提供可访问的入群方式", color = HhyColors.TextSecondary) } }
                    item { PublisherSection(item) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { OutlinedButton(modifier = Modifier.weight(1f), onClick = { val body = "${item.id}:${item.version}"; scope.launch { when (val result = api.favorite(accessToken, item.id, keys.forBody("favorite", body), R08FavoriteRequest(expectedVersion = item.version))) { is R07CallResult.Success -> { group = result.data; keys.consume("favorite", body); message = "已收藏" }; is R07CallResult.Failure -> fail(result) } } }) { Text("收藏") }; if (item.publisher?.userId == currentUserId) OutlinedButton(modifier = Modifier.weight(1f).testTag("r10.group.edit"), onClick = { onEdit(item.id) }) { Text("编辑") } else OutlinedButton(modifier = Modifier.weight(1f), enabled = item.publisher?.userId != null, onClick = { val peer = item.publisher?.userId ?: return@OutlinedButton; val body = "$peer:${item.id}"; scope.launch { when (val result = api.direct(accessToken, keys.forBody("direct", body), R08DirectConversationRequest(peer, item.id))) { is R07CallResult.Success -> { keys.consume("direct", body); onConversationReady(result.data.id, result.data.version, item) }; is R07CallResult.Failure -> fail(result) } } }) { Text("联系发布者") } } }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg)) { FailureCard(failure, ::load) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R10GroupEditorScreen(api: ContractR10Api, mediaApi: ContractMediaApi, accessToken: String, groupId: String?, identityVerified: Boolean, onBack: () -> Unit, onSaved: (String) -> Unit, onSessionExpired: () -> Unit) {
    val scope = rememberCoroutineScope()
    val keys = remember { R10IntentKeys() }
    var form by remember { mutableStateOf(R10GroupForm()) }
    var phase by remember { mutableStateOf(if (groupId == null) R10GroupPhase.CONTENT else R10GroupPhase.LOADING) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirm by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var upload by remember { mutableStateOf(false) }
    LaunchedEffect(groupId) { if (groupId != null) when (val result = api.group(accessToken, groupId)) { is R07CallResult.Success -> { form = R10GroupForm.from(result.data); phase = R10GroupPhase.CONTENT }; is R07CallResult.Failure -> { if (result.statusCode == 401) onSessionExpired(); phase = result.toR10Failure().phase } } }
    fun submit() {
        errors = form.validate(); if (errors.isNotEmpty()) return
        confirm = false; phase = R10GroupPhase.SUBMITTING
        val fingerprint = form.toString()
        scope.launch {
            val result = if (groupId == null) api.create(accessToken, keys.forBody("create", fingerprint), R10CreateGroupRequest(title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(), categoryCode = form.categoryCode.trim(), regionCode = form.regionCode.trim().ifBlank { null }, mediaIds = form.mediaIds, contacts = form.contacts(), attributes = form.attributes())) else api.patch(accessToken, groupId, keys.forBody("patch", fingerprint), R10PatchGroupRequest(title = form.title.trim(), summary = form.summary.trim().ifBlank { null }, description = form.description.trim(), categoryCode = form.categoryCode.trim(), regionCode = form.regionCode.trim().ifBlank { null }, mediaIds = form.mediaIds, contacts = form.contacts(), attributes = form.attributes(), expectedVersion = requireNotNull(form.expectedVersion)))
            when (result) { is R07CallResult.Success -> { keys.consume(if (groupId == null) "create" else "patch", fingerprint); dirty = false; onSaved(result.data.id) }; is R07CallResult.Failure -> { if (result.statusCode == 401) onSessionExpired(); errors = result.fieldErrors; phase = result.toR10Failure().phase } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(if (groupId == null) "确认发布群聊" else "确认更新群聊") }, text = { Text("将提交真实群资料、入群方式和群主联系方式，服务端会复核实名认证、资源所有权和数据版本。") }, confirmButton = { TextButton(onClick = ::submit) { Text("确认提交") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续编辑") } })
    if (upload) MediaUploadSheet(api = mediaApi, accessToken = accessToken, purpose = "CONTENT_GROUP_PROMOTION", maxConcurrentUploads = 2, acceptedTypes = arrayOf("image/*"), onAuthenticationRequired = onSessionExpired, onCompleted = { values: List<MediaUploadSelection> -> form = form.copy(mediaIds = values.map { it.mediaId }, qrMediaId = values.firstOrNull()?.mediaId.orEmpty()); dirty = true; upload = false }, onDismiss = { upload = false })
    Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r10.group.editor.${phase.name.lowercase()}"), topBar = { TopAppBar(title = { Text(if (groupId == null) "发布群聊" else "编辑群聊") }, navigationIcon = { HhyBackButton(onBack) }, actions = { TextButton(enabled = phase == R10GroupPhase.CONTENT && identityVerified, onClick = { errors = form.validate(); if (errors.isEmpty()) confirm = true }) { Text("提交") } }) }, containerColor = HhyColors.PageBackground) { padding ->
        if (!identityVerified) Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg)) { StateCard("需要实名认证", "完成实名认证后才能发布或编辑群聊", "返回", onBack) }
        else if (phase == R10GroupPhase.LOADING || phase == R10GroupPhase.SUBMITTING) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item { GroupSection("基础资料") { FormField("群聊标题", form.title, errors["title"]) { form = form.copy(title = it); dirty = true }; FormField("推广摘要（选填）", form.summary, errors["summary"]) { form = form.copy(summary = it); dirty = true }; FormField("群聊说明", form.description, errors["description"], 4) { form = form.copy(description = it); dirty = true }; FormField("分类", form.categoryCode, errors["categoryCode"]) { form = form.copy(categoryCode = it); dirty = true }; FormField("地区（选填）", form.regionCode, errors["regionCode"]) { form = form.copy(regionCode = it); dirty = true } } }
            item { GroupSection("群聊信息") { Text("群平台", style = MaterialTheme.typography.labelLarge); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { groupPlatforms.forEach { (value, label) -> FilterChip(form.platform == value, { form = form.copy(platform = value); dirty = true }, { Text(label) }) } }; errors["platform"]?.let { Text(it, color = HhyColors.Error) }; FormField("群规模（选填）", form.sizeRange, errors["sizeRange"]) { form = form.copy(sizeRange = it); dirty = true }; FormField("入群要求（选填）", form.joinRequirement, errors["joinRequirement"]) { form = form.copy(joinRequirement = it); dirty = true } } }
            item { GroupSection("真实入群方式") { FormField("HTTPS群链接（选填）", form.groupLink, errors["groupLink"]) { form = form.copy(groupLink = it); dirty = true }; FormField("群号（选填）", form.groupNo, errors["groupNo"]) { form = form.copy(groupNo = it); dirty = true }; FormField("入群口令（选填）", form.joinPassword, errors["joinPassword"]) { form = form.copy(joinPassword = it); dirty = true }; OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { upload = true }) { Text(if (form.qrMediaId.isBlank()) "上传群二维码或群图片" else "已选择 ${form.mediaIds.size} 张图片，重新选择") }; errors["joinChannel"]?.let { Text(it, color = HhyColors.Error) }; Text("入群口令单独加密保存，不会写入群号、入群要求或公开分享页。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) } }
            item { GroupSection("群主联系方式") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { ownerChannels.forEach { (value, label) -> FilterChip(form.ownerContactChannel == value, { form = form.copy(ownerContactChannel = value); dirty = true }, { Text(label) }) } }; FormField("联系方式", form.ownerContactValue, errors["ownerContactValue"]) { form = form.copy(ownerContactValue = it); dirty = true }; Text("详情页默认只显示脱敏值，用户主动获取后才显示原值。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall) } }
            if (dirty) item { Text("存在未保存的修改", color = HhyColors.Warning) }
        }
    }
}

@Composable private fun GroupHeaderCard() = Card(shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.BrandPrimary)) { Row(Modifier.fillMaxWidth().padding(HhySpacing.Lg), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(HhySize.AppLogo), color = HhyColors.Surface, shape = RoundedCornerShape(HhyRadius.NormalCard)) { HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary) }; Column { Text("找到合适的交流圈", color = HhyColors.TextInverse, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("浏览审核通过的真实群聊推广", color = HhyColors.TextInverse.copy(alpha = .84f)) } } }
@Composable private fun GroupListCard(item: ContentResource, onClick: () -> Unit) { val facts = R10GroupFacts.from(item); Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface), elevation = CardDefaults.cardElevation(HhyElevation.Card)) { Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(HhySize.AppLogo), color = HhyColors.SoftBlue, shape = CircleShape) { HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary) }; Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.summary.orEmpty().ifBlank { item.description.orEmpty() }, color = HhyColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) } }; Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { groupPlatformLabel(facts.platform)?.let { Tag(it) }; facts.sizeRange?.let { Tag(it) }; item.regionCode?.let { Tag(it) } }; HorizontalDivider(color = HhyColors.Border); Row { Text(item.publisher?.nickname ?: "发布者信息未提供", Modifier.weight(1f), color = HhyColors.TextSecondary); item.statistics?.let { Text("浏览 ${it.viewCount}", color = HhyColors.TextSecondary) } } } } }
@Composable private fun GroupHero(item: ContentResource, facts: R10GroupFacts) = Card(shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(HhySize.AppLogo), color = HhyColors.SoftBlue, shape = CircleShape) { HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary) }; Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); item.summary?.let { Text(it, color = HhyColors.TextSecondary) } } }; Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { groupPlatformLabel(facts.platform)?.let { Tag(it) }; facts.sizeRange?.let { Tag(it) }; item.categoryCode?.let { Tag(it) } } } }
@Composable private fun GroupMediaGallery(item: ContentResource) = GroupSection("群聊图片") { LazyRow(Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 3), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { items(item.media, key = { it.id }) { media -> GroupImage(media, item.title) } } }
@Composable private fun GroupImage(media: MediaItemResource, title: String) { val url = (media.thumbnailUrl ?: media.url).takeIf(::secureGroupHttps); Box(Modifier.size(HhySize.TopAppBarHeight * 3).clip(RoundedCornerShape(HhyRadius.NormalCard)).background(HhyColors.SoftBlue), contentAlignment = Alignment.Center) { HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary); url?.let { AsyncImage(it, media.altText ?: "$title 群聊图片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } } }
@Composable private fun PublisherSection(item: ContentResource) = GroupSection("发布信息") { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.publisher?.nickname ?: "发布者信息未提供", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); item.publisher?.verified?.takeIf { it }?.let { HhyIcon(HhyIcons.Verified, "已认证", tint = HhyColors.BrandPrimary) } }; item.statistics?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("浏览 ${it.viewCount}"); Text("收藏 ${it.favoriteCount}"); Text("分享 ${it.shareCount}") } } }
@Composable private fun GroupSection(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() } }
@Composable private fun KeyValue(label: String, value: String) = Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = HhyColors.TextSecondary); Text(value, fontWeight = FontWeight.SemiBold) }
@Composable private fun Tag(text: String) = Surface(color = HhyColors.SoftBlue, shape = RoundedCornerShape(HhyRadius.Tag)) { Text(text, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = HhyColors.BrandPrimary, style = MaterialTheme.typography.labelSmall) }
@Composable private fun GroupSkeleton() = Card(Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 2), shape = RoundedCornerShape(HhyRadius.LargeCard), colors = CardDefaults.cardColors(HhyColors.Surface)) { Box(Modifier.padding(HhySpacing.Lg).fillMaxWidth().height(HhySize.InputHeight).background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag))) }
@Composable private fun StateCard(title: String, body: String, action: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(HhyColors.Surface)) { Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = HhyColors.TextSecondary); OutlinedButton(onClick = onClick) { Text(action) } } }
@Composable private fun FailureCard(failure: R10GroupFailure?, retry: () -> Unit) { val copy = when (failure?.phase) { R10GroupPhase.FORBIDDEN -> "无权访问" to "当前账号没有查看或操作权限"; R10GroupPhase.NOT_FOUND -> "群聊不存在" to "内容可能已删除、下架或链接失效"; R10GroupPhase.OFFLINE -> "网络不可用" to "请检查网络后重试"; R10GroupPhase.CONFLICT -> "数据已经变化" to "请加载最新版本后继续"; else -> "加载失败" to "暂时无法完成请求，请稍后重试" }; StateCard(copy.first, copy.second, "重试", retry) }
@Composable private fun FormField(label: String, value: String, error: String?, minLines: Int = 1, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = minLines == 1, minLines = minLines, isError = error != null, supportingText = error?.let { { Text(it) } })
private fun copyText(context: Context, value: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("群聊信息", value)) }
