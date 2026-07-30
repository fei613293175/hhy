package cc.orbexa.hhy.content;

/** Typed R10 group attributes carried by the frozen generic content contract. */
public final class R10Contracts {
    private R10Contracts() { }

    public record GroupAttributes(
            String platform,
            String sizeRange,
            String joinRequirement,
            Long qrMediaId,
            String groupLink,
            String groupNo) { }
}
