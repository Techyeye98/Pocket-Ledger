package com.example.nativeexpenseentry;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    public static final String ACTION_ADD_EXPENSE = "com.example.nativeexpenseentry.ADD_EXPENSE";
    public static final String EXTRA_OPEN_SETTINGS = "open_settings";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    private int pad;
    // Track which screen is showing so system Back knows where to go
    private enum Screen { HOME, SETTINGS }
    private Screen currentScreen = Screen.HOME;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new ConfigStore(this); pad = Design.dp(this, 20); NotificationHelper.prepare(this);
        applyWindowTheme();
        if (ACTION_ADD_EXPENSE.equals(getIntent().getAction())) { startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE)); finish(); return; }
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings(); else showHome();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (ACTION_ADD_EXPENSE.equals(intent.getAction())) startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE)); else if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings(); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    @Override public void onBackPressed() {
        // If Settings is showing, system Back returns to Home — not exit
        if (currentScreen == Screen.SETTINGS) { showHome(); return; }
        super.onBackPressed();
    }

    private void applyWindowTheme() {
        int bg = Design.background(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (Design.dark(this)) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (Design.dark(this)) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private void showHome() {
        currentScreen = Screen.HOME;
        applyWindowTheme();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Design.background(this));
        LinearLayout page = page();
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header(page,"Pocket Ledger","",false);
        TextView hero=Components.text(this, "Your day, clearly tracked.", 24, Design.text(this), Typeface.BOLD); hero.setPadding(0, dp(24), 0, dp(24)); page.addView(hero);
        if ("Expense submission".equals(store.lastOperation()) && !store.lastSuccess()) { 
            Button retry=Components.secondaryButton(this, "Last save didn't sync · Tap to retry"); retry.setTextColor(Design.error(this)); retry.setOnClickListener(v->showSettings()); 
            LinearLayout.LayoutParams lp = match(); lp.setMargins(0,0,0,dp(16)); page.addView(retry, lp); 
        }
        Button add=Components.primaryButton(this, "Add expense"); add.setOnClickListener(v -> startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE))); page.addView(add,match());
        setContentView(scroll);
    }

    private void showSettings() {
        currentScreen = Screen.SETTINGS;
        applyWindowTheme();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Design.background(this));
        LinearLayout page = page();
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header(page, "Settings", "", true); 
        
        section(page, "CONNECTION"); 
        settingRow(page, "Apps Script Web App URL", connectionStateText(), true, connectionStateColor(), this::editUrl); 
        settingRow(page, "Test Connection", "Checks HTTPS reachability", true, 0, this::testConnection);
        
        section(page, "DEVICE"); 
        settingRow(page, "Device Name", store.deviceName(), true, 0, this::editDevice);
        
        section(page, "APPEARANCE"); 
        settingRow(page, "Theme", themeName(), true, 0, this::chooseTheme);
        
        section(page, "CATEGORIES"); 
        settingRow(page, "Manage Categories", store.categories().size() == 0 ? "Not set up yet — tap to add" : store.categories().size() + " configured", true, 0, () -> manage("Categories", true));
        
        section(page, "PAYMENT MODES"); 
        settingRow(page, "Manage Payment Modes", store.paymentModes().size() == 0 ? "Not set up yet — tap to add" : store.paymentModes().size() + " configured", true, 0, () -> manage("Payment Modes", false));
        
        section(page, "ENTRY PREFERENCES"); 
        settingRow(page, "Default Category", displayDefault(store.defaultCategory()), true, 0, this::chooseDefaultCategory); 
        settingRow(page, "Default Payment Mode", displayDefault(store.defaultPaymentMode()), true, 0, this::chooseDefaultPayment);
        
        section(page, "SUPPORT & DIAGNOSTICS"); 
        settingRow(page,"Diagnostics","Copy a safe report",true,0,this::showDiagnostics);
        
        section(page, "ABOUT"); 
        TextView about = Components.text(this, "Pocket Ledger · " + appVersion() + "\nNo background monitoring or scheduled sync is used.", 13, Design.muted(this), Typeface.NORMAL); 
        page.addView(about); 
        setContentView(scroll);
    }

    private String displayDefault(String value) { return value.isEmpty() ? "No default" : value; }
    private String connectionStateText() {
        String url = store.url();
        if (url.isEmpty()) return "Not configured";
        if (!url.isEmpty() && url.equals(store.verifiedUrl())) return "Connected";
        return "Configured · not yet tested";
    }
    private int connectionStateColor() {
        String url = store.url();
        if (url.isEmpty()) return Design.warning(this);
        if (!url.isEmpty() && url.equals(store.verifiedUrl())) return Design.success(this);
        return Design.warning(this);
    }
    private String themeName() { int t = store.theme(); return t == 1 ? "Light" : (t == 2 ? "Dark" : "System"); }

    private void chooseTheme() {
        String[] options = {"System", "Light", "Dark"};
        new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(options, store.theme(), (d, w) -> {
            store.setTheme(w); d.dismiss(); showSettings();
        }).show();
    }

    private void editUrl() {
        prompt("Apps Script Web App URL", "Paste the HTTPS deployment URL", store.url(), value -> {
            if (!value.isEmpty() && !value.startsWith("https://")) { Components.customToast(this, "Use an HTTPS URL.", true); return; }
            // If URL changed, clear any previously verified state for the old URL
            if (!value.trim().equals(store.url())) store.clearVerifiedUrl();
            store.setUrl(value); showSettings();
        });
    }
    private void editDevice() { prompt("Device Name", "Used for every submitted expense", store.deviceName(), value -> { if (value.isEmpty()) { Components.customToast(this, "Device name cannot be empty.", true); return; } store.setDeviceName(value); showSettings(); }); }
    private void testConnection() {
        if (store.url().isEmpty()) { Components.customToast(this, "Add the Apps Script Web App URL first.", true); return; }
        final String testedUrl = store.url();
        Components.customToast(this, "Testing connection…", false);
        executor.execute(() -> {
            ApiClient.Result r = ApiClient.test(testedUrl);
            runOnUiThread(() -> {
                store.recordOperation("Connection test", r.success, r.message);
                // Persist verified state tied to this specific URL only on real success
                if (r.success) store.setVerifiedUrl(testedUrl);
                new AlertDialog.Builder(this).setTitle(r.success ? "Connection Test" : "Connection Failed").setMessage(r.message + (r.success ? "\n\nThis test sends no expense. A successful save still requires the server to return Success." : "")).setPositiveButton("OK", null).show();
                showSettings();
            });
        });
    }
    private void chooseDefaultCategory() { chooseDefault("Default Category", store.categories(), store.defaultCategory(), value -> { store.setDefaultCategory(value); showSettings(); }); }
    private void chooseDefaultPayment() { chooseDefault("Default Payment Mode", store.paymentModes(), store.defaultPaymentMode(), value -> { store.setDefaultPaymentMode(value); showSettings(); }); }
    private void chooseDefault(String title, List<String> values, String current, ValueHandler handler) {
        List<String> options = new ArrayList<>(); options.add("No default"); options.addAll(values); int selected = current.isEmpty() ? 0 : Math.max(0, options.indexOf(current)); new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]), selected, (d,w) -> { handler.accept(w == 0 ? "" : options.get(w)); d.dismiss(); }).show();
    }
    private void manage(String title, boolean category) {
        List<String> values = category ? store.categories() : store.paymentModes();
        ReorderableListDialog.show(this, title, values,
            // Row tap → actions (rename / emoji / delete)
            item -> itemActions(title, category, item),
            // Add button
            () -> addItem(title, category),
            // Order changed by drag — persist immediately, names/emojis unchanged
            newOrder -> {
                if (category) store.setCategories(newOrder);
                else store.setPaymentModes(newOrder);
            }
        );
    }
    private void itemActions(String title, boolean category, String current) {
        String[] actions = new String[]{"Rename", "Change emoji", "Delete"};
        new AlertDialog.Builder(this).setTitle(current).setItems(actions, (d, w) -> {
            if (w == 0) renameItem(title, category, current);
            else if (w == 1) changeEmoji(title, category, current);
            else deleteItem(title, category, current);
        }).show();
    }
    private void changeEmoji(String title,boolean category,String current){String[] icons=category?new String[]{"🍔","🛍️","🚗","💡","🎬","🏥","🏠","📚","💰","🏋️","✈️","🎁"}:new String[]{"📱","💵","💳","🏦","👛","📲","🟢","🟣","💰"};new AlertDialog.Builder(this).setTitle("Choose emoji").setItems(icons,(d,w)->{updateList(category,current,store.format(store.name(current),icons[w]));manage(title,category);}).show();}
    private void addItem(String title, boolean category) { prompt("Add " + title.substring(0, title.length()-1), "Name", "", value -> { if (updateList(category, null, value)) manage(title, category); }); }
    private void renameItem(String title, boolean category, String current) { prompt("Rename", "Name", store.name(current), value -> { if (updateList(category, current, value)) manage(title, category); }); }
    private void deleteItem(String title, boolean category, String current) { new AlertDialog.Builder(this).setTitle("Delete “" + current + "”?").setMessage("Existing Google Sheet transactions will not be changed.").setNegativeButton("Cancel", null).setPositiveButton("Delete", (d,w) -> { List<String> values = category ? store.categories() : store.paymentModes(); if (values.size() == 1) { Components.customToast(this,"Keep at least one option.",true); return; } values.remove(current); if (category) { store.setCategories(values); if (current.equals(store.defaultCategory())) store.setDefaultCategory(""); } else { store.setPaymentModes(values); if (current.equals(store.defaultPaymentMode())) store.setDefaultPaymentMode(""); } manage(title, category); }).show(); }
    private boolean updateList(boolean category, String oldValue, String newValue) {
        String clean = newValue.trim(); if (clean.isEmpty() || clean.contains("\u001F")) { Components.customToast(this,"Enter a valid name.",true); return false; }
        clean = store.canonical(clean, oldValue == null ? (category ? "🏷️" : "💳") : store.emoji(oldValue));
        List<String> values = category ? store.categories() : store.paymentModes(); for (String item : values) if (!item.equals(oldValue) && store.backendValue(item).equalsIgnoreCase(store.backendValue(clean))) { Components.customToast(this,"That option already exists.",true); return false; }
        if (oldValue == null) values.add(clean); else { int i=values.indexOf(oldValue); values.set(i,clean); if (category && oldValue.equals(store.defaultCategory())) store.setDefaultCategory(clean); if (!category && oldValue.equals(store.defaultPaymentMode())) store.setDefaultPaymentMode(clean); }
        if (category) store.setCategories(values); else store.setPaymentModes(values); return true;
    }
    private void prompt(String title, String hint, String current, ValueHandler handler) { EditText input = new EditText(this); input.setHint(hint); input.setText(current); input.setSelectAllOnFocus(true); input.setPadding(pad,pad,pad,pad); new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> handler.accept(input.getText().toString())).create().show(); }
    
    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(pad,pad,pad,pad); p.setBackgroundColor(Design.background(this)); return p; }
    private void header(LinearLayout page, String title, String subtitle, boolean isSettings) { 
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); 
        if (isSettings) {
            Button back = Components.textButton(this, "←"); back.setTextSize(22); back.setPadding(0,0,dp(8),0); back.setContentDescription("Back to home"); back.setOnClickListener(v -> showHome());
            row.addView(back, new LinearLayout.LayoutParams(dp(48),dp(48)));
        } else {
            // Home header — logo before title
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.mipmap.app_logo);
            logo.setContentDescription("Pocket Ledger");
            logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
            lp.setMarginEnd(dp(10));
            row.addView(logo, lp);
        }
        TextView text = new TextView(this); text.setText(subtitle.isEmpty()?title:title + "\n" + subtitle); 
        text.setTextSize(24); text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); text.setTextColor(Design.text(this)); 
        if (isSettings) text.setPadding(dp(8),0,0,0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1)); 
        if (!isSettings) { 
            Button button = Components.textButton(this, "⚙"); button.setTextSize(22); button.setContentDescription("Open settings"); 
            button.setOnClickListener(v -> showSettings()); row.addView(button,new LinearLayout.LayoutParams(dp(44),dp(44))); 
        } 
        page.addView(row, match()); 
    }
    
    private void section(LinearLayout page, String text) { 
        TextView v = Components.subtitle(this, text);
        v.setPadding(0,dp(28),0,dp(8)); 
        page.addView(v); 
    }

    private void settingRow(LinearLayout page, String title, String detail, boolean actionable, int statusColor, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        if (actionable) Components.addPressAnimation(row);
        if (actionable) row.setOnClickListener(v -> action.run());

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Components.text(this, title, 16, Design.text(this), Typeface.BOLD);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        if (actionable) {
            TextView chevron = Components.text(this, "›", 20, Design.muted(this), Typeface.NORMAL);
            top.addView(chevron);
        }
        row.addView(top);

        if (statusColor != 0) {
            TextView badge = Components.badge(this, detail, statusColor);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(0, dp(6), 0, 0);
            row.addView(badge, bp);
        } else if (!detail.isEmpty()) {
            TextView d = Components.text(this, detail, 14, Design.muted(this), Typeface.NORMAL);
            d.setPadding(0, dp(2), 0, 0);
            row.addView(d);
        }

        page.addView(row, match());
    }

    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Design.dp(this, value); }
    
    private void showDiagnostics(){
        String timestamp=store.lastAt()==0?"Not available":new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(store.lastAt()));
        String status=store.url().isEmpty()?"Not configured":(store.lastSuccess()?"Success":"Needs attention");
        String report="Pocket Ledger\nVersion: "+appVersion()+"\nAndroid: "+Build.VERSION.RELEASE+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\n\nConnection: "+status+"\nLast operation: "+store.lastOperation()+"\nLast status: "+(store.lastSuccess()?"Success":"Not successful")+"\nDetail: "+store.lastDetail()+"\nTimestamp: "+timestamp+"\n\nSupport: Telegram @TechyBist";
        
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20),dp(20),dp(20),dp(20)); body.setBackground(Design.shape(this, Design.surface(this), 24, 0));
        TextView t = Components.text(this, "Support & Diagnostics", 24, Design.text(this), Typeface.BOLD); t.setPadding(0,0,0,dp(16)); body.addView(t);
        diagnosticSection(body,"APP","Pocket Ledger\nVersion "+appVersion(), 0); 
        diagnosticSection(body,"DEVICE","Android "+Build.VERSION.RELEASE+"\n"+Build.MANUFACTURER+" "+Build.MODEL, 0); 
        diagnosticSection(body,"CONNECTION",status+"\nLast operation: "+store.lastOperation()+"\n"+store.lastDetail()+"\n"+timestamp, store.lastSuccess() ? Design.success(this) : Design.error(this)); 
        diagnosticSection(body,"SUPPORT","Telegram @TechyBist", 0);
        
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); actions.setPadding(0,dp(20),0,0);
        Button support = Components.secondaryButton(this, "Contact support"); 
        Button copy = Components.primaryButton(this, "Copy report");
        actions.addView(support);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); cp.setMargins(dp(12),0,0,0);
        actions.addView(copy, cp);
        body.addView(actions);
        
        AlertDialog dialog=new AlertDialog.Builder(this).setView(body).create(); 
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        support.setOnClickListener(x->{Intent telegram=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("tg://resolve?domain=TechyBist")); Intent web=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://t.me/TechyBist")); startActivity(telegram.resolveActivity(getPackageManager())!=null?telegram:web);});
        copy.setOnClickListener(x->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pocket Ledger diagnostics",report)); Components.customToast(this, "Report copied to clipboard", false); dialog.dismiss();});
        dialog.show();
    }
    private void diagnosticSection(LinearLayout parent,String heading,String value, int statusColor){
        TextView h=Components.subtitle(this, heading); h.setPadding(0,dp(12),0,dp(4)); parent.addView(h);
        if (statusColor != 0 && value.startsWith("Success")) {
            parent.addView(Components.badge(this, "Success", statusColor));
            value = value.substring(7).trim(); // remove "Success" from start
        } else if (statusColor != 0 && value.startsWith("Needs attention")) {
            parent.addView(Components.badge(this, "Failure", statusColor));
            value = value.substring(15).trim();
        }
        if (!value.isEmpty()) {
            TextView v=Components.text(this, value, 14, Design.text(this), Typeface.NORMAL); v.setLineSpacing(dp(2),1f); v.setPadding(0,dp(4),0,dp(4)); parent.addView(v);
        }
    }
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){return "Unknown";}}
    private interface ValueHandler { void accept(String value); }
}
