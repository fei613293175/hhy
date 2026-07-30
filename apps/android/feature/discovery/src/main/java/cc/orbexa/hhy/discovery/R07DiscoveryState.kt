package cc.orbexa.hhy.discovery

import cc.orbexa.hhy.network.ContactAccessResource
import cc.orbexa.hhy.network.R07CallResult
import java.util.UUID

internal enum class R07LoadPhase { IDLE, LOADING, CONTENT, EMPTY, ERROR, OFFLINE, FORBIDDEN, NOT_FOUND }

internal data class R07UiFailure(
    val phase: R07LoadPhase,
    val title: String,
    val guidance: String,
    val requestId: String?,
)

internal fun R07CallResult.Failure.toUiFailure(): R07UiFailure = when (statusCode) {
    null -> R07UiFailure(R07LoadPhase.OFFLINE, "网络连接不可用", "请检查网络后重试", requestId)
    401 -> R07UiFailure(R07LoadPhase.ERROR, "登录状态已失效", "请重新登录后继续", requestId)
    403 -> R07UiFailure(R07LoadPhase.FORBIDDEN, "暂时无法访问", "当前账号不具备所需权限", requestId)
    404 -> R07UiFailure(R07LoadPhase.NOT_FOUND, "内容不存在", "内容可能已下架或不再公开", requestId)
    409 -> R07UiFailure(R07LoadPhase.ERROR, "数据已经更新", "请重新加载后再操作", requestId)
    429 -> R07UiFailure(R07LoadPhase.ERROR, "操作过于频繁", "请稍后再试", requestId)
    else -> R07UiFailure(R07LoadPhase.ERROR, "暂时无法完成请求", "请稍后重试", requestId)
}

/** Reuses one key for the same write intent and rotates only after success or payload change. */
internal class StableIntentKeys(
    private val factory: () -> String = { "r07-${UUID.randomUUID()}" },
) {
    private val keys = mutableMapOf<String, Pair<String, String>>()

    fun key(scope: String, payloadFingerprint: String): String {
        val existing = keys[scope]
        if (existing != null && existing.first == payloadFingerprint) return existing.second
        return factory().also { generated -> keys[scope] = payloadFingerprint to generated }
    }

    fun complete(scope: String) {
        keys.remove(scope)
    }
}

/** The authorized plaintext exists only in this in-memory state and is erased on close. */
internal data class ContactPanelState(
    val submitting: Boolean = false,
    val contact: ContactAccessResource? = null,
    val failure: R07UiFailure? = null,
) {
    fun close(): ContactPanelState = ContactPanelState()
}
