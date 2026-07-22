package cc.orbexa.hhy.discovery

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContactAccessRequest
import cc.orbexa.hhy.network.ContactChannelSummaryResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR07Api
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.SearchResultResource
import cc.orbexa.hhy.network.SearchTermResource
import kotlinx.coroutines.launch

private val contentTypes = listOf(
    null to "全部",
    "PROJECT" to "项目",
    "APP" to "应用",
    "GROUP_CHAT" to "群聊",
    "TEAM_LEADER" to "团长",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R07SearchScreen(
    api: ContractR07Api,
    accessToken: String,
    onBack: () -> Unit,
    onPublisherSelected: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val intentKeys = remember { StableIntentKeys() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var hot by remember { mutableStateOf<List<SearchTermResource>>(emptyList()) }
    var history by remember { mutableStateOf<List<SearchTermResource>>(emptyList()) }
    var results by remember { mutableStateOf<List<SearchResultResource>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var canLoadMore by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(R07LoadPhase.LOADING) }
    var failure by remember { mutableStateOf<R07UiFailure?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    fun acceptFailure(value: R07CallResult.Failure) {
        if (value.statusCode == 401) onSessionExpired()
        failure = value.toUiFailure()
        phase = failure!!.phase
    }

    fun loadLanding() {
        scope.launch {
            phase = R07LoadPhase.LOADING
            failure = null
            when (val hotResult = api.hot(accessToken)) {
                is R07CallResult.Success -> hot = hotResult.data.items
                is R07CallResult.Failure -> acceptFailure(hotResult)
            }
            when (val historyResult = api.history(accessToken)) {
                is R07CallResult.Success -> {
                    history = historyResult.data.items
                    if (failure == null) phase = R07LoadPhase.CONTENT
                }
                is R07CallResult.Failure -> if (hot.isEmpty()) acceptFailure(historyResult)
            }
        }
    }

    fun submit(reset: Boolean = true) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        scope.launch {
            phase = R07LoadPhase.LOADING
            failure = null
            val cursor = if (reset) null else nextCursor
            when (val result = api.search(accessToken, normalized, selectedType, cursor = cursor)) {
                is R07CallResult.Success -> {
                    results = if (reset) result.data.items else (results + result.data.items).distinctBy { it.id }
                    submittedQuery = normalized
                    nextCursor = result.data.page.nextCursor
                    canLoadMore = result.data.page.canLoadMore()
                    phase = if (results.isEmpty()) R07LoadPhase.EMPTY else R07LoadPhase.CONTENT
                }
                is R07CallResult.Failure -> acceptFailure(result)
            }
        }
    }

    LaunchedEffect(api, accessToken) {
        if (submittedQuery == null) loadLanding() else submit()
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag(if (submittedQuery == null) "hhy.screen.r07.search" else "hhy.screen.r07.search.results"),
        topBar = {
            TopAppBar(
                title = { Text(if (submittedQuery == null) "搜索" else "搜索结果") },
                navigationIcon = { HhyBackButton(onBack) },
            )
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                SearchComposer(
                    query = query,
                    onQueryChange = { if (it.length <= 100) query = it },
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it },
                    enabled = query.trim().isNotEmpty() && phase != R07LoadPhase.LOADING,
                    onSubmit = { submit() },
                )
            }
            if (phase == R07LoadPhase.LOADING) item { LoadingState("正在加载") }
            failure?.let { value -> item { FailureState(value) { if (submittedQuery == null) loadLanding() else submit() } } }
            if (submittedQuery == null && phase != R07LoadPhase.LOADING) {
                if (history.isNotEmpty()) {
                    item {
                        SectionHeader("搜索历史", action = "清空") { showClearDialog = true }
                    }
                    item {
                        TermCloud(history) { term -> query = term; submit() }
                    }
                }
                if (hot.isNotEmpty()) item { SectionHeader("大家都在搜") }
                if (hot.isNotEmpty()) item {
                    TermCloud(hot) { term -> query = term; submit() }
                }
                if (history.isEmpty() && hot.isEmpty() && phase == R07LoadPhase.CONTENT) item {
                    EmptyState("暂无搜索记录", "输入关键词即可查找公开内容")
                }
            } else if (submittedQuery != null) {
                if (phase == R07LoadPhase.EMPTY) item { EmptyState("没有找到相关内容", "换一个关键词或内容类型试试") }
                items(results, key = { it.id }) { result -> SearchResultCard(result, onPublisherSelected) }
                if (canLoadMore && phase == R07LoadPhase.CONTENT) item {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { submit(reset = false) }) { Text("加载更多") }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true }
                .testTag("hhy.dialog.r07.search-history-clear"),
            onDismissRequest = { if (!clearing) showClearDialog = false },
            title = { Text("清空搜索历史？") },
            text = { Text("将永久清空当前账号的全部搜索历史，操作不可恢复，其他账号不受影响。") },
            confirmButton = {
                Button(enabled = !clearing, onClick = {
                    val key = intentKeys.key("clear-history", "current-account")
                    clearing = true
                    scope.launch {
                        when (val result = api.clearHistory(accessToken, key)) {
                            is R07CallResult.Success -> {
                                intentKeys.complete("clear-history")
                                history = emptyList()
                                showClearDialog = false
                            }
                            is R07CallResult.Failure -> acceptFailure(result)
                        }
                        clearing = false
                    }
                }) { Text(if (clearing) "正在清空" else "确认清空") }
            },
            dismissButton = { TextButton(enabled = !clearing, onClick = { showClearDialog = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R07PublisherScreen(
    api: ContractR07Api,
    accessToken: String,
    publisherId: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var publisher by remember { mutableStateOf<PublisherSummaryResource?>(null) }
    var contents by remember { mutableStateOf<List<ContentResource>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var canLoadMore by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(R07LoadPhase.LOADING) }
    var failure by remember { mutableStateOf<R07UiFailure?>(null) }
    var contactTarget by remember { mutableStateOf<Pair<ContentResource, ContactChannelSummaryResource>?>(null) }

    fun acceptFailure(value: R07CallResult.Failure) {
        if (value.statusCode == 401) onSessionExpired()
        failure = value.toUiFailure()
        phase = failure!!.phase
    }

    fun load(reset: Boolean = true) {
        scope.launch {
            phase = R07LoadPhase.LOADING
            failure = null
            if (reset) {
                when (val result = api.publisher(accessToken, publisherId)) {
                    is R07CallResult.Success -> publisher = result.data
                    is R07CallResult.Failure -> { acceptFailure(result); return@launch }
                }
            }
            when (val result = api.contents(accessToken, publisherId, if (reset) null else nextCursor)) {
                is R07CallResult.Success -> {
                    contents = if (reset) result.data.items else (contents + result.data.items).distinctBy { it.id }
                    nextCursor = result.data.page.nextCursor
                    canLoadMore = result.data.page.canLoadMore()
                    phase = R07LoadPhase.CONTENT
                }
                is R07CallResult.Failure -> acceptFailure(result)
            }
        }
    }

    LaunchedEffect(api, accessToken, publisherId) { load() }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r07.publisher"),
        topBar = { TopAppBar(title = { Text("发布者主页") }, navigationIcon = { HhyBackButton(onBack) }) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            if (phase == R07LoadPhase.LOADING && publisher == null) item { LoadingState("正在加载发布者主页") }
            failure?.let { value -> item { FailureState(value) { load() } } }
            publisher?.let { value ->
                item { PublisherHeader(value) }
                item { SectionHeader("公开内容") }
                if (contents.isEmpty() && phase == R07LoadPhase.CONTENT) item { EmptyState("暂无公开内容", "该发布者暂未发布可查看内容") }
                items(contents, key = { it.id }) { content ->
                    ContentCard(content) { channel -> contactTarget = content to channel }
                }
                if (canLoadMore && phase == R07LoadPhase.CONTENT) item {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { load(reset = false) }) { Text("加载更多") }
                }
            }
        }
    }

    contactTarget?.let { (content, channel) ->
        ContactAccessSheet(
            api = api,
            accessToken = accessToken,
            content = content,
            channel = channel,
            onDismiss = { contactTarget = null },
            onSessionExpired = onSessionExpired,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactAccessSheet(
    api: ContractR07Api,
    accessToken: String,
    content: ContentResource,
    channel: ContactChannelSummaryResource,
    onDismiss: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    SecureContentEffect()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { StableIntentKeys() }
    var state by remember { mutableStateOf(ContactPanelState()) }

    fun close() {
        state = state.close()
        onDismiss()
    }

    ModalBottomSheet(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.sheet.r07.contact"),
        onDismissRequest = ::close,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text("联系方式", style = MaterialTheme.typography.titleLarge)
            Text(content.title, color = HhyColors.TextSecondary)
            Text("${channelLabel(channel.channel)} · ${channel.maskedValue ?: "授权后显示"}")
            state.failure?.let { FailureState(it) { state = ContactPanelState() } }
            state.contact?.let { contact ->
                Surface(color = HhyColors.SoftBlue, shape = RoundedCornerShape(HhyRadius.NormalCard)) {
                    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        Text(channelLabel(contact.channel), fontWeight = FontWeight.SemiBold)
                        Text(contact.value, style = MaterialTheme.typography.bodyLarge)
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("联系方式", contact.value))
                        }) { Text("复制联系方式") }
                        Text("仅在本次授权窗口展示，关闭后立即清除", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.contact == null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.submitting && channel.available,
                    onClick = {
                        val intentScope = "contact:${content.id}:${channel.channel}"
                        val key = keys.key(intentScope, "${content.id}|${channel.channel}")
                        state = ContactPanelState(submitting = true)
                        scope.launch {
                            when (val result = api.accessContact(accessToken, content.id, channel.channel, key, ContactAccessRequest())) {
                                is R07CallResult.Success -> {
                                    keys.complete(intentScope)
                                    state = ContactPanelState(contact = result.data)
                                }
                                is R07CallResult.Failure -> {
                                    if (result.statusCode == 401) onSessionExpired()
                                    state = ContactPanelState(failure = result.toUiFailure())
                                }
                            }
                        }
                    },
                ) { Text(if (state.submitting) "正在获取" else "获取联系方式") }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, onClick = ::close) { Text("关闭") }
            Spacer(Modifier.height(HhySpacing.Lg))
        }
    }
}

@Composable
private fun SecureContentEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val wasSecure = window.attributes.flags.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (!wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
private fun PublisherHeader(publisher: PublisherSummaryResource) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Surface(color = HhyColors.SoftBlue) {
            Row(Modifier.fillMaxWidth().padding(HhySpacing.Lg), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.clip(CircleShape), color = HhyColors.Surface) {
                    Box(Modifier.padding(HhySpacing.Xl), contentAlignment = Alignment.Center) {
                        Text(publisher.nickname.take(1), style = MaterialTheme.typography.titleLarge, color = HhyColors.BrandPrimary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Text(publisher.nickname, style = MaterialTheme.typography.titleLarge, color = HhyColors.TextPrimary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        if (publisher.verified) FactBadge("已认证", HhyColors.SuccessSoft, HhyColors.Success)
                        publisher.memberBadge?.let { FactBadge(it, HhyColors.Surface, HhyColors.BrandPrimary) }
                    }
                }
            }
        }
        publisher.bio?.let {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text("个人简介", style = MaterialTheme.typography.labelLarge, color = HhyColors.TextPrimary)
                Text(it, color = HhyColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResultResource, onPublisherSelected: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            FactBadge(contentTypeLabel(result.contentType), HhyColors.SoftBlue, HhyColors.BrandPrimary)
            Text(result.title, style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
            result.summary?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, color = HhyColors.TextSecondary) }
            result.publisher?.let { publisher ->
                HorizontalDivider(color = HhyColors.Border)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("发布者", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = HhyColors.TextTertiary)
                    TextButton(onClick = { onPublisherSelected(publisher.userId) }) { Text(publisher.nickname) }
                }
            }
        }
    }
}

@Composable
private fun ContentCard(content: ContentResource, onContact: (ContactChannelSummaryResource) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            FactBadge(contentTypeLabel(content.contentType), HhyColors.SoftBlue, HhyColors.BrandPrimary)
            Text(content.title, style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
            content.summary?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (content.contactsMasked.isNotEmpty()) {
                HorizontalDivider(color = HhyColors.Border)
                Text("联系发布者", fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
                content.contactsMasked.forEach { channel ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = channel.available,
                        onClick = { onContact(channel) },
                    ) { Text("${channelLabel(channel.channel)} ${channel.maskedValue.orEmpty()}") }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(text: String) {
    Box(Modifier.fillMaxWidth().padding(HhySpacing.Xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            CircularProgressIndicator()
            Text(text, color = HhyColors.TextSecondary)
        }
    }
}

@Composable
private fun FailureState(failure: R07UiFailure, retry: () -> Unit) {
    Surface(color = HhyColors.ErrorSoft, shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(failure.title, fontWeight = FontWeight.SemiBold, color = HhyColors.Error)
            Text(failure.guidance)
            OutlinedButton(onClick = retry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyState(title: String, guidance: String) {
    Surface(color = HhyColors.Surface, shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(guidance, color = HhyColors.TextSecondary)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HhyColors.TextPrimary)
            Surface(modifier = Modifier.fillMaxWidth(0.12f).height(HhySpacing.Xs), color = HhyColors.BrandPrimary, shape = RoundedCornerShape(HhyRadius.Pill)) { }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SearchComposer(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedType: String?,
    onTypeSelected: (String?) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("r07.search.input"),
                label = { Text("搜索项目、应用、群聊或团长") },
                singleLine = true,
                supportingText = { Text("输入 1–100 个字符") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                contentTypes.forEach { (value, label) ->
                    FilterChip(selected = selectedType == value, onClick = { onTypeSelected(value) }, label = { Text(label) })
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = HhySize.PrimaryButtonHeight).testTag("r07.search.submit"),
                enabled = enabled,
                onClick = onSubmit,
            ) { Text("搜索") }
        }
    }
}

@Composable
private fun TermCloud(terms: List<SearchTermResource>, onSelected: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HhyColors.Surface,
        shape = RoundedCornerShape(HhyRadius.NormalCard),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            terms.forEach { term ->
                OutlinedButton(onClick = { onSelected(term.keyword) }) { Text(term.keyword) }
            }
        }
    }
}

@Composable
private fun FactBadge(label: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color) {
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(HhyRadius.Tag)) {
        Text(label, modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), style = MaterialTheme.typography.labelMedium)
    }
}

private fun contentTypeLabel(value: String) = when (value) {
    "PROJECT" -> "项目"
    "APP" -> "应用"
    "GROUP_CHAT" -> "群聊"
    "TEAM_LEADER" -> "团长"
    else -> "内容"
}

private fun channelLabel(value: String) = when (value) {
    "WECHAT" -> "微信"
    "PHONE" -> "手机号"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    "LINK" -> "链接"
    "QR_CODE" -> "二维码"
    else -> "联系方式"
}
