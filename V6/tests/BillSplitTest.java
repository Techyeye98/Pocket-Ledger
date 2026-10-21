package com.pocketledger.v2;

import java.util.*;

public final class BillSplitTest {
    private static void equal(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
    private static void rejected(Runnable work){try{work.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected validation failure");}
    private static BillSplit dinner(){
        BillSplit b=new BillSplit();b.title="Dinner";
        b.people.add(new BillSplit.Person("you","You"));b.people.add(new BillSplit.Person("rahul","Rahul"));b.people.add(new BillSplit.Person("neha","Neha"));
        BillSplit.Item pizza=new BillSplit.Item("Pizza",60000);pizza.portions.put("you",1L);pizza.portions.put("rahul",1L);b.items.add(pizza);
        BillSplit.Item pasta=new BillSplit.Item("Pasta",40000);pasta.portions.put("neha",1L);b.items.add(pasta);
        b.discount="10";b.discountPercent=true;b.tax="5";b.taxPercent=true;return b;
    }
    public static void main(String[] args) throws Exception {
        BillSplit b=dinner();BillSplit.Result r=b.calculate();
        equal(r.subtotal,100000);equal(r.tax,5000);equal(r.discount,10000);equal(r.total,95000);
        equal(r.shares[0],28500);equal(r.shares[1],28500);equal(r.shares[2],38000);
        b.payments.add(new BillSplit.Payment("you","",30000));b.payments.add(new BillSplit.Payment("rahul","",8000));
        r=b.calculate();equal(r.restaurantPaid,38000);equal(r.restaurantDue,57000);equal(r.transfers.size(),0);
        // Friend advances never reduce restaurant dues.
        b.payments.add(new BillSplit.Payment("neha","you",10000));r=b.calculate();equal(r.restaurantDue,57000);
        b.payments.add(new BillSplit.Payment("you","",57000));r=b.calculate();
        equal(r.restaurantDue,0);equal(r.balances[0],48500);equal(r.balances[1],-20500);equal(r.balances[2],-28000);
        for(BillSplit.Transfer t:r.transfers)b.payments.add(new BillSplit.Payment(t.from,t.to,t.amount));
        r=b.calculate();for(long n:r.balances)equal(n,0);equal(r.transfers.size(),0);
        BillSplit copy=BillSplit.read(b.json());equal(copy.calculate().total,95000);equal(copy.payments.size(),b.payments.size());
        copy.items.get(0).amount=90000;equal(b.items.get(0).amount,60000);
        BillSplit multiple=dinner();multiple.payments.add(new BillSplit.Payment("you","",60000));multiple.payments.add(new BillSplit.Payment("rahul","",35000));
        r=multiple.calculate();equal(r.balances[0],31500);equal(r.balances[1],6500);equal(r.balances[2],-38000);equal(r.transfers.size(),2);
        equal(BillSplit.allocate(10000,new long[]{1,1,1})[0],3334);
        long[] weighted=BillSplit.allocate(9000,new long[]{0,50,100});equal(weighted[0],0);equal(weighted[1],3000);equal(weighted[2],6000);
        BillSplit exact=dinner();exact.taxPercent=false;exact.tax="37.61";equal(exact.calculate().tax,3761);
        long[] large=BillSplit.allocate(Money.MAX,new long[]{Money.MAX,Money.MAX});equal(large[0]+large[1],Money.MAX);
        rejected(()->BillSplit.allocate(1,new long[]{0,0}));rejected(()->BillSplit.allocate(1,new long[]{-1,1}));
        BillSplit invalid=dinner();invalid.discount="101";rejected(invalid::calculate);invalid.discount="0";invalid.tax="oops";rejected(invalid::calculate);
        invalid.tax="0";invalid.payments.add(new BillSplit.Payment("you","",100001));rejected(invalid::calculate);
        invalid.payments.clear();invalid.items.get(0).portions.clear();rejected(invalid::calculate);
        BillSplit zero=dinner();zero.discount="100";zero.tax="";equal(zero.calculate().total,0);
        BillSplit round=dinner();round.roundOff=-1;round.receiptTotal=94999;r=round.calculate();equal(r.total,94999);equal(r.shares[0]+r.shares[1]+r.shares[2],94999);equal(BillSplit.read(round.json()).receiptTotal,94999);equal(BillSplit.read(round.json()).roundOff,-1);
        BillSplit finalBill=dinner();finalBill.finalTotal=95000;r=finalBill.calculate();equal(r.discount,10000);equal(r.tax,5000);equal(r.total,95000);equal(r.shares[0],28500);equal(r.shares[1],28500);equal(r.shares[2],38000);
        equal(BillSplit.taxAtRate("0",100000),0);equal(BillSplit.taxAtRate("5",100000),5000);equal(BillSplit.taxAtRate("2.5",100000),2500);equal(BillSplit.taxAtRate("5.125",100000),5125);
        rejected(()->BillSplit.taxAtRate("101",100000));rejected(()->BillSplit.taxAtRate("-1",100000));
        equal(BillSplit.read(finalBill.json()).finalTotal,95000);
        finalBill.finalTotal=0;r=finalBill.calculate();equal(r.discount,105000);for(long n:r.shares)equal(n,0);
        finalBill.finalTotal=105001;rejected(finalBill::calculate);
        org.json.JSONObject legacy=dinner().json();legacy.remove("finalTotal");equal(BillSplit.read(legacy).calculate().total,95000);
        if(!BillSplit.friendKey("  Rahul  ").equals(BillSplit.friendKey("RAHUL")))throw new AssertionError("Saved friend normalization");
        rejected(()->BillSplit.friendKey("  "));rejected(()->BillSplit.friendKey(new String(new char[81]).replace('\0','x')));
        Random random=new Random(47);
        for(int pass=0;pass<200;pass++){
            BillSplit x=dinner();x.items.get(0).amount=1+random.nextInt(99999);x.items.get(1).amount=1+random.nextInt(99999);
            x.extra=random.nextInt(500);x.discount=String.valueOf(random.nextInt(101));x.tax=String.valueOf(random.nextInt(25));
            BillSplit.Result a=x.calculate();long total=0;for(long n:a.shares){if(n<0)throw new AssertionError("Negative share");total+=n;}equal(total,a.total);
            x.finalTotal=random.nextInt((int)(a.subtotal+a.tax+1));a=x.calculate();total=0;long discount=0;for(int i=0;i<a.shares.length;i++){if(a.shares[i]<0||a.discounts[i]<0)throw new AssertionError("Negative final amount allocation");total+=a.shares[i];discount+=a.discounts[i];}equal(total,x.finalTotal);equal(discount,a.subtotal+a.tax-x.finalTotal);
        }
        System.out.println("BillSplitTest PASS: original-subtotal tax, exact shares, partial/multiple payers, advances, settlement, JSON and rounding invariants");
    }
}
