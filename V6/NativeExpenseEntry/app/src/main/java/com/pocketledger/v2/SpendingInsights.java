package com.pocketledger.v2;

import java.util.LinkedHashMap;
import java.util.Map;

/** Amount shares include untagged spending, so incomplete tagging stays visible. */
final class SpendingInsights {
    final double[] amounts = new double[3];
    final int[] counts = new int[3];
    private final Map<String, Double> wantCategories = new LinkedHashMap<>();
    void add(double amount, String type, String category) {
        if (!Double.isFinite(amount) || amount < 0) return;
        int index = SpendingType.NEED.equals(type) ? 0 : SpendingType.WANT.equals(type) ? 1 : 2;
        amounts[index] += amount;
        counts[index]++;
        if (index == 1) wantCategories.put(category, wantCategories.getOrDefault(category, 0.0) + amount);
    }
    void refund(double amount, String type, String category) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        int index = SpendingType.NEED.equals(type) ? 0 : SpendingType.WANT.equals(type) ? 1 : 2;
        amounts[index] -= amount;
        if (index == 1) wantCategories.put(category, wantCategories.getOrDefault(category, 0.0) - amount);
    }
    boolean hasNegative() { return amounts[0] < 0 || amounts[1] < 0 || amounts[2] < 0; }
    double total() { return amounts[0] + amounts[1] + amounts[2]; }
    double share(int index) { return total() > 0 ? amounts[index] / total() * 100 : 0; }
    String topWantCategory() {
        String top = "";
        double highest = 0;
        for (Map.Entry<String, Double> entry : wantCategories.entrySet()) {
            if (entry.getValue() > highest) { highest = entry.getValue(); top = entry.getKey(); }
        }
        return top;
    }
}
