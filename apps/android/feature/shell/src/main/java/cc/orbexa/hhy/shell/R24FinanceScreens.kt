package cc.orbexa.hhy.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.ContractR12MeApi
import cc.orbexa.hhy.network.ContractR24Api
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R24CreateWithdrawalRequest
import cc.orbexa.hhy.network.R24PayoutAccountResource
import cc.orbexa.hhy.network.R24UpdatePayoutAccountRequest
import cc.orbexa.hhy.network.R24WithdrawalQuoteRequest
import cc.orbexa.hhy.network.R24WithdrawalQuoteResource
import cc.orbexa.hhy.network.R24WithdrawalResource
import cc.orbexa.hhy.network.RewardAccountResource
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun R24RewardAccountScreen(
    api: ContractR12MeApi,
    accessToken: String,
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenPayoutAccount: () -> Unit,
    onOpenWithdrawal: () -> Unit,
    onOpenWithdrawals: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var state by remember { mutableStateOf<R07CallResult<RewardAccountResource>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, accessToken, refreshKey) {
        state = null
        state = api.rewardAccount(accessToken).also {
            if (it is R07CallResult.Failure && it.statusCode == 401) onSessionExpired()
        }
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r24.reward-account"),
        topBar = { R24TopBar("奖励账户", onBack) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (val result = state) {
            null -> R24Loading(padding, "scr-reward-001-loading")
            is R07CallResult.Failure -> R24LoadFailure(
                padding = padding,
                title = "奖励账户暂时无法加载",
                failure = result,
                onRetry = { refreshKey += 1 },
            )
            is R07CallResult.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(HhySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HhyColors.BrandPrimary),
                        shape = RoundedCornerShape(HhyRadius.LargeCard),
                        modifier = Modifier.fillMaxWidth().testTag("scr-reward-001-content"),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
                            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                        ) {
                            Text("可用奖励", color = HhyColors.TextInverse)
                            Text(
                                r24Money(result.data.availableCent),
                                color = HhyColors.TextInverse,
                                fontSize = HhyType.PageTitleSize,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("奖励账户已同步", color = HhyColors.TextInverse.copy(alpha = .82f))
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        RewardBalance("待结算", result.data.pendingCent)
                        RewardBalance("可提现", result.data.availableCent)
                    }
                    Spacer(Modifier.height(HhySpacing.Sm))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        RewardBalance("冻结", result.data.frozenCent)
                        RewardBalance("累计提现", result.data.withdrawnCent ?: 0)
                    }
                }
                item {
                    R24SectionCard("奖励与提现") {
                        R24ActionButton("查看奖励明细", onOpenLedger, "reward.open-ledger")
                        R24ActionButton("支付宝账户", onOpenPayoutAccount, "reward.open-payout-account")
                        R24ActionButton("申请提现", onOpenWithdrawal, "reward.open-withdrawal")
                        R24ActionButton("提现记录", onOpenWithdrawals, "reward.open-withdrawals")
                    }
                }
                item {
                    R24SectionCard("账户信息") {
                        Text("数据版本 ${result.data.version}", color = HhyColors.TextSecondary)
                        Text("更新时间 ${result.data.updatedAt ?: "暂无"}", color = HhyColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RewardBalance(label: String, cent: Long) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
        ) {
            Text(label, color = HhyColors.TextSecondary)
            Text(r24Money(cent), fontWeight = FontWeight.SemiBold, color = HhyColors.TextPrimary)
        }
    }
}

@Composable
fun R24RewardLedgerScreen(
    api: ContractR24Api,
    accessToken: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var state by remember { mutableStateOf<R07CallResult<cc.orbexa.hhy.network.RewardLedgerPage>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, accessToken, refreshKey) {
        state = null
        state = api.rewardLedger(accessToken).also {
            if (it is R07CallResult.Failure && it.statusCode == 401) onSessionExpired()
        }
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r24.reward-ledger"),
        topBar = { R24TopBar("奖励明细", onBack) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (val result = state) {
            null -> R24Loading(padding, "scr-reward-002-loading")
            is R07CallResult.Failure -> R24LoadFailure(
                padding, "奖励流水暂时无法加载", result, { refreshKey += 1 },
            )
            is R07CallResult.Success -> if (result.data.items.isEmpty()) {
                R24Empty(padding, "暂无奖励明细", "奖励入账、冻结、提现等变化会显示在这里")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(HhySpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    items(result.data.items, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
                            shape = RoundedCornerShape(HhyRadius.NormalCard),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
                                ) {
                                    Text(item.sourceType ?: "奖励变更", fontWeight = FontWeight.SemiBold)
                                    Text(r24StatusLabel(item.status), color = HhyColors.TextSecondary)
                                    item.createdAt?.let { Text(it, color = HhyColors.TextTertiary) }
                                }
                                Text(
                                    r24SignedMoney(item.amountCent ?: 0),
                                    fontWeight = FontWeight.Bold,
                                    color = if ((item.amountCent ?: 0) >= 0) HhyColors.Success else HhyColors.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun R24PayoutAccountScreen(
    api: ContractR24Api,
    accessToken: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var account by remember { mutableStateOf<R24PayoutAccountResource?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var accountName by remember { mutableStateOf("") }
    var alipayAccount by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeError by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, accessToken, refreshKey) {
        loading = true
        loadFailure = null
        when (val result = api.payoutAccount(accessToken)) {
            is R07CallResult.Success -> {
                account = result.data
                if (accountName.isBlank()) accountName = result.data.accountName
            }
            is R07CallResult.Failure -> when (result.statusCode) {
                401 -> onSessionExpired()
                404 -> account = null
                else -> loadFailure = result
            }
        }
        loading = false
    }

    fun submit() {
        val trimmedName = accountName.trim()
        val trimmedAccount = alipayAccount.trim()
        when {
            trimmedName.isBlank() -> notice = "请输入支付宝实名认证姓名"
            trimmedAccount.length !in 5..64 || !Regex("^[0-9A-Za-z._@+\\-]+$").matches(trimmedAccount) ->
                notice = "请输入有效的支付宝手机号或邮箱"
            smsCode.length !in 4..10 -> notice = "请输入有效短信验证码"
            else -> {
                submitting = true
                notice = null
                scope.launch {
                    val request = R24UpdatePayoutAccountRequest(
                        accountName = trimmedName,
                        alipayAccount = trimmedAccount,
                        smsCode = smsCode,
                        expectedVersion = account?.version,
                    )
                    when (val result = api.updatePayoutAccount(accessToken, UUID.randomUUID().toString(), request)) {
                        is R07CallResult.Success -> {
                            account = result.data
                            accountName = result.data.accountName
                            alipayAccount = ""
                            smsCode = ""
                            notice = "支付宝账户已保存"
                            noticeError = false
                        }
                        is R07CallResult.Failure -> {
                            if (result.statusCode == 401) onSessionExpired()
                            notice = r24FailureText(result, "支付宝账户保存失败")
                            noticeError = true
                            if (result.statusCode == 409) refreshKey += 1
                        }
                    }
                    submitting = false
                }
            }
        }
        if (notice != null && !submitting) noticeError = true
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r24.payout-account"),
        topBar = { R24TopBar("绑定支付宝", onBack, enabled = !submitting) },
        bottomBar = {
            if (!loading && loadFailure == null) {
                R24BottomAction(
                    label = if (account == null) "绑定支付宝" else "保存修改",
                    loading = submitting,
                    enabled = !submitting,
                    onClick = ::submit,
                    testTag = "payout-account.save",
                )
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when {
            loading -> R24Loading(padding, "scr-wd-001-loading")
            loadFailure != null -> R24LoadFailure(
                padding, "支付宝账户暂时无法加载", requireNotNull(loadFailure), { refreshKey += 1 },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(HhySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            ) {
                item {
                    R24SectionCard("当前出款账户") {
                        Text(
                            account?.payoutAccountMasked ?: "尚未绑定支付宝账户",
                            fontSize = HhyType.SectionTitleSize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            account?.let { "${it.accountName} · ${r24StatusLabel(it.status)}" }
                                ?: "提现前需要绑定与实名信息一致的支付宝账户",
                            color = HhyColors.TextSecondary,
                        )
                    }
                }
                notice?.let { message -> item { R24Notice(message, noticeError) } }
                item {
                    R24SectionCard("账户信息") {
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { if (it.length <= 2000) accountName = it; notice = null },
                            modifier = Modifier.fillMaxWidth().testTag("payout-account.name"),
                            label = { Text("支付宝实名认证姓名") },
                            enabled = !submitting,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = alipayAccount,
                            onValueChange = { if (it.length <= 64) alipayAccount = it; notice = null },
                            modifier = Modifier.fillMaxWidth().testTag("payout-account.account"),
                            label = { Text(if (account == null) "支付宝账号" else "输入新支付宝账号") },
                            supportingText = { Text("支持支付宝绑定手机号或邮箱，保存后仅显示脱敏信息") },
                            enabled = !submitting,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        )
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { value ->
                                if (value.length <= 10 && value.all(Char::isDigit)) smsCode = value
                                notice = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("payout-account.sms-code"),
                            label = { Text("短信验证码") },
                            enabled = !submitting,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                    }
                }
                item {
                    R24Notice("账户姓名必须与当前实名认证一致。修改账户需要短信验证。", false)
                }
            }
        }
    }
}

@Composable
fun R24CreateWithdrawalScreen(
    api: ContractR24Api,
    meApi: ContractR12MeApi,
    accessToken: String,
    onBack: () -> Unit,
    onOpenPayoutAccount: () -> Unit,
    onOpenHistory: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var payoutAccount by remember { mutableStateOf<R24PayoutAccountResource?>(null) }
    var rewardAccount by remember { mutableStateOf<RewardAccountResource?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailure by remember { mutableStateOf<R07CallResult.Failure?>(null) }
    var payoutAccountMissing by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<R24WithdrawalQuoteResource?>(null) }
    var submitted by remember { mutableStateOf<R24WithdrawalResource?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, meApi, accessToken, refreshKey) {
        loading = true
        loadFailure = null
        payoutAccountMissing = false
        coroutineScope {
            val payoutCall = async { api.payoutAccount(accessToken) }
            val rewardCall = async { meApi.rewardAccount(accessToken) }
            when (val result = payoutCall.await()) {
                is R07CallResult.Success -> payoutAccount = result.data
                is R07CallResult.Failure -> when (result.statusCode) {
                    401 -> onSessionExpired()
                    404 -> payoutAccountMissing = true
                    else -> loadFailure = result
                }
            }
            when (val result = rewardCall.await()) {
                is R07CallResult.Success -> rewardAccount = result.data
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired() else loadFailure = result
                }
            }
        }
        loading = false
    }

    fun requestQuote() {
        val amountCent = parseR24Money(amountInput)
        val account = payoutAccount
        when {
            amountCent == null || amountCent <= 0 -> notice = "请输入有效提现金额，最多两位小数"
            account == null -> notice = "请先绑定支付宝账户"
            rewardAccount != null && amountCent > requireNotNull(rewardAccount).availableCent -> notice = "提现金额不能超过可提现余额"
            else -> {
                submitting = true
                notice = null
                scope.launch {
                    when (val result = api.quoteWithdrawal(
                        accessToken,
                        UUID.randomUUID().toString(),
                        R24WithdrawalQuoteRequest(amountCent, account.id),
                    )) {
                        is R07CallResult.Success -> quote = result.data
                        is R07CallResult.Failure -> {
                            if (result.statusCode == 401) onSessionExpired()
                            notice = r24FailureText(result, "提现报价失败")
                        }
                    }
                    submitting = false
                }
            }
        }
    }

    fun submitWithdrawal() {
        val currentQuote = quote ?: return
        val account = payoutAccount ?: return
        submitting = true
        notice = null
        scope.launch {
            when (val result = api.createWithdrawal(
                accessToken,
                UUID.randomUUID().toString(),
                R24CreateWithdrawalRequest(currentQuote.amountCent, currentQuote.id, account.id),
            )) {
                is R07CallResult.Success -> submitted = result.data
                is R07CallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    notice = r24FailureText(result, "提现提交失败")
                    if (result.statusCode == 409 || result.statusCode == 422) quote = null
                }
            }
            submitting = false
        }
    }

    val bottomLabel = when {
        submitted != null -> "查看提现记录"
        quote != null -> "确认提现"
        else -> "获取提现报价"
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r24.create-withdrawal"),
        topBar = { R24TopBar("申请提现", onBack, enabled = !submitting) },
        bottomBar = {
            if (!loading && loadFailure == null && !payoutAccountMissing) {
                R24BottomAction(
                    label = bottomLabel,
                    loading = submitting,
                    enabled = !submitting,
                    onClick = when {
                        submitted != null -> onOpenHistory
                        quote != null -> ::submitWithdrawal
                        else -> ::requestQuote
                    },
                    testTag = "withdrawal.primary-action",
                )
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when {
            loading -> R24Loading(padding, "scr-wd-002-loading")
            loadFailure != null -> R24LoadFailure(
                padding, "提现信息暂时无法加载", requireNotNull(loadFailure), { refreshKey += 1 },
            )
            payoutAccountMissing -> R24Empty(
                padding, "请先绑定支付宝", "提现仅支持已完成实名校验的支付宝账户", "去绑定", onOpenPayoutAccount,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(HhySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        R24Metric("可提现", r24Money(rewardAccount?.availableCent ?: 0))
                        R24Metric("待结算", r24Money(rewardAccount?.pendingCent ?: 0))
                    }
                }
                submitted?.let { withdrawal ->
                    item {
                        R24SectionCard("提现申请已提交") {
                            R24StatusPill(withdrawal.status)
                            Text(r24Money(withdrawal.amountCent), fontSize = HhyType.PageTitleSize, fontWeight = FontWeight.Bold)
                            Text("提现单号 ${withdrawal.withdrawalNo}", color = HhyColors.TextSecondary)
                            Text("系统将依次完成风控、财务审核和支付宝出款", color = HhyColors.TextSecondary)
                        }
                    }
                }
                if (submitted == null) {
                    item {
                        R24SectionCard("提现金额") {
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { value ->
                                    if (value.length <= 12 && value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) {
                                        amountInput = value
                                        quote = null
                                        notice = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("withdrawal.amount"),
                                label = { Text("金额（元）") },
                                prefix = { Text("¥") },
                                enabled = !submitting,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Text("支付宝 ${payoutAccount?.payoutAccountMasked}", color = HhyColors.TextSecondary)
                        }
                    }
                    quote?.let { currentQuote ->
                        item {
                            R24SectionCard("报价明细") {
                                R24ValueRow("提现金额", r24Money(currentQuote.amountCent))
                                R24ValueRow("手续费", r24Money(currentQuote.feeCent))
                                R24ValueRow("预计到账", r24Money(currentQuote.netAmountCent), emphasized = true)
                                Text("报价有效至 ${currentQuote.expiresAt ?: "短时间内"}", color = HhyColors.TextSecondary)
                            }
                        }
                    }
                }
                notice?.let { message -> item { R24Notice(message, true) } }
                item { R24Notice("提交即表示确认提现金额、手续费和当前支付宝账户。", false) }
            }
        }
    }
}

@Composable
fun R24WithdrawalHistoryScreen(
    api: ContractR24Api,
    accessToken: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var state by remember { mutableStateOf<R07CallResult<cc.orbexa.hhy.network.R24WithdrawalPage>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, accessToken, refreshKey) {
        state = null
        state = api.withdrawals(accessToken).also {
            if (it is R07CallResult.Failure && it.statusCode == 401) onSessionExpired()
        }
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r24.withdrawal-history"),
        topBar = { R24TopBar("提现记录", onBack, action = { refreshKey += 1 }) },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (val result = state) {
            null -> R24Loading(padding, "scr-wd-003-loading")
            is R07CallResult.Failure -> R24LoadFailure(
                padding, "提现记录暂时无法加载", result, { refreshKey += 1 },
            )
            is R07CallResult.Success -> if (result.data.items.isEmpty()) {
                R24Empty(padding, "暂无提现记录", "提交提现后，可在这里查看审核和到账状态")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(HhySpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    items(result.data.items, key = { it.id }) { withdrawal ->
                        WithdrawalHistoryCard(withdrawal)
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawalHistoryCard(withdrawal: R24WithdrawalResource) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("withdrawal.${withdrawal.id}"),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                R24StatusPill(withdrawal.status)
                Text(r24Money(withdrawal.amountCent), fontWeight = FontWeight.Bold)
            }
            R24ValueRow("手续费", r24Money(withdrawal.feeCent))
            R24ValueRow("到账金额", r24Money(withdrawal.netAmountCent), emphasized = true)
            withdrawal.payoutAccountMasked?.let { R24ValueRow("支付宝", it) }
            Text("提现单号 ${withdrawal.withdrawalNo}", color = HhyColors.TextSecondary)
            Text(withdrawal.createdAt ?: "时间待同步", color = HhyColors.TextTertiary)
            withdrawal.failureCode?.let { Text("失败原因：$it", color = HhyColors.Error) }
        }
    }
}

@Composable
private fun R24TopBar(
    title: String,
    onBack: () -> Unit,
    enabled: Boolean = true,
    action: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                title,
                fontSize = HhyType.PageTitleSize,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = { HhyBackButton(onBack, enabled) },
        actions = { action?.let { TextButton(onClick = it) { Text("刷新") } } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HhyColors.Surface),
    )
}

@Composable
private fun R24Loading(padding: PaddingValues, testTag: String) {
    Column(
        Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.testTag(testTag))
        Spacer(Modifier.height(HhySpacing.Md))
        Text("正在加载", color = HhyColors.TextSecondary)
    }
}

@Composable
private fun R24LoadFailure(
    padding: PaddingValues,
    title: String,
    failure: R07CallResult.Failure,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(HhySpacing.Sm))
        Text(r24FailureText(failure, "请检查网络或稍后重试"), color = HhyColors.TextSecondary)
        Spacer(Modifier.height(HhySpacing.Lg))
        OutlinedButton(onClick = onRetry) { Text("重新加载") }
    }
}

@Composable
private fun R24Empty(
    padding: PaddingValues,
    title: String,
    description: String,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(HhySpacing.Sm))
        Text(description, color = HhyColors.TextSecondary)
        if (actionLabel != null && action != null) {
            Spacer(Modifier.height(HhySpacing.Lg))
            Button(onClick = action) { Text(actionLabel) }
        }
    }
}

@Composable
private fun R24SectionCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Text(title, fontSize = HhyType.CardTitleSize, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun R24ActionButton(label: String, action: () -> Unit, testTag: String) {
    OutlinedButton(
        onClick = action,
        modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight).testTag(testTag),
        shape = RoundedCornerShape(HhyRadius.Button),
    ) { Text(label) }
}

@Composable
private fun R24BottomAction(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg)
                .height(HhySize.PrimaryButtonHeight).testTag(testTag),
            shape = RoundedCornerShape(HhyRadius.Button),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(HhySize.StandardProgress), color = HhyColors.TextInverse)
                Spacer(Modifier.size(HhySpacing.Sm))
            }
            Text(label)
        }
    }
}

@Composable
private fun R24Notice(message: String, error: Boolean) {
    val background = if (error) HhyColors.ErrorSoft else HhyColors.SoftBlue
    val foreground = if (error) HhyColors.Error else HhyColors.TextSecondary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        shape = RoundedCornerShape(HhyRadius.Tag),
    ) {
        Text(message, Modifier.padding(HhySpacing.Md), color = foreground)
    }
}

@Composable
private fun RowScope.R24Metric(label: String, value: String) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
    ) {
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(label, color = HhyColors.TextSecondary)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun R24ValueRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HhyColors.TextSecondary)
        Text(value, fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun R24StatusPill(status: String) {
    val color = r24StatusColor(status)
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(HhyRadius.Pill)) {
        Text(
            r24StatusLabel(status),
            Modifier.padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Xs),
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun parseR24Money(value: String): Long? = runCatching {
    val decimal = value.trim().takeIf(String::isNotEmpty)?.let(::BigDecimal) ?: return null
    if (decimal.scale() > 2 || decimal.signum() <= 0) return null
    decimal.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()

private fun r24Money(cent: Long): String = "¥${BigDecimal.valueOf(cent, 2).setScale(2).toPlainString()}"

private fun r24SignedMoney(cent: Long): String = when {
    cent > 0 -> "+${r24Money(cent)}"
    else -> r24Money(cent)
}

private fun r24StatusLabel(status: String): String = when (status.uppercase()) {
    "ACTIVE" -> "已启用"
    "PENDING", "RISK_REVIEWING" -> "风控审核中"
    "FINANCE_REVIEWING" -> "财务审核中"
    "PAYOUT_PENDING", "PAYOUT_PROCESSING" -> "出款处理中"
    "SUCCESS", "WITHDRAWN", "COMPLETED" -> "提现成功"
    "FAILED" -> "提现失败"
    "REJECTED" -> "审核未通过"
    "CANCELLED" -> "已取消"
    "FROZEN" -> "已冻结"
    "QUOTED" -> "报价已生成"
    else -> status
}

private fun r24StatusColor(status: String): Color = when (status.uppercase()) {
    "SUCCESS", "WITHDRAWN", "COMPLETED", "ACTIVE" -> HhyColors.Success
    "FAILED", "REJECTED", "CANCELLED" -> HhyColors.Error
    "FROZEN" -> HhyColors.BrandTertiary
    else -> HhyColors.Warning
}

private fun r24FailureText(failure: R07CallResult.Failure, fallback: String): String = when {
    failure.statusCode == null -> "网络连接不可用，请恢复网络后重试"
    failure.statusCode == 409 -> "数据已发生变化，请刷新后重试"
    failure.statusCode == 422 && failure.errorCode?.contains("BALANCE_INSUFFICIENT") == true -> "可提现余额不足"
    failure.statusCode == 422 -> "当前账户状态不满足此操作条件"
    failure.statusCode == 403 -> "当前账号无权执行此操作"
    failure.retryable -> "$fallback，稍后可重试"
    else -> fallback
}
