package cc.orbexa.hhy.content;

import java.util.List;

public final class R11Contracts {
    private R11Contracts() { }

    public record TeamLeaderAttributes(
            String teamName,
            String nickname,
            Long logoMediaId,
            String personalIntro,
            String teamIntro,
            String sizeRange,
            String skills,
            String cooperationTypes,
            String cooperationRequirement,
            List<String> pastCases,
            Boolean acceptPrivateChat) { }
}
