package cc.orbexa.hhy.access.identity;

import java.net.URI;
import java.math.BigDecimal;

/** Provider-specific port. Implementations must not log names, ID numbers, tokens, or signed URLs. */
public interface IdentityProviderClient {
    ProviderLivenessResult createLiveness(ProviderLivenessCommand command);

    ProviderLivenessOutcome queryLiveness(ProviderLivenessQuery query);

    ProviderFaceComparison compareFace(ProviderFaceComparisonCommand command);

    record ProviderLivenessCommand(
            String provider, long sessionId, long userId, String state,
            URI returnUrl, String idempotencyKey) { }

    record ProviderLivenessResult(String providerOrderNo, URI livenessUrl) { }

    record ProviderLivenessQuery(String provider, String providerOrderNo) { }

    enum LivenessDecision { PASSED, REJECTED, PENDING }

    record ProviderLivenessOutcome(
            LivenessDecision decision, String providerOrderNo,
            URI faceImageUrl, byte[] rawResponse) {
        public ProviderLivenessOutcome {
            rawResponse = rawResponse == null ? new byte[0] : rawResponse.clone();
        }

        @Override
        public byte[] rawResponse() {
            return rawResponse.clone();
        }
    }

    record ProviderFaceComparisonCommand(
            String provider, String realName, String idNumber, byte[] image) {
        public ProviderFaceComparisonCommand {
            image = image == null ? new byte[0] : image.clone();
        }

        @Override
        public byte[] image() {
            return image.clone();
        }
    }

    enum FaceDecision { VERIFIED, MANUAL_REVIEW, REJECTED }

    record ProviderFaceComparison(
            FaceDecision decision, int resultCode, BigDecimal score,
            String providerOrderNo, byte[] rawResponse) {
        public ProviderFaceComparison {
            rawResponse = rawResponse == null ? new byte[0] : rawResponse.clone();
        }

        @Override
        public byte[] rawResponse() {
            return rawResponse.clone();
        }
    }
}
