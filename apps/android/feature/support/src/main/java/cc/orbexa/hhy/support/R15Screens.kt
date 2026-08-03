package cc.orbexa.hhy.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContractR15Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R15ContentReportRequest
import cc.orbexa.hhy.network.R15CreateTicketRequest
import cc.orbexa.hhy.network.R15NotificationResource
import cc.orbexa.hhy.network.R15SupportMessageRequest
import cc.orbexa.hhy.network.R15SupportTicketResource
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun R15MessageCenterScreen(
    contentPadding: PaddingValues,
    onOpenChats: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAnnouncements: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(HhySpacing.Lg).testTag("hhy.screen.r15.messages"),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Text("消息中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("聊天、通知与平台公告", color = HhyColors.TextSecondary)
        R15Entry("聊天消息", "查看联系人和未读会话", onOpenChats)
        R15Entry("系统通知", "订单、审核和账号动态", onOpenNotifications)
        R15Entry("平台公告", "查看平台规则与服务公告", onOpenAnnouncements)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15NotificationListScreen(
    api: ContractR15Api,
    accessToken: String,
    announcements: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<R15Load<List<R15NotificationResource>>>(R15Load.Loading) }
    var keyword by remember { mutableStateOf("") }
    var markingAll by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var searchVisible by remember { mutableStateOf(false) }

    fun load() {
        state = R15Load.Loading
        scope.launch {
            val result = if (announcements) api.announcements(accessToken, keyword) else api.notifications(accessToken, keyword, category)
            state = when (result) {
                is R07CallResult.Success -> if (result.data.items.isEmpty()) R15Load.Empty else R15Load.Content(result.data.items)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    R15Load.Failed(result)
                }
            }
        }
    }
    LaunchedEffect(announcements, accessToken, category) { load() }
    Scaffold(
        modifier = Modifier.testTag("hhy.screen.r15.${if (announcements) "announcements" else "notifications"}"),
        containerColor = HhyColors.Surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (announcements) "公告中心" else "通知中心") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        HhyIcon(HhyIcons.Search, "搜索")
                    }
                    IconButton(onClick = ::load) { HhyIcon(HhyIcons.Refresh, "刷新") }
                    if (!announcements) TextButton(
                        enabled = !markingAll,
                        onClick = {
                            markingAll = true
                            scope.launch {
                                when (val result = api.readAllNotifications(accessToken, intentKey())) {
                                    is R07CallResult.Success -> load()
                                    is R07CallResult.Failure -> if (result.statusCode == 401) onSessionExpired()
                                }
                                markingAll = false
                            }
                        },
                    ) { Text(if (markingAll) "处理中" else "全部已读") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (announcements) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md),
                    color = HhyColors.BrandPrimary,
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                            Text("合伙云 Pro", color = HhyColors.TextInverse, style = MaterialTheme.typography.titleMedium)
                            Text("官方公告", color = HhyColors.TextInverse, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("重要规则、活动与服务更新", color = HhyColors.TextInverse, style = MaterialTheme.typography.bodySmall)
                        }
                        HhyIcon(HhyIcons.Campaign, null, Modifier.size(48.dp), HhyColors.TextInverse)
                    }
                }
            } else {
                val filters = listOf(null to "全部", "SYSTEM" to "系统", "ACTIVITY" to "活动", "TRANSACTION" to "交易", "INTERACTION" to "互动")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    filters.forEach { (value, label) ->
                        Column(
                            Modifier.weight(1f).clickable { category = value },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                label,
                                color = if (category == value) HhyColors.BrandPrimary else HhyColors.TextSecondary,
                                fontWeight = if (category == value) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Spacer(Modifier.height(HhySpacing.Sm))
                            Box(
                                Modifier.fillMaxWidth().height(2.dp).background(
                                    if (category == value) HhyColors.BrandPrimary else HhyColors.Surface,
                                ),
                            )
                        }
                    }
                }
            }
            if (searchVisible) OutlinedTextField(
                value = keyword,
                onValueChange = { if (it.length <= 100) keyword = it },
                label = { Text(if (announcements) "搜索公告" else "搜索通知") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = ::load) { Text("搜索") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm),
            )
            when (val value = state) {
                R15Load.Loading -> R15Centered("正在加载", true)
            R15Load.Empty -> R15Centered(if (announcements) "暂无公告" else "暂无通知")
                is R15Load.Failed -> R15Failure(value.failure, onBack, ::load)
                is R15Load.Content -> LazyColumn(contentPadding = PaddingValues(bottom = HhySpacing.Xl)) {
                    items(value.value, key = R15NotificationResource::id) { item ->
                        R15NotificationRow(item) { onOpen(item.id) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15NotificationDetailScreen(
    api: ContractR15Api,
    accessToken: String,
    id: String,
    announcement: Boolean,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<R15Load<R15NotificationResource>>(R15Load.Loading) }
    fun load() {
        state = R15Load.Loading
        scope.launch {
            val result = if (announcement) api.announcement(accessToken, id)
            else api.notifications(accessToken).let { page ->
                when (page) {
                    is R07CallResult.Success -> page.data.items.firstOrNull { it.id == id }
                        ?.let { R07CallResult.Success(it, page.requestId, page.timestamp) }
                        ?: R07CallResult.Failure(404)
                    is R07CallResult.Failure -> page
                }
            }
            state = when (result) {
                is R07CallResult.Success -> R15Load.Content(result.data)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    R15Load.Failed(result)
                }
            }
            if (!announcement && result is R07CallResult.Success && result.data.readAt == null) {
                api.readNotification(accessToken, id, intentKey())
            }
        }
    }
    LaunchedEffect(id, accessToken) { load() }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (announcement) "公告详情" else "通知详情") }, navigationIcon = { HhyBackButton(onBack) }) },
        modifier = Modifier.testTag("hhy.screen.r15.notification-detail"),
    ) { padding ->
        when (val value = state) {
            R15Load.Loading -> Box(Modifier.fillMaxSize().padding(padding)) { R15Centered("正在加载", true) }
            R15Load.Empty -> Box(Modifier.fillMaxSize().padding(padding)) { R15Centered("内容不存在") }
            is R15Load.Failed -> Box(Modifier.fillMaxSize().padding(padding)) { R15Failure(value.failure, onBack, ::load) }
            is R15Load.Content -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                Text(value.value.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(value.value.createdAt, color = HhyColors.TextSecondary)
                HorizontalDivider()
                Text(value.value.body.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15SupportHomeScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onTickets: () -> Unit,
    onCreateTicket: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("帮助与客服") }, navigationIcon = { HhyBackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg).testTag("hhy.screen.r15.support"),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            R15Entry("帮助中心", "搜索常见问题和操作说明", onHelp)
            R15Entry("我的工单", "查看处理状态并补充信息", onTickets)
            Button(onClick = onCreateTicket, modifier = Modifier.fillMaxWidth()) { Text("创建工单") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15SupportListScreen(
    api: ContractR15Api,
    accessToken: String,
    help: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<R15Load<List<R15SupportTicketResource>>>(R15Load.Loading) }
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    fun load() {
        state = R15Load.Loading
        scope.launch {
            val result = if (help) api.helpArticles(accessToken, keyword) else api.tickets(accessToken, status)
            state = when (result) {
                is R07CallResult.Success -> if (result.data.items.isEmpty()) R15Load.Empty else R15Load.Content(result.data.items)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    R15Load.Failed(result)
                }
            }
        }
    }
    LaunchedEffect(help, accessToken) { load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (help) "帮助中心" else "我的工单") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { IconButton(onClick = ::load) { HhyIcon(HhyIcons.Refresh, "刷新") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("hhy.screen.r15.${if (help) "help" else "tickets"}")) {
            if (help) OutlinedTextField(
                value = keyword,
                onValueChange = { if (it.length <= 100) keyword = it },
                label = { Text("搜索帮助文章") },
                trailingIcon = { TextButton(onClick = ::load) { Text("搜索") } },
                modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md),
            ) else Row(
                Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                listOf(null to "全部", "OPEN" to "处理中", "CLOSED" to "已关闭").forEach { (code, label) ->
                    FilterChip(selected = status == code, onClick = { status = code; load() }, label = { Text(label) })
                }
            }
            when (val value = state) {
                R15Load.Loading -> R15Centered("正在加载", true)
                R15Load.Empty -> R15Centered(if (help) "没有找到帮助文章" else "还没有工单")
                is R15Load.Failed -> R15Failure(value.failure, onBack, ::load)
                is R15Load.Content -> LazyColumn(Modifier.weight(1f)) {
                    items(value.value, key = R15SupportTicketResource::id) { item -> R15TicketRow(item) { onOpen(item.id) } }
                }
            }
            if (!help) Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(HhySpacing.Md)) { Text("创建新工单") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15CreateTicketScreen(
    api: ContractR15Api,
    accessToken: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("ACCOUNT") }
    var subject by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("创建工单") }, navigationIcon = { HhyBackButton(onBack) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(HhySpacing.Lg)
                .testTag("hhy.screen.r15.ticket-create"),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text("问题类型", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                listOf("ACCOUNT" to "账号", "CONTENT" to "内容", "PAYMENT" to "订单").forEach { (code, label) ->
                    FilterChip(selected = category == code, onClick = { category = code }, label = { Text(label) })
                }
            }
            OutlinedTextField(subject, { if (it.length <= 200) subject = it }, label = { Text("主题") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { if (it.length <= 2000) content = it }, label = { Text("问题描述") }, minLines = 6, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = HhyColors.Error) }
            Button(
                enabled = subject.isNotBlank() && content.isNotBlank() && !submitting,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    submitting = true; error = null
                    scope.launch {
                        when (val result = api.createTicket(accessToken, intentKey(), R15CreateTicketRequest(category, subject.trim(), content.trim()))) {
                            is R07CallResult.Success -> onCreated(result.data.id)
                            is R07CallResult.Failure -> {
                                if (result.statusCode == 401) onSessionExpired()
                                error = failureText(result)
                            }
                        }
                        submitting = false
                    }
                },
            ) { Text(if (submitting) "正在提交" else "提交工单") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R15SupportDetailScreen(
    api: ContractR15Api,
    accessToken: String,
    id: String,
    help: Boolean,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<R15Load<R15SupportTicketResource>>(R15Load.Loading) }
    var reply by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun load() {
        state = R15Load.Loading
        scope.launch {
            val result = if (help) api.helpArticle(accessToken, id) else api.ticket(accessToken, id)
            state = when (result) {
                is R07CallResult.Success -> R15Load.Content(result.data)
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    R15Load.Failed(result)
                }
            }
        }
    }
    LaunchedEffect(id, accessToken) { load() }
        Scaffold(topBar = { TopAppBar(title = { Text(if (help) "帮助详情" else "工单详情") }, navigationIcon = { HhyBackButton(onBack) }) }) { padding ->
        when (val value = state) {
            R15Load.Loading -> Box(Modifier.fillMaxSize().padding(padding)) { R15Centered("正在加载", true) }
            R15Load.Empty -> Box(Modifier.fillMaxSize().padding(padding)) { R15Centered("内容不存在") }
            is R15Load.Failed -> Box(Modifier.fillMaxSize().padding(padding)) { R15Failure(value.failure, onBack, ::load) }
            is R15Load.Content -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                Text(value.value.subject ?: value.value.ticketNo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${value.value.category.orEmpty()} · ${value.value.status}", color = HhyColors.TextSecondary)
                Text("编号 ${value.value.ticketNo}")
                if (help) Text("如仍未解决，可返回客服中心创建工单。", color = HhyColors.TextSecondary)
                else {
                    HorizontalDivider()
                    OutlinedTextField(reply, { if (it.length <= 2000) reply = it }, label = { Text("补充信息") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    message?.let { Text(it, color = if (it == "补充信息已发送") HhyColors.Success else HhyColors.Error) }
                    Button(
                        enabled = reply.isNotBlank() && !submitting && value.value.status != "CLOSED",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            submitting = true; message = null
                            scope.launch {
                                when (val result = api.addMessage(accessToken, id, intentKey(), R15SupportMessageRequest(reply.trim()))) {
                                    is R07CallResult.Success -> { reply = ""; message = "补充信息已发送"; state = R15Load.Content(result.data) }
                                    is R07CallResult.Failure -> {
                                        if (result.statusCode == 401) onSessionExpired()
                                        message = failureText(result)
                                    }
                                }
                                submitting = false
                            }
                        },
                    ) { Text(if (submitting) "正在发送" else "发送补充信息") }
                }
            }
        }
    }
}

@Composable
fun R15ReportContentSheet(
    api: ContractR15Api,
    accessToken: String,
    contentId: String,
    expectedVersion: Long?,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("SPAM") }
    var description by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        Text("举报内容", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            listOf("SPAM" to "垃圾信息", "FRAUD" to "疑似欺诈", "ILLEGAL" to "违法违规").forEach { (code, label) ->
                FilterChip(selected = reason == code, onClick = { reason = code }, label = { Text(label) })
            }
        }
        OutlinedTextField(description, { if (it.length <= 2000) description = it }, label = { Text("详细说明") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = HhyColors.Error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            OutlinedButton(onClick = onDismiss, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                enabled = description.isNotBlank() && !submitting,
                modifier = Modifier.weight(1f),
                onClick = {
                    submitting = true
                    scope.launch {
                        when (val result = api.reportContent(accessToken, contentId, intentKey(), R15ContentReportRequest(reason, description.trim(), expectedVersion = expectedVersion))) {
                            is R07CallResult.Success -> onCompleted()
                            is R07CallResult.Failure -> {
                                if (result.statusCode == 401) onSessionExpired()
                                error = failureText(result)
                            }
                        }
                        submitting = false
                    }
                },
            ) { Text(if (submitting) "提交中" else "提交举报") }
        }
    }
}

@Composable
private fun R15Entry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            HhyIcon(HhyIcons.Information, null, tint = HhyColors.BrandPrimary)
            Column(Modifier.weight(1f).padding(horizontal = HhySpacing.Md)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            HhyIcon(HhyIcons.ChevronRight, null, tint = HhyColors.TextTertiary)
        }
    }
}

