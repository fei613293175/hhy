package cc.orbexa.hhy.apppromotion

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class R09AppPhase { LOADING, CONTENT, EMPTY, SUBMITTING, SUCCESS, ERROR, OFFLINE, FORBIDDEN, NOT_FOUND, CONFLICT }

data class R09AppFailure(
    val phase: R09AppPhase,
    val errorCode: String?,
    val retryAfterSeconds: Long?,
    val fieldErrors: Map<String, String>,
)

fun R07CallResult.Failure.toR09Failure() = R09AppFailure(
    phase = when (statusCode) {
        null -> R09AppPhase.OFFLINE
        403 -> R09AppPhase.FORBIDDEN
        404 -> R09AppPhase.NOT_FOUND
        409 -> R09AppPhase.CONFLICT
        else -> R09AppPhase.ERROR
    },
    errorCode = errorCode,
    retryAfterSeconds = retryAfterSeconds,
    fieldErrors = fieldErrors,
)

data class R09AppFacts(
    val appName: String,
    val platform: String?,
    val versionText: String?,
    val downloadUrl: String?,
    val website: String?,
) {
    companion object {
        fun from(resource: ContentResource): R09AppFacts {
            val attributes = resource.attributes
            return R09AppFacts(
                appName = attributes.string("appName") ?: resource.title,
                platform = attributes.string("platform"),
                versionText = attributes.string("versionText"),
                downloadUrl = attributes.secureUrl("downloadUrl"),
                website = attributes.secureUrl("website"),
            )
        }
    }
}

data class R09AppForm(
    val appName: String = "",
    val title: String = "",
    val summary: String = "",
    val description: String = "",
    val categoryCode: String = "",
    val platform: String = "",
    val versionText: String = "",
    val downloadUrl: String = "",
    val website: String = "",
    val contactChannel: String = "WECHAT",
    val contactValue: String = "",
    val mediaIds: List<String> = emptyList(),
    val expectedVersion: Long? = null,
) {
    fun validate(): Map<String, String> = buildMap {
        if (appName.isBlank()) put("appName", "请输入App名称") else if (appName.length > 120) put("appName", "App名称不能超过120字")
        if (title.isBlank()) put("title", "请输入推广标题") else if (title.length > 2000) put("title", "推广标题不能超过2000字")
        if (description.isBlank()) put("description", "请输入应用介绍") else if (description.length > 2000) put("description", "应用介绍不能超过2000字")
        if (categoryCode.isBlank()) put("categoryCode", "请输入App分类") else if (categoryCode.length > 2000) put("categoryCode", "App分类不能超过2000字")
        if (summary.length > 2000) put("summary", "推广摘要不能超过2000字")
        if (platform.length > 64) put("platform", "平台说明不能超过64字")
        if (versionText.length > 64) put("versionText", "版本说明不能超过64字")
        if (downloadUrl.isNotBlank() && !secureHttps(downloadUrl)) put("downloadUrl", "下载链接必须是安全的HTTPS地址")
        if (website.isNotBlank() && !secureHttps(website)) put("website", "官网链接必须是安全的HTTPS地址")
        if (contactValue.isNotBlank() && contactChannel !in CONTACT_CHANNELS) put("contactChannel", "联系方式渠道不受支持")
        if (contactValue.length > 2000) put("contactValue", "联系方式不能超过2000字")
        if (mediaIds.size > 100 || mediaIds.toSet().size != mediaIds.size) put("mediaIds", "应用图片数量或内容不符合要求")
        if (expectedVersion != null && expectedVersion < 0) put("expectedVersion", "数据版本无效")
    }

    fun attributes(): JsonObject = buildJsonObject {
        put("appName", appName.trim())
        platform.trim().takeIf(String::isNotBlank)?.let { put("platform", it) }
        versionText.trim().takeIf(String::isNotBlank)?.let { put("versionText", it) }
        downloadUrl.trim().takeIf(String::isNotBlank)?.let { put("downloadUrl", it) }
        website.trim().takeIf(String::isNotBlank)?.let { put("website", it) }
    }

    companion object {
        val CONTACT_CHANNELS = setOf("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE")

        fun from(resource: ContentResource): R09AppForm {
            val facts = R09AppFacts.from(resource)
            return R09AppForm(
                appName = facts.appName,
                title = resource.title,
                summary = resource.summary.orEmpty(),
                description = resource.description.orEmpty(),
                categoryCode = resource.categoryCode.orEmpty(),
                platform = facts.platform.orEmpty(),
                versionText = facts.versionText.orEmpty(),
                downloadUrl = facts.downloadUrl.orEmpty(),
                website = facts.website.orEmpty(),
                mediaIds = resource.media.map { it.id },
                expectedVersion = resource.version,
            )
        }
    }
}

internal fun appCategoryLabel(value: String): String = when (value.trim().uppercase()) {
    "TOOLS" -> "实用工具"
    "SOCIAL" -> "社交沟通"
    "BUSINESS" -> "商务办公"
    "LIFESTYLE" -> "生活服务"
    "EDUCATION" -> "教育学习"
    "ENTERTAINMENT" -> "影音娱乐"
    "" -> ""
    else -> value.takeIf { raw -> raw.any { it in '\u4e00'..'\u9fff' } } ?: "其他应用"
}

internal fun appPlatformLabel(value: String?): String? = when (value?.trim()?.uppercase()) {
    null, "" -> null
    "ANDROID" -> "Android"
    "IOS" -> "iOS"
    "WEB" -> "网页应用"
    "MULTI", "CROSS_PLATFORM" -> "多平台"
    else -> value.takeIf { raw -> raw.any { it in '\u4e00'..'\u9fff' } } ?: "其他平台"
}

internal fun contactChannelLabel(value: String): String = when (value) {
    "WECHAT" -> "微信"
    "PHONE" -> "手机号"
    "QQ" -> "QQ"
    "EMAIL" -> "邮箱"
    "LINK" -> "链接"
    "QR_CODE" -> "二维码"
    else -> "联系方式"
}

class R09IntentKeys {
    private val values = mutableMapOf<String, String>()
    fun forBody(operation: String, fingerprint: String) = values.getOrPut("$operation:$fingerprint") { "r09-${UUID.randomUUID()}" }
    fun consume(operation: String, fingerprint: String) { values.remove("$operation:$fingerprint") }
}

internal fun secureHttps(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

private fun JsonObject?.string(key: String): String? = this?.get(key)?.let { value ->
    (value as? JsonPrimitive)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
}

private fun JsonObject?.secureUrl(key: String): String? = string(key)?.takeIf(::secureHttps)
