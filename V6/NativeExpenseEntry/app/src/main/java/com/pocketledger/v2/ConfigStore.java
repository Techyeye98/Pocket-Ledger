package com.pocketledger.v2;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preferences store for app configuration (URL, device name, theme, categories, payment modes).
 *
 * Migrated from V1 with the same emoji/canonical logic preserved.
 * Local transaction data is NOT stored here — it lives in Room (AppDatabase).
 *
 * Category/payment format rule (CANONICAL):
 *   "Food & Dining 🍔"  — text first, space, emoji suffix. Never emoji-first.
 */
final class ConfigStore {
    private static final String PREFS = "pocket_ledger_v2_settings";
    private static final String SEP   = "\u001F";
    private static final Pattern PREFIX = Pattern.compile("^([^\\p{L}\\p{N}\\s]+)\\s+(.+)$");
    private static final Pattern SUFFIX = Pattern.compile("^(.+?)\\s+([^\\p{L}\\p{N}\\s]+)$");

    private final SharedPreferences prefs;

    ConfigStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Sync / connection ─────────────────────────────────────────────────────

    String url()               { return prefs.getString("url", ""); }
    void setUrl(String value)  { prefs.edit().putString("url", value.trim()).apply(); }

    String verifiedUrl()               { return prefs.getString("verifiedUrl", ""); }
    boolean spendingSyncVerified()    { return prefs.getInt("verifiedSpendingTypeSchema", 0) == 1 && prefs.getInt("verifiedLedgerSchema",0)==1; }
    void setVerifiedUrl(String url)    { prefs.edit().putString("verifiedUrl", url == null ? "" : url.trim()).putInt("verifiedSpendingTypeSchema", 1).putInt("verifiedLedgerSchema",1).apply(); }
    void clearVerifiedUrl()            { prefs.edit().putString("verifiedUrl", "").remove("verifiedSpendingTypeSchema").remove("verifiedLedgerSchema").apply(); }

    long lastSyncAt()                  { return prefs.getLong("lastSyncAt", 0); }
    void recordSync(long epochMillis)  { prefs.edit().putLong("lastSyncAt", epochMillis).apply(); }

    // ── Device ────────────────────────────────────────────────────────────────

    String deviceName()               { return prefs.getString("device", "My Android"); }
    void setDeviceName(String value)  { prefs.edit().putString("device", value.trim()).apply(); }

    // ── Appearance ────────────────────────────────────────────────────────────

    /** 0 = system, 1 = light, 2 = dark */
    int  theme()              { return prefs.getInt("theme", 0); }
    void setTheme(int value)  { prefs.edit().putInt("theme", value).apply(); }

    // ── Currency ──────────────────────────────────────────────────────────────

    String currencySymbol()              { return prefs.getString("currency", "₹"); }
    void setCurrencySymbol(String value) { prefs.edit().putString("currency", value.trim()).apply(); }

    // ── Categories ────────────────────────────────────────────────────────────

    List<String> categories() {
        return normalized("categories", Arrays.asList(
                "Food & Dining 🍔", "Shopping 🛍️", "Transportation 🚗",
                "Entertainment 🎬", "Rent & Bills 🏠", "Groceries 🛒",
                "Health & Medical 🏥", "Recharge & Subscriptions 📱",
                "Travel ✈️", "Gifts 🎁", "Education 📚", "Other 💰"
        ), "🏷️");
    }

    void setCategories(List<String> values) { setList("categories", canonicalize(values, "🏷️")); }
    List<String> incomeCategories() { return normalized("incomeCategories", Arrays.asList("Salary", "Freelance", "Gift", "Interest", "Other"), "🏷️"); }
    void setIncomeCategories(List<String> values) { setList("incomeCategories", values); }

    // ── Payment modes ─────────────────────────────────────────────────────────

    List<String> paymentModes() {
        return normalized("payments", Arrays.asList(
                "Cash 💵", "UPI 📱", "Credit Card 💳",
                "Debit Card 💳", "Bank Transfer 🏦", "Other 💰"
        ), "💳");
    }

    void setPaymentModes(List<String> values) { setList("payments", canonicalize(values, "💳")); }

    // ── Entry preferences ─────────────────────────────────────────────────────

    String defaultCategory()              { return canonicalPreference("defaultCategory", "🏷️"); }
    String defaultPaymentMode()           { return canonicalPreference("defaultPayment", "💳"); }
    void setDefaultCategory(String v)     { prefs.edit().putString("defaultCategory", v.isEmpty() ? "" : canonical(v, "🏷️")).apply(); }
    void setDefaultPaymentMode(String v)  { prefs.edit().putString("defaultPayment", v.isEmpty() ? "" : canonical(v, "💳")).apply(); }
    String lastCategory()                 { return canonicalPreference("lastCategory", "🏷️"); }
    String lastPaymentMode()              { return canonicalPreference("lastPayment", "💳"); }
    void recordSelection(String cat, String pay) { prefs.edit().putString("lastCategory", canonical(cat, "🏷️")).putString("lastPayment", canonical(pay, "💳")).apply(); }
    boolean recentEntriesHidden()         { return prefs.getBoolean("recentEntriesHidden", false); }
    void setRecentEntriesHidden(boolean hidden) { prefs.edit().putBoolean("recentEntriesHidden", hidden).apply(); }

    BackupSettings backupSettings() {
        return new BackupSettings(theme(), currencySymbol(), deviceName(), categories(), paymentModes(),
                defaultCategory(), defaultPaymentMode(), lastCategory(), lastPaymentMode(), recentEntriesHidden());
    }

