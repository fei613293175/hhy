package cc.orbexa.hhy.incentive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.incentive.R12RewardContracts.RewardAccountResource;
import cc.orbexa.hhy.shared.api.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class R12RewardServiceTest {
    private static final Instant UPDATED_AT = Instant.parse("2026-07-25T03:00:00Z");
    private final R12RewardStore store = mock(R12RewardStore.class);
    private final R12RewardService service = new R12RewardService(store);

    @Test
    void verifiedUserReceivesIntegerCentBalancesFromTheAuthority() {
        R12RewardStore.RewardAccountRow account = new R12RewardStore.RewardAccountRow(
                15, 120, 340, 50, 90, 6, UPDATED_AT);
        when(store.find(7)).thenReturn(Optional.of(
                new R12RewardStore.RewardLookup(7, "VERIFIED", false, account)));

        RewardAccountResource result = service.current(7);

        assertEquals("7", result.userId());
        assertEquals(120, result.pendingCent());
        assertEquals(340, result.availableCent());
        assertEquals(50, result.frozenCent());
        assertEquals(6, result.version());
    }

    @Test
    void unverifiedIdentityUsesTheFrozenIdentityErrorCode() {
        when(store.find(7)).thenReturn(Optional.of(
                new R12RewardStore.RewardLookup(7, "IN_PROGRESS", false, null)));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.current(7));

        assertEquals("IDENTITY-422-NOT_VERIFIED", failure.code());
        assertEquals(422, failure.httpStatus());
    }

    @Test
    void canonicalRiskFrozenLedgerStateBlocksTheProjection() {
        R12RewardStore.RewardAccountRow account = new R12RewardStore.RewardAccountRow(
                15, 120, 0, 340, 0, 7, UPDATED_AT);
        when(store.find(7)).thenReturn(Optional.of(
                new R12RewardStore.RewardLookup(7, "VERIFIED", true, account)));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.current(7));

        assertEquals("REWARD-423-RISK_FROZEN", failure.code());
        assertEquals(423, failure.httpStatus());
    }
}
