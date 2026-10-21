package com.pocketledger.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V2 Main Activity.
 *
 * Screens:
 *   HOME (Dashboard) — local DB driven KPIs, date range selector
 *   LEDGER           — local transaction list
 *   SETTINGS         — sync URL, preferences, categories/payments, data
 *
 * V2 Architecture:
 *   Local Room DB is the PRIMARY source of truth.
 *   Google Sheets is a secondary mirror, synced explicitly in Settings.
 *   No background sync. No background services.
 */
public final class MainActivity extends Activity {

    public static final String ACTION_ADD_EXPENSE  = "com.pocketledger.v6.preview.ADD_EXPENSE";
    public static final String EXTRA_OPEN_SETTINGS = "open_settings";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    private ScreenPresenter screens;
    private Ledger ledgerCache;
    private final UiRevision dataLoads=new UiRevision();
    private String dataSignature="",homeKey="",activityKey="";
    private View homeScene,activityScene;
    private boolean resumed,foreground,quietRefresh;
    private int pad;

    // Live instance so QuickEntryActivity's background auto-sync can ask the
    // current screen to re-render after it finishes (see notifyPendingSyncCompleted).
    private static volatile MainActivity instance;

    private enum Screen { HOME, LEDGER, SETTINGS }
    private Screen currentScreen = Screen.HOME;

    private enum SettingsSection { MAIN, SYNC, PREFERENCES, CATEGORIES, DATA_SUPPORT }
    private SettingsSection currentSettingsSection = SettingsSection.MAIN;
    private LinearLayout activeSettingsGroup;

    // Date range selection (default: This Month)
    private String dateFrom = "", dateTo = "";
    private long rangeFromMs = 0, rangeToMs = Long.MAX_VALUE;
    private String selectedRangeLabel = "This Month";

    // Ledger filters
    private String ledgerSearchQuery = "";
    private int ledgerSortIndex = 0; // 0: Newest, 1: Oldest, 2: Amount High-Low, 3: Amount Low-High
    private String ledgerCategoryFilter = "All";
    private String ledgerPaymentFilter = "All";
    private String ledgerSpendingFilter = "All";
    private int ledgerRenderId = 0;
    private String ledgerKindFilter = "All";
    private boolean ledgerFiltersExpanded;

    private boolean isRestoring = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        instance = this;
        store = new ConfigStore(this);
        pad   = Design.dp(this, 20);
        setDateRange(selectedRangeLabel);
        NotificationHelper.prepare(this);
        applyWindowTheme();
        registerBackNav();
        screens=new ScreenPresenter(this);

