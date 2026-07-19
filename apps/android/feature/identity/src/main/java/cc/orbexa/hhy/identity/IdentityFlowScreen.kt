package cc.orbexa.hhy.identity

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentityConsentCallResult
import cc.orbexa.hhy.network.IdentityConsentResource
import cc.orbexa.hhy.network.IdentityCreateLivenessTokenRequest
import cc.orbexa.hhy.network.IdentityCreateSessionRequest
import cc.orbexa.hhy.network.IdentityRetrySessionRequest
import cc.orbexa.hhy.network.IdentitySessionResource
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface IdentityDestination {
    data object Home : IdentityDestination
    data object Form : IdentityDestination
    data class Liveness(val session: IdentitySessionResource) : IdentityDestination
    data class Result(val session: IdentitySessionResource) : IdentityDestination
}

@Composable
fun IdentityFlowScreen(
    api: ContractIdentityApi,
    accessToken: String,
    returnUrl: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var destination by remember { mutableStateOf<IdentityDestination>(IdentityDestination.Home) }
    when (val current = destination) {
        IdentityDestination.Home -> IdentityHomeScreen(
            onBack = onBack,
            onStart = { destination = IdentityDestination.Form },
        )
        IdentityDestination.Form -> IdentityFormScreen(
            api = api,
            accessToken = accessToken,
            onBack = { destination = IdentityDestination.Home },
            onSessionExpired = onSessionExpired,
            onCreated = { destination = IdentityDestination.Liveness(it) },
        )
        is IdentityDestination.Liveness -> IdentityLivenessScreen(
            api = api,
            accessToken = accessToken,
            initial = current.session,
            returnUrl = returnUrl,
            onBack = { destination = IdentityDestination.Home },
            onSessionExpired = onSessionExpired,
            onResult = { destination = IdentityDestination.Result(it) },
        )
        is IdentityDestination.Result -> IdentityResultScreen(
            api = api,
            accessToken = accessToken,
            initial = current.session,
            onBack = { destination = IdentityDestination.Home },
            onSessionExpired = onSessionExpired,
            onRetryReady = { destination = IdentityDestination.Liveness(it) },
        )
    }
}

@Composable
private fun IdentityHomeScreen(onBack: () -> Unit, onStart: () -> Unit) {
    IdentityPage(title = "实名认证", onBack = onBack) {
        IdentityHero(symbol = "证", title = "完成实名认证", description = "用于保障账号安全和后续业务权益")
        IdentityCard {
            Text("认证前请准备", style = MaterialTheme.typography.titleMedium)
            Text("本人有效身份证件", color = HhyColors.TextSecondary)
            Text("可正常使用的手机相机", color = HhyColors.TextSecondary)
            Text("请由账号本人完成活体检测", color = HhyColors.TextSecondary)
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onStart) { Text("开始认证") }
        Text(
            "身份信息仅用于完成实名认证，并按隐私政策安全处理。",
            color = HhyColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun IdentityFormScreen(
    api: ContractIdentityApi,
    accessToken: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onCreated: (IdentitySessionResource) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var realName by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(IdentityFormErrors()) }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var requestKey by remember { mutableStateOf<String?>(null) }
    var consent by remember(accessToken) { mutableStateOf<IdentityConsentResource?>(null) }
    var consentLoading by remember(accessToken) { mutableStateOf(true) }
    var consentReload by remember(accessToken) { mutableStateOf(0) }
    var showConsent by remember { mutableStateOf(false) }

    LaunchedEffect(accessToken, consentReload) {
        consentLoading = true
        accepted = false
        when (val result = api.consent(accessToken)) {
            is IdentityConsentCallResult.Success -> {
                consent = result.consent
                message = null
            }
            is IdentityConsentCallResult.Failure -> {
                consent = null
                if (result.statusCode == 401) onSessionExpired()
                else message = when (result.statusCode) {
                    429 -> "操作过于频繁，请稍后再试"
                    null -> "网络连接失败，请检查网络后重试"
                    else -> "实名认证授权说明暂时无法加载，请稍后重试"
                }
            }
        }
        consentLoading = false
    }

    fun clearIntent() {
        requestKey = null
        message = null
    }

    IdentityPage(title = "填写身份信息", onBack = onBack) {
        Text("请填写本人真实信息", style = MaterialTheme.typography.titleLarge)
        Text("信息提交后不可自行修改，请仔细核对。", color = HhyColors.TextSecondary)
        message?.let { BusinessNotice(it, isError = true) }
        OutlinedTextField(
            value = realName,
            onValueChange = { realName = it; clearIntent() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("真实姓名") },
            singleLine = true,
            isError = errors.realName != null,
            supportingText = errors.realName?.let { value -> ({ Text(value) }) },
        )
        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it; clearIntent() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("身份证号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = errors.idNumber != null,
            supportingText = errors.idNumber?.let { value -> ({ Text(value) }) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = accepted,
                enabled = consent != null && !consentLoading,
                onCheckedChange = { accepted = it; clearIntent() },
            )
            Text("我已阅读并同意", color = HhyColors.TextSecondary)
            TextButton(
                enabled = consent != null && !consentLoading,
                onClick = { showConsent = true },
                modifier = Modifier.weight(1f),
            ) { Text("《${consent?.title ?: "实名认证授权说明"}》") }
        }
        if (consentLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text("正在加载实名认证授权说明", modifier = Modifier.padding(start = HhySpacing.Sm))
            }
        } else if (consent == null) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { consentReload += 1 },
            ) { Text("重新加载授权说明") }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting && !consentLoading && consent != null,
            onClick = {
                val nextErrors = validateIdentityForm(realName, idNumber)
                errors = nextErrors
                message = when {
                    !nextErrors.isEmpty -> null
                    !accepted -> "请先阅读并同意实名认证授权说明"
                    consent == null -> "实名认证授权说明暂时无法加载，请稍后重试"
                    else -> null
                }
                val currentConsent = consent ?: return@Button
                if (!nextErrors.isEmpty || !accepted) return@Button
                submitting = true
                val key = requestKey ?: UUID.randomUUID().toString().also { requestKey = it }
                scope.launch {
                    when (val result = api.createSession(
                        accessToken,
                        key,
                        IdentityCreateSessionRequest(
                            realName.trim(), idNumber.trim(), currentConsent.consentVersion,
                        ),
                    )) {
                        is IdentityCallResult.Success -> {
                            realName = ""
                            idNumber = ""
                            requestKey = null
                            onCreated(result.session)
                        }
                        is IdentityCallResult.Failure -> {
                            if (result.statusCode == 401) onSessionExpired()
                            else {
                                message = result.businessMessage("认证资料提交失败，请稍后重试")
                                if (result.statusCode == 422) consentReload += 1
                            }
                        }
                    }
                    submitting = false
                }
            },
        ) {
            if (submitting) CircularProgressIndicator(color = HhyColors.TextInverse)
            else Text("提交并开始活体检测")
        }
    }
    if (showConsent) {
        val currentConsent = consent
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(currentConsent?.title ?: "实名认证授权说明") },
            text = {
                Text(
                    currentConsent?.content.orEmpty(),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { showConsent = false }) { Text("我已阅读") }
            },
        )
    }
}

