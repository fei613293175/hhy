@file:OptIn(ExperimentalMaterial3Api::class)

package cc.orbexa.hhy.redpacket

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ContractR20RedPacketApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R20RedPacketCampaignResource
import cc.orbexa.hhy.network.R22CancelRequest
import cc.orbexa.hhy.network.R22ClaimRequest
import cc.orbexa.hhy.network.R22HeartbeatRequest
import cc.orbexa.hhy.network.R22ViewSessionRequest
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun R22RedPacketHomeScreen(
    api: ContractR20RedPacketApi,
    token: String,
    onBack: () -> Unit,
    onOpenCampaign: (String) -> Unit,
    onOpenMyRedPackets: () -> Unit,
    onExpired: () -> Unit,
) {
    var items by remember { mutableStateOf<List<R20RedPacketCampaignResource>>(emptyList()) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun load() {
        loading = true
        scope.launch {
            when (val result = api.publicCampaigns(token, pageSize = 20, status = "ACTIVE")) {
                is R07CallResult.Success -> { items = result.data.items; failure = null }
                is R07CallResult.Failure -> { failure = result; if (result.statusCode == 401) onExpired() }
            }
            loading = false
        }
    }
    LaunchedEffect(token) { load() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("红包") }, navigationIcon = { HhyBackButton(onBack) }, actions = { TextButton(onClick = onOpenMyRedPackets) { Text("我的红包") }; TextButton(onClick = ::load) { Text("刷新") } }) },
        modifier = Modifier.testTag("hhy.screen.scr-rp-001"),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                R22Panel("红包聚合") {
                    Text("完成 20 秒有效浏览后领取红包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("活动状态、金额与剩余名额均以服务端返回为准。", color = HhyColors.TextSecondary)
                    if (items.isNotEmpty()) {
                        val featured = items.first()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = HhyColors.WarningSoft,
                            shape = RoundedCornerShape(HhyRadius.Tag),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(HhySpacing.Md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                    Text("当前红包", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(r22Money(featured.amountPerClaimCent), color = HhyColors.RewardRed, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                    Text("剩余名额", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text("${featured.remainingCount} / ${featured.totalCount}", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            item {
                R22Panel("进行中的红包") {
                    when {
                        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        failure != null -> { Text("暂时无法加载红包活动", color = HhyColors.Error); OutlinedButton(onClick = ::load) { Text("重试") } }
                        items.isEmpty() -> Text("当前没有可领取的红包", color = HhyColors.TextSecondary)
                        else -> items.forEach { item ->
                            R22CampaignRow(item, onClick = { onOpenCampaign(item.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(HhySpacing.Xl)) }
        }
    }
}

@Composable
fun R22RedPacketEligibilityScreen(
    api: ContractR20RedPacketApi,
    token: String,
    campaignId: String,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var item by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun load() {
        loading = true
        scope.launch {
            when (val result = api.campaign(token, campaignId)) {
                is R07CallResult.Success -> { item = result.data; failure = null }
                is R07CallResult.Failure -> { failure = result; if (result.statusCode == 401) onExpired() }
            }
            loading = false
        }
    }
    LaunchedEffect(campaignId, token) { load() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("红包任务资格") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = { Surface(shadowElevation = HhyElevation.Dialog) {
            Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("返回") }
                Button(onClick = { onStart(item!!.id) }, enabled = item?.status == "ACTIVE" && !loading, modifier = Modifier.weight(1f)) { Text("开始浏览") }
            }
        } },
        modifier = Modifier.testTag("hhy.screen.scr-rp-002"),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item {
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 3), Alignment.Center) { CircularProgressIndicator() }
                    failure != null -> R22Panel("无法确认资格") { Text("请检查网络后重试", color = HhyColors.Error); OutlinedButton(onClick = ::load) { Text("重试") } }
                    item != null -> {
                        R22Panel("活动信息") {
                            val campaign = item!!
                            Text("活动 ${campaign.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = HhyColors.WarningSoft,
                                shape = RoundedCornerShape(HhyRadius.Tag),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(HhySpacing.Md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                        Text("单个红包", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        Text(r22Money(campaign.amountPerClaimCent), color = HhyColors.RewardRed, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                        Text("剩余名额", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                        Text("${campaign.remainingCount} / ${campaign.totalCount}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            R22MetricRow("浏览要求", "20 秒有效浏览")
                            R22MetricRow("资格校验", "实名认证、库存与重复领取")
                        }
                    }
                }
            }
            item { R22Panel("资格状态") { Text(if (item?.status == "ACTIVE") "可以开始任务" else "当前活动不可领取", fontWeight = FontWeight.SemiBold); Text("实名、库存和重复领取校验由服务端完成。", color = HhyColors.TextSecondary) } }
        }
    }
}

@Composable
fun R22RedPacketTaskScreen(
    api: ContractR20RedPacketApi,
    meApi: ContractR12MeApi,
    token: String,
    campaignId: String,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onExpired: () -> Unit,
) {
    var item by remember { mutableStateOf<R20RedPacketCampaignResource?>(null) }
    var session by remember { mutableStateOf<CommandResultResource?>(null) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var showClaim by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val clientNonce = remember { UUID.randomUUID().toString() }
    fun load() {
        loading = true
        scope.launch {
            when (val result = api.campaign(token, campaignId)) {
                is R07CallResult.Success -> { item = result.data; failure = null }
                is R07CallResult.Failure -> { failure = result; if (result.statusCode == 401) onExpired() }
            }
            loading = false
        }
    }
    fun start() {
        loading = true
        scope.launch {
            when (val result = api.startViewSession(
                token,
                campaignId,
                R22ViewSessionRequest(clientNonce, "android"),
                r22StartKey(campaignId, clientNonce),
            )) {
                is R07CallResult.Success -> { session = result.data; failure = null; showClaim = false }
                is R07CallResult.Failure -> { failure = result; if (result.statusCode == 401) onExpired() }
            }
            loading = false
        }
    }
    LaunchedEffect(campaignId, token) { load() }
    LaunchedEffect(session?.resourceId) {
        while (session?.resourceId != null && session?.status == "VIEWING") {
            delay(1000)
            val id = session?.resourceId ?: break
            val next = (session?.lastHeartbeatSequence ?: -1L) + 1L
            when (val result = api.heartbeat(
                token,
                id,
                R22HeartbeatRequest(next, 1, true),
                r22HeartbeatKey(id, next),
            )) {
                is R07CallResult.Success -> { session = result.data; failure = null }
                is R07CallResult.Failure -> {
                    failure = result
                    if (result.statusCode == 401) onExpired()
                    if (result.errorCode == "REDPACKET-422-VIEW_INVALID") {
                        session = session?.copy(status = "EXPIRED")
                    }
                    break
                }
            }
        }
        if (session?.status == "VIEW_COMPLETE") showClaim = true
    }
    if (showClaim && session?.resourceId != null) {
        R22RedPacketClaimSheet(
            api, meApi, token, session!!.resourceId!!, clientNonce,
            session?.lastHeartbeatSequence ?: -1L,
            onDismiss = { showClaim = false }, onCompleted = onCompleted, onExpired = onExpired,
        )
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("红包浏览任务") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = { Surface(shadowElevation = HhyElevation.Dialog) {
            Row(Modifier.fillMaxWidth().padding(HhySpacing.Md), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                OutlinedButton(onClick = {
                    session?.resourceId?.let { id ->
                        scope.launch {
                            when (val result = api.cancel(
                                token, id, R22CancelRequest("用户主动取消"), r22CancelKey(id),
                            )) {
                                is R07CallResult.Success -> { session = result.data; showClaim = false; failure = null }
                                is R07CallResult.Failure -> { failure = result; if (result.statusCode == 401) onExpired() }
                            }
                        }
                    }
                }, enabled = session?.status == "VIEWING" || session?.status == "VIEW_COMPLETE", modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = ::start,
                    enabled = item?.status == "ACTIVE"
                        && (session == null || session?.status in setOf("CANCELLED", "EXPIRED"))
                        && !loading,
                    modifier = Modifier.weight(1f),
                ) { Text(if (loading) "检查中" else "开始任务") }
            }
        } },
        modifier = Modifier.testTag("hhy.screen.scr-rp-003"),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding), contentPadding = PaddingValues(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item {
                R22Panel("任务进度") {
                    val required = session?.requiredSeconds ?: 20L
                    val accumulated = session?.accumulatedSeconds ?: 0L
                    Text("活动 ${item?.id ?: campaignId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (session?.status == "VIEWING") HhyColors.SoftBlue else HhyColors.SurfaceVariant,
                        shape = RoundedCornerShape(HhyRadius.Tag),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(HhySpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                Text("服务器状态", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text(session?.status ?: "READY", color = HhyColors.BrandPrimary, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                                Text("服务端有效浏览", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("${accumulated.coerceIn(0L, required)} / $required 秒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    LinearProgressIndicator(progress = { (accumulated / required.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    R22MetricRow("最近心跳序号", "${session?.lastHeartbeatSequence ?: "尚未开始"}")
                    R22MetricRow("页面可见性", "保持页面可见")
                    Text("完成状态由服务端心跳确认，本地时间不会决定奖励。", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (failure != null) item {
                R22Panel("任务状态") {
                    Text(r22FailureMessage(failure!!), color = HhyColors.Error)
                    OutlinedButton(onClick = ::load) { Text("重试") }
                }
            }
        }
    }
}

@Composable
fun R22RedPacketClaimSheet(
    api: ContractR20RedPacketApi,
    meApi: ContractR12MeApi,
    token: String,
    sessionId: String,
    clientNonce: String,
    finalHeartbeatSequence: Long,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
    onExpired: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var accountText by remember { mutableStateOf("正在读取奖励账户…") }
    var result by remember { mutableStateOf<R07CallResult<CommandResultResource>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionId) {
        when (val account = meApi.rewardAccount(token)) {
            is R07CallResult.Success -> accountText = "可用 ${r22Money(account.data.availableCent)} · 待入账 ${r22Money(account.data.pendingCent)}"
            is R07CallResult.Failure -> { accountText = "奖励账户暂时不可用"; if (account.statusCode == 401) onExpired() }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = HhySpacing.Lg).padding(bottom = HhySpacing.Xl), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            Box(Modifier.fillMaxWidth().padding(top = HhySpacing.Sm), Alignment.Center) { Surface(Modifier.fillMaxWidth(0.16f).height(HhySpacing.Xs), color = HhyColors.Border, shape = RoundedCornerShape(HhyRadius.Tag)) {} }
            Text("领取红包", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("领取后将按服务端结果进入奖励账户，重复提交不会生成新的领取。", color = HhyColors.TextSecondary)
            R22Panel("奖励账户") { Text(accountText); Text("领取状态由服务端确认，重复提交不会重复入账。", color = HhyColors.TextSecondary) }
            if (result is R07CallResult.Success) {
                val success = result as R07CallResult.Success<CommandResultResource>
                Text("领取${if (success.data.status == "PENDING") "已受理" else "成功"}：${success.data.amountPerClaimCent?.let(::r22Money) ?: "金额待确认"}", color = HhyColors.BrandPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (result is R07CallResult.Failure) Text(r22FailureMessage(result as R07CallResult.Failure), color = HhyColors.Error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        result = api.claim(
                            token,
                            sessionId,
                            R22ClaimRequest(clientNonce, finalHeartbeatSequence),
                            r22ClaimKey(sessionId, finalHeartbeatSequence, clientNonce),
                        )
                        busy = false
                        when (val response = result) {
                            is R07CallResult.Success -> onCompleted()
                            is R07CallResult.Failure -> if (response.statusCode == 401) onExpired()
                            null -> Unit
                        }
                    }
                }, enabled = !busy && result !is R07CallResult.Success, modifier = Modifier.weight(1f)) { Text(if (busy) "提交中" else "确认领取") }
            }
        }
    }
}

@Composable
private fun R22Panel(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.NormalCard), border = BorderStroke(HhySize.Hairline, HhyColors.Border), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) {
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun R22CampaignRow(item: R20RedPacketCampaignResource, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text("活动 ${item.id}", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Text(r22Money(item.amountPerClaimCent), color = HhyColors.RewardRed, fontWeight = FontWeight.Bold)
                Text("剩余 ${item.remainingCount} / ${item.totalCount}", color = HhyColors.TextSecondary)
            }
            Text("有效浏览 20 秒 · ${item.status}", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onClick) { Text("查看") }
    }
}

@Composable
private fun R22MetricRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HhyColors.TextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun r22StartKey(campaignId: String, clientNonce: String) = "r22-start-$campaignId-$clientNonce"
private fun r22HeartbeatKey(sessionId: String, sequence: Long) = "r22-heartbeat-$sessionId-$sequence"
private fun r22ClaimKey(sessionId: String, sequence: Long, clientNonce: String) = "r22-claim-$sessionId-$sequence-$clientNonce"
private fun r22CancelKey(sessionId: String) = "r22-cancel-$sessionId"

private fun r22FailureMessage(failure: R07CallResult.Failure): String = when (failure.errorCode) {
    "IDENTITY-422-NOT_VERIFIED" -> "完成实名认证后才可以领取红包"
    "REDPACKET-409-STOCK_EXHAUSTED" -> "红包名额已领完，请返回列表查看其他活动"
    "REDPACKET-409-ALREADY_CLAIMED" -> "你已领取过该红包"
    "REDPACKET-422-VIEW_INVALID" -> "浏览任务已失效，请重新开始"
    "COMMON-409-IDEMPOTENCY_CONFLICT" -> "请求状态发生冲突，请刷新后重试"
    "COMMON-404-NOT_FOUND" -> "红包活动已不存在或不可用"
    else -> if (failure.statusCode == null) "当前处于离线状态，服务端进度尚未更新"
        else "服务暂时不可用，请稍后重试"
}

private fun r22Money(value: Long) = "¥%.2f".format(value / 100.0)
