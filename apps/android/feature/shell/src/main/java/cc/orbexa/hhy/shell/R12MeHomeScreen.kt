package cc.orbexa.hhy.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import cc.orbexa.hhy.designsystem.HhyColors
import cc.orbexa.hhy.designsystem.HhyElevation
import cc.orbexa.hhy.designsystem.HhyIcon
import cc.orbexa.hhy.designsystem.HhyIcons
import cc.orbexa.hhy.designsystem.HhyRadius
import cc.orbexa.hhy.designsystem.HhySize
import cc.orbexa.hhy.designsystem.HhySpacing
import cc.orbexa.hhy.designsystem.HhyType
import cc.orbexa.hhy.network.MembershipBenefitResource
import cc.orbexa.hhy.network.MembershipResource
import cc.orbexa.hhy.network.RewardAccountResource
import cc.orbexa.hhy.network.UserSelfResource
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class MeQuickAction(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
fun R12MeHomeScreen(
    user: R12MeModuleState<UserSelfResource>,
    membership: R12MeModuleState<MembershipResource>,
    reward: R12MeModuleState<RewardAccountResource>,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRetryUser: () -> Unit,
    onRetryMembership: () -> Unit,
    onRetryReward: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMyContents: () -> Unit,
    onOpenMyDrafts: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenIdentity: () -> Unit,
    onOpenLoginDevices: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCancellation: () -> Unit,
) {
    val actions = listOf(
        MeQuickAction("个人资料", HhyIcons.Profile, "mine.profile", onOpenProfile),
        MeQuickAction("我的发布", HhyIcons.Publish, "mine.my-contents", onOpenMyContents),
        MeQuickAction("草稿箱", HhyIcons.Applications, "mine.my-drafts", onOpenMyDrafts),
        MeQuickAction("收藏夹", HhyIcons.Check, "mine.favorites", onOpenFavorites),
        MeQuickAction("浏览记录", HhyIcons.Pending, "mine.history", onOpenHistory),
        MeQuickAction("实名认证", HhyIcons.Verified, "mine.identity", onOpenIdentity),
        MeQuickAction("登录设备", HhyIcons.Devices, "mine.devices", onOpenLoginDevices),
        MeQuickAction("修改密码", HhyIcons.Lock, "mine.password", onOpenChangePassword),
        MeQuickAction("关于与更新", HhyIcons.Information, "mine.about", onOpenAbout),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("hhy.screen.r12.me"),
        contentPadding = PaddingValues(
            start = HhySpacing.Lg,
            top = contentPadding.calculateTopPadding() + HhySpacing.Lg,
            end = HhySpacing.Lg,
            bottom = contentPadding.calculateBottomPadding() + HhySpacing.Xl,
        ),
        verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
    ) {
        item {
            MeIdentityHeader(user, onRefreshAll, onOpenProfile)
        }
        item {
            MeRewardSummary(reward, onRetryReward, onOpenIdentity)
        }
        item {
            MeMembershipSummary(membership, onRetryMembership)
        }
        item {
            MeQuickActions(actions)
        }
        item {
            MeBenefits(membership)
        }
        item {
            MeAccountServices(
                onOpenLoginDevices = onOpenLoginDevices,
                onOpenChangePassword = onOpenChangePassword,
                onOpenAbout = onOpenAbout,
                onOpenCancellation = onOpenCancellation,
            )
        }
        if (user.value == null && user.status != R12MeModuleStatus.LOADING) {
            item {
                MeModuleNotice(
                    title = "账号资料暂时无法刷新",
                    message = moduleMessage(R12MeModuleKind.USER, user.status, user.retryAfterSeconds),
                    onRetry = onRetryUser,
                    testTag = "mine.user.retry",
                )
            }
        }
    }
}

@Composable
private fun MeIdentityHeader(
    state: R12MeModuleState<UserSelfResource>,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val user = state.value
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(HhyRadius.LargeCard))
            .clickable(enabled = user != null, onClick = onOpenProfile)
            .testTag("mine.identity-header"),
        color = HhyColors.Surface.copy(alpha = 0f),
        shadowElevation = HhyElevation.Card,
    ) {
        Column(
            modifier = Modifier.background(
                Brush.horizontalGradient(listOf(HhyColors.BrandPrimaryDark, HhyColors.BrandGradientEnd)),
            ).padding(HhySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(HhySpacing.Md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(HhySize.AppLogo).clip(CircleShape),
                    color = HhyColors.Surface,
                    shape = CircleShape,
                ) {
                    val avatar = user?.avatarUrl?.takeIf(::isSecureMeMediaUrl)
                    if (avatar != null) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = "个人头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            HhyIcon(HhyIcons.Profile, null, Modifier.size(HhySpacing.Xxxl), HhyColors.BrandPrimary)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = HhySpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs),
                ) {
                    Text(
                        user?.nickname?.takeIf(String::isNotBlank) ?: if (user == null) "正在加载账号" else "未设置昵称",
                        color = HhyColors.TextInverse,
                        fontSize = HhyType.CardTitleSize,
                        lineHeight = HhyType.CardTitleLineHeight,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    user?.let {
                        Text(
                            "ID: ${it.id}",
                            color = HhyColors.TextInverse.copy(alpha = 0.8f),
                            fontSize = HhyType.CaptionSize,
                            lineHeight = HhyType.CaptionLineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        user?.let {
                            MeStatusPill(accountStatusLabel(it.status))
                            MeStatusPill(identityStatusLabel(it.identityStatus))
                        }
                    }
                }
                TextButton(onClick = onRefresh, modifier = Modifier.testTag("mine.refresh")) {
                    HhyIcon(HhyIcons.Refresh, "刷新我的首页", tint = HhyColors.TextInverse)
                }
            }
            user?.phoneMasked?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    color = HhyColors.TextInverse.copy(alpha = 0.82f),
                    fontSize = HhyType.SecondaryBodySize,
                    lineHeight = HhyType.SecondaryBodyLineHeight,
                )
            }
            if (state.status in setOf(R12MeModuleStatus.STALE, R12MeModuleStatus.RATE_LIMITED)) {
                Text(
                    moduleMessage(R12MeModuleKind.USER, state.status, state.retryAfterSeconds),
                    color = HhyColors.TextInverse,
                    fontSize = HhyType.CaptionSize,
                    lineHeight = HhyType.CaptionLineHeight,
                )
            }
        }
    }
}

