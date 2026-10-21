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

/** Manual bill splitting tool; deliberately independent of expense/income flows. */
public final class BillSplitActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Map<String,EditText> fields=new HashMap<>();
    private BillSplitStore store;
    private ScreenPresenter presenter;
    private LinearLayout content,footer;
    private BillSplit bill;
    private List<BillSplit> bills=new ArrayList<>();
    private List<BillSplit.Person> savedFriends=new ArrayList<>();
    private int step;
    private boolean editing,busy;
    private Runnable totalPreview;
    private Runnable receiptTaxPreview;
    private ReceiptParser.Draft receipt;
    private final UiRevision receiptLoads=new UiRevision();
    private boolean readingReceipt;
    private String pendingUpload,receiptUri;
    private static final int PICK_RECEIPT=4102;
    private String money(long amount){return bill.currency+Money.decimal(amount);}
    private int dp(int n){return Design.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);setTheme(Design.dark(this)?R.style.Theme_PocketLedger_Ledger_Dark:R.style.Theme_PocketLedger_Ledger);
        getWindow().setStatusBarColor(Design.background(this));getWindow().setNavigationBarColor(Design.background(this));
        if(!Design.dark(this))getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        presenter=new ScreenPresenter(this);store=new BillSplitStore(this);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        if(state!=null)step=state.getInt("step",0);
        if(state!=null&&state.containsKey("bill"))try{bill=BillSplit.read(new JSONObject(state.getString("bill")));step=state.getInt("step");editing=state.getBoolean("editing");}catch(Exception e){error(e);}
        if(state!=null){pendingUpload=state.getString("pendingUpload");receiptUri=state.getString("receiptUri");if(state.containsKey("receipt"))try{receipt=ReceiptParser.Draft.read(new JSONObject(state.getString("receipt")));}catch(Exception e){step=2;error(e);}}
        render(false);if(state!=null)restoreFields(state.getBundle("fields"));load();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        Bundle draft=new Bundle();for(Map.Entry<String,EditText> e:fields.entrySet())draft.putString(e.getKey(),e.getValue().getText().toString());
        state.putBundle("fields",draft);state.putInt("step",step);
        try{capture();}catch(Exception ignored){/* Keep partially entered numeric text in the field bundle. */}
        try{if(bill!=null){state.putString("bill",bill.json().toString());state.putInt("step",step);state.putBoolean("editing",editing);}}
        catch(Exception e){throw new IllegalStateException("Could not preserve split draft",e);}
        state.putString("pendingUpload",pendingUpload);state.putString("receiptUri",receiptUri);
        if(receipt!=null)try{state.putString("receipt",receipt.json().toString());}catch(Exception e){throw new IllegalStateException(e);}
        super.onSaveInstanceState(state);
    }
    private void restoreFields(Bundle draft){if(draft==null)return;for(Map.Entry<String,EditText> e:fields.entrySet())if(draft.containsKey(e.getKey()))e.getValue().setText(draft.getString(e.getKey()));if(totalPreview!=null)totalPreview.run();}
    @Override protected void onDestroy(){receiptLoads.next();presenter.dispose();executor.execute(()->store.close());executor.shutdown();super.onDestroy();}
    private interface Work{void run() throws Exception;}
    private void work(Work task,Runnable done){
        if(busy)return;busy=true;if(content!=null)content.setAlpha(.6f);footer.setEnabled(false);
        executor.execute(()->{try{task.run();runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy=false;done.run();});}
            catch(Exception e){runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy=false;if(content!=null)content.setAlpha(1);footer.setEnabled(true);error(e);});}});
    }
    private void load(){work(()->{bills=store.all();savedFriends=store.friends();},()->{
        if(bill==null||step==1||step==7){Bundle draft=new Bundle();for(Map.Entry<String,EditText> e:fields.entrySet())draft.putString(e.getKey(),e.getValue().getText().toString());render(false);restoreFields(draft);}
        else{content.setAlpha(1);footer.setEnabled(true);if(pendingUpload!=null)readReceipt(android.net.Uri.parse(pendingUpload));}
    });}
    private void error(Exception e){LedgerUi.dialog(this).setTitle("Check this bill").setMessage(e.getMessage()==null?"Could not complete that action. Please try again.":e.getMessage()).setPositiveButton("OK",null).show();}
    private void attempt(Work task){if(busy)return;try{task.run();}catch(Exception e){error(e);}}
    private void confirm(String title,String message,Runnable action){LedgerUi.dialog(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{if(!busy)action.run();}).show();}
    private void heading(String title,String description){content.addView(Components.text(this,title,28,Design.text(this),Typeface.BOLD));note(description);}
    private void note(String text){TextView t=Components.text(this,text,14,Design.textSoft(this),Typeface.NORMAL);t.setPadding(0,dp(8),0,dp(16));content.addView(t);}
    private void section(String text){TextView t=Components.text(this,text,18,Design.text(this),Typeface.BOLD);t.setPadding(0,dp(24),0,dp(8));content.addView(t);}
    private Button action(String label,Runnable run){Button b=Components.secondaryButton(this,label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(14);b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setBackground(Design.shape(Design.surface(this),12,this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(b,p);b.setOnClickListener(v->{if(!busy)run.run();});return b;}
    private void primary(String label,Runnable run){Button b=Components.primaryButton(this,label);footer.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->{if(!busy)run.run();});}
    private void line(String title,String subtitle,Runnable run){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(16),dp(16),dp(16));row.setBackground(Design.shape(Design.surface(this),16,this));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(Components.text(this,title,16,Design.text(this),Typeface.BOLD));TextView sub=Components.text(this,subtitle,14,Design.textSoft(this),Typeface.NORMAL);sub.setPadding(0,dp(5),0,0);labels.addView(sub);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(run!=null){LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(20),dp(20));icon.leftMargin=dp(12);row.addView(new NavIcon(this,NavIcon.ICON_CHEVRON,Design.muted(this)),icon);}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(row,p);
        if(run!=null){row.setFocusable(true);row.setContentDescription(title+". "+subtitle);row.setOnClickListener(v->{if(!busy)run.run();});}
    }
    private EditText field(LinearLayout parent,String label,String value,boolean number){
        TextView caption=Components.text(this,label,14,Design.textSoft(this),Typeface.NORMAL);caption.setPadding(0,dp(16),0,dp(8));parent.addView(caption);
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(Design.text(this));e.setHintTextColor(Design.muted(this));e.setHint(number?"0.00":label);e.setContentDescription(label);
        if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(value);e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setMinHeight(dp(54));e.setBackground(Design.strokeShape(Design.surface(this),Design.border(this),12,this));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private EditText input(String key,String label,String value,boolean number){EditText e=field(content,label,value,number);e.setTag("input:"+key);if(key.equals("finalTotal")||key.equals("receiptFinal")){e.setTextSize(32);e.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));e.setMinHeight(dp(76));e.setBackground(Design.strokeShape(Design.surface(this),Design.primary(this),14,this));}fields.put(key,e);return e;}
    private long amount(EditText input){return input.getText().toString().trim().isEmpty()?0:Money.parse(input.getText().toString());}
    private void finalAmountHelp(){TextView help=Components.text(this,"Total bill paid/payable to the restaurant, seller or business after discounts, including tax. Enter the whole bill; individual or partial payments come next.",12,Design.textSoft(this),Typeface.NORMAL);help.setPadding(0,dp(8),0,dp(16));content.addView(help);}
    private EditText taxPercentage(String key,String value,java.util.function.LongSupplier subtotal){
        LinearLayout presets=new LinearLayout(this);Button zero=LedgerUi.chip(this,"No tax · 0%",false),five=LedgerUi.chip(this,"5%",false);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);presets.addView(zero,left);presets.addView(five,new LinearLayout.LayoutParams(0,-2,1));content.addView(presets);
        EditText rate=input(key,"Exact tax percentage (%)",value,true);rate.setHint("Enter % · e.g. 5 or 2.5");
        TextView calculated=Components.text(this,"",12,Design.textSoft(this),Typeface.NORMAL);calculated.setPadding(0,dp(8),0,dp(12));content.addView(calculated);
        Runnable refresh=()->{String text=rate.getText().toString().trim();try{
            boolean isZero=!text.isEmpty()&&new java.math.BigDecimal(text).compareTo(java.math.BigDecimal.ZERO)==0,isFive=!text.isEmpty()&&new java.math.BigDecimal(text).compareTo(new java.math.BigDecimal("5"))==0;
            for(Button b:new Button[]{zero,five}){boolean on=b==zero?isZero:isFive;b.setSelected(on);b.setTextColor(on?Design.onPrimary(this):Design.textSoft(this));b.setBackground(Design.shape(on?Design.primary(this):Design.surface(this),12,this));}
            if(text.isEmpty()){calculated.setText("Choose 0% or 5%, or type the exact rate. Use 0% when item prices already include tax.");calculated.setTextColor(Design.textSoft(this));return;}
            long original=subtotal.getAsLong(),tax=BillSplit.taxAtRate(text,original);calculated.setText(text+"% of "+money(original)+" = "+money(tax));calculated.setTextColor(Design.textSoft(this));
        }catch(Exception e){calculated.setText(e.getMessage());calculated.setTextColor(Design.error(this));}};
        zero.setOnClickListener(v->{if(!busy)rate.setText("0");});five.setOnClickListener(v->{if(!busy)rate.setText("5");});
        rate.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable text){}});
        if(key.equals("receiptTax"))receiptTaxPreview=refresh;refresh.run();return rate;
    }
    private void capture(){
        if(bill==null)return;
        if(step==1&&fields.containsKey("title"))bill.title=fields.get("title").getText().toString().trim();
        if(step==3&&fields.containsKey("finalTotal")){
            bill.tax=fields.get("tax").getText().toString().trim();bill.taxPercent=true;
            bill.finalTotal=fields.get("finalTotal").getText().toString().trim().isEmpty()?-1:amount(fields.get("finalTotal"));
            bill.extra=0;bill.roundOff=0;bill.discount="";bill.discountPercent=false;bill.receiptTotal=bill.finalTotal;
        }
        if(step==6&&receipt!=null&&fields.containsKey("receiptTax"))captureReceipt();
    }
    private void render(boolean motion){
        fields.clear();totalPreview=null;receiptTaxPreview=null;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Design.background(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(6),dp(20),dp(2));
        LinearLayout back=new LinearLayout(this);back.setGravity(Gravity.CENTER);back.setBackground(Design.shape(Design.surface(this),12,this));back.setContentDescription("Go back");back.setFocusable(true);back.addView(new NavIcon(this,NavIcon.ICON_BACK,Design.text(this)),new LinearLayout.LayoutParams(dp(24),dp(24)));top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->back());
        TextView brand=Components.text(this,"Pocket Ledger",15,Design.textSoft(this),Typeface.NORMAL);brand.setPadding(dp(12),0,0,0);top.addView(brand);root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);footer.setPadding(dp(20),dp(10),dp(20),dp(18));root.addView(footer);
        if(bill!=null&&editing&&step!=7){LinearLayout progress=new LinearLayout(this);for(int i=1;i<=5;i++){View segment=new View(this);segment.setBackground(Design.shape(i<=(step==6?2:step)?Design.primary(this):Design.border(this),2,this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(4),1);p.setMarginEnd(dp(5));progress.addView(segment,p);}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(4));p.bottomMargin=dp(22);content.addView(progress,p);}
        if(step==7)friendBook();else if(bill==null)list();else switch(step){case 1:friends();break;case 2:items();break;case 3:charges();break;case 4:payments();break;case 6:reviewReceipt();break;default:result();}
        presenter.show(root,bill==null?"splits:"+step:"split:"+bill.id+":"+step,motion);
    }
    private void next(){attempt(()->{
        capture();if(step==1){BillSplit.require(!bill.title.isEmpty(),"Give this bill a name");BillSplit.require(bill.people.size()>=2,"Add at least one friend");}
        if(step==3)BillSplit.require(!bill.tax.isEmpty(),"Choose No tax (0%), 5%, or enter the exact tax percentage");
        if(step==2||step==3){BillSplit check=BillSplit.read(bill.json());check.payments.clear();if(step==2){check.tax="";check.discount="";check.extra=0;check.roundOff=0;check.finalTotal=-1;}check.calculate();}
        if(step==3||step==4){BillSplit check=BillSplit.read(bill.json());if(step==3)check.payments.clear();checkReceiptTotal(check.calculate());}step++;render(true);
    });}
    @Override public void onBackPressed(){back();}
    private void back(){if(readingReceipt){receiptLoads.next();pendingUpload=null;readingReceipt=false;busy=false;render(false);return;}if(busy)return;attempt(()->{
        capture();if(step==7){step=bill==null?0:1;render(true);return;}if(bill==null){finish();return;}if(step==6){step=2;render(true);return;}if(editing&&step>1){step--;render(true);return;}
        if(editing)confirm("Discard these changes?","This bill has not been saved. Previously saved bills are kept.",()->{bill=null;editing=false;step=0;load();});
        else{bill=null;step=0;load();}
    });}
    private void list(){
        heading("Split bills","Good food. Clear shares.");
        action("Saved friends · "+savedFriends.size(),()->{step=7;render(true);});
        section("Your bills");
        if(bills.isEmpty())line("No bills yet","Upload a bill or enter a few items to start your first split.",null);
        for(BillSplit b:bills){BillSplit.Result r=b.calculate();long pending=0;for(long n:r.balances)if(n>0)pending=Money.add(pending,n);String status=r.restaurantDue>0?"Restaurant due "+b.currency+Money.decimal(r.restaurantDue):pending>0?"Reimbursements pending":"Settled";
            LinearLayout card=Components.bentoBlock(this);card.setPadding(dp(20),dp(20),dp(20),dp(20));
            card.addView(Components.text(this,b.title,20,Design.text(this),Typeface.BOLD));TextView amount=Components.text(this,b.currency+Money.decimal(r.total),32,Design.text(this),Typeface.NORMAL);amount.setPadding(0,dp(12),0,dp(12));card.addView(amount);
            LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.addView(Components.text(this,b.people.size()+" people",14,Design.textSoft(this),Typeface.NORMAL),new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=Components.text(this,status,12,Design.primary(this),Typeface.BOLD);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(Design.shape(Design.primaryMuted(this),10,this));bottom.addView(badge,new LinearLayout.LayoutParams(0,-2,2));card.addView(bottom);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);content.addView(card,p);card.setFocusable(true);card.setContentDescription(b.title+". "+b.currency+Money.decimal(r.total)+". "+status+". Open bill");card.setOnClickListener(v->attempt(()->{bill=BillSplit.read(b.json());step=5;editing=false;render(true);}));}
        primary("New split bill",()->{receipt=null;receiptUri=null;pendingUpload=null;bill=new BillSplit();bill.currency=new ConfigStore(this).currencySymbol();bill.people.add(new BillSplit.Person("self","You"));step=1;editing=true;render(true);});
        action("About saved split bills",()->LedgerUi.dialog(this).setTitle("Saved on this phone").setMessage("Split bills and saved friends stay separate from expenses, income and balances. They are stored only on this phone and are not included in JSON backups, imports or Sheets sync.").setPositiveButton("OK",null).show());
    }
    private void friends(){
        heading("Who joined you?","Pick today’s group from your saved friends.");input("title","Bill name",bill.title,false);
        section("Today’s group · "+bill.people.size());
        for(BillSplit.Person p:bill.people)if(BillSplit.friendKey(p.name).equals("you"))line(p.name,"Included in this bill",null);
        if(savedFriends.isEmpty())note("Add your friends once. Their names will be here for every outing.");
        for(BillSplit.Person friend:savedFriends){BillSplit.Person selected=selectedFriend(friend);friendSelection(friend,selected!=null,()->attempt(()->{capture();if(selected==null){BillSplit.require(bill.people.size()<30,"A bill supports up to 30 people");bill.people.add(new BillSplit.Person(friend.id,friend.name));}else removeParticipant(selected);render(false);}));}
        for(BillSplit.Person person:new ArrayList<>(bill.people))if(!BillSplit.friendKey(person.name).equals("you")&&savedFriends.stream().noneMatch(f->f.id.equals(person.id)||BillSplit.friendKey(f.name).equals(BillSplit.friendKey(person.name))))line(person.name,"This bill only · Tap to remove",()->attempt(()->{capture();removeParticipant(person);render(false);}));
        action("Add a new friend",()->attempt(()->{capture();savedFriendDialog(null);}));action("Manage saved friends",()->attempt(()->{capture();step=7;render(true);}));primary("Next · Add items",this::next);
    }
    private BillSplit.Person selectedFriend(BillSplit.Person friend){for(BillSplit.Person p:bill.people)if(p.id.equals(friend.id)||BillSplit.friendKey(p.name).equals(BillSplit.friendKey(friend.name)))return p;return null;}
    private void removeParticipant(BillSplit.Person person){for(BillSplit.Item item:bill.items)BillSplit.require(!item.portions.containsKey(person.id),"Remove this friend from their items first");for(BillSplit.Payment p:bill.payments)BillSplit.require(!p.from.equals(person.id)&&!p.to.equals(person.id),"Undo this friend’s payment records first");bill.people.remove(person);}
    private void friendSelection(BillSplit.Person friend,boolean selected,Runnable run){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(Design.strokeShape(selected?Design.primaryMuted(this):Design.surface(this),selected?Design.primary(this):Design.border(this),14,this));
        TextView avatar=Components.text(this,friend.name.substring(0,1).toUpperCase(Locale.ROOT),18,Design.primary(this),Typeface.BOLD);avatar.setGravity(Gravity.CENTER);avatar.setBackground(Design.shape(Design.surface(this),22,this));row.addView(avatar,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView name=Components.text(this,friend.name,16,Design.text(this),Typeface.BOLD);name.setPadding(dp(12),0,dp(8),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox check=new CheckBox(this);check.setChecked(selected);check.setButtonTintList(android.content.res.ColorStateList.valueOf(Design.primary(this)));check.setClickable(false);check.setFocusable(false);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(check);
        row.setFocusable(true);row.setSelected(selected);row.setContentDescription(friend.name+(selected?". Selected. Tap to remove from this bill":". Not selected. Tap to include"));row.setOnClickListener(v->{if(!busy)run.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(row,p);
    }
    private void friendBook(){
        heading("Saved friends","Add once. Pick them for any future bill.");
        if(savedFriends.isEmpty())line("Your group starts here","Names from saved bills are brought here automatically.",null);
        for(BillSplit.Person friend:savedFriends)line(friend.name,"Saved friend · Tap to manage",()->savedFriendDialog(friend));
        primary("Add friend",()->savedFriendDialog(null));
    }
    private void savedFriendDialog(BillSplit.Person old){
        LinearLayout body=dialogBody();EditText name=field(body,"Friend name",old==null?"":old.name,false);String id=old==null?UUID.randomUUID().toString():old.id;
        AlertDialog dialog=form(old==null?"Save a friend":"Edit saved friend",body,"Save friend",()->{
            String value=name.getText().toString().trim();BillSplit.friendKey(value);
            if(old==null&&bill!=null&&step==1)BillSplit.require(bill.people.size()<30,"A bill supports up to 30 people");
            work(()->{store.saveFriend(id,value);savedFriends=store.friends();},()->{if(old==null&&bill!=null&&step==1&&bill.people.stream().noneMatch(p->BillSplit.friendKey(p.name).equals(BillSplit.friendKey(value))))bill.people.add(new BillSplit.Person(id,value));render(false);});
        });
        if(old!=null){Button remove=Components.textButton(this,"Remove saved friend");remove.setTextColor(Design.error(this));body.addView(remove);remove.setOnClickListener(v->confirm("Remove from saved friends?","Existing bills and their shares stay as they are.",()->{dialog.dismiss();work(()->{store.deleteFriend(old.id);savedFriends=store.friends();},()->render(false));}));}
    }
    private LinearLayout dialogBody(){LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),0,dp(24),dp(20));return body;}
    private AlertDialog form(String title,LinearLayout body,String save,Work task){
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog d=LedgerUi.dialog(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(save,null).create();d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->attempt(()->{task.run();d.dismiss();}));return d;
    }
    private void items(){
        heading("Who had what?","Enter each item’s full line total, then select who shared it.");
        action("Upload bill · Image or PDF",this::pickReceipt);
        long total=0;for(BillSplit.Item item:bill.items){total=Money.add(total,item.amount);StringJoiner people=new StringJoiner(", ");for(BillSplit.Person p:bill.people)if(item.portions.getOrDefault(p.id,0L)>0)people.add(p.name);
            line(item.name+" · "+money(item.amount),people.length()==0?"Choose who had this item":people.toString()+" · Tap to edit",()->itemDialog(item));}
        if(bill.items.isEmpty())note("For example: Pizza · 600 · You + Rahul");else note("Food subtotal · "+money(total));
        action("Add item",()->itemDialog(null));primary("Next · Tax & discount",this::next);
    }
    private void itemDialog(BillSplit.Item old){
        LinearLayout body=dialogBody();EditText name=field(body,"Item name",old==null?"":old.name,false),price=field(body,"Full line total",old==null?"":Money.decimal(old.amount),true);
        TextView help=Components.text(this,"Who shared this? Keep portions at 1 for an equal split, or use 2 for twice the share.",14,Design.textSoft(this),Typeface.NORMAL);help.setPadding(0,dp(18),0,dp(8));body.addView(help);
        List<EditText> weights=new ArrayList<>();List<CheckBox> selected=new ArrayList<>();
        for(BillSplit.Person p:bill.people){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,dp(6));
            CheckBox check=new CheckBox(this);check.setText(p.name);check.setTextSize(16);check.setTextColor(Design.text(this));check.setMinHeight(dp(48));check.setButtonTintList(android.content.res.ColorStateList.valueOf(Design.primary(this)));check.setChecked(old==null||old.portions.getOrDefault(p.id,0L)>0);row.addView(check,new LinearLayout.LayoutParams(0,-2,1));selected.add(check);
            EditText portion=new EditText(this);portion.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);portion.setTextSize(16);portion.setTextColor(Design.text(this));portion.setText(old==null||old.portions.getOrDefault(p.id,0L)==0?"1":Money.decimal(old.portions.get(p.id)));portion.setContentDescription(p.name+" portions");portion.setSingleLine(true);portion.setPadding(dp(12),dp(10),dp(12),dp(10));portion.setBackground(Design.strokeShape(Design.surface(this),Design.border(this),12,this));portion.setEnabled(check.isChecked());row.addView(portion,new LinearLayout.LayoutParams(dp(88),dp(48)));weights.add(portion);check.setOnCheckedChangeListener((button,on)->portion.setEnabled(on));body.addView(row);
        }
        AlertDialog d=form(old==null?"Add item":"Edit item",body,"Save item",()->{
            String title=name.getText().toString().trim();long n=amount(price);BillSplit.require(!title.isEmpty()&&n>0,"Enter an item name and a positive line total");
            BillSplit.Item item=new BillSplit.Item(title,n);long[] portions=new long[weights.size()];
            for(int i=0;i<portions.length;i++){portions[i]=selected.get(i).isChecked()?amount(weights.get(i)):0;BillSplit.require(!selected.get(i).isChecked()||portions[i]>0,"Selected people need a positive portion");if(portions[i]>0)item.portions.put(bill.people.get(i).id,portions[i]);}
            BillSplit.allocate(n,portions);if(old==null)bill.items.add(item);else bill.items.set(bill.items.indexOf(old),item);render(false);
        });
        if(old!=null){Button remove=Components.textButton(this,"Remove item");body.addView(remove);remove.setOnClickListener(v->{bill.items.remove(old);d.dismiss();render(false);});}
    }
    private void charges(){
        heading("Final bill amount","Enter the whole bill’s final amount after discount. We’ll work out the saving.");
        long food=0;for(BillSplit.Item item:bill.items)food=Money.add(food,item.amount);line("Items subtotal",money(food),null);
        section("Tax");
        String rate=bill.taxPercent?bill.tax:"0";
        if(!bill.taxPercent&&!bill.tax.trim().isEmpty())try{long previous=Money.parse(bill.tax);rate=food==0?"0":java.math.BigDecimal.valueOf(previous).multiply(new java.math.BigDecimal("100")).divide(java.math.BigDecimal.valueOf(food),4,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();if(BillSplit.taxAtRate(rate,food)!=previous)rate="";}catch(Exception ignored){rate="";}
        final long subtotal=food;EditText tax=taxPercentage("tax",rate,()->subtotal);
        String finalValue=bill.finalTotal>=0?Money.decimal(bill.finalTotal):"";
        if(finalValue.isEmpty()&&(!bill.discount.isEmpty()||bill.extra!=0||bill.roundOff!=0))try{finalValue=Money.decimal(withoutPayments().total);}catch(Exception ignored){}
        EditText total=input("finalTotal","Final bill amount · "+bill.currency,finalValue,true);total.setHint("No discount? Leave blank");
        finalAmountHelp();
        LinearLayout summary=Components.bentoBlock(this);summary.setPadding(dp(18),dp(18),dp(18),dp(18));TextView summaryText=Components.text(this,"",16,Design.text(this),Typeface.NORMAL);summary.addView(summaryText);content.addView(summary);
        totalPreview=()->{try{BillSplit preview=BillSplit.read(bill.json());preview.payments.clear();preview.tax=tax.getText().toString().trim();preview.taxPercent=true;preview.discount="";preview.discountPercent=false;preview.extra=0;preview.roundOff=0;preview.finalTotal=total.getText().toString().trim().isEmpty()?-1:amount(total);BillSplit.Result r=preview.calculate();summaryText.setText("Tax   "+money(r.tax)+"\nBefore discount   "+money(Money.add(r.subtotal,r.tax))+"\nDiscount saved   "+money(r.discount)+"\nFinal bill   "+money(r.total));summaryText.setTextColor(Design.text(this));}catch(Exception e){summaryText.setText(e.getMessage());summaryText.setTextColor(Design.error(this));}};
        android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(totalPreview!=null)totalPreview.run();}public void afterTextChanged(android.text.Editable text){}};
        tax.addTextChangedListener(watcher);total.addTextChangedListener(watcher);totalPreview.run();
        primary("Next · Who paid?",this::next);
    }
    private void payments(){
        heading("Who paid the restaurant?","Record only actual restaurant payments here. You can leave an unpaid amount and add it later.");
        BillSplit.Result r=withoutPayments();long paid=0;for(BillSplit.Payment p:bill.payments)if(p.to.isEmpty())paid=Money.add(paid,p.amount);
        line("Final bill",money(r.total),null);line(paid>r.total?"Overpaid · remove a payment":"Restaurant due",money(Math.abs(r.total-paid)),null);
        paymentRows();if(paid<r.total)action("Add restaurant payment",()->paymentDialog(null,false));
        primary("Review split",this::next);
    }
    private BillSplit.Result withoutPayments(){try{BillSplit copy=BillSplit.read(bill.json());copy.payments.clear();return copy.calculate();}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}
    private void paymentRows(){
        for(BillSplit.Payment p:bill.payments)line(bill.name(p.from)+" → "+bill.name(p.to),money(p.amount)+" · Tap to undo",()->confirm("Undo this payment?","This only removes the payment record from this split bill.",()->changePayments(()->bill.payments.remove(p))));
    }
    private void result(){
        BillSplit.Result r=bill.calculate();heading(bill.title,"Everyone’s share, in one place.");
        LinearLayout statement=Components.bentoBlock(this);statement.setPadding(dp(22),dp(22),dp(22),dp(22));statement.addView(Components.text(this,"Final bill",14,Design.textSoft(this),Typeface.NORMAL));TextView finalAmount=Components.text(this,money(r.total),38,Design.text(this),Typeface.NORMAL);finalAmount.setPadding(0,dp(12),0,dp(16));statement.addView(finalAmount);statement.addView(Components.hairline(this));TextView facts=Components.text(this,bill.people.size()+" people · Saved "+money(r.discount),14,Design.primary(this),Typeface.BOLD);facts.setPadding(0,dp(16),0,0);statement.addView(facts);content.addView(statement);section("Individual shares");
        for(int i=0;i<bill.people.size();i++){
            final int at=i;String status=r.restaurantDue>0?"Paid restaurant "+money(r.paid[i]):r.balances[i]>0?"To receive "+money(r.balances[i]):r.balances[i]<0?"To pay "+money(-r.balances[i]):"Settled";
            line(bill.people.get(i).name+" · "+money(r.shares[i]),status+" · View breakdown",()->LedgerUi.dialog(this).setTitle(bill.people.get(at).name).setMessage("Food: "+money(r.food[at])+"\nTax: "+money(r.taxes[at])+"\nDiscount: −"+money(r.discounts[at])+(r.extras[at]+r.rounding[at]==0?"":"\nEarlier saved adjustment: "+money(r.extras[at]+r.rounding[at]))+"\nFinal share: "+money(r.shares[at])+"\nPaid restaurant: "+money(r.paid[at])).setPositiveButton("OK",null).show());
        }
        if(editing){note("Review the shares before saving. This creates a split bill only.");primary("Save split bill",()->attempt(()->{checkReceiptTotal(r);work(()->store.save(bill),()->{editing=false;render(false);});}));return;}
        section("Restaurant");line(r.restaurantDue>0?"Still to pay":"Fully paid",money(r.restaurantDue),null);
        if(r.restaurantDue>0){note("Reimbursement suggestions appear once the restaurant is fully paid.");primary("Add restaurant payment",()->paymentDialog(null,false));}
        section("Settle between friends");
        if(r.restaurantDue==0){
            if(r.transfers.isEmpty())note("All settled. No payments left between friends.");
            for(BillSplit.Transfer transfer:r.transfers)action(bill.name(transfer.from)+" → "+bill.name(transfer.to)+" · "+money(transfer.amount),()->paymentDialog(transfer,true));
        }
        action("Record a payment between friends",()->paymentDialog(null,true));
        section("Recorded payments");if(bill.payments.isEmpty())note("No payments recorded yet.");else paymentRows();
        action("Share summary",()->share(r));
        action("Edit bill",()->{if(bill.hasSettlements()){error(new IllegalArgumentException("Undo friend payment records before changing the bill’s shares. Restaurant payments are kept."));return;}editing=true;step=1;render(true);});
        action("Delete split bill",()->confirm("Delete this split bill?","Its items and payment records will be deleted. Your Pocket Ledger transactions are unaffected.",()->work(()->{store.delete(bill.id);bills=store.all();},()->{bill=null;step=0;render(true);}))).setTextColor(Design.error(this));
    }
    private void changePayments(Runnable mutation){attempt(()->{
        BillSplit before=BillSplit.read(bill.json());mutation.run();
        try{if(editing)withoutPayments();else bill.calculate();}catch(Exception e){bill=before;throw e;}
        if(editing){render(false);return;}
        work(()->{try{store.save(bill);}catch(Exception e){bill=before;throw e;}},()->render(false));
    });}
    private void paymentDialog(BillSplit.Transfer suggested,boolean friends){attempt(()->{
        BillSplit.Result r=bill.calculate();LinearLayout body=dialogBody();List<String> names=new ArrayList<>();for(BillSplit.Person p:bill.people)names.add(p.name);
        TextView fromLabel=Components.text(this,"Paid by",14,Design.textSoft(this),Typeface.NORMAL);fromLabel.setPadding(0,dp(12),0,dp(8));body.addView(fromLabel);
        LedgerChoice from=new LedgerChoice(this,"Paid by",names,suggested==null?0:bill.index(suggested.from));body.addView(from);
        LedgerChoice to;
        if(friends){TextView l=Components.text(this,"Received by",14,Design.textSoft(this),Typeface.NORMAL);l.setPadding(0,dp(16),0,dp(8));body.addView(l);to=new LedgerChoice(this,"Received by",names,suggested==null?1:bill.index(suggested.to));body.addView(to);}else to=null;
        EditText amount=field(body,"Amount actually paid",suggested==null?(friends?"":Money.decimal(r.restaurantDue)):Money.decimal(suggested.amount),true);
        form(friends?"Record friend payment":"Restaurant payment",body,"Record payment",()->{
            long value=amount(amount);BillSplit.require(value>0,"Enter an amount greater than zero");
            String sender=bill.people.get(from.getSelectedItemPosition()).id,receiver=friends?bill.people.get(to.getSelectedItemPosition()).id:"";
            BillSplit.require(!sender.equals(receiver),"Sender and receiver must be different");
            if(!friends)BillSplit.require(value<=r.restaurantDue,"Payment exceeds the remaining restaurant bill");
            changePayments(()->bill.payments.add(new BillSplit.Payment(sender,receiver,value)));
        });
    });}
    private void checkReceiptTotal(BillSplit.Result r){BillSplit.require(bill.receiptTotal<0||bill.receiptTotal==r.total,"Check the final bill amount. Calculated total "+money(r.total)+" differs from "+money(bill.receiptTotal)+".");}
    private void pickReceipt(){
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp","application/pdf"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,PICK_RECEIPT);}catch(ActivityNotFoundException e){error(new IllegalArgumentException("No file picker is available. You can still add items manually."));}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK_RECEIPT&&result==RESULT_OK&&data!=null&&data.getData()!=null&&bill!=null&&editing){
            pendingUpload=data.getData().toString();if(!busy)readReceipt(data.getData());
        }
    }
    private void readReceipt(android.net.Uri uri){
        note("Reading bill on this device… Back to cancel.");
        final int request=receiptLoads.next();readingReceipt=true;busy=true;content.setAlpha(.6f);
        executor.execute(()->{
            if(!receiptLoads.current(request))return;
            try{ReceiptParser.Draft result=ReceiptParser.parse(ReceiptReader.read(this,uri));
                runOnUiThread(()->{if(!receiptLoads.current(request)||isDestroyed()||isFinishing())return;pendingUpload=null;busy=false;readingReceipt=false;receiptUri=uri.toString();receipt=result;step=6;render(true);});
            }catch(Exception e){runOnUiThread(()->{if(!receiptLoads.current(request)||isDestroyed()||isFinishing())return;pendingUpload=null;busy=false;readingReceipt=false;render(false);error(e);});}
        });
    }
    private void captureReceipt(){
        long total=fields.get("receiptFinal").getText().toString().trim().isEmpty()?-1:amount(fields.get("receiptFinal"));
        receipt.taxRate=fields.get("receiptTax").getText().toString().trim();receipt.total=total;
    }
    private void reviewReceipt(){
        if(receipt==null){step=2;items();return;}
        heading("Review uploaded bill","Check the detected items and amounts. Nothing is added until you confirm.");
        if(receiptUri!=null)action("View uploaded file",()->{
            android.net.Uri uri=android.net.Uri.parse(receiptUri);String type=getContentResolver().getType(uri);
            try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,type==null?"*/*":type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){error(new IllegalArgumentException("This file cannot be previewed. You can review the recognized text below."));}
        });
        action("Review recognized text",()->attempt(()->{captureReceipt();LinearLayout body=dialogBody();EditText raw=field(body,"Recognized text",receipt.raw,false);raw.setSingleLine(false);raw.setMinLines(5);raw.setMaxLines(14);raw.setGravity(Gravity.TOP);raw.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            form("Recognized bill text",body,"Re-read edited text",()->{BillSplit.require(raw.length()<=50000,"Keep the text under 50,000 characters");receipt=ReceiptParser.parse(raw.getText().toString());render(false);});}));
        if(receipt.items.isEmpty())note("No item rows could be identified. Edit the recognized text, try another file, or go back and add items manually.");
        for(ReceiptParser.Candidate candidate:receipt.items){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(Design.shape(Design.surface(this),14,this));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);content.addView(row,p);
            CheckBox selected=new CheckBox(this);selected.setText(candidate.name+"\n"+money(candidate.amount));selected.setTextColor(Design.text(this));selected.setTextSize(16);selected.setMinHeight(dp(56));selected.setButtonTintList(android.content.res.ColorStateList.valueOf(Design.primary(this)));selected.setChecked(candidate.selected);selected.setOnCheckedChangeListener((v,on)->{candidate.selected=on;if(receiptTaxPreview!=null)receiptTaxPreview.run();});row.addView(selected,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=Components.textButton(this,"Edit");edit.setContentDescription("Edit detected item "+candidate.name);row.addView(edit);edit.setOnClickListener(v->attempt(()->{
                captureReceipt();LinearLayout body=dialogBody();EditText name=field(body,"Item name",candidate.name,false),price=field(body,"Full line total",Money.decimal(candidate.amount),true);
                form("Correct detected item",body,"Apply correction",()->{String label=name.getText().toString().trim();long n=amount(price);BillSplit.require(!label.isEmpty()&&n>0,"Enter an item name and a positive amount");candidate.name=label;candidate.amount=n;render(false);});
            }));
        }
        section("Confirm the tax rate");taxPercentage("receiptTax",receipt.taxRate,()->{long n=0;for(ReceiptParser.Candidate c:receipt.items)if(c.selected)n=Money.add(n,c.amount);return n;});
        input("receiptFinal","Final bill amount · "+bill.currency,receipt.total<0?"":Money.decimal(receipt.total),true);
        finalAmountHelp();
        action("Choose another file",()->attempt(()->{captureReceipt();pickReceipt();}));
        primary(bill.items.isEmpty()?"Import selected items":"Replace items with this bill",()->attempt(()->{
            captureReceipt();List<BillSplit.Item> imported=new ArrayList<>();long subtotal=0;
            for(ReceiptParser.Candidate c:receipt.items)if(c.selected){BillSplit.require(!c.name.trim().isEmpty()&&c.amount>0,"Correct the selected items first");subtotal=Money.add(subtotal,c.amount);imported.add(new BillSplit.Item(c.name,c.amount));}
            BillSplit.require(!imported.isEmpty(),"Select at least one detected item, or go back and add items manually");
            BillSplit.require(!receipt.taxRate.isEmpty(),"Choose No tax (0%), 5%, or enter the exact tax percentage");
            long confirmedTax=BillSplit.taxAtRate(receipt.taxRate,subtotal);
            BillSplit.require(receipt.total>=0,"Enter the final bill amount shown on the receipt");
            BillSplit.require(receipt.total<=Money.add(subtotal,confirmedTax),"Final bill exceeds the selected items plus tax. Check the extracted items, tax percentage and final amount.");
            Runnable apply=()->{bill.items.clear();bill.items.addAll(imported);bill.tax=receipt.taxRate;bill.taxPercent=true;bill.discount="";bill.discountPercent=false;bill.extra=0;bill.roundOff=0;bill.finalTotal=receipt.total;bill.receiptTotal=receipt.total;step=2;render(true);};
            if(bill.items.isEmpty())apply.run();else confirm("Replace existing items?","The current items and final amount will be replaced. Existing payment records are kept for your review.",apply);
        }));
    }
    private void share(BillSplit.Result r){
        StringBuilder text=new StringBuilder(bill.title).append("\nBill: ").append(money(r.total));
        for(int i=0;i<bill.people.size();i++)text.append("\n").append(bill.people.get(i).name).append(": ").append(money(r.shares[i]));
        text.append("\nRestaurant due: ").append(money(r.restaurantDue));
        for(BillSplit.Transfer t:r.transfers)text.append("\n").append(bill.name(t.from)).append(" → ").append(bill.name(t.to)).append(": ").append(money(t.amount));
        text.append("\n— Pocket Ledger · Split bills");startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text.toString()),"Share split summary"));
    }
}
