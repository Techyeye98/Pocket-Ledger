package com.pocketledger.v2;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    // Keep exact integers interoperable with the JavaScript Sheets backend.
    static final long MAX = 9_000_000_000_000_000L;
    static long parse(String raw) {
        String value = raw.trim().replace('\u00a0', ' ');
        if (value.contains(",") && !value.contains(".")) value = value.replace(',', '.');
        long minor;
        try { minor = new BigDecimal(value).movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Use at most two decimal places and a supported amount", e); }
        if (minor > MAX || minor < -MAX) throw new IllegalArgumentException("Amount is too large");
        return minor;
    }
    static long legacy(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid legacy amount");
        return parse(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
    static String decimal(long minor) { return BigDecimal.valueOf(minor, 2).toPlainString(); }
    static long add(long a, long b) {
        long result = Math.addExact(a, b);
        if (result > MAX || result < -MAX) throw new IllegalArgumentException("Total exceeds supported amount");
        return result;
    }
}
