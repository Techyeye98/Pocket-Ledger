package com.pocketledger.v2;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Authoritative offline backup export and import manager for Pocket Ledger.
 *
 * Format:
 * {
 *   "format": "pocket-ledger-backup",
 *   "version": 3,
 *   "exportedAt": "2026-09-12T15:10:00Z",
 *   "appName": "Pocket Ledger",
 *   "appVersion": "2.0.0",
 *   "transactions": [ ... ],
 *   "settings": { ... }, // optional for older backups
 * }
 *
 * Invariants:
 * - JSON is the primary and authoritative backup format.
 * - Emojis in category and payment mode are preserved exactly.
 * - Month is always "September", not "September-2026".
 * - Never blindly replaces or wipes the Room database.
 * - Matches by stable transactionId, preserves newer local data based on updatedAt.
 */
final class BackupManager {

    static final String BACKUP_FORMAT = "pocket-ledger-backup";
    static final int SUPPORTED_VERSION = 4;

    private BackupManager() {}

    /**
     * Serializes transactions and portable user settings into the backup JSON string.
     */
    static String createBackupJson(Context context, List<TransactionEntity> transactions) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", BACKUP_FORMAT);
        root.put("version", 3); // Financial ledger only; split bills remain device-local.

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        root.put("exportedAt", isoFormat.format(new Date()));
        root.put("appName", "Pocket Ledger");

        String versionName = "2.0.0";
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        root.put("appVersion", versionName);

        ConfigStore.BackupSettings settings = new ConfigStore(context).backupSettings();
        JSONObject savedSettings = new JSONObject();
        savedSettings.put("theme", settings.theme);
        savedSettings.put("currencySymbol", settings.currencySymbol);
        savedSettings.put("deviceName", settings.deviceName);
        savedSettings.put("categories", stringArray(settings.categories));
        savedSettings.put("paymentModes", stringArray(settings.paymentModes));
        savedSettings.put("defaultCategory", settings.defaultCategory);
        savedSettings.put("defaultPaymentMode", settings.defaultPaymentMode);
        savedSettings.put("lastCategory", settings.lastCategory);
        savedSettings.put("lastPaymentMode", settings.lastPaymentMode);
        savedSettings.put("recentEntriesHidden", settings.recentEntriesHidden);
        root.put("settings", savedSettings);

        AppDatabase db = AppDatabase.get(context);
        final JSONObject[] snapshot = new JSONObject[1];
        db.runInTransaction(() -> { try { snapshot[0] = LedgerJson.write(Ledger.load(db)); } catch (Exception e) { throw new IllegalStateException(e); } });
        JSONObject ledger = snapshot[0];
        root.put("ledgerSchema",1).put("accounts",ledger.getJSONArray("accounts")).put("paymentRoutes",ledger.getJSONArray("paymentRoutes")).put("people",ledger.getJSONArray("people")).put("transactions",ledger.getJSONArray("transactions"));
        JSONObject preferences = new JSONObject();
        preferences.put("incomeCategories",stringArray(new ConfigStore(context).incomeCategories()));
        android.content.SharedPreferences ui=context.getSharedPreferences("ledger_ui",0);
        for(String key:new String[]{"defaultPayment","lastPayment","selectedAccount"})preferences.put(key,ui.getString(key,""));
        root.put("ledgerPreferences",preferences);

