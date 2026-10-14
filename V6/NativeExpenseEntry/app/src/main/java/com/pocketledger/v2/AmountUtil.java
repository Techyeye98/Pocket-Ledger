package com.pocketledger.v2;

import java.math.BigDecimal;

/** Amount validation and normalization — exact, locale-tolerant (migrated from V1). */
final class AmountUtil {
    private AmountUtil() { }

    /**
     * Parse, validate, and normalize a raw amount string.
     * Accepts comma as decimal separator (European locales).
     * Returns a plain decimal string suitable for storage (no trailing zeros).
     * Throws NumberFormatException if zero or invalid.
     */
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().replace('\u00a0', ' ');
        if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) value = value.replace(',', '.');
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException("Amount must be positive");
        return amount.stripTrailingZeros().toPlainString();
    }
}
