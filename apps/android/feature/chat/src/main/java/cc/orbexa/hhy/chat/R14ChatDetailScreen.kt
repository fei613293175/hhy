package cc.orbexa.hhy.chat

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.network.ChatBlockRequest
import cc.orbexa.hhy.network.ChatContactCardPayload
import cc.orbexa.hhy.network.ChatContactCardMessageRequest
import cc.orbexa.hhy.network.ChatContentCardMessageRequest
import cc.orbexa.hhy.network.ChatContentCardPayload
import cc.orbexa.hhy.network.ChatImageMessageRequest
import cc.orbexa.hhy.network.ChatImagePayload
import cc.orbexa.hhy.network.ChatMessagePayload
import cc.orbexa.hhy.network.ChatMessageResource
import cc.orbexa.hhy.network.ChatPostConversationsByIdReadRequest
import cc.orbexa.hhy.network.ChatReportRequest
import cc.orbexa.hhy.network.ChatSendMessageRequest
import cc.orbexa.hhy.network.ChatTextMessageRequest
import cc.orbexa.hhy.network.ChatTextPayload
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R14RealtimeEvent
import cc.orbexa.hhy.network.R14RealtimeScope
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R14ChatDetailScreen(
    api: ContractR14Api,
    mediaApi: ContractMediaApi,
    accessToken: String,
    conversationId: String,
    conversationVersion: Long? = null,
    currentUserId: String,
    initialPeer: PublisherSummaryResource? = null,
    initialContentCard: ChatContentCardPayload? = null,
    realtimeEvents: Flow<R14RealtimeEvent> = emptyFlow(),
    blockStateStore: R14BlockStateStore? = null,
    onBack: () -> Unit,
    onOpenContent: (contentType: String, contentId: String) -> Unit = { _, _ -> },
    onSessionExpired: () -> Unit,
    onConversationUnavailable: () -> Unit = onBack,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val defaultBlockStateStore = remember(context) { SharedPreferencesR14BlockStateStore(context) }
    val resolvedBlockStateStore = blockStateStore ?: defaultBlockStateStore
    val listState = rememberLazyListState()
    val keys = remember { R14IntentKeys() }
    var state by remember(conversationId) { mutableStateOf(R14ChatState()) }
    var composer by remember(conversationId) { mutableStateOf("") }
    var showImagePicker by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var imageNotice by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var actionState by remember(conversationId) { mutableStateOf(R14ChatActionState()) }
    var showContactSheet by remember(conversationId) { mutableStateOf(false) }
    var showSafetySheet by remember(conversationId) { mutableStateOf(false) }
    var showReportSheet by remember(conversationId) { mutableStateOf(false) }
    var showEvidencePicker by remember(conversationId) { mutableStateOf(false) }
    var reportDraft by remember(conversationId) { mutableStateOf(R14ReportDraft()) }
    var showBlockDialog by remember(conversationId) { mutableStateOf(false) }
    var contactSubmitting by remember(conversationId) { mutableStateOf(false) }
    var contactFailure by remember(conversationId) { mutableStateOf<String?>(null) }
    var contactIntent by remember(conversationId) { mutableStateOf<ChatContactCardMessageRequest?>(null) }
    var realtimeRefreshKey by remember(conversationId) { mutableIntStateOf(0) }

    fun handleFailure(failure: R07CallResult.Failure, firstLoad: Boolean = false) {
        if (failure.statusCode == 401) onSessionExpired()
        if (firstLoad && failure.statusCode in setOf(403, 404)) {
            state = state.loadFailed(failure)
        }
    }

    fun markRead(message: ChatMessageResource) {
        if (message.sender.userId == currentUserId) return
        val fingerprint = message.id
        scope.launch {
            when (val result = api.read(
                accessToken,
                conversationId,
                keys.key("read", fingerprint),
                ChatPostConversationsByIdReadRequest(message.id),
            )) {
                is R07CallResult.Success -> Unit
                is R07CallResult.Failure -> handleFailure(result)
            }
        }
    }

    fun load(refresh: Boolean = false, append: Boolean = false) {
        val firstLoad = state.messages.isEmpty()
        state = state.loadStarted(refresh = refresh, append = append)
        scope.launch {
            when (val result = api.messages(
                accessToken = accessToken,
                conversationId = conversationId,
                cursor = if (append) state.nextCursor else null,
            )) {
                is R07CallResult.Success -> {
                    state = state.loaded(result.data, append = append)
                    state.messages.lastOrNull { it.sender.userId != currentUserId }?.let(::markRead)
                }
                is R07CallResult.Failure -> {
                    state = state.loadFailed(result)
                    handleFailure(result, firstLoad)
                }
            }
        }
    }

    fun send(
        request: ChatSendMessageRequest,
        existingKey: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (R07CallResult.Failure) -> Unit = {},
    ) {
        if (!state.canSend()) return
        val fingerprint = request.clientMessageId
        val key = existingKey ?: keys.key("send", fingerprint)
        state = state.sendStarted(request, key)
        scope.launch {
            when (val result = api.send(accessToken, conversationId, key, request)) {
                is R07CallResult.Success -> {
                    state = state.sendSucceeded(request.clientMessageId, result.data)
                    keys.complete("send", fingerprint)
                    onSuccess()
                }
                is R07CallResult.Failure -> {
                    state = state.sendFailed(request.clientMessageId, result)
                    handleFailure(result)
                    onFailure(result)
                }
            }
        }
    }

    fun sendImage(selection: MediaUploadSelection) {
        send(
            ChatImageMessageRequest(
                clientMessageId = UUID.randomUUID().toString(),
                payload = ChatImagePayload(selection.mediaId, thumbnailUrl = selection.readUrl),
            ),
        )
    }

    LaunchedEffect(realtimeEvents, conversationId) {
        realtimeEvents.collect { event ->
            if (
                event is R14RealtimeEvent.ChatChanged && event.conversationId == conversationId ||
                event is R14RealtimeEvent.GapFillRequired &&
                R14RealtimeScope.CHAT in event.affectedScopes
            ) {
                realtimeRefreshKey += 1
            }
        }
    }
    LaunchedEffect(conversationId, realtimeRefreshKey) { load(refresh = realtimeRefreshKey > 0) }
    LaunchedEffect(state.messages.size, state.outgoing.size) {
        val count = state.messages.size + state.outgoing.size
        if (count > 0 && !state.hasMore) listState.animateScrollToItem(count - 1)
    }

    val peer = initialPeer ?: state.peer(currentUserId)
    val blocked = actionState.blocked || state.phase == R14ChatPhase.BLOCKED

    LaunchedEffect(currentUserId, peer?.userId) {
        peer?.let { target ->
            actionState = actionState.copy(
                blocked = resolvedBlockStateStore.isBlockedByMe(currentUserId, target.userId),
            )
        }
    }

    fun executeAction(
        action: R14ChatAction,
        request: suspend () -> R07CallResult<CommandResultResource>,
        onSuccess: () -> Unit,
    ) {
        if (!actionState.canSubmit()) return
        actionState = actionState.started(action)
        scope.launch {
            when (val result = request()) {
                is R07CallResult.Success -> {
                    actionState = actionState.succeeded(action)
                    onSuccess()
                }
                is R07CallResult.Failure -> {
                    actionState = actionState.failed(result)
                    handleFailure(result)
                }
            }
        }
    }

    fun submitBlock(reason: String?) {
        val target = peer ?: return
        val action = if (blocked) R14ChatAction.UNBLOCK else R14ChatAction.BLOCK
        val fingerprint = "${target.userId}:${reason.orEmpty()}"
        val operation = if (blocked) "unblock" else "block"
        val key = keys.key(operation, fingerprint)
        executeAction(
            action = action,
            request = {
                if (blocked) api.unblock(accessToken, target.userId, key)
                else api.block(accessToken, target.userId, key, ChatBlockRequest(reason))
            },
            onSuccess = {
                keys.complete(operation, fingerprint)
                resolvedBlockStateStore.setBlockedByMe(
                    currentUserId = currentUserId,
                    peerId = target.userId,
                    blocked = action == R14ChatAction.BLOCK,
                )
                showBlockDialog = false
                state = state.copy(
                    phase = if (action == R14ChatAction.BLOCK) R14ChatPhase.BLOCKED
                    else if (state.messages.isEmpty()) R14ChatPhase.EMPTY else R14ChatPhase.CONTENT,
                    failure = null,
                )
                actionNotice = if (action == R14ChatAction.BLOCK) "已拉黑 ${target.nickname}" else "已解除拉黑"
            },
        )
    }

    fun submitReport(draft: R14ReportDraft) {
        val expectedVersion = conversationVersion?.takeIf { it >= 0 } ?: return
        val evidenceMediaIds = draft.evidence.map(MediaUploadSelection::mediaId).sorted()
        val messageIds = draft.messageIds.sorted()
        val reasonCode = draft.reasonCode ?: return
        val fingerprint = listOf(
            reasonCode,
            draft.description,
            evidenceMediaIds.joinToString(","),
            messageIds.joinToString(","),
            expectedVersion.toString(),
        ).joinToString(":")
        val key = keys.key("report", fingerprint)
        executeAction(
            action = R14ChatAction.REPORT,
            request = {
                api.report(
                    accessToken,
                    conversationId,
                    key,
                    ChatReportRequest(
                        reasonCode = reasonCode,
                        description = draft.description,
                        evidenceMediaIds = evidenceMediaIds,
                        messageIds = messageIds,
                        expectedVersion = expectedVersion,
                    ),
                )
            },
            onSuccess = {
                keys.complete("report", fingerprint)
                showReportSheet = false
                reportDraft = R14ReportDraft()
                actionNotice = "举报已提交"
            },
        )
    }

    if (showImagePicker) {
        MediaUploadSheet(
            api = mediaApi,
            accessToken = accessToken,
            purpose = "PRIVATE_CHAT",
            maxConcurrentUploads = 1,
            maxSelectionCount = 1,
            acceptedTypes = arrayOf("image/*"),
            onCompleted = { selections ->
                showImagePicker = false
                selections.singleOrNull()?.let(::sendImage)
            },
            onDismiss = { showImagePicker = false },
            onAuthenticationRequired = onSessionExpired,
            onPreview = { selection -> previewImage = selection.readUrl },
        )
    }

    if (showEvidencePicker) {
        val remaining = (100 - reportDraft.evidence.size).coerceAtLeast(1)
        MediaUploadSheet(
            api = mediaApi,
            accessToken = accessToken,
            purpose = "AUDIT_EVIDENCE",
            maxConcurrentUploads = 2,
            maxSelectionCount = remaining,
            acceptedTypes = arrayOf("image/*"),
            onCompleted = { selections ->
                reportDraft = reportDraft.copy(evidence = mergeR14Evidence(reportDraft.evidence, selections))
                showEvidencePicker = false
            },
            onDismiss = { showEvidencePicker = false },
            onAuthenticationRequired = onSessionExpired,
        )
    }

    if (showContactSheet && peer != null) {
        R14ContactSheet(
            peer = peer,
            submitting = contactSubmitting,
            failure = contactFailure,
            onDismiss = {
                showContactSheet = false
                contactFailure = null
            },
            onSubmit = { fields, note ->
                if (state.canSend()) {
                    val payload = ChatContactCardPayload(fields, note)
                    val previous = contactIntent
                    val request = previous?.takeIf { it.payload == payload }
                        ?: ChatContactCardMessageRequest(UUID.randomUUID().toString(), payload = payload)
                    if (previous != null && previous.clientMessageId != request.clientMessageId) {
                        state = state.removeFailed(previous.clientMessageId)
                    }
                    contactIntent = request
                    contactSubmitting = true
                    contactFailure = null
                    send(
                        request = request,
                        onSuccess = {
                            contactSubmitting = false
                            contactIntent = null
                            showContactSheet = false
                        },
                        onFailure = { failure ->
                            contactSubmitting = false
                            contactFailure = failure.r14UserMessage()
                        },
                    )
                }
            },
        )
    }

    if (showSafetySheet && peer != null) {
        R14SafetySheet(
            peer = peer,
            blocked = blocked,
            onDismiss = { showSafetySheet = false },
            onReport = {
                showSafetySheet = false
                actionState = actionState.copy(failure = null)
                reportDraft = R14ReportDraft()
                showReportSheet = true
            },
            onBlock = {
                showSafetySheet = false
                actionState = actionState.copy(failure = null)
                showBlockDialog = true
            },
        )
    }

    if (showReportSheet && !showEvidencePicker && peer != null) {
        R14ReportSheet(
            peer = peer,
            reasons = R14_CHAT_REPORT_REASONS,
            messages = state.messages,
            draft = reportDraft,
            versionAvailable = conversationVersion?.let { it >= 0 } == true,
            submitting = actionState.active == R14ChatAction.REPORT,
            failure = actionState.failure?.r14ActionMessage(),
            onDismiss = {
                showReportSheet = false
                reportDraft = R14ReportDraft()
            },
            onDraftChange = { reportDraft = it },
            onAddEvidence = {
                if (reportDraft.evidence.size < 100) showEvidencePicker = true
            },
            onSubmit = ::submitReport,
        )
    }

    if (showBlockDialog && peer != null) {
        R14BlockDialog(
            peer = peer,
            unblock = blocked,
            submitting = actionState.active in setOf(R14ChatAction.BLOCK, R14ChatAction.UNBLOCK),
            failure = actionState.failure?.r14ActionMessage(),
            onDismiss = { showBlockDialog = false },
            onConfirm = ::submitBlock,
        )
    }

    previewImage?.let { url ->
        ChatImagePreview(
            url = url,
            onDismiss = { previewImage = null },
            onSave = {
                imageNotice = if (enqueueChatImage(context, url)) "图片已加入下载" else "图片暂时无法保存"
                previewImage = null
            },
        )
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("hhy.screen.r14.chat"),
        containerColor = HhyColors.PageBackground,
        topBar = {
            ChatTopBar(
                peer = peer,
                phase = state.phase,
                onBack = onBack,
                onOpenSafetyActions = peer?.let {
                    {
                        actionState = actionState.copy(failure = null)
                        showSafetySheet = true
                    }
                },
            )
        },
        bottomBar = {
            ChatComposer(
                value = composer,
                enabled = state.canSend() && !blocked,
                blocked = blocked || state.phase == R14ChatPhase.FORBIDDEN,
                initialContentCard = initialContentCard,
                contactEnabled = peer != null,
                onValueChange = { if (it.length <= 5_000) composer = it },
                onImage = { showImagePicker = true },
                onContent = {
                    initialContentCard?.let { payload ->
                        send(ChatContentCardMessageRequest(UUID.randomUUID().toString(), payload = payload))
                    }
                },
                onContact = {
                    contactFailure = null
                    showContactSheet = true
                },
                onSend = {
                    val text = composer.trim()
                    if (text.isNotEmpty()) {
                        send(ChatTextMessageRequest(UUID.randomUUID().toString(), payload = ChatTextPayload(text))) {
                            composer = ""
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            imageNotice?.let { notice ->
                InlineNotice(notice, HhyColors.SuccessSoft, HhyColors.Success) { imageNotice = null }
            }
            actionNotice?.let { notice ->
                InlineNotice(notice, HhyColors.SuccessSoft, HhyColors.Success) { actionNotice = null }
            }
            when (state.phase) {
                R14ChatPhase.OFFLINE -> InlineNotice(state.failure?.r14UserMessage().orEmpty(), HhyColors.WarningSoft, HhyColors.Warning) { load() }
                R14ChatPhase.BLOCKED -> InlineNotice(state.failure?.r14UserMessage() ?: "当前会话已限制发送消息", HhyColors.ErrorSoft, HhyColors.Error, null)
                R14ChatPhase.PARTIAL_ERROR -> InlineNotice(state.failure?.r14UserMessage().orEmpty(), HhyColors.WarningSoft, HhyColors.Warning) { load(append = state.hasMore) }
                R14ChatPhase.REFRESHING, R14ChatPhase.CONNECTING -> InlineProgress("正在更新消息")
                else -> Unit
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.phase == R14ChatPhase.LOADING -> ChatSkeleton()
                    state.phase in setOf(R14ChatPhase.ERROR, R14ChatPhase.FORBIDDEN, R14ChatPhase.NOT_FOUND) && state.messages.isEmpty() ->
                        ChatFailureState(state.failure, onBack, ::load, onConversationUnavailable)
                    state.messages.isEmpty() && state.outgoing.isEmpty() -> ChatEmptyState()
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        if (state.hasMore || state.phase == R14ChatPhase.APPENDING) {
                            item(key = "history-loader") {
                                if (state.phase == R14ChatPhase.APPENDING) InlineProgress("正在加载更早消息")
                                else TextButton(modifier = Modifier.fillMaxWidth(), onClick = { load(append = true) }) { Text("加载更早消息") }
                            }
                        }
                        itemsIndexed(state.messages, key = { _, item -> item.id }) { index, message ->
                            if (shouldShowTime(state.messages, index)) {
                                chatTimeGroup(message.createdAt)?.let { TimeDivider(it) }
                            }
                            ChatMessageRow(
                                message = message,
                                own = message.sender.userId == currentUserId,
                                onPreviewImage = { previewImage = it },
                                onOpenContent = onOpenContent,
                            )
                        }
                        itemsIndexed(state.outgoing, key = { _, item -> item.clientMessageId }) { _, item ->
                            OutgoingMessageRow(
                                item = item,
                                onRetry = { send(item.request, item.idempotencyKey) },
                                onRemove = { state = state.removeFailed(item.clientMessageId) },
                                onPreviewImage = { previewImage = it },
                                onOpenContent = onOpenContent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    peer: PublisherSummaryResource?,
    phase: R14ChatPhase,
    onBack: () -> Unit,
    onOpenSafetyActions: (() -> Unit)?,
) {
    TopAppBar(
        modifier = Modifier.testTag("r14.chat.topbar").semantics { contentDescription = "统一会话顶栏" },
        navigationIcon = { HhyBackButton(onBack) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(peer, Modifier.size(HhySize.MinimumTouchTarget - HhySpacing.Sm))
                Column(Modifier.weight(1f).padding(horizontal = HhySpacing.Md)) {
                Text(
                    peer?.nickname?.takeIf(String::isNotBlank) ?: "对方信息暂时无法显示",
                    fontSize = HhyType.CardTitleSize,
                    lineHeight = HhyType.CardTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = HhyColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                connectionLabel(phase)?.let {
                    Text(it, fontSize = HhyType.CaptionSize, lineHeight = HhyType.CaptionLineHeight, color = connectionColor(phase))
                }
            }
            }
        },
        actions = {
            onOpenSafetyActions?.let { action ->
                IconButton(onClick = action, modifier = Modifier.size(HhySize.MinimumTouchTarget)) {
                    HhyIcon(HhyIcons.More, "会话操作", Modifier.size(HhySize.StandardProgress))
                }
            }
        },
    )
}

@Composable
private fun ChatAvatar(peer: PublisherSummaryResource?, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = HhyColors.SoftBlue) {
        if (peer?.avatarUrl?.let(::safeChatUrl) != null) {
            AsyncImage(model = peer.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HhyIcon(HhyIcons.Profile, null, Modifier.size(HhySize.StandardProgress), HhyColors.BrandPrimary)
            }
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    enabled: Boolean,
    blocked: Boolean,
    initialContentCard: ChatContentCardPayload?,
    contactEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onImage: () -> Unit,
    onContent: () -> Unit,
    onContact: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
        if (blocked) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(HhySpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                HhyIcon(HhyIcons.Error, null, Modifier.size(HhySize.StandardProgress), HhyColors.Error)
                Text("当前无法发送消息", color = HhyColors.Error, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Sm)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ComposerAction(HhyIcons.Image, "发送图片", enabled, onImage)
                    ComposerAction(HhyIcons.Attachment, "发送当前内容", enabled && initialContentCard != null, onContent)
                    ComposerAction(HhyIcons.Contact, "发送联系方式", enabled && contactEnabled, onContact)
                    Spacer(Modifier.weight(1f))
                    Text("${value.length}/5000", fontSize = HhyType.CaptionSize, color = HhyColors.TextTertiary)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f).heightIn(min = HhySize.PrimaryButtonHeight)
                            .testTag("r14.chat.composer"),
                        enabled = enabled,
                        placeholder = { Text(if (enabled) "输入消息" else "网络恢复后可发送") },
                        shape = RoundedCornerShape(HhyRadius.Input),
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = onSend,
                        enabled = enabled && value.isNotBlank(),
                        modifier = Modifier.size(HhySize.MinimumTouchTarget).clip(CircleShape).background(HhyColors.BrandPrimary),
                    ) {
                        HhyIcon(HhyIcons.Send, "发送", Modifier.size(HhySize.StandardProgress), HhyColors.TextInverse)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(HhySize.MinimumTouchTarget).semantics { if (!enabled) stateDescription = "当前不可用" },
    ) { HhyIcon(icon, label, Modifier.size(HhySize.StandardProgress)) }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessageResource,
    own: Boolean,
    onPreviewImage: (String) -> Unit,
    onOpenContent: (String, String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!own) {
            ChatAvatar(message.sender, Modifier.size(HhySize.MinimumTouchTarget - HhySpacing.Sm))
            Spacer(Modifier.width(HhySpacing.Sm))
        }
        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth(0.78f)) {
            MessagePayloadCard(message.payload, own, onPreviewImage, onOpenContent)
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                chatTimeLabel(message.createdAt)?.let { Text(it, fontSize = HhyType.CaptionSize, color = HhyColors.TextTertiary) }
                if (own) Text(deliveryLabel(message.status, message.readAt), fontSize = HhyType.CaptionSize, color = HhyColors.BrandPrimary)
            }
        }
    }
}

@Composable
private fun OutgoingMessageRow(
    item: R14OutgoingMessage,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onOpenContent: (String, String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth(0.78f)) {
            MessagePayloadCard(item.payload, true, onPreviewImage, onOpenContent)
            if (item.delivery == R14OutgoingDelivery.SENDING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    CircularProgressIndicator(Modifier.size(HhySize.StandardProgress - HhySpacing.Xs), strokeWidth = HhySize.Hairline)
                    Text("发送中", fontSize = HhyType.CaptionSize, color = HhyColors.TextSecondary)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.failure?.r14UserMessage() ?: "发送失败", fontSize = HhyType.CaptionSize, color = HhyColors.Error)
                    TextButton(onClick = onRetry) { HhyIcon(HhyIcons.Retry, "重试", Modifier.size(HhySize.StandardProgress - HhySpacing.Xs)); Text("重试") }
                    TextButton(onClick = onRemove) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun MessagePayloadCard(
    payload: ChatMessagePayload,
    own: Boolean,
    onPreviewImage: (String) -> Unit,
    onOpenContent: (String, String) -> Unit,
) {
    when (payload) {
        is ChatTextPayload -> Surface(
            color = if (own) HhyColors.BrandPrimary else HhyColors.Surface,
            shape = RoundedCornerShape(HhyRadius.NormalCard),
            shadowElevation = HhyElevation.Card,
        ) {
            Text(
                payload.text,
                Modifier.padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md),
                fontSize = HhyType.BodySize,
                lineHeight = HhyType.BodyLineHeight,
                color = if (own) HhyColors.TextInverse else HhyColors.TextPrimary,
            )
        }
        is ChatImagePayload -> ChatImageCard(payload, onPreviewImage)
        is ChatContentCardPayload -> ChatContentCard(payload, onOpenContent)
        is ChatContactCardPayload -> ChatContactCard(payload)
    }
}

@Composable
private fun ChatImageCard(payload: ChatImagePayload, onPreviewImage: (String) -> Unit) {
    val url = payload.thumbnailUrl?.takeIf(::safeChatUrl)
    Surface(shape = RoundedCornerShape(HhyRadius.NormalCard), color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
        if (url == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    HhyIcon(HhyIcons.Image, null, Modifier.size(HhySize.StandardProgress), HhyColors.TextTertiary)
                    Text("图片暂时无法显示", color = HhyColors.TextSecondary, fontSize = HhyType.SecondaryBodySize)
                }
            }
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "聊天图片",
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clickable { onPreviewImage(url) },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(HhySize.StandardProgress)) } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图片加载失败", color = HhyColors.Error) } },
            )
        }
    }
}

@Composable
private fun ChatContentCard(payload: ChatContentCardPayload, onOpenContent: (String, String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenContent(payload.contentType, payload.contentId) },
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                val cover = payload.coverUrl?.takeIf(::safeChatUrl)
                Surface(Modifier.size(HhySize.AppLogo), shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SoftBlue) {
                    if (cover != null) AsyncImage(cover, null, Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { HhyIcon(HhyIcons.Projects, null, Modifier.size(HhySize.StandardProgress), HhyColors.BrandPrimary) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Text(payload.title, fontSize = HhyType.CardTitleSize, lineHeight = HhyType.CardTitleLineHeight, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(contentTypeLabel(payload.contentType), fontSize = HhyType.SecondaryBodySize, color = HhyColors.TextSecondary)
                }
                HhyIcon(HhyIcons.ChevronRight, null, Modifier.size(HhySize.StandardProgress - HhySpacing.Xs), HhyColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun ChatContactCard(payload: ChatContactCardPayload) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                HhyIcon(HhyIcons.Contact, null, Modifier.size(HhySize.StandardProgress), HhyColors.BrandPrimary)
                Text("联系方式", fontSize = HhyType.CardTitleSize, lineHeight = HhyType.CardTitleLineHeight, fontWeight = FontWeight.SemiBold)
            }
            payload.fields.forEachIndexed { index, field ->
                if (index > 0) HorizontalDivider(color = HhyColors.Border)
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                    Text(field.label?.takeIf(String::isNotBlank) ?: contactTypeLabel(field.type), fontSize = HhyType.CaptionSize, color = HhyColors.TextSecondary)
                    Text(field.value, fontSize = HhyType.BodySize, lineHeight = HhyType.BodyLineHeight, color = HhyColors.TextPrimary)
                }
            }
            payload.note?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = HhyType.SecondaryBodySize, color = HhyColors.TextSecondary) }
        }
    }
}

