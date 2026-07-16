package cc.orbexa.hhy.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestIdPolicyTest {
    @Test
    fun acceptsStableIdentifiers() {
        assertTrue(RequestIdPolicy.isValid("request_1234"))
    }

    @Test
    fun rejectsUnsafeOrShortIdentifiers() {
        assertFalse(RequestIdPolicy.isValid("bad id"))
        assertFalse(RequestIdPolicy.isValid("short"))
        assertFalse(RequestIdPolicy.isValid(null))
    }
}
