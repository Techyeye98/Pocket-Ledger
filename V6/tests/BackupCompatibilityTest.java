package com.pocketledger.v2;
import org.json.JSONObject;
public final class BackupCompatibilityTest {
    public static void main(String[] args) throws Exception {
        JSONObject root=LedgerJson.write(LedgerTest.base()).put("format",BackupManager.BACKUP_FORMAT).put("version",3);
        if(!BackupManager.validateBackup(root.toString()).success)throw new AssertionError("Financial v3 backup rejected");
        root.put("version",4).put("splitter","ignored old preview split data");
        if(!BackupManager.validateBackup(root.toString()).success)throw new AssertionError("Old v4 financial data must import independently of split data");
        root.remove("splitter");
        if(!BackupManager.validateBackup(root.toString()).success)throw new AssertionError("Financial backup must not require split data");
        System.out.println("PASS: financial-only v3/v4 compatibility; split data ignored during import.");
    }
}