    boolean restoreBackupSettings(BackupSettings settings) {
        return prefs.edit()
                .putInt("theme", settings.theme)
                .putString("currency", settings.currencySymbol)
                .putString("device", settings.deviceName)
                .putString("categories", TextUtils.join(SEP, settings.categories))
                .putString("payments", TextUtils.join(SEP, settings.paymentModes))
                .putString("defaultCategory", settings.defaultCategory)
                .putString("defaultPayment", settings.defaultPaymentMode)
                .putString("lastCategory", settings.lastCategory)
                .putString("lastPayment", settings.lastPaymentMode)
                .putBoolean("recentEntriesHidden", settings.recentEntriesHidden)
                .commit();
    }

    static final class BackupSettings {
        final int theme;
        final boolean recentEntriesHidden;
        final String currencySymbol, deviceName, defaultCategory, defaultPaymentMode, lastCategory, lastPaymentMode;
        final List<String> categories, paymentModes;

        BackupSettings(int theme, String currencySymbol, String deviceName, List<String> categories,
                List<String> paymentModes, String defaultCategory, String defaultPaymentMode,
                String lastCategory, String lastPaymentMode, boolean recentEntriesHidden) {
            this.theme = theme;
            this.currencySymbol = currencySymbol;
            this.deviceName = deviceName;
            this.categories = new ArrayList<>(categories);
            this.paymentModes = new ArrayList<>(paymentModes);
            this.defaultCategory = defaultCategory;
            this.defaultPaymentMode = defaultPaymentMode;
            this.lastCategory = lastCategory;
            this.lastPaymentMode = lastPaymentMode;
            this.recentEntriesHidden = recentEntriesHidden;
        }

        boolean sameAs(BackupSettings other) {
            return other != null && theme == other.theme && recentEntriesHidden == other.recentEntriesHidden
                    && currencySymbol.equals(other.currencySymbol)
                    && deviceName.equals(other.deviceName) && categories.equals(other.categories)
                    && paymentModes.equals(other.paymentModes) && defaultCategory.equals(other.defaultCategory)
                    && defaultPaymentMode.equals(other.defaultPaymentMode)
                    && lastCategory.equals(other.lastCategory) && lastPaymentMode.equals(other.lastPaymentMode);
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    void recordOperation(String operation, boolean success, String detail) {
        prefs.edit()
                .putString("lastOperation", operation)
                .putBoolean("lastSuccess", success)
                .putString("lastDetail", detail)
                .putLong("lastAt", System.currentTimeMillis())
                .apply();
    }
    String  lastOperation() { return prefs.getString("lastOperation", "No operations yet"); }
    boolean lastSuccess()   { return prefs.getBoolean("lastSuccess", false); }
    String  lastDetail()    { return prefs.getString("lastDetail", "No status recorded"); }
    long    lastAt()        { return prefs.getLong("lastAt", 0); }

    // ── Anonymous install ID (for privacy-conscious usage stats) ──────────────

    String installId() {
        String id = prefs.getString("installId", null);
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("installId", id).apply();
        }
        return id;
    }

    // ── Emoji / canonical helpers (identical to V1) ───────────────────────────

    /** Canonical stored form: "Name 🏷️" — text, space, emoji. */
    String canonical(String value, String fallbackEmoji) {
        return format(name(value), emoji(value).isEmpty() ? fallbackEmoji : emoji(value));
    }
    String format(String name, String emoji) {
        return name == null || name.trim().isEmpty() ? "" : name.trim() + " " + (emoji == null || emoji.trim().isEmpty() ? "🏷️" : emoji.trim());
    }
    /** The full stored/transmitted value including emoji suffix. Never strips emoji before sending to Sheet. */
    String backendValue(String value) { return value == null ? "" : value.trim(); }
    String emoji(String value) {
        if (value == null) return "";
        Matcher suffix = SUFFIX.matcher(value.trim()); if (suffix.matches()) return suffix.group(2).trim();
        Matcher prefix = PREFIX.matcher(value.trim()); return prefix.matches() ? prefix.group(1).trim() : "";
    }
    String name(String value) {
        if (value == null) return "";
        String clean = value.trim();
        Matcher suffix = SUFFIX.matcher(clean); if (suffix.matches()) return suffix.group(1).trim();
        Matcher prefix = PREFIX.matcher(clean); return prefix.matches() ? prefix.group(2).trim() : clean;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<String> normalized(String key, List<String> fallback, String fallbackEmoji) {
        String raw = prefs.getString(key, null);
        List<String> values = raw == null ? new ArrayList<>(fallback) : new ArrayList<>(Arrays.asList(raw.split(SEP, -1)));
        List<String> canonical = canonicalize(values, fallbackEmoji);
        if (raw != null && !TextUtils.join(SEP, canonical).equals(raw)) setList(key, canonical);
        return canonical;
    }
    private String canonicalPreference(String key, String fallbackEmoji) {
        String raw = prefs.getString(key, "");
        String formatted = canonical(raw, fallbackEmoji);
        if (!formatted.equals(raw)) prefs.edit().putString(key, formatted).apply();
        return formatted;
    }
    private List<String> canonicalize(List<String> source, String fallbackEmoji) {
        List<String> result = new ArrayList<>();
        for (String value : source) {
            String c = canonical(value, fallbackEmoji);
            if (!c.isEmpty()) result.add(c);
        }
        return result;
    }
    private void setList(String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join(SEP, values)).apply();
    }
}
