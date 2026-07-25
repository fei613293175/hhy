package cc.orbexa.hhy.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.commerce.R12MembershipContracts.BenefitResource;
import cc.orbexa.hhy.commerce.R12MembershipContracts.MembershipResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R12MembershipServiceTest {
    private final R12MembershipStore store = mock(R12MembershipStore.class);
    private final R12MembershipService service = new R12MembershipService(store);

    @Test
    void mapsOnlyTheAuthoritativeCurrentMembershipProjection() {
        Instant startsAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2027-01-01T00:00:00Z");
        List<BenefitResource> benefits = List.of(
                new BenefitResource("PUBLISH_LIMIT", "发布额度", 20, null));
        when(store.findCurrent(7)).thenReturn(Optional.of(new R12MembershipStore.MembershipRow(
                11, "PRO-YEAR", "Pro年卡", "ACTIVE", startsAt, expiresAt,
                benefits, 36500L, 18000L, 3)));

        MembershipResource result = service.current(7);

        assertEquals("11", result.id());
        assertEquals("PRO-YEAR", result.skuId());
        assertEquals(benefits, result.benefits());
        assertEquals(3, result.version());
    }

    @Test
    void absenceIsNotReplacedWithAnInventedMembership() {
        when(store.findCurrent(7)).thenReturn(Optional.empty());

        BusinessException failure = assertThrows(BusinessException.class, () -> service.current(7));

        assertEquals("COMMON-404-NOT_FOUND", failure.code());
        assertEquals(404, failure.httpStatus());
    }
}
