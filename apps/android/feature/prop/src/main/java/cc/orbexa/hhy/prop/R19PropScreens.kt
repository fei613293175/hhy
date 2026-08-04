@file:OptIn(ExperimentalMaterial3Api::class)

package cc.orbexa.hhy.prop

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.runtime.mutableLongStateOf
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
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.CommandResultResource
import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR12Api
import cc.orbexa.hhy.network.ContractR19PropApi
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R19PropOrderRequest
import cc.orbexa.hhy.network.R19PropPage
import cc.orbexa.hhy.network.R19PropResource
import cc.orbexa.hhy.network.R19PropUseRequest
import java.util.UUID
import kotlinx.coroutines.launch

private val propTypes = listOf(
    null to "全部",
    "REFRESH" to "刷新",
    "TOP" to "置顶",
    "HEADLINE" to "头条",
    "COLOR" to "变色",
)

@Composable
fun R19PropStoreScreen(
    api: ContractR19PropApi,
    token: String,
    onBack: () -> Unit,
    onMine: () -> Unit,
    onOrderCreated: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<R07CallResult<R19PropPage>?>(null) }
    var selected by remember { mutableStateOf<R19PropResource?>(null) }
    var quantity by remember { mutableLongStateOf(1) }
    var channel by remember { mutableStateOf("ALIPAY") }
    var submit by remember { mutableStateOf<R07CallResult<CommandResultResource>?>(null) }
    val scope = rememberCoroutineScope()
    val intentKey = remember(selected?.id, quantity, channel) { "r19-order-${UUID.randomUUID()}" }
    fun load() {
        scope.launch {
            state = api.store(token, keyword = selectedType, sort = "name:asc").also {
                if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
            }
        }
    }
    LaunchedEffect(token, selectedType) { load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("道具商城") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { TextButton(onClick = onMine) { Text("我的道具") } },
            )
        },
        modifier = Modifier.testTag("hhy.screen.scr-prop-001"),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(HhyColors.PageBackground)) {
            PropBanner()
            LazyRow(
                contentPadding = PaddingValues(horizontal = HhySpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                items(propTypes, key = { it.second }) { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label) },
                    )
                }
            }
            when (val value = state) {
                null -> LoadingPanel(Modifier.weight(1f))
                is R07CallResult.Failure -> FailurePanel(value, Modifier.weight(1f), ::load)
                is R07CallResult.Success -> if (value.data.items.isEmpty()) {
                    EmptyPanel("暂无可购买道具", Modifier.weight(1f), ::load)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(HhySpacing.Lg),
                        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                    ) {
                        gridItems(value.data.items, key = { it.id }) { item ->
                            PropStoreTile(item) {
                                selected = item
                                quantity = 1
                                submit = null
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { if (submit == null) selected = null },
            title = { Text("确认购买") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                    Text(item.name ?: propTypeLabel(item.propType), fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("数量", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { if (quantity > 1) quantity -= 1 }) { Text("-") }
                        Text(quantity.toString(), modifier = Modifier.padding(horizontal = HhySpacing.Lg))
                        OutlinedButton(onClick = { if (quantity < 1_000) quantity += 1 }) { Text("+") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        FilterChip(channel == "ALIPAY", { channel = "ALIPAY" }, { Text("支付宝") })
                        FilterChip(channel == "WECHAT_PAY", { channel = "WECHAT_PAY" }, { Text("微信支付") })
                    }
                    (submit as? R07CallResult.Failure)?.let {
                        Text(propFailureMessage(it), color = HhyColors.Error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = submit !is R07CallResult.Success,
                    onClick = {
                        scope.launch {
                            submit = api.order(
                                token,
                                R19PropOrderRequest(item.id, quantity, channel),
                                intentKey,
                            ).also {
                                if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
                                if (it is R07CallResult.Success) {
                                    selected = null
                                    onOrderCreated(it.data.businessNo.orEmpty())
                                }
                            }
                        }
                    },
                ) { Text(if (submit == null) "提交订单" else "重试提交") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("取消") } },
        )
    }
}

@Composable
fun R19MyPropsScreen(
    api: ContractR19PropApi,
    token: String,
    onBack: () -> Unit,
    onStore: () -> Unit,
    onUse: (String) -> Unit,
    onExpired: () -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<R07CallResult<R19PropPage>?>(null) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            state = api.mine(token, status = status).also {
                if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
            }
        }
    }
    LaunchedEffect(token, status) { load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的道具") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = { TextButton(onClick = onStore) { Text("道具商城") } },
            )
        },
        modifier = Modifier.testTag("hhy.screen.scr-prop-002"),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(HhyColors.PageBackground)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = HhySpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            ) {
                items(listOf(null to "全部", "AVAILABLE" to "可使用", "CONSUMED" to "已用完", "EXPIRED" to "已过期")) {
                    FilterChip(status == it.first, { status = it.first }, { Text(it.second) })
                }
            }
            when (val value = state) {
                null -> LoadingPanel(Modifier.weight(1f))
                is R07CallResult.Failure -> FailurePanel(value, Modifier.weight(1f), ::load)
                is R07CallResult.Success -> if (value.data.items.isEmpty()) {
                    EmptyPanel("当前筛选下暂无道具", Modifier.weight(1f), onStore)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(HhySpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                    ) {
                        items(value.data.items, key = { it.id }) { item ->
                            InventoryRow(item, onUse)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun R19PropUseScreen(
    propApi: ContractR19PropApi,
    contentApi: ContractR12Api,
    token: String,
    inventoryId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onExpired: () -> Unit,
) {
    var inventoryState by remember { mutableStateOf<R07CallResult<R19PropPage>?>(null) }
    var contentState by remember { mutableStateOf<R07CallResult<ContentPageResource>?>(null) }
    var selectedContent by remember { mutableStateOf<ContentResource?>(null) }
    var scheduledAt by remember { mutableStateOf("") }
    var submit by remember { mutableStateOf<R07CallResult<CommandResultResource>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val intentKey = remember(inventoryId, selectedContent?.id, scheduledAt) { "r19-use-${UUID.randomUUID()}" }
    LaunchedEffect(token, inventoryId, reloadKey) {
        inventoryState = propApi.mine(token, pageSize = 100).also {
            if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
        }
        contentState = contentApi.contents(token, pageSize = 100, sort = "updatedAt:desc").also {
            if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
        }
    }
    val inventory = (inventoryState as? R07CallResult.Success)?.data?.items?.firstOrNull { it.id == inventoryId }
    Scaffold(
        topBar = { TopAppBar(title = { Text("使用道具") }, navigationIcon = { HhyBackButton(onBack) }) },
        bottomBar = {
            Surface(shadowElevation = HhySpacing.Xs) {
                Row(
                    Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                    horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        enabled = inventory != null && selectedContent != null && submit !is R07CallResult.Success,
                        modifier = Modifier.weight(1f).testTag("hhy.prop.use.confirm"),
                        onClick = {
                            val item = inventory ?: return@Button
                            val content = selectedContent ?: return@Button
                            scope.launch {
                                submit = propApi.use(
                                    token,
                                    item.id,
                                    R19PropUseRequest(
                                        content.id,
                                        scheduledAt.trim().ifBlank { null },
                                        item.version,
                                    ),
                                    intentKey,
                                ).also {
                                    if (it is R07CallResult.Failure && it.statusCode == 401) onExpired()
                                    if (it is R07CallResult.Success) onDone()
                                }
                            }
                        },
                    ) { Text(if (submit == null) "确认使用" else "重试") }
                }
            }
        },
        modifier = Modifier.testTag("hhy.screen.scr-prop-003"),
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).background(HhyColors.PageBackground),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                when {
                    inventoryState == null -> LoadingPanel()
                    inventory == null -> InlineNotice("道具库存已变化", { reloadKey += 1 })
                    else -> PropSummary(inventory)
                }
            }
            item { Text("选择目标内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            when (val value = contentState) {
                null -> item { LoadingPanel() }
                is R07CallResult.Failure -> item { InlineNotice(propFailureMessage(value), { reloadKey += 1 }) }
                is R07CallResult.Success -> if (value.data.items.isEmpty()) {
                    item { InlineNotice("暂无可选择内容", { reloadKey += 1 }) }
                } else {
                    items(value.data.items, key = { it.id }) { content ->
                        ContentChoice(content, selectedContent?.id == content.id) { selectedContent = content }
                    }
                }
            }
            if (inventory?.propType != "REFRESH") {
                item {
                    OutlinedTextField(
                        value = scheduledAt,
                        onValueChange = { scheduledAt = it.take(64); submit = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("计划生效时间") },
                        placeholder = { Text("ISO 8601 时间，可留空") },
                        singleLine = true,
                    )
                }
            }
            (submit as? R07CallResult.Failure)?.let { failure ->
                item {
                    InlineNotice(propFailureMessage(failure), {
                        if (failure.statusCode == 409) reloadKey += 1
                    })
                }
            }
            item { Spacer(Modifier.height(HhySpacing.Xxl)) }
        }
    }
}

@Composable private fun PropBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
        color = HhyColors.SoftBlue,
        shape = RoundedCornerShape(HhyRadius.Tag),
    ) {
        Column(Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text("让优质内容获得更多展示", fontWeight = FontWeight.Bold, color = HhyColors.TextPrimary)
            Text("可用能力与生效结果以服务端确认为准", color = HhyColors.TextSecondary)
        }
    }
}

