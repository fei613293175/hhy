package cc.orbexa.hhy.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.content.ContentContracts.StatusRequest;
import cc.orbexa.hhy.shared.api.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");
    private static final String KEY = "r06-content-idem-0001";

    @Mock
    private ContentStore store;
    private ContentService service;

    @BeforeEach
    void setUp() {
        service = new ContentService(
                store, new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listMapsFrozenGroupChatTypeToStoredGroupType() {
        when(store.page(any())).thenReturn(new ContentStore.PageRows(List.of(), 0, false));

        service.list(1, 20, null, null, null, "createdAt:desc", "GROUP_CHAT", null, null);

        ArgumentCaptor<ContentStore.ContentQuery> query = ArgumentCaptor.forClass(ContentStore.ContentQuery.class);
        verify(store).page(query.capture());
        assertEquals("GROUP", query.getValue().contentType());
        assertEquals("p.created_at DESC,p.id DESC", query.getValue().orderBy());
    }

    @Test
    void onlinePersistsTransitionHistoryAuditOutboxAndIdempotentResult() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new ContentStore.IdempotencyClaim(71, "hash", null, false));
        when(store.lock(42)).thenReturn(Optional.of(new ContentStore.LockedContent(42, "APPROVED", 3)));
        when(store.transition(42, 3, "ONLINE", NOW)).thenReturn(true);

        var result = service.online(7, "42", new StatusRequest(3L, "审核通过"), KEY, "req-1", "127.0.0.1");

        assertEquals("ONLINE", result.status());
        assertEquals(4, result.version());
        verify(store).statusLog(42, "APPROVED", "ONLINE", "审核通过", "admin:7");
        verify(store).audit(eq(7L), eq("CONTENT_ONLINE"), eq(42L), anyString(), anyString(), eq("127.0.0.1"));
        verify(store).outbox(7, "content.online.v1", "42", "ONLINE", NOW);
        verify(store).complete(eq(71L), anyString());
    }

    @Test
    void repeatedContentCommandReplaysStoredResultWithoutDuplicateMessage() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new ContentStore.IdempotencyClaim(
                        73, invocation.getArgument(2), "v1:42:ONLINE:4:1784534400000", true));

        var result = service.online(7, "42", new StatusRequest(3L, "审核通过"), KEY, "req-replay", "127.0.0.1");

        assertEquals("42", result.resourceId());
        assertEquals("ONLINE", result.status());
        assertEquals(4, result.version());
        verify(store, never()).lock(anyLong());
        verify(store, never()).transition(anyLong(), anyLong(), anyString(), any());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentContentCommandIsRejected() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new ContentStore.IdempotencyClaim(74, "different-request-hash", "v1:42:ONLINE:4:1784534400000", true));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.online(7, "42", new StatusRequest(3L, "审核通过"), KEY, "req-conflict", "127.0.0.1"));

        assertEquals("COMMON-409-IDEMPOTENCY_CONFLICT", error.code());
        verify(store, never()).lock(anyLong());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void inProgressDuplicateCommandCannotCrossConcurrencyGate() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenAnswer(invocation ->
                new ContentStore.IdempotencyClaim(75, invocation.getArgument(2), null, true));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.online(7, "42", new StatusRequest(3L, "审核通过"), KEY, "req-in-progress", "127.0.0.1"));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
        verify(store, never()).lock(anyLong());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void storageTimeoutStopsCommandBeforeAuditOutboxAndCompletion() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new ContentStore.IdempotencyClaim(76, "hash", null, false));
        when(store.lock(42)).thenReturn(Optional.of(new ContentStore.LockedContent(42, "APPROVED", 3)));
        when(store.transition(42, 3, "ONLINE", NOW)).thenThrow(new IllegalStateException("storage timeout"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                service.online(7, "42", new StatusRequest(3L, "审核通过"), KEY, "req-timeout", "127.0.0.1"));

        assertEquals("storage timeout", error.getMessage());
        verify(store, never()).audit(anyLong(), anyString(), anyLong(), any(), any(), anyString());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
        verify(store, never()).complete(anyLong(), anyString());
    }

    @Test
    void statusTransitionRejectsStaleExpectedVersion() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new ContentStore.IdempotencyClaim(72, "hash", null, false));
        when(store.lock(42)).thenReturn(Optional.of(new ContentStore.LockedContent(42, "APPROVED", 4)));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.online(7, "42", new StatusRequest(3L, null), KEY, "req-2", "127.0.0.1"));

        assertEquals("COMMON-409-VERSION_CONFLICT", error.code());
    }

    @Test
    void platformOfflineContentCannotBypassRectificationAndGoDirectlyOnline() {
        when(store.claim(anyString(), eq(KEY), anyString(), any())).thenReturn(
                new ContentStore.IdempotencyClaim(77, "hash", null, false));
        when(store.lock(42)).thenReturn(Optional.of(
                new ContentStore.LockedContent(42, "OFFLINE_BY_PLATFORM", 3)));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.online(7, "42", new StatusRequest(3L, "尝试直接恢复"),
                        KEY, "req-platform-offline", "127.0.0.1"));

        assertEquals("CONTENT-409-STATUS_TRANSITION", error.code());
        verify(store, never()).transition(anyLong(), anyLong(), anyString(), any());
        verify(store, never()).statusLog(anyLong(), any(), anyString(), any(), anyString());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void dictionaryStatusIsAppliedBeforePagingAndCounting() {
        when(store.dictionaries(2, 10, "region", false, "updated_at DESC,id DESC"))
                .thenReturn(List.of());
        when(store.dictionaryCount("region", false)).thenReturn(11L);

        var result = service.dictionaries(2, 10, null, "disabled", "region", "updatedAt:desc");

        assertEquals("11", result.page().total());
        assertEquals("false", result.page().hasMore());
    }

    @Test
    void homeExcludesModulesOutsideTheirFrozenSchedule() {
        when(store.homeModules()).thenReturn(List.of(
                new ContentStore.HomeRow(1, "active", "当前", "NOTICE",
                        "{\"startAt\":\"2026-07-20T07:00:00Z\",\"endAt\":\"2026-07-20T09:00:00Z\",\"items\":[]}"),
                new ContentStore.HomeRow(2, "future", "未来", "NOTICE",
                        "{\"startAt\":\"2026-07-20T09:00:00Z\",\"items\":[]}"),
                new ContentStore.HomeRow(3, "ended", "结束", "NOTICE",
                        "{\"endAt\":\"2026-07-20T08:00:00Z\",\"items\":[]}")));

        var result = service.home("req-home");

        assertEquals(List.of("active"), result.modules().stream()
                .map(module -> module.trackingContext().source()).toList());
        assertEquals(NOW, result.serverTime());
    }

    @Test
    void homeUsesRealDataSourceDeduplicatesContentAndMapsMoreTarget() {
        when(store.homeModules()).thenReturn(List.of(
                new ContentStore.HomeRow(1, "latest-projects", "最新发布", "VERTICAL_LIST", """
                        {"dataSource":"LATEST_PROJECTS","limit":2,"moreTarget":{
                          "targetType":"IN_APP_ROUTE","route":"/content/projects","requiresLogin":true}}
                        """),
                new ContentStore.HomeRow(2, "recommended-projects", "为你推荐", "HORIZONTAL_LIST", """
                        {"dataSource":"LATEST_PROJECTS","limit":2}
                        """)));
        when(store.homeContent(eq("PROJECT"), anyInt())).thenReturn(List.of(
                new ContentStore.HomeContentRow(9, "PROJECT", "真实项目九", "项目摘要", null),
                new ContentStore.HomeContentRow(8, "PROJECT", "真实项目八", null,
                        "https://download.orbexa.cc/project-8.png")));

        var result = service.home("req-home-data-source");

        assertEquals(List.of("9", "8"), result.modules().getFirst().items().stream()
                .map(item -> item.id()).toList());
        assertEquals("/content/project/9", result.modules().getFirst().items().getFirst().target().route());
        assertEquals("/content/projects", result.modules().getFirst().moreTarget().route());
        assertEquals(0, result.modules().get(1).items().size());
    }

    @Test
    void repeatedHomeReadIsStableAndDoesNotCreateMessages() {
        var rows = List.of(new ContentStore.HomeRow(1, "active", "当前", "NOTICE", "{\"items\":[]}"));
        when(store.homeModules()).thenReturn(rows);

        var first = service.home("req-home-repeat");
        var second = service.home("req-home-repeat");

        assertEquals(first, second);
        verify(store, times(2)).homeModules();
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void malformedCmsScheduleIsRejectedInsteadOfServingPartialHomeData() {
        when(store.homeModules()).thenReturn(List.of(
                new ContentStore.HomeRow(1, "invalid", "错误排期", "NOTICE",
                        "{\"startAt\":\"not-an-instant\",\"items\":[]}")));

        BusinessException error = assertThrows(BusinessException.class, () -> service.home("req-home-invalid"));

        assertEquals("COMMON-400-VALIDATION", error.code());
    }

    @Test
    void cmsProviderTimeoutPropagatesWithoutServingPartialHomeData() {
        when(store.homeModules()).thenThrow(new IllegalStateException("cms provider timeout"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.home("req-home-provider-timeout"));

        assertEquals("cms provider timeout", error.getMessage());
        verify(store, never()).outbox(anyLong(), anyString(), anyString(), anyString(), any());
    }
}
