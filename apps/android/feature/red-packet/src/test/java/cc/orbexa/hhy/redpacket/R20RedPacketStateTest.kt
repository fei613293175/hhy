package cc.orbexa.hhy.redpacket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R20RedPacketStateTest {
    @Test fun `draft and rejected campaign can submit review`() {
        assertTrue(r20CanSubmitReview("DRAFT", 0))
        assertTrue(r20CanSubmitReview("PRE_REVIEW_REJECTED", 1))
        assertFalse(r20CanSubmitReview("PRE_REVIEWING", 1))
    }

    @Test fun `only an approved campaign can request a quote`() {
        assertTrue(r20CanRequestQuote("PRE_REVIEW_APPROVED", 2))
        assertFalse(r20CanRequestQuote("PRE_REVIEWING", 2))
    }

    @Test fun `only a live quote for an approved campaign can create an order`() {
        assertTrue(r20CanOrder("QUOTED", "PRE_REVIEW_APPROVED", 2))
        assertFalse(r20CanOrder("EXPIRED", "PRE_REVIEW_APPROVED", 2))
        assertFalse(r20CanOrder("QUOTED", "DRAFT", 2))
    }
}
