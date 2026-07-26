package cc.orbexa.hhy.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentPostContentsByIdInvalidFeedbackRequest
import cc.orbexa.hhy.network.ContentPostContentsByIdShareRequest
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR13Api
import cc.orbexa.hhy.network.R07CallResult
import kotlinx.coroutines.launch

private data class R13Category(val code: String?, val label: String)

private val R13_CATEGORIES = listOf(
    R13Category(null, "全部"),
    R13Category("PROJECT", "项目"),
    R13Category("APP", "APP"),
    R13Category("GROUP_CHAT", "群聊"),
    R13Category("TEAM_LEADER", "团队长"),
)

enum class R13ContentSheet { SHARE, INVALID_FEEDBACK }

@Composable
fun R13FavoritesScreen(
    api: ContractR13Api,
    accessToken: String,
    onBack: () -> Unit,
    onContentSelected: (ContentResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    R13ActivityListScreen(
        mode = R13ListMode.FAVORITES,
        api = api,
        accessToken = accessToken,
        onBack = onBack,
        onContentSelected = onContentSelected,
        onSessionExpired = onSessionExpired,
    )
}

@Composable
fun R13HistoryScreen(
    api: ContractR13Api,
    accessToken: String,
    onBack: () -> Unit,
    onContentSelected: (ContentResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    R13ActivityListScreen(
        mode = R13ListMode.HISTORY,
        api = api,
        accessToken = accessToken,
        onBack = onBack,
        onContentSelected = onContentSelected,
        onSessionExpired = onSessionExpired,
    )
}

private enum class R13ListMode(val title: String, val tag: String) {
    FAVORITES("我的收藏", "favorites"),
    HISTORY("浏览记录", "history"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun R13ActivityListScreen(
    mode: R13ListMode,
    api: ContractR13Api,
    accessToken: String,
    onBack: () -> Unit,
    onContentSelected: (ContentResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keys = remember { R13IntentKeys() }
    var state by remember(mode) { mutableStateOf(R13ActivityListState()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var busyFavoriteId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun acceptFailure(failure: R07CallResult.Failure) {
        if (failure.statusCode == 401) onSessionExpired()
        state = state.failed(failure)
    }

    fun load(refresh: Boolean = false, append: Boolean = false) {
        if (append && (!state.hasMore || state.phase == R13ListPhase.APPENDING)) return
        val cursor = if (append) state.nextCursor else null
        state = state.loadStarted(refresh = refresh, append = append)
        scope.launch {
            val result = when (mode) {
                R13ListMode.FAVORITES -> api.favorites(accessToken, cursor = cursor)
                R13ListMode.HISTORY -> api.history(accessToken, cursor = cursor)
            }
            when (result) {
                is R07CallResult.Success -> state = state.loaded(result.data, append)
                is R07CallResult.Failure -> acceptFailure(result)
            }
        }
    }

    LaunchedEffect(mode, accessToken) { load() }
    val visible = state.items.filter { selectedCategory == null || it.contentType == selectedCategory }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r13.${mode.tag}.${state.phase.name.lowercase()}"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(mode.title, fontWeight = FontWeight.SemiBold)
                        state.total?.let {
                            Text("共 $it 条", color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
                        }
                    }
                },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    IconButton(
                        enabled = state.phase !in setOf(R13ListPhase.LOADING, R13ListPhase.REFRESHING),
                        onClick = { load(refresh = true) },
                        modifier = Modifier.testTag("r13.${mode.tag}.refresh"),
                    ) {
                        HhyIcon(HhyIcons.Refresh, "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            R13CategoryTabs(selectedCategory) { selectedCategory = it }
            when {
                state.phase == R13ListPhase.LOADING -> R13SkeletonList()
                state.items.isEmpty() && state.phase == R13ListPhase.EMPTY -> R13EmptyState(mode, onBack)
                state.items.isEmpty() && state.phase in setOf(
                    R13ListPhase.ERROR, R13ListPhase.OFFLINE, R13ListPhase.FORBIDDEN, R13ListPhase.NOT_FOUND,
                ) -> R13FullError(state, onBack) { load() }
                visible.isEmpty() -> R13FilteredEmptyState { selectedCategory = null }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = HhySpacing.Xl),
                ) {
                    if (state.phase == R13ListPhase.REFRESHING) item {
                        R13InlineProgress("正在刷新")
                    }
                    var previousGroup: String? = null
                    items(visible, key = ContentResource::id) { item ->
                        if (mode == R13ListMode.HISTORY) {
                            val group = activityDateGroup(item)
                            if (group != previousGroup) {
                                R13TimeHeader(group)
                                previousGroup = group
                            }
                        }
                        R13ActivityRow(
                            item = item,
                            mode = mode,
                            busy = busyFavoriteId == item.id,
                            onClick = { onContentSelected(item) },
                            onUnfavorite = if (mode == R13ListMode.FAVORITES) {{
                                val fingerprint = item.id
                                val key = keys.key("unfavorite", fingerprint)
                                busyFavoriteId = item.id
                                scope.launch {
                                    when (val result = api.unfavorite(accessToken, item.id, key)) {
                                        is R07CallResult.Success -> {
                                            keys.complete("unfavorite", fingerprint)
                                            state = state.remove(item.id)
                                            notice = "已取消收藏"
                                        }
                                        is R07CallResult.Failure -> {
                                            if (result.statusCode == 401) onSessionExpired()
                                            notice = r13FailureMessage(result)
                                        }
                                    }
                                    busyFavoriteId = null
                                }
                            }} else null,
                        )
                    }
                    if (state.phase == R13ListPhase.PARTIAL_ERROR) item {
                        R13InlineError { load(append = state.hasMore) }
                    }
                    if (state.hasMore) item {
                        Box(Modifier.fillMaxWidth().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
                            OutlinedButton(
                                enabled = state.phase != R13ListPhase.APPENDING,
                                onClick = { load(append = true) },
                                modifier = Modifier.testTag("r13.${mode.tag}.load-more"),
                            ) {
                                if (state.phase == R13ListPhase.APPENDING) {
                                    CircularProgressIndicator(Modifier.size(HhySpacing.Xl), strokeWidth = HhySize.Hairline)
                                    Spacer(Modifier.size(HhySpacing.Sm))
                                }
                                Text(if (state.phase == R13ListPhase.APPENDING) "正在加载" else "加载更多")
                            }
                        }
                    } else item {
                        Text(
                            "没有更多了",
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            color = HhyColors.TextSecondary,
                            fontSize = HhyType.CaptionSize,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("操作结果") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun R13CategoryTabs(selected: String?, onSelected: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(HhyColors.Surface)
            .padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
    ) {
        R13_CATEGORIES.forEach { category ->
            FilterChip(
                selected = selected == category.code,
                onClick = { onSelected(category.code) },
                label = { Text(category.label, maxLines = 1) },
                modifier = Modifier.weight(1f).testTag("r13.category.${category.code ?: "all"}"),
            )
        }
    }
    HorizontalDivider(color = HhyColors.Border)
}

@Composable
private fun R13ActivityRow(
    item: ContentResource,
    mode: R13ListMode,
    busy: Boolean,
    onClick: () -> Unit,
    onUnfavorite: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(HhyColors.Surface).clickable(onClick = onClick)
            .padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mediaUrl = secureActivityMediaUrl(item.media.firstOrNull()?.thumbnailUrl ?: item.media.firstOrNull()?.url)
        Box(
            modifier = Modifier.size(width = HhySize.TopAppBarHeight * 1.55f, height = HhySize.TopAppBarHeight)
                .clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.SoftBlue),
            contentAlignment = Alignment.Center,
        ) {
            HhyIcon(contentTypeIcon(item.contentType), null, tint = HhyColors.BrandPrimary)
            mediaUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = item.media.firstOrNull()?.altText ?: "${item.title}缩略图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    color = HhyColors.TextPrimary,
                    fontSize = HhyType.BodySize,
                    lineHeight = HhyType.BodyLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SoftBlue) {
                    Text(
                        contentTypeLabel(item.contentType),
                        modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
                        color = HhyColors.BrandPrimary,
                        fontSize = HhyType.CaptionSize,
                    )
                }
            }
            Text(
                item.publisher?.nickname ?: "发布者信息未提供",
                color = HhyColors.TextSecondary,
                fontSize = HhyType.CaptionSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                activityTimeLabel(item) ?: "内容时间未提供",
                color = HhyColors.TextSecondary,
                fontSize = HhyType.CaptionSize,
            )
        }
        onUnfavorite?.let {
            IconButton(
                enabled = !busy,
                onClick = it,
                modifier = Modifier.testTag("r13.favorite.remove.${item.id}"),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(HhySpacing.Xl), strokeWidth = HhySize.Hairline)
                else HhyIcon(HhyIcons.Favorite, "取消收藏", tint = HhyColors.Warning)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = HhySpacing.Lg + HhySize.TopAppBarHeight * 1.55f), color = HhyColors.Border)
}

@Composable
private fun R13TimeHeader(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().background(HhyColors.PageBackground)
            .padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm),
        color = HhyColors.TextSecondary,
        fontSize = HhyType.CaptionSize,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun R13SkeletonList() {
    Column(Modifier.fillMaxSize().padding(top = HhySpacing.Sm)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().background(HhyColors.Surface).padding(HhySpacing.Lg), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Box(Modifier.size(width = HhySize.TopAppBarHeight * 1.55f, height = HhySize.TopAppBarHeight).background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    Box(Modifier.fillMaxWidth(.7f).height(HhySpacing.Lg).background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag)))
                    Box(Modifier.fillMaxWidth(.45f).height(HhySpacing.Md).background(HhyColors.Border, RoundedCornerShape(HhyRadius.Tag)))
                }
            }
        }
    }
}

@Composable
private fun R13EmptyState(mode: R13ListMode, onBack: () -> Unit) = R13CenteredState(
    icon = if (mode == R13ListMode.FAVORITES) HhyIcons.Favorite else HhyIcons.Pending,
    title = if (mode == R13ListMode.FAVORITES) "还没有收藏内容" else "还没有浏览记录",
    body = if (mode == R13ListMode.FAVORITES) "浏览平台内容后，可从详情页加入收藏" else "浏览内容详情后，记录会安全地显示在这里",
    action = "返回浏览",
    onAction = onBack,
)

@Composable
private fun R13FilteredEmptyState(clear: () -> Unit) = R13CenteredState(
    icon = HhyIcons.Search,
    title = "当前分类暂无内容",
    body = "可以切换其他分类查看已加载内容",
    action = "查看全部",
    onAction = clear,
)

@Composable
private fun R13FullError(state: R13ActivityListState, onBack: () -> Unit, retry: () -> Unit) {
    val (title, body) = when (state.phase) {
        R13ListPhase.OFFLINE -> "网络不可用" to "已停止写操作，请恢复网络后重试"
        R13ListPhase.FORBIDDEN -> "无法访问" to "当前账号没有查看该页面的权限"
        R13ListPhase.NOT_FOUND -> "页面内容不存在" to "相关数据可能已经失效"
        else -> "加载失败" to "暂时无法获取可靠内容，请稍后重试"
    }
    R13CenteredState(HhyIcons.Error, title, body, "重试", retry, onBack)
}

@Composable
private fun R13CenteredState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(HhySpacing.Xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                HhyIcon(icon, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary)
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = HhyType.SectionTitleSize)
            Text(body, color = HhyColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                onBack?.let { OutlinedButton(onClick = it) { Text("返回") } }
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun R13InlineProgress(label: String) {
    Row(Modifier.fillMaxWidth().padding(HhySpacing.Sm), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(HhySpacing.Xl), strokeWidth = HhySize.Hairline)
        Spacer(Modifier.size(HhySpacing.Sm))
        Text(label, color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
    }
}

@Composable
private fun R13InlineError(retry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("加载失败，已保留现有内容", color = HhyColors.Error, fontSize = HhyType.CaptionSize)
        TextButton(onClick = retry) { Text("重试") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R13ContentActionSheet(
    sheet: R13ContentSheet,
    api: ContractR13Api,
    accessToken: String,
    contentId: String,
    contentTitle: String,
    feedbackChannels: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onCompleted: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember(contentId) { R13IntentKeys() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HhyColors.Surface,
        modifier = Modifier.testTag("hhy.sheet.r13.${if (sheet == R13ContentSheet.SHARE) "share" else "invalid-feedback"}"),
    ) {
        when (sheet) {
            R13ContentSheet.SHARE -> R13ShareSheetBody(
                api, accessToken, contentId, contentTitle, keys, context, onDismiss, onCompleted, onSessionExpired, scope,
            )
            R13ContentSheet.INVALID_FEEDBACK -> R13InvalidFeedbackSheetBody(
                api, accessToken, contentId, contentTitle, feedbackChannels, keys, onDismiss, onCompleted, onSessionExpired, scope,
            )
        }
    }
}

private data class R13ShareChannel(val code: String, val label: String, val icon: ImageVector)

@Composable
private fun R13ShareSheetBody(
    api: ContractR13Api,
    accessToken: String,
    contentId: String,
    contentTitle: String,
    keys: R13IntentKeys,
    context: Context,
    onDismiss: () -> Unit,
    onCompleted: (String) -> Unit,
    onSessionExpired: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val channels = listOf(
        R13ShareChannel("WECHAT", "微信", HhyIcons.Message),
        R13ShareChannel("WECHAT_MOMENTS", "朋友圈", HhyIcons.Groups),
        R13ShareChannel("COPY_LINK", "复制链接", HhyIcons.Copy),
        R13ShareChannel("OTHER", "其他", HhyIcons.Applications),
    )
    var selected by remember { mutableStateOf(channels.first()) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
    ) {
        Text("分享", fontSize = HhyType.SectionTitleSize, fontWeight = FontWeight.Bold)
        Text("将“${contentTitle.take(24)}”通过选定渠道分享", color = HhyColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            channels.forEach { channel ->
                val active = selected.code == channel.code
                Surface(
                    modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(HhyRadius.NormalCard))
                        .clickable(enabled = !submitting) { selected = channel; error = null }
                        .testTag("r13.share.${channel.code.lowercase()}"),
                    color = if (active) HhyColors.SoftBlue else HhyColors.PageBackground,
                    border = androidx.compose.foundation.BorderStroke(HhySize.Hairline, if (active) HhyColors.BrandPrimary else HhyColors.Border),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (submitting && active) CircularProgressIndicator(Modifier.size(HhySpacing.Xxl), strokeWidth = HhySize.Hairline)
                        else HhyIcon(channel.icon, null, tint = if (active) HhyColors.BrandPrimary else HhyColors.TextSecondary)
                        Spacer(Modifier.height(HhySpacing.Sm))
                        Text(channel.label, fontSize = HhyType.CaptionSize, color = if (active) HhyColors.BrandPrimary else HhyColors.TextPrimary)
                    }
                }
            }
        }
        error?.let { Text(it, color = HhyColors.Error, fontSize = HhyType.CaptionSize) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                enabled = !submitting,
                modifier = Modifier.weight(1f).testTag("r13.share.submit"),
                onClick = {
                    val fingerprint = "$contentId:${selected.code}"
                    val key = keys.key("share", fingerprint)
                    submitting = true
                    error = null
                    scope.launch {
                        when (val result = api.share(
                            accessToken, contentId, key, ContentPostContentsByIdShareRequest(selected.code),
                        )) {
                            is R07CallResult.Success -> {
                                keys.complete("share", fingerprint)
                                if (selected.code == "COPY_LINK") copyR13Link(context, result.data.url)
                                else shareR13Link(context, contentTitle, result.data.url)
                                onCompleted(if (selected.code == "COPY_LINK") "链接已复制" else "已打开系统分享")
                            }
                            is R07CallResult.Failure -> {
                                if (result.statusCode == 401) onSessionExpired()
                                error = r13FailureMessage(result)
                            }
                        }
                        submitting = false
                    }
                },
            ) { Text("继续分享") }
        }
    }
}

@Composable
private fun R13InvalidFeedbackSheetBody(
    api: ContractR13Api,
    accessToken: String,
    contentId: String,
    contentTitle: String,
    feedbackChannels: List<String>,
    keys: R13IntentKeys,
    onDismiss: () -> Unit,
    onCompleted: (String) -> Unit,
    onSessionExpired: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val reasons = feedbackChannels.distinct().map { it to r13ContactFailureLabel(it) }
    var selected by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val reason = selected ?: return
        val fingerprint = "$contentId:$reason:$description"
        val key = keys.key("invalid-feedback", fingerprint)
        confirming = false
        submitting = true
        error = null
        scope.launch {
            when (val result = api.invalidFeedback(
                accessToken,
                contentId,
                key,
                ContentPostContentsByIdInvalidFeedbackRequest(reason, description.trim().ifBlank { null }),
            )) {
                is R07CallResult.Success -> {
                    keys.complete("invalid-feedback", fingerprint)
                    description = ""
                    onCompleted("反馈已提交")
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    error = r13FailureMessage(result)
                }
            }
            submitting = false
        }
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Text("联系方式失效反馈", fontSize = HhyType.SectionTitleSize, fontWeight = FontWeight.Bold)
        Text("反馈“${contentTitle.take(24)}”中的公开联系信息问题，不会展示或记录真实联系方式。", color = HhyColors.TextSecondary)
        reasons.forEach { (code, label) ->
            FilterChip(
                selected = selected == code,
                onClick = { selected = code; error = null },
                label = { Text(label) },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().testTag("r13.invalid.$code"),
            )
        }
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 2000) description = it },
            modifier = Modifier.fillMaxWidth().testTag("r13.invalid.description"),
            label = { Text("详细说明（选填）") },
            supportingText = { Text("${description.length}/2000") },
            minLines = 3,
            maxLines = 5,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        error?.let { Text(it, color = HhyColors.Error, fontSize = HhyType.CaptionSize) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                enabled = selected != null && !submitting,
                onClick = { confirming = true },
                modifier = Modifier.weight(1f).testTag("r13.invalid.submit"),
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(HhySpacing.Xl), color = HhyColors.TextInverse, strokeWidth = HhySize.Hairline)
                else Text("提交反馈")
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("确认提交反馈") },
            text = { Text("平台将核查该内容的联系信息。提交后本次反馈不可撤回，是否继续？") },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("再检查一下") } },
            confirmButton = { Button(onClick = ::submit) { Text("确认提交") } },
        )
    }
}