        // If launched via Add Expense action (shortcut / widget / tile redirect)
        if (ACTION_ADD_EXPENSE.equals(getIntent().getAction())) {
            startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE));
            finish();
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings();
        else showHome();
    }

    @Override protected void onResume() {
        super.onResume();foreground=true;
        if(resumed)reloadLedger();
        resumed=true;
    }
    @Override protected void onPause(){foreground=false;super.onPause();}

    private void reloadLedger(){
        final int request=dataLoads.next();
        executor.execute(()->{
            try{
                final Ledger[] loaded=new Ledger[1];AppDatabase db=AppDatabase.get(this);
                db.runInTransaction(()->loaded[0]=Ledger.load(db));
                String signature=LedgerJson.write(loaded[0]).toString();
                runOnUiThread(()->{
                    if(!dataLoads.current(request)||isFinishing()||isDestroyed())return;
                    boolean changed=!signature.equals(dataSignature);
                    ledgerCache=loaded[0];dataSignature=signature;
                    if(changed){homeScene=null;activityScene=null;}
                    if(!foreground)return;
                    quietRefresh=true;
                    if(currentScreen==Screen.HOME)showHome();else if(currentScreen==Screen.LEDGER)showLedger();
                    quietRefresh=false;
                });
            }catch(Exception error){runOnUiThread(()->{if(dataLoads.current(request)&&!isFinishing())Components.customToast(this,"Could not refresh entries. Try again.",true);});}
        });
    }
    private String homeSceneKey(){return rangeFromMs+":"+rangeToMs+":"+getSharedPreferences("ledger_ui",0).getString("selectedAccount","")+":"+store.theme()+":"+store.currencySymbol()+":"+store.recentEntriesHidden();}
    private String activitySceneKey(){return homeSceneKey()+":"+ledgerCategoryFilter+":"+ledgerPaymentFilter+":"+ledgerSpendingFilter+":"+ledgerKindFilter+":"+ledgerSortIndex+":"+ledgerSearchQuery+":"+ledgerFiltersExpanded+":"+getSharedPreferences("ledger_ui",0).getString("personFilter","");}
    private void present(View view,String key){
        screens.navigation(currentScreen==Screen.SETTINGS?null:buildBottomNavigation());
        // Repeated period/filter changes are instant; only navigation gets movement.
        screens.show(view,key,!quietRefresh&&!screens.isRoute(key));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (ACTION_ADD_EXPENSE.equals(intent.getAction()))
            startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE));
        else if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings();
    }

    @Override protected void onDestroy() {
        if (instance == this) instance = null;
        dataLoads.next();if(screens!=null)screens.dispose();
        executor.shutdownNow();
        super.onDestroy();
    }

    /**
     * Called by QuickEntryActivity's background auto-sync once it has finished,
     * so MainActivity re-queries Room and re-renders the screen. Without this the
     * Ledger would keep showing a row that was rendered BEFORE the sync completed
     * (badge stuck on "Not synced"), even though the transaction is now SYNCED.
     * No-op when this activity is no longer alive.
     */
    static Ledger cachedLedger(){MainActivity a=instance;return a==null?null:a.ledgerCache;}
    static String cachedLedgerSnapshot(){MainActivity a=instance;return a==null||a.ledgerCache==null?null:a.dataSignature;}

    static void notifyPendingSyncCompleted() {
        MainActivity a = instance;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if(a.foreground)a.reloadLedger();
        });
    }

    /**
     * Modern Android back (system button + edge-swipe gesture) must route through
     * the app's navigation hierarchy. targetSdk 35+ forces predictive back on
     * Android 15+ devices, where the legacy onBackPressed() is no longer invoked,
     * so the activity registers an OnBackInvokedCallback via the window dispatcher.
     * Both paths share this same navigation logic.
     *
     * @return true if the back event was consumed by in-app navigation,
     *         false to hand it to the system (Home → normal exit/background).
     */
    private boolean handleBackNav() {
        if (currentScreen == Screen.SETTINGS && currentSettingsSection != SettingsSection.MAIN) {
            // Settings subpage → Main Settings
            currentSettingsSection = SettingsSection.MAIN;
            showSettings();
            return true;
        }
        if (currentScreen == Screen.SETTINGS || currentScreen == Screen.LEDGER) {
            // Main Settings / Ledger → Home
            showHome();
            return true;
        }
        return false;
    }

    private void registerBackNav() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (!handleBackNav()) finishAfterTransition(); });
        }
    }

    /** Fallback for API < 33 and devices where predictive back is disabled. */
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (!handleBackNav()) super.onBackPressed();
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    private void applyWindowTheme() {
        setTheme(Design.dark(this) ? R.style.Theme_PocketLedger_Dark : R.style.Theme_PocketLedger);
        int bg = Design.background(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (Design.dark(this)) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else                   flags |=  View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (Design.dark(this)) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else                   flags |=  View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOME / DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────

    private void showHome() {
        currentScreen = Screen.HOME;
        applyWindowTheme();
        if(ledgerCache==null){reloadLedger();return;}
        String key=homeSceneKey();
        if(homeScene!=null&&homeKey.equals(key)){present(homeScene,"HOME");return;}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, dp(18), pad, dp(16));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.app_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        logoParams.setMarginEnd(dp(12));
        header.addView(logo, logoParams);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView brand = Components.text(this, "Pocket Ledger", 20, Design.text(this), Typeface.BOLD);
        brand.setLetterSpacing(-0.025f);
        identity.addView(brand);
        TextView context = Components.text(this, "Your money, in focus", 12, Design.textSoft(this), Typeface.NORMAL);
        identity.addView(context);
        header.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        NavIcon gear = new NavIcon(this, NavIcon.ICON_GEAR, Design.text(this));
        LinearLayout gearTarget = new LinearLayout(this);
        gearTarget.setGravity(Gravity.CENTER);
        gearTarget.setBackground(Design.shape(Design.surface(this), 14, this));
        gearTarget.addView(gear, new LinearLayout.LayoutParams(dp(24), dp(24)));
        gearTarget.setContentDescription("Open settings");
        Components.addPressAnimation(gearTarget);
        gearTarget.setOnClickListener(v -> showSettings());
        header.addView(gearTarget, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, match());

        LinearLayout statement = new LinearLayout(this);
        statement.setOrientation(LinearLayout.VERTICAL);
        statement.setTag("spending_statement");
        statement.setPadding(dp(24), dp(24), dp(24), dp(22));
        statement.setBackground(Design.shape(Design.dark(this) ? Design.surface(this) : Color.parseColor("#103F2B"), 20, this));
        int statementInk = Color.parseColor("#F3FCF5");
        int statementSoft = Color.parseColor("#BCE1C9");
        TextView statementLabel = Components.text(this, selectedRangeLabel + " spending", 14, statementSoft, Typeface.NORMAL);
        statementLabel.setTag("statement_label");
        statement.addView(statementLabel);
        TextView balance = Components.heroAmount(this, store.currencySymbol() + "—");
        balance.setTextColor(statementInk);
        balance.setTag("balance_hero");
        LinearLayout.LayoutParams amountParams = match();
        amountParams.setMargins(0, dp(10), 0, dp(28));
        statement.addView(balance, amountParams);
        View rule = new View(this);
        rule.setBackgroundColor(Color.parseColor("#3B7558"));
        statement.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout facts = new LinearLayout(this);
        facts.setPadding(0, dp(18), 0, 0);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        facts.setTag("statement_facts");
        LinearLayout countFact = new LinearLayout(this);
        countFact.setOrientation(LinearLayout.VERTICAL);
        countFact.addView(Components.text(this, "Entries", 12, statementSoft, Typeface.NORMAL));
        TextView txCount = Components.text(this, "—", 18, statementInk, Typeface.BOLD);
        txCount.setTag("tx_count");
        countFact.addView(txCount);
        facts.addView(countFact, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout averageFact = new LinearLayout(this);
        averageFact.setOrientation(LinearLayout.VERTICAL);
        averageFact.addView(Components.text(this, "Average per day", 12, statementSoft, Typeface.NORMAL));
        TextView avgDay = Components.text(this, "—", 18, statementInk, Typeface.BOLD);
        avgDay.setTag("avg_day");
        averageFact.addView(avgDay);
        facts.addView(averageFact, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statement.addView(facts);
        LinearLayout.LayoutParams statementParams = match();
        statementParams.setMargins(pad, dp(8), pad, dp(18));
        root.addView(statement, statementParams);

        root.addView(buildHomeTools(),match());

        View homeRange=buildDateRangeSelector();homeRange.setTag("home_period_selector");root.addView(homeRange, match());

        LinearLayout chartsBlock = new LinearLayout(this);
        chartsBlock.setOrientation(LinearLayout.VERTICAL);
        chartsBlock.setPadding(pad, dp(18), pad, dp(12));
        chartsBlock.setTag("charts_block");
        root.addView(chartsBlock, match());

        LinearLayout recentBlock = new LinearLayout(this);
        recentBlock.setOrientation(LinearLayout.VERTICAL);
        recentBlock.setTag("recent_block");
        root.addView(recentBlock, match());

        LedgerHome.attach(this,root,ledgerCache,rangeFromMs,rangeToMs,selectedRangeLabel,this::showHome);
        loadDashboardData(root);
        homeScene=showScreen(root);homeKey=homeSceneKey();present(homeScene,"HOME");
    }
    
    private View buildHomeTools() {
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);tools.setPadding(pad,dp(6),pad,dp(22));
        TextView heading=Components.text(this,"Tools",16,Design.textSoft(this),Typeface.BOLD);tools.addView(heading);
        LinearLayout button=new LinearLayout(this);button.setGravity(Gravity.CENTER_VERTICAL);button.setPadding(dp(16),dp(16),dp(16),dp(16));button.setBackground(Design.shape(Design.surface(this),16,this));
        LinearLayout icon=new LinearLayout(this);icon.setGravity(Gravity.CENTER);icon.setBackground(Design.shape(Design.primaryMuted(this),12,this));icon.addView(new NavIcon(this,NavIcon.ICON_SPLIT_BILL,Design.primary(this)),new LinearLayout.LayoutParams(dp(28),dp(28)));button.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(14),0,dp(10),0);labels.addView(Components.text(this,"Split bills",17,Design.text(this),Typeface.BOLD));TextView description=Components.text(this,"Share a meal. Settle with friends.",13,Design.textSoft(this),Typeface.NORMAL);description.setPadding(0,dp(5),0,0);labels.addView(description);button.addView(labels,new LinearLayout.LayoutParams(0,-2,1));button.addView(new NavIcon(this,NavIcon.ICON_CHEVRON,Design.muted(this)),new LinearLayout.LayoutParams(dp(20),dp(20)));
        button.setFocusable(true);button.setContentDescription("Split bills. Open standalone bill splitter");Components.addPressAnimation(button);button.setOnClickListener(v->startActivity(new Intent(this,BillSplitActivity.class)));
        LinearLayout.LayoutParams p=match();p.topMargin=dp(10);tools.addView(button,p);return tools;
    }

    private void loadDashboardData(LinearLayout page) {

            Ledger snapshot=ledgerCache;
            List<TransactionEntity> txs=new ArrayList<>();
            for(TransactionEntity tx:snapshot.transactions)if(!tx.deleted&&Ledger.at(tx)>=rangeFromMs&&Ledger.at(tx)<=rangeToMs)txs.add(tx);
            String accountFilter = getSharedPreferences("ledger_ui",0).getString("selectedAccount", "");
            txs.removeIf(t -> !snapshot.matches(t, accountFilter));
            
            List<TransactionEntity> allTxs=new ArrayList<>();for(TransactionEntity tx:snapshot.transactions)if(!tx.deleted)allTxs.add(tx);
            allTxs.removeIf(t -> !snapshot.matches(t, accountFilter));
            java.util.Collections.sort(allTxs, (a, b) -> Long.compare(Ledger.at(b), Ledger.at(a)));
            List<TransactionEntity> recent = allTxs.subList(0, Math.min(5, allTxs.size()));
            
            double total = 0;
            long exactTotal = 0;
            java.util.Map<String, Double> catsMap = new java.util.LinkedHashMap<>();
            java.util.Map<String, Double> dailyMap = new java.util.TreeMap<>();
            SpendingInsights spendingInsights = new SpendingInsights();
            
            for (TransactionEntity tx : txs) {
                long own = Ledger.expense(tx);
                exactTotal = Money.add(exactTotal, own);
                if (own == 0) continue;
                double expenseAmount = own / 100.0;
                if (expenseAmount > 0) spendingInsights.add(expenseAmount, tx.spendingType, tx.category);
                else { TransactionEntity origin=snapshot.transaction(tx.originalId);spendingInsights.refund(-expenseAmount,origin==null?tx.spendingType:origin.spendingType,origin==null?tx.category:origin.category); }
                catsMap.put(tx.category, catsMap.getOrDefault(tx.category, 0.0) + expenseAmount);
                String day = dayKey(tx);
                dailyMap.put(day, dailyMap.getOrDefault(day, 0.0) + expenseAmount);
            }
            
            total = exactTotal / 100.0;
            final double finalTotal = total;
            final int finalCount = (int) txs.stream().filter(t -> Ledger.expense(t) > 0).count();
            final double finalAvg = finalCount > 0 ? (total / Math.max(1, dailyMap.size())) : 0;
            
            List<DonutChartView.CategorySpend> cats = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, Double> e : catsMap.entrySet()) {
                if (e.getValue() > 0) cats.add(new DonutChartView.CategorySpend(e.getKey(), e.getValue()));
            }
            
            java.util.Collections.sort(cats, (a, b) -> Double.compare(b.amount, a.amount));

            runOnUiThread(() -> {
                TextView balanceView = page.findViewWithTag("balance_hero");
                if (balanceView != null) {
                    balanceView.setText(store.currencySymbol() + String.format(java.util.Locale.US, "%.2f", finalTotal));
                }
                TextView countView = page.findViewWithTag("tx_count");
                if (countView != null) {
                    countView.setText(String.valueOf(finalCount));
                }
                TextView avgView = page.findViewWithTag("avg_day");
                if (avgView != null) {
                    avgView.setText(store.currencySymbol() + String.format(java.util.Locale.US, "%.0f", finalAvg));
                }

                LinearLayout chartsBlock = page.findViewWithTag("charts_block");
                if (chartsBlock != null) {
                    chartsBlock.removeAllViews();
                    TextView analysisTitle = Components.text(this, "At a glance", 22, Design.text(this), Typeface.BOLD);
                    chartsBlock.addView(analysisTitle);
                    if (!cats.isEmpty()) {
                        TextView mixTitle = Components.text(this, "Where it went", 14, Design.textSoft(this), Typeface.NORMAL);
                        LinearLayout.LayoutParams mt = match();
                        mt.setMargins(0, dp(20), 0, 0);
                        chartsBlock.addView(mixTitle, mt);
                        DonutChartView comp = new DonutChartView(this);
                        comp.setData(cats, store.currencySymbol());
                        LinearLayout.LayoutParams cp = match();
                        cp.setMargins(0, dp(6), 0, dp(3));
                        chartsBlock.addView(comp, cp);
                        for (int i = 0; i < cats.size(); i++) {
                            final int selectedIndex = i;
                            DonutChartView.CategorySpend item = cats.get(i);
                            LinearLayout legendRow = new LinearLayout(this);
                            legendRow.setGravity(Gravity.CENTER_VERTICAL);
                            legendRow.setMinimumHeight(dp(48));
                            View swatch = new View(this);
                            swatch.setBackground(Design.shape(DonutChartView.colorFor(this, i), 4, this));
                            legendRow.addView(swatch, new LinearLayout.LayoutParams(dp(10), dp(10)));
                            TextView category = Components.text(this, item.name, 14, Design.text(this), Typeface.NORMAL);
                            category.setPadding(dp(11), 0, dp(8), 0);
                            category.setSingleLine(true);
                            category.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            legendRow.addView(category, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                            String share = String.format(Locale.US, "%.0f%%", item.amount / cats.stream().mapToDouble(c -> c.amount).sum() * 100);
                            TextView value = Components.text(this, share, 14, Design.textSoft(this), Typeface.BOLD);
                            legendRow.addView(value);
                            legendRow.setOnClickListener(v -> comp.setSelectedIndex(selectedIndex));
                            legendRow.setContentDescription(item.name + ", " + share + " of selected spending. Show on chart.");
                            chartsBlock.addView(legendRow);
                        }
                        View mixRule = Components.hairline(this);
                        LinearLayout.LayoutParams mr = match();
                        mr.setMargins(0, dp(11), 0, dp(14));
                        chartsBlock.addView(mixRule, mr);
                    }
                    if (finalCount > 0 || txs.stream().anyMatch(t -> "REFUND".equals(t.kind))) addSpendingInsights(chartsBlock, spendingInsights);
                    if (txs.stream().anyMatch(t -> "REFUND".equals(t.kind))) {
                        chartsBlock.addView(Components.text(this,"Net spending includes refunds recorded in this period. The donut shows positive category totals.",14,Design.textSoft(this),Typeface.NORMAL));
                        for(Map.Entry<String,Double> e:catsMap.entrySet())if(e.getValue()<0)chartsBlock.addView(Components.text(this,e.getKey()+" · net refund "+store.currencySymbol()+String.format(Locale.US,"%.2f",-e.getValue()),14,Design.text(this),Typeface.NORMAL));
                    }
                    if (cats.isEmpty() && finalCount == 0) {
                        TextView empty = Components.text(this,
                                "Your first expense will appear here. Add one to start seeing the pattern.",
                                15, Design.textSoft(this), Typeface.NORMAL);
                        empty.setPadding(0, dp(18), 0, dp(28));
                        chartsBlock.addView(empty);
                    }
                }

                LinearLayout recentBlock = page.findViewWithTag("recent_block");
                if (recentBlock != null) {
                    recentBlock.removeAllViews();
                    LinearLayout heading = new LinearLayout(this);
                    heading.setGravity(Gravity.CENTER_VERTICAL);
                    heading.setPadding(pad, dp(23), pad, dp(13));
                    TextView recentTitle = Components.text(this, "Latest entries", 22, Design.text(this), Typeface.BOLD);
                    recentTitle.setSingleLine(true);
                    recentTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    heading.addView(recentTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    boolean hidden = store.recentEntriesHidden();
                    Button visibility = Components.textButton(this, hidden ? "Show" : "Hide");
                    visibility.setTextSize(13);
                    visibility.setTextColor(Design.primary(this));
                    visibility.setBackground(Design.shape(Design.primaryMuted(this), 12, this));
                    visibility.setMinWidth(dp(64));
                    visibility.setPadding(dp(10), 0, dp(10), 0);
                    visibility.setContentDescription(hidden ? "Show latest entries" : "Hide latest entries");
                    heading.addView(visibility, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
                    TextView all = Components.text(this, "View all", 14, Design.primary(this), Typeface.BOLD);
                    all.setPadding(dp(8), 0, 0, 0);
                    all.setGravity(Gravity.CENTER);
                    all.setMinHeight(dp(48));
                    all.setFocusable(true);
                    all.setContentDescription("View all transactions");
                    Components.addPressAnimation(all);
                    all.setOnClickListener(v -> showLedger());
                    heading.addView(all);
                    recentBlock.addView(heading);
                    LinearLayout entries = new LinearLayout(this);
                    entries.setOrientation(LinearLayout.VERTICAL);
                    entries.setVisibility(hidden ? View.GONE : View.VISIBLE);
                    recentBlock.addView(entries);
                    visibility.setOnClickListener(v -> {
                        boolean hide = entries.getVisibility() == View.VISIBLE;
                        store.setRecentEntriesHidden(hide);homeKey=homeSceneKey();
                        entries.setVisibility(hide ? View.GONE : View.VISIBLE);
                        visibility.setText(hide ? "Show" : "Hide");
                        visibility.setContentDescription(hide ? "Show latest entries" : "Hide latest entries");
                    });
                    for (TransactionEntity tx : recent) {
                        entries.addView(buildLedgerRow(tx, entries));
                    }
                    if (recent.isEmpty()) {
                        TextView empty = Components.text(this, "No entries yet", 14, Design.muted(this), Typeface.NORMAL);
                        empty.setPadding(pad, dp(12), pad, dp(30));
                        entries.addView(empty);
                    }
                }
            });

    }

    private void addSpendingInsights(LinearLayout parent, SpendingInsights insights) {
        TextView title = Components.text(this, "Your spending choices", 22, Design.text(this), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = match();
        titleParams.setMargins(0, dp(22), 0, dp(6));
        parent.addView(title, titleParams);
        parent.addView(Components.text(this, "Essential, optional, and still untagged. Tap to view entries.", 13, Design.textSoft(this), Typeface.NORMAL));
        int[] colors = {Design.primary(this), Design.dark(this) ? Color.parseColor("#6CCFC5") : Color.parseColor("#177E78"), Design.muted(this)};
        String[] types = {SpendingType.NEED, SpendingType.WANT, SpendingType.UNTAGGED};
        String[] names = {"Needs", "Wants", "Untagged"};
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Design.shape(Design.border(this), 5, this));
        if(insights.hasNegative())bar.setVisibility(View.GONE);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < 3; i++) {
            if (insights.amounts[i] <= 0 || insights.hasNegative()) continue;
            View segment = new View(this);
            segment.setBackgroundColor(colors[i]);
            bar.addView(segment, new LinearLayout.LayoutParams(0, dp(10), (float)(insights.amounts[i] / insights.total())));
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        barParams.setMargins(0, dp(18), 0, dp(12));
        parent.addView(bar, barParams);
        for (int i = 0; i < 3; i++) {
            final String type = types[i];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(62));
            View dot = new View(this);
            dot.setBackground(Design.shape(colors[i], 3, this));
            row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
            TextView name = Components.text(this, names[i] + " · " + insights.counts[i], 15, Design.text(this), Typeface.BOLD);
            name.setPadding(dp(10), 0, dp(8), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout values = new LinearLayout(this);
            values.setOrientation(LinearLayout.VERTICAL);
            TextView amount = Components.text(this, store.currencySymbol() + String.format(Locale.US, "%.2f", insights.amounts[i]), 16, Design.text(this), Typeface.BOLD);
            amount.setGravity(Gravity.END);
            values.addView(amount);
            TextView share = Components.text(this, (insights.hasNegative() || insights.total() <= 0 ? "Net after refunds" : String.format(Locale.US, "%.1f%% of spending", insights.share(i))), 12, Design.textSoft(this), Typeface.NORMAL);
            share.setGravity(Gravity.END);
            values.addView(share);
            row.addView(values);
            row.setFocusable(true);
            row.setContentDescription(names[i] + ", " + insights.counts[i] + " entries, " + amount.getText() + ", " + share.getText() + ". View entries.");
            row.setOnClickListener(v -> openSpendingEntries(type));
            Components.addPressAnimation(row);
            parent.addView(row, match());
        }
        String topCategory = insights.topWantCategory();
        if (!topCategory.isEmpty()) {
            TextView top = Components.text(this, "Largest Wants category: " + topCategory, 13, Design.textSoft(this), Typeface.NORMAL);
            top.setPadding(0, dp(8), 0, dp(8));
            parent.addView(top);
        }
        if (insights.counts[2] > 0) {
            Button review = Components.secondaryButton(this, "Review " + insights.counts[2] + " untagged entries");
            review.setOnClickListener(v -> openSpendingEntries(SpendingType.UNTAGGED));
            parent.addView(review, match());
        }
    }

    private void openSpendingEntries(String type) {
        ledgerSpendingFilter = type;
        ledgerCategoryFilter = "All";
        ledgerPaymentFilter = "All";
        ledgerSearchQuery = "";
        showLedger();
    }

    private String dayKey(TransactionEntity tx) {
        String date = tx.date == null ? "" : tx.date;
        if (date.length() == 10 && date.charAt(2) == '-' && date.charAt(5) == '-')
            return date.substring(6) + "-" + date.substring(3, 5) + "-" + date.substring(0, 2);
        if (date.length() == 10 && date.charAt(4) == '-' && date.charAt(7) == '-') return date;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(Ledger.at(tx)));
    }

    private View buildDateRangeSelector() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(pad, 0, pad, dp(9));

        String[] ranges = {"This Month", "Last Month", "This Year", "All Time"};
        for (String r : ranges) {
            boolean active = r.equals(selectedRangeLabel);
            TextView t = new TextView(this);
            t.setText(r);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(active ? Design.onPrimary(this) : Design.textSoft(this));
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setBackground(Design.shape(active ? Design.primary(this) : Design.surface(this), 12, this));
            t.setPadding(dp(17), dp(12), dp(17), dp(12));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            chipParams.setMarginEnd(dp(8));
            container.addView(t, chipParams);
            Components.addPressAnimation(t);
            t.setOnClickListener(v -> applyDateRange(r));
        }
        scroll.addView(container);
        return scroll;
    }

    private void applyDateRange(String type) {
        setDateRange(type);
        if (currentScreen == Screen.HOME) showHome();
        else if (currentScreen == Screen.LEDGER) showLedger();
    }

    private void setDateRange(String type) {
        selectedRangeLabel = type;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (type.equals("This Month")) {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
            rangeFromMs = cal.getTimeInMillis();
            cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59);
            rangeToMs = cal.getTimeInMillis();
        } else if (type.equals("Last Month")) {
            cal.add(java.util.Calendar.MONTH, -1);
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
            rangeFromMs = cal.getTimeInMillis();
            cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59);
            rangeToMs = cal.getTimeInMillis();
        } else if (type.equals("This Year")) {
            cal.set(java.util.Calendar.DAY_OF_YEAR, 1);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0);
            rangeFromMs = cal.getTimeInMillis();
            cal.set(java.util.Calendar.DAY_OF_YEAR, cal.getActualMaximum(java.util.Calendar.DAY_OF_YEAR));
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59);
            rangeToMs = cal.getTimeInMillis();
        } else {
            rangeFromMs = 0;
            rangeToMs = Long.MAX_VALUE;
        }
    }

    private View buildBottomNavigation() {
        LinearLayout navWrapper = new LinearLayout(this);
        navWrapper.setOrientation(LinearLayout.VERTICAL);
        navWrapper.setBackgroundColor(Design.background(this));
        navWrapper.setPadding(dp(12), dp(6), dp(12), dp(10));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(Design.shape(Design.surface(this), 20, this));
        
        View overview = navTab("Home", currentScreen == Screen.HOME, () -> showHome());
        nav.addView(overview, new LinearLayout.LayoutParams(0, dp(52), 1));

        LinearLayout fab = new LinearLayout(this);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Design.shape(Design.primary(this), 14, this));
        fab.setContentDescription("Add expense");
        NavIcon plus = new NavIcon(this, NavIcon.ICON_FAB, Design.onPrimary(this));
        fab.addView(plus, new LinearLayout.LayoutParams(dp(20), dp(20)));
        Components.addPressAnimation(fab);
        fab.setOnClickListener(v -> startActivity(LedgerActivity.intent(this,"entry").putExtra("kind","EXPENSE")));
        nav.addView(fab, new LinearLayout.LayoutParams(dp(58), dp(52)));

        View history = navTab("Activity", currentScreen == Screen.LEDGER, () -> showLedger());
        nav.addView(history, new LinearLayout.LayoutParams(0, dp(52), 1));

        navWrapper.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        return navWrapper;
    }

    private View navTab(String text, boolean active, Runnable onClick) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(active ? Design.primary(this) : Design.textSoft(this));
        t.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
        l.addView(t);
        if (active) l.setBackground(Design.shape(Design.primaryMuted(this), 14, this));
        
        Components.addPressAnimation(l);
        l.setOnClickListener(v -> { if (!active) onClick.run(); });
        return l;
    }

    private View showScreen(View content) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(this));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void showLedger() {
        currentScreen = Screen.LEDGER;
        applyWindowTheme();
        if(ledgerCache==null){reloadLedger();return;}
        String key=activitySceneKey();
        if(activityScene!=null&&activityKey.equals(key)){present(activityScene,"LEDGER");return;}

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(this));
        TextView title = Components.text(this, "Activity", 30, Design.text(this), Typeface.BOLD);
        title.setPadding(pad, dp(22), pad, 0);
        root.addView(title);
        TextView description = Components.text(this, "Income, spending, transfers and repayments.", 14, Design.textSoft(this), Typeface.NORMAL);
        description.setPadding(pad, dp(2), pad, dp(17));
        root.addView(description);
        root.addView(buildDateRangeSelector(), match());

        EditText search = new EditText(this);
        search.setTag("ledger_search");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search category or note");
        search.setTextColor(Design.text(this));
        search.setHintTextColor(Design.muted(this));
        search.setBackground(Design.shape(Design.surface(this), 14, this));
        search.setPadding(dp(17), 0, dp(17), 0);
        search.setText(ledgerSearchQuery);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        searchParams.setMargins(pad, dp(4), pad, dp(12));
        root.addView(search, searchParams);

        LinearLayout advancedFilters=new LinearLayout(this);advancedFilters.setOrientation(LinearLayout.VERTICAL);
        advancedFilters.setVisibility(ledgerFiltersExpanded?View.VISIBLE:View.GONE);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(pad, 0, pad, dp(13));
        filters.addView(filterChip("Category: " + ledgerCategoryFilter, () -> chooseLedgerFilter(true)), new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams paymentParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        paymentParams.setMargins(dp(8), 0, dp(8), 0);
        filters.addView(filterChip("Payment: " + ledgerPaymentFilter, () -> chooseLedgerFilter(false)), paymentParams);
        filters.addView(filterChip("Sort", this::chooseLedgerSort), new LinearLayout.LayoutParams(dp(64), dp(44)));
        advancedFilters.addView(filters);

        HorizontalScrollView typeScroll = new HorizontalScrollView(this);
        typeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout typeFilters = new LinearLayout(this);
        typeFilters.setPadding(pad, 0, pad, dp(10));
        String[] typeValues = {"All", SpendingType.NEED, SpendingType.WANT, SpendingType.UNTAGGED};
        for (String type : typeValues) {
            Button button = Components.textButton(this, "All".equals(type) ? "All types" : SpendingType.label(type));
            boolean active = type.equals(ledgerSpendingFilter);
            button.setSelected(active);
            button.setTextColor(active ? Design.onPrimary(this) : Design.text(this));
            button.setBackground(Design.shape(active ? Design.primary(this) : Design.surface(this), 12, this));
            button.setOnClickListener(v -> { ledgerSpendingFilter = type; showLedger(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            params.setMarginEnd(dp(8));
            typeFilters.addView(button, params);
        }
        typeScroll.addView(typeFilters);
        advancedFilters.addView(typeScroll);
        HorizontalScrollView operationScroll=new HorizontalScrollView(this);operationScroll.setHorizontalScrollBarEnabled(false);operationScroll.setClipToPadding(false);
        LinearLayout operations=new LinearLayout(this);operations.setPadding(pad,0,pad,dp(10));
        String[] kinds={"All","EXPENSE","INCOME","TRANSFER","LENA/DENA"};
        String[] kindNames={"All","Expenses","Income","Transfers","Lena / Dena"};
        for(int i=0;i<kinds.length;i++){
            final String kind=kinds[i];Button b=LedgerUi.chip(this,kindNames[i],kind.equals(ledgerKindFilter));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginEnd(dp(8));operations.addView(b,p);
            b.setOnClickListener(v->{ledgerKindFilter=kind;showLedger();});
        }
        operationScroll.addView(operations);root.addView(operationScroll);
        int activeFilters=(ledgerCategoryFilter.equals("All")?0:1)+(ledgerPaymentFilter.equals("All")?0:1)+(ledgerSpendingFilter.equals("All")?0:1)
            +(getSharedPreferences("ledger_ui",0).getString("selectedAccount","").isEmpty()?0:1)+(getSharedPreferences("ledger_ui",0).getString("personFilter","").isEmpty()?0:1);
        Button toggle=Components.textButton(this,(ledgerFiltersExpanded?"Hide filters":"Filters")+(activeFilters>0?" · "+activeFilters+" active":""));
        toggle.setTextColor(Design.primary(this));toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setPadding(dp(16),dp(10),dp(16),dp(10));
        toggle.setBackground(Design.shape(Design.surface(this),12,this));
        LinearLayout.LayoutParams toggleParams=match();toggleParams.setMargins(pad,0,pad,dp(12));root.addView(toggle,toggleParams);
        toggle.setOnClickListener(v->{ledgerFiltersExpanded=!ledgerFiltersExpanded;advancedFilters.setVisibility(ledgerFiltersExpanded?View.VISIBLE:View.GONE);activityKey=activitySceneKey();toggle.setText((ledgerFiltersExpanded?"Hide filters":"Filters")+(activeFilters>0?" · "+activeFilters+" active":""));});
        LedgerHome.filters(this,advancedFilters,ledgerCache,this::showLedger);root.addView(advancedFilters);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, match());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ledgerSearchQuery = s.toString();activityKey=activitySceneKey();
                renderLedgerItems(list, ledgerSearchQuery, 0);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        renderLedgerItems(list, ledgerSearchQuery, 0);

        activityScene=showScreen(root);activityKey=activitySceneKey();present(activityScene,"LEDGER");
    }

    private View filterChip(String label, Runnable onClick) {
        TextView chip = Components.text(this, label, 12, Design.text(this), Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setBackground(Design.shape(Design.surface(this), 11, this));
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private void chooseLedgerFilter(boolean category) {
        executor.execute(()->{
            Ledger l=Ledger.load(AppDatabase.get(this));List<String> values=new ArrayList<>();values.add("All");
            if(category){values.addAll(store.categories());for(String value:store.incomeCategories())if(!values.contains(value))values.add(value);}
            else for(PaymentChoice choice:PaymentChoice.list(l,true))if(!values.contains(choice.label))values.add(choice.label);
            for(TransactionEntity t:l.transactions){String value=category?t.category:t.paymentMode;if(!values.contains(value)&&!value.isEmpty())values.add(value);}
            runOnUiThread(()->LedgerUi.choose(this,category?"Category":"Payment method",values,values.indexOf(category?ledgerCategoryFilter:ledgerPaymentFilter),w->{if(category)ledgerCategoryFilter=values.get(w);else ledgerPaymentFilter=values.get(w);showLedger();}));
        });
    }

    private void chooseLedgerSort() {
        List<String> options=java.util.Arrays.asList("Newest first","Oldest first","Amount: high to low","Amount: low to high");
        LedgerUi.choose(this,"Sort entries",options,ledgerSortIndex,which->{ledgerSortIndex=which;showLedger();});
    }

    private void renderLedgerItems(LinearLayout list, String query, int startIndex) {
        final int renderId = ++ledgerRenderId;

            List<TransactionEntity> allTxs=new ArrayList<>();
            for(TransactionEntity tx:ledgerCache.transactions)if(!tx.deleted&&Ledger.at(tx)>=rangeFromMs&&Ledger.at(tx)<=rangeToMs)allTxs.add(tx);
            List<TransactionEntity> txs = new java.util.ArrayList<>();
            String q = query.toLowerCase(java.util.Locale.US);
            for(TransactionEntity tx : allTxs) {
                String searchable = (tx.category + " " + tx.paymentMode + " " + tx.remarks + " "
                        + tx.date + " " + tx.amount + " " + Ledger.label(tx)).toLowerCase(Locale.US);
                if ((ledgerCategoryFilter.equals("All") || tx.category.contains(store.name(ledgerCategoryFilter)))
                        && (ledgerPaymentFilter.equals("All") || tx.paymentMode.contains(store.name(ledgerPaymentFilter)))
                        && (ledgerSpendingFilter.equals("All") || (Ledger.expense(tx)>0 && ledgerSpendingFilter.equals(SpendingType.normalize(tx.spendingType))))
                        && LedgerHome.matches(this,tx)
                        && (ledgerKindFilter.equals("All") || tx.kind.equals(ledgerKindFilter) || (ledgerKindFilter.equals("LENA/DENA") && (!tx.personId.isEmpty() || !Ledger.shares(tx).isEmpty())))
                        && (q.isEmpty() || searchable.contains(q))) {
                    txs.add(tx);
                }
            }
            java.util.Comparator<TransactionEntity> order;
            if (ledgerSortIndex == 1) order = (a, b) -> Long.compare(Ledger.at(a), Ledger.at(b));
            else if (ledgerSortIndex == 2) order = (a, b) -> Double.compare(b.amount, a.amount);
            else if (ledgerSortIndex == 3) order = (a, b) -> Double.compare(a.amount, b.amount);
            else order = (a, b) -> Long.compare(Ledger.at(b), Ledger.at(a));
            txs.sort(order);
            
            runOnUiThread(() -> {
                if (renderId != ledgerRenderId || currentScreen != Screen.LEDGER) return;
                while (list.getChildCount() > startIndex) list.removeViewAt(startIndex);
                if (txs.isEmpty()) {
                    TextView empty = Components.text(this, "No matching entries. Try another period or filter.", 15, Design.textSoft(this), Typeface.NORMAL);
                    empty.setPadding(pad, dp(48), pad, dp(48));
                    list.addView(empty);
                } else {
                    String lastDate = "";
                    for (TransactionEntity tx : txs) {
                        if (ledgerSortIndex <= 1 && !tx.date.equals(lastDate)) {
                            TextView dHeader = Components.text(this, tx.date, 13, Design.textSoft(this), Typeface.BOLD);
                            dHeader.setPadding(pad, dp(22), pad, dp(8));
                            list.addView(dHeader, match());
                            lastDate = tx.date;
                        }
                        list.addView(buildLedgerRow(tx, list));
                    }
                }
            });

    }

    private View buildLedgerRow(TransactionEntity tx, ViewGroup container) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, dp(14), pad, dp(14));
        row.setMinimumHeight(dp(69));
        
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        
        TextView cat = Components.text(this, tx.category, 16, Design.text(this), Typeface.BOLD);
        cat.setSingleLine(true);
        cat.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(cat);
        TextView typeLabel = Components.text(this, Ledger.label(tx)+("EXPENSE".equals(tx.kind)?" · "+SpendingType.label(tx.spendingType):""), 12, Design.primary(this), Typeface.BOLD);
        center.addView(typeLabel);
        
        String sub = tx.paymentMode + "  ·  " + tx.time;
        if (tx.remarks != null && !tx.remarks.isEmpty()) sub += "  ·  " + tx.remarks;
        if("EXPENSE".equals(tx.kind)&&Ledger.personal(tx)!=Ledger.amount(tx))sub+=" · Your expense "+store.currencySymbol()+Money.decimal(Ledger.personal(tx));
        if(tx.accountId.isEmpty()&&"EXPENSE".equals(tx.kind))sub+=" · Needs account";
        if (!TransactionEntity.SYNC_SYNCED.equals(tx.syncStatus)) sub += "  ·  Not synced";
        TextView subT = Components.text(this, sub, 12, Design.textSoft(this), Typeface.NORMAL);
        subT.setSingleLine(true);
        subT.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(subT);
        
        row.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        TextView amt = Components.text(this, (Ledger.income(tx)>0||"REPAYMENT_IN".equals(tx.kind)||"BORROW".equals(tx.kind)||"REFUND".equals(tx.kind)?"+":"TRANSFER".equals(tx.kind)?"↔ ":"−")+store.currencySymbol()+Money.decimal(Ledger.amount(tx)), 16, Design.text(this), Typeface.BOLD);
        amt.setPadding(dp(10), 0, 0, 0);
        amt.setSingleLine(true);
        row.addView(amt);
        
        row.setContentDescription(tx.category + ", " + SpendingType.label(tx.spendingType) + ", " + store.currencySymbol() + String.format(Locale.US, "%.2f", tx.amount) + ". Double tap for edit or delete.");
        row.setOnClickListener(v -> showTransactionActions(tx, (LinearLayout) container));
        row.setOnLongClickListener(v -> { showTransactionActions(tx, (LinearLayout)container); return true; });
        Components.addPressAnimation(row);
        
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, match());
        View rule = new View(this);
        rule.setBackgroundColor(Design.border(this));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        ruleParams.setMargins(pad, 0, pad, 0);
        wrapper.addView(rule, ruleParams);
        
        return wrapper;
    }

    private void showTransactionActions(TransactionEntity tx, LinearLayout container) {
        startActivity(LedgerActivity.intent(this,"detail").putExtra("id",tx.transactionId));
    }

    private void showSettings() {
        currentScreen = Screen.SETTINGS;
        applyWindowTheme();

        switch (currentSettingsSection) {
            case SYNC: showSyncSettings(); return;
            case PREFERENCES: showPreferencesSettings(); return;
            case CATEGORIES: showCategoriesSettings(); return;
            case DATA_SUPPORT: showDataSupportSettings(); return;
            default: showMainSettings();
        }
    }

    private void showMainSettings() {
        currentSettingsSection = SettingsSection.MAIN;
        LinearLayout page = page();
        settingsHeader(page, "Settings", "Your ledger, your way", true);

        LinearLayout syncPanel = new LinearLayout(this);
        syncPanel.setOrientation(LinearLayout.VERTICAL);
        syncPanel.setPadding(dp(20), dp(19), dp(20), dp(19));
        syncPanel.setBackground(Design.shape(Design.dark(this) ? Design.surfaceRaised(this) : Color.parseColor("#103F2B"), 18, this));
        syncPanel.addView(Components.text(this, "GOOGLE SHEETS", 11, Color.parseColor("#BCE1C9"), Typeface.BOLD));
        TextView syncState = Components.text(this, connectionStateText(), 23, Color.parseColor("#F3FCF5"), Typeface.BOLD);
        LinearLayout.LayoutParams syncStateParams = match();
        syncStateParams.setMargins(0, dp(8), 0, dp(4));
        syncPanel.addView(syncState, syncStateParams);
        syncPanel.addView(Components.text(this,
                store.url().isEmpty() ? "Your expenses stay on this device until you connect a Sheet."
                        : "Review your connection, sync pending entries, or restore data.",
                13, Color.parseColor("#BCE1C9"), Typeface.NORMAL));
        syncPanel.setContentDescription("Google Sheets. " + connectionStateText() + ". Open sync settings.");
        Components.addPressAnimation(syncPanel);
        syncPanel.setOnClickListener(v -> { currentSettingsSection = SettingsSection.SYNC; showSettings(); });
        LinearLayout.LayoutParams syncPanelParams = match();
        syncPanelParams.setMargins(0, dp(24), 0, dp(28));
        page.addView(syncPanel, syncPanelParams);

        TextView controls = Components.text(this, "Manage your ledger", 18, Design.text(this), Typeface.BOLD);
        LinearLayout.LayoutParams controlsParams = match();
        controlsParams.setMargins(0, 0, 0, dp(11));
        page.addView(controls, controlsParams);

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Design.shape(Design.surface(this), 18, this));
        group.addView(settingsSectionRow(NavIcon.ICON_GEAR, "Preferences", "Theme, currency and defaults", () -> { currentSettingsSection = SettingsSection.PREFERENCES; showSettings(); }));
        group.addView(settingsDivider());
        group.addView(settingsSectionRow(NavIcon.ICON_FILTER, "Categories & payments", "Edit your entry options", () -> { currentSettingsSection = SettingsSection.CATEGORIES; showSettings(); }));
        group.addView(settingsDivider());
        group.addView(settingsSectionRow(NavIcon.ICON_FAB, "Add home screen widget", "Quick expense entry, one tap away", this::addHomeScreenWidget));
        group.addView(settingsDivider());
        group.addView(settingsSectionRow(NavIcon.ICON_ARCHIVE, "Data & support", "Backups, privacy and help", () -> { currentSettingsSection = SettingsSection.DATA_SUPPORT; showSettings(); }));
        page.addView(group, match());

        present(showScreen(page),"SETTINGS:"+currentSettingsSection);
    }

    private void addHomeScreenWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        try {
            if (manager.isRequestPinAppWidgetSupported()
                    && manager.requestPinAppWidget(new ComponentName(this, AddExpenseWidgetProvider.class), null, null)) {
                Components.customToast(this, "Confirm the widget on your home screen.", false);
                return;
            }
        } catch (RuntimeException ignored) {
            // Some launchers advertise pinning but reject the request.
        }
        new AlertDialog.Builder(this)
                .setTitle("Add widget from home screen")
                .setMessage("Your launcher can't add the widget from inside the app. Long-press an empty spot on your home screen, choose Widgets, then select Pocket Ledger → Add Expense.")
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout settingsSectionRow(int iconType, String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(15), dp(16));
        row.setMinimumHeight(dp(76));
        LinearLayout iconTile = new LinearLayout(this);
        iconTile.setGravity(Gravity.CENTER);
        iconTile.setBackground(Design.shape(Design.primaryMuted(this), 12, this));
        iconTile.addView(new NavIcon(this, iconType, Design.primary(this)), new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.addView(iconTile, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(13), 0, dp(6), 0);
        labels.addView(Components.text(this, title, 15, Design.text(this), Typeface.BOLD));
        labels.addView(Components.text(this, detail, 13, Design.textSoft(this), Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(new NavIcon(this, NavIcon.ICON_CHEVRON, Design.muted(this)),
                new LinearLayout.LayoutParams(dp(22), dp(22)));
        Components.addPressAnimation(row);
        row.setOnClickListener(v -> action.run());
        row.setContentDescription(title + ". " + detail);
        return row;
    }
    private void showSyncSettings() {
        LinearLayout page = page();
        settingsHeader(page, "Sync & Sheets", "Your device stays the source of truth.", false);

        // Google Apps Script URL
        section(page, "Google Apps Script");
        settingRow(page, "Web App URL", connectionStateText(), true, connectionStateColor(), this::editUrl);
        settingRow(page, "Test Connection", "Checks accounts, people and ledger sync support", true, 0, this::testConnection);
        settingRow(page, "Sheet & dashboard setup", "Updated script for accounts, people and reports", true, 0, this::showSpendingSyncSetup);

        // Sync Operations
        section(page, "Sync actions");
        settingRow(page, "Sync Now", "Sync accounts, entries and people to Sheet", true, 0, this::syncNow);
        settingRow(page, "Refresh Sheet dashboard", "Refresh the existing Canvas data from your synced ledger", true, 0, this::refreshSheetDashboard);
        settingRow(page, "Restore Data from Sheet", "Merge the complete ledger from Sheet", true, 0, this::restoreFromSheet);

        // Sync Status
        section(page, "Status");
        String lastSync = store.lastSyncAt() == 0 ? "Never" : new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(store.lastSyncAt()));
        settingRow(page, "Last Sync", lastSync, false, 0, null);
        settingRow(page, "Device Name", store.deviceName(), true, 0, this::editDevice);

        present(showScreen(page),"SETTINGS:"+currentSettingsSection);
    }

    private void showSpendingSyncSetup() {
        new AlertDialog.Builder(this).setTitle("Set up your Android Sheet")
                .setMessage("Use your existing Sheet, or create a blank one in Google Sheets. Tracking works offline.\n\n1. Export a JSON backup in Data & support.\n2. In your Sheet’s desktop browser: Extensions → Apps Script. Replace the code with Copy updated script and save.\n3. Run setupPocketLedger once and authorize. For a clean Transactions report, also run setupCleanTransactions: the existing Canvas data moves to a hidden Sync Support sheet. Edit entries in the app or Dashboard.\n4. For an existing Web App: Deploy → Manage deployments → Edit → New version → Deploy. Keep the same /exec URL. For first setup: New deployment → Web app → Execute as Me → Anyone; keep the URL private and paste it into this app.\n5. Test Connection, set up accounts/opening balances and link payment methods in the app, then Sync Now.\n\nThe dashboard refreshes after sync. Date filters change income/spending; balances stay current. Split bills and saved split friends stay on this phone, outside backups and Sheet sync. Older apps cannot write after ledger sync is enabled.")
                .setNegativeButton("Close", null)
                .setNeutralButton("Copy updated script", (dialog,which)->executor.execute(()->{
                    try(InputStream in=getAssets().open("Pocket-Ledger-AppsScript.gs.txt")){
                        StringBuilder scriptText=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)scriptText.append(line).append("\n");}String script=scriptText.toString();
                        runOnUiThread(()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pocket Ledger Apps Script",script));Components.customToast(this,"Updated ledger script copied",false);});
                    }catch(Exception e){runOnUiThread(()->Components.customToast(this,"Could not copy script. Try again.",true));}
                }))
                .setPositiveButton("Open Google Sheets", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://sheets.google.com")));
                    } catch (android.content.ActivityNotFoundException error) {
                        Components.customToast(this, "No browser available to open Google Sheets", true);
                    }
                }).show();
    }

    private void showPreferencesSettings() {
        LinearLayout page = page();
        settingsHeader(page, "Preferences", "Make the app feel like yours.", false);

        // Appearance
        section(page, "Appearance");
        settingRow(page, "Theme", themeName(), true, 0, this::chooseTheme);

        // Regional
        section(page, "Regional");
        settingRow(page, "Currency Symbol", store.currencySymbol(), true, 0, this::editCurrency);

        // Entry Defaults
        section(page, "Entry defaults");
        settingRow(page, "Default Category", displayDefault(store.defaultCategory()), true, 0, this::chooseDefaultCategory);
        settingRow(page, "Default payment account", "Choose an account or use last selection", true, 0, this::chooseDefaultPayment);

        present(showScreen(page),"SETTINGS:"+currentSettingsSection);
    }

    private void showCategoriesSettings() {
        LinearLayout page = page();
        settingsHeader(page, "Entry options", "Keep categories and payment methods tidy.", false);

        // Categories
        section(page, "Categories");
        int catCount = store.categories().size();
        settingRow(page, "Manage Categories", catCount == 0 ? "Not set up yet" : catCount + " configured", true, 0, () -> manage("Categories", true));

        // Payment Modes
        section(page, "Payment methods");
        int payCount = store.paymentModes().size();
        settingRow(page, "Accounts & Payment Methods", "Balances, linked payments and aliases", true, 0, () -> manage("Payment Modes", false));

        present(showScreen(page),"SETTINGS:"+currentSettingsSection);
    }

    private void showDataSupportSettings() {
        LinearLayout page = page();
        settingsHeader(page, "Data & support", "Your records, under your control.", false);

        // Data Management
        section(page, "Data management");
        settingRow(page, "Export Backup", "Save ledger and preferences", true, 0, this::exportBackup);
        settingRow(page, "Import Backup", "Merge ledger and preferences safely", true, 0, this::importBackup);
        settingRow(page, "Export as Text", "Copy transactions only to clipboard", true, 0, this::exportData);
        settingRow(page, "Clear transaction history", "Fresh start · keep accounts and settings", true, 0, this::clearTransactionHistory);

        // Support
        section(page, "Support");
        settingRow(page, "Diagnostics", "Copy a safe report", true, 0, this::showDiagnostics);
        settingRow(page, "Contact Support", "Telegram @TechyBist", true, 0, this::contactSupport);

        // Information
        section(page, "Information");
        settingRow(page, "Privacy Policy", "How your data is used", true, 0, this::showPrivacyPolicy);

        TextView about = Components.text(this,
                "Pocket Ledger"
                        + "\nLocal-first. No background monitoring or scheduled sync.",
                13, Design.muted(this), Typeface.NORMAL);
        LinearLayout.LayoutParams aboutParams = match();
        aboutParams.setMargins(0, dp(12), 0, 0);
        page.addView(about, aboutParams);

        present(showScreen(page),"SETTINGS:"+currentSettingsSection);
    }

    private String connectionStateText() {
        String url = store.url();
        if (url.isEmpty()) return "Not configured";
        if (url.equals(store.verifiedUrl()) && store.spendingSyncVerified()) return "Connected";
        return "Configured · not yet tested";
    }
    private int connectionStateColor() {
        String url = store.url();
        if (url.isEmpty()) return Design.warning(this);
        if (url.equals(store.verifiedUrl()) && store.spendingSyncVerified()) return Design.success(this);
        return Design.warning(this);
    }
    private String themeName() { int t = store.theme(); return t == 1 ? "Light" : (t == 2 ? "Dark" : "System"); }
    private String displayDefault(String v) { return v.isEmpty() ? "No default" : v; }

    private void chooseTheme() {
        String[] options = {"System", "Light", "Dark"};
        new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(options, store.theme(), (d, w) -> {
            store.setTheme(w); d.dismiss(); showSettings();
        }).show();
    }

    private void editUrl() {
        prompt("Apps Script Web App URL", "Paste the HTTPS deployment URL", store.url(), value -> {
            if (!value.isEmpty() && !value.startsWith("https://")) { Components.customToast(this, "Use an HTTPS URL.", true); return; }
            if (!value.trim().equals(store.url())) store.clearVerifiedUrl();
            store.setUrl(value); showSettings();
        });
    }

    private void editDevice() {
        prompt("Device Name", "Used when syncing to Sheet", store.deviceName(), value -> {
            if (value.isEmpty()) { Components.customToast(this, "Device name cannot be empty.", true); return; }
            store.setDeviceName(value); showSettings();
        });
    }

    private void editCurrency() {
        prompt("Currency Symbol", "e.g. ₹ $ € £", store.currencySymbol(), value -> {
            if (!value.isEmpty()) store.setCurrencySymbol(value);
            showSettings();
        });
    }

    private void testConnection() {
        if (store.url().isEmpty()) { Components.customToast(this, "Add the Apps Script URL first.", true); return; }
        final String testedUrl = store.url();
        Components.customToast(this, "Testing connection…", false);
        executor.execute(() -> {
            SyncClient.Result r = SyncClient.test(testedUrl);
            runOnUiThread(() -> {
                // Keep the detailed technical error for Diagnostics; the dialog only shows the concise message.
                store.recordOperation("Connection test", r.success,
                        r.detail == null || r.detail.isEmpty() ? r.message : r.detail);
                if (r.success && testedUrl.equals(store.url())) store.setVerifiedUrl(testedUrl);
                else if (testedUrl.equals(store.url())) store.clearVerifiedUrl();
                new AlertDialog.Builder(this)
                        .setTitle(r.success ? "Connection Test" : "Connection Failed")
                        .setMessage(r.message)
                        .setPositiveButton("OK", null).show();
                showSettings();
            });
        });
    }

    private boolean clearingHistory;
    private void clearTransactionHistory(){
        if(clearingHistory)return;
        new AlertDialog.Builder(this).setTitle("Clear all transaction history?")
                .setMessage("Remove all expense, income, transfer and lending/repayment entries from this app"+(store.url().isEmpty()?".":" and the connected Google Sheet.")+"\n\nAccounts, payment links, opening balances, settings and separate split bills stay. Current balances return to their opening amounts; people dues become zero. Set today's actual opening balances afterwards.\n\nOld history cannot be restored by importing an old backup.")
                .setNegativeButton("Cancel",null).setPositiveButton("Clear history",(dialog,which)->{
                    if(clearingHistory)return;clearingHistory=true;Components.customToast(this,"Clearing transaction history…",false);
                    executor.execute(()->{
                        try{
                            SyncEngine.SyncResult result=SyncEngine.clearTransactionHistory(getApplicationContext(),store);
                            store.recordOperation("Clear transaction history",result.failed==0,result.failed==0?"History cleared":"Phone cleared; Sheet sync pending");
                            runOnUiThread(()->{clearingHistory=false;if(isFinishing()||isDestroyed())return;reloadLedger();
                                if(result.failed==0)Components.customToast(this,store.url().isEmpty()?"Transaction history cleared":"App and Sheet history cleared",false);
                                else new AlertDialog.Builder(this).setTitle("Phone history cleared").setMessage("Sheet sync is pending. Use Sync Now when connected.\n\n"+result.lastError).setPositiveButton("OK",null).show();
                            });
                        }catch(Exception e){runOnUiThread(()->{clearingHistory=false;if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("History was not cleared").setMessage("Could not read the connected ledger. Check your connection and retry.\n\n"+e.getMessage()).setPositiveButton("OK",null).show();});}
                    });
                }).show();
    }
    private void syncNow() {
        if (store.url().isEmpty()) {
            Components.customToast(this, "Configure the Apps Script URL first.", true);
            return;
        }
        Components.customToast(this, "Syncing with Google Sheet…", false);
        executor.execute(() -> {
            // Shared engine processes ALL pending (new + previously failed) transactions.
            SyncEngine.SyncResult result = SyncEngine.syncAllPending(this, store);

            long now = System.currentTimeMillis();
            if (result.failed == 0) store.recordSync(now);
            store.recordOperation("Sync", result.failed == 0,
                    result.synced + " synced" + (result.failed > 0 ? ", " + result.failed + " failed: " + result.lastError : result.lastError.isEmpty()?"":", Dashboard: "+result.lastError));

            final int finalSuccess = result.synced;
            final int finalFail = result.failed;
            final String finalErr = result.lastError;
            runOnUiThread(() -> {
                reloadLedger();
                if (finalFail == 0) {
                    Components.customToast(this, "Sync complete (" + finalSuccess + " items).", false);
                    if(!finalErr.isEmpty())new AlertDialog.Builder(this).setTitle("Data synced · dashboard needs attention").setMessage(finalErr+"\n\nYour ledger was saved. Update Apps Script if needed, then use Refresh Sheet dashboard.").setPositiveButton("OK",null).show();
                } else {
                    Components.customToast(this, "Synced " + finalSuccess + ", " + finalFail + " failed: " + finalErr, true);
                }
                if (currentScreen == Screen.SETTINGS) showSettings();
            });
        });
    }

    private void refreshSheetDashboard() {
        if(store.url().isEmpty()){Components.customToast(this,"Configure the Apps Script URL first.",true);return;}
        Components.customToast(this,"Refreshing Sheet dashboard…",false);
        executor.execute(()->{
            try{SyncClient.refreshDashboard(store.url());store.recordOperation("Refresh dashboard",true,"Reports rebuilt from the synced ledger");runOnUiThread(()->Components.customToast(this,"Sheet dashboard refreshed",false));}
            catch(Exception e){store.recordOperation("Refresh dashboard",false,e.getMessage());runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Dashboard could not refresh").setMessage("Update Apps Script using Sheet & dashboard setup, then retry.\n\n"+e.getMessage()).setPositiveButton("OK",null).show());}
        });
    }

    private void restoreFromSheet() {
        if(isRestoring)return;
        new AlertDialog.Builder(this).setTitle("Restore ledger from Sheet?").setMessage("Merge accounts, entries and people. Newer local data is preserved; invalid linked records are rejected together.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{
            isRestoring=true;executor.execute(()->{
                try{LedgerJson.importLedger(AppDatabase.get(this),SyncClient.restoreLedger(store.url()));runOnUiThread(()->{isRestoring=false;Components.customToast(this,"Ledger restored",false);ledgerCache=null;homeScene=null;activityScene=null;showSettings();});}
                catch(Exception e){runOnUiThread(()->{isRestoring=false;new AlertDialog.Builder(this).setTitle("Restore failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();});}
            });
        }).show();
    }

    private static final int REQ_CREATE_BACKUP = 2001;
    private static final int REQ_OPEN_BACKUP   = 2002;

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String dateSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "pocket_ledger_backup_" + dateSuffix + ".json");
        try {
            startActivityForResult(intent, REQ_CREATE_BACKUP);
        } catch (Exception e) {
            Components.customToast(this, "No file manager found to save backup file.", true);
        }
    }

    private void importBackup() {
        new AlertDialog.Builder(this)
                .setTitle("Import backup?")
                .setMessage("Ledger entries will merge while preserving newer local records. Split bills and saved split friends stay on this phone and are excluded from backup/import.\n\nSaved preferences, categories and payment options replace current settings when included.\n\nYour Google Sheets connection will stay the same. Choose a backup file?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose File", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    String[] mimes = {"application/json", "text/plain", "application/octet-stream"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
                    try {
                        startActivityForResult(intent, REQ_OPEN_BACKUP);
                    } catch (Exception e) {
                        Components.customToast(this, "No file manager found to open backup file.", true);
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_CREATE_BACKUP) {
            performExport(uri);
        } else if (requestCode == REQ_OPEN_BACKUP) {
            performImport(uri);
        }
    }

    private void performExport(Uri uri) {
        Components.customToast(this, "Exporting backup…", false);
        executor.execute(() -> {
            try {
                List<TransactionEntity> all = AppDatabase.get(this).transactionDao().getAllForBackup();
                String json = BackupManager.createBackupJson(this, all);
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Could not access destination storage.");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> {
                    String msg = "Backup exported successfully!\n\nTransactions exported: " + all.size()
                            + "\nAccounts, people and preferences included. Split bills stay on this phone."
                            + "\nGoogle Sheets connection is not included.";
                    new AlertDialog.Builder(this)
                            .setTitle("Backup Exported")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                    store.recordOperation("Export Backup", true, all.size() + " transactions and settings exported");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Export Failed")
                            .setMessage("Could not save backup: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    store.recordOperation("Export Backup", false, e.getMessage());
                });
            }
        });
    }

    private void performImport(Uri uri) {
        Components.customToast(this, "Reading backup file…", false);
        executor.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                try (InputStream is = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    if (is == null) throw new Exception("Could not open selected file.");
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                }
                String content = sb.toString();

                BackupManager.ValidationResult val = BackupManager.validateBackup(content);
                if (!val.success) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Invalid Backup File")
                                .setMessage(val.error)
                                .setPositiveButton("OK", null)
                                .show();
                        store.recordOperation("Import Backup", false, val.error);
                    });
                    return;
                }

                BackupManager.MergeResult mr = LedgerJson.importLedger(AppDatabase.get(this),val.ledger!=null?val.ledger:new Ledger(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),val.transactions));
                boolean settingsRestored = val.settings == null || store.restoreBackupSettings(val.settings);
                boolean importComplete = settingsRestored;
                if(val.preferences!=null){
                    org.json.JSONArray categories=val.preferences.getJSONArray("incomeCategories");List<String> income=new ArrayList<>();for(int i=0;i<categories.length();i++)income.add(categories.getString(i));store.setIncomeCategories(income);
                    android.content.SharedPreferences.Editor ui=getSharedPreferences("ledger_ui",0).edit();for(String key:new String[]{"defaultPayment","lastPayment","selectedAccount"})ui.putString(key,val.preferences.getString(key));ui.apply();
                }
                store.recordOperation("Import Backup", importComplete, "Added: " + mr.added
                        + ", Updated: " + mr.updated + ", Deleted: " + mr.deleted
                        + ", Settings: " + (val.settings == null ? "not in backup" : settingsRestored ? "restored" : "failed"));

                runOnUiThread(() -> {
                    String msg = (importComplete ? "Backup imported.\n\n" : "Ledger imported. Some backup data needs another restore attempt.\n\n")
                            + "Total in backup: " + mr.total + "\n"
                            + "• Added: " + mr.added + "\n"
                            + "• Updated: " + mr.updated + "\n"
                            + "• Deleted (tombstoned): " + mr.deleted + "\n"
                            + "• Preserved (newer local): " + mr.preserved + "\n\n"
                            + (val.settings == null ? "Older backup: your current settings were kept."
                                : settingsRestored ? "Preferences, categories and payment methods restored in their saved order. Google Sheets connection was kept."
                                : "Please retry the import to restore your preferences.");

                    new AlertDialog.Builder(this)
                            .setTitle(importComplete ? "Import Complete" : "Import Partly Complete")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();

                    reloadLedger();
                    if (currentScreen == Screen.SETTINGS) showSettings();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Import Failed")
                            .setMessage("Error reading file: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    store.recordOperation("Import Backup", false, e.getMessage());
                });
            }
        });
    }

    private void exportData() {
        executor.execute(() -> {
            List<TransactionEntity> txs = AppDatabase.get(this).transactionDao().getAll();
            StringBuilder sb = new StringBuilder("Pocket Ledger Export\n\n");
            String cur = store.currencySymbol();
            Ledger ledger = Ledger.load(AppDatabase.get(this));
            sb.append("Accounts\n");
            for(AccountEntity account:ledger.accounts) if(!account.deleted) sb.append(ledger.accountName(account.accountId)).append(" | ").append(account.isCard()?"Signed card balance: ":"Balance: ").append(cur).append(Money.decimal(ledger.balance(account))).append(account.archived?" | Archived":"").append("\n");
            sb.append("\nTo receive: ").append(cur).append(Money.decimal(ledger.owedTotal(true))).append(" | To pay: ").append(cur).append(Money.decimal(ledger.owedTotal(false))).append("\n");
            for(Ledger.Owed owed:ledger.owed()) if(owed.remaining>0) sb.append(ledger.personName(owed.personId)).append(owed.receivable?" | To receive: ":" | To pay: ").append(cur).append(Money.decimal(owed.remaining)).append(" | ").append(owed.origin.date).append("\n");
            sb.append("\nActivity\n");
            for (TransactionEntity tx : txs) {
                sb.append(tx.date).append(" ").append(tx.time).append(" | ")
                  .append(cur).append(Money.decimal(Ledger.amount(tx))).append(" | ")
                  .append(Ledger.label(tx)).append(" | ").append(tx.category).append(" | ").append(tx.paymentMode).append(" | Your expense: ").append(cur).append(Money.decimal(Ledger.expense(tx)))
                  .append(" | Account: ").append(ledger.accountName(tx.accountId)).append(tx.destinationId.isEmpty()?"":" → "+ledger.accountName(tx.destinationId));
                if(!tx.personId.isEmpty())sb.append(" | Person: ").append(ledger.personName(tx.personId));
                for(Map.Entry<String,Long> share:Ledger.shares(tx).entrySet())sb.append(" | ").append(ledger.personName(share.getKey())).append(": ").append(cur).append(Money.decimal(share.getValue()));
                if (!tx.remarks.isEmpty()) sb.append(" | ").append(tx.remarks);
                sb.append("\n");
            }
            String report = sb.toString();
            runOnUiThread(() -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("Pocket Ledger Export", report));
                Components.customToast(this, txs.size() + " transactions copied to clipboard.", false);
            });
        });
    }

    private void chooseDefaultCategory() { chooseDefault("Default Category", store.categories(), store.defaultCategory(), v -> { store.setDefaultCategory(v); showSettings(); }); }
    private void chooseDefaultPayment() { startActivity(LedgerActivity.intent(this,"accounts")); }
    private void chooseDefault(String title, List<String> values, String current, ValueHandler handler) {
        List<String> options = new ArrayList<>(); options.add("No default"); options.addAll(values);
        int selected = current.isEmpty() ? 0 : Math.max(0, options.indexOf(current));
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]), selected,
                (d, w) -> { handler.accept(w == 0 ? "" : options.get(w)); d.dismiss(); }).show();
    }

    private void manage(String title, boolean category) {
        if (!category) { startActivity(LedgerActivity.intent(this,"accounts")); return; }
        List<String> values = category ? store.categories() : store.paymentModes();
        ReorderableListDialog.show(this, title, values,
                item -> itemActions(title, category, item),
                () -> addItem(title, category),
                newOrder -> { if (category) store.setCategories(newOrder); else store.setPaymentModes(newOrder); });
    }
    private void itemActions(String title, boolean category, String current) {
        new AlertDialog.Builder(this).setTitle(current).setItems(new String[]{"Rename", "Change emoji", "Delete"}, (d, w) -> {
            if      (w == 0) renameItem(title, category, current);
            else if (w == 1) changeEmoji(title, category, current);
            else             deleteItem(title, category, current);
        }).show();
    }
    private void changeEmoji(String title, boolean category, String current) {
        String[] icons = category
                ? new String[]{"🍔","🛍️","🚗","💡","🎬","🏥","🏠","📚","💰","🏋️","✈️","🎁"}
                : new String[]{"📱","💵","💳","🏦","👛","📲","🟢","🟣","💰"};
        new AlertDialog.Builder(this).setTitle("Choose emoji").setItems(icons, (d, w) -> {
            updateList(category, current, store.format(store.name(current), icons[w]));
            manage(title, category);
        }).show();
    }
    private void addItem(String title, boolean category) { prompt("Add " + title.substring(0, title.length()-1), "Name", "", value -> { if (updateList(category, null, value)) manage(title, category); }); }
    private void renameItem(String title, boolean category, String current) { prompt("Rename", "Name", store.name(current), value -> { if (updateList(category, current, value)) manage(title, category); }); }
    private void deleteItem(String title, boolean category, String current) {
        new AlertDialog.Builder(this).setTitle("Delete \u201C" + current + "\u201D?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    List<String> values = category ? store.categories() : store.paymentModes();
                    if (values.size() == 1) { Components.customToast(this, "Keep at least one option.", true); return; }
                    values.remove(current);
                    if (category) { store.setCategories(values); if (current.equals(store.defaultCategory())) store.setDefaultCategory(""); }
                    else          { store.setPaymentModes(values); if (current.equals(store.defaultPaymentMode())) store.setDefaultPaymentMode(""); }
                    manage(title, category);
                }).show();
    }
    private boolean updateList(boolean category, String oldValue, String newValue) {
        String clean = newValue.trim();
        if (clean.isEmpty() || clean.contains("\u001F")) { Components.customToast(this, "Enter a valid name.", true); return false; }
        clean = store.canonical(clean, oldValue == null ? (category ? "🏷️" : "💳") : store.emoji(oldValue));
        List<String> values = category ? store.categories() : store.paymentModes();
        for (String item : values) if (!item.equals(oldValue) && store.backendValue(item).equalsIgnoreCase(store.backendValue(clean))) { Components.customToast(this, "That option already exists.", true); return false; }
        if (oldValue == null) values.add(clean);
        else { int i = values.indexOf(oldValue); values.set(i, clean); if (category && oldValue.equals(store.defaultCategory())) store.setDefaultCategory(clean); if (!category && oldValue.equals(store.defaultPaymentMode())) store.setDefaultPaymentMode(clean); }
        if (category) store.setCategories(values); else store.setPaymentModes(values); return true;
    }

    private void showDiagnostics() {
        String ts = store.lastAt() == 0 ? "Not available"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(store.lastAt()));
        String status = store.lastSuccess() ? "Success" : "Needs attention";
        String report = "Pocket Ledger\nVersion: " + appVersion()
                + "\nAndroid: " + Build.VERSION.RELEASE
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n\nLast operation: " + store.lastOperation()
                + "\nStatus: " + status
                + "\nDetail: " + store.lastDetail()
                + "\nTimestamp: " + ts
                + "\n\nSupport: Telegram @TechyBist";

        new AlertDialog.Builder(this).setTitle("Diagnostics")
                .setMessage(report)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy", (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(ClipData.newPlainText("Pocket Ledger diagnostics", report));
                    Components.customToast(this, "Copied to clipboard", false);
                }).show();
    }

    private void contactSupport() {
        Intent telegram = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?domain=TechyBist"));
        Intent web      = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/TechyBist"));
        startActivity(telegram.resolveActivity(getPackageManager()) != null ? telegram : web);
    }

    private void showPrivacyPolicy() {
        String policy = "Pocket Ledger — Privacy Policy\n\n"
                + "LOCAL DATA:\n"
                + "All expense data is stored on your device in a local database. "
                + "Data is not sent to Google Sheets unless you configure a sync URL.\n\n"
                + "GOOGLE SHEETS (OPTIONAL):\n"
                + "If you configure a Google Apps Script URL, saved expenses are also synced "
                + "after entry. You can manually sync pending expenses in Settings.\n\n"
                + "ANONYMOUS USAGE STATISTICS:\n"
                + "A random anonymous installation ID may be used to track basic usage metrics "
                + "(app opens, active usage). No expense amounts, categories, remarks, or any "
                + "personal financial data are ever included.\n\n"
                + "BACKGROUND ACTIVITY:\n"
                + "Pocket Ledger does not run background services, location tracking, "
                + "sensor monitoring, or scheduled sync. The optional sync attempt after saving "
                + "runs while the app is active.\n\n"
                + "Support: Telegram @TechyBist";

        new AlertDialog.Builder(this).setTitle("Privacy Policy")
                .setMessage(policy)
                .setPositiveButton("Close", null).show();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(pad, dp(18), pad, dp(32));
        p.setBackgroundColor(Design.background(this));
        activeSettingsGroup = null;
        return p;
    }

    private void settingsHeader(LinearLayout page, String title, String summary, boolean root) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout back = new LinearLayout(this);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Design.shape(Design.surface(this), 12, this));
        back.addView(new NavIcon(this, NavIcon.ICON_BACK, Design.text(this)),
                new LinearLayout.LayoutParams(dp(24), dp(24)));
        back.setContentDescription(root ? "Back to home" : "Back to settings");
        Components.addPressAnimation(back);
        back.setOnClickListener(v -> {
            if (root) showHome();
            else { currentSettingsSection = SettingsSection.MAIN; showSettings(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, 0, 0);
        labels.addView(Components.text(this, title, 26, Design.text(this), Typeface.BOLD));
        labels.addView(Components.text(this, summary, 13, Design.textSoft(this), Typeface.NORMAL));
        top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(top, match());
    }

    private View settingsDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Design.border(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(16), 0, dp(16), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private void section(LinearLayout page, String text) {
        TextView heading = Components.text(this, text.toUpperCase(Locale.ROOT), 12, Design.textSoft(this), Typeface.BOLD);
        heading.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams headingParams = match();
        headingParams.setMargins(0, dp(27), 0, dp(10));
        page.addView(heading, headingParams);
        activeSettingsGroup = new LinearLayout(this);
        activeSettingsGroup.setOrientation(LinearLayout.VERTICAL);
        activeSettingsGroup.setBackground(Design.shape(Design.surface(this), 16, this));
        page.addView(activeSettingsGroup, match());
    }

    private void settingRow(LinearLayout page, String title, String detail, boolean actionable, int statusColor, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setMinimumHeight(dp(72));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Components.text(this, title, 15, Design.text(this), Typeface.BOLD));
        if (detail != null && !detail.isEmpty()) {
            TextView subtitle = Components.text(this, detail, 13,
                    statusColor == 0 ? Design.textSoft(this) : statusColor,
                    statusColor == 0 ? Typeface.NORMAL : Typeface.BOLD);
            subtitle.setPadding(0, dp(3), 0, 0);
            labels.addView(subtitle);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actionable && action != null) {
            row.addView(new NavIcon(this, NavIcon.ICON_CHEVRON, Design.muted(this)),
                    new LinearLayout.LayoutParams(dp(22), dp(22)));
            Components.addPressAnimation(row);
            row.setOnClickListener(v -> action.run());
        }
        row.setContentDescription(title + (detail == null || detail.isEmpty() ? "" : ". " + detail));
        LinearLayout group = activeSettingsGroup == null ? page : activeSettingsGroup;
        if (group.getChildCount() > 0) group.addView(settingsDivider());
        group.addView(row, match());
    }

    private void prompt(String title, String hint, String current, ValueHandler handler) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> handler.accept(input.getText().toString()))
                .create().show();
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return Design.dp(this, value); }
    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "Unknown"; }
    }

    private interface ValueHandler { void accept(String value); }
}