        String json = root.toString(2);
        ValidationResult check = validateBackup(json);
        if (!check.success || !settings.sameAs(check.settings))
            throw new IllegalStateException("Backup verification failed: " + check.error);
        return json;
    }

    private static JSONArray stringArray(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    /**
     * Validates a backup JSON string before any database operations occur.
     */
    static ValidationResult validateBackup(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return ValidationResult.fail("The selected file is empty.");
        }

        try {
            JSONObject root = new JSONObject(jsonStr);

            String format = root.optString("format", "");
            if (!BACKUP_FORMAT.equals(format)) {
                return ValidationResult.fail("Invalid backup format: expected '" + BACKUP_FORMAT + "'.");
            }

            int version = root.optInt("version", -1);
            if (version < 1 || version > SUPPORTED_VERSION) {
                return ValidationResult.fail("Unsupported backup version: " + version + " (supported: 1–4).");
            }

            if (version >= 3) {
                Ledger ledger = LedgerJson.read(root);
                ConfigStore.BackupSettings settings = root.has("settings") ? readSettings(root.getJSONObject("settings")) : null;
                ValidationResult result = ValidationResult.ok(ledger.transactions,settings);
                result.ledger = ledger;
                // Accept older preview v4 financial data without importing its optional splitter data.
                if(root.has("ledgerPreferences")) {
                    JSONObject preferences=root.getJSONObject("ledgerPreferences");readList(preferences,"incomeCategories");
                    for(String key:new String[]{"defaultPayment","lastPayment","selectedAccount"})readText(preferences,key);
                    result.preferences=preferences;
                }
                return result;
            }

            JSONArray arr = root.optJSONArray("transactions");
            if (arr == null) {
                return ValidationResult.fail("Backup file does not contain a transactions list.");
            }

            List<TransactionEntity> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    return ValidationResult.fail("Malformed transaction item at position " + (i + 1) + ".");
                }

                String id = obj.optString("transactionId", "").trim();
                if (id.isEmpty()) {
                    return ValidationResult.fail("Transaction at position " + (i + 1) + " is missing a transactionId.");
                }

                double amount = obj.optDouble("amount", -1);
                if (!Double.isFinite(amount) || amount < 0) {
                    return ValidationResult.fail("Transaction " + id + " contains an invalid amount.");
                }

                TransactionEntity tx = new TransactionEntity();
                tx.transactionId = id;
                tx.amount        = amount;
                tx.category      = obj.optString("category", "").trim();
                tx.paymentMode   = obj.optString("paymentMode", "").trim();
                tx.remarks       = obj.optString("remarks", "").trim();
                if (obj.has("spendingType") && (!(obj.opt("spendingType") instanceof String)
                        || !SpendingType.normalize(obj.getString("spendingType")).equals(obj.getString("spendingType"))))
                    return ValidationResult.fail("Transaction " + id + " has an invalid spending type.");
                tx.spendingType = obj.optString("spendingType", SpendingType.UNTAGGED);
                tx.date          = obj.optString("date", "").trim();
                tx.time          = obj.optString("time", "").trim();
                tx.deviceName    = obj.optString("deviceName", "").trim();
                tx.createdAt     = obj.optLong("createdAt", System.currentTimeMillis());
                tx.updatedAt     = obj.optLong("updatedAt", tx.createdAt);
                tx.syncStatus    = obj.optString("syncStatus", TransactionEntity.SYNC_PENDING);
                tx.deleted       = obj.optBoolean("deleted", false);

                tx.amountMinor=Money.legacy(tx.amount);tx.personalMinor=tx.amountMinor;tx.effectiveAt=Ledger.effectiveDate(tx.date,tx.time,tx.createdAt);
                list.add(tx);
            }

            ConfigStore.BackupSettings settings = null;
            if (root.has("settings")) {
                JSONObject savedSettings = root.optJSONObject("settings");
                if (savedSettings == null) return ValidationResult.fail("Backup settings are malformed.");
                settings = readSettings(savedSettings);
            }
            return ValidationResult.ok(list, settings);
        } catch (org.json.JSONException e) {
            return ValidationResult.fail("Invalid JSON file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail("Invalid backup settings: " + e.getMessage());
        } catch (Exception e) {
            return ValidationResult.fail("Error reading backup: " + e.getMessage());
        }
    }

    private static ConfigStore.BackupSettings readSettings(JSONObject obj) {
        Object rawTheme = obj.opt("theme");
        if (!(rawTheme instanceof Number) || ((Number) rawTheme).doubleValue() != ((Number) rawTheme).intValue())
            throw new IllegalArgumentException("Theme must be System, Light, or Dark.");
        int theme = ((Number) rawTheme).intValue();
        if (theme < 0 || theme > 2) throw new IllegalArgumentException("Theme is out of range.");
        String currency = readText(obj, "currencySymbol");
        String device = readText(obj, "deviceName");
        List<String> categories = readList(obj, "categories");
        List<String> payments = readList(obj, "paymentModes");
        String defaultCategory = readText(obj, "defaultCategory");
        String defaultPayment = readText(obj, "defaultPaymentMode");
        String lastCategory = readText(obj, "lastCategory");
        String lastPayment = readText(obj, "lastPaymentMode");
        Object savedHidden = obj.opt("recentEntriesHidden");
        if (savedHidden != null && !(savedHidden instanceof Boolean))
            throw new IllegalArgumentException("Recent entries visibility must be true or false.");
        boolean recentEntriesHidden = Boolean.TRUE.equals(savedHidden);
        if (!defaultCategory.isEmpty() && !categories.contains(defaultCategory))
            throw new IllegalArgumentException("Default category is not in the saved categories.");
        if (!defaultPayment.isEmpty() && !payments.contains(defaultPayment))
            throw new IllegalArgumentException("Default payment method is not in the saved methods.");
        return new ConfigStore.BackupSettings(theme, currency, device, categories, payments,
                defaultCategory, defaultPayment, lastCategory, lastPayment, recentEntriesHidden);
    }

    private static String readText(JSONObject obj, String key) {
        Object raw = obj.opt(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException(key + " must be text.");
        String value = (String) raw;
        if (value.indexOf('\u001F') >= 0) throw new IllegalArgumentException(key + " contains an invalid separator.");
        return value;
    }

    private static List<String> readList(JSONObject obj, String key) {
        JSONArray array = obj.optJSONArray(key);
        if (array == null || array.length() == 0)
            throw new IllegalArgumentException(key + " must contain at least one item.");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (!(raw instanceof String)) throw new IllegalArgumentException(key + " has a non-text item.");
            String value = (String) raw;
            if (value.trim().isEmpty() || !value.equals(value.trim())
                    || value.indexOf('\u001F') >= 0 || result.contains(value))
                throw new IllegalArgumentException(key + " has an empty, duplicate, or invalid item.");
            result.add(value);
        }
        return result;
    }

    private static String extractMonth(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            Date d = parser.parse(dateStr);
            return new SimpleDateFormat("MMMM", Locale.US).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    // ── Result Classes ────────────────────────────────────────────────────────

    static final class ValidationResult {
        final boolean success;
        final String error;
        final List<TransactionEntity> transactions;
        final ConfigStore.BackupSettings settings;
        Ledger ledger;
        JSONObject preferences;

        private ValidationResult(boolean s, String err, List<TransactionEntity> t, ConfigStore.BackupSettings settings) {
            this.success = s;
            this.error = err;
            this.transactions = t != null ? t : Collections.emptyList();
            this.settings = settings;
        }

        static ValidationResult ok(List<TransactionEntity> t, ConfigStore.BackupSettings settings) {
            return new ValidationResult(true, null, t, settings);
        }

        static ValidationResult fail(String err) {
            return new ValidationResult(false, err, Collections.emptyList(), null);
        }
    }

    static final class MergeResult {
        final int added;
        final int updated;
        final int deleted;
        final int preserved;
        final int total;

        MergeResult(int a, int u, int d, int p, int tot) {
            this.added = a;
            this.updated = u;
            this.deleted = d;
            this.preserved = p;
            this.total = tot;
        }
    }
}
