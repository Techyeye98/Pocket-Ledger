package com.pocketledger.v2;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Local transaction record — the primary source of truth in V2.
 *
 * transactionId: stable UUID generated at creation time.
 *   Never relies on Google Sheet row number as identity.
 *
 * syncStatus: LOCAL_ONLY | SYNCED | SYNC_PENDING
 * deleted: tombstone flag — row is kept for safe sync/restore rather than hard-deleted.
 */
@Entity(tableName = "transactions")
public final class TransactionEntity {

    public static final String SYNC_LOCAL_ONLY   = "LOCAL_ONLY";
    public static final String SYNC_PENDING      = "SYNC_PENDING";
    public static final String SYNC_SYNCED       = "SYNCED";

    @PrimaryKey
    @NonNull
    public String transactionId = "";   // UUID, e.g. "550e8400-e29b-..."

    public double amount;
    public String category  = "";
    public String paymentMode = "";
    public String remarks   = "";
    public String date      = "";       // "dd-MM-yyyy"
    public String time      = "";       // "HH:mm:ss"
    public String deviceName = "";
    public long   createdAt  = 0;       // epoch millis
    public long   updatedAt  = 0;       // epoch millis — updated on every local edit
    public String syncStatus = SYNC_LOCAL_ONLY;
    public boolean deleted   = false;   // tombstone — never hard-delete until after sync
}
