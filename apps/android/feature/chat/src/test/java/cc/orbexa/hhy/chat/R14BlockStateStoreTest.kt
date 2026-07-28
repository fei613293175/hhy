package cc.orbexa.hhy.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R14BlockStateStoreTest {
    @Test
    fun compositeKeyCannotCollideAcrossAccountsOrPeers() {
        assertNotEquals(blockStateKey("12", "3"), blockStateKey("1", "23"))
        assertNotEquals(blockStateKey("current-a", "peer"), blockStateKey("current-b", "peer"))
        assertNotEquals(blockStateKey("current", "peer-a"), blockStateKey("current", "peer-b"))
        assertEquals("7:current:4:peer", blockStateKey("current", "peer"))
    }

    @Test
    fun blankIdentityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { blockStateKey("", "peer") }
        assertThrows(IllegalArgumentException::class.java) { blockStateKey("current", "") }
    }
}
