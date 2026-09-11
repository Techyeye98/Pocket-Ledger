package com.example.nativeexpenseentry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QuickEntryActivity extends Activity {
    private static final String STATE_AMOUNT="amount", STATE_REMARK="remark", STATE_CATEGORY="category", STATE_PAYMENT="payment", STATE_SAVING="saving";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store; private EditText amount, remark; private TextView category, payment, error; private Button save;
    private String chosenCategory, chosenPayment; private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new ConfigStore(this); NotificationHelper.prepare(this);
        // Restore saving flag first so UI reflects the correct state if recreated mid-request
        saving = state != null && state.getBoolean(STATE_SAVING, false);
        chosenCategory = state == null ? initial(store.categories(), store.defaultCategory(), store.lastCategory()) : state.getString(STATE_CATEGORY, "");
        chosenPayment = state == null ? initial(store.paymentModes(), store.defaultPaymentMode(), store.lastPaymentMode()) : state.getString(STATE_PAYMENT, "");
        build(state); configureWindow();
        // If recreated during a save, reflect the disabled save-button state
        if (saving && save != null) { save.setEnabled(false); save.setText("Saving…"); }
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString(STATE_AMOUNT, amount == null ? "" : amount.getText().toString()); out.putString(STATE_REMARK, remark == null ? "" : remark.getText().toString()); out.putString(STATE_CATEGORY, chosenCategory); out.putString(STATE_PAYMENT, chosenPayment); out.putBoolean(STATE_SAVING, saving); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    private String initial(List<String> list, String preferred, String recent) { return list.contains(preferred) ? preferred : (list.contains(recent) ? recent : (list.isEmpty() ? "" : list.get(0))); }
    private void configureWindow() { Window w=getWindow(); w.setBackgroundDrawable(Design.shape(this, Design.surface(this), 24, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); WindowManager.LayoutParams lp=w.getAttributes(); lp.gravity=Gravity.BOTTOM; lp.dimAmount=.40f; w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); w.setAttributes(lp); }

    private void build(Bundle state) {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(10),dp(20),dp(20)); page.setBackground(Design.shape(this, Design.surface(this), 24, 0)); scroll.addView(page);
        
        TextView handle=Components.text(this, "", 0, Design.muted(this), Typeface.NORMAL); handle.setBackground(Design.shape(this,Design.border(this),3,0)); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(36),dp(4)); hp.gravity=Gravity.CENTER_HORIZONTAL; hp.setMargins(0,dp(2),0,dp(18)); page.addView(handle,hp);
        
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); 
        TextView title=Components.text(this, "Add an expense",24,Design.text(this),Typeface.BOLD); header.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); 
        Button cancel=Components.textButton(this, "Cancel"); cancel.setContentDescription("Cancel expense entry"); cancel.setOnClickListener(v->finish()); header.addView(cancel,new LinearLayout.LayoutParams(dp(72),dp(48))); 
        page.addView(header);
        
        TextView amtLabel = Components.subtitle(this, "AMOUNT"); amtLabel.setPadding(0, dp(20), 0, dp(8)); page.addView(amtLabel); 
        LinearLayout amountBox=new LinearLayout(this); amountBox.setGravity(Gravity.CENTER_VERTICAL); amountBox.setPadding(dp(16),0,dp(16),0); amountBox.setBackground(Design.shape(this,Design.surfaceAlt(this),14,Design.border(this))); 
        TextView rupee=Components.text(this, "₹",26,Design.text(this),Typeface.BOLD); amountBox.addView(rupee); 
        amount=new EditText(this); amount.setHint("0.00"); amount.setHintTextColor(Design.muted(this)); amount.setTextColor(Design.text(this)); amount.setTextSize(40); amount.setTypeface(Typeface.DEFAULT,Typeface.BOLD); amount.setSingleLine(true); amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); amount.setBackgroundColor(android.graphics.Color.TRANSPARENT); amount.setPadding(dp(10),0,0,0); amount.setContentDescription("Amount in rupees"); if(state!=null)amount.setText(state.getString(STATE_AMOUNT,"")); amountBox.addView(amount,new LinearLayout.LayoutParams(0,dp(64),1)); 
        page.addView(amountBox,wide(ViewGroup.LayoutParams.WRAP_CONTENT));
        
        TextView catLabel = Components.subtitle(this, "CATEGORY"); catLabel.setPadding(0, dp(20), 0, dp(8)); page.addView(catLabel); 
        category=select(chosenCategory,"Choose category"); category.setOnClickListener(v->pick("Choose category",store.categories(),true)); page.addView(category,wide(dp(52)));
        
        TextView payLabel = Components.subtitle(this, "PAYMENT MODE"); payLabel.setPadding(0, dp(16), 0, dp(8)); page.addView(payLabel); 
        payment=select(chosenPayment,"Choose payment mode"); payment.setOnClickListener(v->pick("Choose payment mode",store.paymentModes(),false)); page.addView(payment,wide(dp(52)));
        
        TextView noteLabel = Components.subtitle(this, "NOTE · OPTIONAL"); noteLabel.setPadding(0, dp(16), 0, dp(8)); page.addView(noteLabel); 
        remark=new EditText(this); remark.setHint("What was this for?"); remark.setHintTextColor(Design.muted(this)); remark.setTextColor(Design.text(this)); remark.setTextSize(16); remark.setMinLines(2); remark.setMaxLines(3); remark.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE); remark.setPadding(dp(16),dp(12),dp(16),dp(12)); remark.setBackground(Design.shape(this,Design.surfaceAlt(this),14,Design.border(this))); if(state!=null)remark.setText(state.getString(STATE_REMARK,"")); page.addView(remark,wide(ViewGroup.LayoutParams.WRAP_CONTENT));
        
        error=Components.text(this, "",13,Design.error(this),Typeface.NORMAL); error.setVisibility(View.GONE); error.setPadding(0,dp(10),0,0); page.addView(error);
        TextView note=Components.text(this, "Saved only after your Google Sheet confirms it.",13,Design.muted(this),Typeface.NORMAL); note.setPadding(0,dp(14),0,dp(14)); page.addView(note);
        
        save=Components.primaryButton(this, "Review & save"); save.setOnClickListener(v->review()); page.addView(save,wide(dp(52))); 
        setContentView(scroll);
        
        amount.requestFocus(); amount.postDelayed(()->((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(amount,InputMethodManager.SHOW_IMPLICIT),180);
    }

    private void pick(String title,List<String> options,boolean isCategory) { if(options.isEmpty()){showError("Add an option in Settings first.");return;} new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]),options.indexOf(isCategory?chosenCategory:chosenPayment),(d,which)->{if(isCategory){chosenCategory=options.get(which);category.setText(chosenCategory);}else{chosenPayment=options.get(which);payment.setText(chosenPayment);}d.dismiss();}).setNegativeButton("Cancel",null).show(); }
    
    private void review() { 
        String normalized; 
        try { normalized=AmountUtil.normalize(amount.getText().toString()); } catch(Exception ex) { showError("Enter an amount greater than zero."); amount.requestFocus(); return; } 
        if(!store.categories().contains(chosenCategory)||!store.paymentModes().contains(chosenPayment)){showError("Choose a valid category and payment mode.");return;} 
        if(store.url().isEmpty()){showError("Add your Apps Script Web App URL in Settings before saving.");return;} 
        hideError(); 
        String summary="₹"+normalized+"\n"+chosenCategory+" · "+chosenPayment+(remark.getText().toString().trim().isEmpty()?"":"\n“"+remark.getText().toString().trim()+"”"); 
        new AlertDialog.Builder(this).setTitle("Save this expense?").setMessage(summary).setNegativeButton("Edit",null).setPositiveButton("Save",(d,w)->submit(normalized)).show(); 
    }
    
    private void submit(String normalized) { 
        if(saving)return; saving=true; save.setEnabled(false); save.setText("Saving…"); save.performHapticFeedback(HapticFeedbackConstants.CONFIRM); 
        final ExpensePayload payload=new ExpensePayload(store.backendValue(chosenCategory),normalized,store.backendValue(chosenPayment),remark.getText().toString().trim(),store.deviceName()); 
        executor.execute(()->{
            ApiClient.Result result=ApiClient.submit(store.url(),payload);
            runOnUiThread(()->{
                saving=false;save.setEnabled(true);save.setText("Review & save");
                store.recordOperation("Expense submission",result.success,result.message);
                if(result.success){
                    store.recordSelection(chosenCategory,chosenPayment);
                    NotificationHelper.saved(this);
                    Components.customToast(this, "Expense saved", false);
                    finish();
                } else {
                    showError("Unable to save expense. "+result.message);
                }
            });
        }); 
    }
    
    private void showError(String message){error.setText(message);error.setVisibility(View.VISIBLE);} 
    private void hideError(){error.setVisibility(View.GONE);}
    
    private TextView select(String value,String description){
        TextView v=Components.text(this, value.isEmpty()?description:value,16,Design.text(this),Typeface.NORMAL);
        v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),0,dp(16),0);
        v.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.arrow_down_float,0);
        v.setBackground(Design.shape(this,Design.surfaceAlt(this),14,Design.border(this)));
        v.setContentDescription(description);
        Components.addPressAnimation(v);
        return v;
    }
    
    private LinearLayout.LayoutParams wide(int height){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height);} 
    private int dp(int value){return Design.dp(this,value);}
}
