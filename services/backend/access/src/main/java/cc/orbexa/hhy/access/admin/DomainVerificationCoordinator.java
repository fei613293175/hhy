package cc.orbexa.hhy.access.admin;

import cc.orbexa.hhy.access.admin.DomainConfigPolicy.ValidatedDomain;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Runs DNS, TLS/certificate and application health gates in strict order. */
public final class DomainVerificationCoordinator {
    private final DnsProbe dnsProbe;
    private final TlsProbe tlsProbe;
    private final ServiceHealthProbe serviceHealthProbe;
    private final Clock clock;

    public DomainVerificationCoordinator(
            DnsProbe dnsProbe,
            TlsProbe tlsProbe,
            ServiceHealthProbe serviceHealthProbe,
            Clock clock) {
        this.dnsProbe = Objects.requireNonNull(dnsProbe, "dnsProbe");
        this.tlsProbe = Objects.requireNonNull(tlsProbe, "tlsProbe");
        this.serviceHealthProbe = Objects.requireNonNull(serviceHealthProbe, "serviceHealthProbe");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VerificationResult verify(ValidatedDomain domain) {
        Objects.requireNonNull(domain, "domain");
        Instant attemptedAt = clock.instant();

        ProbeOutcome dns = requireOutcome(dnsProbe.resolve(domain.hostname()), "DNS");
        if (!dns.success()) {
            return failed(domain, attemptedAt, OverallStatus.DNS_FAILED,
                    layer(LayerStatus.FAILED, dns.safeCode()), notRun(), notRun(), "UNKNOWN");
        }

        ProbeOutcome tls = requireOutcome(tlsProbe.handshake(domain.hostname()), "TLS");
        if (!tls.success()) {
            return failed(domain, attemptedAt, OverallStatus.TLS_FAILED,
                    layer(LayerStatus.PASSED, dns.safeCode()),
                    layer(LayerStatus.FAILED, tls.safeCode()), notRun(), "INVALID");
        }

        ServiceTarget serviceTarget = ServiceTarget.forDomain(domain);
        ProbeOutcome service = requireOutcome(serviceHealthProbe.check(serviceTarget), "SERVICE");
        if (!service.success()) {
            return failed(domain, attemptedAt, OverallStatus.SERVICE_UNHEALTHY,
                    layer(LayerStatus.PASSED, dns.safeCode()),
                    layer(LayerStatus.PASSED, tls.safeCode()),
                    layer(LayerStatus.FAILED, service.safeCode()), "VALID");
        }

        return new VerificationResult(
                domain.code(), domain.hostname(), OverallStatus.HEALTHY,
                layer(LayerStatus.PASSED, dns.safeCode()),
                layer(LayerStatus.PASSED, tls.safeCode()),
                layer(LayerStatus.PASSED, service.safeCode()),
                "VALID", attemptedAt, attemptedAt);
    }

    private static VerificationResult failed(
            ValidatedDomain domain,
            Instant attemptedAt,
            OverallStatus overall,
            LayerResult dns,
            LayerResult tls,
            LayerResult service,
            String certificateStatus) {
        return new VerificationResult(
                domain.code(), domain.hostname(), overall, dns, tls, service,
                certificateStatus, attemptedAt, null);
    }

    private static ProbeOutcome requireOutcome(ProbeOutcome outcome, String layer) {
        return Objects.requireNonNull(outcome, layer + " probe outcome");
    }

    private static LayerResult layer(LayerStatus status, String code) {
        return new LayerResult(status, safeCode(code));
    }

    private static LayerResult notRun() {
        return new LayerResult(LayerStatus.NOT_RUN, "NOT_RUN");
    }

    private static String safeCode(String code) {
        if (code == null || code.isBlank()) return "UNSPECIFIED";
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9_]{1,64}$")) return "UNSAFE_DETAIL_REDACTED";
        return normalized;
    }

    public interface DnsProbe {
        ProbeOutcome resolve(String hostname);
    }

    public interface TlsProbe {
        /** Must verify trust chain, expiry and exact peer hostname on port 443. */
        ProbeOutcome handshake(String hostname);
    }

    public interface ServiceHealthProbe {
        ProbeOutcome check(ServiceTarget target);
    }

    public enum LayerStatus { NOT_RUN, PASSED, FAILED }

    public enum OverallStatus { DNS_FAILED, TLS_FAILED, SERVICE_UNHEALTHY, HEALTHY }

    public enum ServiceKind { HTTP_API, WEBSOCKET, STATIC_DOWNLOAD, WEB }

    public record ProbeOutcome(boolean success, String safeCode) {
        public ProbeOutcome {
            safeCode = DomainVerificationCoordinator.safeCode(safeCode);
        }

        public static ProbeOutcome passed(String code) {
            return new ProbeOutcome(true, code);
        }

        public static ProbeOutcome failed(String code) {
            return new ProbeOutcome(false, code);
        }
    }

    public record LayerResult(LayerStatus status, String code) {
        public LayerResult {
            Objects.requireNonNull(status, "status");
            code = safeCode(code);
        }
    }

    public record ServiceTarget(
            String code, String hostname, ServiceKind kind, String healthPath) {
        public ServiceTarget {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(hostname, "hostname");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(healthPath, "healthPath");
        }

        static ServiceTarget forDomain(ValidatedDomain domain) {
            String code = domain.code();
            if (code.equals("api") || code.equals("stg_api")) {
                return new ServiceTarget(
                        code, domain.hostname(), ServiceKind.HTTP_API,
                        "/public-api/v1/platform/status");
            }
            if (code.equals("ws") || code.equals("stg_ws")) {
                return new ServiceTarget(code, domain.hostname(), ServiceKind.WEBSOCKET, "/ws");
            }
            if (code.equals("download") || code.equals("stg_download")) {
                return new ServiceTarget(code, domain.hostname(), ServiceKind.STATIC_DOWNLOAD, "/");
            }
            return new ServiceTarget(code, domain.hostname(), ServiceKind.WEB, "/");
        }
    }

    public record VerificationResult(
            String code,
            String hostname,
            OverallStatus overallStatus,
            LayerResult dns,
            LayerResult tls,
            LayerResult service,
            String certificateStatus,
            Instant attemptedAt,
            Instant verifiedAt) {
        public VerificationResult {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(hostname, "hostname");
            Objects.requireNonNull(overallStatus, "overallStatus");
            Objects.requireNonNull(dns, "dns");
            Objects.requireNonNull(tls, "tls");
            Objects.requireNonNull(service, "service");
            certificateStatus = safeCode(certificateStatus);
            Objects.requireNonNull(attemptedAt, "attemptedAt");
            if (overallStatus == OverallStatus.HEALTHY && verifiedAt == null) {
                throw new IllegalArgumentException("Healthy verification requires verifiedAt");
            }
            if (overallStatus != OverallStatus.HEALTHY && verifiedAt != null) {
                throw new IllegalArgumentException("Failed verification cannot set verifiedAt");
            }
        }

        public boolean healthy() {
            return overallStatus == OverallStatus.HEALTHY;
        }
    }
}
