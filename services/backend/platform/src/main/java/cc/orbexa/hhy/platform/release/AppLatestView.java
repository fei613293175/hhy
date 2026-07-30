package cc.orbexa.hhy.platform.release;

public record AppLatestView(
        String code,
        String title,
        String description,
        DownloadInfo download,
        long version) {

    public record DownloadInfo(
            String platform,
            String versionName,
            long versionCode,
            String downloadUrl,
            String sha256) { }
}
