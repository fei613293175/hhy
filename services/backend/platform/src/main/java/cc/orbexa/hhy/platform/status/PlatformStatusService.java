package cc.orbexa.hhy.platform.status;

import cc.orbexa.hhy.platform.config.HhyPlatformProperties;
import java.time.Clock;
import java.time.Instant;
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
                new PlatformCapabilitiesView(
                        properties.registrationEnabled(),
                        properties.publishingEnabled(),
                        properties.redPacketEnabled(),
                        properties.withdrawalEnabled()),
                Instant.now(clock));
    }
}
