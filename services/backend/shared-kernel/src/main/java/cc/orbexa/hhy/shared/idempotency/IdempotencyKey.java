package cc.orbexa.hhy.shared.idempotency;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.length() < 16 || value.length() > 128) throw new IllegalArgumentException("invalid idempotency key length");
    }
}
