package com.pocketledger.v2;

import java.util.*;

final class PaymentChoice {
    final String id,label,accountId,routeId;
    PaymentChoice(String id,String label,String accountId,String routeId){this.id=id;this.label=label;this.accountId=accountId;this.routeId=routeId;}
    static List<PaymentChoice> list(Ledger l,boolean cards) {
        List<PaymentChoice> result=new ArrayList<>();
        for(AccountEntity a:l.activeAccounts(cards))result.add(new PaymentChoice("a:"+a.accountId,l.accountName(a.accountId),a.accountId,""));
        for(PaymentRouteEntity r:l.routes){AccountEntity a=l.account(r.accountId);if(!r.deleted&&a!=null&&!a.deleted&&!a.archived&&(cards||!a.isCard()))result.add(new PaymentChoice("r:"+r.routeId,r.label+" · "+l.accountName(a.accountId),a.accountId,r.routeId));}
        return result;
    }
    @Override public String toString(){return label;}
}
