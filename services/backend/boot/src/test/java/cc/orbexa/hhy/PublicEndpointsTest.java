package cc.orbexa.hhy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicEndpointsTest {
    @Autowired MockMvc mvc;
    @Test void platformStatusIsPublicAndTyped() throws Exception {
        mvc.perform(get("/public-api/v1/platform/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serverTime").exists()).andExpect(header().exists("X-Request-Id"));
    }
    @Test void versionCheckRejectsInvalidPayload() throws Exception {
        mvc.perform(post("/public-api/v1/app/version-check").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON-400-VALIDATION"));
    }
    @Test void protectedEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
