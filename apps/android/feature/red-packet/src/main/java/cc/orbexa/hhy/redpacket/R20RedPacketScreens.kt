@file:OptIn(ExperimentalMaterial3Api::class)

package cc.orbexa.hhy.redpacket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyOpacity
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractR20RedPacketApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R20CreateRedPacketRequest
import cc.orbexa.hhy.network.R20OrderRequest
import cc.orbexa.hhy.network.R20PatchRedPacketRequest
import cc.orbexa.hhy.network.R20QuoteRequest
import cc.orbexa.hhy.network.R20RedPacketCampaignResource
import cc.orbexa.hhy.network.R20RedPacketPage
import cc.orbexa.hhy.network.R20SubmitReviewRequest
import java.util.UUID
import kotlinx.coroutines.launch

private val campaignFilters = listOf(
    null to "全部",
    "ACTIVE" to "进行中",
    "DRAFT" to "未开始",
    "CLOSED_BY_OWNER" to "已结束",
)

@Composable
fun R20RedPacketCampaignListScreen(
    api: ContractR20RedPacketApi,
    token: String,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenReview: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var filter by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<R20RedPacketCampaignResource>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var appending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun request(target: Int, refresh: Boolean) {
        try {
            when (val result = api.campaigns(token, target, 20, filter)) {
                is R07CallResult.Success -> {
                    items = if (refresh) result.data.items else (items + result.data.items).distinctBy { it.id }
                    page = target
                    hasMore = result.data.page.canLoadMore()
                    error = null
                }
                is R07CallResult.Failure -> {
                    error = result
                    if (result.statusCode == 401) onExpired()
                }
            }
        } finally {
            loading = false
            appending = false
        }
    }
    fun load(refresh: Boolean = true) {
        if (loading || appending || (!refresh && !hasMore)) return
        val target = if (refresh) 1 else page + 1
        if (refresh) loading = true else appending = true
        scope.launch { request(target, refresh) }
    }
    LaunchedEffect(filter, token) {
        loading = true
        appending = false
        request(target = 1, refresh = true)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("红包活动管理") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { TextButton(onClick = onCreate) { Text("新建活动") } },
            )
        },
        modifier = Modifier.testTag("hhy.screen.scr-rp-adv-001"),
    ) { padding ->
        Column(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                items(campaignFilters, key = { it.second }) { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
            if (loading && items.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading && items.isEmpty() -> LoadingPanel()
                error != null && items.isEmpty() -> FailurePanel(error!!, ::load)
                items.isEmpty() -> EmptyPanel("暂无红包活动", ::load)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = HhySpacing.Lg, vertical = HhySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    items(items, key = { it.id }) { item -> CampaignCard(item) { onOpenReview(item.id) } }
                    if (error != null) item { InlineFailure(error!!, ::load) }
                    if (hasMore) item {
                        TextButton(onClick = { load(false) }, enabled = !appending, modifier = Modifier.fillMaxWidth()) {
                            Text(if (appending) "正在加载" else "加载更多")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun R20RedPacketCreateScreen(
    api: ContractR20RedPacketApi,
    token: String,
    campaignId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var contentId by remember { mutableStateOf("") }
    var totalCount by remember { mutableStateOf("100") }
    var unitCent by remember { mutableStateOf("100") }
    var startAt by remember { mutableStateOf("") }
    var endAt by remember { mutableStateOf("") }
    var targeting by remember { mutableStateOf("") }
    var expectedVersion by remember { mutableStateOf<Long?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<R07CallResult<out R20RedPacketCampaignResource>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(campaignId, token) {
        if (campaignId == null) return@LaunchedEffect
        when (val loaded = api.campaign(token, campaignId)) {
            is R07CallResult.Success -> {
                contentId = loaded.data.contentId
                totalCount = loaded.data.totalCount.toString()
                unitCent = loaded.data.amountPerClaimCent.toString()
                startAt = loaded.data.startAt.orEmpty()
                endAt = loaded.data.endAt.orEmpty()
                expectedVersion = loaded.data.version
                result = null
            }
            is R07CallResult.Failure -> {
                result = loaded
                if (loaded.statusCode == 401) onExpired()
            }
        }
    }
    fun save(submitReview: Boolean) {
        val count = totalCount.toLongOrNull(); val amount = unitCent.toLongOrNull()
        if ((campaignId == null && contentId.isBlank()) || count == null || amount == null
            || startAt.isBlank() || endAt.isBlank() || (campaignId != null && expectedVersion == null)) {
            result = R07CallResult.Failure(422, errorCode = "INVALID_FORM"); return
        }
        submitting = true
        scope.launch {
            val saved = if (campaignId == null) {
                api.create(token, R20CreateRedPacketRequest(contentId.trim(), count, amount, startAt.trim(), endAt.trim()), key("create"))
            } else {
                api.patch(
                    token,
                    campaignId,
                    R20PatchRedPacketRequest(
                        totalCount = count,
                        amountPerClaimCent = amount,
                        startAt = startAt.trim(),
                        endAt = endAt.trim(),
                        expectedVersion = expectedVersion!!,
                    ),
                    key("patch"),
                )
            }
            result = saved
            when (saved) {
                is R07CallResult.Success -> if (submitReview) {
                    val reviewed = api.submitReview(token, saved.data.id, R20SubmitReviewRequest(saved.data.version, targeting.takeIf { it.isNotBlank() }), key("review"))
                    result = reviewed
                    if (reviewed is R07CallResult.Success) onSaved(reviewed.data.id)
                    if (reviewed is R07CallResult.Failure && reviewed.statusCode == 401) onExpired()
                } else onSaved(saved.data.id)
                is R07CallResult.Failure -> if (saved.statusCode == 401) onExpired()
            }
            submitting = false
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (campaignId == null) "创建红包" else "编辑红包") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = {
            Surface(shadowElevation = HhyElevation.Dialog) {
                Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(onClick = { save(false) }, enabled = !submitting, modifier = Modifier.weight(1f)) { Text("保存草稿") }
                    Button(onClick = { save(true) }, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(if (submitting) "提交中" else "提交预审核") }
                }
            }
        },
        modifier = Modifier.testTag("hhy.screen.scr-rp-adv-002"),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item { SectionCard("关联内容") { Text("红包将关联已发布内容，服务端会再次校验广告主资格。", color = HhyColors.TextSecondary); R20Field("内容 ID", contentId, { contentId = it }, "例如 content-123") } }
            item { SectionCard("金额与规则") { R20Field("红包总数量", totalCount, { totalCount = it.filter(Char::isDigit) }, "1 - 1,000,000"); R20Field("单个红包金额（分）", unitCent, { unitCent = it.filter(Char::isDigit) }, "服务端按分校验"); R20Field("定向规则（可选）", targeting, { targeting = it }, "仅保留业务备注") } }
            item { SectionCard("投放时间") { R20Field("开始时间", startAt, { startAt = it }, "ISO-8601，例如 2026-08-05T10:00:00Z"); R20Field("结束时间", endAt, { endAt = it }, "ISO-8601") } }
            item { MoneySummary(amount = (totalCount.toLongOrNull() ?: 0) * (unitCent.toLongOrNull() ?: 0)) }
            item { Spacer(Modifier.height(HhySpacing.Xl)) }
            (result as? R07CallResult.Failure)?.let { failure -> item { InlineFailure(failure, {}) } }
        }
    }
}

@Composable
fun R20RedPacketReviewResultScreen(
    api: ContractR20RedPacketApi,
    token: String,
    campaignId: String,
    onBack: () -> Unit,
    onQuote: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var resource by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { when (val value = api.campaign(token, campaignId)) { is R07CallResult.Success -> { resource = value.data; failure = null }; is R07CallResult.Failure -> { failure = value; if (value.statusCode == 401) onExpired() } } } }
    fun submit() { val version = resource?.version ?: return; submitting = true; scope.launch { when (val value = api.submitReview(token, campaignId, R20SubmitReviewRequest(version), key("review"))) { is R07CallResult.Success -> resource = value.data; is R07CallResult.Failure -> { failure = value; if (value.statusCode == 401) onExpired() } }; submitting = false } }
    LaunchedEffect(campaignId, token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("预审核结果") }, navigationIcon = { HhyBackButton(onBack) }) }, modifier = Modifier.testTag("hhy.screen.scr-rp-adv-003")) { padding ->
        when {
            resource == null && failure == null -> LoadingPanel(padding)
            failure != null && resource == null -> FailurePanel(failure!!, ::load, padding)
            else -> LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding), contentPadding = PaddingValues(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                item { ReviewStatePanel(resource!!, onRefresh = ::load) }
                item { SectionCard("业务摘要") { Text("活动 ${resource!!.id}"); Text("内容 ${resource!!.contentId}"); Text("数量 ${resource!!.totalCount} · 单个 ${money(resource!!.amountPerClaimCent)}"); Text("时间 ${resource!!.startAt ?: "待确认"} - ${resource!!.endAt ?: "待确认"}") } }
                item { SectionCard("版本与审计") { Text("服务端版本 v${resource!!.version}", fontWeight = FontWeight.SemiBold); Text("提交操作使用幂等键并保留请求上下文", color = HhyColors.TextSecondary) } }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Button(onClick = ::submit, enabled = !submitting && r20CanSubmitReview(resource!!.status, resource!!.version), modifier = Modifier.weight(1f)) { Text(if (submitting) "提交中" else "提交预审核") }; OutlinedButton(onClick = { onQuote(campaignId) }, enabled = r20CanRequestQuote(resource!!.status, resource!!.version), modifier = Modifier.weight(1f)) { Text("查看报价") } } }
            }
        }
    }
}

