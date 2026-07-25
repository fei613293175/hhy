package cc.orbexa.hhy.contentmanagement

import cc.orbexa.hhy.network.ContentPageResource
import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.MediaItemResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12CopyContentResult
import cc.orbexa.hhy.network.UserSelfResource
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

enum class R12PublishPhase {
    LOADING,
    CONTENT,
    EMPTY,
    FORBIDDEN,
    NOT_FOUND,
    OFFLINE,
    ERROR,
}

enum class R12SubmitPhase {
    SUBMITTING,
    RETRYING,
    SUCCESS,
    PENDING,
    REJECTED,
    CONFLICT,
    FAILED,
    UNKNOWN,
    OFFLINE,
    FORBIDDEN,
    NOT_FOUND,
    ERROR,
}

internal data class R12SubmitPresentation(
    val title: String,
    val detail: String,
    val statusLabel: String,
)

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

internal fun r12SubmitIdempotencyKey(
    contentId: String,
    expectedVersion: Long,
    intentId: String = UUID.randomUUID().toString(),
): String {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(contentId))
    require(expectedVersion >= 0)
    require(intentId.isNotBlank())
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$contentId:$expectedVersion:$intentId".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "r12-submit-$digest"
}

internal fun R07CallResult.Failure.toR12SubmitPhase(): R12SubmitPhase = when (statusCode) {
    403 -> R12SubmitPhase.FORBIDDEN
    404 -> R12SubmitPhase.NOT_FOUND
    409 -> R12SubmitPhase.CONFLICT
    400, 422 -> R12SubmitPhase.FAILED
    429 -> R12SubmitPhase.ERROR
    null -> R12SubmitPhase.UNKNOWN
    in 500..599 -> R12SubmitPhase.UNKNOWN
    else -> R12SubmitPhase.ERROR
}

internal fun r12SubmitPhaseFromContent(status: String): R12SubmitPhase = when (status) {
    "PENDING_REVIEW", "REVIEWING" -> R12SubmitPhase.PENDING
    "REJECTED", "RECTIFICATION" -> R12SubmitPhase.REJECTED
    "APPROVED", "ONLINE" -> R12SubmitPhase.SUCCESS
    else -> R12SubmitPhase.UNKNOWN
}

internal fun R12CopyContentResult.submittedStatus(): String = when (this) {
    is R12CopyContentResult.Content -> resource.status
    is R12CopyContentResult.Command -> command.status
}

internal fun r12SubmitPresentation(
    phase: R12SubmitPhase,
    contentStatus: String?,
    retryAfterSeconds: Long? = null,
): R12SubmitPresentation {
    val statusLabel = contentStatus?.let(::r12ContentStatusLabel) ?: "等待状态同步"
    return when (phase) {
        R12SubmitPhase.SUBMITTING -> R12SubmitPresentation(
            "正在提交",
            "正在校验最新内容并提交平台审核，请勿重复操作",
            "提交中",
        )
        R12SubmitPhase.RETRYING -> R12SubmitPresentation(
            "正在重新提交",
            "正在使用原请求继续处理，不会创建新的提交意图",
            "处理中",
        )
        R12SubmitPhase.SUCCESS -> R12SubmitPresentation(
            "提交已受理",
            "内容已交由平台处理，请在我的发布中查看最新审核进度",
            statusLabel,
        )
        R12SubmitPhase.PENDING -> R12SubmitPresentation(
            "审核中",
            "平台正在审核该内容，最终结果以服务端最新状态为准",
            statusLabel,
        )
        R12SubmitPhase.REJECTED -> R12SubmitPresentation(
            "审核未通过",
            "请根据平台审核结果修改内容后，再发起新的提交",
            statusLabel,
        )
        R12SubmitPhase.CONFLICT -> R12SubmitPresentation(
            "内容已发生变化",
            "已重新读取服务端最新内容，请确认后再操作",
            statusLabel,
        )
        R12SubmitPhase.FAILED -> R12SubmitPresentation(
            "暂未提交",
            "当前内容或账号条件暂不满足提交要求，请修改后重试",
            statusLabel,
        )
        R12SubmitPhase.UNKNOWN -> R12SubmitPresentation(
            "结果确认中",
            "网络中断后无法确认最终结果，请先查询最新状态，不要重复提交",
            statusLabel,
        )
        R12SubmitPhase.OFFLINE -> R12SubmitPresentation(
            "网络不可用",
            "暂时无法查询最新结果，恢复网络后可继续确认",
            statusLabel,
        )
        R12SubmitPhase.FORBIDDEN -> R12SubmitPresentation(
            "暂时无法提交",
            "当前账号没有提交此内容的权限，请确认实名认证和内容归属",
            statusLabel,
        )
        R12SubmitPhase.NOT_FOUND -> R12SubmitPresentation(
            "内容不存在",
            "内容可能已删除或链接已经失效",
            "内容不可用",
        )
        R12SubmitPhase.ERROR -> R12SubmitPresentation(
            "请稍后再试",
            retryAfterSeconds?.let { "操作较频繁，请在 $it 秒后使用原请求继续" }
                ?: "暂时无法完成提交，可保留当前内容稍后继续",
            statusLabel,
        )
    }
}

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