private fun contentTypeIcon(value: String): ImageVector = when (value) {
    "PROJECT" -> HhyIcons.Projects
    "APP" -> HhyIcons.Applications
    "GROUP_CHAT" -> HhyIcons.Groups
    "TEAM_LEADER" -> HhyIcons.Profile
    else -> HhyIcons.Information
}

private fun r13ContactFailureLabel(value: String): String = when (value) {
    "QR_CODE" -> "二维码无法识别"
    "LINK" -> "链接无法打开"
    "WECHAT" -> "微信联系信息无效"
    "PHONE" -> "电话号码无效"
    "QQ" -> "QQ联系信息无效"
    "EMAIL" -> "邮箱地址无效"
    "JOIN_PASSWORD" -> "入群口令无效"
    else -> "该联系方式无效"
}

private fun r13FailureMessage(failure: R07CallResult.Failure): String = when (failure.statusCode) {
    null -> "网络不可用，已保留当前内容，请恢复网络后重试"
    400 -> failure.fieldErrors.values.firstOrNull() ?: "请检查输入内容"
    403 -> "当前账号没有执行此操作的权限"
    404 -> "内容已失效或不存在"
    409 -> "数据已经变化，请刷新后再试"
    422 -> "当前业务状态不允许此操作"
    429 -> failure.retryAfterSeconds?.let { "操作较频繁，请 $it 秒后重试" } ?: "操作较频繁，请稍后重试"
    else -> "操作失败，请稍后重试"
}

private fun copyR13Link(context: Context, url: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("合伙云分享链接", url))
}

private fun shareR13Link(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