@Composable
fun R20RedPacketQuoteScreen(api: ContractR20RedPacketApi, token: String, campaignId: String, onBack: () -> Unit, onCompleted: () -> Unit, onExpired: () -> Unit) {
    var resource by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var quote by remember { mutableStateOf<CommandResultResource?>(null) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var busy by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf("ALIPAY") }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { when (val value = api.campaign(token, campaignId)) { is R07CallResult.Success -> resource = value.data; is R07CallResult.Failure -> { failure = value; if (value.statusCode == 401) onExpired() } } } }
    fun requestQuote() { val item = resource ?: return; busy = true; scope.launch { when (val value = api.quote(token, campaignId, R20QuoteRequest(item.totalCount, item.amountPerClaimCent, item.version), key("quote"))) { is R07CallResult.Success -> quote = value.data; is R07CallResult.Failure -> { failure = value; if (value.statusCode == 401) onExpired() } }; busy = false } }
    fun order() { val item = resource ?: return; val q = quote ?: return; busy = true; scope.launch { when (val value = api.order(token, campaignId, R20OrderRequest(q.resourceId.orEmpty(), channel, item.version), key("order"))) { is R07CallResult.Success -> onCompleted(); is R07CallResult.Failure -> { failure = value; if (value.statusCode == 401) onExpired() } }; busy = false } }
    LaunchedEffect(campaignId, token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("红包报价确认") }, navigationIcon = { HhyBackButton(onBack) }) }, bottomBar = { Surface(shadowElevation = HhyElevation.Dialog) { Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { OutlinedButton(onClick = ::requestQuote, enabled = !busy && resource?.let { r20CanRequestQuote(it.status, it.version) } == true, modifier = Modifier.weight(1f)) { Text("重新报价") }; Button(onClick = ::order, enabled = !busy && resource?.let { r20CanOrder(quote?.status, it.status, it.version) } == true, modifier = Modifier.weight(1f)) { Text(if (busy) "处理中" else "确认并创建订单") } } } }, modifier = Modifier.testTag("hhy.screen.scr-rp-adv-004")) { padding ->
        when { resource == null && failure == null -> LoadingPanel(padding); failure != null && resource == null -> FailurePanel(failure!!, ::load, padding); else -> LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { item { MoneySummary(resource!!.principalCent + resource!!.serviceFeeCent) }; item { SectionCard("报价明细") { Text("红包本金：${money(resource!!.principalCent)}"); Text("服务费：${money(resource!!.serviceFeeCent)}"); Text("应付合计：${money(resource!!.principalCent + resource!!.serviceFeeCent)}", fontWeight = FontWeight.Bold); Text("报价状态：${quoteStatusLabel(quote?.status)}") } }; item { SectionCard("活动上下文") { Text("活动 ${resource!!.id}"); Text("${resource!!.totalCount} 个红包 · 单个 ${money(resource!!.amountPerClaimCent)}"); quote?.resourceId?.let { Text("报价编号：$it", color = HhyColors.TextSecondary) } } }; item { Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { FilterChip(channel == "ALIPAY", { channel = "ALIPAY" }, { Text("支付宝") }); FilterChip(channel == "WECHAT_PAY", { channel = "WECHAT_PAY" }, { Text("微信支付") }) } }; item { Text("红包订单创建后不可退款，请在确认前核对金额与活动版本。", color = HhyColors.TextSecondary) }; (failure as? R07CallResult.Failure)?.let { item { InlineFailure(it, {}) } } } }
    }
}