@Composable
private fun MeStatusPill(label: String) {
    Surface(shape = RoundedCornerShape(HhyRadius.Pill), color = HhyColors.Surface.copy(alpha = 0.18f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = HhySpacing.Sm, vertical = HhySpacing.Xs),
            color = HhyColors.TextInverse,
            fontSize = HhyType.CaptionSize,
            lineHeight = HhyType.CaptionLineHeight,
        )
    }
}

@Composable
private fun MeRewardSummary(
    state: R12MeModuleState<RewardAccountResource>,
    onRetry: () -> Unit,
    onOpenIdentity: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("mine.reward"),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Md)) {
            MeSectionTitle("奖励资产", state.updatedAt)
            state.value?.let { reward ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    MeAmountCell("可用奖励(元)", reward.availableCent, true, Modifier.weight(1f))
                    MeAmountCell("待结算(元)", reward.pendingCent, true, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                    MeAmountCell("冻结金额", reward.frozenCent, false, Modifier.weight(1f))
                    MeAmountCell("累计提现", reward.withdrawnCent, false, Modifier.weight(1f))
                }
            } ?: MeModuleBody(
                kind = R12MeModuleKind.REWARD,
                status = state.status,
                retryAfterSeconds = state.retryAfterSeconds,
                onRetry = onRetry,
                onIdentity = onOpenIdentity,
            )
            if (state.value != null && state.status !in setOf(R12MeModuleStatus.CONTENT, R12MeModuleStatus.REFRESHING)) {
                MeInlineNotice(moduleMessage(R12MeModuleKind.REWARD, state.status, state.retryAfterSeconds), onRetry)
            }
        }
    }
}

@Composable
private fun MeAmountCell(label: String, cents: Long?, emphasized: Boolean, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.PageBackground) {
        Column(Modifier.padding(HhySpacing.Md), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(label, color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
            Text(
                cents?.let(::formatR12Cent) ?: "--",
                color = if (emphasized) HhyColors.TextPrimary else HhyColors.TextSecondary,
                fontSize = if (emphasized) HhyType.SectionTitleSize else HhyType.BodySize,
                lineHeight = if (emphasized) HhyType.SectionTitleLineHeight else HhyType.BodyLineHeight,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MeMembershipSummary(state: R12MeModuleState<MembershipResource>, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("mine.membership"),
        shape = RoundedCornerShape(HhyRadius.NormalCard),
        colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
            MeSectionTitle("会员状态", state.updatedAt)
            state.value?.let { membership ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                        HhyIcon(HhyIcons.Verified, null, Modifier.padding(HhySpacing.Sm), HhyColors.BrandPrimary)
                    }
                    Column(Modifier.weight(1f).padding(start = HhySpacing.Md)) {
                        Text(
                            membership.name?.takeIf(String::isNotBlank) ?: membershipStatusLabel(membership.status),
                            color = HhyColors.TextPrimary,
                            fontSize = HhyType.CardTitleSize,
                            lineHeight = HhyType.CardTitleLineHeight,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            membership.expiresAt?.let { "有效期至 ${it.r12MeBusinessTimeLabel()}" }
                                ?: membershipStatusLabel(membership.status),
                            color = HhyColors.TextSecondary,
                            fontSize = HhyType.CaptionSize,
                            lineHeight = HhyType.CaptionLineHeight,
                        )
                    }
                }
            } ?: MeModuleBody(R12MeModuleKind.MEMBERSHIP, state.status, state.retryAfterSeconds, onRetry)
            if (state.value != null && state.status !in setOf(R12MeModuleStatus.CONTENT, R12MeModuleStatus.REFRESHING)) {
                MeInlineNotice(moduleMessage(R12MeModuleKind.MEMBERSHIP, state.status, state.retryAfterSeconds), onRetry)
            }
        }
    }
}