@Composable
private fun ChatSkeleton() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Xl),
    ) {
        items(5) { index ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End) {
                Surface(
                    modifier = Modifier.fillMaxWidth(if (index % 2 == 0) 0.62f else 0.72f).height(HhySize.PrimaryButtonHeight),
                    shape = RoundedCornerShape(HhyRadius.NormalCard),
                    color = HhyColors.Border,
                ) {}
            }
        }
    }
}

@Composable
private fun ChatEmptyState() {
    Box(Modifier.fillMaxSize().padding(HhySpacing.Xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Surface(shape = CircleShape, color = HhyColors.SoftBlue) { HhyIcon(HhyIcons.Message, null, Modifier.padding(HhySpacing.Lg).size(HhySize.StandardProgress), HhyColors.BrandPrimary) }
            Text("暂无消息", fontSize = HhyType.CardTitleSize, fontWeight = FontWeight.SemiBold)
            Text("发送第一条消息开始沟通", color = HhyColors.TextSecondary)
        }
    }
}

@Composable
private fun ChatFailureState(
    failure: R07CallResult.Failure?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUnavailable: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(HhyColors.Surface), shape = RoundedCornerShape(HhyRadius.LargeCard)) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md), horizontalAlignment = Alignment.CenterHorizontally) {
                HhyIcon(HhyIcons.Error, null, Modifier.size(HhySize.StandardProgress), HhyColors.Error)
                Text(failure?.r14UserMessage() ?: "消息暂时无法加载", color = HhyColors.TextSecondary)
                if (failure?.statusCode in setOf(403, 404)) Button(modifier = Modifier.fillMaxWidth(), onClick = onUnavailable) { Text("返回安全上级") }
                else Button(modifier = Modifier.fillMaxWidth(), onClick = onRetry) { Text("重新加载") }
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}

