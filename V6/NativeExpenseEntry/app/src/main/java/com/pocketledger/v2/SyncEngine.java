package com.pocketledger.v2;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared foreground sync engine — processes the ENTIRE pending-transaction queue.
 *
 * Called from:
 *   - Settings → Sync Now   (explicit manual sync, with user-facing toasts)
 *   - Expense saved locally  (immediate foreground sync of new + previously failed txns)
 *
 * Runs on the caller's background executor; never touches the UI.
 * Google Sheets is the cloud mirror; Room is the primary local store + pending queue.
 */
final class SyncEngine {

    static final class SyncResult {
        final int    synced;
        final int    failed;
        final String lastError;

        SyncResult(int synced, int failed, String lastError) {
            this.synced    = synced;
            this.failed    = failed;
            this.lastError = lastError == null ? "" : lastError;
        }
    }

    /**
     * Process every non-SYNCED transaction in Room.
     *
     * If the Google Sheets URL is not configured, this is a silent no-op (the
     * caller can simply skip error/UI reporting).
     *
     * Returns aggregate counts so the caller decides what to show in the UI.
     * On success, each fully-synced transaction is marked SYNCED in Room;
     * failed transactions remain safely pending for the next sync.
     */
    static SyncResult syncAllPending(Context context, ConfigStore store) {
        String url = store.url();
        if (url == null || url.trim().isEmpty()) {
            return new SyncResult(0, 0, "");
        }

        TransactionDao dao = AppDatabase.get(context).transactionDao();
        List<TransactionEntity> pending = dao.getPendingSync();
        if (pending.isEmpty()) {
            return new SyncResult(0, 0, "");
        }

        // Decision #4 — push the whole queue in ONE HTTP POST instead of one
        // request per transaction. Apps Script cold-start (~1.5s) + sheet-lock
        // contention made serial sync take 30-60s for 20+ items; a batch does it
        // in a single round-trip and reports success/failure per item.
        List<SyncClient.PendingOp> ops = new ArrayList<>(pending.size());
        for (TransactionEntity tx : pending) {
            String action;
            if (tx.deleted)                                                    action = "DELETE";
            else if (TransactionEntity.SYNC_LOCAL_ONLY.equals(tx.syncStatus)) action = "CREATE";
            else                                                               action = "UPDATE";
            ops.add(new SyncClient.PendingOp(tx, action));
        }

        SyncClient.BatchResult br = SyncClient.pushBatch(url, ops, store);
        Set<String> confirmed = new HashSet<>(br.confirmedIds);

        int successCount = 0;
        int failCount    = 0;
        String lastErr   = br.lastError;

        for (SyncClient.PendingOp op : ops) {
            if (confirmed.contains(op.tx.transactionId)) {
                dao.markSynced(op.tx.transactionId);
                successCount++;
            } else {
                failCount++;
                if (lastErr.isEmpty()) {
                    lastErr = "Transaction " + op.tx.transactionId + " was not confirmed by the server.";
                }
            }
        }

        dao.purgeConfirmedDeletions();
        return new SyncResult(successCount, failCount, lastErr);
    }
}
