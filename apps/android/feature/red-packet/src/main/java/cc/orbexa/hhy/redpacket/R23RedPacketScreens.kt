@file:OptIn(ExperimentalMaterial3Api::class)

package cc.orbexa.hhy.redpacket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ContractR20RedPacketApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R20RedPacketCampaignResource
import cc.orbexa.hhy.network.RewardAccountResource
import kotlinx.coroutines.launch

/** R23 SCR-RP-004. Data is limited to the frozen claims and reward-account contracts. */
@Composable
fun R23MyRedPacketsScreen(
    redPacketApi: ContractR20RedPacketApi,
    meApi: ContractR12MeApi,
    token: String,
    onBack: () -> Unit,
    onExpired: () -> Unit,
) {
    var account by remember { mutableStateOf<RewardAccountResource?>(null) }
    var claims by remember { mutableStateOf<List<R20RedPacketCampaignResource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true
        scope.launch {
            val accountResult = meApi.rewardAccount(token)
            val claimsResult = redPacketApi.claims(token, page = 1, pageSize = 20)
            failure = listOfNotNull(
                accountResult as? R07CallResult.Failure,
                claimsResult as? R07CallResult.Failure,
            ).firstOrNull()
            (accountResult as? R07CallResult.Success)?.let { account = it.data }
            (claimsResult as? R07CallResult.Success)?.let { claims = it.data.items }
            if (failure?.statusCode == 401) onExpired()
            loading = false
        }
    }

    LaunchedEffect(token) { load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的红包") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { TextButton(onClick = ::load, enabled = !loading) { Text("刷新") } },
            )
        },
        modifier = Modifier.testTag("hhy.screen.scr-rp-004"),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HhyColors.BrandPrimary,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        Text("红包奖励账户", color = HhyColors.TextInverse)
                        Text(
                            account?.availableCent?.let(::r23Money) ?: "--",
                            style = MaterialTheme.typography.headlineLarge,
                            color = HhyColors.TextInverse,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            R23BalanceMetric("待结算", account?.pendingCent)
                            R23BalanceMetric("冻结", account?.frozenCent)
                            R23BalanceMetric("已提现", account?.withdrawnCent)
                        }
                    }
                }
            }
            item {
                R23Panel("红包记录") {
                    when {
                        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        failure != null && claims.isEmpty() -> {
                            Text(r23Failure(failure!!), color = HhyColors.Error)
                            OutlinedButton(onClick = ::load) { Text("重新加载") }
                        }
                        claims.isEmpty() -> Text("暂无红包记录，完成有效浏览并领取后会显示在这里。", color = HhyColors.TextSecondary)
                        else -> Text("按服务端状态展示待结算、可提现与历史记录。", color = HhyColors.TextSecondary)
                    }
                }
            }
            if (claims.isNotEmpty()) items(claims, key = { it.id }) { claim ->
                R23ClaimRow(claim)
            }
            if (failure != null && claims.isNotEmpty()) item {
                Text(r23Failure(failure!!), color = HhyColors.Warning, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun R23BalanceMetric(label: String, amount: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = HhyColors.TextInverse.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
        Text(amount?.let(::r23Money) ?: "--", color = HhyColors.TextInverse, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun R23ClaimRow(item: R20RedPacketCampaignResource) {
    R23Panel("红包 ${item.id}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text("内容 ${item.contentId}", fontWeight = FontWeight.SemiBold)
                Text("${item.status} · 剩余 ${item.remainingCount}/${item.totalCount}", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(r23Money(item.amountPerClaimCent), color = HhyColors.RewardRed, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        Text("服务端版本 v${item.version}", color = HhyColors.TextTertiary, style = MaterialTheme.typography.bodySmall)
    }
}

private fun r23Money(value: Long) = "¥%.2f".format(value / 100.0)

@Composable
private fun R23Panel(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        border = androidx.compose.foundation.BorderStroke(HhySize.Hairline, HhyColors.Border),
    ) {
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

private fun r23Failure(failure: R07CallResult.Failure): String = when (failure.statusCode) {
    null -> "当前离线，恢复网络后可刷新奖励账户和红包记录。"
    401 -> "登录状态已失效，请重新登录。"
    403 -> "当前账号无权查看红包记录。"
    404 -> "红包记录已不可用。"
    else -> "数据暂时无法刷新，请稍后重试。"
}
