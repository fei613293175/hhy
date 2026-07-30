package cc.orbexa.hhy.content;

/** Typed R09 App attributes carried by the frozen generic content contract. */
public final class R09Contracts {
    private R09Contracts() { }

    public record AppAttributes(
            String appName, String platform, String versionText,
            String downloadUrl, String website) { }
}
