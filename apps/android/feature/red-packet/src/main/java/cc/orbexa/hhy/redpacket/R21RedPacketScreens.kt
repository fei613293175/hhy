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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContractR20RedPacketApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R20IncreaseOrderRequest
import cc.orbexa.hhy.network.R20IncreaseQuoteRequest
import cc.orbexa.hhy.network.R20LifecycleRequest
import cc.orbexa.hhy.network.R20RedPacketCampaignResource
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun R21RedPacketDetailScreen(
    api: ContractR20RedPacketApi,
    token: String,
    campaignId: String,
    onBack: () -> Unit,
    onOpenIncrease: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var item by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            when (val result = api.analytics(token, campaignId)) {
                is R07CallResult.Success -> {
                    item = result.data.items.firstOrNull()
                    failure = if (item == null) R07CallResult.Failure(404, errorCode = "NOT_FOUND") else null
                }
                is R07CallResult.Failure -> {
                    failure = result
                    if (result.statusCode == 401) onExpired()
                }
            }
        }
    }

    fun apply(action: String) {
        val current = item ?: return
        busy = true
        scope.launch {
            val request = R20LifecycleRequest("发起人通过活动详情操作", current.version)
            val result = when (action) {
                "pause" -> api.pause(token, campaignId, request, r21Key("pause"))
                "resume" -> api.resume(token, campaignId, request, r21Key("resume"))
                else -> api.close(token, campaignId, request, r21Key("close"))
            }
            when (result) {
                is R07CallResult.Success -> item = result.data
                is R07CallResult.Failure -> {
                    failure = result
                    if (result.statusCode == 401) onExpired()
                    if (result.statusCode == 409) load()
                }
            }
            busy = false
        }
    }

    LaunchedEffect(campaignId, token) { load() }
    val current = item
    if (confirmAction != null && current != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmAction = null },
            title = { Text(if (confirmAction == "close") "确认关闭活动" else "确认操作") },
            text = { Text(if (confirmAction == "close") "关闭后不会退款，且不能恢复。" else "操作将使用当前服务端版本提交。") },
            confirmButton = {
                Button(onClick = { val action = confirmAction!!; confirmAction = null; apply(action) }, enabled = !busy) {
                    Text(if (busy) "提交中" else "确认")
                }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }, enabled = !busy) { Text("取消") } },
        )
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("红包活动详情") }, navigationIcon = { HhyBackButton(onBack) }, actions = { TextButton(onClick = ::load) { Text("刷新") } }) },
        bottomBar = {
            if (current != null) Surface(shadowElevation = HhyElevation.Dialog) {
                Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    when {
                        r21CanPause(current.status, current.version) -> Button(onClick = { confirmAction = "pause" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("暂停") }
                        r21CanResume(current.status, current.version) -> Button(onClick = { confirmAction = "resume" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("恢复") }
                    }
                    if (r21CanRequestIncrease(current.status, current.version)) OutlinedButton(onClick = { onOpenIncrease(campaignId) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("提高金额") }
                    if (r21CanClose(current.status, current.version)) OutlinedButton(onClick = { confirmAction = "close" }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("关闭") }
                }
            }
        },
        modifier = Modifier.testTag("hhy.screen.scr-rp-adv-005"),
    ) { padding ->
        when {
            current == null && failure == null -> LoadingPanelR21(padding)
            current == null -> FailurePanelR21(failure!!, ::load, padding)
            else -> LazyColumn(
                Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding),
                contentPadding = PaddingValues(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { R21TitleBlock(current) }
                item { R21StatusSummary(current) }
                item { R21Details(current) }
                if (failure != null) item { InlineFailureR21(failure!!, ::load) }
                item { Spacer(Modifier.height(HhySpacing.Xl)) }
            }
        }
    }
}

