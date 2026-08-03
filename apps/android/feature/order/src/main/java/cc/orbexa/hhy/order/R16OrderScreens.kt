package cc.orbexa.hhy.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import cc.orbexa.hhy.designsystem.*
import cc.orbexa.hhy.network.ContractR16Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R16OrderResource
import kotlinx.coroutines.launch

private fun money(value: Long?, currency: String = "CNY") = value?.let { if (currency == "CNY") "¥%.2f".format(it / 100.0) else "${currency} %.2f".format(it / 100.0) } ?: "暂不可用"
private fun status(value: String) = mapOf("PENDING_PAYMENT" to "待支付", "PAYMENT_PROCESSING" to "支付处理中", "PAID" to "已支付", "FULFILLING" to "履约中", "COMPLETED" to "已完成", "PAYMENT_FAILED" to "支付失败", "CLOSED" to "已关闭", "CHANNEL_REVERSAL" to "渠道冲正")[value] ?: "订单状态暂不可用"
private fun failureText(result: R07CallResult.Failure) = when (result.statusCode) { 403 -> "暂无权限查看该内容"; 404 -> "订单不存在或已不可访问"; else -> if (result.statusCode == null) "网络不可用，请检查网络后重试" else "暂时无法加载，请稍后重试" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun R16OrderListScreen(api: ContractR16Api, token: String, onBack: () -> Unit, onOpen: (String) -> Unit, onExpired: () -> Unit) {
    val scope = rememberCoroutineScope(); var state by remember { mutableStateOf<R07CallResult<R16OrderPageState>?>(null) }
    fun load() { scope.launch { state = null; state = when (val r = api.orders(token)) { is R07CallResult.Success -> R07CallResult.Success(R16OrderPageState(r.data.items), r.requestId, r.timestamp); is R07CallResult.Failure -> { if (r.statusCode == 401) onExpired(); r } } } }
    LaunchedEffect(token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("我的订单") }, navigationIcon = { HhyBackButton(onBack) }, actions = { IconButton(onClick = ::load) { HhyIcon(HhyIcons.Refresh, "刷新") } }) }, modifier = Modifier.testTag("hhy.screen.scr-order-001")) { pad ->
        when (val value = state) {
            null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            is R07CallResult.Failure -> R16Failure(value, pad, onBack, ::load)
            is R07CallResult.Success -> if (value.data.items.isEmpty()) R16Empty(pad, "暂无订单") else LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(value.data.items, key = { it.orderNo }) { order -> R16OrderCard(order) { onOpen(order.orderNo) } } }
        }
    }
}

private data class R16OrderPageState(val items: List<R16OrderResource>)
@Composable private fun R16OrderCard(order: R16OrderResource, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(HhyRadius.NormalCard)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(order.orderNo, style = MaterialTheme.typography.labelMedium); Text(status(order.status), color = HhyColors.BrandPrimary, fontWeight = FontWeight.SemiBold) }; Text(order.items.take(2).joinToString("、") { "${it.itemName} × ${it.quantity}" }.ifBlank { "订单明细暂不可用" }); Text("订单类型：${order.orderType}", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("应付 ${money(order.priceSnapshot.payableAmountCent, order.currency)}", fontWeight = FontWeight.Bold); Text("实付 ${money(order.paidAmountCent, order.currency)}", style = MaterialTheme.typography.bodySmall) }; Text(if (order.noRefundEvidence.confirmed) "已确认不可退款约定" else "不退款确认：未记录", style = MaterialTheme.typography.bodySmall); Text(order.createdAt, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun R16OrderDetailScreen(api: ContractR16Api, token: String, orderNo: String, onBack: () -> Unit, onExpired: () -> Unit) {
    val scope = rememberCoroutineScope(); var state by remember { mutableStateOf<R07CallResult<R16OrderResource>?>(null) }
    fun load() { scope.launch { state = api.order(token, orderNo).also { if (it is R07CallResult.Failure && it.statusCode == 401) onExpired() } } }
    LaunchedEffect(orderNo, token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("订单详情") }, navigationIcon = { HhyBackButton(onBack) }, actions = { IconButton(onClick = ::load) { HhyIcon(HhyIcons.Refresh, "刷新") } }) }, modifier = Modifier.testTag("hhy.screen.scr-order-002")) { pad ->
        when (val value = state) { null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }; is R07CallResult.Failure -> R16Failure(value, pad, onBack, ::load); is R07CallResult.Success -> R16Detail(value.data, pad) }
    }
}
@Composable private fun R16Detail(order: R16OrderResource, pad: PaddingValues) { LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { R16Section("状态摘要") { Text(order.orderNo); Text("${status(order.status)} · ${order.orderType}"); Text("创建时间：${order.createdAt}") } }; item { R16Section("订单项") { if (order.items.isEmpty()) Text("订单明细暂不可用") else order.items.forEach { Text("${it.itemName} × ${it.quantity}    ${money(it.subtotalAmountCent, order.currency)}") } } }; item { R16Section("价格明细") { Text("原价：${money(order.priceSnapshot.originalAmountCent, order.currency)}"); Text("优惠：${money(order.priceSnapshot.discountAmountCent, order.currency)}"); Text("服务费：${money(order.priceSnapshot.serviceFeeCent, order.currency)}"); Text("应付：${money(order.priceSnapshot.payableAmountCent, order.currency)}", fontWeight = FontWeight.Bold); Text("实付：${money(order.paidAmountCent, order.currency)}", fontWeight = FontWeight.Bold) } }; item { R16Section("不退款证据") { Text(if (order.noRefundEvidence.confirmed) "用户已确认不可退款约定" else "历史订单未记录"); Text("协议版本：${order.noRefundEvidence.agreementVersion.ifBlank { "暂无" }}"); order.noRefundEvidence.confirmedAt?.let { Text("确认时间：$it") } } }; item { R16Section("时间信息") { Text("创建时间：${order.createdAt}"); order.paidAt?.let { Text("支付时间：$it") } } } } }
@Composable private fun R16Section(title: String, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } } }
@Composable private fun R16Empty(pad: PaddingValues, message: String) { Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { Text(message, color = HhyColors.TextSecondary) } }
@Composable private fun R16Failure(value: R07CallResult.Failure, pad: PaddingValues, onBack: () -> Unit, retry: () -> Unit) { Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(failureText(value), style = MaterialTheme.typography.titleMedium); value.requestId?.let { Text("请求编号：$it", style = MaterialTheme.typography.bodySmall) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onBack) { Text("返回") }; Button(onClick = retry) { Text("重试") } } } } }
