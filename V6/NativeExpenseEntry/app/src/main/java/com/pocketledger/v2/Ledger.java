package com.pocketledger.v2;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** One calculation model for balances, personal reports, and outstanding items. */
final class Ledger {
    static final String[] KINDS = {"EXPENSE", "INCOME", "TRANSFER", "LEND", "BORROW", "FRIEND_PAID",
            "REPAYMENT_IN", "REPAYMENT_OUT", "WRITE_OFF_RECEIVABLE", "FORGIVE_PAYABLE", "REFUND", "ADJUSTMENT"};
    final List<AccountEntity> accounts;
    final List<PaymentRouteEntity> routes;
    final List<PersonEntity> people;
    final List<TransactionEntity> transactions;

    Ledger(List<AccountEntity> a, List<PaymentRouteEntity> r, List<PersonEntity> p, List<TransactionEntity> t) {
        accounts = a; routes = r; people = p; transactions = t;
    }
    static Ledger load(AppDatabase db) {
        // Call only inside a DB transaction when a consistent backup/merge snapshot is required.
        return new Ledger(db.ledgerDao().accounts(), db.ledgerDao().routes(), db.ledgerDao().people(), db.transactionDao().getAllForBackup());
    }
    /** Retain deletion identities so a sync or old backup cannot restore cleared history. */
    Ledger clearedTransactions(long now) {
        Ledger copy=presentationCopy();List<TransactionEntity> cleared=new ArrayList<>();
        for(TransactionEntity old:transactions){
            TransactionEntity t=new TransactionEntity();t.transactionId=old.transactionId;t.amount=0;t.amountMinor=0;t.personalMinor=0;
            t.updatedAt=Math.max(now,Math.addExact(old.updatedAt,1));t.deleted=true;t.syncStatus=TransactionEntity.SYNC_PENDING;cleared.add(t);
        }
        Ledger result=new Ledger(copy.accounts,copy.routes,copy.people,cleared);result.validate();return result;
    }
    /** Cheap private UI snapshot; opening a screen never parses/validates JSON on the UI thread. */
    Ledger presentationCopy() {
        List<AccountEntity> a=new ArrayList<>();List<PaymentRouteEntity> r=new ArrayList<>();
        List<PersonEntity> p=new ArrayList<>();List<TransactionEntity> t=new ArrayList<>();
        for(AccountEntity source:accounts){AccountEntity x=new AccountEntity();x.accountId=source.accountId;x.name=source.name;x.type=source.type;x.openingMinor=source.openingMinor;x.openingAt=source.openingAt;x.position=source.position;x.archived=source.archived;x.deleted=source.deleted;x.updatedAt=source.updatedAt;a.add(x);}
        for(PaymentRouteEntity source:routes){PaymentRouteEntity x=new PaymentRouteEntity();x.routeId=source.routeId;x.label=source.label;x.accountId=source.accountId;x.updatedAt=source.updatedAt;x.deleted=source.deleted;r.add(x);}
        for(PersonEntity source:people){PersonEntity x=new PersonEntity();x.personId=source.personId;x.name=source.name;x.archived=source.archived;x.deleted=source.deleted;x.updatedAt=source.updatedAt;p.add(x);}
        for(TransactionEntity source:transactions){
            TransactionEntity x=new TransactionEntity();x.transactionId=source.transactionId;x.amount=source.amount;x.kind=source.kind;
            x.amountMinor=source.amountMinor;x.personalMinor=source.personalMinor;x.effectiveAt=source.effectiveAt;
            x.accountId=source.accountId;x.destinationId=source.destinationId;x.routeId=source.routeId;x.personId=source.personId;x.originalId=source.originalId;x.shares=source.shares;
            x.category=source.category;x.paymentMode=source.paymentMode;x.remarks=source.remarks;x.spendingType=source.spendingType;
            x.date=source.date;x.time=source.time;x.deviceName=source.deviceName;x.createdAt=source.createdAt;x.updatedAt=source.updatedAt;x.syncStatus=source.syncStatus;x.deleted=source.deleted;t.add(x);
        }
        return new Ledger(a,r,p,t);
    }
    static long effectiveDate(String date, String time, long fallback) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US); f.setLenient(false);
            String value = date + " " + time;
            Date d = f.parse(value);
            return d != null && f.format(d).equals(value) ? d.getTime() : fallback;
        } catch (Exception ignored) { return fallback; }
    }
    static long amount(TransactionEntity t) { return t.amountMinor < 0 && !"ADJUSTMENT".equals(t.kind) ? Money.legacy(t.amount) : t.amountMinor; }
    static long personal(TransactionEntity t) { return t.personalMinor < 0 ? amount(t) : t.personalMinor; }
    static long at(TransactionEntity t) { return t.effectiveAt > 0 ? t.effectiveAt : effectiveDate(t.date, t.time, t.createdAt); }
    AccountEntity account(String id) { for (AccountEntity a : accounts) if (a.accountId.equals(id)) return a; return null; }
    PersonEntity person(String id) { for (PersonEntity p : people) if (p.personId.equals(id)) return p; return null; }
    TransactionEntity transaction(String id) { for (TransactionEntity t : transactions) if (t.transactionId.equals(id)) return t; return null; }
    String accountName(String id) { AccountEntity a=account(id);if(a==null)return "Needs account";long duplicates=accounts.stream().filter(x->!x.deleted&&x.name.equals(a.name)).count();return a.name+(duplicates>1?" · "+a.type+" · "+a.accountId.substring(0,Math.min(6,a.accountId.length())):""); }
    String personName(String id) { PersonEntity p=person(id);return p==null?"Unknown person":p.name; }
    List<AccountEntity> activeAccounts(boolean allowCard) {
        List<AccountEntity> result=new ArrayList<>();
        for(AccountEntity a:accounts) if(!a.archived&&!a.deleted&&(allowCard||!a.isCard())) result.add(a);
        return result;
    }
    static Map<String, Long> shares(TransactionEntity t) {
        try {
            JSONObject o=new JSONObject(t.shares); Map<String,Long> result=new LinkedHashMap<>();
            Iterator<String> keys=o.keys();
            while(keys.hasNext()) {String key=keys.next();Object v=o.get(key);
                if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).longValue()) throw new IllegalArgumentException("Invalid friend share");
                result.put(key,((Number)v).longValue());}
            return result;
        } catch(Exception e) {throw new IllegalArgumentException("Invalid friend shares",e);}
    }
    static long expense(TransactionEntity t) {
        if(t.deleted)return 0;
        switch(t.kind) {
            case "EXPENSE": case "FRIEND_PAID": return personal(t);
            case "WRITE_OFF_RECEIVABLE": return amount(t);
            case "REFUND": return -personal(t);
            default:return 0;
        }
    }
    static long income(TransactionEntity t) {
        return !t.deleted && ("INCOME".equals(t.kind)||"FORGIVE_PAYABLE".equals(t.kind))?amount(t):0;
    }
    static long effect(TransactionEntity t,String accountId) {
        if(t.deleted)return 0;
        long n=amount(t);
        if("TRANSFER".equals(t.kind))return accountId.equals(t.accountId)?-n:accountId.equals(t.destinationId)?n:0;
        if(!accountId.equals(t.accountId)||accountId.isEmpty())return 0;
        switch(t.kind) {
            case "EXPENSE":case "LEND":case "REPAYMENT_OUT":return -n;
            case "INCOME":case "BORROW":case "REPAYMENT_IN":case "REFUND":case "ADJUSTMENT":return n;
            default:return 0;
        }
    }
    long balance(AccountEntity a) {
        long n=a.openingMinor;
        for(TransactionEntity t:transactions)if(at(t)>=a.openingAt&&at(t)<=System.currentTimeMillis())n=Money.add(n,effect(t,a.accountId));
        return n;
    }
    boolean matches(TransactionEntity t,String accountId) {
        return accountId.isEmpty()||accountId.equals(t.accountId)||accountId.equals(t.destinationId);
    }
    int unassigned() {int n=0;for(TransactionEntity t:transactions)if(!t.deleted&&"EXPENSE".equals(t.kind)&&t.accountId.isEmpty())n++;return n;}
    static final class Owed {
        final TransactionEntity origin;final String personId;final boolean receivable;final long remaining;
        Owed(TransactionEntity t,String p,boolean r,long n){origin=t;personId=p;receivable=r;remaining=n;}
    }
    List<Owed> owed() {
        List<Owed> result=new ArrayList<>();
        for(TransactionEntity t:transactions) {
            if(t.deleted)continue;
            if("EXPENSE".equals(t.kind))for(Map.Entry<String,Long> e:shares(t).entrySet())result.add(new Owed(t,e.getKey(),true,remaining(t,e.getKey(),e.getValue())));
            if("LEND".equals(t.kind)||"BORROW".equals(t.kind)||"FRIEND_PAID".equals(t.kind))
                result.add(new Owed(t,t.personId,"LEND".equals(t.kind),remaining(t,t.personId,amount(t))));
        }
        return result;
    }
    long remaining(TransactionEntity origin,String person,long total) {
        long n=total;
        for(TransactionEntity t:transactions)if(!t.deleted&&origin.transactionId.equals(t.originalId)) {
            if("REFUND".equals(t.kind))n=Money.add(n,-shares(t).getOrDefault(person,0L));
            else if(person.equals(t.personId))n=Money.add(n,-amount(t));
        }
        return n;
    }
    long owedTotal(boolean receive) {long n=0;for(Owed o:owed())if(o.receivable==receive)n=Money.add(n,o.remaining);return n;}

    void validate() {
        Set<String> ids=new HashSet<>();
        for(AccountEntity a:accounts) {
            require(!a.accountId.isEmpty()&&ids.add(a.accountId)&&a.name!=null&&!a.name.trim().isEmpty(),"Invalid or duplicate account");
            require(Arrays.asList("BANK","CASH","WALLET","CARD").contains(a.type),"Invalid account type");
            require(a.openingAt>0&&a.openingAt<=System.currentTimeMillis(),"Invalid starting balance date");Money.add(a.openingMinor,0);
        }
        ids.clear();
        for(PersonEntity p:people)require(!p.personId.isEmpty()&&ids.add(p.personId)&&p.name!=null&&!p.name.trim().isEmpty(),"Invalid or duplicate person");
        ids.clear();
        for(PaymentRouteEntity r:routes) {
            require(!r.routeId.isEmpty()&&ids.add(r.routeId)&&r.label!=null&&!r.label.trim().isEmpty(),"Invalid payment choice");
            require(account(r.accountId)!=null&&(r.deleted||!account(r.accountId).deleted),"Payment choice references a missing account");
        }
        ids.clear();
        for(TransactionEntity t:transactions) {
            require(!t.transactionId.isEmpty()&&ids.add(t.transactionId),"Duplicate transaction ID");
            require(Arrays.asList(KINDS).contains(t.kind),"Unknown transaction type");
            Money.add(amount(t),0);
            if(t.deleted)continue;
            require(amount(t)>0||("ADJUSTMENT".equals(t.kind)&&amount(t)!=0),"Amount must be positive");
            require(at(t)>0&&at(t)<=System.currentTimeMillis(),"Choose a valid date, not in the future");
            boolean noAccount=Arrays.asList("FRIEND_PAID","WRITE_OFF_RECEIVABLE","FORGIVE_PAYABLE").contains(t.kind);
            if(!noAccount&&!("EXPENSE".equals(t.kind)&&t.accountId.isEmpty()))require(account(t.accountId)!=null&&!account(t.accountId).deleted,"Choose an account");
            if(noAccount)require(t.accountId.isEmpty(),"This entry must not move account money");
            if(!t.accountId.isEmpty())require(account(t.accountId)!=null&&!account(t.accountId).deleted,"Account is missing");
            if(!t.routeId.isEmpty()) {
                boolean linked=false;for(PaymentRouteEntity r:routes)if(r.routeId.equals(t.routeId)&&r.accountId.equals(t.accountId))linked=true;
                require(linked,"Payment choice is not linked to this account");
            }
            if(Arrays.asList("INCOME","BORROW","LEND","REPAYMENT_IN","REPAYMENT_OUT","TRANSFER").contains(t.kind))
                require(!account(t.accountId).isCard(),"Use a bank, cash, or wallet account");
            if("TRANSFER".equals(t.kind))require(!t.accountId.equals(t.destinationId)&&account(t.destinationId)!=null&&!account(t.destinationId).deleted,"Choose two different accounts");
            else require(t.destinationId.isEmpty(),"Only transfers can have a destination account");
            Map<String,Long> allocations=shares(t);long sum=0;
            for(Map.Entry<String,Long> e:allocations.entrySet()) {
                require(person(e.getKey())!=null&&!person(e.getKey()).deleted&&e.getValue()>0,"Invalid friend share");sum=Money.add(sum,e.getValue());
            }
            if("EXPENSE".equals(t.kind)||"REFUND".equals(t.kind)) require(personal(t)>=0&&Money.add(personal(t),sum)==amount(t),"Your share + friend shares must equal the total");
            else require(allocations.isEmpty(),"Shares only apply to expenses and refunds");
            if(Arrays.asList("LEND","BORROW","FRIEND_PAID","REPAYMENT_IN","REPAYMENT_OUT","WRITE_OFF_RECEIVABLE","FORGIVE_PAYABLE").contains(t.kind))
                require(person(t.personId)!=null&&!person(t.personId).deleted,"Choose a person");
            if("FRIEND_PAID".equals(t.kind))require(personal(t)==amount(t),"Friend-paid personal expense must equal the amount");
            if(Arrays.asList("REPAYMENT_IN","REPAYMENT_OUT","WRITE_OFF_RECEIVABLE","FORGIVE_PAYABLE","REFUND").contains(t.kind))validateChild(t);
            else require(t.originalId.isEmpty(),"Unexpected linked transaction");
        }
        for(Owed o:owed())require(o.remaining>=0,"Repayments/refunds exceed the outstanding amount. Correct linked entries first.");
        for(AccountEntity a:accounts)if(!a.deleted)balance(a);
    }
    private void validateChild(TransactionEntity child) {
        TransactionEntity origin=transaction(child.originalId);
        require(origin!=null&&!origin.deleted&&!origin.transactionId.equals(child.transactionId),"Linked entry is missing or deleted");
        require(at(child)>=at(origin),"A repayment or refund cannot predate its original entry");
        if("REFUND".equals(child.kind)) {
            require("EXPENSE".equals(origin.kind),"Only an expense can be refunded");
            long own=0;Map<String,Long> refunded=new HashMap<>();
            for(TransactionEntity t:transactions)if(!t.deleted&&"REFUND".equals(t.kind)&&origin.transactionId.equals(t.originalId)) {
                own=Money.add(own,personal(t));for(Map.Entry<String,Long> e:shares(t).entrySet())refunded.put(e.getKey(),Money.add(refunded.getOrDefault(e.getKey(),0L),e.getValue()));
            }
            require(own<=personal(origin),"Refund exceeds your original share");
            for(Map.Entry<String,Long> e:refunded.entrySet())require(e.getValue()<=shares(origin).getOrDefault(e.getKey(),0L),"Refund exceeds a friend's original share");
        } else {
            boolean receive="REPAYMENT_IN".equals(child.kind)||"WRITE_OFF_RECEIVABLE".equals(child.kind);
            boolean expectedReceive="LEND".equals(origin.kind)||("EXPENSE".equals(origin.kind)&&shares(origin).containsKey(child.personId));
            boolean expectedPay=("BORROW".equals(origin.kind)||"FRIEND_PAID".equals(origin.kind))&&origin.personId.equals(child.personId);
            require(receive?expectedReceive:(expectedPay),"Repayment direction or person does not match the original item");
            if("LEND".equals(origin.kind))require(origin.personId.equals(child.personId),"Wrong person for repayment");
        }
    }
    static void require(boolean ok,String message) {if(!ok)throw new IllegalArgumentException(message);}
    static String label(TransactionEntity t) {
        switch(t.kind){case "FRIEND_PAID":return "Friend paid";case "REPAYMENT_IN":return "Repayment received";case "REPAYMENT_OUT":return "Repayment sent";case "WRITE_OFF_RECEIVABLE":return "Debt written off";case "FORGIVE_PAYABLE":return "Debt forgiven";default:return t.kind.substring(0,1)+t.kind.substring(1).toLowerCase(Locale.US);}
    }
}
