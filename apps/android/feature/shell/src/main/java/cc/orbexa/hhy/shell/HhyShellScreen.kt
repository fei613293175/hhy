package cc.orbexa.hhy.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySpacing

private data class NavigationItem(val label: String, val compactLabel: String)

private val navigationItems = listOf(
    NavigationItem("首页", "首"),
    NavigationItem("红包", "包"),
    NavigationItem("发布", "发"),
    NavigationItem("消息", "信"),
    NavigationItem("我的", "我"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HhyShellScreen(
    versionName: String,
    buildType: String,
    apiBaseUrl: String,
    contractVersion: String,
    onOpenLoginDevices: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("合伙云 Pro · 开发基线") })
        },
        bottomBar = {
            NavigationBar {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Text(item.compactLabel, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            item {
                Text(
                    text = "$versionName 工程已就绪",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(HhySpacing.Sm))
                Text(
                    text = "首批开发从登录、平台状态、版本检查与基础 RBAC 开始。",
                    color = HhyColors.TextSecondary,
                )
            }
            if (selectedIndex == 4) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(HhyRadius.LargeCard),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                        ) {
                            Text("账号与安全", fontWeight = FontWeight.SemiBold)
                            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenLoginDevices) {
                                Text("登录设备")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenChangePassword) {
                                Text("修改登录密码")
                            }
                        }
                    }
                }
            }
            item { ReadinessCard("契约", "OpenAPI / WebSocket 已冻结", true) }
            item { ReadinessCard("数据", "Flyway 迁移与资金硬约束已落地", true) }
            item { ReadinessCard("安全", "公开接口、请求 ID 与默认拒绝策略已建立", true) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(HhySpacing.Lg)) {
                        Text("构建信息", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(HhySpacing.Sm))
                        MetadataRow("App", "$versionName ($buildType)")
                        MetadataRow("Contract", contractVersion)
                        MetadataRow("API", apiBaseUrl)
                        MetadataRow("Screen", navigationItems[selectedIndex].label)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(title: String, description: String, ready: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = HhyColors.TextSecondary)
            }
            Text(
                text = if (ready) "READY" else "PENDING",
                color = if (ready) HhyColors.Success else HhyColors.Warning,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HhySpacing.Xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = HhyColors.TextSecondary)
        Text(value, modifier = Modifier.padding(start = HhySpacing.Lg))
    }
}
