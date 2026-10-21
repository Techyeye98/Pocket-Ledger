package com.pocketledger.v2;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public final class AccountEntity {
    @PrimaryKey @NonNull public String accountId = "";
    public String name = "";
    public String type = "BANK";
    public long openingMinor;
    public long openingAt;
    public int position;
    public boolean archived;
    public boolean deleted;
    public long updatedAt;
    public boolean isCard() { return "CARD".equals(type); }
}
