package cc.orbexa.hhy.teamleader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.media.MediaUploadSelection
import cc.orbexa.hhy.media.MediaUploadSheet
import cc.orbexa.hhy.network.ContractMediaApi
import cc.orbexa.hhy.network.ContractR11Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R11CreateTeamLeaderRequest
import cc.orbexa.hhy.network.R11PatchTeamLeaderRequest
import kotlinx.coroutines.launch

private val contactChannels = listOf("WECHAT" to "微信", "PHONE" to "手机号", "QQ" to "QQ", "EMAIL" to "邮箱")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R11TeamLeaderEditorScreen(
    api: ContractR11Api,
    mediaApi: ContractMediaApi,
    accessToken: String,
    teamLeaderId: String?,
    identityVerified: Boolean,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keys = remember { R11IntentKeys() }
    var form by remember { mutableStateOf(R11TeamLeaderForm()) }
    var phase by remember { mutableStateOf(if (teamLeaderId == null) R11TeamLeaderPhase.CONTENT else R11TeamLeaderPhase.LOADING) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showUpload by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<R11TeamLeaderFailure?>(null) }
    var submitFailureMessage by remember { mutableStateOf<String?>(null) }

    fun leave() {
        if (dirty) showDiscardConfirm = true else onBack()
    }

    fun applyFailure(result: R07CallResult.Failure) {
        if (result.statusCode == 401) onSessionExpired()
        errors = result.fieldErrors
        failure = result.toR11TeamLeaderFailure()
        phase = failure!!.phase
    }

    fun load() {
        val id = teamLeaderId ?: return
        scope.launch {
            phase = R11TeamLeaderPhase.LOADING
            when (val result = api.teamLeader(accessToken, id)) {
                is R07CallResult.Success -> {
                    if (result.data.contentType != "TEAM_LEADER") {
                        phase = R11TeamLeaderPhase.NOT_FOUND
                    } else {
                        form = R11TeamLeaderForm.from(result.data)
                        errors = emptyMap()
                        failure = null
                        dirty = false
                        phase = R11TeamLeaderPhase.CONTENT
                    }
                }
                is R07CallResult.Failure -> applyFailure(result)
            }
        }
    }

    fun validateAndConfirm() {
        errors = form.validate()
        if (errors.isEmpty()) {
            submitFailureMessage = null
            showSubmitConfirm = true
        }
    }

    fun submit() {
        errors = form.validate()
        if (errors.isNotEmpty()) return
        showSubmitConfirm = false
        phase = R11TeamLeaderPhase.SUBMITTING
        val fingerprint = form.fingerprint()
        val operation = if (teamLeaderId == null) "create" else "patch"
        scope.launch {
            val result = if (teamLeaderId == null) {
                api.create(
                    accessToken,
                    keys.forBody(operation, fingerprint),
                    R11CreateTeamLeaderRequest(
                        title = form.teamName.trim(),
                        summary = form.personalIntro.trim().ifBlank { null },
                        description = form.teamIntro.trim(),
                        categoryCode = form.categoryCode.trim(),
                        regionCode = form.regionCode.trim().ifBlank { null },
                        mediaIds = form.mediaIds,
                        contacts = requireNotNull(form.contacts()),
                        attributes = form.attributes(),
                    ),
                )
            } else {
                api.patch(
                    accessToken,
                    teamLeaderId,
                    keys.forBody(operation, fingerprint),
                    R11PatchTeamLeaderRequest(
                        title = form.teamName.trim(),
                        summary = form.personalIntro.trim().ifBlank { null },
                        description = form.teamIntro.trim(),
                        categoryCode = form.categoryCode.trim(),
                        regionCode = form.regionCode.trim().ifBlank { null },
                        mediaIds = form.mediaIds,
                        contacts = form.contacts(),
                        attributes = form.attributes(),
                        expectedVersion = requireNotNull(form.expectedVersion),
                    ),
                )
            }
            when (result) {
                is R07CallResult.Success -> {
                    keys.consume(operation, fingerprint)
                    dirty = false
                    submitFailureMessage = null
                    phase = R11TeamLeaderPhase.CONTENT
                    onSaved(result.data.id)
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) {
                        onSessionExpired()
                    } else {
                        errors = result.fieldErrors
                        failure = result.toR11TeamLeaderFailure()
                        phase = when (result.statusCode) {
                            403 -> R11TeamLeaderPhase.FORBIDDEN
                            404 -> R11TeamLeaderPhase.NOT_FOUND
                            409 -> R11TeamLeaderPhase.CONFLICT
                            else -> R11TeamLeaderPhase.CONTENT
                        }
                        submitFailureMessage = when (result.statusCode) {
                            400, 422 -> "部分内容未通过校验，请按提示修改后重试。"
                            429 -> result.retryAfterSeconds?.let { "操作过于频繁，请在${it}秒后重试。" }
                                ?: "操作过于频繁，请稍后重试。"
                            else -> "保存未完成，表单内容已保留，请稍后重试。"
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(teamLeaderId) { load() }
    BackHandler(onBack = ::leave)

    if (showDiscardConfirm) AlertDialog(
        onDismissRequest = { showDiscardConfirm = false },
        title = { Text("放弃未保存修改？") },
        text = { Text("返回后，本次尚未保存的团队资料会丢失。") },
        confirmButton = { TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") } },
    )

    if (showSubmitConfirm) AlertDialog(
        onDismissRequest = { showSubmitConfirm = false },
        title = { Text(if (teamLeaderId == null) "确认保存团队长资料" else "确认更新团队长资料") },
        text = { Text("将保存团队介绍、能力、案例、图片和联系方式；联系方式仅在用户主动获取时展示原值。正式提交审核与上下架将在发布管理版本提供。") },
        confirmButton = { TextButton(onClick = ::submit) { Text("确认保存") } },
        dismissButton = { TextButton(onClick = { showSubmitConfirm = false }) { Text("继续编辑") } },
    )

    if (showUpload) MediaUploadSheet(
        api = mediaApi,
        accessToken = accessToken,
        purpose = "CONTENT_TEAM_LEADER",
        maxConcurrentUploads = 2,
        acceptedTypes = arrayOf("image/*"),
        onAuthenticationRequired = onSessionExpired,
        onCompleted = { values: List<MediaUploadSelection> ->
            form = form.copy(mediaIds = values.map(MediaUploadSelection::mediaId))
            dirty = true
            showUpload = false
        },
        onDismiss = { showUpload = false },
    )

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r11.team-leader.editor.${phase.name.lowercase()}"),
        topBar = {
            TopAppBar(
                title = { Text(if (teamLeaderId == null) "团队长入驻" else "编辑团队长资料") },
                navigationIcon = { HhyBackButton(::leave, enabled = phase != R11TeamLeaderPhase.SUBMITTING) },
            )
        },
        bottomBar = {
            if (identityVerified && phase == R11TeamLeaderPhase.CONTENT) {
                Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(HhySpacing.Md),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        OutlinedButton(
                            onClick = ::leave,
                            modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
                        ) { Text(if (dirty) "稍后继续" else "返回") }
                        Button(
                            onClick = ::validateAndConfirm,
                            modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight)
                                .testTag("r11.team-leader.editor.save"),
                        ) { Text("保存资料") }
                    }
                }
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when {
            !identityVerified -> EditorStateCard(padding, "需要实名认证", "完成实名认证后才能申请或编辑团队长资料。", "返回", onBack)
            phase in setOf(R11TeamLeaderPhase.LOADING, R11TeamLeaderPhase.SUBMITTING) ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            phase in setOf(R11TeamLeaderPhase.ERROR, R11TeamLeaderPhase.OFFLINE, R11TeamLeaderPhase.FORBIDDEN, R11TeamLeaderPhase.NOT_FOUND) ->
                EditorFailure(padding, phase, if (teamLeaderId == null) onBack else ::load)
            else -> TeamLeaderEditorForm(
                padding = padding,
                form = form,
                errors = errors,
                dirty = dirty,
                conflict = phase == R11TeamLeaderPhase.CONFLICT,
                submitFailureMessage = submitFailureMessage,
                onChange = { form = it; dirty = true; if (phase == R11TeamLeaderPhase.CONFLICT) phase = R11TeamLeaderPhase.CONTENT },
                onUpload = { showUpload = true },
                onReload = ::load,
            )
        }
    }
}

@Composable
private fun TeamLeaderEditorForm(
    padding: PaddingValues,
    form: R11TeamLeaderForm,
    errors: Map<String, String>,
    dirty: Boolean,
    conflict: Boolean,
    submitFailureMessage: String?,
    onChange: (R11TeamLeaderForm) -> Unit,
    onUpload: () -> Unit,
    onReload: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(HhySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        if (conflict) item {
            EditorNotice("资料已被更新", "请重新加载服务端最新版本后再编辑，不能直接覆盖。", "重新加载", onReload)
        }
        submitFailureMessage?.let { message ->
            item { EditorNotice("保存未完成", message) }
        }
        item {
            EditorSection("个人与团队") {
                EditorField("团队长昵称", form.nickname, errors["nickname"], 1) { onChange(form.copy(nickname = it)) }
                EditorField("个人介绍", form.personalIntro, errors["personalIntro"], 3) { onChange(form.copy(personalIntro = it)) }
                EditorField("团队名称", form.teamName, errors["teamName"], 1) { onChange(form.copy(teamName = it)) }
                EditorField("团队介绍", form.teamIntro, errors["teamIntro"], 4) { onChange(form.copy(teamIntro = it)) }
            }
        }
        item {
            EditorSection("团队人数") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    R11TeamLeaderForm.SIZE_RANGES.forEach { value ->
                        FilterChip(selected = form.sizeRange == value, onClick = { onChange(form.copy(sizeRange = value)) }, label = { Text(value) })
                    }
                }
                errors["sizeRange"]?.let { Text(it, color = HhyColors.Error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            EditorSection("团队能力与合作") {
                EditorField("擅长领域", form.categoryCode, errors["categoryCode"], 1) { onChange(form.copy(categoryCode = it)) }
                EditorField("所在地区（选填）", form.regionCode, errors["regionCode"], 1) { onChange(form.copy(regionCode = it)) }
                EditorField("核心能力（选填）", form.skills, errors["skills"], 1) { onChange(form.copy(skills = it)) }
                EditorField("合作类型（选填）", form.cooperationTypes, errors["cooperationTypes"], 1) { onChange(form.copy(cooperationTypes = it)) }
                EditorField("合作要求（选填）", form.cooperationRequirement, errors["cooperationRequirement"], 3) { onChange(form.copy(cooperationRequirement = it)) }
                EditorField("过往案例（选填，每行一项）", form.pastCases, errors["pastCases"], 3) { onChange(form.copy(pastCases = it)) }
            }
        }
        item {
            EditorSection("团队图片") {
                OutlinedButton(onClick = onUpload, modifier = Modifier.fillMaxWidth()) {
                    Text(if (form.mediaIds.isEmpty()) "上传团队标识或风采图片" else "已选择 ${form.mediaIds.size} 张图片，重新选择")
                }
                errors["mediaIds"]?.let { Text(it, color = HhyColors.Error, style = MaterialTheme.typography.bodySmall) }
                Text("首张图片用于团队标识，其余图片展示团队风采。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            EditorSection("联系方式") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    contactChannels.forEach { (value, label) ->
                        FilterChip(selected = form.contactChannel == value, onClick = { onChange(form.copy(contactChannel = value)) }, label = { Text(label) })
                    }
                }
                EditorField(
                    if (form.existingContactAvailable) "新联系方式（留空则保持原值）" else "联系方式",
                    form.contactValue,
                    errors["contactValue"],
                    1,
                ) { onChange(form.copy(contactValue = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.acceptPrivateChat, onCheckedChange = { onChange(form.copy(acceptPrivateChat = it)) })
                    Text("允许已登录用户发起私聊")
                }
                Text("详情页默认只显示脱敏联系方式，用户主动获取后才显示原值。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (errors.isNotEmpty()) item { EditorNotice("请检查表单", "共有 ${errors.size} 项内容需要完善。") }
        if (dirty) item { Text("存在未保存的修改", color = HhyColors.Warning, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) = Card(
    Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(HhyRadius.NormalCard),
    colors = CardDefaults.cardColors(HhyColors.Surface),
    elevation = CardDefaults.cardElevation(HhyElevation.Card),
) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun EditorField(label: String, value: String, error: String?, lines: Int, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    singleLine = lines == 1,
    minLines = lines,
    isError = error != null,
    supportingText = error?.let { { Text(it) } },
)

@Composable
private fun EditorNotice(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(HhyColors.WarningSoft),
) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        Text(title, fontWeight = FontWeight.Bold, color = HhyColors.Warning)
        Text(body, color = HhyColors.TextSecondary)
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun EditorStateCard(padding: PaddingValues, title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(HhyColors.Surface)) {
            Column(Modifier.padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(body, color = HhyColors.TextSecondary)
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun EditorFailure(padding: PaddingValues, phase: R11TeamLeaderPhase, retry: () -> Unit) {
    val copy = when (phase) {
        R11TeamLeaderPhase.FORBIDDEN -> "无权编辑" to "当前账号不是资料所有者，或没有编辑权限。"
        R11TeamLeaderPhase.NOT_FOUND -> "资料不存在" to "内容可能已删除或链接已经失效。"
        R11TeamLeaderPhase.OFFLINE -> "网络不可用" to "表单不会离线提交，请恢复网络后重试。"
        else -> "加载失败" to "暂时无法读取团队资料，请稍后重试。"
    }
    EditorStateCard(padding, copy.first, copy.second, "重试", retry)
}
