package cc.orbexa.hhy.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
    onOpenLoginDevices: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    onOpenCancellation: () -> Unit = {},
    onOpenIdentity: () -> Unit = {},
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("合伙云 Pro") })
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
                    text = navigationItems[selectedIndex].label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(HhySpacing.Sm))
                Text(
                    text = when (selectedIndex) {
                        0 -> "发现值得合作的人和项目"
                        1 -> "参与活动，获得更多权益"
                        2 -> "分享你的项目与能力"
                        3 -> "与合作伙伴保持联系"
                        else -> "管理个人资料与账号安全"
                    },
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
                            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenIdentity) {
                                Text("实名认证")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenLoginDevices) {
                                Text("登录设备")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenChangePassword) {
                                Text("修改登录密码")
                            }
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenCancellation) {
                                Text("注销账号")
                            }
                        }
                    }
                }
            }
            if (selectedIndex != 4) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(HhyRadius.LargeCard),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        "更多内容正在陆续开放",
                        modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
                        color = HhyColors.TextSecondary,
                    )
                }
            }
        }
    }
}
