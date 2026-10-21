package com.pocketledger.v2;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

/** Account and people management, and advanced entries; widget stays expense-first. */
public final class LedgerActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService syncExecutor=Executors.newSingleThreadExecutor();
    private ConfigStore config;
    private Ledger ledger;
    private LinearLayout content;
    private LinearLayout footer;
    private Button saveAction;
    private String saveLabel;
    private boolean resumed;
    private ScreenPresenter screens;
    private final UiRevision dataLoads=new UiRevision();
    private String loadedSignature="";
    private String page="accounts",itemId="",kind="EXPENSE",originalId="",settlePerson="";
    private Bundle draft;
    private final Map<String,EditText> inputs=new LinkedHashMap<>();
    private final Map<String,LedgerChoice> selects=new LinkedHashMap<>();
    private long chosenAt;
    private boolean busy;
    private boolean applyingSavedState;
    private String currency(long n){return config.currencySymbol()+Money.decimal(n);}
    private int dp(int n){return Design.dp(this,n);}
    public static Intent intent(Context c,String page){return new Intent(c,LedgerActivity.class).putExtra("page",page);}

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);config=new ConfigStore(this);setTheme(Design.dark(this)?R.style.Theme_PocketLedger_Ledger_Dark:R.style.Theme_PocketLedger_Ledger);
        page=getIntent().getStringExtra("page")==null?"accounts":getIntent().getStringExtra("page");
        itemId=getIntent().getStringExtra("id")==null?"":getIntent().getStringExtra("id");
        kind=getIntent().getStringExtra("kind")==null?"EXPENSE":getIntent().getStringExtra("kind");
        if(saved!=null){page=saved.getString("page",page);itemId=saved.getString("item",itemId);kind=saved.getString("kind",kind);originalId=saved.getString("original","");settlePerson=saved.getString("person","");draft=saved.getBundle("draft");}
        getWindow().setStatusBarColor(Design.background(this));getWindow().setNavigationBarColor(Design.background(this));
        if(!Design.dark(this))getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        screens=new ScreenPresenter(this);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        Ledger seed=MainActivity.cachedLedger();
        if(seed!=null){ledger=seed.presentationCopy();loadedSignature=MainActivity.cachedLedgerSnapshot();render(false);}
        reload();
    }
    @Override protected void onResume(){super.onResume();if(resumed&&ledger!=null)reload();resumed=true;}
    @Override protected void onPause(){if(ledger!=null&&("entry".equals(page)||"accountForm".equals(page)))captureDraft();super.onPause();}
    @Override protected void onDestroy(){dataLoads.next();if(screens!=null)screens.dispose();executor.shutdown();syncExecutor.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("page",page);out.putString("item",itemId);out.putString("kind",kind);out.putString("original",originalId);out.putString("person",settlePerson);
        Bundle b=new Bundle();for(Map.Entry<String,EditText> e:inputs.entrySet())b.putString(e.getKey(),e.getValue().getText().toString());
        for(Map.Entry<String,LedgerChoice> e:selects.entrySet())b.putInt(e.getKey(),e.getValue().getSelectedItemPosition());b.putLong("chosenAt",chosenAt);if(activeTags!=null)b.putString("tag",activeTags.getValue());out.putBundle("draft",b);super.onSaveInstanceState(out);
    }
    private void reload(){
        if(applyingSavedState){if("entry".equals(page)||"accountForm".equals(page))captureDraft();render(true);return;}
        final int request=dataLoads.next();
        executor.execute(()->{try{
            if(!dataLoads.current(request))return;
            final Ledger[] loaded=new Ledger[1];AppDatabase db=AppDatabase.get(this);
            db.runInTransaction(()->loaded[0]=Ledger.load(db));
            String signature=LedgerJson.write(loaded[0]).toString();
            runOnUiThread(()->{
                if(!dataLoads.current(request)||isFinishing()||isDestroyed())return;
                ledger=loaded[0];if(signature.equals(loadedSignature)&&content!=null)return;
                loadedSignature=signature;
                if(!inputs.isEmpty()&&("entry".equals(page)||"accountForm".equals(page)))captureDraft();
                render(false);
            });
        }catch(Exception e){runOnUiThread(()->{if(dataLoads.current(request)&&!isFinishing())error(e);});}});
    }
    private void go(String p,String id){page=p;itemId=id;draft=null;originalId="";settlePerson="";selectAccountHint="";render(true);}
    @Override public void onBackPressed(){back();}
    private void back(){
        if(busy)return;
        if(page.equals("accounts")||page.equals("people")||"entry".equals(getIntent().getStringExtra("page"))||"detail".equals(getIntent().getStringExtra("page")))finish();
        else go(page.equals("person")?"people":"accounts","");
    }
    private void goEntry(String type,String original,String person,String account){
        kind=type;page="entry";itemId="";draft=null;originalId=original;settlePerson=person;selectAccountHint=account;render(true);
    }
    private void render(boolean motion){
        inputs.clear();selects.clear();activeTags=null;chosenAt=draft==null?System.currentTimeMillis():draft.getLong("chosenAt",System.currentTimeMillis());
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Design.background(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(6),dp(20),dp(2));
        LinearLayout back=new LinearLayout(this);back.setGravity(Gravity.CENTER);back.setBackground(Design.shape(Design.surface(this),12,this));back.setContentDescription("Go back");back.setFocusable(true);
        back.addView(new NavIcon(this,NavIcon.ICON_BACK,Design.text(this)),new LinearLayout.LayoutParams(dp(24),dp(24)));
        back.setOnClickListener(v->back());
        top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView brand=Components.text(this,"Pocket Ledger",15,Design.textSoft(this),Typeface.NORMAL);brand.setPadding(dp(14),0,0,0);top.addView(brand);
        root.addView(top);ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(20),dp(12),dp(20),dp(18));footer.setBackgroundColor(Design.background(this));footer.setVisibility(View.GONE);saveAction=null;root.addView(footer);
        switch(page){case "accounts":accounts();break;case "account":accountDetail();break;case "accountForm":accountForm();break;case "people":people();break;case "person":personDetail();break;case "entry":entry();break;case "detail":detail();break;default:accounts();}
        draft=null;
        screens.show(root,page+":"+itemId,motion);
    }
    private void heading(String title,String sub){TextView h=Components.text(this,title,28,Design.text(this),Typeface.BOLD);content.addView(h);note(sub);}
    private TextView note(String text){TextView t=Components.text(this,text,14,Design.textSoft(this),Typeface.NORMAL);t.setPadding(0,dp(8),0,dp(16));content.addView(t);return t;}
    private void summary(String label,String value,String detail){
        LinearLayout card=Components.bentoBlock(this);card.setPadding(dp(20),dp(20),dp(20),dp(20));
        card.addView(Components.text(this,label,13,Design.textSoft(this),Typeface.NORMAL));
        TextView amount=Components.text(this,value,36,Design.text(this),Typeface.NORMAL);amount.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));amount.setPadding(0,dp(8),0,dp(8));card.addView(amount);
        card.addView(Components.text(this,detail,13,Design.textSoft(this),Typeface.NORMAL));content.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    private Button action(String label,Runnable run){
        Button b=Components.secondaryButton(this,label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setBackground(Design.shape(Design.surface(this),12,this));
        if(label.startsWith("Delete"))b.setTextColor(Design.error(this));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(b,p);b.setOnClickListener(v->{if(!busy)run.run();});return b;
    }
    private Button primaryAction(String label,Runnable run){
        saveAction=Components.primaryButton(this,label);saveLabel=label;footer.setVisibility(View.VISIBLE);footer.removeAllViews();footer.addView(saveAction,new LinearLayout.LayoutParams(-1,-2));
        saveAction.setOnClickListener(v->{if(!busy)run.run();});return saveAction;
    }
    private void section(String title){TextView text=Components.text(this,title,18,Design.text(this),Typeface.BOLD);text.setPadding(0,dp(26),0,dp(8));content.addView(text);}
    private void line(String title,String sub,Runnable run){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(16),0,dp(16));row.setMinimumHeight(dp(72));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Components.text(this,title,16,Design.text(this),Typeface.BOLD));TextView text=Components.text(this,sub,13,Design.textSoft(this),Typeface.NORMAL);text.setPadding(0,dp(5),0,0);labels.addView(text);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(run!=null){NavIcon arrow=new NavIcon(this,NavIcon.ICON_CHEVRON,Design.muted(this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(20),dp(20));p.leftMargin=dp(12);row.addView(arrow,p);row.setOnClickListener(v->{if(!busy)run.run();});row.setFocusable(true);row.setContentDescription(title+". "+sub);}
        content.addView(row);content.addView(Components.hairline(this));
    }
    private EditText input(String key,String label,String value,boolean numeric){
        TextView l=Components.text(this,label,14,Design.textSoft(this),Typeface.NORMAL);l.setPadding(0,dp(20),0,dp(9));l.setTag("input_label:"+key);content.addView(l);
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(Design.text(this));e.setHintTextColor(Design.muted(this));e.setHint(numeric?"0.00":label);e.setContentDescription(label);
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|("opening".equals(key)||"ADJUSTMENT".equals(kind)?InputType.TYPE_NUMBER_FLAG_SIGNED:0));
        e.setText(draft!=null&&draft.containsKey(key)?draft.getString(key):value);
        if("amount".equals(key)||"opening".equals(key)){
            LinearLayout amountRow=new LinearLayout(this);amountRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView symbol=Components.text(this,config.currencySymbol(),32,Design.primary(this),Typeface.NORMAL);amountRow.addView(symbol);
            e.setTextSize(44);e.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));e.setLetterSpacing(-0.025f);e.setBackground(null);e.setPadding(dp(8),dp(8),0,dp(12));
            amountRow.addView(e,new LinearLayout.LayoutParams(0,-2,1));content.addView(amountRow);content.addView(Components.hairline(this));
        }else{e.setBackground(Design.strokeShape(Design.surface(this),Design.border(this),12,this));e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setMinHeight(dp(54));content.addView(e,new LinearLayout.LayoutParams(-1,-2));}
        e.setTag("input:"+key);inputs.put(key,e);return e;
    }
    private LedgerChoice select(String key,String label,List<String> options,int index){
        TextView l=Components.text(this,label,14,Design.textSoft(this),Typeface.NORMAL);l.setPadding(0,dp(20),0,dp(8));content.addView(l);
        int chosen=draft!=null&&draft.containsKey(key)?draft.getInt(key):index;
        LedgerChoice s=new LedgerChoice(this,label,options,chosen);content.addView(s,new LinearLayout.LayoutParams(-1,-2));selects.put(key,s);return s;
    }
    private void dateField(long initial){
        if(draft==null)chosenAt=initial;
        Button b=action(dateText(chosenAt),()->{Calendar cal=Calendar.getInstance();cal.setTimeInMillis(chosenAt);
            DatePickerDialog d=new DatePickerDialog(this,(date,y,m,day)->new TimePickerDialog(this,(time,h,min)->{cal.set(y,m,day,h,min,0);cal.set(Calendar.MILLISECOND,0);chosenAt=cal.getTimeInMillis();((Button)content.findViewWithTag("entry_date")).setText(dateText(chosenAt));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show(),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH));d.getDatePicker().setMaxDate(System.currentTimeMillis());d.show();});b.setTag("entry_date");
    }
    private String dateText(long at){return "Date · "+new java.text.SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.US).format(new Date(at));}
    private interface Work {void run() throws Exception;}
    private void work(Work task,Runnable done){
        work(task,done,"Saved successfully");
    }
    private void work(Work task,Runnable done,String success){
        if(busy)return;busy=true;content.setAlpha(0.6f);if(saveAction!=null){saveAction.setEnabled(false);saveAction.setText("Saving…");}
        dataLoads.next();
        executor.execute(()->{try{
            task.run();
            final Ledger[] saved=new Ledger[1];AppDatabase db=AppDatabase.get(this);db.runInTransaction(()->saved[0]=Ledger.load(db));
            String signature=LedgerJson.write(saved[0]).toString();
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()){busy=false;MainActivity.notifyPendingSyncCompleted();return;}
                ledger=saved[0];loadedSignature=signature;dataLoads.next();
                android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(keyboard!=null)keyboard.hideSoftInputFromWindow(content.getWindowToken(),0);
                content.setAlpha(1);applyingSavedState=true;
                try{done.run();Components.customToast(this,success,false);}
                finally{applyingSavedState=false;busy=false;if(saveAction!=null){saveAction.setEnabled(true);saveAction.setText(saveLabel);}}
                MainActivity.notifyPendingSyncCompleted();
            });
            Context app=getApplicationContext();
            try{syncExecutor.execute(()->{try{SyncEngine.syncAllPending(app,new ConfigStore(app));}catch(Exception ignored){}finally{MainActivity.notifyPendingSyncCompleted();}});}catch(RejectedExecutionException ignored){}
        }catch(Exception e){runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;content.setAlpha(1);if(saveAction!=null){saveAction.setEnabled(true);saveAction.setText(saveLabel);}error(e);});}});
    }
    private void error(Exception e){LedgerUi.dialog(this).setTitle("Could not save").setMessage(e.getMessage()==null?"Please try again.":e.getMessage()).setPositiveButton("OK",null).show();}
    private void confirm(String title,String message,Runnable run){LedgerUi.dialog(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->run.run()).show();}
    private void accounts(){
        heading("Accounts","Your money, with a balance for every account.");
        long cash=0,card=0;for(AccountEntity a:ledger.accounts)if(!a.deleted){if(a.isCard())card=Money.add(card,ledger.balance(a));else cash=Money.add(cash,ledger.balance(a));}
        summary("Available money",currency(cash),"Net balance "+currency(Money.add(cash,card)));
        section("Your accounts");
        if(ledger.accounts.stream().noneMatch(a->!a.deleted))note("Add My money for one balance, or create separate bank, cash, wallet and card accounts. Enter today's actual balance to start now.");
        for(AccountEntity a:ledger.accounts)if(!a.deleted)line(a.name+(a.archived?" · Archived":""),a.isCard()?(ledger.balance(a)<0?"Amount owed · "+currency(-ledger.balance(a)):"Card credit · "+currency(ledger.balance(a))):"Current balance · "+currency(ledger.balance(a)),()->go("account",a.accountId));
        primaryAction("Add account",()->go("accountForm",""));
        action("Connect old payment methods",this::mapping);
        section("Preferences");
        action("Use last payment (no fixed default)",()->{getSharedPreferences("ledger_ui",0).edit().remove("defaultPayment").apply();Components.customToast(this,"Last payment will be preselected",false);});
        action("People · Lena / Dena",()->go("people",""));
        action("Income categories",this::incomeCategories);
        if(ledger.unassigned()>0)note(ledger.unassigned()+" expenses need an account. Overall spending includes them; account balances are incomplete until assigned.");
    }
    private void accountDetail(){
        AccountEntity a=ledger.account(itemId);if(a==null||a.deleted){go("accounts","");return;}
        heading(a.name,LedgerUi.label(a.type));
        long amount=ledger.balance(a);
        summary(a.isCard()?(amount<0?"Amount owed":"Card credit"):"Current balance",currency(a.isCard()?Math.abs(amount):amount),a.archived?"Archived account":"Account balance today");
        note("Balance starts "+dateText(a.openingAt).replace("Date · ","")+". Older entries remain in history but are not deducted again.");
        primaryAction("Transfer / Pay card",()->goEntry("TRANSFER","","",a.isCard()?"":a.accountId));
        LinearLayout mainContent=content;
        LinearLayout management=new LinearLayout(this);management.setOrientation(LinearLayout.VERTICAL);management.setVisibility(View.GONE);
        action("Account settings",()->management.setVisibility(management.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        content=management;
        action("Edit name / opening balance",()->go("accountForm",a.accountId));
        action(a.archived?"Unarchive account":"Archive account",()->confirm("Change account visibility?","Archived accounts stay in Overall balances and history. Settle or transfer remaining money first if you want a zero balance.",()->work(()->{a.archived=!a.archived;a.updatedAt=System.currentTimeMillis();LedgerStore.putAccount(this,a);},this::reload)));
        action("Set as default payment",()->{if(a.archived){Components.customToast(this,"Unarchive this account first",true);return;}getSharedPreferences("ledger_ui",0).edit().putString("defaultPayment","a:"+a.accountId).apply();Components.customToast(this,"Default payment saved",false);});
        action("Move account earlier",()->work(()->{a.position=ledger.accounts.stream().mapToInt(x->x.position).min().orElse(0)-1;a.updatedAt=System.currentTimeMillis();LedgerStore.putAccount(this,a);},this::reload));
        action("Add payment alias (UPI / debit card)",()->alias(a));
        action("Balance adjustment",()->goEntry("ADJUSTMENT","","",a.accountId));
        boolean used=false;for(TransactionEntity t:ledger.transactions)if(a.accountId.equals(t.accountId)||a.accountId.equals(t.destinationId))used=true;
        if(!used)action("Delete unused account",()->confirm("Delete unused account?",a.name,()->work(()->{AppDatabase db=AppDatabase.get(this);db.runInTransaction(()->{Ledger l=Ledger.load(db);for(TransactionEntity t:l.transactions)Ledger.require(!a.accountId.equals(t.accountId)&&!a.accountId.equals(t.destinationId),"Account has history; archive it instead");for(PaymentRouteEntity r:l.routes)if(a.accountId.equals(r.accountId)){r.deleted=true;r.updatedAt=System.currentTimeMillis();db.ledgerDao().putRoute(r);}a.deleted=true;a.updatedAt=System.currentTimeMillis();db.ledgerDao().putAccount(a);});},()->{page="accounts";itemId="";reload();},"Account deleted successfully")));
        content=mainContent;content.addView(management);section("Account activity");
        boolean hasHistory=false;
        for(TransactionEntity t:ledger.transactions)if(!t.deleted&&ledger.matches(t,a.accountId)){hasHistory=true;transactionLine(t);}
        if(!hasHistory)note("Your first entry will appear here.");
    }
    private String selectAccountHint="";
    private void accountForm(){
        AccountEntity old=ledger.account(itemId);heading(old==null?"Add account":"Edit account","Starting balance is money already there, not income.");
        final String accountId=old==null?UUID.randomUUID().toString():old.accountId;
        EditText name=input("name","Account name",old==null?"":old.name,false);
        List<String>types=Arrays.asList("BANK","CASH","WALLET","CARD");LedgerChoice type=select("type","Account type",types,old==null?0:types.indexOf(old.type));
        EditText amount=input("opening",old!=null&&old.isCard()?"Amount owed":"Starting balance",old==null?"0":Money.decimal(old.isCard()?-old.openingMinor:old.openingMinor),true);
        TextView help=note(old!=null&&old.isCard()?"Positive means money owed; negative means credit with the card issuer.":"Enter today's actual balance. Earlier entries stay in history and are not deducted again.");
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
            boolean card="CARD".equals(types.get(position));((TextView)content.findViewWithTag("input_label:opening")).setText(card?"Amount owed":"Starting balance");
            help.setText(card?"Positive means money owed; negative means credit with the card issuer.":"Enter today's actual balance. Earlier entries stay in history and are not deducted again.");
        }});
        dateField(old==null?System.currentTimeMillis():old.openingAt);
        primaryAction(old==null?"Create account":"Save account",()->{try{
            AccountEntity a=new AccountEntity();a.accountId=accountId;a.name=name.getText().toString().trim();a.type=types.get(type.getSelectedItemPosition());
            Ledger.require(!a.name.isEmpty(),"Enter an account name");if(old!=null)Ledger.require(a.type.equals(old.type),"Account type cannot change after setup. Create another account instead.");
            a.openingMinor=Money.parse(amount.getText().toString());if(a.isCard())a.openingMinor=-a.openingMinor;a.openingAt=chosenAt;a.updatedAt=System.currentTimeMillis();a.position=old==null?ledger.accounts.size():old.position;a.archived=old!=null&&old.archived;
            long proposed=ledger.balance(a);confirm("Save account?",(a.isCard()?(proposed<0?"Amount owed will be ":"Card credit will be "):"Current balance will be ")+currency(a.isCard()?Math.abs(proposed):proposed)+". Earlier history is preserved.",()->work(()->LedgerStore.putAccount(this,a),()->{page="accounts";itemId="";reload();},old==null?"Account created successfully":"Account updated successfully"));
        }catch(Exception e){error(e);}});
    }
    private void incomeCategories(){
        List<String> values=config.incomeCategories();List<String> options=new ArrayList<>(values);options.add("Add category");
        LedgerUi.dialog(this).setTitle("Income categories").setItems(options.toArray(new String[0]),(d,w)->{
            EditText field=dialogInput("Category name");if(w<values.size())field.setText(values.get(w));
            AlertDialog.Builder editor=LedgerUi.dialog(this).setTitle(w==values.size()?"Add income category":"Edit income category").setView(dialogContent(field)).setNegativeButton("Cancel",null).setPositiveButton("Save",(dd,ww)->{
                String name=field.getText().toString().trim();if(name.isEmpty()){Components.customToast(this,"Enter a name",true);return;}
                if(w==values.size())values.add(name);else values.set(w,name);config.setIncomeCategories(new ArrayList<>(new LinkedHashSet<>(values)));incomeCategories();
            });
            if(w<values.size()&&values.size()>1)editor.setNeutralButton("Delete",(dd,ww)->{values.remove(w);config.setIncomeCategories(values);incomeCategories();});editor.show();
        }).show();
    }
    private void alias(AccountEntity a){
        EditText field=dialogInput("e.g. SBI · UPI");LedgerUi.dialog(this).setTitle("Payment alias → "+a.name).setView(dialogContent(field)).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String label=field.getText().toString().trim();if(label.isEmpty())return;work(()->LedgerStore.mapPayment(this,label,a.accountId),this::reload);}).show();
    }
    private void mapping(){
        List<String> labels=new ArrayList<>();for(String s:config.paymentModes())labels.add(s);for(TransactionEntity t:ledger.transactions)if(!t.deleted&&t.accountId.isEmpty()&&!labels.contains(t.paymentMode))labels.add(t.paymentMode);
        LedgerUi.dialog(this).setTitle("Link a payment label").setItems(labels.toArray(new String[0]),(d,w)->{
            List<AccountEntity> accounts=ledger.activeAccounts(true);if(accounts.isEmpty()){Components.customToast(this,"Add an account first",true);return;}
            String label=labels.get(w);LedgerUi.dialog(this).setTitle(label+" belongs to").setItems(accounts.stream().map(a->a.name).toArray(String[]::new),(dd,ww)->{
                AccountEntity a=accounts.get(ww);long count=ledger.transactions.stream().filter(t->!t.deleted&&t.accountId.isEmpty()&&t.paymentMode.equals(label)).count();
                confirm("Link "+label+" to "+a.name+"?",count+" unassigned entries will be linked. Only entries on/after the account's balance start affect its current balance.",()->work(()->LedgerStore.mapPayment(this,label,a.accountId),this::reload));
            }).show();
        }).show();
    }
    private void people(){
        heading("Lena / Dena","Keep track of what comes back, and what you owe.");
        LinearLayout totals=Components.bentoBlock(this);totals.setOrientation(LinearLayout.HORIZONTAL);totals.setPadding(dp(20),dp(20),dp(20),dp(20));
        totals.addView(LedgerUi.fact(this,"To receive",currency(ledger.owedTotal(true)),Design.text(this)),new LinearLayout.LayoutParams(0,-2,1));
        totals.addView(LedgerUi.fact(this,"To pay",currency(ledger.owedTotal(false)),Design.text(this)),new LinearLayout.LayoutParams(0,-2,1));content.addView(totals);section("People");
        if(ledger.people.stream().noneMatch(p->!p.deleted))note("Add a friend to record a loan, shared bill or repayment.");
        for(PersonEntity p:ledger.people)if(!p.deleted){long receive=0,pay=0;for(Ledger.Owed o:ledger.owed())if(o.personId.equals(p.personId)){if(o.receivable)receive=Money.add(receive,o.remaining);else pay=Money.add(pay,o.remaining);}
            if(!p.archived||receive!=0||pay!=0)line(p.name+(p.archived?" · Archived":""),"Receive "+currency(receive)+" · Pay "+currency(pay),()->go("person",p.personId));}
        primaryAction("Add person",()->personName(null));
        action("Lend / Borrow / Friend paid",()->{kind="LEND";go("entry","");});
        action("Shared expense / Buy for a friend",()->{kind="EXPENSE";go("entry","");});
    }
    private View dialogContent(View field){LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(dp(24),dp(8),dp(24),0);wrapper.addView(field,new LinearLayout.LayoutParams(-1,-2));return wrapper;}
    private EditText dialogInput(String hint){EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(16);field.setHint(hint);field.setTextColor(Design.text(this));field.setHintTextColor(Design.muted(this));field.setBackground(Design.strokeShape(Design.background(this),Design.border(this),12,this));field.setPadding(dp(16),dp(14),dp(16),dp(14));return field;}
    private void personName(PersonEntity old){
        EditText field=dialogInput("Name");if(old!=null)field.setText(old.name);
        LedgerUi.dialog(this).setTitle(old==null?"Add person":"Rename person").setView(dialogContent(field)).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            PersonEntity p=new PersonEntity();p.personId=old==null?UUID.randomUUID().toString():old.personId;p.name=field.getText().toString().trim();p.updatedAt=System.currentTimeMillis();p.archived=old!=null&&old.archived;
            work(()->LedgerStore.putPerson(this,p),this::reload,old==null?"Person added successfully":"Person updated successfully");
        }).show();
    }
    private void personDetail(){
        PersonEntity person=ledger.person(itemId);if(person==null){go("people","");return;}
        heading(person.name,"Open items and repayment history.");
        long receive=0,pay=0;
        for(Ledger.Owed item:ledger.owed())if(item.personId.equals(person.personId)){if(item.receivable)receive=Money.add(receive,item.remaining);else pay=Money.add(pay,item.remaining);}
        summary("To receive",currency(receive),"To pay "+currency(pay));
        LinearLayout parent=content,settings=new LinearLayout(this);settings.setOrientation(LinearLayout.VERTICAL);settings.setVisibility(View.GONE);
        action("Person settings",()->settings.setVisibility(settings.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));content=settings;
        action("Rename person",()->personName(person));
        action(person.archived?"Unarchive person":"Archive person",()->work(()->{person.archived=!person.archived;person.updatedAt=System.currentTimeMillis();LedgerStore.putPerson(this,person);},this::reload));
        boolean referenced=ledger.transactions.stream().anyMatch(t->person.personId.equals(t.personId)||Ledger.shares(t).containsKey(person.personId));
        if(!referenced)action("Delete unused person",()->confirm("Delete person?",person.name,()->work(()->{person.deleted=true;person.updatedAt=System.currentTimeMillis();LedgerStore.putPerson(this,person);},()->{page="people";itemId="";reload();},"Person deleted successfully")));
        content=parent;content.addView(settings);section("Open items");
        if(receive==0&&pay==0)note("All settled. Nothing to receive or pay.");
        for(Ledger.Owed item:ledger.owed())if(item.personId.equals(person.personId)&&item.remaining>0)
            line((item.receivable?"To receive · ":"To pay · ")+currency(item.remaining),item.origin.date+" · "+item.origin.category,()->{
                List<String> choices=Arrays.asList(item.receivable?"Record money received":"Record repayment sent",item.receivable?"Write off remaining debt":"Debt was forgiven","View original entry");
                LedgerUi.choose(this,"Outstanding "+currency(item.remaining),choices,-1,which->{
                    if(which==2){go("detail",item.origin.transactionId);return;}
                    kind=which==0?(item.receivable?"REPAYMENT_IN":"REPAYMENT_OUT"):(item.receivable?"WRITE_OFF_RECEIVABLE":"FORGIVE_PAYABLE");
                    goEntry(kind,item.origin.transactionId,item.personId,"");
                });
            });
        section("History");
        boolean history=false;for(TransactionEntity tx:ledger.transactions)if(!tx.deleted&&(person.personId.equals(tx.personId)||Ledger.shares(tx).containsKey(person.personId))){history=true;transactionLine(tx);}
        if(!history)note("Shared payments, loans and repayments will appear here.");
    }
    private void transactionLine(TransactionEntity t){line(Ledger.label(t)+" · "+currency(Ledger.amount(t)),t.date+" · "+ledger.accountName(t.accountId)+" · "+t.category+" · "+t.remarks,()->go("detail",t.transactionId));}
    private void detail(){
        TransactionEntity t=ledger.transaction(itemId);if(t==null){finish();return;}
        heading(Ledger.label(t),t.category);
        summary("Amount",currency(Ledger.amount(t)),t.date+" · "+t.time);
        note("Account: "+ledger.accountName(t.accountId)+(t.destinationId.isEmpty()?"":" → "+ledger.accountName(t.destinationId))+"\n"+t.remarks);
        if(Ledger.expense(t)!=0)line("Your expense",currency(Ledger.expense(t)),null);
        for(Map.Entry<String,Long> e:Ledger.shares(t).entrySet())line(ledger.personName(e.getKey()),currency(e.getValue()),null);
        if(!t.personId.isEmpty())line("Person",ledger.personName(t.personId),null);
        primaryAction("Edit entry",()->{kind=t.kind;go("entry",t.transactionId);});
        if("EXPENSE".equals(t.kind))action("Record merchant refund",()->goEntry("REFUND",t.transactionId,"",""));
        action("Delete entry",()->confirm("Delete this entry?","All effects of this operation are removed together. Linked refunds or repayments must be resolved first.",()->work(()->LedgerStore.delete(this,t.transactionId),this::finish,"Entry deleted successfully")));
    }
    private void entry(){
        TransactionEntity old=ledger.transaction(itemId);if(old!=null){kind=old.kind;originalId=old.originalId;settlePerson=old.personId;}
        boolean linked=!originalId.isEmpty();boolean expense="EXPENSE".equals(kind),refund="REFUND".equals(kind),transfer="TRANSFER".equals(kind);
        boolean noAccount=Arrays.asList("FRIEND_PAID","WRITE_OFF_RECEIVABLE","FORGIVE_PAYABLE").contains(kind);
        heading(old==null?"Add "+LedgerUi.label(kind).toLowerCase(Locale.US):"Edit "+Ledger.label(old),entrySubtitle(kind));
        if(old==null&&!linked){
            LinearLayout modes=new LinearLayout(this);
            for(String mode:new String[]{"EXPENSE","INCOME","MORE"}){
                boolean active=mode.equals(kind)||mode.equals("MORE")&&!expense&&!"INCOME".equals(kind);
                Button choice=LedgerUi.chip(this,mode.equals("MORE")?"More":LedgerUi.label(mode),active);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMarginEnd(dp(6));modes.addView(choice,p);
                choice.setOnClickListener(v->{if(mode.equals("MORE")){List<String> more=Arrays.asList("TRANSFER","LEND","BORROW","FRIEND_PAID","ADJUSTMENT");LedgerUi.choose(this,"More entries",more,more.indexOf(kind),pos->switchKind(more.get(pos)));}else if(!mode.equals(kind))switchKind(mode);});
            }
            content.addView(modes);
        }
        long defaultAmount=0;
        if(linked&&!refund)for(Ledger.Owed o:ledger.owed())if(o.origin.transactionId.equals(originalId)&&o.personId.equals(settlePerson))defaultAmount=o.remaining;
        EditText amount=input("amount","Amount",old==null?(defaultAmount>0?Money.decimal(defaultAmount):getIntent().getStringExtra("amount")==null?"":getIntent().getStringExtra("amount")):Money.decimal(Ledger.amount(old)),true);
        List<PaymentChoice> choices=PaymentChoice.list(ledger,expense||refund||"ADJUSTMENT".equals(kind));
        if(old!=null&&!old.accountId.isEmpty()&&choices.stream().noneMatch(c->c.accountId.equals(old.accountId)))choices.add(new PaymentChoice("a:"+old.accountId,ledger.accountName(old.accountId)+" (archived)",old.accountId,""));
        if(expense)choices.add(new PaymentChoice("","Needs account (assign later)","",""));
        String defaultPayment=getSharedPreferences("ledger_ui",0).getString("defaultPayment","");
        if(defaultPayment.isEmpty())defaultPayment=getSharedPreferences("ledger_ui",0).getString("lastPayment","");
        int chosen=0;String preferred=old!=null?(old.accountId.isEmpty()?"":old.routeId.isEmpty()?"a:"+old.accountId:"r:"+old.routeId):selectAccountHint.isEmpty()?(getIntent().hasExtra("paymentChoice")?getIntent().getStringExtra("paymentChoice"):defaultPayment):"a:"+selectAccountHint;
        for(int i=0;i<choices.size();i++)if(choices.get(i).id.equals(preferred))chosen=i;
        LedgerChoice payment=noAccount?null:select("payment",transfer?"From account":kind.equals("INCOME")?"Received in":"Payment method",choiceNames(choices),chosen);
        if(!noAccount&&choices.isEmpty())action("Add an account",()->{captureDraft();startActivity(intent(this,"accounts"));});
        List<AccountEntity> to=ledger.activeAccounts(true);if(old!=null&&!old.destinationId.isEmpty()&&to.stream().noneMatch(a->a.accountId.equals(old.destinationId))){AccountEntity a=ledger.account(old.destinationId);if(a!=null)to.add(a);}
        int toIndex=0;for(int i=0;i<to.size();i++)if(old!=null&&to.get(i).accountId.equals(old.destinationId))toIndex=i;
        LedgerChoice destination=transfer?select("destination","To account / credit card",to.stream().map(a->a.name).collect(java.util.stream.Collectors.toList()),toIndex):null;
        List<String> categories=new ArrayList<>("INCOME".equals(kind)?config.incomeCategories():config.categories());
        if(old!=null&&!categories.contains(old.category))categories.add(old.category);
        String preferredCategory=old!=null?old.category:expense?(config.defaultCategory().isEmpty()?config.lastCategory():config.defaultCategory()):"";
        LedgerChoice category=(expense||"INCOME".equals(kind)||"FRIEND_PAID".equals(kind))?select("category","Category",categories,Math.max(0,categories.indexOf(preferredCategory))):null;
        SpendingTypePicker tags=null;if(expense||"FRIEND_PAID".equals(kind)){section("Need or want? (optional)");tags=new SpendingTypePicker(this,draft!=null?draft.getString("tag",old==null?SpendingType.UNTAGGED:old.spendingType):old==null?SpendingType.UNTAGGED:old.spendingType);activeTags=tags;content.addView(tags);}
        List<PersonEntity> people=new ArrayList<>();for(PersonEntity p:ledger.people)if(!p.deleted&&(!p.archived||old!=null&&p.personId.equals(old.personId)))people.add(p);
        boolean choosePerson=Arrays.asList("LEND","BORROW","FRIEND_PAID").contains(kind);int pi=0;for(int i=0;i<people.size();i++)if(old!=null&&old.personId.equals(people.get(i).personId))pi=i;
        LedgerChoice person=choosePerson?select("person","Person",people.stream().map(p->p.name).collect(java.util.stream.Collectors.toList()),pi):null;
        if(choosePerson&&people.isEmpty())action("Add a person first",()->{captureDraft();personName(null);});
        Map<String,EditText> splitFields=new LinkedHashMap<>();EditText own=null;
        if(expense||refund){
            boolean sharesOpen=(old!=null&&!Ledger.shares(old).isEmpty())||refund;
            android.widget.CheckBox shareSwitch=new android.widget.CheckBox(this);shareSwitch.setText(refund?"Allocate refund between you and friends":"For a friend / Split payment");shareSwitch.setTextColor(Design.text(this));shareSwitch.setTextSize(15);shareSwitch.setPadding(0,dp(16),0,dp(8));shareSwitch.setButtonTintList(android.content.res.ColorStateList.valueOf(Design.primary(this)));shareSwitch.setChecked(sharesOpen||draft!=null&&"true".equals(draft.getString("split")));content.addView(shareSwitch);
            LinearLayout splitArea=new LinearLayout(this);splitArea.setOrientation(LinearLayout.VERTICAL);LinearLayout parent=content;content=splitArea;
            own=input("own","Your share (0 if entirely for a friend)",old==null?"0":Money.decimal(Ledger.personal(old)),true);
            Map<String,Long> oldShares=old==null?Collections.emptyMap():Ledger.shares(old);TransactionEntity origin=ledger.transaction(originalId);
            for(PersonEntity p:ledger.people)if(!p.deleted&&(!p.archived||oldShares.containsKey(p.personId))&&(!refund||origin!=null&&Ledger.shares(origin).containsKey(p.personId)))splitFields.put(p.personId,input("share:"+p.personId,p.name+" share",Money.decimal(oldShares.getOrDefault(p.personId,0L)),true));
            if(splitFields.isEmpty()&&!refund)note("Add people from Lena / Dena before splitting. Your share must equal the total for an ordinary expense.");
            if(!refund)action("Add person",()->{captureDraft();personName(null);});
            content=parent;content.addView(splitArea);splitArea.setVisibility(shareSwitch.isChecked()?View.VISIBLE:View.GONE);shareSwitch.setOnCheckedChangeListener((button,checked)->splitArea.setVisibility(checked?View.VISIBLE:View.GONE));
            // A hidden text field preserves this optional section's state across rotation.
            EditText state=new EditText(this);state.setText(String.valueOf(shareSwitch.isChecked()));inputs.put("split",state);shareSwitch.setOnCheckedChangeListener((button,checked)->{state.setText(String.valueOf(checked));splitArea.setVisibility(checked?View.VISIBLE:View.GONE);});
            splitEnabled=shareSwitch;
        }else splitEnabled=null;
        if(linked)note("Linked to "+(ledger.transaction(originalId)==null?"original entry":ledger.transaction(originalId).date)+(!settlePerson.isEmpty()?" · "+ledger.personName(settlePerson):""));
        dateField(old==null?System.currentTimeMillis():Ledger.at(old));EditText notes=input("note",kind.equals("ADJUSTMENT")?"Reason for adjustment":"Note (optional)",old==null?(getIntent().getStringExtra("note")==null?"":getIntent().getStringExtra("note")):old.remarks,false);
        final EditText ownField=own;final SpendingTypePicker tagPicker=tags;final CheckBox splitControl=splitEnabled;
        primaryAction(old==null?"Save "+LedgerUi.label(kind).toLowerCase(Locale.US):"Save changes",()->{try{
            long n=Money.parse(amount.getText().toString());Ledger.require(n>0||"ADJUSTMENT".equals(kind)&&n!=0,"Enter a positive amount");
            TransactionEntity t=new TransactionEntity();t.transactionId=old==null?UUID.randomUUID().toString():old.transactionId;t.createdAt=old==null?System.currentTimeMillis():old.createdAt;t.kind=kind;t.amountMinor=n;t.personalMinor=(expense||"FRIEND_PAID".equals(kind)||refund)?n:0;t.amount=n/100.0;t.effectiveAt=chosenAt;t.date=new java.text.SimpleDateFormat("dd-MM-yyyy",Locale.US).format(new Date(chosenAt));t.time=new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date(chosenAt));t.deviceName=config.deviceName();t.originalId=originalId;t.personId=settlePerson;t.remarks=notes.getText().toString().trim();t.category=category==null?kind.replace('_',' '):categories.get(category.getSelectedItemPosition());t.spendingType=tagPicker==null?SpendingType.UNTAGGED:tagPicker.getValue();if(refund&&ledger.transaction(originalId)!=null){t.spendingType=ledger.transaction(originalId).spendingType;t.category=ledger.transaction(originalId).category;}
            if(!noAccount){Ledger.require(payment!=null&&payment.getSelectedItemPosition()>=0&&!choices.isEmpty(),"Add an account first");PaymentChoice c=choices.get(payment.getSelectedItemPosition());t.accountId=c.accountId;t.routeId=c.routeId;t.paymentMode=c.label;}
            if(transfer){Ledger.require(destination.getSelectedItemPosition()>=0,"Choose a destination");t.destinationId=to.get(destination.getSelectedItemPosition()).accountId;t.paymentMode=ledger.accountName(t.accountId)+" → "+ledger.accountName(t.destinationId);}
            if(choosePerson){Ledger.require(person.getSelectedItemPosition()>=0,"Add a person first");t.personId=people.get(person.getSelectedItemPosition()).personId;}
            if(splitControl!=null&&splitControl.isChecked()){t.personalMinor=Money.parse(ownField.getText().toString());JSONObject shares=new JSONObject();for(Map.Entry<String,EditText> e:splitFields.entrySet()){long part=Money.parse(e.getValue().getText().toString());Ledger.require(part>=0,"Friend shares cannot be negative");if(part>0)shares.put(e.getKey(),part);}t.shares=shares.toString();}
            if("ADJUSTMENT".equals(kind))Ledger.require(!t.remarks.isEmpty(),"Add a reason for this balance adjustment");
            work(()->LedgerStore.save(this,t),()->{
                if(expense){config.recordSelection(t.category,t.paymentMode);if(!t.accountId.isEmpty())getSharedPreferences("ledger_ui",0).edit().putString("lastPayment",t.routeId.isEmpty()?"a:"+t.accountId:"r:"+t.routeId).apply();}
                finish();
            },"Entry saved locally");
        }catch(Exception e){error(e);}});
    }
    private void switchKind(String next){captureDraft();draft.remove("category");draft.remove("payment");kind=next;render(true);}
    private String entrySubtitle(String type){
        switch(type){case "INCOME":return "Money coming into your account.";case "TRANSFER":return "Move money between accounts, or pay a card.";case "LEND":return "Money a friend will pay back.";case "BORROW":return "Money you need to pay back.";case "FRIEND_PAID":return "Your expense, paid by someone else.";case "ADJUSTMENT":return "Correct an account balance with a reason.";default:return "Keep every part of your money in order.";}
    }
    private SpendingTypePicker activeTags;
    private void captureDraft(){
        Bundle b=new Bundle();for(Map.Entry<String,EditText> e:inputs.entrySet())b.putString(e.getKey(),e.getValue().getText().toString());
        for(Map.Entry<String,LedgerChoice> e:selects.entrySet())b.putInt(e.getKey(),e.getValue().getSelectedItemPosition());
        b.putLong("chosenAt",chosenAt);if(activeTags!=null)b.putString("tag",activeTags.getValue());draft=b;
    }
    private CheckBox splitEnabled;
    private List<String> choiceNames(List<PaymentChoice> c){List<String>s=new ArrayList<>();for(PaymentChoice p:c)s.add(p.label);return s;}
}
