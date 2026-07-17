package cc.orbexa.hhy.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hhy.platform")
public record HhyPlatformProperties(
        boolean maintenance,
        String maintenanceMessage,
        boolean registrationEnabled,
        boolean publishingEnabled,
        boolean redPacketEnabled,
        boolean withdrawalEnabled) { }
