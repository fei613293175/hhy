package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.UserSelfResource
import java.net.URI

enum class R12PublishPhase {
    LOADING,
    CONTENT,
    EMPTY,
    FORBIDDEN,
    NOT_FOUND,
    OFFLINE,
    ERROR,
}

data class R12PublishEligibility(
    val canPublish: Boolean,
    val title: String,
    val detail: String,
)

data class R12PublishOverview(
    val total: Long,
    val online: Int,
    val pending: Int,
    val drafts: Int,
)

data class R12PublishOption(
    val contentType: String,
    val title: String,
    val description: String,
)

internal fun r12PublishEligibility(user: UserSelfResource): R12PublishEligibility = when {
    user.status != "ACTIVE" -> R12PublishEligibility(
        canPublish = false,
        title = "发布资格暂不可用",
        detail = "账号状态恢复正常后可继续发布",
    )
    user.identityStatus != "VERIFIED" -> R12PublishEligibility(
        canPublish = false,
        title = "完成实名认证后发布",
        detail = "发布内容前需要完成实名认证",
    )
    else -> R12PublishEligibility(
        canPublish = true,
        title = "发布资格已具备",
        detail = "发布额度和提交条件以服务端实时校验为准",
    )
}

internal fun r12PublishOverview(page: ContentPageResource): R12PublishOverview = R12PublishOverview(
    total = page.page.total?.toLongOrNull() ?: page.items.size.toLong(),
    online = page.items.count { it.status == "ONLINE" },
    pending = page.items.count { it.status in setOf("PENDING_REVIEW", "REVIEWING") },
    drafts = page.items.count { it.status in setOf("DRAFT", "REJECTED", "RECTIFICATION") },
)

internal fun r12PublishOptions(): List<R12PublishOption> = listOf(
    R12PublishOption("PROJECT", "项目", "发布合作项目、产品或服务"),
    R12PublishOption("APP", "App", "发布应用、工具或平台"),
    R12PublishOption("GROUP_CHAT", "群聊", "发布微信群、QQ群等群聊"),
    R12PublishOption("TEAM_LEADER", "团队长", "创建团队长资料并展示团队能力"),
)

internal fun R07CallResult.Failure.toR12PublishPhase(): R12PublishPhase = when (statusCode) {
    403 -> R12PublishPhase.FORBIDDEN
    404 -> R12PublishPhase.NOT_FOUND
    null -> R12PublishPhase.OFFLINE
    else -> R12PublishPhase.ERROR
}

internal fun r12ContentTypeLabel(value: String): String = when (value) {
    "PROJECT" -> "项目"
    "APP" -> "App"
    "GROUP_CHAT" -> "群聊"
    "TEAM_LEADER" -> "团队长"
    else -> "内容"
}

internal fun r12ContentStatusLabel(value: String): String = when (value) {
    "DRAFT" -> "草稿"
    "PENDING_REVIEW" -> "审核中"
    "REVIEWING" -> "审核中"
    "APPROVED" -> "已通过"
    "ONLINE" -> "已上架"
    "OFFLINE" -> "已下架"
    "REJECTED" -> "未通过"
    "RECTIFICATION" -> "待修改"
    "BANNED" -> "已封禁"
    "DELETED" -> "已删除"
    else -> "状态待同步"
}

internal fun r12ReviewStatusLabel(value: String): String = when (value) {
    "PENDING" -> "待审核"
    "REVIEWING" -> "审核中"
    "APPROVED" -> "审核通过"
    "REJECTED" -> "审核未通过"
    "ESCALATED" -> "二审中"
    else -> "审核状态待同步"
}

internal fun ContentResource.canSubmitFromPreview(user: UserSelfResource): Boolean =
    user.status == "ACTIVE" &&
        user.identityStatus == "VERIFIED" &&
        publisher?.userId == user.id &&
        status in setOf("DRAFT", "REJECTED", "RECTIFICATION")

internal fun ContentResource.securePreviewMedia(): List<MediaItemResource> = media
    .asSequence()
    .filter { item -> item.mediaType.startsWith("IMAGE", ignoreCase = true) }
    .filter { item ->
        runCatching {
            val uri = URI.create(item.url)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        }.getOrDefault(false)
    }
    .sortedBy(MediaItemResource::sortOrder)
    .take(9)
    .toList()

internal fun ContentResource.previewFacts(): List<Pair<String, String>> = buildList {
    categoryCode?.takeIf(String::isNotBlank)?.let { add("分类" to it) }
    regionCode?.takeIf(String::isNotBlank)?.let { add("地区" to it) }
    publisher?.nickname?.takeIf(String::isNotBlank)?.let { add("发布者" to it) }
    add("发布状态" to r12ContentStatusLabel(status))
    reviewStatus?.takeIf(String::isNotBlank)?.let { add("审核状态" to r12ReviewStatusLabel(it)) }
    updatedAt?.takeIf(String::isNotBlank)?.let { add("最近更新" to it) }
}
