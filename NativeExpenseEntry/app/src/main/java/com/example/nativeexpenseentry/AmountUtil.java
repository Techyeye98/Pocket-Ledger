package com.example.nativeexpenseentry;

import java.math.BigDecimal;

/** Keeps amount validation and JSON representation exact and locale-tolerant. */
final class AmountUtil {
    private AmountUtil() { }
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().replace('\u00a0', ' ');
        if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) value = value.replace(',', '.');
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException("Amount must be positive");
        return amount.stripTrailingZeros().toPlainString();
    }
}
