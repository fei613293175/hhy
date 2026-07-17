package cc.orbexa.hhy.platform.status;

import java.time.Instant;

public record PlatformStatusView(
        boolean maintenance,
        String maintenanceMessage,
        PlatformCapabilitiesView capabilities,
        Instant serverTime) { }