private fun key(prefix: String) = "r20-$prefix-${UUID.randomUUID()}"
private fun money(value: Long) = "¥%.2f".format(value / 100.0)
private fun statusLabel(status: String) = r20StatusLabel(status)
private fun quoteStatusLabel(status: String?) = when (status) {
    null -> "未获取"
    "QUOTED" -> "报价有效"
    "EXPIRED" -> "报价已过期"
    else -> "状态待确认"
}
private fun failureText(value: R07CallResult.Failure) = when (value.statusCode) { null -> "网络不可用，请检查网络后重试"; 401 -> "登录状态已失效"; 403 -> "当前账号无权访问此红包活动"; 404 -> "红包活动不存在或已不可访问"; 409 -> "活动版本已变化，请刷新后再操作"; 422 -> "提交内容未通过业务校验"; else -> "服务暂时不可用，请稍后重试" }

@Composable private fun CampaignCard(item: R20RedPacketCampaignResource, onClick: () -> Unit) { Card(onClick = onClick, Modifier.fillMaxWidth().testTag("hhy.redpacket.campaign.${item.id}"), shape = RoundedCornerShape(HhyRadius.NormalCard), border = BorderStroke(HhySize.Hairline, HhyColors.Border), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) { Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("内容 ${item.contentId}", fontWeight = FontWeight.SemiBold); Text("活动 ${item.id}", style = MaterialTheme.typography.labelSmall, color = HhyColors.TextSecondary) }; StatusChip(statusLabel(item.status), item.status) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("总额 ${money(item.principalCent)}", fontWeight = FontWeight.Bold); Text("已发放 ${money(item.principalCent - item.remainingCount * item.amountPerClaimCent)}", style = MaterialTheme.typography.bodySmall) }; Text("${item.totalCount} 个 · 单个 ${money(item.amountPerClaimCent)}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary); Text("${item.startAt ?: "待开始"} - ${item.endAt ?: "待结束"}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) } } }
@Composable private fun ReviewStatePanel(item: R20RedPacketCampaignResource, onRefresh: () -> Unit) { val rejected = item.status == "PRE_REVIEW_REJECTED"; Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.LargeCard), color = if (rejected) HhyColors.ErrorSoft else HhyColors.WarningSoft) { Column(Modifier.padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(statusLabel(item.status), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(if (rejected) "服务端已返回审核结果，请修改活动后重新提交" else "状态由服务端确认，提交后可在本页刷新", color = HhyColors.TextSecondary); TextButton(onClick = onRefresh) { Text("刷新状态") } } } }
@Composable private fun MoneySummary(amount: Long) { Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.LargeCard), color = HhyColors.RewardRed) { Column(Modifier.padding(HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text("金额摘要", color = HhyColors.TextInverse); Text(money(amount), color = HhyColors.TextInverse, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("本金与服务费以服务端报价为准", color = HhyColors.TextInverse) } } }
@Composable private fun StatusChip(label: String, status: String) { val danger = status in setOf("PRE_REVIEW_REJECTED", "PAUSED_BY_RISK", "TERMINATED_BY_PLATFORM"); Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = if (danger) HhyColors.ErrorSoft else HhyColors.SoftBlue) { Text(label, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), style = MaterialTheme.typography.labelSmall, color = if (danger) HhyColors.Error else HhyColors.BrandPrimary) } }
@Composable private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) { Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); HorizontalDivider(); content() } } }
@Composable private fun R20Field(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) { OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true) }
@Composable private fun LoadingPanel(padding: PaddingValues = PaddingValues()) { Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun FailurePanel(value: R07CallResult.Failure, retry: () -> Unit, padding: PaddingValues = PaddingValues()) { Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xl), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(failureText(value), style = MaterialTheme.typography.titleMedium); value.requestId?.let { Text("请求编号：$it", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) }; Button(onClick = retry) { Text("重试") } } } }
@Composable private fun EmptyPanel(message: String, retry: () -> Unit) { Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) { Text(message, color = HhyColors.TextSecondary); OutlinedButton(onClick = retry) { Text("刷新") } } } }
@Composable private fun InlineFailure(value: R07CallResult.Failure, retry: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = HhySpacing.Sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(failureText(value), color = HhyColors.Error, modifier = Modifier.weight(1f)); TextButton(onClick = retry) { Text("重试") } } }
