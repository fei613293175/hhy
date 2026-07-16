package cc.orbexa.hhy.platform.status;

import cc.orbexa.hhy.platform.config.HhyPlatformProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public final class PlatformStatusService {
    private final HhyPlatformProperties properties;
    private final Clock clock;

    public PlatformStatusService(HhyPlatformProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public PlatformStatusView current() {
        return new PlatformStatusView(
                properties.maintenance(),
                properties.maintenanceMessage(),
                Map.of(
                        "registration.enabled", properties.registrationEnabled(),
                        "publishing.enabled", properties.publishingEnabled(),
                        "redpacket.enabled", properties.redPacketEnabled(),
                        "withdrawal.enabled", properties.withdrawalEnabled()),
                Instant.now(clock));
    }
}
