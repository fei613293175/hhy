package cc.orbexa.hhy.teamleader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cc.orbexa.hhy.designsystem.HhyBackButton
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.ContractR11Api
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08DirectConversationRequest
import cc.orbexa.hhy.network.R08FavoriteRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R11TeamLeaderDetailScreen(
    api: ContractR11Api,
    accessToken: String,
    teamLeaderId: String,
    currentUserId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onConversationReady: (String, Long, ContentResource) -> Unit,
    onShare: (String) -> Unit = {},
    onInvalidFeedback: (String, List<String>) -> Unit = { _, _ -> },
    onSessionExpired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keys = remember { R11IntentKeys() }
    var content by remember { mutableStateOf<ContentResource?>(null) }
    var phase by remember { mutableStateOf(R11TeamLeaderPhase.LOADING) }
    var failure by remember { mutableStateOf<R11TeamLeaderFailure?>(null) }
    var actionState by remember { mutableStateOf(R11TeamLeaderActionState()) }
    var revealedContact by remember { mutableStateOf<Pair<String, String>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun fail(result: R07CallResult.Failure) {
        if (result.statusCode == 401) onSessionExpired()
        failure = result.toR11TeamLeaderFailure()
        phase = failure!!.phase
    }

    fun failAction(result: R07CallResult.Failure) {
        if (result.statusCode == 401) onSessionExpired()
        notice = when (result.statusCode) {
            403 -> "当前账号无权执行此操作"
            409 -> "内容已更新，请刷新后重试"
            else -> "操作未完成，请稍后重试"
        }
    }

    fun launchAction(action: String, block: suspend () -> Unit) {
        val started = actionState.begin(action) ?: return
        actionState = started
        scope.launch {
            try {
                block()
            } finally {
                actionState = actionState.finish(action)
            }
        }
    }

    fun load() {
        scope.launch {
            phase = R11TeamLeaderPhase.LOADING
            when (val result = api.teamLeader(accessToken, teamLeaderId)) {
                is R07CallResult.Success -> {
                    if (result.data.contentType != "TEAM_LEADER") {
                        phase = R11TeamLeaderPhase.NOT_FOUND
                    } else {
                        content = result.data
                        failure = null
                        phase = R11TeamLeaderPhase.CONTENT
                    }
                }
                is R07CallResult.Failure -> fail(result)
            }
        }
    }

    fun reveal(channel: String) {
        val fingerprint = "$teamLeaderId:$channel"
        launchAction("contact:$channel") {
            when (val result = api.accessContact(accessToken, teamLeaderId, channel, keys.forBody("contact", fingerprint))) {
                is R07CallResult.Success -> {
                    keys.consume("contact", fingerprint)
                    revealedContact = result.data.channel to result.data.value
                }
                is R07CallResult.Failure -> failAction(result)
            }
        }
    }

    LaunchedEffect(teamLeaderId) { load() }

    revealedContact?.let { (channel, value) ->
        ModalBottomSheet(onDismissRequest = { revealedContact = null }) {
            Column(
                Modifier.fillMaxWidth().padding(HhySpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
            ) {
                Text(contactLabel(channel), style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(value, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        copyText(context, "团队联系方式", value)
                        revealedContact = null
                        notice = "联系方式已复制"
                    },
                ) { Text("复制联系方式") }
                Text("联系方式仅用于本次主动联系，请勿向无关人员转发", color = HhyColors.TextSecondary)
            }
        }
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true }
            .testTag("hhy.screen.r11.team_leader.detail.${phase.name.lowercase()}"),
        topBar = {
            TopAppBar(
                title = { Text("团队长详情") },
                navigationIcon = { HhyBackButton(onBack) },
                actions = {
                    if (content?.publisher?.userId == currentUserId) {
                        TextButton(onClick = { onEdit(teamLeaderId) }) { Text("编辑") }
                    }
                    TextButton(enabled = content?.contactsMasked?.isNotEmpty() == true && phase == R11TeamLeaderPhase.CONTENT && !actionState.busy, onClick = { content?.let { onInvalidFeedback(it.title, it.contactsMasked.map { contact -> contact.channel }) } }) { Text("反馈") }
                    TextButton(enabled = content != null && phase == R11TeamLeaderPhase.CONTENT && !actionState.busy, onClick = { content?.title?.let(onShare) }) { Text("分享") }
                },
            )
        },
        bottomBar = {
            content?.takeIf { phase == R11TeamLeaderPhase.CONTENT }?.let { item ->
                TeamLeaderActions(
                    canContact = item.contactsMasked.any { it.available },
                    canChat = item.publisher?.userId != null && R11TeamLeaderFacts.from(item).acceptPrivateChat != false,
                    enabled = !actionState.busy,
                    onFavorite = {
                        val fingerprint = "${item.id}:${item.version}"
                        launchAction("favorite") {
                            when (val result = api.favorite(accessToken, item.id, keys.forBody("favorite", fingerprint), R08FavoriteRequest(expectedVersion = item.version))) {
                                is R07CallResult.Success -> {
                                    content = result.data
                                    keys.consume("favorite", fingerprint)
                                    notice = "已收藏"
                                }
                                is R07CallResult.Failure -> failAction(result)
                            }
                        }
                    },
                    onContact = { item.contactsMasked.firstOrNull { it.available }?.channel?.let(::reveal) },
                    onChat = {
                        val peer = item.publisher?.userId ?: return@TeamLeaderActions
                        val fingerprint = "$peer:${item.id}"
                        launchAction("direct") {
                            when (val result = api.direct(accessToken, keys.forBody("direct", fingerprint), R08DirectConversationRequest(peer, item.id))) {
                                is R07CallResult.Success -> {
                                    keys.consume("direct", fingerprint)
                                    notice = "私聊会话已准备"
                                    onConversationReady(result.data.id, result.data.version, item)
                                }
                                is R07CallResult.Failure -> failAction(result)
                            }
                        }
                    },
                )
            }
        },
        containerColor = HhyColors.PageBackground,
    ) { padding ->
        when (phase) {
            R11TeamLeaderPhase.LOADING, R11TeamLeaderPhase.SUBMITTING ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            R11TeamLeaderPhase.CONTENT -> content?.let { item ->
                val facts = R11TeamLeaderFacts.from(item)
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
                ) {
                    item { TeamLeaderHero(item, facts) }
                    notice?.let { text -> item { NoticeCard(text) } }
                    if (item.media.isNotEmpty()) item { TeamLeaderMedia(item) }
                    facts.detailSections(item).forEach { (title, values) ->
                        item(title) { TeamLeaderSection(title) { values.forEach { Text(it) } } }
                    }
                    item { ContactSection(item, !actionState.busy, ::reveal) }
                    item { PublisherSection(item) }
                    item { StatisticsSection(item) }
                    item { Text(item.updatedAt?.let { "最近更新 $it" } ?: item.createdAt?.let { "发布于 $it" }.orEmpty(), color = HhyColors.TextSecondary) }
                }
            }
            else -> DetailFailure(phase, ::load, onBack)
        }
    }
}

