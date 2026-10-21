package com.pocketledger.v2;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;

final class LedgerHome {
    static void attach(Activity a,LinearLayout root,Ledger ledger,long from,long to,String period,Runnable refresh) {
        ConfigStore config=new ConfigStore(a);
        LinearLayout toolbar=new LinearLayout(a);toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(Design.dp(a,20),0,Design.dp(a,20),Design.dp(a,8));root.addView(toolbar,1);
        try{
            String saved=a.getSharedPreferences("ledger_ui",0).getString("selectedAccount","");
            if(ledger.account(saved)==null||ledger.account(saved).deleted){saved="";a.getSharedPreferences("ledger_ui",0).edit().putString("selectedAccount","").apply();}
            final String selected=saved;
            List<AccountEntity> accounts=new ArrayList<>();for(AccountEntity account:ledger.accounts)if(!account.deleted)accounts.add(account);
            long available=0,cardTotal=0,dues=0,credit=0,income=0,expense=0;
            for(AccountEntity account:accounts){long n=ledger.balance(account);if(account.isCard()){cardTotal=Money.add(cardTotal,n);if(n<0)dues=Money.add(dues,-n);else credit=Money.add(credit,n);}else available=Money.add(available,n);}
            for(TransactionEntity tx:ledger.transactions)if(Ledger.at(tx)>=from&&Ledger.at(tx)<=to&&ledger.matches(tx,selected)){income=Money.add(income,Ledger.income(tx));expense=Money.add(expense,Ledger.expense(tx));}
            final long cash=available,net=Money.add(available,cardTotal),due=dues,cardCredit=credit,earned=income,spent=expense;
                if(a.isFinishing())return;
                LinearLayout statement=root.findViewWithTag("spending_statement");
                if(statement==null)return;
                if(!accounts.isEmpty()){
                    List<String> names=new ArrayList<>();names.add("Overall · All accounts");int index=0;
                    for(int i=0;i<accounts.size();i++){names.add(ledger.accountName(accounts.get(i).accountId));if(accounts.get(i).accountId.equals(selected))index=i+1;}
                    LedgerChoice picker=new LedgerChoice(a,"Account view",names,index);toolbar.addView(picker,new LinearLayout.LayoutParams(-1,-2));
                    picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View v,int position,long id){a.getSharedPreferences("ledger_ui",0).edit().putString("selectedAccount",position==0?"":accounts.get(position-1).accountId).apply();refresh.run();}});
                    AccountEntity chosen=ledger.account(selected);long balance=chosen==null?net:ledger.balance(chosen);
                    TextView label=root.findViewWithTag("statement_label");
                    label.setText(chosen==null?"Current net balance":chosen.isCard()?(balance<0?"Amount owed":"Card credit"):"Current balance");
                    if(ledger.unassigned()>0)label.setText(label.getText()+" · Incomplete");
                    TextView hero=root.findViewWithTag("balance_hero");hero.setTag("current_balance_hero");
                    hero.setText(config.currencySymbol()+Money.decimal(chosen!=null&&chosen.isCard()?Math.abs(balance):balance));
                    LinearLayout facts=root.findViewWithTag("statement_facts");facts.removeAllViews();
                    int ink=android.graphics.Color.parseColor("#F3FCF5"),soft=android.graphics.Color.parseColor("#BCE1C9");
                    fact(a,facts,chosen==null?"Available money":"Balance starts",chosen==null?config.currencySymbol()+Money.decimal(cash):new java.text.SimpleDateFormat("dd MMM yyyy",Locale.US).format(new Date(chosen.openingAt)),ink,soft);
                    fact(a,facts,chosen==null?"Card dues":chosen.isCard()?"Type":"Account",chosen==null?config.currencySymbol()+Money.decimal(due):LedgerUi.label(chosen.type),ink,soft);
                    if(cardCredit>0&&chosen==null){TextView value=Components.text(a,"Card credit "+config.currencySymbol()+Money.decimal(cardCredit),12,soft,Typeface.NORMAL);value.setPadding(0,Design.dp(a,12),0,0);statement.addView(value);}
                    statement.setContentDescription(label.getText()+" "+hero.getText()+". Tap for balance information.");statement.setFocusable(true);
                    statement.setOnClickListener(v->LedgerUi.dialog(a).setTitle("About your balance").setMessage("Current balances carry across months. Period filters change income and spending summaries. Net balance subtracts card dues and excludes money owed between people.").setPositiveButton("Got it",null).show());
                }else toolbar.setVisibility(View.GONE);

                LinearLayout links=new LinearLayout(a);links.setPadding(Design.dp(a,20),0,Design.dp(a,20),Design.dp(a,12));
                Button accountsButton=Components.secondaryButton(a,accounts.isEmpty()?"Set up accounts":"Accounts");
                accountsButton.setOnClickListener(v->a.startActivity(LedgerActivity.intent(a,"accounts")));
                LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMarginEnd(Design.dp(a,8));links.addView(accountsButton,left);
                Button people=Components.secondaryButton(a,"Lena / Dena");people.setOnClickListener(v->a.startActivity(LedgerActivity.intent(a,"people")));links.addView(people,new LinearLayout.LayoutParams(0,-2,1));
                root.addView(links,root.indexOfChild(statement)+1);

                LinearLayout afterPeriod=new LinearLayout(a);afterPeriod.setOrientation(LinearLayout.VERTICAL);afterPeriod.setPadding(Design.dp(a,20),Design.dp(a,12),Design.dp(a,20),Design.dp(a,6));
                if(!accounts.isEmpty()){
                    LinearLayout flow=new LinearLayout(a);
                    flow.addView(LedgerUi.fact(a,"Income",config.currencySymbol()+Money.decimal(earned),Design.text(a)),new LinearLayout.LayoutParams(0,-2,1));
                    flow.addView(LedgerUi.fact(a,"Expenses",config.currencySymbol()+Money.decimal(spent),Design.text(a)),new LinearLayout.LayoutParams(0,-2,1));
                    afterPeriod.addView(flow);
                    TextView change=Components.text(a,"Net change  "+config.currencySymbol()+Money.decimal(earned-spent),13,Design.textSoft(a),Typeface.NORMAL);change.setPadding(0,Design.dp(a,12),0,Design.dp(a,14));afterPeriod.addView(change);
                    afterPeriod.addView(Components.hairline(a));
                }
                long receive=ledger.owedTotal(true),pay=ledger.owedTotal(false);
                if(receive>0||pay>0){Button owed=Components.textButton(a,"To receive "+config.currencySymbol()+Money.decimal(receive)+"  ·  To pay "+config.currencySymbol()+Money.decimal(pay));owed.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);owed.setTextColor(Design.primary(a));owed.setPadding(0,Design.dp(a,10),0,Design.dp(a,8));owed.setOnClickListener(v->a.startActivity(LedgerActivity.intent(a,"people")));afterPeriod.addView(owed);}
                if(ledger.unassigned()>0){
                    Button setup=Components.textButton(a,ledger.unassigned()+" expenses need an account · Connect");setup.setTextSize(13);setup.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);setup.setTextColor(Design.primary(a));setup.setPadding(0,Design.dp(a,8),0,Design.dp(a,8));setup.setOnClickListener(v->a.startActivity(LedgerActivity.intent(a,"accounts")));afterPeriod.addView(setup);
                }
                long rounded=ledger.transactions.stream().filter(tx->Math.abs(tx.amount-Ledger.amount(tx)/100.0)>0.000001).count();
                if(rounded>0){Button rounding=Components.textButton(a,rounded+" older amounts rounded · Details");rounding.setTextSize(13);rounding.setTextColor(Design.textSoft(a));rounding.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);rounding.setOnClickListener(v->LedgerUi.dialog(a).setTitle("Older amounts").setMessage("Balances use two decimal places. Older amounts with extra decimals were rounded half up; the original imported values remain preserved.").setPositiveButton("Got it",null).show());afterPeriod.addView(rounding);}
                View periodView=root.findViewWithTag("home_period_selector");root.addView(afterPeriod,periodView==null?root.indexOfChild(links)+1:root.indexOfChild(periodView)+1);
        }catch(Exception e){Button retry=Components.textButton(a,"Reload account balances");retry.setOnClickListener(v->refresh.run());toolbar.addView(retry);}
    }
    private static void fact(Activity a,LinearLayout parent,String label,String amount,int ink,int soft){
        LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);
        column.addView(Components.text(a,label,12,soft,Typeface.NORMAL));TextView value=Components.text(a,amount,18,ink,Typeface.BOLD);value.setPadding(0,Design.dp(a,6),Design.dp(a,8),0);column.addView(value);parent.addView(column,new LinearLayout.LayoutParams(0,-2,1));
    }
    static boolean matches(Context c,TransactionEntity t) {
        String account=c.getSharedPreferences("ledger_ui",0).getString("selectedAccount","");String person=c.getSharedPreferences("ledger_ui",0).getString("personFilter","");
        return (account.isEmpty()||account.equals(t.accountId)||account.equals(t.destinationId))&&(person.isEmpty()||person.equals(t.personId)||Ledger.shares(t).containsKey(person));
    }
    static void filters(Activity a,LinearLayout root,Ledger ledger,Runnable refresh) {

            LinearLayout fields=new LinearLayout(a);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(Design.dp(a,20),0,Design.dp(a,20),Design.dp(a,12));root.addView(fields);
            List<String> names=new ArrayList<>(),ids=new ArrayList<>();names.add("All accounts");ids.add("");
            for(AccountEntity account:ledger.accounts)if(!account.deleted){names.add(ledger.accountName(account.accountId));ids.add(account.accountId);}
            String saved=a.getSharedPreferences("ledger_ui",0).getString("selectedAccount","");
            LedgerChoice account=new LedgerChoice(a,"Filter by account",names,Math.max(0,ids.indexOf(saved)));fields.addView(account);account.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){a.getSharedPreferences("ledger_ui",0).edit().putString("selectedAccount",ids.get(position)).apply();refresh.run();}});
            List<String> people=new ArrayList<>(),personIds=new ArrayList<>();people.add("All people");personIds.add("");for(PersonEntity person:ledger.people)if(!person.deleted){people.add(person.name);personIds.add(person.personId);}
            LedgerChoice person=new LedgerChoice(a,"Filter by person",people,Math.max(0,personIds.indexOf(a.getSharedPreferences("ledger_ui",0).getString("personFilter",""))));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=Design.dp(a,10);fields.addView(person,params);
            person.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){a.getSharedPreferences("ledger_ui",0).edit().putString("personFilter",personIds.get(position)).apply();refresh.run();}});

    }
}
