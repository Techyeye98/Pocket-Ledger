package com.pocketledger.v2;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * V2 Room database — single shared instance.
 *
 * The local database is the primary source of truth.
 * Google Sheets is a secondary mirror; sync is always explicit, never automatic.
 */
@Database(entities = {TransactionEntity.class, AccountEntity.class, PaymentRouteEntity.class, PersonEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    private static final String DB_NAME = "pocket_ledger_v2.db";
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN spendingType TEXT NOT NULL DEFAULT 'UNTAGGED'");
        }
    };

    public abstract TransactionDao transactionDao();
    public abstract LedgerDao ledgerDao();

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN amountMinor INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE transactions ADD COLUMN personalMinor INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE transactions ADD COLUMN effectiveAt INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN accountId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN destinationId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN routeId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN personId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN originalId TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE transactions ADD COLUMN shares TEXT NOT NULL DEFAULT '{}'");
            db.execSQL("CREATE TABLE IF NOT EXISTS accounts (accountId TEXT NOT NULL PRIMARY KEY, name TEXT, type TEXT, openingMinor INTEGER NOT NULL, openingAt INTEGER NOT NULL, position INTEGER NOT NULL, archived INTEGER NOT NULL, deleted INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS payment_routes (routeId TEXT NOT NULL PRIMARY KEY, label TEXT, accountId TEXT, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS people (personId TEXT NOT NULL PRIMARY KEY, name TEXT, archived INTEGER NOT NULL, deleted INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
            try (android.database.Cursor rows = db.query("SELECT transactionId, amount, date, time, createdAt FROM transactions")) {
                while (rows.moveToNext()) {
                    long minor = Money.legacy(rows.getDouble(1));
                    long at = Ledger.effectiveDate(rows.getString(2), rows.getString(3), rows.getLong(4));
                    db.execSQL("UPDATE transactions SET amountMinor=?, personalMinor=?, effectiveAt=? WHERE transactionId=?",
                            new Object[]{minor, minor, at, rows.getString(0)});
                }
            }
        }
    };

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return INSTANCE;
    }
}
