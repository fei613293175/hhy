package cc.orbexa.hhy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R14RealtimeEvent
import cc.orbexa.hhy.network.R14RealtimeScope
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R14ConversationListScreen(
    api: ContractR14Api,
    accessToken: String,
    contentPadding: PaddingValues = PaddingValues(),
    realtimeEvents: Flow<R14RealtimeEvent> = emptyFlow(),
    onOpenNotifications: (() -> Unit)? = null,
    onOpenAnnouncements: (() -> Unit)? = null,
    onConversationSelected: (ChatConversationResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    var state by remember { mutableStateOf(R14ConversationListState()) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var actionConversation by remember { mutableStateOf<ChatConversationResource?>(null) }
    var deleteConversation by remember { mutableStateOf<ChatConversationResource?>(null) }
    var deleteSubmitting by remember { mutableStateOf(false) }
    var deleteFailure by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keys = remember { R14IntentKeys() }

    LaunchedEffect(realtimeEvents) {
        realtimeEvents.collect { event ->
            if (
                event is R14RealtimeEvent.ChatChanged ||
                event is R14RealtimeEvent.GapFillRequired &&
                R14RealtimeScope.CHAT in event.affectedScopes
            ) {
                refreshKey += 1
            }
        }
    }

    LaunchedEffect(keyword, refreshKey) {
        if (keyword.isNotEmpty()) delay(300)
        val query = keyword.trim()
        state = state.loadStarted(query)
        val generation = state.requestGeneration
        when (val result = r14CallWithRetry(call = {
            api.conversations(
                accessToken = accessToken,
                keyword = query.takeIf(String::isNotEmpty),
                sort = "updatedAt:desc",
            )
        })) {
            is R07CallResult.Success -> state = state.loaded(generation, query, result.data)
            is R07CallResult.Failure -> {
                state = state.loadFailed(generation, result)
                if (result.statusCode == 401) onSessionExpired()
            }
        }
    }

    LaunchedEffect(listState, state.nextCursor, state.hasMore, state.activeQuery) {
        if (!state.canAppend()) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 2
        }.filter { it }.first()
        if (!state.canAppend()) return@LaunchedEffect
        val cursor = state.nextCursor ?: return@LaunchedEffect
        val query = state.activeQuery
        state = state.loadStarted(query, append = true)
        val generation = state.requestGeneration
        when (val result = r14CallWithRetry(call = {
            api.conversations(
                accessToken = accessToken,
                cursor = cursor,
                keyword = query.takeIf(String::isNotEmpty),
                sort = "updatedAt:desc",
            )
        })) {
            is R07CallResult.Success -> state = state.loaded(generation, query, result.data, append = true)
            is R07CallResult.Failure -> {
                state = state.loadFailed(generation, result)
                if (result.statusCode == 401) onSessionExpired()
            }
        }
    }

    actionConversation?.let { conversation ->
        ConversationActionsSheet(
            conversation = conversation,
            onDismiss = { actionConversation = null },
            onDelete = {
                actionConversation = null
                deleteFailure = null
                deleteConversation = conversation
            },
        )
    }
    deleteConversation?.let { conversation ->
        val peer = conversation.peer
        if (peer != null) {
            R14DeleteConversationDialog(
                peer = peer,
                submitting = deleteSubmitting,
                failure = deleteFailure,
                onDismiss = {
                    if (!deleteSubmitting) {
                        deleteConversation = null
                        deleteFailure = null
                    }
                },
                onConfirm = {
                    if (!deleteSubmitting) {
                        deleteSubmitting = true
                        deleteFailure = null
                        val key = keys.key("delete-conversation", conversation.id)
                        scope.launch {
                            when (val result = api.deleteConversation(accessToken, conversation.id, key)) {
                                is R07CallResult.Success -> {
                                    keys.complete("delete-conversation", conversation.id)
                                    state = state.removed(conversation.id)
                                    deleteSubmitting = false
                                    deleteConversation = null
                                }
                                is R07CallResult.Failure -> {
                                    deleteSubmitting = false
                                    deleteFailure = result.r14ActionMessage()
                                    if (result.statusCode == 401) onSessionExpired()
                                }
                            }
                        }
                    }
                },
            )
        } else {
            deleteConversation = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r14.conversations"),
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("r14.conversations.topbar"),
                title = { Text("消息") },
            )
        },
        containerColor = HhyColors.Surface,
    ) { screenPadding ->
        Column(Modifier.fillMaxSize().padding(screenPadding).background(HhyColors.Surface)) {
            ConversationSearchField(
                value = keyword,
                onValueChange = { if (it.length <= 100) keyword = it },
            )
            if (onOpenNotifications != null && onOpenAnnouncements != null) {
                ConversationQuickActions(
                    onOpenNotifications = onOpenNotifications,
                    onOpenAnnouncements = onOpenAnnouncements,
                    onOpenCustomerMessages = { keyword = "官方客服" },
                    onOpenSystemMessages = { keyword = "系统消息" },
                )
            }
            PullToRefreshBox(
                isRefreshing = state.phase == R14ConversationPhase.SYNCING && !state.appending,
                onRefresh = { refreshKey += 1 },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                state.phase == R14ConversationPhase.CONNECTING && state.items.isEmpty() -> ConversationLoading()
                state.phase == R14ConversationPhase.OFFLINE && state.items.isEmpty() -> ConversationFailure(
                    message = state.failure?.r14ConversationMessage().orEmpty(),
                    onRetry = { refreshKey += 1 },
                )
                state.phase == R14ConversationPhase.ERROR && state.items.isEmpty() -> ConversationFailure(
                    message = state.failure?.r14ConversationMessage().orEmpty(),
                    onRetry = { refreshKey += 1 },
                )
                state.phase == R14ConversationPhase.EMPTY -> ConversationEmpty(
                    filtered = keyword.isNotBlank(),
                    onClear = { keyword = "" },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = HhySpacing.Md),
                ) {
                    state.failure?.let { failure ->
                        item(key = "partial-failure") {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(HhyColors.WarningSoft)
                                    .padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    failure.r14ConversationMessage(),
                                    modifier = Modifier.weight(1f),
                                    fontSize = HhyType.CaptionSize,
                                    color = HhyColors.TextSecondary,
                                )
                                TextButton(onClick = { refreshKey += 1 }) { Text("重试") }
                            }
                        }
                    }
                    items(state.items, key = ChatConversationResource::id) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { onConversationSelected(conversation) },
                            onLongClick = { actionConversation = conversation },
                        )
                        HorizontalDivider(
                            color = HhyColors.Border,
                            modifier = Modifier.padding(start = HhySize.MinimumTouchTarget + HhySpacing.Xxl),
                        )
                    }
                    if (state.appending) {
                        item(key = "append-progress") {
                            Box(Modifier.fillMaxWidth().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(HhySize.StandardProgress),
                                    strokeWidth = HhySize.Hairline + HhySize.Hairline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationActionsSheet(
    conversation: ChatConversationResource,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.sheet.r14.conversation-actions"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Xl, vertical = HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Text(
                conversation.peer?.nickname ?: "会话操作",
                fontSize = HhyType.CardTitleSize,
                fontWeight = FontWeight.SemiBold,
            )
            Text("会话管理", color = HhyColors.TextSecondary)
            TextButton(
                modifier = Modifier.fillMaxWidth().testTag("r14.conversation.delete"),
                onClick = onDelete,
            ) { Text("删除会话", color = HhyColors.Error) }
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismiss) { Text("取消") }
        }
    }
}

