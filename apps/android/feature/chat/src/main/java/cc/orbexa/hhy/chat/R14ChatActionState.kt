package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.R07CallResult

enum class R14ChatAction { REPORT, BLOCK, UNBLOCK, DELETE_CONVERSATION }

data class R14ChatActionState(
    val active: R14ChatAction? = null,
    val failure: R07CallResult.Failure? = null,
    val blocked: Boolean = false,
    val deleted: Boolean = false,
) {
    fun started(action: R14ChatAction): R14ChatActionState {
        require(active == null)
        return copy(active = action, failure = null)
    }

    fun succeeded(action: R14ChatAction): R14ChatActionState = copy(
        active = null,
        failure = null,
        blocked = when (action) {
            R14ChatAction.BLOCK -> true
            R14ChatAction.UNBLOCK -> false
            else -> blocked
        },
        deleted = deleted || action == R14ChatAction.DELETE_CONVERSATION,
    )

    fun failed(value: R07CallResult.Failure): R14ChatActionState = copy(active = null, failure = value)
    fun canSubmit(): Boolean = active == null && !deleted
}

internal fun R07CallResult.Failure.r14ActionMessage(): String = when (statusCode) {
    null -> "网络不可用，请恢复网络后重试"
    400 -> fieldErrors.values.firstOrNull() ?: "提交内容不符合要求"
    401 -> "登录状态已失效，请重新登录"
    403 -> "当前没有执行此操作的权限"
    404 -> "会话或用户已失效"
    409 -> "状态已经变化，请刷新后重新确认"
    422 -> "当前状态无法执行此操作"
    429 -> retryAfterSeconds?.let { "操作较频繁，请 $it 秒后重试" } ?: "操作较频繁，请稍后重试"
    else -> "操作暂未完成，请稍后重试"
}
