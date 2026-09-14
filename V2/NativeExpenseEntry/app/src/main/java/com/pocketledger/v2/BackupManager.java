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
 * Authoritative offline backup export and import manager for Pocket Ledger V2.
 *
 * Format:
 * {
 *   "format": "pocket-ledger-backup",
 *   "version": 1,
 *   "exportedAt": "2026-09-12T15:10:00Z",
 *   "appName": "Pocket Ledger",
 *   "appVersion": "2.0.0",
 *   "transactions": [ ... ]
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
    static final int SUPPORTED_VERSION = 1;

    private BackupManager() {}

    /**
     * Serializes transactions into the versioned backup JSON string.
     */
    static String createBackupJson(Context context, List<TransactionEntity> transactions) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", BACKUP_FORMAT);
        root.put("version", SUPPORTED_VERSION);

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        root.put("exportedAt", isoFormat.format(new Date()));
        root.put("appName", "Pocket Ledger");

        String versionName = "2.0.0";
        try {
            versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        root.put("appVersion", versionName);

        JSONArray arr = new JSONArray();
        for (TransactionEntity tx : transactions) {
            JSONObject obj = new JSONObject();
            obj.put("transactionId", tx.transactionId);
            obj.put("amount", tx.amount);
            obj.put("category", tx.category != null ? tx.category : "");
            obj.put("paymentMode", tx.paymentMode != null ? tx.paymentMode : "");
            obj.put("remarks", tx.remarks != null ? tx.remarks : "");
            obj.put("date", tx.date != null ? tx.date : "");
            obj.put("month", extractMonth(tx.date)); // "September" not "September-2026"
            obj.put("time", tx.time != null ? tx.time : "");
            obj.put("deviceName", tx.deviceName != null ? tx.deviceName : "");
            obj.put("createdAt", tx.createdAt);
            obj.put("updatedAt", tx.updatedAt);
            obj.put("syncStatus", tx.syncStatus != null ? tx.syncStatus : TransactionEntity.SYNC_LOCAL_ONLY);
            obj.put("deleted", tx.deleted);
            arr.put(obj);
        }
        root.put("transactions", arr);

        return root.toString(2);
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
                return ValidationResult.fail("Unsupported backup version: " + version + " (supported: 1).");
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
                if (amount < 0) {
                    return ValidationResult.fail("Transaction " + id + " contains an invalid amount.");
                }

                TransactionEntity tx = new TransactionEntity();
                tx.transactionId = id;
                tx.amount        = amount;
                tx.category      = obj.optString("category", "").trim();
                tx.paymentMode   = obj.optString("paymentMode", "").trim();
                tx.remarks       = obj.optString("remarks", "").trim();
                tx.date          = obj.optString("date", "").trim();
                tx.time          = obj.optString("time", "").trim();
                tx.deviceName    = obj.optString("deviceName", "").trim();
                tx.createdAt     = obj.optLong("createdAt", System.currentTimeMillis());
                tx.updatedAt     = obj.optLong("updatedAt", tx.createdAt);
                tx.syncStatus    = obj.optString("syncStatus", TransactionEntity.SYNC_PENDING);
                tx.deleted       = obj.optBoolean("deleted", false);

                list.add(tx);
            }

            return ValidationResult.ok(list);
        } catch (org.json.JSONException e) {
            return ValidationResult.fail("Invalid JSON file: " + e.getMessage());
        } catch (Exception e) {
            return ValidationResult.fail("Error reading backup: " + e.getMessage());
        }
    }

    /**
     * Non-destructive merge into Room database.
     * Uses transactionId as stable key and updatedAt for conflict resolution.
     */
    static MergeResult mergeBackup(TransactionDao dao, List<TransactionEntity> importedTxs) {
        int added = 0;
        int updated = 0;
        int deleted = 0;
        int preserved = 0;

        for (TransactionEntity imp : importedTxs) {
            TransactionEntity local = dao.findById(imp.transactionId);

            if (local == null) {
                if (imp.deleted) {
                    // Deleted in backup and doesn't exist locally: do not resurrect
                    preserved++;
                } else {
                    // New item: insert with SYNC_PENDING so user can sync later if desired
                    imp.syncStatus = TransactionEntity.SYNC_PENDING;
                    dao.insert(imp);
                    added++;
                }
            } else {
                // Exists locally: compare timestamps
                if (local.updatedAt >= imp.updatedAt) {
                    // Local is newer or identical: keep local version untouched
                    preserved++;
                } else {
                    // Imported version is newer
                    if (imp.deleted) {
                        // Apply tombstone to local transaction
                        dao.softDelete(local.transactionId, imp.updatedAt);
                        deleted++;
                    } else {
                        // Update with newer imported data
                        imp.syncStatus = TransactionEntity.SYNC_PENDING;
                        dao.update(imp);
                        updated++;
                    }
                }
            }
        }

        return new MergeResult(added, updated, deleted, preserved, importedTxs.size());
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

        private ValidationResult(boolean s, String err, List<TransactionEntity> t) {
            this.success = s;
            this.error = err;
            this.transactions = t != null ? t : Collections.emptyList();
        }

        static ValidationResult ok(List<TransactionEntity> t) {
            return new ValidationResult(true, null, t);
        }

        static ValidationResult fail(String err) {
            return new ValidationResult(false, err, Collections.emptyList());
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
