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
    static synchronized SyncResult clearTransactionHistory(Context context,ConfigStore store) throws Exception {
        // Read the Sheet first so cloud-only entries are included in the same reset.
        String address=store.url();Ledger remote=address.isEmpty()?null:SyncClient.restoreLedger(address);
        AppDatabase db=AppDatabase.get(context);
        db.runInTransaction(()->{
            Ledger local=Ledger.load(db),all=remote==null?local:LedgerJson.merge(local,remote);
            LedgerJson.persist(db,all.clearedTransactions(System.currentTimeMillis()));
        });
        try{return syncAllPending(context,address);}catch(Exception e){return new SyncResult(0,1,e.getMessage());}
    }

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
    static synchronized SyncResult syncAllPending(Context context, ConfigStore store) {
        return syncAllPending(context,store.url());
    }
    private static SyncResult syncAllPending(Context context,String url) {
        if (url == null || url.trim().isEmpty()) {
            return new SyncResult(0, 0, "");
        }

        AppDatabase db=AppDatabase.get(context);
        final Ledger[] snapshot=new Ledger[1];
        db.runInTransaction(()->snapshot[0]=Ledger.load(db));
        try {
            String[] reportWarning={""};
            Ledger confirmed=SyncClient.pushLedger(url,snapshot[0],java.util.UUID.randomUUID().toString(),reportWarning);
            db.runInTransaction(()->{
                Ledger local=Ledger.load(db);
                Ledger merged=LedgerJson.merge(local,confirmed);
                LedgerJson.persist(db,merged);
                for(TransactionEntity sent:snapshot[0].transactions) {
                    TransactionEntity current=db.transactionDao().findById(sent.transactionId);
                    TransactionEntity remote=confirmed.transaction(sent.transactionId);
                    if(current!=null&&remote!=null&&current.updatedAt==sent.updatedAt&&remote.updatedAt==sent.updatedAt)db.transactionDao().markSynced(sent.transactionId);
                }
            });
            return new SyncResult(snapshot[0].transactions.size()+snapshot[0].accounts.size()+snapshot[0].routes.size()+snapshot[0].people.size(),0,reportWarning[0]);
        } catch(Exception e) {return new SyncResult(0,Math.max(1,snapshot[0].transactions.size()),e.getMessage());}

    }
}