@Composable
fun R21RedPacketIncreaseAmountScreen(
    api: ContractR20RedPacketApi,
    token: String,
    campaignId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onExpired: () -> Unit,
) {
    var item by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var quote by remember { mutableStateOf<CommandResultResource?>(null) }
    var newAmount by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("ALIPAY") }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            when (val result = api.campaign(token, campaignId)) {
                is R07CallResult.Success -> {
                    item = result.data
                    if (newAmount.isBlank()) newAmount = (result.data.amountPerClaimCent + 100).toString()
                }
                is R07CallResult.Failure -> {
                    failure = result
                    if (result.statusCode == 401) onExpired()
                }
            }
        }
    }

    fun requestQuote() {
        val current = item ?: return
        val amount = newAmount.toLongOrNull()
        if (amount == null || amount <= current.amountPerClaimCent || !r21CanRequestIncrease(current.status, current.version)) {
            failure = R07CallResult.Failure(422, errorCode = "INVALID_INCREASE_AMOUNT")
            return
        }
        busy = true
        scope.launch {
            when (val result = api.increaseQuote(token, campaignId, R20IncreaseQuoteRequest(amount, current.version), r21Key("increase-quote"))) {
                is R07CallResult.Success -> { quote = result.data; failure = null }
                is R07CallResult.Failure -> {
                    failure = result
                    quote = null
                    if (result.statusCode == 401) onExpired()
                    if (result.statusCode == 409) load()
                }
            }
            busy = false
        }
    }

    fun order() {
        val current = item ?: return
        val currentQuote = quote ?: return
        if (!r21QuoteReady(currentQuote)) return
        busy = true
        scope.launch {
            when (val result = api.increaseOrder(
                token,
                campaignId,
                R20IncreaseOrderRequest(currentQuote.resourceId!!, channel, current.version),
                r21Key("increase-order"),
            )) {
                is R07CallResult.Success -> onCompleted()
                is R07CallResult.Failure -> {
                    failure = result
                    if (result.statusCode == 401) onExpired()
                    if (result.statusCode == 409) { quote = null; load() }
                }
            }
            busy = false
        }
    }

    LaunchedEffect(campaignId, token) { load() }
    val current = item
    val ready = r21QuoteReady(quote)
    Scaffold(
        topBar = { TopAppBar(title = { Text("提高红包金额") }, navigationIcon = { HhyBackButton(onBack) }, actions = { TextButton(onClick = ::load) { Text("刷新") } }) },
        bottomBar = {
            Surface(shadowElevation = HhyElevation.Dialog) {
                Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(onClick = ::requestQuote, enabled = !busy && current != null, modifier = Modifier.weight(1f)) { Text("重新报价") }
                    Button(onClick = ::order, enabled = !busy && ready, modifier = Modifier.weight(1f)) { Text(if (busy) "处理中" else "确认支付") }
                }
            }
        },
        modifier = Modifier.testTag("hhy.screen.scr-rp-adv-006"),
    ) { padding ->
        when {
            current == null && failure == null -> LoadingPanelR21(padding)
            current == null -> FailurePanelR21(failure!!, ::load, padding)
            else -> LazyColumn(
                Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding),
                contentPadding = PaddingValues(HhySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { R21IncreaseContext(current) }
                item {
                    R21Section("提高后的单个金额") {
                        OutlinedTextField(newAmount, { newAmount = it.filter(Char::isDigit); quote = null }, Modifier.fillMaxWidth(), label = { Text("新单个金额（分）") }, singleLine = true)
                        Text("报价只能提高剩余红包的单价，库存与版本由服务端校验。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    R21Section("服务端报价") {
                        Text("报价状态：${r21QuoteStatusLabel(quote)}")
                        quote?.principalCent?.let { Text("增加本金：${r21Money(it)}") }
                        quote?.serviceFeeCent?.let { Text("服务费：${r21Money(it)}") }
                        quote?.payableCent?.let { Text("最终应付：${r21Money(it)}", fontWeight = FontWeight.Bold) }
                        quote?.expiresAt?.let { Text("有效期至：$it", color = HhyColors.TextSecondary) }
                    }
                }
                item {
                    R21Section("协议与支付方式") {
                        Text("红包订单创建后不可退款，请确认服务端最终金额。", color = HhyColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                            FilterChip(channel == "ALIPAY", { channel = "ALIPAY" }, { Text("支付宝") })
                            FilterChip(channel == "WECHAT_PAY", { channel = "WECHAT_PAY" }, { Text("微信支付") })
                        }
                    }
                }
                if (failure != null) item { InlineFailureR21(failure!!, ::load) }
                item { Spacer(Modifier.height(HhySpacing.Xl)) }
            }
        }
    }
}

