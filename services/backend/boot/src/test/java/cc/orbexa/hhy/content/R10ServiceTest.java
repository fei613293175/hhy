package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class R10ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-23T06:30:00Z");
    private static final String KEY = "r10-test-key-0001";
    @Mock private R10Store store;
    @Mock private R08Store shared;
    @Mock private ContentService content;
    private R10Service service;

    @BeforeEach
    void setUp() {
        service = new R10Service(store, shared, content,
                new ContentContactCipher("r10-test-contact-root-secret-at-least-32-characters"),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPersistsTypedGroupAndEncryptsJoinPasswordSeparately() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.ownsReadyMedia(11, List.of(91L))).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(1, invocation.getArgument(2), null, null, null, false));
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.createGroup(anyLong(), anyString(), any(), anyString(), any(), any(), eq(91L),
                any(), any(), anyString(), any(), eq(NOW))).thenReturn(61L);
        ContentResource resource = resource("61", "DRAFT", 0);
        when(content.detail("61")).thenReturn(resource);

        ContentResource result = service.create(11, request(), KEY);

        assertEquals(resource, result);
        ArgumentCaptor<List<R08Store.ContactWrite>> contacts = ArgumentCaptor.forClass(List.class);
        verify(shared).replaceContacts(eq(61L), contacts.capture(), eq(NOW));
        assertEquals(List.of("WECHAT", "JOIN_PASSWORD"),
                contacts.getValue().stream().map(R08Store.ContactWrite::channel).toList());
        R08Store.ContactWrite password = contacts.getValue().get(1);
        assertEquals("口令***", password.displayMask());
        assertFalse(password.valueCipher().contains("加群口令888"));
        verify(shared).outbox(11, "CONTENT", "content.group.created.v1", "61", "DRAFT", NOW);
        verify(shared).complete(eq(1L), anyString(), eq("r10.content-resource.v1"), anyString());
    }

    @Test
    void duplicateOrUnsupportedGroupContactIsRejectedBeforeClaim() {
        when(shared.identityVerified(11)).thenReturn(true);
        var duplicate = new CreateProjectRequest("GROUP_CHAT", "群聊", null, "说明", "COMMUNITY", null,
                List.of(), List.of(new ContactInput("JOIN_PASSWORD", "一"),
                new ContactInput("join_password", "二")), Map.of("platform", "微信"));
        BusinessException duplicateError = assertThrows(BusinessException.class,
                () -> service.create(11, duplicate, KEY));
        assertEquals("COMMON-400-VALIDATION", duplicateError.code());

        var unsupported = new CreateProjectRequest("GROUP_CHAT", "群聊", null, "说明", "COMMUNITY", null,
                List.of(), List.of(new ContactInput("LINK", "https://example.invalid")),
                Map.of("platform", "微信"));
        BusinessException unsupportedError = assertThrows(BusinessException.class,
                () -> service.create(11, unsupported, KEY));
        assertEquals("COMMON-400-VALIDATION", unsupportedError.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
    }

    @Test
    void insecureGroupLinkAndUnownedQrAreRejectedWithoutBusinessWrites() {
        when(shared.identityVerified(11)).thenReturn(true);
        var insecure = new CreateProjectRequest("GROUP_CHAT", "群聊", null, "说明", "COMMUNITY", null,
                List.of(), List.of(new ContactInput("WECHAT", "owner")),
                Map.of("platform", "微信", "groupLink", "http://example.invalid/join"));
        assertEquals("COMMON-400-VALIDATION",
                assertThrows(BusinessException.class, () -> service.create(11, insecure, KEY)).code());

        when(shared.ownsReadyMedia(11, List.of(91L))).thenReturn(false);
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.create(11, request(), KEY));
        assertEquals("COMMON-403-FORBIDDEN", forbidden.code());
        verify(shared, never()).integerConfig(anyString());
        verify(store, never()).createGroup(anyLong(), anyString(), any(), anyString(), any(), any(), any(),
                any(), any(), anyString(), any(), any());
    }

    @Test
    void networkRetryReplaysEncryptedSnapshotWithoutDuplicateOutbox() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.ownsReadyMedia(11, List.of(91L))).thenReturn(true);
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.createGroup(anyLong(), anyString(), any(), anyString(), any(), any(), eq(91L),
                any(), any(), anyString(), any(), eq(NOW))).thenReturn(61L);
        ContentResource resource = resource("61", "DRAFT", 0);
        when(content.detail("61")).thenReturn(resource);
        var calls = new AtomicInteger();
        var responseType = new AtomicReference<String>();
        var responsePayload = new AtomicReference<String>();
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            if (calls.getAndIncrement() == 0) {
                return new R08Store.IdempotencyClaim(9, requestHash, null, null, null, false);
            }
            return new R08Store.IdempotencyClaim(9, requestHash, "r10.content-resource.v1:ok",
                    responseType.get(), responsePayload.get(), true);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(shared).complete(eq(9L), anyString(), anyString(), anyString());

        assertEquals(resource, service.create(11, request(), KEY));
        assertEquals(resource, service.create(11, request(), KEY));

        verify(store, times(1)).createGroup(anyLong(), anyString(), any(), anyString(), any(), any(), eq(91L),
                any(), any(), anyString(), any(), eq(NOW));
        verify(shared, times(1)).outbox(11, "CONTENT", "content.group.created.v1", "61", "DRAFT", NOW);
    }

    @Test
    void staleOrConcurrentPatchCannotEmitSuccessSideEffects() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(store.group(61)).thenReturn(Optional.of(group("DRAFT", 3)));
        var stale = new PatchProjectRequest("新标题", null, null, null, null,
                null, null, null, 2L);
        assertEquals("COMMON-409-VERSION_CONFLICT",
                assertThrows(BusinessException.class, () -> service.patch(11, "61", stale, KEY)).code());

        var patch = new PatchProjectRequest("新标题", null, null, null, null,
                null, null, null, 3L);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(12, invocation.getArgument(2), null, null, null, false));
        when(store.lockGroup(61)).thenReturn(Optional.of(group("DRAFT", 3)));
        when(store.updateGroup(anyLong(), anyLong(), anyString(), any(), anyString(), any(), any(), any(),
                any(), any(), anyString(), any(), eq(false), any(), anyLong())).thenReturn(false);
        assertEquals("COMMON-409-VERSION_CONFLICT",
                assertThrows(BusinessException.class, () -> service.patch(11, "61", patch, KEY)).code());
        verify(shared, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(shared, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void publicShareContainsOnlyNonSensitiveGroupFacts() {
        when(store.group(61)).thenReturn(Optional.of(group("ONLINE", 3)));
        when(content.publicDetail("61")).thenReturn(resource("61", "ONLINE", 3));
        when(shared.textConfig("domain.h5.host")).thenReturn("h5.orbexa.cc");

        var page = service.publicShare("61");

        assertEquals("GROUP", page.code());
        assertNull(page.download());
        String serialized = page.toString();
        assertFalse(serialized.contains("加群口令888"));
        assertFalse(serialized.contains("group.example.invalid"));
        assertFalse(serialized.contains("998877"));
        assertEquals("https://h5.orbexa.cc/share/group/61", page.seoMetadata().canonicalUrl());
    }

    private static CreateProjectRequest request() {
        return new CreateProjectRequest("GROUP_CHAT", "合伙交流群", "摘要", "群聊说明", "COMMUNITY", "CN-11",
                List.of(), List.of(new ContactInput("WECHAT", "owner-wechat"),
                new ContactInput("JOIN_PASSWORD", "加群口令888")),
                Map.of("platform", "微信", "sizeRange", "100-200", "joinRequirement", "实名行业人士",
                        "qrMediaId", "91", "groupLink", "https://group.example.invalid/join",
                        "groupNo", "998877"));
    }

    private static R10Store.GroupRow group(String status, long version) {
        return new R10Store.GroupRow(61, 11, "GROUP", status, version, "合伙交流群", "摘要",
                "微信", "100-200", "实名行业人士", 91L,
                "https://group.example.invalid/join", "998877",
                "{\"platform\":\"微信\",\"description\":\"群聊说明\",\"categoryCode\":\"COMMUNITY\"}");
    }

    private static ContentResource resource(String id, String status, long version) {
        return new ContentResource(id, "GROUP_CHAT", "合伙交流群", "摘要", "群聊说明", "COMMUNITY", "CN-11",
                List.of(), null, List.of(), status, null, null, NOW, NOW, version,
                Map.of("platform", "微信"));
    }
}