@Composable
private fun ConversationSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm)
            .heightIn(min = HhySize.PrimaryButtonHeight)
            .testTag("r14.conversations.search"),
        placeholder = { Text("搜索聊天或联系人", fontSize = HhyType.BodySize, color = HhyColors.TextTertiary) },
        leadingIcon = { HhyIcon(HhyIcons.Search, contentDescription = null, tint = HhyColors.TextTertiary) },
        singleLine = true,
        shape = RoundedCornerShape(HhyRadius.Input),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = HhyColors.PageBackground,
            unfocusedContainerColor = HhyColors.PageBackground,
            focusedBorderColor = HhyColors.BrandPrimary,
            unfocusedBorderColor = HhyColors.Border.copy(alpha = 0f),
        ),
    )
}

@Composable
private fun ConversationQuickActions(
    onOpenNotifications: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenCustomerMessages: () -> Unit,
    onOpenSystemMessages: () -> Unit,
) {
    val actions = listOf(
        Triple("通知中心", HhyIcons.Information, onOpenNotifications),
        Triple("公告中心", HhyIcons.Information, onOpenAnnouncements),
        Triple("客服消息", HhyIcons.Contact, onOpenCustomerMessages),
        Triple("系统消息", HhyIcons.Message, onOpenSystemMessages),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEachIndexed { index, (label, icon, action) ->
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = action)
                    .padding(vertical = HhySpacing.Sm).testTag("r14.conversations.quick.$index"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
            ) {
                Surface(
                    modifier = Modifier.size(HhySize.MinimumTouchTarget),
                    shape = CircleShape,
                    color = when (index) {
                        0 -> HhyColors.SoftBlue
                        1 -> HhyColors.WarningSoft
                        2 -> HhyColors.SuccessSoft
                        else -> HhyColors.SurfaceVariant
                    },
                ) {
                    HhyIcon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(HhySpacing.Md),
                        tint = when (index) {
                            0 -> HhyColors.BrandPrimary
                            1 -> HhyColors.Warning
                            2 -> HhyColors.Success
                            else -> HhyColors.BrandTertiary
                        },
                    )
                }
                Text(label, fontSize = HhyType.CaptionSize, color = HhyColors.TextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ChatConversationResource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val peer = conversation.peer
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(
                enabled = peer != null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md)
            .semantics {
                contentDescription = peer?.let { "与${it.nickname}的会话" } ?: "对方信息暂时无法显示"
                stateDescription = if (conversation.unreadCount > 0) "${conversation.unreadCount}条未读" else "无未读消息"
            }
            .testTag("r14.conversation.row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(peer?.avatarUrl, peer?.nickname)
        Spacer(Modifier.width(HhySpacing.Md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(
                text = peer?.nickname ?: "对方信息暂时无法显示",
                modifier = Modifier.testTag("r14.conversation.peer-name"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = HhyType.CardTitleSize,
                lineHeight = HhyType.CardTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = HhyColors.TextPrimary,
            )
            conversation.lastMessage?.preview?.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    text = preview,
                    modifier = Modifier.testTag("r14.conversation.preview"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = HhyType.SecondaryBodySize,
                    lineHeight = HhyType.SecondaryBodyLineHeight,
                    color = HhyColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(HhySpacing.Sm))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            conversationTimeLabel(conversation.lastMessage?.createdAt ?: conversation.updatedAt)?.let { time ->
                Text(time, fontSize = HhyType.CaptionSize, color = HhyColors.TextTertiary)
            }
            if (conversation.unreadCount > 0) {
                Surface(shape = CircleShape, color = HhyColors.Error) {
                    Text(
                        text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
                        fontSize = HhyType.CaptionSize,
                        color = HhyColors.TextInverse,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(url: String?, nickname: String?) {
    Surface(
        modifier = Modifier.size(HhySize.MinimumTouchTarget - HhySpacing.Sm).clip(CircleShape),
        shape = CircleShape,
        color = HhyColors.SoftBlue,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    nickname?.take(1).orEmpty(),
                    fontSize = HhyType.CardTitleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = HhyColors.BrandPrimary,
                )
            }
        }
    }
}

@Composable
private fun ConversationLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            Modifier.size(HhySize.StandardProgress),
            strokeWidth = HhySize.Hairline + HhySize.Hairline,
        )
    }
}

@Composable
private fun ConversationFailure(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HhySpacing.Xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HhyIcon(
            HhyIcons.Error,
            contentDescription = null,
            tint = HhyColors.Error,
            modifier = Modifier.size(HhySize.MinimumTouchTarget - HhySpacing.Sm),
        )
        Text(message, modifier = Modifier.padding(top = HhySpacing.Md), color = HhyColors.TextSecondary)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun ConversationEmpty(filtered: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(HhySpacing.Xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HhyIcon(
            HhyIcons.Message,
            contentDescription = null,
            tint = HhyColors.TextTertiary,
            modifier = Modifier.size(HhySize.MinimumTouchTarget - HhySpacing.Sm),
        )
        Text(
            if (filtered) "没有找到相关会话" else "暂无会话",
            modifier = Modifier
                .padding(top = HhySpacing.Md)
                .testTag("r14.conversations.empty-message"),
            color = HhyColors.TextSecondary,
        )
        if (filtered) TextButton(onClick = onClear) { Text("清除搜索") }
    }
}
