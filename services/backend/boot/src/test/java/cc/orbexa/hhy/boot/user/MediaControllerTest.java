package cc.orbexa.hhy.boot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.orbexa.hhy.access.storage.MediaContracts.CompleteUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.CreateUploadSessionRequest;
import cc.orbexa.hhy.access.storage.MediaContracts.MediaResource;
import cc.orbexa.hhy.access.storage.MediaUploadService;
import cc.orbexa.hhy.access.user.UserAuthContracts.CommandResultResource;
import cc.orbexa.hhy.access.user.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

class MediaControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(11, 1, 0, "jti", "ACTIVE");

    @Test
    void exposesOnlyTheThreeFrozenMediaMappingsAndDelegatesToService() throws Exception {
        MediaUploadService service = mock(MediaUploadService.class);
        MediaController controller = new MediaController(service, Clock.fixed(NOW, ZoneOffset.UTC));
        HttpServletRequest servlet = request();
        String key = "media-controller-001";
        CreateUploadSessionRequest create = new CreateUploadSessionRequest(
                "public_media", "cover.png", "image/png", 32L, "a".repeat(64));
        CompleteUploadSessionRequest complete = new CompleteUploadSessionRequest("etag", List.of());
        MediaResource media = new MediaResource("1", "public_media", "image/png", 32L,
                "a".repeat(64), null, null, "CREATED", NOW.plusSeconds(300));
        CommandResultResource deleted = new CommandResultResource("1", null,
                "DELETE_PENDING", 1L, NOW);
        when(service.create(PRINCIPAL, create, key)).thenReturn(media);
        when(service.complete(PRINCIPAL, "1", complete, key)).thenReturn(media);
        when(service.delete(PRINCIPAL, "1", key)).thenReturn(deleted);

        assertEquals(media, controller.mediaPostMediaUploadSessions(
                PRINCIPAL, create, key, servlet).data());
        assertEquals(media, controller.mediaPostMediaUploadSessionsByIdComplete(
                PRINCIPAL, "1", complete, key, servlet).data());
        assertEquals(deleted, controller.mediaDeleteMediaById(PRINCIPAL, "1", key, servlet).data());
        verify(service).create(PRINCIPAL, create, key);
        verify(service).complete(PRINCIPAL, "1", complete, key);
        verify(service).delete(PRINCIPAL, "1", key);

        assertPost("mediaPostMediaUploadSessions", "/api/v1/media/upload-sessions",
                UserPrincipal.class, CreateUploadSessionRequest.class, String.class, HttpServletRequest.class);
        assertPost("mediaPostMediaUploadSessionsByIdComplete",
                "/api/v1/media/upload-sessions/{id}/complete", UserPrincipal.class, String.class,
                CompleteUploadSessionRequest.class, String.class, HttpServletRequest.class);
        Method delete = MediaController.class.getMethod("mediaDeleteMediaById",
                UserPrincipal.class, String.class, String.class, HttpServletRequest.class);
        assertEquals("/api/v1/media/{id}", delete.getAnnotation(DeleteMapping.class).value()[0]);
    }

    private static void assertPost(String method, String path, Class<?>... parameters) throws Exception {
        Method mapping = MediaController.class.getMethod(method, parameters);
        assertEquals(path, mapping.getAnnotation(PostMapping.class).value()[0]);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("requestId")).thenReturn("request-media-001");
        return request;
    }
}
