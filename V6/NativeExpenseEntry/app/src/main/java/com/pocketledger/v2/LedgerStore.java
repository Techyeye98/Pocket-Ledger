package com.pocketledger.v2;

import android.content.Context;
import java.util.*;

/** Validates whole logical operations and commits all their effects together. */
final class LedgerStore {
    static void save(Context context,TransactionEntity tx) {
        AppDatabase db=AppDatabase.get(context);
        db.runInTransaction(()->{
            Ledger before=Ledger.load(db);TransactionEntity old=before.transaction(tx.transactionId);
            for(String id:Arrays.asList(tx.accountId,tx.destinationId))if(!id.isEmpty()){
                AccountEntity a=before.account(id);
                Ledger.require(a!=null&&!a.deleted&&(!a.archived||(old!=null&&(old.accountId.equals(id)||old.destinationId.equals(id)))),"Choose an active account");
            }
            tx.amountMinor=Ledger.amount(tx);tx.personalMinor=Ledger.personal(tx);tx.amount=tx.amountMinor/100.0;
            tx.effectiveAt=Ledger.at(tx);tx.updatedAt=Math.max(System.currentTimeMillis(),old==null?0:old.updatedAt+1);tx.syncStatus=TransactionEntity.SYNC_PENDING;
            List<TransactionEntity> all=new ArrayList<>(before.transactions);all.removeIf(t->t.transactionId.equals(tx.transactionId));all.add(tx);
            new Ledger(before.accounts,before.routes,before.people,all).validate();
            db.transactionDao().insert(tx);
        });
    }
    static void delete(Context context,String id) {
        AppDatabase db=AppDatabase.get(context);
        db.runInTransaction(()->{
            Ledger l=Ledger.load(db);TransactionEntity t=l.transaction(id);Ledger.require(t!=null,"Entry missing");
            for(TransactionEntity child:l.transactions)Ledger.require(child.deleted||!id.equals(child.originalId),"Resolve linked repayments/refunds first");
            t.deleted=true;t.updatedAt=System.currentTimeMillis();t.syncStatus=TransactionEntity.SYNC_PENDING;
            l.validate();db.transactionDao().insert(t);
        });
    }
    static void putAccount(Context c,AccountEntity account) {
        AppDatabase db=AppDatabase.get(c);db.runInTransaction(()->{
            Ledger l=Ledger.load(db);l.accounts.removeIf(a->a.accountId.equals(account.accountId));l.accounts.add(account);l.validate();db.ledgerDao().putAccount(account);
        });
    }
    static void putPerson(Context c,PersonEntity person) {
        AppDatabase db=AppDatabase.get(c);db.runInTransaction(()->{
            Ledger l=Ledger.load(db);l.people.removeIf(p->p.personId.equals(person.personId));l.people.add(person);l.validate();db.ledgerDao().putPerson(person);
        });
    }
    static void mapPayment(Context c,String label,String accountId) {
        AppDatabase db=AppDatabase.get(c);db.runInTransaction(()->{
            Ledger l=Ledger.load(db);AccountEntity a=l.account(accountId);Ledger.require(a!=null&&!a.deleted&&!a.archived,"Choose an active account");
            PaymentRouteEntity route=null;for(PaymentRouteEntity r:l.routes)if(!r.deleted&&r.label.equals(label))route=r;
            if(route!=null)Ledger.require(route.accountId.equals(accountId),"This payment choice already has an account. Create another alias instead.");
            else{route=new PaymentRouteEntity();route.routeId=UUID.randomUUID().toString();route.label=label;route.accountId=accountId;}
            route.updatedAt=System.currentTimeMillis();db.ledgerDao().putRoute(route);
            for(TransactionEntity t:l.transactions)if(!t.deleted&&t.accountId.isEmpty()&&t.paymentMode.equals(label)) {
                t.accountId=accountId;t.routeId=route.routeId;t.updatedAt=System.currentTimeMillis();t.syncStatus=TransactionEntity.SYNC_PENDING;db.transactionDao().insert(t);
            }
            Ledger.load(db).validate();
        });
    }
}
