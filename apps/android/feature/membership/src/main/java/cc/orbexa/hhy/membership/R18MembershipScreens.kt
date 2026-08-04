@file:OptIn(ExperimentalMaterial3Api::class)

package cc.orbexa.hhy.membership

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.*
import cc.orbexa.hhy.network.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private fun money(value: Long?): String = if (value == null) "暂不可用" else "¥" + String.format(Locale.ROOT, "%.2f", value / 100.0)
private fun status(value: String): String = when (value) {
    "ACTIVE" -> "会员有效"
    "PENDING" -> "待生效"
    "EXPIRED" -> "已到期"
    "CANCELLED" -> "已取消"
    else -> "状态待确认"
}
internal fun isMissingMembership(value: R07CallResult<MembershipResource>?): Boolean =
    value is R07CallResult.Failure && value.statusCode == 404

private fun termLabel(sku: MembershipResource): String {
    val source = listOfNotNull(sku.name, sku.skuId).joinToString(" ").lowercase(Locale.ROOT)
    return when {
        source.contains("月") || source.contains("month") -> "月度"
        source.contains("季") || source.contains("quarter") -> "季度"
        source.contains("年") || source.contains("year") -> "年度"
        else -> "期限待确认"
    }
}

private fun dateLabel(value: String?): String = value?.let {
    runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")) }.getOrDefault(it)
} ?: "开通后展示有效期"
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
        val member = (current as? R07CallResult.Success)?.data
        val plans = (skus as? R07CallResult.Success)?.data?.items.orEmpty()
        val currentFailure = current as? R07CallResult.Failure
        val skuFailure = skus as? R07CallResult.Failure
        when {
            current == null || skus == null -> R18Loading(padding)
            currentFailure != null && !isMissingMembership(current) && skuFailure != null ->
                R18Failure(currentFailure, padding, ::load)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { R18MemberCard(member) }
                if (currentFailure != null && !isMissingMembership(current)) {
                    item { R18InlineNotice("会员状态暂时无法加载", currentFailure, ::load) }
                }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Pro 会员权益", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onBenefits) { Text("查看全部") }
                } }
                if (member?.benefits.isNullOrEmpty()) {
                    item { R18EmptyBenefits() }
                } else {
                    items(member?.benefits?.take(4).orEmpty(), key = { it.benefitCode }) { R18BenefitRow(it) }
                }
                item { Text("选择套餐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (skuFailure != null) {
                    item { R18InlineNotice("会员套餐暂时无法加载", skuFailure, ::load) }
                } else {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        plans.take(3).forEach { sku -> R18SkuCard(sku, Modifier.weight(1f)) { onPurchase(sku.skuId.orEmpty()) } }
                    } }
                }
                item { Button(
                    onClick = if (member?.status == "ACTIVE") onUpgrade else {
                        { plans.firstOrNull()?.skuId?.let { onPurchase(it) } ?: Unit }
                    },
                    enabled = plans.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("hhy.member.primary"),
                ) { Text(if (member?.status == "ACTIVE") "升级 Pro" else "开通 Pro") } }
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
            is R07CallResult.Failure -> if (value.statusCode == 404) {
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { R18MemberCard(null) }
                    item { R18EmptyBenefits() }
                }
            } else R18Failure(value, padding, ::load)
            is R07CallResult.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { R18MemberCard(value.data) }
                items(value.data.benefits, key = { it.benefitCode }) { R18BenefitRow(it) }
            }
        }
    }
}

@Composable private fun R18MemberCard(member: MembershipResource?) {
    val active = member?.status == "ACTIVE"
    Card(Modifier.fillMaxWidth().testTag("hhy.member.card"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(HhyRadius.LargeCard)) {
        Column(Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(HhyColors.BrandPrimaryDark, HhyColors.BrandPrimary, HhyColors.BrandGradientEnd)),
        ).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = Color.White.copy(alpha = .18f)) {
                    HhyIcon(HhyIcons.Verified, "Pro 会员", Modifier.padding(11.dp), HhyColors.TextInverse)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Pro 会员", color = HhyColors.TextInverse, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (active) "统一 Pro 身份" else "未开通 Pro", color = HhyColors.TextInverse.copy(alpha = .82f),
                        style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = Color.White.copy(alpha = .2f)) {
                    Text(if (active) "已开通" else "去开通", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = HhyColors.TextInverse, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                R18CardFact("会员状态", if (active) status(member?.status.orEmpty()) else "暂未开通", Modifier.weight(1f))
                R18CardFact("有效期", if (active) dateLabel(member?.expiresAt) else "开通后展示", Modifier.weight(1f))
            }
        }
    }
}
@Composable private fun R18SkuSummary(sku: MembershipResource?) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(sku?.name ?: "套餐加载中", fontWeight = FontWeight.SemiBold)
            Text("${sku?.let(::termLabel) ?: "期限待确认"} · ${money(sku?.paidValueCent)}", color = HhyColors.BrandPrimary)
        }
    }
}
@Composable private fun R18SkuCard(sku: MembershipResource, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 132.dp).testTag("hhy.member.sku." + sku.skuId),
        shape = RoundedCornerShape(HhyRadius.NormalCard), border = BorderStroke(1.dp, HhyColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(termLabel(sku), color = HhyColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(sku.name ?: "Pro 会员", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(sku.paidValueCent), color = HhyColors.BrandPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = HhyColors.SoftBlue) {
                Text(status(sku.status), modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = HhyColors.BrandPrimary)
            }
        }
    }
}
@Composable private fun R18BenefitRow(benefit: MembershipBenefitResource) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SurfaceVariant) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = HhyColors.SuccessSoft) {
                HhyIcon(HhyIcons.Check, "权益", Modifier.padding(6.dp), HhyColors.Success)
            }
            Text(benefit.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(benefit.value.toString().removeSurrounding("\"") + (benefit.unit?.let { " $it" } ?: ""),
                color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun R18EmptyBenefits() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.SurfaceVariant) {
        Text("开通后展示真实会员权益", Modifier.padding(14.dp), color = HhyColors.TextSecondary)
    }
}
@Composable private fun R18CardFact(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = HhyColors.TextInverse.copy(alpha = .68f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = HhyColors.TextInverse, style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun R18InlineNotice(title: String, value: R07CallResult.Failure, retry: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.ErrorSoft) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = HhyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(errorText(value), color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = retry) { Text("重试") }
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
