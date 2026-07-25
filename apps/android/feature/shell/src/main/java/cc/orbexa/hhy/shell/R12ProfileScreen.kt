package cc.orbexa.hhy.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR12ProfileApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12ProfilePatchResult
import cc.orbexa.hhy.network.UserSelfResource
import coil.compose.AsyncImage
import java.net.URI
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R12ProfileScreen(
    api: ContractR12ProfileApi,
    mediaApi: ContractMediaApi,
    accessToken: String,
    initialUser: UserSelfResource,
    onBack: () -> Unit,
    onUserUpdated: (UserSelfResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    var state by remember(initialUser.id) { mutableStateOf(R12ProfileState().loaded(initialUser)) }
    val intentKeys = remember { R12ProfileIntentKeys() }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var showUploader by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.isDirty && !state.submitting) confirmDiscard = true else onBack()
    }

    LaunchedEffect(api, accessToken, refreshKey) {
        state = if (state.user == null) state.copy(phase = R12ProfilePhase.LOADING)
        else state.checkingLatest()
        when (val result = api.profile(accessToken)) {
            is R07CallResult.Success -> {
                val before = state
                state = when (before.reconcileMode) {
                    R12ProfileReconcileMode.CONFLICT -> before.conflictReconciled(result.data)
                    R12ProfileReconcileMode.UNKNOWN_RESULT -> before.unknownResultReconciled(result.data)
                    null -> before.loaded(result.data)
                }
                onUserUpdated(result.data)
            }
            is R07CallResult.Failure -> {
                if (result.statusCode == 401) onSessionExpired()
                else state = state.loadFailed(result)
            }
        }
    }

    fun save() {
        if (!state.canSave) return
        val request = state.patchRequest()
        val key = intentKeys.forRequest(request)
        state = state.submitStarted(request, key)
        scope.launch {
            when (val result = api.patchProfile(accessToken, key, request)) {
                is R07CallResult.Success -> when (val payload = result.data) {
                    is R12ProfilePatchResult.User -> {
                        intentKeys.consume(request)
                        state = state.submitted(payload.resource)
                        onUserUpdated(payload.resource)
                    }
                    is R12ProfilePatchResult.Command -> {
                        state = state.commandAccepted()
                        refreshKey += 1
                    }
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) {
                        onSessionExpired()
                    } else {
                        state = state.submitFailed(result)
                        if (state.reconcileMode != null) refreshKey += 1
                    }
                }
            }
        }
    }

    val screenTag = "hhy.screen.r12.profile.${state.phase.name.lowercase()}"
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(screenTag),
        topBar = {
            TopAppBar(
                modifier = Modifier.height(HhySize.TopAppBarHeight),
                title = {
                    Text(
                        "个人资料",
                        fontSize = HhyType.PageTitleSize,
                        lineHeight = HhyType.PageTitleLineHeight,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { HhyBackButton(::requestBack, enabled = !state.submitting) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HhyColors.Surface),
            )
        },
        bottomBar = {
            if (state.user != null) {
                Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg)
                            .height(HhySize.PrimaryButtonHeight).testTag("profile.save"),
                        enabled = when {
                            state.reconcileMode != null -> !state.checkingLatest
                            else -> state.canSave
                        },
                        onClick = {
                            if (state.reconcileMode != null) refreshKey += 1 else save()
                        },
                    ) {
                        if (state.submitting || state.checkingLatest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(HhySize.StandardProgress),
                                color = HhyColors.TextInverse,
                            )
                            Spacer(Modifier.size(HhySpacing.Sm))
                        }
                        Text(profileActionLabel(state))
                    }
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when {
            state.user != null -> ProfileContent(
                state = state,
                padding = padding,
                onNicknameChanged = { state = state.nicknameChanged(it) },
                onBioChanged = { state = state.bioChanged(it) },
                onChooseAvatar = { if (state.inputsEnabled) showUploader = true },
                onClearAvatar = { if (state.inputsEnabled) state = state.avatarChanged("", null) },
                onRefresh = { refreshKey += 1 },
            )
            state.phase == R12ProfilePhase.LOADING -> ProfileLoading(padding)
            else -> ProfileLoadFailure(state, padding) { refreshKey += 1 }
        }
    }

    if (showUploader) {
        MediaUploadSheet(
            api = mediaApi,
            accessToken = accessToken,
            purpose = "public_media",
            onCompleted = { selections ->
                selections.singleOrNull()?.let { selected ->
                    state = state.avatarChanged(selected.mediaId, selected.readUrl)
                    showUploader = false
                }
            },
            onDismiss = { showUploader = false },
            maxConcurrentUploads = 1,
            maxSelectionCount = 1,
            onAuthenticationRequired = onSessionExpired,
            acceptedTypes = arrayOf("image/*"),
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("昵称、简介或头像的本地修改尚未保存。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text("放弃", color = HhyColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
        )
    }
}

