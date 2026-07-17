package cc.orbexa.hhy.platform.status;

public record PlatformCapabilitiesView(
        boolean registration,
        boolean publishing,
        boolean redPacket,
        boolean withdrawal) { }