@Composable
private fun IdentityLivenessScreen(
    api: ContractIdentityApi,
    accessToken: String,
    initial: IdentitySessionResource,
    returnUrl: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onResult: (IdentitySessionResource) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember(initial.id) { mutableStateOf(initial) }
    var livenessUrl by remember(initial.id) { mutableStateOf(initial.livenessUrl) }
    var message by remember(initial.id) { mutableStateOf<String?>(null) }
    var loading by remember(initial.id) { mutableStateOf(livenessUrl.isNullOrBlank()) }
    var cameraGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        if (!it) message = "需要相机权限才能完成人脸活体检测"
    }

    fun refresh() {
        scope.launch {
            when (val result = api.session(accessToken, session.id)) {
                is IdentityCallResult.Success -> {
                    session = result.session
                    if (result.session.resultKind() !in setOf(IdentityResultKind.READY, IdentityResultKind.PENDING)) {
                        onResult(result.session)
                    }
                }
                is IdentityCallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    else message = result.businessMessage("认证结果查询失败，请稍后重试")
                }
            }
        }
    }

    LaunchedEffect(initial.id) {
        if (livenessUrl.isNullOrBlank()) {
            if (returnUrl.isBlank()) {
                message = "活体检测服务正在准备中，请稍后再试"
                loading = false
            } else when (val result = api.createLivenessToken(
                accessToken,
                initial.id,
                UUID.randomUUID().toString(),
                IdentityCreateLivenessTokenRequest(returnUrl),
            )) {
                is IdentityCallResult.Success -> {
                    session = result.session
                    livenessUrl = result.session.livenessUrl
                    if (livenessUrl.isNullOrBlank()) message = "暂时无法打开活体检测，请稍后重试"
                    loading = false
                }
                is IdentityCallResult.Failure -> {
                    loading = false
                    if (result.statusCode == 401) onSessionExpired()
                    else message = result.businessMessage("活体检测服务暂时不可用，请稍后重试")
                }
            }
        }
    }

    LaunchedEffect(session.id, livenessUrl) {
        if (livenessUrl.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            delay(3_000)
            when (val result = api.session(accessToken, session.id)) {
                is IdentityCallResult.Success -> {
                    session = result.session
                    if (result.session.resultKind() !in setOf(IdentityResultKind.READY, IdentityResultKind.PENDING)) {
                        onResult(result.session)
                        break
                    }
                }
                is IdentityCallResult.Failure -> if (result.statusCode == 401) {
                    onSessionExpired()
                    break
                }
            }
        }
    }

    IdentityPage(title = "活体检测", onBack = onBack, scrollable = false) {
        message?.let { BusinessNotice(it, isError = true) }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            livenessUrl.isNullOrBlank() -> OutlinedButton(
                modifier = Modifier.fillMaxWidth(), onClick = onBack,
            ) { Text("返回后重试") }
            !cameraGranted -> {
                IdentityHero("脸", "需要使用相机", "相机画面仅用于本次活体检测")
                Button(modifier = Modifier.fillMaxWidth(), onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("允许相机并继续")
                }
            }
            else -> {
                SecureLivenessWebView(
                    url = requireNotNull(livenessUrl),
                    returnUrl = returnUrl,
                    onReturned = { refresh() },
                    onUnsafeNavigation = { message = "检测页面跳转异常，请返回后重试" },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { refresh() }) {
                    Text("我已完成，查看结果")
                }
            }
        }
    }
}

