package cc.orbexa.hhy.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.*
import cc.orbexa.hhy.network.ContractR16Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R16OrderResource
import kotlinx.coroutines.launch

private val orderStatuses = listOf(
    null to "全部", "PENDING_PAYMENT" to "待支付", "PAYMENT_PROCESSING" to "处理中",
    "PAID" to "已支付", "FULFILLING" to "履约中", "COMPLETED" to "已完成", "CLOSED" to "已关闭",
)

internal fun r16Money(value: Long?, currency: String = "CNY") = value?.let {
    if (currency == "CNY") "¥%.2f".format(it / 100.0) else "$currency %.2f".format(it / 100.0)
} ?: "暂不可用"

internal fun r16Status(value: String) = mapOf(
    "PENDING_PAYMENT" to "待支付", "PAYMENT_PROCESSING" to "支付处理中", "PAID" to "已支付",
    "FULFILLING" to "履约中", "COMPLETED" to "已完成", "PAYMENT_FAILED" to "支付失败",
    "CLOSED" to "已关闭", "CHANNEL_REVERSAL" to "渠道冲正",
)[value] ?: "订单状态暂不可用"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R16OrderListScreen(
    api: ContractR16Api,
    token: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<R16OrderResource>>(emptyList()) }
    var filter by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var appending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<R07CallResult.Failure?>(null) }

    fun load(refresh: Boolean = true) {
        if (loading || appending) return
        val targetPage = if (refresh) 1 else page + 1
        if (!refresh && !hasMore) return
        if (refresh) loading = true else appending = true
        scope.launch {
            when (val result = api.orders(token, filter, targetPage)) {
                is R07CallResult.Success -> {
                    val next = if (refresh) result.data.items else (items + result.data.items).distinctBy(R16OrderResource::orderNo)
                    items = next; page = targetPage; hasMore = result.data.page.canLoadMore(); error = null
                }
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onExpired()
                    error = result
                }
            }
            loading = false; appending = false
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(filter) { if (!loading) load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的订单") }, navigationIcon = { HhyBackButton(onBack) }, actions = { IconButton(onClick = { load() }) { HhyIcon(HhyIcons.Refresh, "刷新") } }) },
        modifier = Modifier.testTag("hhy.screen.scr-order-001"),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(orderStatuses) { (value, label) -> FilterChip(modifier = Modifier.testTag("hhy.order.filter.${value ?: "ALL"}"), selected = filter == value, onClick = { filter = value }, label = { Text(label) }) }
            }
            if (loading && items.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading && items.isEmpty() -> R16OrderSkeleton()
                error != null && items.isEmpty() -> R16Failure(error!!, onBack, retry = { load() })
                items.isEmpty() -> R16Empty("暂无订单")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = R16OrderResource::orderNo) { order -> R16OrderCard(order) { onOpen(order.orderNo) } }
                    if (error != null) item { R16InlineFailure(error!!, ::load) }
                    if (hasMore) item { TextButton(onClick = { load(false) }, modifier = Modifier.fillMaxWidth().testTag("hhy.order.load-more"), enabled = !appending) { Text(if (appending) "正在加载" else "加载更多") } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R16OrderDetailScreen(api: ContractR16Api, token: String, orderNo: String, onBack: () -> Unit, onExpired: () -> Unit) {
    val scope = rememberCoroutineScope(); var state by remember { mutableStateOf<R07CallResult<R16OrderResource>?>(null) }
    fun load() { scope.launch { state = api.order(token, orderNo).also { if (it is R07CallResult.Failure && it.statusCode == 401) onExpired() } } }
    LaunchedEffect(orderNo, token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("订单详情") }, navigationIcon = { HhyBackButton(onBack) }, actions = { IconButton(onClick = ::load) { HhyIcon(HhyIcons.Refresh, "刷新") } }) }, modifier = Modifier.testTag("hhy.screen.scr-order-002")) { pad ->
        when (val value = state) {
            null -> R16OrderSkeleton(pad)
            is R07CallResult.Failure -> R16Failure(value, onBack, ::load, pad)
            is R07CallResult.Success -> R16Detail(value.data, pad)
        }
    }
}

