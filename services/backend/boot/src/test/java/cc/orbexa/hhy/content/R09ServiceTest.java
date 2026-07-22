package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class R09ServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T18:00:00Z");
    private static final String KEY = "r09-test-key-0001";
    @Mock private R09Store store;
    @Mock private R08Store shared;
    @Mock private ContentService content;
    private R09Service service;

    @BeforeEach
    void setUp() {
        service = new R09Service(store, shared, content,
                new ContentContactCipher("r09-test-contact-root-secret-at-least-32-characters"),
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPersistsTypedAppEncryptsContactsAndWritesSingleOutbox() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(1, invocation.getArgument(2), null, null, null, false));
        when(store.ownsReadyNonApkMedia(11, List.of())).thenReturn(true);
        when(shared.integerConfig("content.limit.normal.drafts")).thenReturn(10);
        when(shared.countOwnedInStatus(11, "DRAFT")).thenReturn(0L);
        when(store.createApp(anyLong(), anyString(), any(), anyString(), any(), any(), any(), any(),
                anyString(), any(), eq(NOW))).thenReturn(51L);
        ContentResource resource = resource("51", "DRAFT", 0);
        when(content.detail("51")).thenReturn(resource);

        ContentResource result = service.create(11, request(Map.of(
                "appName", "合伙云伙伴", "platform", "ANDROID", "versionText", "1.0.0",
                "downloadUrl", "https://download.example.invalid/app",
                "website", "https://app.example.invalid")), KEY);

        assertEquals(resource, result);
        ArgumentCaptor<List<R08Store.ContactWrite>> contacts = ArgumentCaptor.forClass(List.class);
        verify(shared).replaceContacts(eq(51L), contacts.capture(), eq(NOW));
        assertFalse(contacts.getValue().getFirst().valueCipher().contains("owner@example.com"));
        verify(shared).outbox(11, "CONTENT", "content.app.created.v1", "51", "DRAFT", NOW);
        verify(shared).complete(eq(1L), anyString(), eq("r09.content-resource.v1"), anyString());
    }

    @Test
    void unverifiedOwnerIsRejectedBeforeIdempotencyOrWrites() {
        when(shared.identityVerified(11)).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(11, request(Map.of("appName", "App")), KEY));
        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).createApp(anyLong(), anyString(), any(), anyString(), any(), any(), any(), any(),
                anyString(), any(), any());
    }

    @Test
    void insecureExternalDownloadUrlIsRejectedWithoutSideEffects() {
        when(shared.identityVerified(11)).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class, () -> service.create(
                11, request(Map.of("appName", "App", "downloadUrl", "http://example.invalid/app.apk")), KEY));
        assertEquals("COMMON-400-VALIDATION", error.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
    }

    @Test
    void apkOrUnownedMediaIsRejectedBeforeLimitAndDatabaseWrite() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(shared.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new R08Store.IdempotencyClaim(3, invocation.getArgument(2), null, null, null, false));
        when(store.ownsReadyNonApkMedia(11, List.of(99L))).thenReturn(false);
        var request = new CreateProjectRequest("APP", "App", null, "介绍", "TOOLS", null,
                List.of("99"), List.of(), Map.of("appName", "App"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(11, request, KEY));

        assertEquals("COMMON-403-FORBIDDEN", error.code());
        verify(shared, never()).integerConfig(anyString());
        verify(store, never()).createApp(anyLong(), anyString(), any(), anyString(), any(), any(), any(), any(),
                anyString(), any(), any());
    }

    @Test
    void patchRejectsStaleVersionBeforeClaimOrWrite() {
        when(shared.identityVerified(11)).thenReturn(true);
        when(store.app(51)).thenReturn(Optional.of(app(51, 11, "DRAFT", 3)));
        var request = new PatchProjectRequest("新标题", null, null, null, null,
                null, null, null, 2L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.patch(11, "51", request, KEY));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        verify(shared, never()).claim(anyString(), anyString(), anyString(), any());
        verify(store, never()).updateApp(anyLong(), anyLong(), anyString(), any(), anyString(), any(), any(), any(),
                any(), anyString(), any(), anyBoolean(), any(), anyLong());
    }

    @Test
    void publicShareContainsOnlyExternalDownloadMetadataAndAppRoute() {
        when(store.app(51)).thenReturn(Optional.of(app(51, 11, "ONLINE", 3)));
        when(content.publicDetail("51")).thenReturn(resource("51", "ONLINE", 3));
        when(shared.textConfig("domain.h5.host")).thenReturn("h5.orbexa.cc");

        var page = service.publicShare("51");

        assertEquals("APP", page.code());
        assertEquals("https://download.example.invalid/app", ((Map<?, ?>) page.download()).get("externalUrl"));
        assertEquals("https://app.example.invalid", ((Map<?, ?>) page.download()).get("website"));
        assertEquals("https://h5.orbexa.cc/share/app/51", page.seoMetadata().canonicalUrl());
    }

    private static CreateProjectRequest request(Map<String, Object> attributes) {
        return new CreateProjectRequest("APP", "伙伴App", "摘要", "介绍", "TOOLS", "CN-11",
                List.of(), List.of(new ContactInput("EMAIL", "owner@example.com")), attributes);
    }

    private static R09Store.AppRow app(long id, long owner, String status, long version) {
        return new R09Store.AppRow(id, owner, "APP", status, version, "伙伴App", "摘要",
                "合伙云伙伴", "ANDROID", "1.0.0", "https://download.example.invalid/app",
                "https://app.example.invalid",
                "{\"appName\":\"合伙云伙伴\",\"description\":\"介绍\",\"categoryCode\":\"TOOLS\"}");
    }

    private static ContentResource resource(String id, String status, long version) {
        return new ContentResource(id, "APP", "伙伴App", "摘要", "介绍", "TOOLS", "CN-11",
                List.of(), null, List.of(), status, null, null, NOW, NOW, version,
                Map.of("appName", "合伙云伙伴"));
    }
}
