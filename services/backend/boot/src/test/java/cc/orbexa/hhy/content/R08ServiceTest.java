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
import cc.orbexa.hhy.content.R08Contracts.DirectConversationRequest;
import cc.orbexa.hhy.content.R08Contracts.FavoriteRequest;
import cc.orbexa.hhy.content.R08Contracts.ShareRequest;
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
class R08ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T02:00:00Z");
    private static final String KEY = "r08-test-key-0001";
    @Mock private R08Store store;
    @Mock private ContentService content;
    private ContentContactCipher cipher;
    private R08Service service;

    @BeforeEach
    void setUp() {
        cipher = new ContentContactCipher("r08-test-contact-root-secret-at-least-32-characters");
        service = new R08Service(store, content, cipher,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createRequiresVerifiedOwnerEncryptsContactsAndWritesOneOutboxEvent() {
        when(store.identityVerified(11)).thenReturn(true);
        when(store.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(store.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.ownsReadyMedia(11, List.of())).thenReturn(true);
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(1, invocation.getArgument(2), null, null, null, false));
        when(store.createProject(anyLong(), anyString(), any(), anyString(), anyString(), any(),
                any(), any(), anyString(), any(), eq(NOW))).thenReturn(42L);
        ContentResource resource = resource("42", "DRAFT", 0);
        when(content.detail("42")).thenReturn(resource);

        var result = service.create(11, new CreateProjectRequest(
                "PROJECT", "合作项目", "摘要", "详细说明", "COOP", "CN-11",
                List.of(), List.of(new ContactInput("EMAIL", "owner@example.com")), Map.of()), KEY);

        assertEquals(resource, result);
        ArgumentCaptor<List<R08Store.ContactWrite>> contacts = ArgumentCaptor.forClass(List.class);
        verify(store).replaceContacts(eq(42L), contacts.capture(), eq(NOW));
        assertEquals("EMAIL", contacts.getValue().getFirst().channel());
        assertFalse(contacts.getValue().getFirst().valueCipher().contains("owner@example.com"));
        assertEquals("o***@example.com", contacts.getValue().getFirst().displayMask());
        verify(store).outbox(11, "CONTENT", "content.project.created.v1", "42", "DRAFT", NOW);
        verify(store).complete(eq(1L), anyString(), eq("r08.content-resource.v1"), anyString());
    }

    @Test
    void unverifiedCreateIsRejectedBeforeLimitsIdempotencyOrWrites() {
        when(store.identityVerified(11)).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(
                11, new CreateProjectRequest("PROJECT", "标题", null, "说明", "C", null,
                        List.of(), List.of(), Map.of()), KEY));
        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(store, never()).integerConfig(anyString());
        verify(store, never()).claim(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shareReturnsConfiguredHttpsUrlAndRecordsDomainAudit() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(2, invocation.getArgument(2), null, null, null, false));
        when(store.project(42)).thenReturn(Optional.of(project(42, 7, "ONLINE", 3)));
        when(store.textConfig("domain.h5.host")).thenReturn("h5.orbexa.cc");

        var result = service.share(11, "42", new ShareRequest("copy_link"), KEY);

        assertEquals("https://h5.orbexa.cc/share/project/42", result.url());
        assertEquals("COPY_LINK", result.channel());
        verify(store).share(11, 42, "COPY_LINK", NOW);
        verify(store).outbox(11, "CONTENT", "content.shared.v1", "42", "COPY_LINK", NOW);
    }

    @Test
    void favoriteVersionConflictStopsFavoriteOutboxAndCompletion() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(12, invocation.getArgument(2), null, null, null, false));
        when(store.lockProject(42)).thenReturn(Optional.of(project(42, 7, "ONLINE", 3)));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.favorite(11, "42", new FavoriteRequest(null, 2L, Map.of()), KEY));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        verify(store, never()).favorite(anyLong(), anyLong(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shareReplayReturnsFrozenSnapshotWithoutDuplicateAuditOrOutbox() {
        AtomicInteger claims = new AtomicInteger();
        AtomicReference<String> responseType = new AtomicReference<>();
        AtomicReference<String> responsePayload = new AtomicReference<>();
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            boolean replay = claims.getAndIncrement() > 0;
            return new R08Store.IdempotencyClaim(13, requestHash,
                    replay ? "stored" : null, replay ? responseType.get() : null,
                    replay ? responsePayload.get() : null, replay);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(store).complete(eq(13L), anyString(), anyString(), anyString());
        when(store.project(42)).thenReturn(Optional.of(project(42, 7, "ONLINE", 3)));
        when(store.textConfig("domain.h5.host")).thenReturn("h5.orbexa.cc");
        var request = new ShareRequest("copy_link");

        var first = service.share(11, "42", request, KEY);
        var replay = service.share(11, "42", request, KEY);

        assertEquals(first, replay);
        verify(store, times(1)).share(11, 42, "COPY_LINK", NOW);
        verify(store, times(1)).outbox(11, "CONTENT", "content.shared.v1", "42", "COPY_LINK", NOW);
        verify(store, times(1)).project(42);
        verify(store, times(1)).complete(eq(13L), anyString(), eq("r08.share-result.v1"), anyString());
    }

    @Test
    void directConversationRejectsEitherDirectionBlockBeforeClaim() {
        when(store.activeUser(11)).thenReturn(true);
        when(store.activeUser(7)).thenReturn(true);
        when(store.blockedEitherWay(11, 7)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class, () -> service.direct(
                11, new DirectConversationRequest("7", null), KEY));

        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(store, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).createDirectConversation(anyLong(), anyLong(), any());
    }

    @Test
    void completedCreateReplayReturnsFrozenSnapshotWithoutDuplicateOutbox() {
        when(store.identityVerified(11)).thenReturn(true);
        when(store.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(store.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.ownsReadyMedia(11, List.of())).thenReturn(true);
        when(store.createProject(anyLong(), anyString(), any(), anyString(), anyString(), any(),
                any(), any(), anyString(), any(), eq(NOW))).thenReturn(42L);
        ContentResource resource = resource("42", "DRAFT", 0);
        when(content.detail("42")).thenReturn(resource);
        var calls = new AtomicInteger();
        var responseType = new AtomicReference<String>();
        var responsePayload = new AtomicReference<String>();
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation -> {
            String requestHash = invocation.getArgument(2);
            if (calls.getAndIncrement() == 0) {
                return new R08Store.IdempotencyClaim(9, requestHash, null, null, null, false);
            }
            return new R08Store.IdempotencyClaim(
                    9, requestHash, "r08.content-resource.v1:ok",
                    responseType.get(), responsePayload.get(), true);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            responseType.set(invocation.getArgument(2));
            responsePayload.set(invocation.getArgument(3));
            return null;
        }).when(store).complete(eq(9L), anyString(), anyString(), anyString());
        var request = new CreateProjectRequest(
                "PROJECT", "合作项目", "摘要", "详细说明", "COOP", "CN-11",
                List.of(), List.of(new ContactInput("EMAIL", "owner@example.com")), Map.of());

        assertEquals(resource, service.create(11, request, KEY));
        assertEquals(resource, service.create(11, request, KEY));

        verify(store, times(1)).createProject(anyLong(), anyString(), any(), anyString(), anyString(), any(),
                any(), any(), anyString(), any(), eq(NOW));
        verify(store, times(1)).outbox(11, "CONTENT", "content.project.created.v1", "42", "DRAFT", NOW);
        verify(content, times(1)).detail("42");
        verify(store, times(1)).complete(eq(9L), anyString(), anyString(), anyString());
    }

    @Test
    void mediaStorageTimeoutStopsCreateBeforeLimitsWritesOutboxAndCompletion() {
        when(store.identityVerified(11)).thenReturn(true);
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(10, invocation.getArgument(2), null, null, null, false));
        when(store.ownsReadyMedia(11, List.of(99L)))
                .thenThrow(new IllegalStateException("media storage timeout"));
        var request = new CreateProjectRequest(
                "PROJECT", "合作项目", null, "详细说明", "COOP", null,
                List.of("99"), List.of(), Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.create(11, request, KEY));

        assertEquals("media storage timeout", error.getMessage());
        verify(store, never()).integerConfig(anyString());
        verify(store, never()).createProject(anyLong(), anyString(), any(), anyString(), anyString(), any(),
                any(), any(), anyString(), any(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shareConfigurationProviderTimeoutLeavesNoShareOutboxOrCompletion() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(11, invocation.getArgument(2), null, null, null, false));
        when(store.project(42)).thenReturn(Optional.of(project(42, 7, "ONLINE", 3)));
        when(store.textConfig("domain.h5.host"))
                .thenThrow(new IllegalStateException("configuration provider timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.share(11, "42", new ShareRequest("copy_link"), KEY));

        assertEquals("configuration provider timeout", error.getMessage());
        verify(store, never()).share(anyLong(), anyLong(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString(), anyString(), anyString());
    }

    private static R08Store.ProjectRow project(long id, long owner, String status, long version) {
        return new R08Store.ProjectRow(id, owner, "PROJECT", status, version, "项目", "摘要",
                "说明", "COOP", "CN-11", null, null,
                "{\"description\":\"说明\",\"categoryCode\":\"COOP\",\"regionCode\":\"CN-11\"}");
    }

    private static ContentResource resource(String id, String status, long version) {
        return new ContentResource(id, "PROJECT", "项目", "摘要", "说明", "COOP", "CN-11",
                List.of(), null, List.of(), status, null, null, NOW, NOW, version, Map.of());
    }
}
