package cc.orbexa.hhy.project

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import java.util.UUID

enum class R08ProjectPhase {
    LOADING, CONTENT, EMPTY, SUBMITTING, SUCCESS, ERROR, OFFLINE, FORBIDDEN, NOT_FOUND, CONFLICT,
}

data class R08ProjectFailure(
    val phase: R08ProjectPhase,
    val errorCode: String?,
    val requestId: String?,
    val retryAfterSeconds: Long?,
    val fieldErrors: Map<String, String>,
)

fun R07CallResult.Failure.toR08Failure(): R08ProjectFailure = R08ProjectFailure(
    phase = when (statusCode) {
        null -> R08ProjectPhase.OFFLINE
        403 -> R08ProjectPhase.FORBIDDEN
        404 -> R08ProjectPhase.NOT_FOUND
        409 -> R08ProjectPhase.CONFLICT
        else -> R08ProjectPhase.ERROR
    },
    errorCode = errorCode,
    requestId = requestId,
    retryAfterSeconds = retryAfterSeconds,
    fieldErrors = fieldErrors,
)

data class R08ProjectForm(
    val title: String = "",
    val summary: String = "",
    val description: String = "",
    val categoryCode: String = "",
    val regionCode: String = "",
    val contactChannel: String = "WECHAT",
    val contactValue: String = "",
    val mediaIds: List<String> = emptyList(),
    val expectedVersion: Long? = null,
) {
    fun validate(): Map<String, String> = buildMap {
        if (title.isBlank()) put("title", "请输入项目标题") else if (title.length > 2000) put("title", "项目标题不能超过2000字")
        if (description.isBlank()) put("description", "请输入详细说明") else if (description.length > 2000) put("description", "详细说明不能超过2000字")
        if (categoryCode.isBlank()) put("categoryCode", "请输入分类编码") else if (categoryCode.length > 2000) put("categoryCode", "分类编码不能超过2000字")
        if (summary.length > 2000) put("summary", "摘要不能超过2000字")
        if (regionCode.length > 2000) put("regionCode", "地区编码不能超过2000字")
        if (contactValue.isNotBlank() && contactChannel !in CONTACT_CHANNELS) put("contactChannel", "联系方式渠道不受支持")
        if (contactValue.length > 2000) put("contactValue", "联系方式不能超过2000字")
        if (mediaIds.size > 100 || mediaIds.toSet().size != mediaIds.size) put("mediaIds", "项目媒体数量或内容不符合要求")
        if (expectedVersion != null && expectedVersion < 0) put("expectedVersion", "数据版本无效")
    }

    companion object {
        val CONTACT_CHANNELS = setOf("WECHAT", "PHONE", "QQ", "EMAIL", "LINK", "QR_CODE")

        fun from(resource: ContentResource) = R08ProjectForm(
            title = resource.title,
            summary = resource.summary.orEmpty(),
            description = resource.description.orEmpty(),
            categoryCode = resource.categoryCode.orEmpty(),
            regionCode = resource.regionCode.orEmpty(),
            mediaIds = resource.media.map { it.id },
            expectedVersion = resource.version,
        )
    }
}

class R08IntentKeys {
    private val values = mutableMapOf<String, String>()

    fun forBody(operation: String, bodyFingerprint: String): String = values.getOrPut("$operation:$bodyFingerprint") {
        "r08-${UUID.randomUUID()}"
    }

    fun consume(operation: String, bodyFingerprint: String) {
        values.remove("$operation:$bodyFingerprint")
    }
}