@Composable
private fun ProfileContent(
    state: R12ProfileState,
    padding: PaddingValues,
    onNicknameChanged: (String) -> Unit,
    onBioChanged: (String) -> Unit,
    onChooseAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onRefresh: () -> Unit,
) {
    val user = requireNotNull(state.user)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        item {
            ProfileIdentityHeader(
                user = user,
                avatarUrl = state.displayedAvatarUrl,
                editable = state.inputsEnabled,
                onChooseAvatar = onChooseAvatar,
                onClearAvatar = onClearAvatar,
            )
        }
        state.notice?.let { notice ->
            item {
                ProfileNotice(
                    text = notice,
                    warning = state.phase in setOf(
                        R12ProfilePhase.STALE_CACHE,
                        R12ProfilePhase.CONFLICT,
                        R12ProfilePhase.UNKNOWN_RESULT,
                        R12ProfilePhase.FORBIDDEN,
                    ),
                    onRefresh = onRefresh.takeIf {
                        state.phase == R12ProfilePhase.STALE_CACHE || state.reconcileMode != null
                    },
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(HhyRadius.NormalCard),
                colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                        Text(
                            "基本资料",
                            fontSize = HhyType.SectionTitleSize,
                            lineHeight = HhyType.SectionTitleLineHeight,
                            fontWeight = FontWeight.SemiBold,
                            color = HhyColors.TextPrimary,
                        )
                        Text("完善真实资料，方便合作伙伴识别你", color = HhyColors.TextSecondary)
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().testTag("profile.nickname"),
                        value = state.nicknameInput,
                        onValueChange = onNicknameChanged,
                        enabled = state.inputsEnabled,
                        label = { Text("昵称") },
                        singleLine = true,
                        isError = state.fieldErrors["nickname"] != null,
                        supportingText = {
                            Text(state.fieldErrors["nickname"] ?: "${state.nicknameCount}/255")
                        },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().testTag("profile.bio"),
                        value = state.bioInput,
                        onValueChange = onBioChanged,
                        enabled = state.inputsEnabled,
                        label = { Text("个人简介") },
                        minLines = 4,
                        maxLines = 6,
                        isError = state.fieldErrors["bio"] != null,
                        supportingText = {
                            Text(state.fieldErrors["bio"] ?: "${state.bioCount}/255")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentityHeader(
    user: UserSelfResource,
    avatarUrl: String?,
    editable: Boolean,
    onChooseAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(HhySize.AppLogo + HhySpacing.Lg).clip(CircleShape)
                        .clickable(enabled = editable, onClick = onChooseAvatar)
                        .testTag("profile.avatar"),
                    shape = CircleShape,
                    color = HhyColors.SoftBlue,
                ) {
                    if (secureProfileAvatarUrl(avatarUrl) != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "当前头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            HhyIcon(
                                HhyIcons.Profile,
                                contentDescription = null,
                                modifier = Modifier.size(HhySize.MinimumTouchTarget),
                                tint = HhyColors.BrandPrimary,
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).size(HhySize.MinimumTouchTarget)
                        .clip(CircleShape).clickable(enabled = editable, onClick = onChooseAvatar),
                    shape = CircleShape,
                    color = HhyColors.BrandPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HhyIcon(HhyIcons.Camera, contentDescription = "更换头像", tint = HhyColors.TextInverse)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(
                    user.nickname?.takeIf(String::isNotBlank) ?: "未设置昵称",
                    fontSize = HhyType.CardTitleSize,
                    lineHeight = HhyType.CardTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = HhyColors.TextPrimary,
                )
                user.phoneMasked?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = HhyColors.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    ProfileBadge(profileAccountStatusLabel(user.status), user.status == "ACTIVE")
                    user.identityStatus?.let {
                        ProfileBadge(profileIdentityStatusLabel(it), it == "VERIFIED")
                    }
                }
                if (!avatarUrl.isNullOrBlank() && editable) {
                    TextButton(onClick = onClearAvatar) {
                        Text("移除头像", color = HhyColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBadge(label: String, positive: Boolean) {
    Surface(
        shape = RoundedCornerShape(HhyRadius.Tag),
        color = if (positive) HhyColors.SuccessSoft else HhyColors.WarningSoft,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
            color = if (positive) HhyColors.Success else HhyColors.Warning,
            fontSize = HhyType.CaptionSize,
            lineHeight = HhyType.CaptionLineHeight,
        )
    }
}

@Composable
private fun ProfileNotice(text: String, warning: Boolean, onRefresh: (() -> Unit)?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.Tag),
        color = if (warning) HhyColors.WarningSoft else HhyColors.SuccessSoft,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HhyIcon(
                if (warning) HhyIcons.Information else HhyIcons.Check,
                contentDescription = null,
                tint = if (warning) HhyColors.Warning else HhyColors.Success,
            )
            Text(text, modifier = Modifier.weight(1f), color = HhyColors.TextPrimary)
            onRefresh?.let { TextButton(onClick = it) { Text("重新查询") } }
        }
    }
}

@Composable
private fun ProfileLoading(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
    }
}

@Composable
private fun ProfileLoadFailure(state: R12ProfileState, padding: PaddingValues, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xl), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HhyRadius.LargeCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                HhyIcon(HhyIcons.Error, contentDescription = null, tint = HhyColors.Warning)
                Text(state.notice ?: "个人资料暂时无法加载", fontWeight = FontWeight.SemiBold)
                if (state.phase !in setOf(R12ProfilePhase.FORBIDDEN, R12ProfilePhase.NOT_FOUND)) {
                    OutlinedButton(onClick = onRetry) { Text("重新加载") }
                }
            }
        }
    }
}

internal fun profileActionLabel(state: R12ProfileState): String = when {
    state.submitting -> "正在保存"
    state.checkingLatest -> "正在查询"
    state.reconcileMode != null -> "查询最新资料"
    else -> "保存修改"
}

internal fun profileAccountStatusLabel(value: String): String = when (value) {
    "ACTIVE" -> "账号正常"
    "FROZEN" -> "账号已冻结"
    else -> "账号受限"
}

internal fun profileIdentityStatusLabel(value: String): String = when (value) {
    "VERIFIED" -> "已实名认证"
    "REVIEWING", "PENDING" -> "认证审核中"
    "REJECTED" -> "认证未通过"
    else -> "未实名认证"
}

internal fun secureProfileAvatarUrl(value: String?): String? = value?.takeIf {
    runCatching {
        val uri = URI.create(it)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
}