@Composable
private fun MeQuickActions(actions: List<MeQuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        MeSectionTitle("常用功能", null)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HhyRadius.NormalCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        ) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Md), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                actions.chunked(4).forEach { rowActions ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                        rowActions.forEach { action ->
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(HhyRadius.Button))
                                    .clickable(onClick = action.onClick).padding(vertical = HhySpacing.Sm)
                                    .testTag(action.testTag),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm),
                            ) {
                                Surface(shape = CircleShape, color = HhyColors.SoftBlue) {
                                    HhyIcon(action.icon, null, Modifier.padding(HhySpacing.Md), HhyColors.BrandPrimary)
                                }
                                Text(
                                    action.label,
                                    color = HhyColors.TextPrimary,
                                    fontSize = HhyType.NavigationSize,
                                    lineHeight = HhyType.NavigationLineHeight,
                                    maxLines = 2,
                                )
                            }
                        }
                        repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeBenefits(state: R12MeModuleState<MembershipResource>) {
    val benefits = state.value?.benefits.orEmpty()
    if (benefits.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        MeSectionTitle("会员权益", null)
        Card(
            modifier = Modifier.fillMaxWidth().testTag("mine.membership-benefits"),
            shape = RoundedCornerShape(HhyRadius.NormalCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        ) {
            Column(Modifier.fillMaxWidth().padding(HhySpacing.Lg), verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
                benefits.take(6).forEach { benefit -> MeBenefitRow(benefit) }
            }
        }
    }
}

@Composable
private fun MeBenefitRow(benefit: MembershipBenefitResource) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HhyIcon(HhyIcons.Check, null, Modifier.size(HhySpacing.Xl), HhyColors.Success)
        Text(
            benefit.name,
            modifier = Modifier.weight(1f).padding(start = HhySpacing.Sm),
            color = HhyColors.TextPrimary,
            fontSize = HhyType.BodySize,
            lineHeight = HhyType.BodyLineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        benefit.unit?.takeIf(String::isNotBlank)?.let { unit ->
            Text(unit, color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
        }
    }
}

@Composable
private fun MeAccountServices(
    onOpenLoginDevices: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCancellation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HhySpacing.Sm)) {
        MeSectionTitle("账号服务", null)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(HhyRadius.NormalCard),
            colors = CardDefaults.cardColors(containerColor = HhyColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = HhyElevation.Card),
        ) {
            Column(Modifier.fillMaxWidth()) {
                MeServiceRow("登录设备", HhyIcons.Devices, "mine.service.devices", onOpenLoginDevices)
                MeServiceRow("修改登录密码", HhyIcons.Lock, "mine.service.password", onOpenChangePassword)
                MeServiceRow("关于与检查更新", HhyIcons.Information, "mine.service.about", onOpenAbout)
                MeServiceRow("注销账号", HhyIcons.Delete, "mine.service.cancellation", onOpenCancellation, destructive = true)
            }
        }
    }
}

@Composable
private fun MeServiceRow(
    label: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(HhySpacing.Lg).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HhyIcon(icon, null, Modifier.size(HhySpacing.Xl), if (destructive) HhyColors.Error else HhyColors.BrandPrimary)
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = HhySpacing.Md),
            color = if (destructive) HhyColors.Error else HhyColors.TextPrimary,
            fontSize = HhyType.BodySize,
            lineHeight = HhyType.BodyLineHeight,
        )
        HhyIcon(HhyIcons.ChevronRight, null, tint = HhyColors.TextTertiary)
    }
}

