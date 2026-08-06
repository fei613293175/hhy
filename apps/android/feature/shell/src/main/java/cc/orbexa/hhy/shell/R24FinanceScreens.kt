package cc.orbexa.hhy.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ContractR24Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.RewardAccountResource

@Composable
fun R24RewardAccountScreen(api: ContractR12MeApi, accessToken: String, onBack: () -> Unit, onOpenLedger: () -> Unit, onRetry: () -> Unit = {}) {
    var state by remember { mutableStateOf<R07CallResult<RewardAccountResource>?>(null) }
    LaunchedEffect(api, accessToken) { state = api.rewardAccount(accessToken) }
    Scaffold(topBar = { TopAppBar(title = { Text("奖励账户") }, navigationIcon = { androidx.compose.material3.TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val result = state) {
                null -> CircularProgressIndicator(Modifier.testTag("scr-reward-001-loading"))
                is R07CallResult.Failure -> Card(colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("奖励账户暂时无法加载", fontWeight = FontWeight.SemiBold); Text("请检查网络或稍后重试", color = HhyColors.TextSecondary); OutlinedButton(onClick = onRetry) { Text("重新加载") } } }
                is R07CallResult.Success -> {
                    val account = result.data
                    Card(colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary), shape = RoundedCornerShape(24.dp)) { Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("可用奖励", color = HhyColors.TextInverse); Text(money(account.availableCent), color = HhyColors.TextInverse, fontWeight = FontWeight.Bold); Text("奖励账户 · 已同步", color = HhyColors.TextInverse.copy(alpha = .82f)) } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { balance("待结算", account.pendingCent); balance("可提现", account.availableCent); balance("冻结", account.frozenCent); balance("累计提现", account.withdrawnCent ?: 0) }
                    Card(colors = CardDefaults.cardColors(containerColor = HhyColors.Surface), modifier = Modifier.fillMaxWidth().testTag("scr-reward-001-content")) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("资产明细", fontWeight = FontWeight.SemiBold); Text("数据版本 ${account.version}", color = HhyColors.TextSecondary); Text("更新时间 ${account.updatedAt ?: "—"}", color = HhyColors.TextSecondary); OutlinedButton(onClick = onOpenLedger) { Text("查看奖励明细") } } }
                }
            }
        }
    }
}

@Composable private fun RowScope.balance(label: String, cent: Long) { Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, color = HhyColors.TextSecondary); Text(money(cent), fontWeight = FontWeight.SemiBold) } } }
private fun money(cent: Long) = "¥${"%.2f".format(cent / 100.0)}"

@Composable fun R24RewardLedgerScreen(api: ContractR24Api, accessToken: String, onBack: () -> Unit, onRetry: () -> Unit = {}) { var state by remember { mutableStateOf<R07CallResult<cc.orbexa.hhy.network.RewardLedgerPage>?>(null) }; LaunchedEffect(api, accessToken) { state = api.rewardLedger(accessToken) }; Scaffold(topBar = { TopAppBar(title = { Text("奖励明细") }, navigationIcon = { androidx.compose.material3.TextButton(onClick = onBack) { Text("返回") } }) }) { padding -> when (val result = state) { null -> CircularProgressIndicator(Modifier.padding(padding).padding(20.dp)); is R07CallResult.Failure -> Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("奖励流水暂时无法加载"); OutlinedButton(onClick = onRetry) { Text("重新加载") } }; is R07CallResult.Success -> LazyColumn(Modifier.fillMaxSize().background(HhyColors.PageBackground).padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(result.data.items) { item -> Card(colors = CardDefaults.cardColors(containerColor = HhyColors.Surface)) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(item.sourceType ?: "奖励变更", fontWeight = FontWeight.SemiBold); Text(item.status, color = HhyColors.TextSecondary) }; Text(money(item.amountCent ?: 0), fontWeight = FontWeight.Bold) } } } } } } }
