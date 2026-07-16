package cc.orbexa.hhy.platform.status;

import java.time.Instant;
import java.util.Map;

public record PlatformStatusView(boolean maintenance, String maintenanceMessage, Map<String, Boolean> featureFlags, Instant serverTime) { }