@Composable
private fun InlineNotice(text: String, background: Color, foreground: Color, onAction: (() -> Unit)?) {
    Surface(color = background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = foreground, fontSize = HhyType.SecondaryBodySize)
            onAction?.let { TextButton(onClick = it) { Text("重试") } }
        }
    }
}

@Composable
private fun InlineProgress(text: String) {
    Row(Modifier.fillMaxWidth().padding(HhySpacing.Sm), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(HhySize.StandardProgress - HhySpacing.Xs), strokeWidth = HhySize.Hairline)
        Spacer(Modifier.width(HhySpacing.Sm))
        Text(text, fontSize = HhyType.CaptionSize, color = HhyColors.TextSecondary)
    }
}

@Composable
private fun TimeDivider(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = HhyColors.Border, shape = RoundedCornerShape(HhyRadius.Pill)) {
            Text(text, Modifier.padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Xs), fontSize = HhyType.CaptionSize, color = HhyColors.TextSecondary)
        }
    }
}

@Composable
private fun ChatImagePreview(url: String, onDismiss: () -> Unit, onSave: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(HhyRadius.Dialog), colors = CardDefaults.cardColors(HhyColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图片加载失败", color = HhyColors.Error) } },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Button(onClick = onSave) { Text("保存图片") }
                }
            }
        }
    }
}

