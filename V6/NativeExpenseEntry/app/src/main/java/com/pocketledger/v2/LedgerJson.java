package com.pocketledger.v2;

import org.json.*;
import java.util.*;

/** Versioned wire format shared by backups and the Sheets mirror. */
final class LedgerJson {
    static JSONObject transaction(TransactionEntity t) throws JSONException {
        JSONObject o=new JSONObject();
        o.put("transactionId",t.transactionId).put("amount",Ledger.amount(t)/100.0).put("amountMinor",Ledger.amount(t)).put("personalMinor",Ledger.personal(t));
        o.put("kind",t.kind).put("effectiveAt",Ledger.at(t)).put("accountId",t.accountId).put("destinationId",t.destinationId).put("routeId",t.routeId);
        o.put("personId",t.personId).put("originalId",t.originalId).put("shares",new JSONObject(t.shares));
        o.put("category",t.category).put("paymentMode",t.paymentMode).put("remarks",t.remarks).put("spendingType",t.spendingType);
        o.put("date",t.date).put("time",t.time).put("deviceName",t.deviceName).put("createdAt",t.createdAt).put("updatedAt",t.updatedAt).put("deleted",t.deleted).put("syncStatus",t.syncStatus);
        return o;
    }
    static TransactionEntity transaction(JSONObject o,boolean modern) throws JSONException {
        TransactionEntity t=new TransactionEntity();
        t.transactionId=o.getString("transactionId");t.amount=o.getDouble("amount");
        t.category=o.optString("category","");t.paymentMode=o.optString("paymentMode","");t.remarks=o.optString("remarks","");
        t.spendingType=o.optString("spendingType",SpendingType.UNTAGGED);t.date=o.optString("date","");t.time=o.optString("time","");t.deviceName=o.optString("deviceName","");
        t.createdAt=integer(o,"createdAt");t.updatedAt=integer(o,"updatedAt");t.deleted=o.optBoolean("deleted",false);t.syncStatus=TransactionEntity.SYNC_PENDING;
        if(modern) {
            t.kind=o.getString("kind");t.amountMinor=integer(o,"amountMinor");t.personalMinor=integer(o,"personalMinor");t.effectiveAt=integer(o,"effectiveAt");
            t.accountId=o.getString("accountId");t.destinationId=o.getString("destinationId");t.routeId=o.getString("routeId");t.personId=o.getString("personId");t.originalId=o.getString("originalId");t.shares=o.getJSONObject("shares").toString();
            Ledger.require(Double.isFinite(t.amount)&&Math.abs(t.amount-t.amountMinor/100.0)<0.000001,"Conflicting transaction amounts");
        } else {t.amountMinor=Money.legacy(t.amount);t.personalMinor=t.amountMinor;t.effectiveAt=Ledger.effectiveDate(t.date,t.time,t.createdAt);}
        Ledger.require(SpendingType.normalize(t.spendingType).equals(t.spendingType),"Invalid spending tag");
        return t;
    }
    static long integer(JSONObject o,String key) throws JSONException {
        Object n=o.get(key);Ledger.require(n instanceof Number&&((Number)n).doubleValue()==((Number)n).longValue(),"Invalid integer: "+key);
        long value=((Number)n).longValue();Ledger.require(value<=Money.MAX&&value>=-Money.MAX,"Value out of range: "+key);return value;
    }
    static JSONObject write(Ledger l) throws JSONException {
        JSONObject root=new JSONObject().put("ledgerSchema",1);JSONArray a=new JSONArray(),r=new JSONArray(),p=new JSONArray(),t=new JSONArray();
        for(AccountEntity x:l.accounts)a.put(new JSONObject().put("accountId",x.accountId).put("name",x.name).put("type",x.type).put("openingMinor",x.openingMinor).put("openingAt",x.openingAt).put("position",x.position).put("archived",x.archived).put("deleted",x.deleted).put("updatedAt",x.updatedAt));
        for(PaymentRouteEntity x:l.routes)r.put(new JSONObject().put("routeId",x.routeId).put("label",x.label).put("accountId",x.accountId).put("deleted",x.deleted).put("updatedAt",x.updatedAt));
        for(PersonEntity x:l.people)p.put(new JSONObject().put("personId",x.personId).put("name",x.name).put("archived",x.archived).put("deleted",x.deleted).put("updatedAt",x.updatedAt));
        for(TransactionEntity x:l.transactions)t.put(transaction(x));
        return root.put("accounts",a).put("paymentRoutes",r).put("people",p).put("transactions",t);
    }
    static Ledger read(JSONObject root) throws JSONException {
        Ledger.require(root.getInt("ledgerSchema")==1,"Unsupported ledger schema");
        List<AccountEntity>a=new ArrayList<>();List<PaymentRouteEntity>r=new ArrayList<>();List<PersonEntity>p=new ArrayList<>();List<TransactionEntity>t=new ArrayList<>();
        JSONArray arr=root.getJSONArray("accounts");
        for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);AccountEntity x=new AccountEntity();x.accountId=o.getString("accountId");x.name=o.getString("name");x.type=o.getString("type");x.openingMinor=integer(o,"openingMinor");x.openingAt=integer(o,"openingAt");x.position=o.getInt("position");x.archived=o.getBoolean("archived");x.deleted=o.getBoolean("deleted");x.updatedAt=integer(o,"updatedAt");a.add(x);}
        arr=root.getJSONArray("paymentRoutes");for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);PaymentRouteEntity x=new PaymentRouteEntity();x.routeId=o.getString("routeId");x.label=o.getString("label");x.accountId=o.getString("accountId");x.deleted=o.getBoolean("deleted");x.updatedAt=integer(o,"updatedAt");r.add(x);}
        arr=root.getJSONArray("people");for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);PersonEntity x=new PersonEntity();x.personId=o.getString("personId");x.name=o.getString("name");x.archived=o.getBoolean("archived");x.deleted=o.getBoolean("deleted");x.updatedAt=integer(o,"updatedAt");p.add(x);}
        arr=root.getJSONArray("transactions");for(int i=0;i<arr.length();i++)t.add(transaction(arr.getJSONObject(i),true));
        Ledger l=new Ledger(a,r,p,t);l.validate();return l;
    }
    static Ledger merge(Ledger local,Ledger remote) {
        List<AccountEntity>a=new ArrayList<>(local.accounts);for(AccountEntity x:remote.accounts){AccountEntity old=local.account(x.accountId);if(old==null||old.updatedAt<x.updatedAt){a.removeIf(y->y.accountId.equals(x.accountId));a.add(x);}}
        List<PersonEntity>p=new ArrayList<>(local.people);for(PersonEntity x:remote.people){PersonEntity old=local.person(x.personId);if(old==null||old.updatedAt<x.updatedAt){p.removeIf(y->y.personId.equals(x.personId));p.add(x);}}
        List<PaymentRouteEntity>r=new ArrayList<>(local.routes);for(PaymentRouteEntity x:remote.routes){PaymentRouteEntity old=null;for(PaymentRouteEntity y:r)if(y.routeId.equals(x.routeId))old=y;if(old==null||old.updatedAt<x.updatedAt){r.removeIf(y->y.routeId.equals(x.routeId));r.add(x);}}
        List<TransactionEntity>t=new ArrayList<>(local.transactions);for(TransactionEntity x:remote.transactions){TransactionEntity old=local.transaction(x.transactionId);if(old==null||old.updatedAt<x.updatedAt){t.removeIf(y->y.transactionId.equals(x.transactionId));t.add(x);}}
        Ledger result=new Ledger(a,r,p,t);result.validate();return result;
    }
    static void persist(AppDatabase db,Ledger l) {
        for(AccountEntity a:l.accounts)db.ledgerDao().putAccount(a);
        for(PersonEntity p:l.people)db.ledgerDao().putPerson(p);
        for(PaymentRouteEntity r:l.routes)db.ledgerDao().putRoute(r);
        for(TransactionEntity t:l.transactions)db.transactionDao().insert(t);
    }
    static BackupManager.MergeResult importLedger(AppDatabase db,Ledger remote) {
        final BackupManager.MergeResult[] result=new BackupManager.MergeResult[1];
        db.runInTransaction(()->{
            Ledger local=Ledger.load(db),merged=merge(local,remote);int added=0,updated=0,deleted=0,preserved=0;
            for(TransactionEntity incoming:remote.transactions){TransactionEntity old=local.transaction(incoming.transactionId);
                if(old==null){if(incoming.deleted)deleted++;else added++;}
                else if(old.updatedAt>=incoming.updatedAt)preserved++;
                else if(incoming.deleted)deleted++;else updated++;
            }
            persist(db,merged);result[0]=new BackupManager.MergeResult(added,updated,deleted,preserved,remote.transactions.size());
        });
        return result[0];
    }
}
