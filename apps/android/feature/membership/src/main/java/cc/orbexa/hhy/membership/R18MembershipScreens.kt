package cc.orbexa.hhy.membership

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import cc.orbexa.hhy.network.*
import java.util.UUID
import kotlinx.coroutines.launch

private fun money(value: Long?): String = if (value == null) "暂不可用" else "¥" + "%.2f".format(value / 100.0)
private fun status(value: String): String = when (value) {
    "ACTIVE" -> "会员有效"
    "PENDING" -> "待生效"
    "EXPIRED" -> "已到期"
    "CANCELLED" -> "已取消"
    else -> "状态待确认"
}
private fun errorText(value: R07CallResult.Failure) =
    if (value.statusCode == null) "网络不可用，请稍后重试" else "暂时无法加载，请稍后重试"

@Composable
fun R18MembershipCenterScreen(
    api: ContractR18MembershipApi, token: String, onBack: () -> Unit,
    onPurchase: (String) -> Unit, onUpgrade: () -> Unit, onBenefits: () -> Unit, onExpired: () -> Unit,
) {
    var current by remember { mutableStateOf<R07CallResult<MembershipResource>?>(null) }
    var skus by remember { mutableStateOf<R07CallResult<R18MembershipPage>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch {
        current = api.current(token).also { if (it is R07CallResult.Failure && it.statusCode == 401) onExpired() }
        skus = api.skus(token).also { if (it is R07CallResult.Failure && it.statusCode == 401) onExpired() }
    } }
    LaunchedEffect(token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("会员中心") }, navigationIcon = { HhyBackButton(onBack) }) },
        modifier = Modifier.testTag("hhy.screen.scr-member-001")) { padding ->
        when {
            current == null || skus == null -> R18Loading(padding)
            current is R07CallResult.Failure && skus is R07CallResult.Failure ->
                R18Failure(current as R07CallResult.Failure, padding, ::load)
            else -> {
                val member = (current as? R07CallResult.Success)?.data
                val plans = (skus as? R07CallResult.Success)?.data?.items.orEmpty()
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { R18MemberCard(member) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("权益摘要", fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = onBenefits) { Text("查看全部") }
                    } }
                    items(member?.benefits?.take(4).orEmpty(), key = { it.benefitCode }) { R18BenefitRow(it) }
                    item { Text("选择套餐", fontWeight = FontWeight.SemiBold) }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plans.take(3).forEach { sku -> R18SkuCard(sku, Modifier.weight(1f)) { onPurchase(sku.skuId.orEmpty()) } }
                    } }
                    item { Button(
                        onClick = if (member?.status == "ACTIVE") onUpgrade else { { plans.firstOrNull()?.skuId?.let(onPurchase) } },
                        enabled = plans.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("hhy.member.primary"),
                    ) { Text(if (member?.status == "ACTIVE") "升级 Pro" else "开通 Pro") } }
                }
            }
        }
    }
}

@Composable
fun R18MembershipPurchaseScreen(
    api: ContractR18MembershipApi, token: String, skuId: String,
    onBack: () -> Unit, onDone: () -> Unit, onExpired: () -> Unit,
) {
    var page by remember { mutableStateOf<R07CallResult<R18MembershipPage>?>(null) }
    var channel by remember { mutableStateOf("ALIPAY") }
    var agreement by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<R07CallResult<CommandResultResource>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { page = api.skus(token).also {
        if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
    } } }
    LaunchedEffect(token, skuId) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("确认开通 Pro") }, navigationIcon = { HhyBackButton(onBack) }) },
        modifier = Modifier.testTag("hhy.screen.scr-member-002")) { padding ->
        val sku = (page as? R07CallResult.Success)?.data?.items?.firstOrNull { it.skuId == skuId }
        if (page == null) R18Loading(padding)
        else if (page is R07CallResult.Failure) R18Failure(page as R07CallResult.Failure, padding, ::load)
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { R18SkuSummary(sku) }
            item { Text("权益清单", fontWeight = FontWeight.SemiBold) }
            items(sku?.benefits.orEmpty(), key = { it.benefitCode }) { R18BenefitRow(it) }
            item { Text("应付金额：" + money(sku?.paidValueCent), fontWeight = FontWeight.Bold) }
            item { Text("支付方式", fontWeight = FontWeight.SemiBold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALIPAY" to "支付宝", "WECHAT_PAY" to "微信支付").forEach { (value, label) ->
                    FilterChip(selected = channel == value, onClick = { channel = value }, label = { Text(label) })
                }
            } }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreement, onCheckedChange = { agreement = it })
                Text("我已阅读并同意会员服务协议")
            } }
            item { Button(onClick = { scope.launch {
                result = api.purchase(token, R18MembershipOrderRequest(skuId, channel), UUID.randomUUID().toString())
                if (result is R07CallResult.Failure && (result as R07CallResult.Failure).statusCode == 401) onExpired()
                if (result is R07CallResult.Success) onDone()
            } }, enabled = sku != null && agreement && result !is R07CallResult.Success,
                modifier = Modifier.fillMaxWidth().testTag("hhy.member.purchase")) {
                Text("提交并支付 " + money(sku?.paidValueCent))
            } }
            if (result is R07CallResult.Failure) item { Text(errorText(result as R07CallResult.Failure), color = HhyColors.Error) }
        }
    }
}

