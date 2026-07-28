package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.R07CallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R14ChatActionStateTest {
    @Test fun `writes are mutually exclusive`() {
        val state = R14ChatActionState().started(R14ChatAction.BLOCK)
        assertFalse(state.canSubmit())
        assertThrows(IllegalArgumentException::class.java) { state.started(R14ChatAction.DELETE_CONVERSATION) }
    }

    @Test fun `block unblock and delete preserve server outcome`() {
        val blocked = R14ChatActionState().started(R14ChatAction.BLOCK).succeeded(R14ChatAction.BLOCK)
        assertTrue(blocked.blocked)
        val unblocked = blocked.started(R14ChatAction.UNBLOCK).succeeded(R14ChatAction.UNBLOCK)
        assertFalse(unblocked.blocked)
        assertTrue(unblocked.started(R14ChatAction.DELETE_CONVERSATION).succeeded(R14ChatAction.DELETE_CONVERSATION).deleted)
    }

    @Test fun `action failures use user safe messages`() {
        assertEquals("状态已经变化，请刷新后重新确认", R07CallResult.Failure(409).r14ActionMessage())
        assertEquals("操作较频繁，请 9 秒后重试", R07CallResult.Failure(429, retryAfterSeconds = 9).r14ActionMessage())
    }
}
