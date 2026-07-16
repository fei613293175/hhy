package cc.orbexa.hhy.platform.release;

import java.time.Instant;

public record AppVersionPolicyView(String platform, int latestVersionCode, String latestVersionName, String updateType,
                                   String downloadUrl, String sha256, String releaseNotes, int minSupportedVersionCode, Instant serverTime) { }
