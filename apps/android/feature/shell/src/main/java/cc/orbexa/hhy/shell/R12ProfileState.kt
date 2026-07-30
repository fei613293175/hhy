package cc.orbexa.hhy.shell

import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12ProfilePatchRequest
import cc.orbexa.hhy.network.UserSelfResource
import java.util.UUID

enum class R12ProfilePhase {
    LOADING,
    CONTENT,
    STALE_CACHE,
    CONFLICT,
    UNKNOWN_RESULT,
    FORBIDDEN,
    NOT_FOUND,
    OFFLINE,
    ERROR,
}

enum class R12ProfileReconcileMode {
    CONFLICT,
    UNKNOWN_RESULT,
}

data class R12ProfilePendingWrite(
    val request: R12ProfilePatchRequest,
    val idempotencyKey: String,
)

data class R12ProfileState(
    val phase: R12ProfilePhase = R12ProfilePhase.LOADING,
    val user: UserSelfResource? = null,
    val nicknameInput: String = "",
    val bioInput: String = "",
    val avatarMediaId: String? = null,
    val avatarPreviewUrl: String? = null,
    val submitting: Boolean = false,
    val checkingLatest: Boolean = false,
    val pendingWrite: R12ProfilePendingWrite? = null,
    val reconcileMode: R12ProfileReconcileMode? = null,
    val notice: String? = null,
    val remoteFieldErrors: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Long? = null,
) {
    val nicknameCount: Int get() = profileTextCodePointCount(nicknameInput)
    val bioCount: Int get() = profileTextCodePointCount(bioInput)

    val localFieldErrors: Map<String, String>
        get() = buildMap {
            val nickname = nicknameInput.trim()
            if (nickname.isEmpty()) put("nickname", "请输入昵称")
            else if (profileTextCodePointCount(nickname) > PROFILE_TEXT_LIMIT) {
                put("nickname", "昵称不能超过255个字符")
            }
            if (profileTextCodePointCount(bioInput.trim()) > PROFILE_TEXT_LIMIT) {
                put("bio", "个人简介不能超过255个字符")
            }
        }

    val fieldErrors: Map<String, String> get() = remoteFieldErrors + localFieldErrors

    val isDirty: Boolean
        get() = user?.let { current ->
            nicknameInput.trim() != current.nickname.orEmpty().trim() ||
                bioInput.trim() != current.bio.orEmpty().trim() ||
                avatarMediaId != null
        } ?: false

    val inputsEnabled: Boolean
        get() = user != null && !submitting && !checkingLatest && reconcileMode == null &&
            phase !in setOf(R12ProfilePhase.FORBIDDEN, R12ProfilePhase.NOT_FOUND)

    val canSave: Boolean
        get() = inputsEnabled && isDirty && fieldErrors.isEmpty()

    val displayedAvatarUrl: String?
        get() = when {
            avatarMediaId == "" -> null
            avatarPreviewUrl != null -> avatarPreviewUrl
            else -> user?.avatarUrl
        }

    fun loaded(value: UserSelfResource, resetDraft: Boolean = true): R12ProfileState = copy(
        phase = R12ProfilePhase.CONTENT,
        user = value,
        nicknameInput = if (resetDraft) value.nickname.orEmpty() else nicknameInput,
        bioInput = if (resetDraft) value.bio.orEmpty() else bioInput,
        avatarMediaId = if (resetDraft) null else avatarMediaId,
        avatarPreviewUrl = if (resetDraft) null else avatarPreviewUrl,
        submitting = false,
        checkingLatest = false,
        pendingWrite = null,
        reconcileMode = null,
        remoteFieldErrors = emptyMap(),
        retryAfterSeconds = null,
    )

    fun nicknameChanged(value: String): R12ProfileState = copy(
        nicknameInput = value,
        notice = null,
        remoteFieldErrors = remoteFieldErrors - "nickname",
        retryAfterSeconds = null,
    )

    fun bioChanged(value: String): R12ProfileState = copy(
        bioInput = value,
        notice = null,
        remoteFieldErrors = remoteFieldErrors - "bio",
        retryAfterSeconds = null,
    )

    fun avatarChanged(mediaId: String, previewUrl: String?): R12ProfileState = copy(
        avatarMediaId = mediaId,
        avatarPreviewUrl = previewUrl,
        notice = null,
        remoteFieldErrors = remoteFieldErrors - "avatarMediaId",
    )

    fun patchRequest(): R12ProfilePatchRequest {
        val current = requireNotNull(user)
        val nickname = nicknameInput.trim()
        val bio = bioInput.trim()
        return R12ProfilePatchRequest(
            nickname = nickname.takeIf { it != current.nickname.orEmpty().trim() },
            avatarMediaId = avatarMediaId,
            bio = bio.takeIf { it != current.bio.orEmpty().trim() },
            expectedVersion = current.version,
        ).also {
            require(fieldErrors.isEmpty())
            require(it.nickname != null || it.avatarMediaId != null || it.bio != null)
        }
    }

    fun submitStarted(request: R12ProfilePatchRequest, key: String): R12ProfileState = copy(
        submitting = true,
        checkingLatest = false,
        pendingWrite = R12ProfilePendingWrite(request, key),
        reconcileMode = null,
        notice = null,
        remoteFieldErrors = emptyMap(),
        retryAfterSeconds = null,
    )

    fun submitted(value: UserSelfResource): R12ProfileState = loaded(value).copy(
        notice = "个人资料已保存",
    )

    fun commandAccepted(): R12ProfileState = copy(
        phase = R12ProfilePhase.UNKNOWN_RESULT,
        submitting = false,
        checkingLatest = false,
        reconcileMode = R12ProfileReconcileMode.UNKNOWN_RESULT,
        notice = "保存请求已受理，正在查询最新资料",
    )

    fun submitFailed(failure: R07CallResult.Failure): R12ProfileState {
        val statusCode = failure.statusCode
        return when {
            statusCode == 401 -> copy(submitting = false, pendingWrite = null, reconcileMode = null)
            statusCode == 409 -> copy(
                phase = R12ProfilePhase.CONFLICT,
                submitting = false,
                reconcileMode = R12ProfileReconcileMode.CONFLICT,
                notice = "资料已发生变化，正在查询服务端最新内容",
            )
            statusCode == null || statusCode >= 500 -> copy(
                phase = R12ProfilePhase.UNKNOWN_RESULT,
                submitting = false,
                reconcileMode = R12ProfileReconcileMode.UNKNOWN_RESULT,
                notice = "暂时无法确认保存结果，请先查询最新资料",
            )
            statusCode == 403 -> copy(
                phase = R12ProfilePhase.FORBIDDEN,
                submitting = false,
                pendingWrite = null,
                reconcileMode = null,
                notice = "当前账号暂时不能修改个人资料",
            )
            statusCode == 404 -> copy(
                phase = R12ProfilePhase.NOT_FOUND,
                submitting = false,
                pendingWrite = null,
                reconcileMode = null,
                notice = "个人资料不存在或已不可用",
            )
            statusCode == 429 -> copy(
                phase = R12ProfilePhase.CONTENT,
                submitting = false,
                pendingWrite = null,
                reconcileMode = null,
                retryAfterSeconds = failure.retryAfterSeconds,
                notice = failure.retryAfterSeconds?.let { "操作较频繁，请在 $it 秒后重试" } ?: "操作较频繁，请稍后重试",
            )
            else -> copy(
                phase = R12ProfilePhase.CONTENT,
                submitting = false,
                pendingWrite = null,
                reconcileMode = null,
                remoteFieldErrors = failure.fieldErrors.filterKeys { it in PROFILE_FIELDS },
                notice = if (failure.fieldErrors.isEmpty()) "当前资料未能保存，请检查后重试" else null,
            )
        }
    }

    fun checkingLatest(): R12ProfileState = copy(checkingLatest = true, notice = null)

    fun conflictReconciled(latest: UserSelfResource): R12ProfileState = copy(
        phase = R12ProfilePhase.CONTENT,
        user = latest,
        submitting = false,
        checkingLatest = false,
        pendingWrite = null,
        reconcileMode = null,
        remoteFieldErrors = emptyMap(),
        retryAfterSeconds = null,
        notice = "已加载最新资料，你的输入仍然保留",
    )

    fun unknownResultReconciled(latest: UserSelfResource): R12ProfileState {
        require(reconcileMode == R12ProfileReconcileMode.UNKNOWN_RESULT)
        val pending = requireNotNull(pendingWrite)
        val pendingAvatarMediaId = pending.request.avatarMediaId
        return when {
            profileMatchesRequest(latest, pending.request, avatarPreviewUrl) ->
                loaded(latest).copy(notice = "个人资料已保存")
            latest.version == pending.request.expectedVersion -> copy(
                phase = R12ProfilePhase.CONTENT,
                user = latest,
                submitting = false,
                checkingLatest = false,
                pendingWrite = null,
                reconcileMode = null,
                notice = "未发现资料变更，可以使用原请求继续保存",
            )
            else -> copy(
                phase = R12ProfilePhase.CONTENT,
                user = latest,
                submitting = false,
                checkingLatest = false,
                pendingWrite = null,
                reconcileMode = null,
                avatarMediaId = if (pendingAvatarMediaId != null) null else avatarMediaId,
                avatarPreviewUrl = if (pendingAvatarMediaId != null) null else avatarPreviewUrl,
                notice = if (pendingAvatarMediaId != null) {
                    "已加载服务端最新头像；如需更换，请重新选择图片"
                } else {
                    "服务端资料已经变化，你的输入仍然保留"
                },
            )
        }
    }

    fun loadFailed(failure: R07CallResult.Failure): R12ProfileState = if (user != null) {
        copy(
            phase = R12ProfilePhase.STALE_CACHE,
            checkingLatest = false,
            submitting = false,
            notice = "暂时无法刷新，当前内容可能不是最新状态",
        )
    } else {
        copy(
            phase = when (failure.statusCode) {
                null -> R12ProfilePhase.OFFLINE
                403 -> R12ProfilePhase.FORBIDDEN
                404 -> R12ProfilePhase.NOT_FOUND
                else -> R12ProfilePhase.ERROR
            },
            checkingLatest = false,
            submitting = false,
            notice = when (failure.statusCode) {
                null -> "网络不可用，请恢复网络后重试"
                403 -> "当前账号暂时不能查看个人资料"
                404 -> "个人资料不存在或已不可用"
                else -> "个人资料暂时无法加载"
            },
        )
    }
}

