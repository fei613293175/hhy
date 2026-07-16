package cc.orbexa.hhy.shared.domain;

import java.util.Currency;
import java.util.Objects;

public record Money(long amountCent, Currency currency) {
    public Money {
        if (amountCent < 0) throw new IllegalArgumentException("amountCent must be non-negative");
        Objects.requireNonNull(currency, "currency");
    }
    public static Money cny(long amountCent) { return new Money(amountCent, Currency.getInstance("CNY")); }
}
