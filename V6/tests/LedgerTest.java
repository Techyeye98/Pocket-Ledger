package com.pocketledger.v2;

import java.util.*;
import org.json.JSONObject;

public final class LedgerTest {
    private static final long NOW=System.currentTimeMillis()-60_000;
    static void eq(long a,long b,String message){if(a!=b)throw new AssertionError(message+": "+a+" != "+b);}
    static void bad(Runnable r,String message){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection: "+message);}
    static AccountEntity account(String id,String type,long opening){AccountEntity a=new AccountEntity();a.accountId=id;a.name=id;a.type=type;a.openingMinor=opening;a.openingAt=NOW-10_000;a.updatedAt=NOW;return a;}
    static TransactionEntity tx(String id,String kind,long amount,String account){TransactionEntity t=new TransactionEntity();t.transactionId=id;t.kind=kind;t.amountMinor=amount;t.personalMinor=kind.equals("EXPENSE")||kind.equals("FRIEND_PAID")?amount:0;t.amount=amount/100.0;t.accountId=account;t.effectiveAt=NOW;t.createdAt=NOW;t.updatedAt=NOW;t.date="15-10-2026";t.time="12:00:00";return t;}
    static Ledger base(){PersonEntity p=new PersonEntity();p.personId="rahul";p.name="Rahul";p.updatedAt=NOW;return new Ledger(new ArrayList<>(Arrays.asList(account("sbi","BANK",1_000_000),account("cash","CASH",100_000),account("card","CARD",-200_000))),new ArrayList<>(),new ArrayList<>(Arrays.asList(p)),new ArrayList<>());}
    static void add(Ledger l,TransactionEntity t){l.transactions.add(t);l.validate();}
    public static void main(String[] args) throws Exception {
        UiRevision loads=new UiRevision();int firstHome=loads.next(),activity=loads.next(),latestHome=loads.next();
        Ledger.require(!loads.current(firstHome)&&!loads.current(activity)&&loads.current(latestHome),"Only the latest rapid navigation load may update the UI");
        loads.next();Ledger.require(!loads.current(latestHome),"Destroyed screens reject delayed loads");
        eq(Money.add(Money.parse("0.10"),Money.parse("0.20")),30,"Exact decimal addition");
        eq(Money.legacy(1.005),101,"Legacy half-up conversion");bad(()->Money.parse("1.001"),"More than two decimals");bad(()->Money.add(Money.MAX,1),"Overflow");
        Ledger l=base();l.validate();
        add(l,tx("salary","INCOME",2_000_000,"sbi"));
        TransactionEntity atm=tx("atm","TRANSFER",100_000,"sbi");atm.destinationId="cash";add(l,atm);
        TransactionEntity dinner=tx("dinner","EXPENSE",120_000,"sbi");dinner.personalMinor=40_000;dinner.shares="{\"rahul\":80000}";add(l,dinner);
        eq(l.owedTotal(true),80_000,"Friend share receivable");eq(Ledger.expense(dinner),40_000,"Own expense only");
        TransactionEntity repayment=tx("repay","REPAYMENT_IN",80_000,"cash");repayment.personId="rahul";repayment.originalId="dinner";add(l,repayment);eq(l.owedTotal(true),0,"Repaid debt");eq(Ledger.income(repayment),0,"Repayment is not income");
        add(l,tx("purchase","EXPENSE",50_000,"card"));
        TransactionEntity cardPayment=tx("card-pay","TRANSFER",200_000,"sbi");cardPayment.destinationId="card";add(l,cardPayment);
        eq(l.balance(l.account("sbi")),2_580_000,"Final SBI");eq(l.balance(l.account("cash")),280_000,"Final cash");eq(l.balance(l.account("card")),-50_000,"Final card debt");
        eq(Money.add(Money.add(l.balance(l.account("sbi")),l.balance(l.account("cash"))),l.balance(l.account("card"))),2_810_000,"Final net");
        Ledger roundTrip=LedgerJson.read(LedgerJson.write(l));eq(roundTrip.balance(roundTrip.account("sbi")),2_580_000,"Wire round trip");
        Ledger cleared=l.clearedTransactions(System.currentTimeMillis());
        Ledger.require(cleared.transactions.stream().allMatch(t->t.deleted&&t.amountMinor==0&&t.personalMinor==0&&t.remarks.isEmpty()&&t.accountId.isEmpty()&&t.date.isEmpty()),"Clear removes transaction contents and retains deletion identities");
        eq(cleared.accounts.size(),l.accounts.size(),"Clear retains accounts");eq(cleared.owedTotal(true),0,"Clear removes linked dues together");
        eq(cleared.balance(cleared.account("sbi")),cleared.account("sbi").openingMinor,"Clear returns balances to opening amounts");
        Ledger.require(l.transactions.stream().noneMatch(t->t.deleted),"Reset preparation does not mutate the original ledger");
        Ledger.require(LedgerJson.merge(cleared,roundTrip).transactions.stream().allMatch(t->t.deleted),"Old backups cannot resurrect cleared entries");
        Ledger.require(LedgerJson.read(LedgerJson.write(cleared)).transactions.stream().allMatch(t->t.deleted),"Cleared history round-trips through sync");
        Ledger display=l.presentationCopy();eq(display.balance(display.account("sbi")),l.balance(l.account("sbi")),"Warm screen snapshot preserves balances");
        display.account("sbi").name="Changed only in the screen";display.transaction("salary").amountMinor=1;
        Ledger.require(!l.account("sbi").name.equals(display.account("sbi").name),"Screen account edits cannot mutate the retained snapshot");
        eq(Ledger.amount(l.transaction("salary")),2_000_000,"Screen transactions are independent copies");
        eq(LedgerJson.merge(l,roundTrip).transactions.size(),l.transactions.size(),"Idempotent import");
        JSONObject backup=LedgerJson.write(l).put("format","pocket-ledger-backup").put("version",3);
        Ledger.require(BackupManager.validateBackup(backup.toString()).success,"New backup validates its complete graph");
        JSONObject oldBackup=new JSONObject().put("format","pocket-ledger-backup").put("version",2).put("transactions",new org.json.JSONArray().put(new JSONObject().put("transactionId","legacy").put("amount",1.005).put("createdAt",NOW).put("updatedAt",NOW).put("date","15-10-2026").put("time","12:00:00")));
        BackupManager.ValidationResult legacy=BackupManager.validateBackup(oldBackup.toString());
        Ledger.require(legacy.success,"Legacy backup remains readable");eq(legacy.transactions.get(0).amountMinor,101,"Legacy backup conversion");
        Ledger duplicate=base();duplicate.transactions.add(tx("same","EXPENSE",100,"sbi"));duplicate.transactions.add(tx("same","EXPENSE",100,"sbi"));bad(duplicate::validate,"Duplicate transaction identity");
        repayment.amountMinor=80_001;bad(l::validate,"Over repayment");repayment.amountMinor=80_000;
        dinner.deleted=true;bad(l::validate,"Deleted parent");dinner.deleted=false;
        repayment.effectiveAt=NOW-1;bad(l::validate,"Repayment before origin");repayment.effectiveAt=NOW;
        dinner.personalMinor=39_999;bad(l::validate,"Split mismatch");dinner.personalMinor=40_000;
        TransactionEntity refund=tx("refund","REFUND",10_000,"sbi");refund.personalMinor=10_000;refund.originalId="dinner";add(l,refund);eq(Ledger.expense(refund),-10_000,"Refund reverses spending");
        refund.personalMinor=refund.amountMinor=40_001;bad(l::validate,"Over refund");refund.personalMinor=refund.amountMinor=10_000;
        TransactionEntity sharedRefund=tx("friend-refund","REFUND",100,"sbi");sharedRefund.originalId="dinner";sharedRefund.personalMinor=0;sharedRefund.shares="{\"rahul\":100}";l.transactions.add(sharedRefund);bad(l::validate,"Refund cannot erase money already settled");l.transactions.remove(sharedRefund);
        Ledger older=base();TransactionEntity historic=tx("historic","EXPENSE",10_000,"sbi");historic.effectiveAt=NOW-20_000;add(older,historic);eq(older.balance(older.account("sbi")),1_000_000,"Opening cutoff prevents double deduction");eq(Ledger.expense(historic),10_000,"Historical report retained");
        TransactionEntity boundary=tx("boundary","EXPENSE",100,"sbi");boundary.effectiveAt=older.account("sbi").openingAt;add(older,boundary);eq(older.balance(older.account("sbi")),999_900,"Opening boundary is inclusive");
        Ledger debt=base();TransactionEntity lend=tx("lend","LEND",200_000,"sbi");lend.personId="rahul";add(debt,lend);eq(Ledger.expense(lend),0,"Lending not spending");
        TransactionEntity partial=tx("partial","REPAYMENT_IN",50_000,"cash");partial.personId="rahul";partial.originalId="lend";add(debt,partial);eq(debt.owedTotal(true),150_000,"Partial repayment");
        TransactionEntity loss=tx("loss","WRITE_OFF_RECEIVABLE",150_000,"");loss.originalId="lend";loss.personId="rahul";add(debt,loss);eq(debt.owedTotal(true),0,"Write-off closes debt");eq(Ledger.expense(loss),150_000,"Write-off expense without cash movement");
        TransactionEntity borrow=tx("borrow","BORROW",100_000,"sbi");borrow.personId="rahul";add(debt,borrow);eq(Ledger.income(borrow),0,"Borrowing not income");
        TransactionEntity forgive=tx("forgiven","FORGIVE_PAYABLE",100_000,"");forgive.personId="rahul";forgive.originalId="borrow";add(debt,forgive);eq(debt.owedTotal(false),0,"Forgiveness closes payable");
        TransactionEntity fp=tx("friendpaid","FRIEND_PAID",60_000,"");fp.personId="rahul";add(debt,fp);eq(Ledger.expense(fp),60_000,"Friend-paid expense");eq(debt.owedTotal(false),60_000,"Friend-paid payable");
        System.out.println("PASS: exact money, full scenario, balance cutoffs, wire round-trip, idempotent merge, split/settlement/refund guards, lending, borrowing, write-offs and friend-paid spending.");
    }
}