@Composable private fun PropStoreTile(item: R19PropResource, onBuy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(HhyRadius.Tag),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        border = BorderStroke(HhySize.Hairline, HhyColors.Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            Box(
                Modifier.size(HhySize.PrimaryButtonHeight).background(
                    propColor(item.propType), RoundedCornerShape(HhyRadius.Tag),
                ),
                contentAlignment = Alignment.Center,
            ) { Text(propTypeLabel(item.propType).take(1), color = HhyColors.TextInverse, fontWeight = FontWeight.Bold) }
            Text(item.name ?: propTypeLabel(item.propType), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(statusLabel(item.status), color = HhyColors.TextSecondary)
            Button(onClick = onBuy, enabled = item.status == "ACTIVE", modifier = Modifier.fillMaxWidth()) { Text("购买") }
        }
    }
}

@Composable private fun InventoryRow(item: R19PropResource, onUse: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(HhyRadius.Tag),
        color = HhyColors.Surface,
        border = BorderStroke(HhySize.Hairline, HhyColors.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Box(
                Modifier.size(HhySize.PrimaryButtonHeight).background(propColor(item.propType), RoundedCornerShape(HhyRadius.Tag)),
                contentAlignment = Alignment.Center,
            ) { Text(propTypeLabel(item.propType).take(1), color = HhyColors.TextInverse, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(item.name ?: propTypeLabel(item.propType), fontWeight = FontWeight.SemiBold)
                Text("${statusLabel(item.status)} · 数量 ${item.quantity}", color = HhyColors.TextSecondary)
                item.expiresAt?.let { Text("有效期至 $it", color = HhyColors.TextTertiary, maxLines = 1) }
            }
            if (canUseProp(item.status, item.quantity)) {
                Button(onClick = { onUse(item.id) }) { Text("使用") }
            }
        }
    }
}

