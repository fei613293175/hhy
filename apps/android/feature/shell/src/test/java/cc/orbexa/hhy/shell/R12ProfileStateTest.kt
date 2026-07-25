package cc.orbexa.hhy.shell

import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R12ProfilePatchRequest
import cc.orbexa.hhy.network.UserSelfResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R12ProfileStateTest {
    @Test
    fun dirtyDraftOnlySendsFieldsThatActuallyChanged() {
        val state = R12ProfileState().loaded(user()).nicknameChanged("  新昵称  ").bioChanged("原简介")

        val request = state.patchRequest()

        assertEquals("新昵称", request.nickname)
        assertEquals(null, request.bio)
        assertEquals(null, request.avatarMediaId)
        assertEquals(7, request.expectedVersion)
    }

    @Test
    fun unicodeValidationUsesCodePointsAndRejectsThe256thCharacter() {
        val emoji = "\uD83D\uDE80"
        val accepted = R12ProfileState().loaded(user()).nicknameChanged(emoji.repeat(255))
        val rejected = accepted.nicknameChanged(emoji.repeat(256))

        assertEquals(255, accepted.nicknameCount)
        assertTrue(accepted.canSave)
        assertEquals(256, rejected.nicknameCount)
        assertFalse(rejected.canSave)
        assertEquals("昵称不能超过255个字符", rejected.fieldErrors["nickname"])
    }

    @Test
    fun idempotencyKeyIsStableForTheSameBodyAndChangesWithTheBody() {
        val keys = R12ProfileIntentKeys()
        val first = R12ProfilePatchRequest(nickname = "甲", expectedVersion = 7)
        val edited = first.copy(nickname = "乙")

        assertEquals(keys.forRequest(first), keys.forRequest(first.copy()))
        assertNotEquals(keys.forRequest(first), keys.forRequest(edited))
    }

    @Test
    fun conflictRefreshPreservesLocalInputAndUsesLatestVersion() {
        val state = R12ProfileState().loaded(user()).nicknameChanged("本地昵称")
        val failed = state.submitStarted(state.patchRequest(), "r12-profile-1234567890abcdef")
            .submitFailed(R07CallResult.Failure(statusCode = 409))
        val reconciled = failed.conflictReconciled(user(version = 8, nickname = "服务端昵称"))

        assertEquals("本地昵称", reconciled.nicknameInput)
        assertEquals("服务端昵称", reconciled.user?.nickname)
        assertEquals(8, reconciled.patchRequest().expectedVersion)
        assertTrue(reconciled.canSave)
    }

    @Test
    fun unknownWriteMustQueryBeforeRetryAndKeepsTheOriginalIntent() {
        val state = R12ProfileState().loaded(user()).bioChanged("新的简介")
        val request = state.patchRequest()
        val unknown = state.submitStarted(request, "r12-profile-1234567890abcdef")
            .submitFailed(R07CallResult.Failure(statusCode = null))

        assertEquals(R12ProfilePhase.UNKNOWN_RESULT, unknown.phase)
        assertFalse(unknown.canSave)
        assertEquals(request, unknown.pendingWrite?.request)

        val unchanged = unknown.unknownResultReconciled(user())
        assertTrue(unchanged.canSave)
        assertEquals("新的简介", unchanged.bioInput)
    }

    @Test
    fun unknownWriteRecognizesTheCommittedProfile() {
        val state = R12ProfileState().loaded(user()).bioChanged("新的简介")
        val request = state.patchRequest()
        val unknown = state.submitStarted(request, "r12-profile-1234567890abcdef")
            .submitFailed(R07CallResult.Failure(statusCode = 503))

        val committed = unknown.unknownResultReconciled(user(version = 8, bio = "新的简介"))

        assertEquals(R12ProfilePhase.CONTENT, committed.phase)
        assertFalse(committed.isDirty)
        assertEquals("个人资料已保存", committed.notice)
    }

    private fun user(
        version: Long = 7,
        nickname: String = "原昵称",
        bio: String = "原简介",
    ) = UserSelfResource(
        id = "user-1",
        phoneMasked = "138****0000",
        nickname = nickname,
        avatarUrl = "https://cdn.orbexa.cc/avatar.jpg",
        bio = bio,
        status = "ACTIVE",
        identityStatus = "VERIFIED",
        membershipStatus = null,
        createdAt = "2026-07-25T12:00:00Z",
        version = version,
    )
}
