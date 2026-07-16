package cc.orbexa.hhy.platform.release;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AppVersionCheckRequest(
        @NotBlank @Pattern(regexp="ANDROID") String platform,
        @Min(1) int versionCode,
        String versionName,
        @NotBlank String channel,
        @NotBlank @Pattern(regexp="DEV|TEST|STAGING|PROD") String environment) { }
