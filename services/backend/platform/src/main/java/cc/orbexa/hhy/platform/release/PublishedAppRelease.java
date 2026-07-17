package cc.orbexa.hhy.platform.release;

record PublishedAppRelease(
        long versionCode,
        String versionName,
        String updateType,
        long minSupportedVersionCode,
        String releaseNotes,
        String downloadDomain,
        String objectKey,
        String sha256) { }
