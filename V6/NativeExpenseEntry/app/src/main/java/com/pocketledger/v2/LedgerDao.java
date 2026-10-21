package com.pocketledger.v2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY position, name") List<AccountEntity> accounts();
    @Query("SELECT * FROM payment_routes ORDER BY label") List<PaymentRouteEntity> routes();
    @Query("SELECT * FROM people ORDER BY name") List<PersonEntity> people();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putAccount(AccountEntity account);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putRoute(PaymentRouteEntity route);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putPerson(PersonEntity person);
}
