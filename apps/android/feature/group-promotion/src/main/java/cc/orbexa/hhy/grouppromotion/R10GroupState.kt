package cc.orbexa.hhy.grouppromotion

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08ContactInput
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class R10GroupPhase { LOADING, CONTENT, EMPTY, SUBMITTING, SUCCESS, ERROR, OFFLINE, FORBIDDEN, NOT_FOUND, CONFLICT }

data class R10GroupFailure(val phase: R10GroupPhase, val errorCode: String?, val retryAfterSeconds: Long?, val fieldErrors: Map<String, String>)

fun R07CallResult.Failure.toR10Failure() = R10GroupFailure(
    phase = when (statusCode) {
        null -> R10GroupPhase.OFFLINE
        403 -> R10GroupPhase.FORBIDDEN
        404 -> R10GroupPhase.NOT_FOUND
        409 -> R10GroupPhase.CONFLICT
        else -> R10GroupPhase.ERROR
    },
    errorCode = errorCode,
    retryAfterSeconds = retryAfterSeconds,
    fieldErrors = fieldErrors,
)

data class R10GroupFacts(
    val platform: String?,
    val sizeRange: String?,
    val joinRequirement: String?,
    val qrMediaId: String?,
    val groupLink: String?,
    val groupNo: String?,
) {
    companion object {
        fun from(resource: ContentResource) = R10GroupFacts(
            platform = resource.attributes.string("platform"),
            sizeRange = resource.attributes.string("sizeRange"),
            joinRequirement = resource.attributes.string("joinRequirement"),
            qrMediaId = resource.attributes.string("qrMediaId"),
            groupLink = resource.attributes.string("groupLink")?.takeIf(::secureGroupHttps),
            groupNo = resource.attributes.string("groupNo"),
        )
    }
}

data class R10GroupForm(
    val title: String = "",
    val summary: String = "",
    val description: String = "",
    val categoryCode: String = "",
    val regionCode: String = "",
    val platform: String = "",
    val sizeRange: String = "",
    val joinRequirement: String = "",
    val qrMediaId: String = "",
    val groupLink: String = "",
    val groupNo: String = "",
    val ownerContactChannel: String = "WECHAT",
    val ownerContactValue: String = "",
    val joinPassword: String = "",
    val mediaIds: List<String> = emptyList(),
    val expectedVersion: Long? = null,
) {
    fun validate(): Map<String, String> = buildMap {
        if (title.isBlank()) put("title", "请输入群聊标题") else if (title.length > 2000) put("title", "群聊标题不能超过2000字")
        if (description.isBlank()) put("description", "请输入群聊说明") else if (description.length > 2000) put("description", "群聊说明不能超过2000字")
        if (categoryCode.isBlank()) put("categoryCode", "请输入群聊分类") else if (categoryCode.length > 2000) put("categoryCode", "群聊分类不能超过2000字")
        if (platform.isBlank()) put("platform", "请选择群平台") else if (platform.length > 255) put("platform", "群平台不能超过255字")
        if (summary.length > 2000) put("summary", "摘要不能超过2000字")
        if (regionCode.length > 2000) put("regionCode", "地区不能超过2000字")
        if (sizeRange.length > 255) put("sizeRange", "群规模不能超过255字")
        if (joinRequirement.length > 255) put("joinRequirement", "入群要求不能超过255字")
        if (groupLink.isNotBlank() && !secureGroupHttps(groupLink)) put("groupLink", "群链接必须是安全的HTTPS地址")
        if (groupNo.length > 255) put("groupNo", "群号不能超过255字")
        if (ownerContactValue.isBlank()) put("ownerContactValue", "请填写群主联系方式")
        if (ownerContactChannel !in OWNER_CHANNELS) put("ownerContactChannel", "群主联系方式渠道不受支持")
        if (ownerContactValue.length > 2000) put("ownerContactValue", "群主联系方式不能超过2000字")
        if (joinPassword.length > 2000) put("joinPassword", "入群口令不能超过2000字")
        if (qrMediaId.isNotBlank() && !ID.matches(qrMediaId)) put("qrMediaId", "二维码媒体标识无效")
        if (listOf(qrMediaId, groupLink, groupNo, joinPassword).all(String::isBlank)) put("joinChannel", "请至少提供一种真实入群方式")
        if (mediaIds.size > 100 || mediaIds.toSet().size != mediaIds.size || mediaIds.any { !ID.matches(it) }) put("mediaIds", "群聊图片数量或内容不符合要求")
        if (expectedVersion != null && expectedVersion < 0) put("expectedVersion", "数据版本无效")
    }

    fun attributes(): JsonObject = buildJsonObject {
        put("platform", platform.trim())
        sizeRange.trim().takeIf(String::isNotBlank)?.let { put("sizeRange", it) }
        joinRequirement.trim().takeIf(String::isNotBlank)?.let { put("joinRequirement", it) }
        qrMediaId.trim().takeIf(String::isNotBlank)?.let { put("qrMediaId", it) }
        groupLink.trim().takeIf(String::isNotBlank)?.let { put("groupLink", it) }
        groupNo.trim().takeIf(String::isNotBlank)?.let { put("groupNo", it) }
    }

    fun contacts(): List<R08ContactInput> = buildList {
        ownerContactValue.trim().takeIf(String::isNotBlank)?.let { add(R08ContactInput(ownerContactChannel, it)) }
        joinPassword.trim().takeIf(String::isNotBlank)?.let { add(R08ContactInput("JOIN_PASSWORD", it)) }
    }

    companion object {
        val OWNER_CHANNELS = setOf("WECHAT", "PHONE", "QQ", "EMAIL")
        private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun from(resource: ContentResource): R10GroupForm {
            val facts = R10GroupFacts.from(resource)
            return R10GroupForm(
                title = resource.title,
                summary = resource.summary.orEmpty(),
                description = resource.description.orEmpty(),
                categoryCode = resource.categoryCode.orEmpty(),
                regionCode = resource.regionCode.orEmpty(),
                platform = facts.platform.orEmpty(),
                sizeRange = facts.sizeRange.orEmpty(),
                joinRequirement = facts.joinRequirement.orEmpty(),
                qrMediaId = facts.qrMediaId.orEmpty(),
                groupLink = facts.groupLink.orEmpty(),
                groupNo = facts.groupNo.orEmpty(),
                mediaIds = resource.media.map { it.id },
                expectedVersion = resource.version,
            )
        }
    }
}

class R10IntentKeys {
    private val values = mutableMapOf<String, String>()
    fun forBody(operation: String, fingerprint: String) = values.getOrPut("$operation:$fingerprint") { "r10-${UUID.randomUUID()}" }
    fun consume(operation: String, fingerprint: String) { values.remove("$operation:$fingerprint") }
}

internal fun secureGroupHttps(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

internal fun groupPlatformLabel(value: String?): String? = when (value?.trim()?.uppercase()) {
    null, "" -> null
    "WECHAT" -> "微信群"
    "QQ" -> "QQ群"
    "DINGTALK" -> "钉钉群"
    "FEISHU" -> "飞书群"
    else -> value.takeIf { raw -> raw.any { it in '\u4e00'..'\u9fff' } } ?: "其他群平台"
}

private fun JsonObject?.string(key: String): String? = this?.get(key)?.let { value ->
    (value as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}