class R12ProfileIntentKeys {
    private val keys = mutableMapOf<String, String>()

    fun forRequest(request: R12ProfilePatchRequest): String {
        val fingerprint = request.profileFingerprint()
        return keys.getOrPut(fingerprint) { "r12-profile-${UUID.randomUUID()}" }
    }

    fun consume(request: R12ProfilePatchRequest) {
        keys.remove(request.profileFingerprint())
    }
}

private fun R12ProfilePatchRequest.profileFingerprint(): String {
    val requestNickname = nickname
    val requestAvatarMediaId = avatarMediaId
    val requestBio = bio
    return buildString {
        append(expectedVersion).append('|')
        appendFingerprintField(requestNickname)
        appendFingerprintField(requestAvatarMediaId)
        appendFingerprintField(requestBio)
    }
}

private fun StringBuilder.appendFingerprintField(value: String?) {
    if (value == null) {
        append("N|")
    } else {
        append('V').append(value.length).append(':').append(value).append('|')
    }
}

internal fun profileTextCodePointCount(value: String): Int =
    Character.codePointCount(value, 0, value.length)

internal fun profileMatchesRequest(
    user: UserSelfResource,
    request: R12ProfilePatchRequest,
    avatarPreviewUrl: String? = null,
): Boolean {
    val requestNickname = request.nickname
    val requestBio = request.bio
    val requestAvatarMediaId = request.avatarMediaId
    return (requestNickname == null || user.nickname.orEmpty().trim() == requestNickname.trim()) &&
        (requestBio == null || user.bio.orEmpty().trim() == requestBio.trim()) &&
        when {
            requestAvatarMediaId == null -> true
            requestAvatarMediaId.isEmpty() -> user.avatarUrl.isNullOrBlank()
            avatarPreviewUrl != null -> user.avatarUrl == avatarPreviewUrl
            else -> false
        }
}

private const val PROFILE_TEXT_LIMIT = 255
private val PROFILE_FIELDS = setOf("nickname", "avatarMediaId", "bio")
