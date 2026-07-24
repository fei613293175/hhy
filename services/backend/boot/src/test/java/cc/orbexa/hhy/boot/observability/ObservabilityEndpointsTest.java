package cc.orbexa.hhy.boot.observability;

import cc.orbexa.hhy.boot.web.RequestIdFilter;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityEndpointsTest {
    @Autowired
    MockMvc mvc;

    @Test
    void exposesOnlyHealthInfoAndPrometheusWithoutSensitiveHealthDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_outbox_dead_letter")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_ledger_unbalanced_transactions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_reconciliation_open_differences")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_admin_active_sessions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_admin_auth_failures_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_admin_mfa_active_methods")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_admin_idempotency_incomplete_snapshots")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_user_active_sessions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_user_security_challenge_failures_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_user_sms_expired_unused")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_provider_connection_test_failures_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_provider_config_untested_active")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_provider_certificates_expiring_30d")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_domain_verification_failures")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_media_upload_failures_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_media_upload_expired_open")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_media_delete_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_storage_migration_blocked")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_identity_active_sessions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_identity_provider_failures_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_identity_manual_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_identity_private_media_invalid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_content_online_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_content_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_content_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_home_enabled_modules")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_search_history_rows")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_search_hot_terms_active")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_publisher_active_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_contact_accesses_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_contact_rejections_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_r07_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_total_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_online_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_favorites_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_contact_accesses_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_project_contact_rejections_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_r08_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_total_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_online_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_favorites_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_contact_accesses_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_app_contact_rejections_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_r09_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_total_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_online_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_favorites_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_contact_accesses_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_group_contact_rejections_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_r10_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_total_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_online_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_review_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_favorites_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_contact_accesses_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_team_leader_contact_rejections_5m")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_r11_outbox_backlog")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hhy_business_metric_query_failures_total")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http_server_requests_seconds_bucket")));
        mvc.perform(get("/actuator").with(user("observability-auditor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.health").exists())
                .andExpect(jsonPath("$._links.prometheus").exists())
                .andExpect(jsonPath("$._links.env").doesNotExist())
                .andExpect(jsonPath("$._links.configprops").doesNotExist());
    }

    @Test
    void completionLogContainsTraceFieldsAndNeverCapturesSensitiveRequestData() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mvc.perform(get("/public-api/v1/platform/status")
                            .queryParam("password", "must-not-appear")
                            .header("Authorization", "Bearer must-not-appear")
                            .header("Cookie", "session=must-not-appear")
                            .header("X-Request-Id", "request_observe_001")
                            .header("X-Trace-Id", "0123456789abcdef0123456789abcdef"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Request-Id", "request_observe_001"))
                    .andExpect(header().string("X-Trace-Id", "0123456789abcdef0123456789abcdef"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent event = appender.list.stream()
                .filter(row -> row.getLevel() == Level.INFO)
                .filter(row -> "http_request_completed".equals(row.getFormattedMessage()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(event.getMDCPropertyMap())
                .containsEntry("requestId", "request_observe_001")
                .containsEntry("traceId", "0123456789abcdef0123456789abcdef");
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(fields.get("operation")).isEqualTo("GET /public-api/v1/platform/status");
        assertThat(fields.get("status")).isEqualTo(200);
        assertThat(((Number) fields.get("latency")).longValue()).isGreaterThanOrEqualTo(0);
        assertThat(event.toString()).doesNotContain("must-not-appear", "Authorization", "Cookie", "password");
    }

    @Test
    void securityRejectionKeepsRequestIdAndTraceIdDistinctAndCorrelated() throws Exception {
        mvc.perform(get("/admin-api/v1/me/security")
                        .header("X-Request-Id", "request_reject_observe_001")
                        .header("X-Trace-Id", "abcdef0123456789abcdef0123456789"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "request_reject_observe_001"))
                .andExpect(header().string("X-Trace-Id", "abcdef0123456789abcdef0123456789"))
                .andExpect(jsonPath("$.requestId").value("request_reject_observe_001"))
                .andExpect(jsonPath("$.error.traceId").value("abcdef0123456789abcdef0123456789"));
    }

    @Test
    void unknownPermittedManagementPathIsNotReportedAsServerFailure() throws Exception {
        mvc.perform(get("/actuator/health/not-a-real-group")
                        .header("X-Request-Id", "request_missing_observe_001")
                        .header("X-Trace-Id", "1234567890abcdef1234567890abcdef"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "1234567890abcdef1234567890abcdef"));
    }
}