@Composable private fun R21TitleBlock(item: R20RedPacketCampaignResource) {
    R21Section("红包活动") {
        Text("活动 ${item.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("关联内容 ${item.contentId}", color = HhyColors.TextSecondary)
        Text("所有者 ${item.ownerUserId ?: "当前账号"}", color = HhyColors.TextSecondary)
    }
}

@Composable private fun R21StatusSummary(item: R20RedPacketCampaignResource) {
    R21Section("状态与统计") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r20StatusLabel(item.status), fontWeight = FontWeight.SemiBold)
            Text("版本 v${item.version}", color = HhyColors.TextSecondary)
        }
        Text("剩余 ${item.remainingCount} / ${item.totalCount} 个")
        Text("单个 ${r21Money(item.amountPerClaimCent)} · 本金 ${r21Money(item.principalCent)}")
        Text("服务费 ${r21Money(item.serviceFeeCent)}", color = HhyColors.TextSecondary)
    }
}

@Composable private fun R21Details(item: R20RedPacketCampaignResource) {
    R21Section("分区详情") {
        Text("投放时间")
        Text("${item.startAt ?: "待开始"} - ${item.endAt ?: "待结束"}", color = HhyColors.TextSecondary)
        HorizontalDivider()
        Text("金额事实由服务端活动版本与报价返回，客户端不自行推导状态。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun R21IncreaseContext(item: R20RedPacketCampaignResource) {
    R21Section("活动上下文") {
        Text("活动 ${item.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("剩余 ${item.remainingCount} 个 · 当前单个 ${r21Money(item.amountPerClaimCent)}")
        Text("活动版本 v${item.version} · ${r20StatusLabel(item.status)}", color = HhyColors.TextSecondary)
    }
}

@Composable private fun R21Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), border = BorderStroke(HhySize.Hairline, HhyColors.Border), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) {
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable private fun LoadingPanelR21(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
}

@Composable private fun FailurePanelR21(value: R07CallResult.Failure, retry: () -> Unit, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xl), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Text(r21FailureText(value), style = MaterialTheme.typography.titleMedium)
            value.requestId?.let { Text("请求编号：$it", color = HhyColors.TextSecondary) }
            Button(onClick = retry) { Text("重试") }
        }
    }
}

@Composable private fun InlineFailureR21(value: R07CallResult.Failure, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = HhySpacing.Sm), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(r21FailureText(value), color = HhyColors.Error, modifier = Modifier.weight(1f))
        TextButton(onClick = retry) { Text("重试") }
    }
}

private fun r21Key(prefix: String) = "r21-$prefix-${UUID.randomUUID()}"
private fun r21Money(value: Long) = "¥%.2f".format(value / 100.0)
private fun r21FailureText(value: R07CallResult.Failure) = when (value.statusCode) {
    null -> "网络不可用，请检查网络后重试"
    401 -> "登录状态已失效"
    403 -> "当前账号无权访问此红包活动"
    404 -> "红包活动不存在或已不可访问"
    409 -> "活动版本已变化，请刷新后重新确认"
    422 -> "提交内容未通过业务校验"
    else -> "服务暂时不可用，请稍后重试"
}
