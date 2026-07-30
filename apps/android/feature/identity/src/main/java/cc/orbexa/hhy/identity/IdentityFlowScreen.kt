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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyMotion
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContractIdentityApi
import cc.orbexa.hhy.network.IdentityCallResult
import cc.orbexa.hhy.network.IdentityConsentCallResult
import cc.orbexa.hhy.network.IdentityConsentResource
import cc.orbexa.hhy.network.IdentityCreateLivenessTokenRequest
import cc.orbexa.hhy.network.IdentityCreateSessionRequest
import cc.orbexa.hhy.network.IdentityOverviewCallResult
import cc.orbexa.hhy.network.IdentityOverviewResource
import cc.orbexa.hhy.network.IdentityRetrySessionRequest
import cc.orbexa.hhy.network.IdentitySessionResource
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private sealed interface IdentityRoute {
    @Serializable data object Home : IdentityRoute
    @Serializable data object Form : IdentityRoute
    @Serializable data class Liveness(val sessionId: String) : IdentityRoute
    @Serializable data class Provider(val sessionId: String) : IdentityRoute
    @Serializable data class Result(val sessionId: String) : IdentityRoute
}

@Composable
fun IdentityFlowScreen(
    api: ContractIdentityApi,
    accessToken: String,
    returnUrl: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val navController = rememberNavController()
    val sessionCache = remember { mutableStateMapOf<String, IdentitySessionResource>() }
    var overviewRevision by remember { mutableStateOf(0) }
    NavHost(
        navController = navController,
        startDestination = IdentityRoute.Home,
        enterTransition = { HhyMotion.forwardEnter() },
        exitTransition = { HhyMotion.forwardExit() },
        popEnterTransition = { HhyMotion.backwardEnter() },
        popExitTransition = { HhyMotion.backwardExit() },
    ) {
        composable<IdentityRoute.Home> {
            IdentityHomeScreen(
                api = api,
                accessToken = accessToken,
                refreshKey = overviewRevision,
                onBack = onBack,
                onStart = { navController.navigate(IdentityRoute.Form) },
                onContinue = { session ->
                    sessionCache[session.id] = session
                    val route = if (session.status in setOf("PROVIDER_PROCESSING", "MANUAL_REVIEW")) {
                        IdentityRoute.Result(session.id)
                    } else {
                        IdentityRoute.Liveness(session.id)
                    }
                    navController.navigate(route)
                },
                onSessionExpired = onSessionExpired,
            )
        }
        composable<IdentityRoute.Form> {
            IdentityFormScreen(
                api = api,
                accessToken = accessToken,
                onBack = { overviewRevision += 1; navController.popBackStack() },
                onSessionExpired = onSessionExpired,
                onCreated = { session ->
                    sessionCache[session.id] = session
                    navController.navigate(IdentityRoute.Liveness(session.id))
                },
            )
        }
        composable<IdentityRoute.Liveness> { entry ->
            val route = entry.toRoute<IdentityRoute.Liveness>()
            IdentitySessionDestination(
                title = "活体检测",
                api = api,
                accessToken = accessToken,
                sessionId = route.sessionId,
                cached = sessionCache[route.sessionId],
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
                onLoaded = { sessionCache[it.id] = it },
            ) { session ->
                IdentityLivenessScreen(
                    api = api,
                    accessToken = accessToken,
                    initial = session,
                    returnUrl = returnUrl,
                    onBack = {
                        overviewRevision += 1
                        navController.popBackStack(IdentityRoute.Home, inclusive = false)
                    },
                    onSessionExpired = onSessionExpired,
                    onLaunchProvider = {
                        sessionCache[it.id] = it
                        navController.navigate(IdentityRoute.Provider(it.id))
                    },
                    onResult = {
                        sessionCache[it.id] = it
                        navController.navigate(IdentityRoute.Result(it.id)) {
                            popUpTo(IdentityRoute.Liveness(it.id)) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable<IdentityRoute.Provider> { entry ->
            val route = entry.toRoute<IdentityRoute.Provider>()
            IdentitySessionDestination(
                title = "活体检测",
                api = api,
                accessToken = accessToken,
                sessionId = route.sessionId,
                cached = sessionCache[route.sessionId],
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
                onLoaded = { sessionCache[it.id] = it },
            ) { session ->
                IdentityProviderH5Screen(
                    api = api,
                    accessToken = accessToken,
                    initial = session,
                    returnUrl = returnUrl,
                    onBack = { navController.popBackStack() },
                    onSessionExpired = onSessionExpired,
                    onResult = {
                        sessionCache[it.id] = it
                        navController.navigate(IdentityRoute.Result(it.id)) {
                            popUpTo(IdentityRoute.Liveness(it.id)) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable<IdentityRoute.Result> { entry ->
            val route = entry.toRoute<IdentityRoute.Result>()
            IdentitySessionDestination(
                title = "认证结果",
                api = api,
                accessToken = accessToken,
                sessionId = route.sessionId,
                cached = sessionCache[route.sessionId],
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
                onLoaded = { sessionCache[it.id] = it },
            ) { session ->
                IdentityResultScreen(
                    api = api,
                    accessToken = accessToken,
                    initial = session,
                    onBack = { navController.popBackStack() },
                    onPrimaryAction = { kind ->
                        if (kind == IdentityResultKind.FAILED) {
                            navController.popBackStack(IdentityRoute.Home, inclusive = false)
                        } else {
                            overviewRevision += 1
                            onBack()
                        }
                    },
                    onSessionExpired = onSessionExpired,
                    onRetryReady = {
                        sessionCache[it.id] = it
                        navController.navigate(IdentityRoute.Liveness(it.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun IdentitySessionDestination(
    title: String,
    api: ContractIdentityApi,
    accessToken: String,
    sessionId: String,
    cached: IdentitySessionResource?,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onLoaded: (IdentitySessionResource) -> Unit,
    content: @Composable (IdentitySessionResource) -> Unit,
) {
    var session by remember(sessionId) { mutableStateOf(cached) }
    var loading by remember(sessionId) { mutableStateOf(cached == null) }
    var message by remember(sessionId) { mutableStateOf<String?>(null) }
    var reload by remember(sessionId) { mutableStateOf(0) }

    LaunchedEffect(sessionId, reload) {
        if (session != null) return@LaunchedEffect
        loading = true
        when (val result = api.session(accessToken, sessionId)) {
            is IdentityCallResult.Success -> {
                session = result.session
                onLoaded(result.session)
                message = null
            }
            is IdentityCallResult.Failure -> {
                if (result.statusCode == 401) onSessionExpired()
                else message = result.businessMessage("认证信息暂时无法加载，请稍后重试")
            }
        }
        loading = false
    }

    val currentSession = session
    if (currentSession != null) {
        content(currentSession)
    } else {
        IdentityPage(title = title, onBack = onBack) {
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            message?.let { BusinessNotice(it, isError = true) }
            Button(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                onClick = { message = null; reload += 1 },
            ) {
                HhyIcon(HhyIcons.Refresh, contentDescription = null)
                Spacer(Modifier.width(HhySpacing.Sm))
                Text("重新加载")
            }
        }
        }
    }
}

@Composable
internal fun IdentityHomeScreen(
    api: ContractIdentityApi,
    accessToken: String,
    refreshKey: Int,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onContinue: (IdentitySessionResource) -> Unit,
    onSessionExpired: () -> Unit,
) {
    var overview by remember(accessToken) { mutableStateOf<IdentityOverviewResource?>(null) }
    var loading by remember(accessToken) { mutableStateOf(true) }
    var message by remember(accessToken) { mutableStateOf<String?>(null) }
    var reload by remember(accessToken) { mutableStateOf(0) }

    LaunchedEffect(accessToken, refreshKey, reload) {
        loading = true
        when (val result = api.overview(accessToken)) {
            is IdentityOverviewCallResult.Success -> {
                overview = result.overview
                message = null
            }
            is IdentityOverviewCallResult.Failure -> {
                if (result.failure.statusCode == 401) onSessionExpired()
                else message = result.failure.businessMessage("认证状态暂时无法加载，请稍后重试")
            }
        }
        loading = false
    }

    IdentityPage(title = "实名认证", onBack = onBack) {
        val current = overview
        val active = current?.activeSession
        val verified = current?.status == "VERIFIED"
        val inProgress = current?.status == "IN_PROGRESS" && active != null
        IdentityStatusCard(
            title = when {
                loading -> "正在确认认证状态"
                verified -> "实名认证已完成"
                inProgress -> if (active.status == "MANUAL_REVIEW") "实名认证审核中" else "实名认证进行中"
                else -> "尚未完成实名认证"
            },
            description = when {
                loading -> "请稍候"
                verified -> "认证信息已通过核验，账号实名状态正常"
                inProgress -> if (active.status == "MANUAL_REVIEW") "资料正在审核，无需重复提交" else "继续完成当前认证流程"
                else -> "完成认证后可提升账号可信度，并使用需要实名的业务能力"
            },
            icon = if (verified) HhyIcons.Check else if (inProgress) HhyIcons.Pending else HhyIcons.Shield,
        )
        message?.let { BusinessNotice(it, isError = true) }
        when {
            verified -> IdentityCard {
                Text("认证信息", style = MaterialTheme.typography.titleMedium)
                IdentityInfoRow("当前状态", "实名认证已完成")
                IdentityInfoRow("资料保护", "认证资料不可自行修改")
            }
            inProgress -> IdentityCard {
                Text("认证进度", style = MaterialTheme.typography.titleMedium)
                IdentityInfoRow(
                    "当前状态",
                    if (active.status == "MANUAL_REVIEW") "正在人工审核" else "等待继续认证",
                )
                IdentityInfoRow("资料状态", "已安全保存")
            }
            else -> IdentityCard {
                Text("认证前请准备", style = MaterialTheme.typography.titleMedium)
                IdentityRequirement(HhyIcons.Check, "本人有效身份证件", "请填写与证件一致的真实信息")
                IdentityRequirement(HhyIcons.Camera, "可正常使用的手机相机", "活体检测需要使用前置相机")
                IdentityRequirement(HhyIcons.Shield, "由账号本人完成检测", "请勿由他人代为操作")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
            shape = RoundedCornerShape(HhyRadius.Button),
            enabled = !loading,
            onClick = {
                when {
                    message != null -> reload += 1
                    verified -> onBack()
                    active != null -> onContinue(active)
                    else -> onStart()
                }
            },
        ) {
            Text(
                when {
                    message != null -> "重新加载"
                    verified -> "返回我的"
                    active != null -> if (active.status == "MANUAL_REVIEW") "查看审核进度" else "继续认证"
                    else -> "开始认证"
                },
            )
        }
        Text(
            "身份信息将按照隐私政策用于完成实名认证",
            modifier = Modifier.fillMaxWidth(),
            color = HhyColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun IdentityFormScreen(
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
        Text(
            "信息提交后不可自行修改，请仔细核对。",
            color = HhyColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        message?.let { BusinessNotice(it, isError = true) }
        IdentityCard {
            OutlinedTextField(
                value = realName,
                onValueChange = { realName = it; clearIntent() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("真实姓名") },
                placeholder = { Text("请输入本人真实姓名") },
                singleLine = true,
                shape = RoundedCornerShape(HhyRadius.Input),
                isError = errors.realName != null,
                supportingText = errors.realName?.let { value -> ({ Text(value) }) },
            )
            OutlinedTextField(
                value = idNumber,
                onValueChange = { idNumber = it; clearIntent() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("身份证号") },
                placeholder = { Text("请输入本人身份证号") },
                singleLine = true,
                shape = RoundedCornerShape(HhyRadius.Input),
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
                Text("我已阅读并同意", color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    enabled = consent != null && !consentLoading,
                    onClick = { showConsent = true },
                    modifier = Modifier.weight(1f),
                ) { Text("《${consent?.title ?: "实名认证授权说明"}》") }
            }
        }
        if (consentLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
                Text("正在加载授权说明", modifier = Modifier.padding(start = HhySpacing.Sm))
            }
        } else if (consent == null) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { consentReload += 1 },
            ) { Text("重新加载授权说明") }
        }
        Button(
            modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
            shape = RoundedCornerShape(HhyRadius.Button),
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
            if (submitting) CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress), color = HhyColors.TextInverse)
            else Text("提交并开始活体检测")
        }
        Text(
            "请确认姓名和证件号码准确无误",
            modifier = Modifier.fillMaxWidth(),
            color = HhyColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
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
internal fun IdentityLivenessScreen(
    api: ContractIdentityApi,
    accessToken: String,
    initial: IdentitySessionResource,
    returnUrl: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onLaunchProvider: (IdentitySessionResource) -> Unit,
    onResult: (IdentitySessionResource) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember(initial.id) { mutableStateOf(initial) }
    var livenessUrl by remember(initial.id) { mutableStateOf(initial.livenessUrl) }
    var message by remember(initial.id) { mutableStateOf<String?>(null) }
    var loading by remember(initial.id) { mutableStateOf(livenessUrl.isNullOrBlank()) }
    var tokenRevision by remember(initial.id) { mutableStateOf(0) }
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

    LaunchedEffect(initial.id, tokenRevision) {
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
        IdentityStepTag(if (message == null) "步骤 2 / 2" else "需要重新检测", message != null)
        when {
            loading -> {
                LivenessViewport(dark = false) {
                    CircularProgressIndicator(modifier = Modifier.size(HhySize.StandardProgress))
                    Text("正在启动安全检测", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                    onClick = onBack,
                ) { Text("取消检测") }
            }
            livenessUrl.isNullOrBlank() -> {
                LivenessViewport(error = true) {
                    Text("!", color = HhyColors.Error, style = MaterialTheme.typography.headlineSmall)
                    Text("检测未完成", color = HhyColors.Error, style = MaterialTheme.typography.titleMedium)
                    Text(message ?: "请调整光线和距离后重试", color = HhyColors.Error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(HhySize.PrimaryButtonHeight),
                        onClick = onBack,
                    ) { Text("返回") }
                    Button(
                        modifier = Modifier.weight(2f).height(HhySize.PrimaryButtonHeight),
                        onClick = {
                            message = null
                            loading = true
                            tokenRevision += 1
                        },
                    ) { Text("重新检测") }
                }
            }
            !cameraGranted -> {
                LivenessViewport {
                    HhyIcon(
                        HhyIcons.Face,
                        contentDescription = "人脸活体检测",
                        modifier = Modifier.size(HhySize.MinimumTouchTarget),
                        tint = HhyColors.BrandPrimary,
                    )
                    Text("准备开始活体检测", style = MaterialTheme.typography.titleMedium)
                    Text("请在光线充足、环境安静的位置完成检测", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                }
                IdentityCard {
                    Text("检测前请确认", style = MaterialTheme.typography.titleMedium)
                    IdentityRequirement(HhyIcons.Face, "保持面部清晰可见", "请摘下口罩、帽子或遮挡物")
                    IdentityRequirement(HhyIcons.Camera, "正对屏幕完成动作", "根据页面提示缓慢完成")
                }
                Button(
                    modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                ) {
                    Text("允许相机并继续")
                }
            }
            else -> {
                LivenessViewport {
                    HhyIcon(
                        HhyIcons.Face,
                        contentDescription = "人脸活体检测",
                        modifier = Modifier.size(HhySize.MinimumTouchTarget),
                        tint = HhyColors.BrandPrimary,
                    )
                    Text("已准备好开始检测", style = MaterialTheme.typography.titleMedium)
                    Text("下一步将进入安全检测页面", style = MaterialTheme.typography.bodySmall, color = HhyColors.TextSecondary)
                }
                BusinessNotice("请按检测页面提示完成动作，完成后将自动返回。", isError = false)
                Button(
                    modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                    onClick = { onLaunchProvider(session.copy(livenessUrl = livenessUrl)) },
                ) {
                    Text("开始检测")
                }
            }
        }
    }
}

@Composable
private fun IdentityProviderH5Screen(
    api: ContractIdentityApi,
    accessToken: String,
    initial: IdentitySessionResource,
    returnUrl: String,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onResult: (IdentitySessionResource) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember(initial.id) { mutableStateOf(initial) }
    var message by remember(initial.id) { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            when (val result = api.session(accessToken, session.id)) {
                is IdentityCallResult.Success -> {
                    session = result.session
                    message = null
                    if (result.session.status == "MANUAL_REVIEW" ||
                        result.session.resultKind() !in setOf(IdentityResultKind.READY, IdentityResultKind.PENDING)
                    ) {
                        onResult(result.session)
                    }
                }
                is IdentityCallResult.Failure -> {
                    if (result.statusCode == 401) onSessionExpired()
                    else message = result.businessMessage("认证结果暂时无法确认，请稍后重试")
                }
            }
        }
    }

    LaunchedEffect(session.id) {
        while (true) {
            delay(3_000)
            when (val result = api.session(accessToken, session.id)) {
                is IdentityCallResult.Success -> {
                    session = result.session
                    if (result.session.status == "MANUAL_REVIEW" ||
                        result.session.resultKind() !in setOf(IdentityResultKind.READY, IdentityResultKind.PENDING)
                    ) {
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

    if (message != null || session.livenessUrl.isNullOrBlank()) {
        IdentityPage(title = "活体检测", onBack = onBack, scrollable = false) {
            LivenessViewport(error = true) {
                HhyIcon(HhyIcons.Error, contentDescription = null, tint = HhyColors.Error)
                Text("检测页面暂时无法继续", color = HhyColors.Error, style = MaterialTheme.typography.titleMedium)
                Text(message ?: "请返回后重试", color = HhyColors.Error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                onClick = { message = null; refresh() },
            ) { Text("重新确认") }
        }
    } else {
        IdentityProviderWebPage(
            url = requireNotNull(session.livenessUrl),
            returnUrl = returnUrl,
            onBack = onBack,
            onReturned = { refresh() },
            onUnsafeNavigation = { message = "检测页面跳转异常，请返回后重试" },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityProviderWebPage(
    url: String,
    returnUrl: String,
    onBack: () -> Unit,
    onReturned: () -> Unit,
    onUnsafeNavigation: () -> Unit,
) {
    Scaffold(
        containerColor = HhyColors.LivenessDark,
        topBar = {
            TopAppBar(
                title = { Text("活体检测", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { HhyBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HhyColors.Surface),
            )
        },
    ) { padding ->
        SecureLivenessWebView(
            url = url,
            returnUrl = returnUrl,
            onReturned = onReturned,
            onUnsafeNavigation = onUnsafeNavigation,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
internal fun IdentityResultScreen(
    api: ContractIdentityApi,
    accessToken: String,
    initial: IdentitySessionResource,
    onBack: () -> Unit,
    onPrimaryAction: (IdentityResultKind) -> Unit,
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
        IdentityStatusCard(
            title = session.businessStatusText(),
            description = when (kind) {
                IdentityResultKind.SUCCESS -> "认证信息已通过核验，账号实名状态正常"
                IdentityResultKind.FAILED -> "本次认证未能完成，请按提示处理"
                IdentityResultKind.PENDING -> "认证结果正在确认，请耐心等待"
                else -> "请稍候刷新确认认证结果"
            },
            icon = if (kind == IdentityResultKind.SUCCESS) HhyIcons.Check else if (kind == IdentityResultKind.FAILED) HhyIcons.Error else HhyIcons.Pending,
            tone = when {
                kind == IdentityResultKind.FAILED -> IdentityTone.Error
                session.status == "MANUAL_REVIEW" || session.status == "EXPIRED" -> IdentityTone.Warning
                else -> IdentityTone.Brand
            },
        )
        message?.let { BusinessNotice(it, isError = true) }
        if (kind != IdentityResultKind.FAILED) {
            IdentityCard {
                Text("认证信息", style = MaterialTheme.typography.titleMedium)
                IdentityInfoRow("当前状态", session.businessStatusText())
                IdentityInfoRow("资料保护", "认证资料不可自行修改")
            }
        }
        if (kind in setOf(IdentityResultKind.PENDING, IdentityResultKind.UNKNOWN, IdentityResultKind.READY)) {
            IdentityActionCard(
                title = if (session.status == "MANUAL_REVIEW") "审核进度" else "刷新认证状态",
                description = if (session.status == "MANUAL_REVIEW") "审核完成后将显示最新结果" else "查看最新核验结果",
                enabled = !loading,
                onClick = { refresh() },
            )
            BusinessNotice(
                if (session.status == "MANUAL_REVIEW") "审核结果更新后会在本页显示。" else "核验期间无需重复提交。",
                isError = false,
                tone = if (session.status == "MANUAL_REVIEW") IdentityTone.Warning else IdentityTone.Brand,
            )
        }
        if (kind == IdentityResultKind.SUCCESS) {
            IdentityActionCard("认证状态", "实名认证已通过", enabled = false, onClick = {})
            Text(
                "认证信息不可自行修改，如有疑问请联系客服",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = HhyColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (kind == IdentityResultKind.FAILED) {
            IdentityCard {
                Text(if (session.status == "EXPIRED") "为什么会失效" else "未通过原因", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (session.status == "EXPIRED") "认证流程已超时，请重新开始。" else "请核对本人信息并确保相机与网络可正常使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HhyColors.TextSecondary,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
                enabled = !loading,
                onClick = { confirmRetry = true },
            ) {
                Text("重新认证")
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().height(HhySize.PrimaryButtonHeight),
            onClick = { onPrimaryAction(kind) },
        ) { Text(if (kind == IdentityResultKind.FAILED) "返回实名认证" else "返回我的") }
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
                tag = url
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
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
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
                title = {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = { HhyBackButton(onClick = onBack) },
                actions = { Spacer(Modifier.width(HhySize.MinimumTouchTarget)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HhyColors.Surface),
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

private enum class IdentityTone { Brand, Warning, Error }

@Composable
private fun IdentityStatusCard(
    title: String,
    description: String,
    icon: ImageVector,
    tone: IdentityTone = IdentityTone.Brand,
) {
    val colors = when (tone) {
        IdentityTone.Brand -> listOf(HhyColors.BrandPrimary, HhyColors.BrandGradientEnd)
        IdentityTone.Warning -> listOf(HhyColors.Warning, HhyColors.RewardGold)
        IdentityTone.Error -> listOf(HhyColors.Error, HhyColors.RewardOrange)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HhyRadius.LargeCard))
            .background(Brush.horizontalGradient(colors))
            .padding(HhySpacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(title, color = HhyColors.TextInverse, style = MaterialTheme.typography.titleMedium)
            Text(description, color = HhyColors.TextInverse, style = MaterialTheme.typography.bodySmall)
        }
        HhyIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(HhySize.StandardProgress),
            tint = HhyColors.TextInverse,
        )
    }
}

@Composable
private fun IdentityCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = cc.orbexa.hhy.designsystem.HhyElevation.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
            content = content,
        )
    }
}

@Composable
private fun IdentityRequirement(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        Box(
            modifier = Modifier.size(HhySize.MinimumTouchTarget).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.SoftBlue),
            contentAlignment = Alignment.Center,
        ) {
            HhyIcon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(HhySize.StandardProgress),
                tint = HhyColors.BrandPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IdentityStepTag(text: String, error: Boolean) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HhyRadius.Pill))
            .background(if (error) HhyColors.ErrorSoft else HhyColors.SoftBlue)
            .padding(horizontal = HhySpacing.Md, vertical = HhySpacing.Xs),
        color = if (error) HhyColors.Error else HhyColors.BrandPrimary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LivenessViewport(
    dark: Boolean = false,
    error: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = when {
        error -> HhyColors.ErrorSoft
        dark -> HhyColors.LivenessDark
        else -> HhyColors.SoftBlue
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.size(HhySize.IdentityLivenessFrame)
                .clip(RoundedCornerShape(HhyRadius.Dialog))
                .background(background)
                .border(HhySize.Hairline, if (error) HhyColors.Error else HhyColors.BrandPrimary, RoundedCornerShape(HhyRadius.Dialog))
                .padding(HhySpacing.Lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun IdentityInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = HhyColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IdentityActionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(HhyRadius.LargeCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = cc.orbexa.hhy.designsystem.HhyElevation.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Box(
                modifier = Modifier.size(HhySize.MinimumTouchTarget).clip(RoundedCornerShape(HhyRadius.Tag)).background(HhyColors.SoftBlue),
                contentAlignment = Alignment.Center,
            ) {
                HhyIcon(
                    HhyIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(HhySize.StandardProgress),
                    tint = HhyColors.BrandPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, color = HhyColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            HhyIcon(HhyIcons.ChevronRight, contentDescription = null, tint = HhyColors.TextSecondary)
        }
    }
}

@Composable
private fun BusinessNotice(message: String, isError: Boolean, tone: IdentityTone = IdentityTone.Brand) {
    val background = when {
        isError || tone == IdentityTone.Error -> HhyColors.ErrorSoft
        tone == IdentityTone.Warning -> HhyColors.WarningSoft
        else -> HhyColors.SoftBlue
    }
    val foreground = when {
        isError || tone == IdentityTone.Error -> HhyColors.Error
        tone == IdentityTone.Warning -> HhyColors.Warning
        else -> HhyColors.BrandPrimary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HhyRadius.Tag),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(HhySpacing.Lg),
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
