package cc.orbexa.hhy.platform.release;

import cc.orbexa.hhy.shared.api.BusinessException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class AppVersionService {
    private static final Pattern SHA_256 = Pattern.compile("^[A-Fa-f0-9]{64}$");
    private static final Set<String> UPDATE_TYPES = Set.of("NONE", "OPTIONAL", "FORCED");

    private final PublishedAppReleaseRepository repository;
    private final Clock clock;

    public AppVersionService(PublishedAppReleaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AppVersionPolicyView check(AppVersionCheckRequest request) {
        Instant now = Instant.now(clock);
        PublishedAppRelease release = repository.findLatest(
                        request.channel(), request.environment(), now)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-404-NOT_FOUND", "当前渠道和环境没有已发布版本", 404, false));
        validatePublishedRelease(release);

        String updateType = resolveUpdateType(request.versionCode(), release);
        return new AppVersionPolicyView(
                "ANDROID",
                release.versionCode(),
                release.versionName(),
                updateType,
                downloadUrl(release.downloadDomain(), release.objectKey()),
                release.sha256(),
                release.releaseNotes(),
                release.minSupportedVersionCode(),
                now);
    }

    public AppLatestView latestPublic() {
        Instant now = Instant.now(clock);
        PublishedAppRelease release = repository.findLatestPublic(now)
                .orElseThrow(() -> new BusinessException(
                        "COMMON-404-NOT_FOUND", "当前没有可公开下载的正式版本", 404, false));
        validatePublishedRelease(release);
        return new AppLatestView(
                "app-latest",
                "合伙云 Pro " + release.versionName(),
                release.releaseNotes(),
                new AppLatestView.DownloadInfo(
                        "ANDROID",
                        release.versionName(),
                        release.versionCode(),
                        downloadUrl(release.downloadDomain(), release.objectKey()),
                        release.sha256()),
                release.versionCode());
    }

    private static String resolveUpdateType(long currentVersionCode, PublishedAppRelease release) {
        if (currentVersionCode >= release.versionCode()) {
            return "NONE";
        }
        if (currentVersionCode < release.minSupportedVersionCode()) {
            return "FORCED";
        }
        return release.updateType();
    }

    private static void validatePublishedRelease(PublishedAppRelease release) {
        if (release.versionCode() < 1
                || release.minSupportedVersionCode() < 1
                || release.minSupportedVersionCode() > release.versionCode()
                || release.versionName() == null
                || release.versionName().length() > 32
                || !UPDATE_TYPES.contains(release.updateType())
                || release.sha256() == null
                || !SHA_256.matcher(release.sha256()).matches()
                || release.releaseNotes() == null
                || release.releaseNotes().length() > 2000) {
            throw new IllegalStateException("Published app release violates the public version policy contract");
        }
    }

    private static String downloadUrl(String domain, String objectKey) {
        if (domain == null || domain.isBlank() || objectKey == null || objectKey.isBlank()) {
            throw new IllegalStateException("Published app release is missing its download location");
        }
        String value = domain.replaceAll("/+$", "") + "/" + objectKey.replaceAll("^/+", "");
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Published app release has an invalid download location", exception);
        }
        if (!uri.isAbsolute() || value.length() > 2048) {
            throw new IllegalStateException("Published app release has an invalid download location");
        }
        return value;
    }
}
