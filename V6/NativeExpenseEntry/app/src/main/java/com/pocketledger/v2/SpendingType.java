package com.pocketledger.v2;

/** User intent is independent of category; missing historical tags stay untagged. */
final class SpendingType {
    static final String NEED = "NEED", WANT = "WANT", UNTAGGED = "UNTAGGED";
    static String normalize(String value) {
        if (NEED.equals(value) || WANT.equals(value)) return value;
        return UNTAGGED;
    }
    static String label(String value) {
        if (NEED.equals(value)) return "Need";
        if (WANT.equals(value)) return "Want";
        return "Untagged";
    }
}