@Composable private fun R16OrderCard(order: R16OrderResource, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("hhy.order.${order.orderNo}"), shape = RoundedCornerShape(HhyRadius.NormalCard)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(order.orderNo, style = MaterialTheme.typography.labelMedium); Text(r16Status(order.status), color = HhyColors.BrandPrimary, fontWeight = FontWeight.SemiBold) }; Text(order.items.take(2).joinToString("、") { "${it.itemName} × ${it.quantity}" }.ifBlank { "订单明细暂不可用" }); Text("订单类型：${order.orderType}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("应付 ${r16Money(order.priceSnapshot.payableAmountCent, order.currency)}", fontWeight = FontWeight.Bold); Text("实付 ${r16Money(order.paidAmountCent, order.currency)}", style = MaterialTheme.typography.bodySmall) }; Text(if (order.noRefundEvidence.confirmed) "已确认不可退款约定" else "不退款确认：未记录", style = MaterialTheme.typography.bodySmall); Text(order.createdAt, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) } } }
@Composable private fun R16Detail(order: R16OrderResource, pad: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { R16Section("状态摘要") { Text(order.orderNo); Text("${r16Status(order.status)} · ${order.orderType}"); Text("创建时间：${order.createdAt}") } }; item { R16Section("订单项") { if (order.items.isEmpty()) Text("订单明细暂不可用") else order.items.forEach { Text("${it.itemName} × ${it.quantity}    ${r16Money(it.subtotalAmountCent, order.currency)}") } } }; item { R16Section("价格明细") { Text("原价：${r16Money(order.priceSnapshot.originalAmountCent, order.currency)}"); Text("优惠：${r16Money(order.priceSnapshot.discountAmountCent, order.currency)}"); Text("服务费：${r16Money(order.priceSnapshot.serviceFeeCent, order.currency)}"); Text("应付：${r16Money(order.priceSnapshot.payableAmountCent, order.currency)}", fontWeight = FontWeight.Bold); Text("实付：${r16Money(order.paidAmountCent, order.currency)}", fontWeight = FontWeight.Bold) } }; item { R16Section("不退款证据") { Text(if (order.noRefundEvidence.confirmed) "用户已确认不可退款约定" else "历史订单未记录"); Text("协议版本：${order.noRefundEvidence.agreementVersion.ifBlank { "暂无" }}"); order.noRefundEvidence.confirmedAt?.let { Text("确认时间：$it") } } }; item { R16Section("时间信息") { Text("创建时间：${order.createdAt}"); order.paidAt?.let { Text("支付时间：$it") } } } } }
@Composable private fun R16Section(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } } }
@Composable private fun R16OrderSkeleton(pad: PaddingValues = PaddingValues()) { Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun R16Empty(message: String) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(message, color = HhyColors.TextSecondary) } }
@Composable private fun R16Failure(value: R07CallResult.Failure, onBack: () -> Unit, retry: () -> Unit, pad: PaddingValues = PaddingValues()) { Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(r16FailureText(value), style = MaterialTheme.typography.titleMedium); value.requestId?.let { Text("请求编号：$it", style = MaterialTheme.typography.bodySmall) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onBack) { Text("返回") }; Button(onClick = retry) { Text("重试") } } } } }
@Composable private fun R16InlineFailure(value: R07CallResult.Failure, retry: () -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(r16FailureText(value), color = HhyColors.Error); TextButton(onClick = retry) { Text("重试") } } }
private fun r16FailureText(value: R07CallResult.Failure) = when (value.statusCode) { 403 -> "暂无权限查看该内容"; 404 -> "订单不存在或已不可访问"; else -> if (value.statusCode == null) "网络不可用，请检查网络后重试" else "暂时无法加载，请稍后重试" }
