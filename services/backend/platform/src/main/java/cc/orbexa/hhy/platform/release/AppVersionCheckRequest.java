package cc.orbexa.hhy.platform.release;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AppVersionCheckRequest(
        @NotBlank @Pattern(regexp = "ANDROID") String platform,
        @Min(1) long versionCode,
        @Size(max = 32) String versionName,
        @NotBlank @Size(max = 64) String channel,
        @NotBlank @Pattern(regexp = "DEV|TEST|STAGING|PROD") String environment) { }