@Composable
private fun MeSectionTitle(title: String, updatedAt: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = HhyColors.TextPrimary,
            fontSize = HhyType.CardTitleSize,
            lineHeight = HhyType.CardTitleLineHeight,
            fontWeight = FontWeight.SemiBold,
        )
        updatedAt?.let {
            Text(
                "更新于 ${it.r12MeBusinessTimeLabel()}",
                color = HhyColors.TextTertiary,
                fontSize = HhyType.CaptionSize,
                lineHeight = HhyType.CaptionLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val r12MeBusinessTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val r12MeBusinessZone = ZoneId.of("Asia/Shanghai")

internal fun String.r12MeBusinessTimeLabel(): String {
    val instant = runCatching { Instant.parse(this) }
        .recoverCatching { OffsetDateTime.parse(this).toInstant() }
        .getOrNull()
        ?: return "时间待同步"
    return r12MeBusinessTimeFormatter.format(instant.atZone(r12MeBusinessZone))
}

@Composable
private fun MeModuleBody(
    kind: R12MeModuleKind,
    status: R12MeModuleStatus,
    retryAfterSeconds: Long?,
    onRetry: () -> Unit,
    onIdentity: (() -> Unit)? = null,
) {
    if (status in setOf(R12MeModuleStatus.LOADING, R12MeModuleStatus.REFRESHING)) {
        Row(Modifier.fillMaxWidth().height(HhySize.InputHeight), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(HhySize.StandardProgress))
            Text("正在加载", Modifier.padding(start = HhySpacing.Md), color = HhyColors.TextSecondary)
        }
        return
    }
    MeModuleNotice(
        title = when (status) {
            R12MeModuleStatus.EMPTY -> if (kind == R12MeModuleKind.MEMBERSHIP) "暂未开通会员" else "暂无奖励账户"
            R12MeModuleStatus.IDENTITY_REQUIRED -> "完成实名后查看奖励资产"
            R12MeModuleStatus.RISK_FROZEN -> "奖励账户正在进行风险审核"
            R12MeModuleStatus.FORBIDDEN -> "当前账号暂不可查看"
            R12MeModuleStatus.OFFLINE -> "网络暂不可用"
            else -> "当前模块暂时无法加载"
        },
        message = moduleMessage(kind, status, retryAfterSeconds),
        onRetry = if (status == R12MeModuleStatus.IDENTITY_REQUIRED) onIdentity ?: onRetry else onRetry,
        actionLabel = if (status == R12MeModuleStatus.IDENTITY_REQUIRED) "去实名认证" else "重新加载",
        testTag = "mine.${kind.name.lowercase()}.retry",
    )
}

@Composable
private fun MeModuleNotice(
    title: String,
    message: String,
    onRetry: () -> Unit,
    actionLabel: String = "重新加载",
    testTag: String,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(HhyRadius.Tag), color = HhyColors.PageBackground) {
        Column(Modifier.fillMaxWidth().padding(HhySpacing.Md), verticalArrangement = Arrangement.spacedBy(HhySpacing.Xs)) {
            Text(title, color = HhyColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(message, color = HhyColors.TextSecondary, fontSize = HhyType.CaptionSize)
            TextButton(onClick = onRetry, modifier = Modifier.testTag(testTag)) {
                HhyIcon(HhyIcons.Refresh, null, Modifier.size(HhySpacing.Xl))
                Text(actionLabel, Modifier.padding(start = HhySpacing.Xs))
            }
        }
    }
}

@Composable
private fun MeInlineNotice(message: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = HhyColors.Warning, fontSize = HhyType.CaptionSize)
        TextButton(onClick = onRetry) {
            HhyIcon(HhyIcons.Refresh, "重新加载", Modifier.size(HhySpacing.Xl))
        }
    }
}

internal fun moduleMessage(
    kind: R12MeModuleKind,
    status: R12MeModuleStatus,
    retryAfterSeconds: Long?,
): String = when (status) {
    R12MeModuleStatus.EMPTY -> if (kind == R12MeModuleKind.MEMBERSHIP) "开通后将在这里展示会员名称、有效期和真实权益。" else "产生奖励后将在这里展示真实余额。"
    R12MeModuleStatus.FORBIDDEN -> "相关信息当前不可用，你仍可继续使用其他功能。"
    R12MeModuleStatus.IDENTITY_REQUIRED -> "奖励账户要求先完成实名认证。"
    R12MeModuleStatus.RISK_FROZEN -> "审核期间奖励资产只显示受限状态。"
    R12MeModuleStatus.RATE_LIMITED -> retryAfterSeconds?.let { "请求较频繁，请在 $it 秒后再试。" } ?: "请求较频繁，请稍后再试。"
    R12MeModuleStatus.OFFLINE -> "恢复网络后可以重新加载。"
    R12MeModuleStatus.STALE -> "暂时无法刷新，当前内容可能不是最新状态。"
    R12MeModuleStatus.ERROR -> "服务暂时不可用，请稍后重新加载。"
    else -> ""
}

private fun isSecureMeMediaUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI.create(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)
