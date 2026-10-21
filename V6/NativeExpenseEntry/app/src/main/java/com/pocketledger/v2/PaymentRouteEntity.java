package com.pocketledger.v2;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "payment_routes")
public final class PaymentRouteEntity {
    @PrimaryKey @NonNull public String routeId = "";
    public String label = "";
    public String accountId = "";
    public long updatedAt;
    public boolean deleted;
}
