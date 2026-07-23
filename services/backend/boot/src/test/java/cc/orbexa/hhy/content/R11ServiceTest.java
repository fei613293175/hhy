package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.ContentContracts.ContentResource;
import cc.orbexa.hhy.content.R08Contracts.ContactInput;
import cc.orbexa.hhy.content.R08Contracts.CreateProjectRequest;
import cc.orbexa.hhy.content.R08Contracts.PatchProjectRequest;
import cc.orbexa.hhy.content.R11Contracts.TeamLeaderAttributes;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class R11ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-24T02:00:00Z");
    private static final String KEY = "r11-test-key-0001";
    @Mock private R11Store store;
    @Mock private R08Store shared;
    @Mock private ContentService content;
    private R11Service service;

    @BeforeEach
    void setUp() {
        service = new R11Service(store, shared, content,
                new ContentContactCipher("r11-test-contact-root-secret-at-least-32-characters"),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPersistsTypedDraftAndEncryptedContacts() {
        allowCreate();
        when(store.createTeamLeader(eq(11L), anyString(), any(), anyString(), any(), any(),
                anyString(), eq(List.of(91L)), eq(NOW))).thenReturn(71L);
        ContentResource resource = resource("71", "DRAFT", 0);
        when(content.detail("71")).thenReturn(resource);

        assertEquals(resource, service.create(11, request(), KEY));

        ArgumentCaptor<List<R08Store.ContactWrite>> contacts = ArgumentCaptor.forClass(List.class);
        verify(shared).replaceContacts(eq(71L), contacts.capture(), eq(NOW));
        assertEquals("WECHAT", contacts.getValue().getFirst().channel());
        assertFalse(contacts.getValue().getFirst().valueCipher().contains("team-owner"));
        verify(shared).outbox(11, "CONTENT", "content.team-leader.created.v1", "71", "DRAFT", NOW);
    }

    @Test
    void oneAccountAndUnknownAttributesFailBeforeBusinessWrites() {
        when(shared.identityVerified(11)).thenReturn(true);
        CreateProjectRequest unknown = new CreateProjectRequest("TEAM_LEADER", "团队", null, "说明",
                "TEAM", "CN-11", List.of(), List.of(), Map.of("unregistered", "value"));
        assertEquals("COMMON-400-VALIDATION",
                assertThrows(BusinessException.class, () -> service.create(11, unknown, KEY)).code());

        allowCreateBase();
        when(store.lockOwnerTeamLeader(11)).thenReturn(Optional.of(70L));
        assertEquals("COMMON-422-BUSINESS_RULE",
                assertThrows(BusinessException.class, () -> service.create(11, request(), KEY)).code());
        verify(store, never()).createTeamLeader(anyLong(), anyString(), any(), anyString(), any(), any(),
                anyString(), any(), any());
    }

    @Test
    void retryReplaysSnapshotWithoutDuplicateWrites() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.ownsReadyMedia(11, List.of(91L))).thenReturn(true);
        when(shared.integerConfig("content.team_leader_per_account")).thenReturn(1);
        when(store.lockOwnerTeamLeader(11)).thenReturn(Optional.empty());
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.createTeamLeader(eq(11L), anyString(), any(), anyString(), any(), any(),
                anyString(), eq(List.of(91L)), eq(NOW))).thenReturn(71L);
        ContentResource resource = resource("71", "DRAFT", 0);
        when(content.detail("71")).thenReturn(resource);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> responseType = new AtomicReference<>();
        AtomicReference<String> responsePayload = new AtomicReference<>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(2);
            if (calls.getAndIncrement() == 0) return new R08Store.IdempotencyClaim(9, hash, null, null, null, false);
            return new R08Store.IdempotencyClaim(9, hash, "r11.content-resource.v1:ok",
                    responseType.get(), responsePayload.get(), true);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(9L), anyString(), anyString(), anyString());

        assertEquals(resource, service.create(11, request(), KEY));
        assertEquals(resource, service.create(11, request(), KEY));
        verify(store, times(1)).createTeamLeader(anyLong(), anyString(), any(), anyString(), any(), any(),
                anyString(), any(), any());
        verify(shared, times(1)).outbox(11, "CONTENT", "content.team-leader.created.v1", "71", "DRAFT", NOW);
    }

    @Test
    void stalePatchCannotEmitSuccessSideEffects() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(store.teamLeader(71)).thenReturn(Optional.of(row("DRAFT", 3)));
        PatchProjectRequest stale = new PatchProjectRequest("新标题", null, null, null, null,
                null, null, null, 2L);
        assertEquals("COMMON-409-VERSION_CONFLICT",
                assertThrows(BusinessException.class, () -> service.patch(11, "71", stale, KEY)).code());
        verify(shared, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private void allowCreate() {
        allowCreateBase();
        when(store.lockOwnerTeamLeader(11)).thenReturn(Optional.empty());
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
    }

    private void allowCreateBase() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.ownsReadyMedia(11, List.of(91L))).thenReturn(true);
        when(shared.integerConfig("content.team_leader_per_account")).thenReturn(1);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(1, invocation.getArgument(2), null, null, null, false));
    }

    private static CreateProjectRequest request() {
        return new CreateProjectRequest("TEAM_LEADER", "合伙团队", "摘要", "团队详细说明", "TEAM", "CN-11",
                List.of("91"), List.of(new ContactInput("WECHAT", "team-owner")),
                Map.of("teamName", "合伙团队", "nickname", "负责人", "logoMediaId", "91",
                        "sizeRange", "10-20", "skills", "产品与运营", "cooperationTypes", "联合创业",
                        "cooperationRequirement", "诚信合作", "pastCases", List.of("案例一"),
                        "acceptPrivateChat", true));
    }

    private static R11Store.TeamLeaderRow row(String status, long version) {
        TeamLeaderAttributes attributes = new TeamLeaderAttributes("合伙团队", "负责人", 91L,
                null, null, "10-20", "产品与运营", "联合创业", "诚信合作", List.of("案例一"), true);
        return new R11Store.TeamLeaderRow(71, 11, "TEAM_LEADER", status, version, "合伙团队", "摘要",
                "TEAM", "CN-11", attributes,
                "{\"description\":\"团队详细说明\",\"categoryCode\":\"TEAM\",\"regionCode\":\"CN-11\"}");
    }

    private static ContentResource resource(String id, String status, long version) {
        return new ContentResource(id, "TEAM_LEADER", "合伙团队", "摘要", "团队详细说明", "TEAM", "CN-11",
                List.of(), null, List.of(), status, null, null, NOW, NOW, version,
                Map.of("teamName", "合伙团队"));
    }
}