@Composable
private fun TeamLeaderActions(
    canContact: Boolean,
    canChat: Boolean,
    enabled: Boolean,
    onFavorite: () -> Unit,
    onContact: () -> Unit,
    onChat: () -> Unit,
) {
    Surface(color = HhyColors.Surface, shadowElevation = HhyElevation.Card) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(HhySpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
        ) {
            OutlinedButton(onClick = onFavorite, modifier = Modifier.weight(1f).testTag("r11.team_leader.favorite"), enabled = enabled) { Text("收藏") }
            OutlinedButton(onClick = onContact, modifier = Modifier.weight(1f).testTag("r11.team_leader.contact"), enabled = enabled && canContact) { Text("联系方式") }
            Button(onClick = onChat, modifier = Modifier.weight(1f).testTag("r11.team_leader.chat"), enabled = enabled && canChat) { Text("发起私聊") }
        }
    }
}

@Composable
private fun TeamLeaderHero(item: ContentResource, facts: R11TeamLeaderFacts) = Card(
    shape = RoundedCornerShape(HhyRadius.LargeCard),
    colors = CardDefaults.cardColors(HhyColors.Surface),
    elevation = CardDefaults.cardElevation(HhyElevation.Card),
) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(HhySize.AppLogo).clip(CircleShape).background(HhyColors.SoftBlue), contentAlignment = Alignment.Center) {
                HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary)
                facts.logoUrl?.let { AsyncImage(it, "${facts.teamName}团队标识", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f)) {
                Text(facts.teamName, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                facts.introduction?.let { Text(it, color = HhyColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            facts.tags.forEach { TeamTag(it) }
            item.regionCode?.let { TeamTag(it) }
        }
    }
}

@Composable
private fun TeamLeaderMedia(item: ContentResource) = TeamLeaderSection("团队风采") {
    LazyRow(Modifier.fillMaxWidth().height(HhySize.TopAppBarHeight * 3), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        items(item.media, key = { it.id }) { media -> TeamLeaderImage(media, item.title) }
    }
}

@Composable
private fun TeamLeaderImage(media: MediaItemResource, title: String) {
    val url = (media.thumbnailUrl ?: media.url).takeIf(::secureHttpsMedia)
    Box(
        Modifier.size(HhySize.TopAppBarHeight * 3).clip(RoundedCornerShape(HhyRadius.NormalCard)).background(HhyColors.SoftBlue),
        contentAlignment = Alignment.Center,
    ) {
        HhyIcon(HhyIcons.Groups, null, Modifier.padding(HhySpacing.Lg), HhyColors.BrandPrimary)
        url?.let { AsyncImage(it, media.altText ?: "$title 团队图片", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun ContactSection(item: ContentResource, enabled: Boolean, reveal: (String) -> Unit) = TeamLeaderSection("联系团队") {
    val available = item.contactsMasked.filter { it.available }
    available.forEach { contact ->
        OutlinedButton(onClick = { reveal(contact.channel) }, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
            Text("获取${contactLabel(contact.channel)} ${contact.maskedValue.orEmpty()}")
        }
    }
    if (available.isEmpty()) Text("团队暂未提供可访问的联系方式", color = HhyColors.TextSecondary)
}

@Composable
private fun PublisherSection(item: ContentResource) = TeamLeaderSection("发布信息") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(item.publisher?.nickname ?: "发布者信息未提供", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        item.publisher?.verified?.takeIf { it }?.let { HhyIcon(HhyIcons.Verified, "已认证", tint = HhyColors.BrandPrimary) }
    }
    item.publisher?.bio?.takeIf(String::isNotBlank)?.let { Text(it, color = HhyColors.TextSecondary) }
}

@Composable
private fun StatisticsSection(item: ContentResource) {
    item.statistics?.let { statistics ->
        TeamLeaderSection("内容数据") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("浏览 ${statistics.viewCount}")
                Text("收藏 ${statistics.favoriteCount}")
                Text("分享 ${statistics.shareCount}")
                Text("联系 ${statistics.contactAccessCount}")
            }
        }
    }
}

@Composable
private fun TeamLeaderSection(title: String, content: @Composable () -> Unit) = Card(
    Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(HhyRadius.NormalCard),
    colors = CardDefaults.cardColors(HhyColors.Surface),
) {
    Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun TeamTag(value: String) = Surface(color = HhyColors.SoftBlue, shape = RoundedCornerShape(HhyRadius.Tag)) {
    Text(value, Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs), color = HhyColors.BrandPrimary)
}

@Composable
private fun NoticeCard(value: String) = Surface(color = HhyColors.SuccessSoft, shape = RoundedCornerShape(HhyRadius.NormalCard)) {
    Text(value, Modifier.fillMaxWidth().padding(HhySpacing.Md), color = HhyColors.Success)
}

@Composable
private fun DetailFailure(phase: R11TeamLeaderPhase, retry: () -> Unit, back: () -> Unit) {
    val copy = when (phase) {
        R11TeamLeaderPhase.FORBIDDEN -> "无权访问" to "当前账号没有查看该团队资料的权限"
        R11TeamLeaderPhase.NOT_FOUND -> "团队资料不存在" to "内容可能已删除、下架或链接失效"
        R11TeamLeaderPhase.OFFLINE -> "网络不可用" to "请检查网络连接后重试"
        R11TeamLeaderPhase.CONFLICT -> "资料已经变化" to "请重新加载最新内容后继续"
        else -> "加载失败" to "暂时无法完成请求，请稍后重试"
    }
    Box(Modifier.fillMaxSize().padding(HhySpacing.Lg), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(HhyColors.Surface)) {
            Column(Modifier.padding(HhySpacing.Xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
                Text(copy.first, fontWeight = FontWeight.Bold)
                Text(copy.second, color = HhyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    OutlinedButton(onClick = back) { Text("返回") }
                    if (phase !in setOf(R11TeamLeaderPhase.FORBIDDEN, R11TeamLeaderPhase.NOT_FOUND)) Button(onClick = retry) { Text("重试") }
                }
            }
        }
    }
}

private fun contactLabel(channel: String): String = when (channel) {
    "WECHAT" -> "微信"
    "PHONE" -> "手机号"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    else -> "联系方式"
}

private fun secureHttpsMedia(value: String): Boolean = runCatching {
    val uri = java.net.URI.create(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

private fun copyText(context: Context, label: String, value: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}