@Composable
private fun R15NotificationRow(item: R15NotificationResource, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg, vertical = HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = notificationIconColor(item),
            ) {
                HhyIcon(notificationIcon(item), null, tint = HhyColors.TextInverse, modifier = Modifier.padding(HhySpacing.Sm))
            }
            Column(Modifier.weight(1f).padding(horizontal = HhySpacing.Md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                item.body?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 1) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(notificationDate(item.createdAt), color = HhyColors.TextTertiary, style = MaterialTheme.typography.labelSmall)
                if (item.readAt == null) Surface(Modifier.size(8.dp), shape = CircleShape, color = HhyColors.RewardRed) {}
            }
        }
    }
    HorizontalDivider()
}

private fun notificationIcon(item: R15NotificationResource) = when (item.type) {
    "ACTIVITY" -> HhyIcons.Reward
    "TRANSACTION" -> HhyIcons.Reward
    "INTERACTION" -> HhyIcons.Message
    else -> HhyIcons.Information
}

private fun notificationIconColor(item: R15NotificationResource) = when (item.type) {
    "ACTIVITY" -> HhyColors.BrandPrimary
    "TRANSACTION" -> HhyColors.BrandTertiary
    "INTERACTION" -> HhyColors.BrandSecondary
    else -> HhyColors.BrandPrimary
}

