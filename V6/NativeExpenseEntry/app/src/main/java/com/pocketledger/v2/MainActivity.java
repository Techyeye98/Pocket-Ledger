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
        super.onResume();
        // Refresh the current screen when returning from QuickEntry (new expense added)
        if (currentScreen == Screen.HOME) showHome();
        else if (currentScreen == Screen.LEDGER) showLedger();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (ACTION_ADD_EXPENSE.equals(intent.getAction()))
            startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE));
        else if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings();
    }

    @Override protected void onDestroy() {
        if (instance == this) instance = null;
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
    static void notifyPendingSyncCompleted() {
        MainActivity a = instance;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.currentScreen == Screen.LEDGER) a.showLedger();
            else if (a.currentScreen == Screen.HOME) a.showHome();
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(this));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, dp(18), pad, dp(16));

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
        statement.setPadding(dp(24), dp(24), dp(24), dp(22));
        statement.setBackground(Design.shape(Design.dark(this) ? Design.surface(this) : Color.parseColor("#103F2B"), 20, this));
        int statementInk = Color.parseColor("#F3FCF5");
        int statementSoft = Color.parseColor("#BCE1C9");
        TextView statementLabel = Components.text(this, selectedRangeLabel + " spending", 14, statementSoft, Typeface.NORMAL);
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

        root.addView(buildDateRangeSelector(), match());

        LinearLayout chartsBlock = new LinearLayout(this);
        chartsBlock.setOrientation(LinearLayout.VERTICAL);
        chartsBlock.setPadding(pad, dp(18), pad, dp(12));
        chartsBlock.setTag("charts_block");
        root.addView(chartsBlock, match());

        LinearLayout recentBlock = new LinearLayout(this);
        recentBlock.setOrientation(LinearLayout.VERTICAL);
        recentBlock.setTag("recent_block");
        root.addView(recentBlock, match());

        loadDashboardData(root);
        setContentView(showScreen(root));
    }
    
    private void loadDashboardData(LinearLayout page) {
        executor.execute(() -> {
            TransactionDao dao = AppDatabase.get(this).transactionDao();
            List<TransactionEntity> txs = dao.getByTimeRange(rangeFromMs, rangeToMs);
            
            List<TransactionEntity> allTxs = dao.getAll();
            java.util.Collections.sort(allTxs, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            List<TransactionEntity> recent = allTxs.subList(0, Math.min(5, allTxs.size()));
            
            double total = 0;
            java.util.Map<String, Double> catsMap = new java.util.LinkedHashMap<>();
            java.util.Map<String, Double> dailyMap = new java.util.TreeMap<>();
            SpendingInsights spendingInsights = new SpendingInsights();
            
            for (TransactionEntity tx : txs) {
                total += tx.amount;
                spendingInsights.add(tx.amount, tx.spendingType, tx.category);
                catsMap.put(tx.category, catsMap.getOrDefault(tx.category, 0.0) + tx.amount);
                String day = dayKey(tx);
                dailyMap.put(day, dailyMap.getOrDefault(day, 0.0) + tx.amount);
            }
            
            final double finalTotal = total;
            final int finalCount = txs.size();
            final double finalAvg = finalCount > 0 ? (total / Math.max(1, dailyMap.size())) : 0;
            
            List<DonutChartView.CategorySpend> cats = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, Double> e : catsMap.entrySet()) {
                cats.add(new DonutChartView.CategorySpend(e.getKey(), e.getValue()));
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
                            String share = String.format(Locale.US, "%.0f%%", item.amount / finalTotal * 100);
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
                    if (finalCount > 0) addSpendingInsights(chartsBlock, spendingInsights);
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
                        store.setRecentEntriesHidden(hide);
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
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int i = 0; i < 3; i++) {
            if (insights.amounts[i] <= 0) continue;
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
            TextView share = Components.text(this, String.format(Locale.US, "%.1f%% of spending", insights.share(i)), 12, Design.textSoft(this), Typeface.NORMAL);
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
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(tx.createdAt));
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
        fab.setOnClickListener(v -> startActivity(new Intent(this, QuickEntryActivity.class)));
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
        if (currentScreen != Screen.SETTINGS) root.addView(buildBottomNavigation(), match());
        return root;
    }

    private void showLedger() {
        currentScreen = Screen.LEDGER;
        applyWindowTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Design.background(this));
        TextView title = Components.text(this, "Activity", 30, Design.text(this), Typeface.BOLD);
        title.setPadding(pad, dp(22), pad, 0);
        root.addView(title);
        TextView description = Components.text(this, "Every expense, in order.", 14, Design.textSoft(this), Typeface.NORMAL);
        description.setPadding(pad, dp(2), pad, dp(17));
        root.addView(description);
        root.addView(buildDateRangeSelector(), match());

        EditText search = new EditText(this);
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

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(pad, 0, pad, dp(13));
        filters.addView(filterChip("Category: " + ledgerCategoryFilter, () -> chooseLedgerFilter(true)), new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams paymentParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        paymentParams.setMargins(dp(8), 0, dp(8), 0);
        filters.addView(filterChip("Payment: " + ledgerPaymentFilter, () -> chooseLedgerFilter(false)), paymentParams);
        filters.addView(filterChip("Sort", this::chooseLedgerSort), new LinearLayout.LayoutParams(dp(64), dp(44)));
        root.addView(filters);

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
        root.addView(typeScroll);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, match());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ledgerSearchQuery = s.toString();
                renderLedgerItems(list, ledgerSearchQuery, 0);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        renderLedgerItems(list, ledgerSearchQuery, 0);

        setContentView(showScreen(root));
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
        List<String> values = category ? store.categories() : store.paymentModes();
        String[] options = new String[values.size() + 1];
        options[0] = "All";
        for (int i = 0; i < values.size(); i++) options[i + 1] = values.get(i);
        new AlertDialog.Builder(this).setTitle(category ? "Category" : "Payment method")
                .setItems(options, (dialog, which) -> {
                    if (category) ledgerCategoryFilter = options[which];
                    else ledgerPaymentFilter = options[which];
                    showLedger();
                }).show();
    }

    private void chooseLedgerSort() {
        String[] options = {"Newest first", "Oldest first", "Amount: high to low", "Amount: low to high"};
        new AlertDialog.Builder(this).setTitle("Sort entries")
                .setSingleChoiceItems(options, ledgerSortIndex, (dialog, which) -> {
                    ledgerSortIndex = which;
                    dialog.dismiss();
                    showLedger();
                }).show();
    }

    private void renderLedgerItems(LinearLayout list, String query, int startIndex) {
        final int renderId = ++ledgerRenderId;
        executor.execute(() -> {
            List<TransactionEntity> allTxs = AppDatabase.get(this).transactionDao().getByTimeRange(rangeFromMs, rangeToMs);
            List<TransactionEntity> txs = new java.util.ArrayList<>();
            String q = query.toLowerCase(java.util.Locale.US);
            for(TransactionEntity tx : allTxs) {
                String searchable = (tx.category + " " + tx.paymentMode + " " + tx.remarks + " "
                        + tx.date + " " + tx.amount).toLowerCase(Locale.US);
                if ((ledgerCategoryFilter.equals("All") || tx.category.contains(store.name(ledgerCategoryFilter)))
                        && (ledgerPaymentFilter.equals("All") || tx.paymentMode.contains(store.name(ledgerPaymentFilter)))
                        && (ledgerSpendingFilter.equals("All") || ledgerSpendingFilter.equals(SpendingType.normalize(tx.spendingType)))
                        && (q.isEmpty() || searchable.contains(q))) {
                    txs.add(tx);
                }
            }
            java.util.Comparator<TransactionEntity> order;
            if (ledgerSortIndex == 1) order = (a, b) -> Long.compare(a.createdAt, b.createdAt);
            else if (ledgerSortIndex == 2) order = (a, b) -> Double.compare(b.amount, a.amount);
            else if (ledgerSortIndex == 3) order = (a, b) -> Double.compare(a.amount, b.amount);
            else order = (a, b) -> Long.compare(b.createdAt, a.createdAt);
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
        TextView typeLabel = Components.text(this, SpendingType.label(tx.spendingType), 12, Design.primary(this), Typeface.BOLD);
        center.addView(typeLabel);
        
        String sub = tx.paymentMode + "  ·  " + tx.time;
        if (tx.remarks != null && !tx.remarks.isEmpty()) sub += "  ·  " + tx.remarks;
        if (!TransactionEntity.SYNC_SYNCED.equals(tx.syncStatus)) sub += "  ·  Not synced";
        TextView subT = Components.text(this, sub, 12, Design.textSoft(this), Typeface.NORMAL);
        subT.setSingleLine(true);
        subT.setEllipsize(android.text.TextUtils.TruncateAt.END);
        center.addView(subT);
        
        row.addView(center, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        TextView amt = Components.text(this, "−" + store.currencySymbol() + String.format(Locale.US, "%.2f", tx.amount), 16, Design.text(this), Typeface.BOLD);
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
        String summary = String.format(java.util.Locale.US, "%.2f", tx.amount) + " / " + tx.category;
        new android.app.AlertDialog.Builder(this)
                .setTitle(summary)
                .setItems(new String[]{"Edit", "Set Need / Want", "Delete"}, (d, which) -> {
                    if (which == 0) showEditTransaction(tx, container);
                    else if (which == 1) chooseTransactionType(tx);
                    else showDeleteConfirmation(tx, container);
                }).show();
    }

    private void chooseTransactionType(TransactionEntity tx) {
        String[] types = {SpendingType.NEED, SpendingType.WANT, SpendingType.UNTAGGED};
        new AlertDialog.Builder(this).setTitle("Was this a need or a want?")
                .setItems(new String[]{"Need · essential", "Want · optional", "Untagged · clear selection"}, (dialog, index) -> {
                    if (types[index].equals(SpendingType.normalize(tx.spendingType))) return;
                    tx.spendingType = types[index];
                    tx.updatedAt = System.currentTimeMillis();
                    if (!TransactionEntity.SYNC_LOCAL_ONLY.equals(tx.syncStatus)) tx.syncStatus = TransactionEntity.SYNC_PENDING;
                    executor.execute(() -> {
                        AppDatabase.get(this).transactionDao().update(tx);
                        runOnUiThread(() -> { Components.customToast(this, "Spending type saved", false); showLedger(); });
                    });
                }).show();
    }

    private void showEditTransaction(TransactionEntity tx, LinearLayout container) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, dp(10), pad, dp(6));

        EditText amtField = new EditText(this);
        amtField.setHint("Amount");
        amtField.setText(String.format(java.util.Locale.US, "%.2f", tx.amount));
        amtField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amtField);

        EditText noteField = new EditText(this);
        noteField.setHint("Note (optional)");
        noteField.setText(tx.remarks);
        LinearLayout.LayoutParams noteParams = match();
        noteParams.setMargins(0, dp(8), 0, 0);
        layout.addView(noteField, noteParams);

        final String[] category = {tx.category};
        final String[] payment = {tx.paymentMode};
        Button categoryButton = Components.secondaryButton(this, "Category: " + category[0]);
        LinearLayout.LayoutParams categoryParams = match();
        categoryParams.setMargins(0, dp(13), 0, 0);
        layout.addView(categoryButton, categoryParams);
        categoryButton.setOnClickListener(v -> {
            List<String> options = store.categories();
            new AlertDialog.Builder(this).setTitle("Category")
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        category[0] = options.get(which);
                        categoryButton.setText("Category: " + category[0]);
                    }).show();
        });

        Button paymentButton = Components.secondaryButton(this, "Payment: " + payment[0]);
        LinearLayout.LayoutParams paymentParams = match();
        paymentParams.setMargins(0, dp(8), 0, 0);
        layout.addView(paymentButton, paymentParams);
        paymentButton.setOnClickListener(v -> {
            List<String> options = store.paymentModes();
            new AlertDialog.Builder(this).setTitle("Payment method")
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        payment[0] = options.get(which);
                        paymentButton.setText("Payment: " + payment[0]);
                    }).show();
        });

        TextView typeHeading = Components.text(this, "Need or want? (optional)", 14, Design.text(this), Typeface.BOLD);
        typeHeading.setPadding(0, dp(16), 0, dp(8));
        layout.addView(typeHeading);
        SpendingTypePicker typePicker = new SpendingTypePicker(this, tx.spendingType);
        layout.addView(typePicker, match());
        ScrollView editScroll = new ScrollView(this);
        editScroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit expense")
                .setView(editScroll).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double amount;
            try { amount = Double.parseDouble(AmountUtil.normalize(amtField.getText().toString())); }
            catch (Exception error) { Components.customToast(this, "Enter a valid amount", true); return; }
            if (!Double.isFinite(amount) || amount <= 0) {
                Components.customToast(this, "Amount must be greater than zero", true);
                return;
            }
            tx.amount = amount;
            tx.category = category[0];
            tx.paymentMode = payment[0];
            tx.remarks = noteField.getText().toString().trim();
            tx.spendingType = typePicker.getValue();
            tx.updatedAt = System.currentTimeMillis();
            if (!TransactionEntity.SYNC_LOCAL_ONLY.equals(tx.syncStatus)) tx.syncStatus = TransactionEntity.SYNC_PENDING;
            dialog.dismiss();
            executor.execute(() -> {
                AppDatabase.get(this).transactionDao().update(tx);
                runOnUiThread(() -> {
                    Components.customToast(this, "Expense updated", false);
                    showLedger();
                });
            });
        }));
        dialog.show();
    }

    private void showDeleteConfirmation(TransactionEntity tx, LinearLayout container) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete expense?")
                .setMessage("This removes the entry locally. If Sheets is connected, the change is sent on the next sync.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    executor.execute(() -> {
                        AppDatabase.get(this).transactionDao().softDelete(tx.transactionId, System.currentTimeMillis());
                        runOnUiThread(() -> {
                            Components.customToast(this, "Expense deleted", false);
                            showLedger();
                        });
                    });
                }).show();
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

        setContentView(showScreen(page));
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
        settingRow(page, "Test Connection", "Checks connection and Need/Want sync support", true, 0, this::testConnection);
        settingRow(page, "Need / Want sync setup", "Open the latest Android Sheet template", true, 0, this::showSpendingSyncSetup);

        // Sync Operations
        section(page, "Sync actions");
        settingRow(page, "Sync Now", "Push pending transactions to Sheet", true, 0, this::syncNow);
        settingRow(page, "Restore Data from Sheet", "Import Sheet rows into local DB", true, 0, this::restoreFromSheet);

        // Sync Status
        section(page, "Status");
        String lastSync = store.lastSyncAt() == 0 ? "Never" : new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(store.lastSyncAt()));
        settingRow(page, "Last Sync", lastSync, false, 0, null);
        settingRow(page, "Device Name", store.deviceName(), true, 0, this::editDevice);

        setContentView(showScreen(page));
    }

    private void showSpendingSyncSetup() {
        new AlertDialog.Builder(this).setTitle("Set up your Android Sheet")
                .setMessage("Tracking works offline. To connect your own Sheet:\n\n1. Tap Open Sheet template and make a copy in your Google Drive.\n2. Open your copy in a desktop browser, then Extensions → Apps Script. Make sure the Need/Want script is saved.\n3. Choose Deploy → New deployment → Web app. Execute as Me and allow Anyone to access it; authorize and deploy. Keep the deployment URL private.\n4. Copy the Web App URL ending in /exec and paste it in this app’s Web App URL setting.\n5. Run Test Connection, then Sync Now.\n\nSync Now sends entries waiting to sync.")
                .setNegativeButton("Close", null)
                .setPositiveButton("Open Sheet template", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/spreadsheets/d/1vu140rEacxVWVeWVil1BvjcWNFS2xyJII1b-rjoisrw/copy")));
                    } catch (android.content.ActivityNotFoundException error) {
                        Components.customToast(this, "No browser available to open the Sheet template", true);
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
        settingRow(page, "Default Payment Mode", displayDefault(store.defaultPaymentMode()), true, 0, this::chooseDefaultPayment);

        setContentView(showScreen(page));
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
        settingRow(page, "Manage Payment Methods", payCount == 0 ? "Not set up yet" : payCount + " configured", true, 0, () -> manage("Payment Modes", false));

        setContentView(showScreen(page));
    }

    private void showDataSupportSettings() {
        LinearLayout page = page();
        settingsHeader(page, "Data & support", "Your records, under your control.", false);

        // Data Management
        section(page, "Data management");
        settingRow(page, "Export Backup", "Save transactions and preferences as JSON", true, 0, this::exportBackup);
        settingRow(page, "Import Backup", "Merge transactions and restore saved preferences", true, 0, this::importBackup);
        settingRow(page, "Export as Text", "Copy transactions only to clipboard", true, 0, this::exportData);

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

        setContentView(showScreen(page));
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

    private void syncNow() {
        if (store.url().isEmpty()) {
            Components.customToast(this, "Configure the Apps Script URL first.", true);
            return;
        }
        Components.customToast(this, "Syncing with Google Sheet…", false);
        executor.execute(() -> {
            TransactionDao dao = AppDatabase.get(this).transactionDao();
            if (dao.getPendingSync().isEmpty()) {
                runOnUiThread(() -> Components.customToast(this, "All transactions already up to date.", false));
                return;
            }

            // Shared engine processes ALL pending (new + previously failed) transactions.
            SyncEngine.SyncResult result = SyncEngine.syncAllPending(this, store);

            long now = System.currentTimeMillis();
            if (result.synced > 0) store.recordSync(now);
            store.recordOperation("Sync", result.failed == 0,
                    result.synced + " synced" + (result.failed > 0 ? ", " + result.failed + " failed: " + result.lastError : ""));

            final int finalSuccess = result.synced;
            final int finalFail = result.failed;
            final String finalErr = result.lastError;
            runOnUiThread(() -> {
                if (finalFail == 0) {
                    Components.customToast(this, "Sync complete (" + finalSuccess + " items).", false);
                } else {
                    Components.customToast(this, "Synced " + finalSuccess + ", " + finalFail + " failed: " + finalErr, true);
                }
                if (currentScreen == Screen.SETTINGS) showSettings();
                else if (currentScreen == Screen.LEDGER) showLedger();
            });
        });
    }

    private void restoreFromSheet() {
        if (store.url().isEmpty()) {
            Components.customToast(this, "Configure the Apps Script URL first.", true);
            return;
        }
        if (isRestoring) {
            Components.customToast(this, "Restore already in progress.", false);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Restore from Google Sheet")
                .setMessage("This will merge transactions from your Google Sheet into the local database.\n\n• Local data will NOT be wiped\n• Newer local changes will NOT be overwritten\n• Remotely deleted items will be marked deleted\n\nProceed?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (dialog, which) -> {
                    isRestoring = true;
                    Components.customToast(this, "Connecting to Google Sheet…", false);

                    executor.execute(() -> {
                        SyncClient.RestoreResult result = SyncClient.restore(store.url());

                        if (!result.success) {
                            runOnUiThread(() -> {
                                isRestoring = false;
                                store.recordOperation("Restore", false, result.message);
                                new AlertDialog.Builder(this)
                                        .setTitle("Restore Failed")
                                        .setMessage(result.message)
                                        .setPositiveButton("OK", null)
                                        .show();
                                if (currentScreen == Screen.SETTINGS) showSettings();
                            });
                            return;
                        }

                        TransactionDao dao = AppDatabase.get(this).transactionDao();
                        int added = 0;
                        int updated = 0;
                        int deleted = 0;

                        for (TransactionEntity rem : result.transactions) {
                            TransactionEntity local = dao.findById(rem.transactionId);

                            if (local == null) {
                                if (!rem.deleted) {
                                    // Insert new transaction from Sheet
                                    rem.syncStatus = TransactionEntity.SYNC_SYNCED;
                                    dao.insert(rem);
                                    added++;
                                }
                            } else {
                                // Transaction already exists locally — reconcile based on timestamps and state
                                if (local.deleted && rem.deleted) {
                                    // Both agree it is deleted; ensure marked synced
                                    dao.markSynced(local.transactionId);
                                } else if (local.deleted && !rem.deleted) {
                                    // Local was soft-deleted
                                    if (local.updatedAt <= rem.updatedAt) {
                                        // Remote was updated or recreated after local deletion
                                        rem.syncStatus = TransactionEntity.SYNC_SYNCED;
                                        dao.update(rem);
                                        updated++;
                                    }
                                } else if (!local.deleted && rem.deleted) {
                                    // Remote row is marked deleted
                                    if (!TransactionEntity.SYNC_PENDING.equals(local.syncStatus) || local.updatedAt <= rem.updatedAt) {
                                        dao.softDelete(local.transactionId, rem.updatedAt > 0 ? rem.updatedAt : System.currentTimeMillis());
                                        dao.markSynced(local.transactionId);
                                        deleted++;
                                    }
                                } else {
                                    // Both are active
                                    if (!TransactionEntity.SYNC_PENDING.equals(local.syncStatus) || rem.updatedAt >= local.updatedAt) {
                                        rem.syncStatus = TransactionEntity.SYNC_SYNCED;
                                        dao.update(rem);
                                        updated++;
                                    }
                                }
                            }
                        }

                        long now = System.currentTimeMillis();
                        store.recordSync(now);
                        String detail = "Added: " + added + ", Updated: " + updated + ", Deleted: " + deleted;
                        store.recordOperation("Restore", true, detail);

                        final int fAdded = added;
                        final int fUpdated = updated;
                        final int fDeleted = deleted;
                        final int fTotal = result.transactions.size();

                        runOnUiThread(() -> {
                            isRestoring = false;
                            String msg = "Restore complete!\n\n"
                                    + "Total rows in Sheet: " + fTotal + "\n"
                                    + "• Added: " + fAdded + "\n"
                                    + "• Updated: " + fUpdated + "\n"
                                    + "• Deleted: " + fDeleted;

                            new AlertDialog.Builder(this)
                                    .setTitle("Restore Complete")
                                    .setMessage(msg)
                                    .setPositiveButton("OK", null)
                                    .show();

                            // Immediately refresh the current view
                            if (currentScreen == Screen.HOME) showHome();
                            else if (currentScreen == Screen.LEDGER) showLedger();
                            else if (currentScreen == Screen.SETTINGS) showSettings();
                        });
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
                .setMessage("Transactions will be merged without deleting newer local changes.\n\nIf the backup includes settings, its theme, currency, device name, categories and payment methods (including their names, order and defaults) will replace your current settings. Older transaction-only backups leave your settings unchanged.\n\nYour Google Sheets connection will not change. Choose a backup file?")
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
                            + "\nPreferences, categories and payment methods included."
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

                BackupManager.MergeResult mr = BackupManager.mergeBackup(AppDatabase.get(this).transactionDao(), val.transactions);
                boolean settingsRestored = val.settings == null || store.restoreBackupSettings(val.settings);
                store.recordOperation("Import Backup", settingsRestored, "Added: " + mr.added
                        + ", Updated: " + mr.updated + ", Deleted: " + mr.deleted
                        + ", Settings: " + (val.settings == null ? "not in backup" : settingsRestored ? "restored" : "failed"));

                runOnUiThread(() -> {
                    String msg = (settingsRestored ? "Backup imported.\n\n" : "Transactions imported, but settings could not be saved.\n\n")
                            + "Total in backup: " + mr.total + "\n"
                            + "• Added: " + mr.added + "\n"
                            + "• Updated: " + mr.updated + "\n"
                            + "• Deleted (tombstoned): " + mr.deleted + "\n"
                            + "• Preserved (newer local): " + mr.preserved + "\n\n"
                            + (val.settings == null ? "Older backup: your current settings were kept."
                                : settingsRestored ? "Preferences, categories and payment methods restored in their saved order. Google Sheets connection was kept."
                                : "Please retry the import to restore your preferences.");

                    new AlertDialog.Builder(this)
                            .setTitle(settingsRestored ? "Import Complete" : "Import Partly Complete")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();

                    if (currentScreen == Screen.HOME) showHome();
                    else if (currentScreen == Screen.LEDGER) showLedger();
                    else if (currentScreen == Screen.SETTINGS) showSettings();
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
            for (TransactionEntity tx : txs) {
                sb.append(tx.date).append(" ").append(tx.time).append(" | ")
                  .append(cur).append(String.format(Locale.US, "%.2f", tx.amount)).append(" | ")
                  .append(tx.category).append(" | ").append(tx.paymentMode)
                  .append(" | ").append(SpendingType.label(tx.spendingType));
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
    private void chooseDefaultPayment()  { chooseDefault("Default Payment Mode", store.paymentModes(), store.defaultPaymentMode(), v -> { store.setDefaultPaymentMode(v); showSettings(); }); }
    private void chooseDefault(String title, List<String> values, String current, ValueHandler handler) {
        List<String> options = new ArrayList<>(); options.add("No default"); options.addAll(values);
        int selected = current.isEmpty() ? 0 : Math.max(0, options.indexOf(current));
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options.toArray(new String[0]), selected,
                (d, w) -> { handler.accept(w == 0 ? "" : options.get(w)); d.dismiss(); }).show();
    }

    private void manage(String title, boolean category) {
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