@Composable
private fun IdentityResultScreen(
    api: ContractIdentityApi,
    accessToken: String,
    initial: IdentitySessionResource,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onRetryReady: (IdentitySessionResource) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember(initial.id) { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRetry by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        scope.launch {
            when (val result = api.session(accessToken, session.id)) {
                is IdentityCallResult.Success -> { session = result.session; message = null }
                is IdentityCallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    else message = result.businessMessage("认证结果查询失败，请稍后重试")
                }
            }
            loading = false
        }
    }

    if (confirmRetry) AlertDialog(
        onDismissRequest = { confirmRetry = false },
        title = { Text("重新进行实名认证？") },
        text = { Text("当前认证记录将保留，新流程需要再次完成活体检测。") },
        confirmButton = {
            TextButton(onClick = {
                confirmRetry = false
                loading = true
                val key = retryKey ?: UUID.randomUUID().toString().also { retryKey = it }
                scope.launch {
                    when (val result = api.retry(
                        accessToken,
                        session.id,
                        key,
                        IdentityRetrySessionRequest(reason = "USER_RETRY", expectedVersion = session.version),
                    )) {
                        is IdentityCallResult.Success -> { retryKey = null; onRetryReady(result.session) }
                        is IdentityCallResult.Failure -> {
                            if (result.statusCode == 401) onSessionExpired()
                            else message = result.businessMessage("暂时无法重新认证，请稍后重试")
                        }
                    }
                    loading = false
                }
            }) { Text("重新认证") }
        },
        dismissButton = { TextButton(onClick = { confirmRetry = false }) { Text("暂不") } },
    )

    IdentityPage(title = "认证结果", onBack = onBack) {
        val kind = session.resultKind()
        IdentityHero(
            symbol = when (kind) {
                IdentityResultKind.SUCCESS -> "✓"
                IdentityResultKind.FAILED -> "!"
                else -> "…"
            },
            title = session.businessStatusText(),
            description = when (kind) {
                IdentityResultKind.SUCCESS -> "你已可以使用需要实名认证的功能"
                IdentityResultKind.FAILED -> "请核对本人信息后重新进行认证"
                IdentityResultKind.PENDING -> "结果更新后会在这里显示"
                else -> "请稍候刷新确认最终结果"
            },
        )
        message?.let { BusinessNotice(it, isError = true) }
        if (kind in setOf(IdentityResultKind.PENDING, IdentityResultKind.UNKNOWN, IdentityResultKind.READY)) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = { refresh() }) {
                Text(if (loading) "正在刷新" else "刷新结果")
            }
        }
        if (kind == IdentityResultKind.FAILED) {
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = { confirmRetry = true }) {
                Text("重新认证")
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("返回我的") }
    }
}

@Composable
private fun SecureLivenessWebView(
    url: String,
    returnUrl: String,
    onReturned: () -> Unit,
    onUnsafeNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val callback = remember(returnUrl) { Uri.parse(returnUrl) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        val allowed = request.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                        if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (target.scheme != "https") {
                            onUnsafeNavigation()
                            return true
                        }
                        if (target.host == callback.host && target.path == callback.path) {
                            onReturned()
                            return true
                        }
                        return false
                    }
                }
                loadUrl(url)
            }
        },
        update = { view -> if (view.url != url) view.loadUrl(url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityPage(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = HhyColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        val base = Modifier.fillMaxSize().padding(padding).padding(HhySpacing.Lg)
        Column(
            modifier = if (scrollable) base.verticalScroll(rememberScrollState()) else base,
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Lg),
            content = content,
        )
    }
}

@Composable
private fun IdentityHero(symbol: String, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = HhySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
    ) {
        Card(
            shape = RoundedCornerShape(HhyRadius.LargeCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.SoftBlue),
        ) { Text(symbol, modifier = Modifier.padding(HhySpacing.Xxl), color = HhyColors.BrandPrimary, fontWeight = FontWeight.Bold) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, color = HhyColors.TextSecondary)
    }
}

@Composable
private fun IdentityCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            content = content,
        )
    }
}

@Composable
private fun BusinessNotice(message: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isError) HhyColors.Surface else HhyColors.SoftBlue),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            color = if (isError) HhyColors.Error else HhyColors.TextPrimary,
        )
    }
}