private fun notificationDate(value: String): String = value.substringBefore('T').ifBlank { value }

@Composable
private fun R15TicketRow(item: R15SupportTicketResource, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Row(Modifier.fillMaxWidth()) {
                Text(item.subject ?: item.ticketNo, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(item.status, color = HhyColors.BrandPrimary)
            }
            Text("${item.category.orEmpty()} · ${item.ticketNo}", color = HhyColors.TextSecondary)
        }
    }
    HorizontalDivider()
}

@Composable
private fun R15Centered(text: String, progress: Boolean = false) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (progress) { CircularProgressIndicator(); Spacer(Modifier.height(HhySpacing.Md)) }
        Text(text, color = HhyColors.TextSecondary)
    }
}

@Composable
private fun R15Failure(failure: R07CallResult.Failure, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HhyIcon(HhyIcons.Error, null, tint = HhyColors.Error)
        Spacer(Modifier.height(HhySpacing.Md))
        Text(failureText(failure), fontWeight = FontWeight.SemiBold)
        failure.requestId?.let { Text("请求编号 $it", color = HhyColors.TextSecondary, style = MaterialTheme.typography.labelSmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

private sealed interface R15Load<out T> {
    data object Loading : R15Load<Nothing>
    data object Empty : R15Load<Nothing>
    data class Content<T>(val value: T) : R15Load<T>
    data class Failed(val failure: R07CallResult.Failure) : R15Load<Nothing>
}

private fun intentKey(): String = "android-r15-${UUID.randomUUID()}"

private fun failureText(failure: R07CallResult.Failure): String = when (failure.statusCode) {
    null -> "网络不可用，请恢复网络后重试"
    400 -> failure.fieldErrors.values.firstOrNull() ?: "请检查输入内容"
    401 -> "登录状态已失效"
    403 -> "当前账号没有访问权限"
    404 -> "内容不存在或已失效"
    409 -> "数据已经变化，请刷新后重试"
    422 -> "当前状态不允许此操作"
    429 -> failure.retryAfterSeconds?.let { "操作频繁，请 $it 秒后重试" } ?: "操作频繁，请稍后重试"
    else -> "服务暂时不可用，请稍后重试"
}
