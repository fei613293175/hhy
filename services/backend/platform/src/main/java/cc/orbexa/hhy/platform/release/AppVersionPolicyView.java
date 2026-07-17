package cc.orbexa.hhy.platform.release;

import java.time.Instant;

public record AppVersionPolicyView(
        String platform,
        long latestVersionCode,
        String latestVersionName,
        String updateType,
        String downloadUrl,
        String sha256,
        String releaseNotes,
        long minSupportedVersionCode,
        Instant serverTime) { }
