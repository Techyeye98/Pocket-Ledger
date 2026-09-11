package com.example.nativeexpenseentry;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConfigStore {
    private static final String PREFS = "pocket_ledger_settings", SEP = "\u001F";
    private static final Pattern PREFIX = Pattern.compile("^([^\\p{L}\\p{N}\\s]+)\\s+(.+)$");
    private static final Pattern SUFFIX = Pattern.compile("^(.+?)\\s+([^\\p{L}\\p{N}\\s]+)$");
    private final SharedPreferences prefs;
    ConfigStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    String url() { return prefs.getString("url", ""); }
    void setUrl(String value) { prefs.edit().putString("url", value.trim()).apply(); }
    String deviceName() { return prefs.getString("device", "My Android"); }
    void setDeviceName(String value) { prefs.edit().putString("device", value.trim()).apply(); }
    int theme() { return prefs.getInt("theme", 0); }
    void setTheme(int value) { prefs.edit().putInt("theme", value).apply(); }

    List<String> categories() { return normalized("categories", Arrays.asList("Food & Dining 🍔", "Shopping 🛍️", "Transportation 🚗", "Entertainment 🎬", "Rent & Bills 🏠", "Groceries 🛒", "Health & Medical 🏥", "Recharge & Subscriptions 📱", "Travel ✈️", "Gifts 🎁", "Education 📚", "Other 💰"), "🏷️"); }
    List<String> paymentModes() { return normalized("payments", Arrays.asList("Cash 💵", "UPI 📱", "Credit Card 💳", "Debit Card 💳", "Bank Transfer 🏦", "Other 💰"), "💳"); }
    void setCategories(List<String> values) { setList("categories", canonicalize(values, "🏷️")); }
    void setPaymentModes(List<String> values) { setList("payments", canonicalize(values, "💳")); }

    /** Canonical stored/transmitted value: text, a space, then an emoji suffix. */
    String canonical(String value, String fallbackEmoji) { return format(name(value), emoji(value).isEmpty() ? fallbackEmoji : emoji(value)); }
    String format(String name, String emoji) { return name == null || name.trim().isEmpty() ? "" : name.trim() + " " + (emoji == null || emoji.trim().isEmpty() ? "🏷️" : emoji.trim()); }
    String backendValue(String value) { return value == null ? "" : value.trim(); }
    String emoji(String value) {
        if (value == null) return "";
        Matcher suffix = SUFFIX.matcher(value.trim()); if (suffix.matches()) return suffix.group(2).trim();
        Matcher prefix = PREFIX.matcher(value.trim()); return prefix.matches() ? prefix.group(1).trim() : "";
    }
    String name(String value) {
        if (value == null) return "";
        String clean = value.trim(); Matcher suffix = SUFFIX.matcher(clean); if (suffix.matches()) return suffix.group(1).trim();
        Matcher prefix = PREFIX.matcher(clean); return prefix.matches() ? prefix.group(2).trim() : clean;
    }

    String defaultCategory() { return canonicalPreference("defaultCategory", "🏷️"); }
    String defaultPaymentMode() { return canonicalPreference("defaultPayment", "💳"); }
    void setDefaultCategory(String value) { prefs.edit().putString("defaultCategory", value.isEmpty() ? "" : canonical(value, "🏷️")).apply(); }
    void setDefaultPaymentMode(String value) { prefs.edit().putString("defaultPayment", value.isEmpty() ? "" : canonical(value, "💳")).apply(); }
    String lastCategory() { return canonicalPreference("lastCategory", "🏷️"); }
    String lastPaymentMode() { return canonicalPreference("lastPayment", "💳"); }
    void recordSelection(String category, String payment) { prefs.edit().putString("lastCategory", canonical(category, "🏷️")).putString("lastPayment", canonical(payment, "💳")).apply(); }
    void recordOperation(String operation, boolean success, String detail) { prefs.edit().putString("lastOperation", operation).putBoolean("lastSuccess", success).putString("lastDetail", detail).putLong("lastAt", System.currentTimeMillis()).apply(); }
    String lastOperation() { return prefs.getString("lastOperation", "No operations yet"); }
    boolean lastSuccess() { return prefs.getBoolean("lastSuccess", false); }
    String lastDetail() { return prefs.getString("lastDetail", "No status recorded"); }
    long lastAt() { return prefs.getLong("lastAt", 0); }

    /** The URL that was last successfully connection-tested. Empty if never tested or if the URL has since changed. */
    String verifiedUrl() { return prefs.getString("verifiedUrl", ""); }
    /** Record that the currently configured URL was successfully verified. */
    void setVerifiedUrl(String url) { prefs.edit().putString("verifiedUrl", url == null ? "" : url.trim()).apply(); }
    /** Clear any verified status (e.g. when the URL is changed to a new value). */
    void clearVerifiedUrl() { prefs.edit().putString("verifiedUrl", "").apply(); }

    private List<String> normalized(String key, List<String> fallback, String fallbackEmoji) {
        String raw = prefs.getString(key, null); List<String> values = raw == null ? new ArrayList<>(fallback) : new ArrayList<>(Arrays.asList(raw.split(SEP, -1)));
        List<String> canonical = canonicalize(values, fallbackEmoji); if (raw != null && !TextUtils.join(SEP, canonical).equals(raw)) setList(key, canonical); return canonical;
    }
    private String canonicalPreference(String key, String fallbackEmoji) { String raw = prefs.getString(key, ""); String formatted = canonical(raw, fallbackEmoji); if (!formatted.equals(raw)) prefs.edit().putString(key, formatted).apply(); return formatted; }
    private List<String> canonicalize(List<String> source, String fallbackEmoji) { List<String> result = new ArrayList<>(); for (String value : source) { String canonical = canonical(value, fallbackEmoji); if (!canonical.isEmpty()) result.add(canonical); } return result; }
    private void setList(String key, List<String> values) { prefs.edit().putString(key, TextUtils.join(SEP, values)).apply(); }
}
