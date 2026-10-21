package com.pocketledger.v2;

import org.json.*;
import java.math.*;
import java.util.*;

/** Standalone split calculator. No accounts, income, expense or ledger references. */
final class BillSplit {
    String id=UUID.randomUUID().toString(), title="", currency="₹";
    long updatedAt=System.currentTimeMillis();
    final List<Person> people=new ArrayList<>();
    final List<Item> items=new ArrayList<>();
    final List<Payment> payments=new ArrayList<>();
    String discount="", tax="";
    boolean discountPercent=false, taxPercent=false;
    long extra,roundOff;
    long receiptTotal=-1;
    long finalTotal=-1;
    static String friendKey(String name){String value=name.trim();require(!value.isEmpty()&&value.length()<=80,"Use a friend name between 1 and 80 characters");return value.toLowerCase(Locale.ROOT);}
    static final class Person {
        String id,name;
        long updatedAt;
        Person(String id,String name){this.id=id;this.name=name;}
    }
    static final class Item {
        String name;long amount;
        final Map<String,Long> portions=new LinkedHashMap<>();
        Item(String name,long amount){this.name=name;this.amount=amount;}
    }
    static final class Payment {
        String id,from,to;long amount;
        Payment(String from,String to,long amount){this.id=UUID.randomUUID().toString();this.from=from;this.to=to;this.amount=amount;}
    }
    static final class Transfer {
        final String from,to;final long amount;
        Transfer(String from,String to,long amount){this.from=from;this.to=to;this.amount=amount;}
    }
    static final class Result {
        long subtotal,discount,tax,total,restaurantPaid,restaurantDue;
        long[] food,discounts,taxes,extras,rounding,shares,paid,balances;
        final List<Transfer> transfers=new ArrayList<>();
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    private static long valid(long n){require(n>=0&&n<=Money.MAX,"Amount is out of range");return n;}
    private static long charge(String text,boolean percent,long subtotal){
        if(text.trim().isEmpty())return 0;
        if(!percent)return valid(Money.parse(text));
        try{
            BigDecimal rate=new BigDecimal(text.trim());
            require(rate.signum()>=0&&rate.compareTo(new BigDecimal("100"))<=0&&rate.scale()<=4,"Use a percentage from 0 to 100 (up to four decimal places)");
            return valid(BigDecimal.valueOf(subtotal).multiply(rate).divide(new BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact());
        }catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException("Enter a valid percentage");}
    }
    static long taxAtRate(String rate,long subtotal){return charge(rate,true,subtotal);}
    // Largest remainder, stable participant order on ties, without overflowing long products.
    static long[] allocate(long amount,long[] weights){
        valid(amount);BigInteger denominator=BigInteger.ZERO;
        for(long w:weights){require(w>=0,"Portions cannot be negative");denominator=denominator.add(BigInteger.valueOf(w));}
        long[] result=new long[weights.length];
        if(denominator.signum()==0){require(amount==0,"Choose at least one person for each item");return result;}
        BigInteger[] remainder=new BigInteger[weights.length];long assigned=0;
        for(int i=0;i<weights.length;i++){
            BigInteger[] qr=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(weights[i])).divideAndRemainder(denominator);
            // Quotient is between zero and the already bounded amount (also safe before API 31).
            result[i]=qr[0].longValue();remainder[i]=qr[1];assigned+=result[i];
        }
        Integer[] order=new Integer[weights.length];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,(a,b)->remainder[b].compareTo(remainder[a]));
        for(int i=0;i<amount-assigned;i++)result[order[i]]++;
        return result;
    }
    Result calculate(){
        require(!title.trim().isEmpty(),"Give this bill a name");require(people.size()>=2&&people.size()<=30,"Add between 2 and 30 people");
        Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Person p:people){require(!p.id.isEmpty()&&ids.add(p.id),"Duplicate person ID");require(!p.name.trim().isEmpty()&&names.add(p.name.trim().toLowerCase(Locale.ROOT)),"Use a different name for each person");}
        require(!items.isEmpty(),"Add at least one bill item");
        Result r=new Result();int n=people.size();r.food=new long[n];
        for(Item item:items){
            require(!item.name.trim().isEmpty()&&valid(item.amount)>0,"Items need a name and a positive line total");
            for(String person:item.portions.keySet())require(ids.contains(person),"An item refers to a removed person");
            long[] weights=new long[n];for(int i=0;i<n;i++)weights[i]=item.portions.getOrDefault(people.get(i).id,0L);
            long[] shares=allocate(item.amount,weights);r.subtotal=Money.add(r.subtotal,item.amount);
            for(int i=0;i<n;i++)r.food[i]=Money.add(r.food[i],shares[i]);
        }
        if(finalTotal>=0){
            r.tax=charge(tax,taxPercent,r.subtotal);long gross=Money.add(r.subtotal,r.tax);valid(finalTotal);
            require(finalTotal<=gross,"Final bill cannot exceed the items plus tax. Check the item prices, tax or final amount.");
            r.total=finalTotal;r.discount=gross-finalTotal;r.taxes=allocate(r.tax,r.food);r.extras=new long[n];r.rounding=new long[n];
            long[] beforeDiscount=new long[n];for(int i=0;i<n;i++)beforeDiscount[i]=Money.add(r.food[i],r.taxes[i]);
            r.discounts=allocate(r.discount,beforeDiscount);r.shares=new long[n];r.paid=new long[n];r.balances=new long[n];
            for(int i=0;i<n;i++)r.shares[i]=beforeDiscount[i]-r.discounts[i];
        }else{
        r.discount=charge(discount,discountPercent,r.subtotal);require(r.discount<=r.subtotal,"Discount cannot exceed the food subtotal");
        // Tax percentage deliberately uses the original subtotal, as selected for this tool.
        r.tax=charge(tax,taxPercent,r.subtotal);valid(extra);
        r.total=Money.add(Money.add(r.subtotal-r.discount,r.tax),extra);
        r.discounts=allocate(r.discount,r.food);r.taxes=allocate(r.tax,r.food);r.extras=allocate(extra,r.food);
        r.shares=new long[n];r.paid=new long[n];r.balances=new long[n];
        for(int i=0;i<n;i++)r.shares[i]=Money.add(Money.add(r.food[i]-r.discounts[i],r.taxes[i]),r.extras[i]);
        require(roundOff>=-100&&roundOff<=100,"Round-off must be between -1.00 and 1.00");
        require(r.total+roundOff>=0,"Round-off cannot make the bill negative");
        r.rounding=allocate(Math.abs(roundOff),r.total==0?r.food:r.shares);for(int i=0;i<n;i++){if(roundOff<0)r.rounding[i]=-r.rounding[i];r.shares[i]+=r.rounding[i];}r.total=Money.add(r.total,roundOff);
        }
        Set<String> paymentIds=new HashSet<>();
        for(Payment p:payments){
            require(!p.id.isEmpty()&&paymentIds.add(p.id),"Duplicate payment ID");
            require(ids.contains(p.from)&&(p.to.isEmpty()||ids.contains(p.to))&&!p.from.equals(p.to),"Choose valid payment participants");
            require(valid(p.amount)>0,"Payment must be greater than zero");int from=index(p.from);
            if(p.to.isEmpty()){r.restaurantPaid=Money.add(r.restaurantPaid,p.amount);r.paid[from]=Money.add(r.paid[from],p.amount);}
            r.balances[from]=Money.add(r.balances[from],p.amount);
            if(!p.to.isEmpty()){int to=index(p.to);r.balances[to]=Money.add(r.balances[to],-p.amount);}
        }
        require(r.restaurantPaid<=r.total,"Restaurant payments exceed the final bill; correct the payments first");
        r.restaurantDue=r.total-r.restaurantPaid;
        for(int i=0;i<n;i++)r.balances[i]=Money.add(r.balances[i],-r.shares[i]);
        if(r.restaurantDue==0){
            long[] remaining=r.balances.clone();
            for(int debtor=0;debtor<n;debtor++)if(remaining[debtor]<0)
                for(int creditor=0;creditor<n&&remaining[debtor]<0;creditor++)if(remaining[creditor]>0){
                    long amount=Math.min(-remaining[debtor],remaining[creditor]);r.transfers.add(new Transfer(people.get(debtor).id,people.get(creditor).id,amount));
                    remaining[debtor]+=amount;remaining[creditor]-=amount;
                }
        }
        return r;
    }
    int index(String id){for(int i=0;i<people.size();i++)if(people.get(i).id.equals(id))return i;throw new IllegalArgumentException("Unknown person");}
    String name(String id){return id.isEmpty()?"Restaurant":people.get(index(id)).name;}
    boolean hasSettlements(){for(Payment p:payments)if(!p.to.isEmpty())return true;return false;}
    JSONObject json() throws JSONException {
        JSONObject o=new JSONObject().put("id",id).put("title",title).put("currency",currency).put("updatedAt",updatedAt)
                .put("discount",discount).put("discountPercent",discountPercent).put("tax",tax).put("taxPercent",taxPercent).put("extra",extra).put("roundOff",roundOff).put("receiptTotal",receiptTotal).put("finalTotal",finalTotal);
        JSONArray persons=new JSONArray(),lines=new JSONArray(),paid=new JSONArray();
        for(Person p:people)persons.put(new JSONObject().put("id",p.id).put("name",p.name));
        for(Item item:items){JSONObject portions=new JSONObject();for(Map.Entry<String,Long> e:item.portions.entrySet())portions.put(e.getKey(),e.getValue());lines.put(new JSONObject().put("name",item.name).put("amount",item.amount).put("portions",portions));}
        for(Payment p:payments)paid.put(new JSONObject().put("id",p.id).put("from",p.from).put("to",p.to).put("amount",p.amount));
        return o.put("people",persons).put("items",lines).put("payments",paid);
    }
    static BillSplit read(JSONObject o) throws JSONException {
        BillSplit b=new BillSplit();b.id=o.getString("id");require(!b.id.isEmpty(),"Missing bill ID");b.title=o.getString("title");b.currency=o.getString("currency");b.updatedAt=o.getLong("updatedAt");
        b.discount=o.getString("discount");b.tax=o.getString("tax");b.discountPercent=o.getBoolean("discountPercent");b.taxPercent=o.getBoolean("taxPercent");b.extra=o.getLong("extra");b.roundOff=o.optLong("roundOff",0);b.receiptTotal=o.optLong("receiptTotal",-1);b.finalTotal=o.optLong("finalTotal",-1);
        JSONArray a=o.getJSONArray("people");for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);b.people.add(new Person(p.getString("id"),p.getString("name")));}
        a=o.getJSONArray("items");for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);Item item=new Item(x.getString("name"),x.getLong("amount"));JSONObject portions=x.getJSONObject("portions");Iterator<String> keys=portions.keys();while(keys.hasNext()){String key=keys.next();item.portions.put(key,portions.getLong(key));}b.items.add(item);}
        a=o.getJSONArray("payments");for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);Payment pay=new Payment(p.getString("from"),p.getString("to"),p.getLong("amount"));pay.id=p.getString("id");b.payments.add(pay);}
        return b;
    }
}