private fun shouldShowTime(messages: List<ChatMessageResource>, index: Int): Boolean {
    if (index == 0) return true
    val current = runCatching { OffsetDateTime.parse(messages[index].createdAt).toInstant() }.getOrNull() ?: return false
    val previous = runCatching { OffsetDateTime.parse(messages[index - 1].createdAt).toInstant() }.getOrNull() ?: return true
    return Duration.between(previous, current).abs().toMinutes() >= 5
}

private fun connectionLabel(phase: R14ChatPhase): String? = when (phase) {
    R14ChatPhase.LOADING, R14ChatPhase.CONNECTING, R14ChatPhase.REFRESHING -> "连接中"
    R14ChatPhase.OFFLINE -> "离线"
    R14ChatPhase.BLOCKED -> "已限制发送"
    else -> null
}

private fun connectionColor(phase: R14ChatPhase): Color = when (phase) {
    R14ChatPhase.OFFLINE -> HhyColors.Warning
    R14ChatPhase.BLOCKED -> HhyColors.Error
    else -> HhyColors.Success
}

private fun contentTypeLabel(value: String): String = when (value) {
    "PROJECT" -> "项目"
    "APP" -> "APP"
    "GROUP_CHAT" -> "群聊"
    "TEAM_LEADER" -> "团队长"
    else -> "内容"
}

private fun contactTypeLabel(value: String): String = when (value) {
    "PHONE" -> "手机号"
    "WECHAT" -> "微信"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    else -> "其他"
}

private fun safeChatUrl(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

private fun enqueueChatImage(context: Context, url: String): Boolean = runCatching {
    require(safeChatUrl(url))
    val request = DownloadManager.Request(Uri.parse(url))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "hhy-chat-${System.currentTimeMillis()}.jpg")
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    true
}.getOrDefault(false)
