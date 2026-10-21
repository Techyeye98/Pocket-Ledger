package com.pocketledger.v2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data access object for local transactions.
 *
 * All queries exclude tombstoned rows by default (deleted = 0).
 * Tombstoned rows are kept until explicit sync confirms deletion on Sheet.
 */
@Dao
public interface TransactionDao {

    // ── INSERT / UPDATE ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TransactionEntity tx);

    @Update
    void update(TransactionEntity tx);

    // ── READ ─────────────────────────────────────────────────────────────────

    /** All non-deleted transactions, newest first. */
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY effectiveAt DESC")
    List<TransactionEntity> getAll();

    /** All transactions including soft-deleted tombstones (used for full backups). */
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    List<TransactionEntity> getAllForBackup();

    /** Transactions for a specific time range (inclusive), non-deleted, newest first. */
    @Query("SELECT * FROM transactions WHERE deleted = 0 AND effectiveAt >= :fromTime AND effectiveAt <= :toTime ORDER BY effectiveAt DESC")
    List<TransactionEntity> getByTimeRange(long fromTime, long toTime);

    /** Single transaction by UUID (including tombstoned — used for sync). */
    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    TransactionEntity findById(String id);

    /** Rows pending sync (new, edited, or deleted). */
    @Query("SELECT * FROM transactions WHERE syncStatus != 'SYNCED'")
    List<TransactionEntity> getPendingSync();

    // ── SOFT DELETE ──────────────────────────────────────────────────────────

    /**
     * Tombstone a transaction. Row is kept so deletion can sync safely.
     * updatedAt and syncStatus are updated to allow the sync layer to push deletion.
     */
    @Query("UPDATE transactions SET deleted = 1, syncStatus = 'SYNC_PENDING', updatedAt = :now WHERE transactionId = :id")
    void softDelete(String id, long now);

    // ── SYNC HELPERS ─────────────────────────────────────────────────────────

    @Query("UPDATE transactions SET syncStatus = 'SYNCED' WHERE transactionId = :id")
    void markSynced(String id);

    // Keep deletion tombstones so imports cannot resurrect removed financial records.
}