@Composable private fun PropSummary(item: R19PropResource) {
    Surface(
        shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.Surface,
        border = BorderStroke(HhySize.Hairline, HhyColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            Text(item.name ?: propTypeLabel(item.propType), fontWeight = FontWeight.Bold)
            Text("${propTypeLabel(item.propType)} · ${statusLabel(item.status)}", color = HhyColors.TextSecondary)
            HorizontalDivider(color = HhyColors.Border)
            Text("可用数量 ${item.quantity}")
            item.expiresAt?.let { Text("有效期至 $it", color = HhyColors.TextSecondary) }
        }
    }
}

@Composable private fun ContentChoice(content: ContentResource, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(HhyRadius.Tag),
        color = if (selected) HhyColors.SoftBlue else HhyColors.Surface,
        border = BorderStroke(HhySize.Hairline, if (selected) HhyColors.BrandPrimary else HhyColors.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
                Text(content.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(content.status, color = HhyColors.TextSecondary)
            }
            Text(if (selected) "已选择" else "选择", color = HhyColors.BrandPrimary)
        }
    }
}

@Composable private fun LoadingPanel(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(HhySpacing.Xxxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
    }
}

@Composable private fun FailurePanel(
    failure: R07CallResult.Failure,
    modifier: Modifier = Modifier,
    retry: () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { InlineNotice(propFailureMessage(failure), retry) }
}

@Composable private fun EmptyPanel(message: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { InlineNotice(message, action) }
}

@Composable private fun InlineNotice(message: String, action: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(HhySpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Text(message, color = HhyColors.TextSecondary)
        OutlinedButton(onClick = action) { Text("刷新") }
    }
}

private fun propTypeLabel(value: String): String = when (value) {
    "REFRESH" -> "刷新"
    "TOP" -> "置顶"
    "HEADLINE" -> "头条"
    "COLOR" -> "变色"
    else -> "道具"
}

private fun statusLabel(value: String): String = when (value) {
    "ACTIVE", "AVAILABLE" -> "可使用"
    "RESERVED" -> "已预留"
    "CONSUMED" -> "已用完"
    "EXPIRED" -> "已过期"
    "INACTIVE" -> "已下架"
    else -> "状态待确认"
}

private fun propColor(value: String) = when (value) {
    "REFRESH" -> HhyColors.BrandPrimary
    "TOP" -> HhyColors.BrandSecondary
    "HEADLINE" -> HhyColors.RewardRed
    "COLOR" -> HhyColors.BrandTertiary
    else -> HhyColors.TextSecondary
}
