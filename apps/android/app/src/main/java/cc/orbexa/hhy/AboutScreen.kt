package cc.orbexa.hhy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ExperienceApi
import cc.orbexa.hhy.network.VersionCheckRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(api: ExperienceApi, accessToken: String, onBack: () -> Unit) {
    var policy by remember { mutableStateOf<cc.orbexa.hhy.network.VersionPolicy?>(null) }
    var agreement by remember { mutableStateOf<cc.orbexa.hhy.network.AgreementSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(api, accessToken) {
        loading = true; failed = false
        api.checkVersion(VersionCheckRequest("ANDROID", BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, BuildConfig.APP_CHANNEL, BuildConfig.APP_ENVIRONMENT)).onSuccess { policy = it }.onFailure { failed = true }
        api.agreement("privacy-policy").onSuccess { agreement = it }
        loading = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text("关于与检查更新") }, navigationIcon = { cc.orbexa.hhy.designsystem.HhyBackButton(onBack) }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            item { Text("合伙云 Pro", style = MaterialTheme.typography.headlineSmall); Text("当前版本 ${BuildConfig.VERSION_NAME}", color = HhyColors.TextSecondary) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    if (loading) CircularProgressIndicator()
                    else if (failed) { Text("版本信息暂时无法获取"); OutlinedButton(onClick = onBack) { Text("返回") } }
                    else { Text("最新版本 ${policy?.latestVersionName ?: "暂无"}"); Text(policy?.releaseNotes ?: "暂无更新说明", color = HhyColors.TextSecondary); Button(onClick = {}, enabled = policy?.updateType != cc.orbexa.hhy.network.UpdateType.NONE) { Text("检查更新") } }
                } }
            }
            agreement?.let { page ->
                item { Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) { Text(page.title ?: "隐私政策", style = MaterialTheme.typography.titleMedium); page.description?.let { Text(it, color = HhyColors.TextSecondary) } } } }
                items(page.content) { text -> Text(text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
