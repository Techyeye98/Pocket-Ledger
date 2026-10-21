package com.pocketledger.v2;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "people")
public final class PersonEntity {
    @PrimaryKey @NonNull public String personId = "";
    public String name = "";
    public boolean archived;
    public boolean deleted;
    public long updatedAt;
}
