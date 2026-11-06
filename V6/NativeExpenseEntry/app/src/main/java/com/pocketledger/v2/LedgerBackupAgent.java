package com.pocketledger.v2;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FullBackupDataOutput;
import java.io.IOException;

/** A phone restore never reconnects a private Sheet, including restores of older backups. */
public final class LedgerBackupAgent extends BackupAgentHelper {
    @Override public void onFullBackup(FullBackupDataOutput data) throws IOException {
        new ConfigStore(this); // Remove legacy connection keys even if the upgraded app has not been opened.
        super.onFullBackup(data);
    }
    @Override public void onRestoreFinished() {
        super.onRestoreFinished();ConfigStore.clearRestoredConnection(this);
    }
}
