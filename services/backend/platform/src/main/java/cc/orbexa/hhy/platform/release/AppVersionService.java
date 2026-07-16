package cc.orbexa.hhy.platform.release;

import cc.orbexa.hhy.platform.config.HhyPlatformProperties;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public final class AppVersionService {
    private final HhyPlatformProperties properties;
    public AppVersionService(HhyPlatformProperties properties) { this.properties=properties; }
    public AppVersionPolicyView check(AppVersionCheckRequest request) {
        String updateType = request.versionCode() < properties.minimumSupportedVersionCode() ? "FORCED"
                : request.versionCode() < properties.latestVersionCode() ? "OPTIONAL" : "NONE";
        return new AppVersionPolicyView("ANDROID", properties.latestVersionCode(), properties.latestVersionName(), updateType,
                properties.downloadUrl(), properties.apkSha256(), properties.releaseNotes(), properties.minimumSupportedVersionCode(), Instant.now());
    }
}
