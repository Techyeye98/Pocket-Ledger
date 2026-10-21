package com.pocketledger.v2;

public final class SpendingInsightsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        SpendingInsights summary = new SpendingInsights();
        check(summary.share(0) == 0 && summary.topWantCategory().isEmpty(), "Empty ledger");
        summary.add(600, SpendingType.NEED, "Food");
        summary.add(150, SpendingType.WANT, "Food");
        summary.add(100, SpendingType.WANT, "Cinema");
        summary.add(150, SpendingType.UNTAGGED, "Other");
        summary.add(Double.NaN, SpendingType.NEED, "Invalid");
        check(summary.total() == 1000, "Totals include untagged");
        check(summary.share(0) == 60 && summary.share(1) == 25 && summary.share(2) == 15, "Shares use all spending");
        check(summary.counts[0] == 1 && summary.counts[1] == 2 && summary.counts[2] == 1, "Entry counts");
        check("Food".equals(summary.topWantCategory()), "Largest Wants category");
        check(SpendingType.UNTAGGED.equals(SpendingType.normalize(null)), "Legacy missing tag");
        SpendingInsights old = new SpendingInsights();
        old.add(800, null, "Old entry");
        check(old.share(2) == 100 && old.share(0) == 0, "Historical spending is not auto-classified");
        old.refund(100, SpendingType.UNTAGGED, "Old entry");
        check(old.total() == 700 && old.counts[2] == 1, "Refund reduces spending without adding a purchase count");
        old.refund(800, SpendingType.UNTAGGED, "Old entry");
        check(old.hasNegative() && old.total() == -100, "Refund-only periods retain negative net spending");
        System.out.println("PASS: spending totals, shares, counts, categories, empty and historical ledgers.");
    }
}
