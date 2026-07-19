package cc.orbexa.hhy.boot.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cc.orbexa.hhy.access.admin.AdminPrincipal;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.FreezeRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.MediaAccessRequest;
import cc.orbexa.hhy.access.identity.AdminIdentityContracts.ReviewRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class AdminIdentityControllerTest {
    @Test
    void exposesAllFiveFrozenOperationIdsWithExactPermissions() throws Exception {
        assertGet("adminIdentityGetIdentities", "/admin-api/v1/identities",
                "hasAuthority('identity.read')", int.class, int.class, String.class,
                String.class, String.class, String.class, HttpServletRequest.class);
        assertGet("adminIdentityGetIdentitiesByUserid", "/admin-api/v1/identities/{userId}",
                "hasAuthority('identity.read')", AdminPrincipal.class, String.class,
                HttpServletRequest.class);
        assertPost("adminIdentityPostIdentitiesByUseridMediaAccess",
                "/admin-api/v1/identities/{userId}/media-access",
                "hasAuthority('identity.media.view')", AdminPrincipal.class, String.class,
                MediaAccessRequest.class, String.class, HttpServletRequest.class);
        assertPost("adminIdentityPostIdentitySessionsByIdReview",
                "/admin-api/v1/identity-sessions/{id}/review",
                "hasAuthority('identity.review')", AdminPrincipal.class, String.class,
                ReviewRequest.class, String.class, HttpServletRequest.class);
        assertPost("adminIdentityPostIdentitiesByUseridFreeze",
                "/admin-api/v1/identities/{userId}/freeze",
                "hasAuthority('identity.freeze')", AdminPrincipal.class, String.class,
                FreezeRequest.class, String.class, HttpServletRequest.class);
    }

    private static void assertGet(
            String name, String path, String permission, Class<?>... parameters) throws Exception {
        Method method = AdminIdentityController.class.getMethod(name, parameters);
        assertEquals(path, method.getAnnotation(GetMapping.class).value()[0]);
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
    }

    private static void assertPost(
            String name, String path, String permission, Class<?>... parameters) throws Exception {
        Method method = AdminIdentityController.class.getMethod(name, parameters);
        assertEquals(path, method.getAnnotation(PostMapping.class).value()[0]);
        assertEquals(permission, method.getAnnotation(PreAuthorize.class).value());
    }
}