@Composable
fun R18MembershipUpgradeScreen(api: ContractR18MembershipApi, token: String, onBack: () -> Unit, onExpired: () -> Unit) {
    var current by remember { mutableStateOf<R07CallResult<MembershipResource>?>(null) }
    var page by remember { mutableStateOf<R07CallResult<R18MembershipPage>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var quote by remember { mutableStateOf<R07CallResult<MembershipResource>?>(null) }
    var agreement by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch {
        current = api.current(token); page = api.skus(token)
        if (current is R07CallResult.Failure && (current as R07CallResult.Failure).statusCode == 401) onExpired()
    } }
    LaunchedEffect(token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("升级 Pro") }, navigationIcon = { HhyBackButton(onBack) }) },
        modifier = Modifier.testTag("hhy.screen.scr-member-003")) { padding ->
        val member = (current as? R07CallResult.Success)?.data
        val plans = (page as? R07CallResult.Success)?.data?.items.orEmpty()
        if (current == null || page == null) R18Loading(padding)
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { R18MemberCard(member) }
            item { Text("目标套餐", fontWeight = FontWeight.SemiBold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.take(3).forEach { sku -> R18SkuCard(sku, Modifier.weight(1f)) { selected = sku.skuId } }
            } }
            item { Text("服务端报价", fontWeight = FontWeight.SemiBold) }
            item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前剩余价值：" + money(member?.remainingValueCent))
                    Text("应付补差：" + money((quote as? R07CallResult.Success)?.data?.paidValueCent), fontWeight = FontWeight.Bold)
                    Text("报价只以服务端结果为准", color = HhyColors.TextSecondary)
                }
            } }
            item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected?.let { id -> scope.launch {
                    quote = api.quote(token, R18MembershipUpgradeQuoteRequest(id), UUID.randomUUID().toString())
                } } }, enabled = selected != null) { Text("获取报价") }
                Checkbox(checked = agreement, onCheckedChange = { agreement = it })
                Text("同意协议")
            } }
            item { Button(onClick = { val id = (quote as? R07CallResult.Success)?.data?.id
                if (id != null) scope.launch { api.upgrade(token, R18MembershipUpgradeOrderRequest(id, "ALIPAY"), UUID.randomUUID().toString()) }
            }, enabled = agreement && quote is R07CallResult.Success, modifier = Modifier.fillMaxWidth()) { Text("提交升级") } }
        }
    }
}

@Composable
fun R18MembershipBenefitsScreen(api: ContractR18MembershipApi, token: String, onBack: () -> Unit, onExpired: () -> Unit) {
    var state by remember { mutableStateOf<R07CallResult<MembershipResource>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch { state = api.current(token).also {
        if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
    } } }
    LaunchedEffect(token) { load() }
    Scaffold(topBar = { TopAppBar(title = { Text("Pro 权益") }, navigationIcon = { HhyBackButton(onBack) }) },
        modifier = Modifier.testTag("hhy.screen.scr-member-004")) { padding ->
        when (val value = state) {
            null -> R18Loading(padding)
            is R07CallResult.Failure -> R18Failure(value, padding, ::load)
            is R07CallResult.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { R18MemberCard(value.data) }
                items(value.data.benefits, key = { it.benefitCode }) { R18BenefitRow(it) }
            }
        }
    }
}

@Composable private fun R18MemberCard(member: MembershipResource?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary),
        shape = RoundedCornerShape(HhyRadius.LargeCard)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pro", color = HhyColors.TextInverse, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (member == null) "暂未开通" else status(member.status), color = HhyColors.TextInverse)
            Text(if (member?.expiresAt == null) "开通后展示有效期" else "有效期至 " + member.expiresAt, color = HhyColors.TextInverse.copy(alpha = .86f))
        }
    }
}
@Composable private fun R18SkuSummary(sku: MembershipResource?) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sku?.name ?: "套餐加载中", fontWeight = FontWeight.SemiBold)
            Text("Pro · " + money(sku?.paidValueCent), color = HhyColors.BrandPrimary)
        }
    }
}
@Composable private fun R18SkuCard(sku: MembershipResource, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.testTag("hhy.member.sku." + sku.skuId), onClick = onClick, shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sku.name ?: "Pro", fontWeight = FontWeight.SemiBold)
            Text(money(sku.paidValueCent), color = HhyColors.BrandPrimary)
            Text(status(sku.status), style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
        }
    }
}
@Composable private fun R18BenefitRow(benefit: MembershipBenefitResource) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhyIcon(HhyIcons.Check, "权益", tint = HhyColors.BrandSecondary)
            Column(Modifier.weight(1f)) {
                Text(benefit.name, fontWeight = FontWeight.SemiBold)
                Text(benefit.benefitCode, style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
            }
            Text(benefit.value.toString().removeSurrounding("\"") + (benefit.unit?.let { " " + it } ?: ""))
        }
    }
}
@Composable private fun R18Loading(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
}
@Composable private fun R18Failure(value: R07CallResult.Failure, padding: PaddingValues, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(errorText(value)); Button(onClick = retry) { Text("重试") } }
}
