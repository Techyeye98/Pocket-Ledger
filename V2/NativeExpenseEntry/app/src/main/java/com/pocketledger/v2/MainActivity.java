package com.pocketledger.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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

    public static final String ACTION_ADD_EXPENSE  = "com.pocketledger.v2.ADD_EXPENSE";
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

    // Date range selection (default: This Month)
    private String dateFrom = "", dateTo = "";
    private long rangeFromMs = 0, rangeToMs = Long.MAX_VALUE;
    private String selectedRangeLabel = "This Month";

    // Ledger filters
    private String ledgerSearchQuery = "";
    private int ledgerSortIndex = 0; // 0: Newest, 1: Oldest, 2: Amount High-Low, 3: Amount Low-High
    private String ledgerCategoryFilter = "All";
    private String ledgerPaymentFilter = "All";

    private boolean isRestoring = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        instance = this;
        store = new ConfigStore(this);
        pad   = Design.dp(this, 20);
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
        initDefaultDateRange();

        LinearLayout page = page();

        // Header with logo, title, and settings
        buildHomeHeader(page);

        // Date range selector
        buildDateRangeSelector(page);

        // Summary KPI cards (Total Expenses + Avg per Expense)
        LinearLayout kpiRow = new LinearLayout(this);
        kpiRow.setOrientation(LinearLayout.HORIZONTAL);
        kpiRow.setWeightSum(2f);
        LinearLayout.LayoutParams kpiParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kpiParams.setMargins(0, dp(16), 0, 0);

        LinearLayout totalCard = kpiCard("Total Expenses", "Loading…", Design.primary(this));
        LinearLayout avgCard   = kpiCard("Avg per Expense", "Loading…", Design.secondary(this));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.setMargins(0, 0, dp(8), 0);
        kpiRow.addView(totalCard, half);
        LinearLayout.LayoutParams halfR = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        kpiRow.addView(avgCard, halfR);
        page.addView(kpiRow, kpiParams);

        // Spending Distribution (Donut Chart)
        section(page, "SPENDING DISTRIBUTION");
        LinearLayout donutCard = Components.card(this);
        donutCard.setOrientation(LinearLayout.VERTICAL);
        donutCard.setGravity(Gravity.CENTER);
        DonutChartView donutChart = new DonutChartView(this);
        donutCard.addView(donutChart);

        // Legend container for category breakdown
        LinearLayout legendContainer = new LinearLayout(this);
        legendContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        legendParams.setMargins(0, dp(16), 0, 0);
        donutCard.addView(legendContainer, legendParams);

        LinearLayout.LayoutParams donutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        donutParams.setMargins(0, dp(8), 0, 0);
        page.addView(donutCard, donutParams);

        // Daily Trend
        section(page, "DAILY TREND");
        LinearLayout trendCard = Components.card(this);
        DailyTrendView trendChart = new DailyTrendView(this);
        trendCard.addView(trendChart);
        LinearLayout.LayoutParams trendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trendParams.setMargins(0, dp(8), 0, 0);
        page.addView(trendCard, trendParams);

        // Spending by Category (top categories list)
        section(page, "SPENDING BY CATEGORY");
        LinearLayout categoryCard = Components.card(this);
        TextView catPlaceholder = Components.text(this, "Loading…", 14, Design.muted(this), Typeface.NORMAL);
        categoryCard.addView(catPlaceholder);
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catParams.setMargins(0, dp(8), 0, 0);
        page.addView(categoryCard, catParams);

        // Recent Transactions
        section(page, "RECENT TRANSACTIONS");
        LinearLayout recentCard = Components.card(this);
        TextView recentPlaceholder = Components.text(this, "Loading…", 14, Design.muted(this), Typeface.NORMAL);
        recentCard.addView(recentPlaceholder);
        LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recentParams.setMargins(0, dp(8), 0, 0);
        page.addView(recentCard, recentParams);

        // Add Expense button (prominent)
        Button add = Components.primaryButton(this, "+ Add Expense");
        add.setContentDescription("Add a new expense");
        add.setOnClickListener(v -> startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE)));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.setMargins(0, dp(20), 0, 0);
        page.addView(add, addParams);

        setContentView(showScreen(page));

        // Load DB data async
        TextView totalValue = (TextView) ((LinearLayout)((LinearLayout) totalCard.getChildAt(1))).getChildAt(0);
        TextView avgValue   = (TextView) ((LinearLayout)((LinearLayout) avgCard.getChildAt(1))).getChildAt(0);
        loadDashboardData(totalCard, avgCard, donutChart, legendContainer, trendChart, categoryCard, recentCard, totalValue, avgValue, catPlaceholder, recentPlaceholder);
    }

    private void loadDashboardData(
            LinearLayout totalCard, LinearLayout avgCard,
            DonutChartView donutChart, LinearLayout legendContainer,
            DailyTrendView trendChart, LinearLayout categoryCard,
            LinearLayout recentCard, TextView totalValue, TextView avgValue,
            TextView catPlaceholder, TextView recentPlaceholder) {

        executor.execute(() -> {
            String currency = store.currencySymbol();
            double total = AppDatabase.get(this).transactionDao().sumByTimeRange(rangeFromMs, rangeToMs);
            int    count = AppDatabase.get(this).transactionDao().countByTimeRange(rangeFromMs, rangeToMs);
            double avg   = count > 0 ? total / count : 0;
            List<TransactionEntity> txs = AppDatabase.get(this).transactionDao().getByTimeRange(rangeFromMs, rangeToMs);

            // Category breakdown
            Map<String, Double> catTotals = new LinkedHashMap<>();
            for (TransactionEntity tx : txs) {
                catTotals.merge(tx.category, tx.amount, Double::sum);
            }

            // Sort categories by amount (descending)
            List<Map.Entry<String, Double>> sortedCats = new ArrayList<>(catTotals.entrySet());
            Collections.sort(sortedCats, (a, b) -> Double.compare(b.getValue(), a.getValue()));

            // Prepare donut chart data
            List<DonutChartView.CategoryData> donutData = new ArrayList<>();
            for (Map.Entry<String, Double> e : sortedCats) {
                donutData.add(new DonutChartView.CategoryData(e.getKey(), e.getValue()));
            }

            // Prepare daily trend data
            Map<String, Double> dailyTotals = new LinkedHashMap<>();
            SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM", Locale.US);
            for (TransactionEntity tx : txs) {
                try {
                    SimpleDateFormat txFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
                    Date date = txFormat.parse(tx.date);
                    String dayLabel = dayFormat.format(date);
                    dailyTotals.merge(dayLabel, tx.amount, Double::sum);
                } catch (Exception ignored) {}
            }

            List<DailyTrendView.DayData> trendData = new ArrayList<>();
            for (Map.Entry<String, Double> e : dailyTotals.entrySet()) {
                trendData.add(new DailyTrendView.DayData(e.getKey(), e.getValue()));
            }

            // Get recent transactions (latest 5)
            List<TransactionEntity> allTxs = AppDatabase.get(this).transactionDao().getAll();
            Collections.sort(allTxs, (a, b) -> Long.compare(b.createdAt, a.createdAt));
            List<TransactionEntity> recentTxs = allTxs.subList(0, Math.min(5, allTxs.size()));

            runOnUiThread(() -> {
                String totalStr = currency + String.format(Locale.US, "%.2f", total);
                String avgStr   = count > 0 ? currency + String.format(Locale.US, "%.2f", avg) : "—";
                totalValue.setText(totalStr);
                avgValue.setText(avgStr);

                // Update donut chart
                donutChart.setData(donutData, total, currency);

                // Update legend
                legendContainer.removeAllViews();
                if (sortedCats.isEmpty()) {
                    TextView emptyText = Components.text(this, "No expenses in this period.", 14, Design.muted(this), Typeface.NORMAL);
                    emptyText.setGravity(Gravity.CENTER);
                    legendContainer.addView(emptyText);
                } else {
                    for (Map.Entry<String, Double> e : sortedCats) {
                        double pct = total > 0 ? e.getValue() / total * 100 : 0;
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rp.setMargins(0, 0, 0, dp(8));

                        TextView name = Components.text(this, e.getKey(), 14, Design.text(this), Typeface.NORMAL);
                        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        TextView val = Components.text(this,
                                currency + String.format(Locale.US, "%.2f", e.getValue())
                                + " · " + String.format(Locale.US, "%.0f%%", pct),
                                13, Design.muted(this), Typeface.NORMAL);
                        row.addView(val);
                        legendContainer.addView(row, rp);
                    }
                }

                // Update daily trend chart
                trendChart.setData(trendData, currency);

                // Update spending by category (top 5)
                categoryCard.removeAllViews();
                if (sortedCats.isEmpty()) {
                    categoryCard.addView(Components.text(this, "No expenses in this period.", 14, Design.muted(this), Typeface.NORMAL));
                } else {
                    int limit = Math.min(5, sortedCats.size());
                    for (int i = 0; i < limit; i++) {
                        Map.Entry<String, Double> e = sortedCats.get(i);
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(0, dp(8), 0, dp(8));

                        TextView emoji = Components.text(this, store.emoji(e.getKey()), 24, Design.text(this), Typeface.NORMAL);
                        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
                        row.addView(emoji, ep);

                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        TextView name = Components.text(this, store.name(e.getKey()), 15, Design.text(this), Typeface.BOLD);
                        textCol.addView(name);
                        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        TextView amt = Components.text(this, currency + String.format(Locale.US, "%.2f", e.getValue()), 15, Design.primary(this), Typeface.BOLD);
                        row.addView(amt);

                        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        categoryCard.addView(row, rp);

                        // Divider
                        if (i < limit - 1) {
                            View divider = new View(this);
                            divider.setBackgroundColor(Design.border(this));
                            categoryCard.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                        }
                    }

                    // View All button if there are more categories
                    if (sortedCats.size() > 5) {
                        Button viewAll = Components.textButton(this, "View All →");
                        viewAll.setOnClickListener(v -> showLedger());
                        LinearLayout.LayoutParams vap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        vap.setMargins(0, dp(8), 0, 0);
                        categoryCard.addView(viewAll, vap);
                    }
                }

                // Update recent transactions
                recentCard.removeAllViews();
                if (recentTxs.isEmpty()) {
                    recentCard.addView(Components.text(this, "No transactions yet. Tap + Add Expense to get started.", 14, Design.muted(this), Typeface.NORMAL));
                } else {
                    for (int i = 0; i < recentTxs.size(); i++) {
                        TransactionEntity tx = recentTxs.get(i);
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(0, dp(10), 0, dp(10));

                        // Category emoji
                        TextView emoji = Components.text(this, store.emoji(tx.category), 24, Design.text(this), Typeface.NORMAL);
                        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
                        row.addView(emoji, ep);

                        // Category and details
                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);
                        TextView cat = Components.text(this, store.name(tx.category), 15, Design.text(this), Typeface.BOLD);
                        textCol.addView(cat);

                        String subText = tx.date;
                        if (!tx.remarks.isEmpty()) subText = tx.remarks + " · " + subText;
                        TextView sub = Components.text(this, subText, 13, Design.muted(this), Typeface.NORMAL);
                        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        sp.setMargins(0, dp(2), 0, 0);
                        textCol.addView(sub, sp);

                        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        // Amount
                        TextView amt = Components.text(this, currency + String.format(Locale.US, "%.2f", tx.amount), 15, Design.primary(this), Typeface.BOLD);
                        row.addView(amt);

                        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        recentCard.addView(row, rp);

                        // Divider
                        if (i < recentTxs.size() - 1) {
                            View divider = new View(this);
                            divider.setBackgroundColor(Design.border(this));
                            recentCard.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
                        }
                    }

                    // View All button
                    Button viewAll = Components.textButton(this, "View All →");
                    viewAll.setOnClickListener(v -> showLedger());
                    LinearLayout.LayoutParams vap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    vap.setMargins(0, dp(8), 0, 0);
                    recentCard.addView(viewAll, vap);
                }
            });
        });
    }

    private LinearLayout kpiCard(String label, String value, int accentColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Design.card(this));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView labelView = Components.subtitle(this, label);
        card.addView(labelView);

        LinearLayout valRow = new LinearLayout(this);
        valRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMargins(0, dp(8), 0, 0);
        TextView valueView = Components.text(this, value, 22, accentColor, Typeface.BOLD);
        valRow.addView(valueView);
        card.addView(valRow, vp);
        return card;
    }

    private void buildHomeHeader(LinearLayout page) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.app_logo);
        logo.setContentDescription("Pocket Ledger");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
        lp.setMarginEnd(dp(10));
        row.addView(logo, lp);

        TextView title = Components.text(this, "Pocket Ledger", 24, Design.text(this), Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button settingsBtn = Components.textButton(this, "⚙");
        settingsBtn.setTextSize(22);
        settingsBtn.setContentDescription("Open settings");
        settingsBtn.setOnClickListener(v -> showSettings());
        row.addView(settingsBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        page.addView(row, match());
    }

    private void buildDateRangeSelector(LinearLayout page) {
        String[] labels = {"Today", "This Week", "This Month", "Last Month"};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(16), 0, 0);
        int btnH = dp(36);

        for (String label : labels) {
            Button btn = new Button(this);
            btn.setText(label);
            btn.setTextSize(13);
            btn.setAllCaps(false);
            boolean selected = label.equals(selectedRangeLabel);
            btn.setBackground(Design.shape(this,
                    selected ? Design.primary(this) : Design.surfaceAlt(this),
                    20, selected ? 0 : Design.border(this)));
            btn.setTextColor(selected ? Design.primaryOn(this) : Design.text(this));
            btn.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            btn.setMinHeight(btnH);
            btn.setPadding(dp(12), 0, dp(12), 0);
            btn.setOnClickListener(v -> { selectedRangeLabel = label; applyDateRange(label); showHome(); });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, btnH);
            bp.setMargins(0, 0, dp(8), 0);
            row.addView(btn, bp);
        }

        // Custom Range button
        Button custom = new Button(this);
        custom.setText("Custom");
        custom.setTextSize(13);
        custom.setAllCaps(false);
        boolean customSelected = selectedRangeLabel.startsWith("Custom");
        custom.setBackground(Design.shape(this,
                customSelected ? Design.primary(this) : Design.surfaceAlt(this),
                20, customSelected ? 0 : Design.border(this)));
        custom.setTextColor(customSelected ? Design.primaryOn(this) : Design.text(this));
        custom.setMinHeight(dp(36));
        custom.setPadding(dp(12), 0, dp(12), 0);
        custom.setOnClickListener(v -> showCustomDateDialog());
        row.addView(custom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        // Wrap in horizontal scrollable
        android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        hScroll.addView(row);
        page.addView(hScroll, rp);
    }

    private void showCustomDateDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        EditText fromField = new EditText(this);
        fromField.setHint("From (dd-MM-yyyy)");
        fromField.setText(dateFrom);
        fromField.setInputType(InputType.TYPE_CLASS_DATETIME);
        layout.addView(fromField);

        EditText toField = new EditText(this);
        toField.setHint("To (dd-MM-yyyy)");
        toField.setText(dateTo);
        toField.setInputType(InputType.TYPE_CLASS_DATETIME);
        LinearLayout.LayoutParams toP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toP.setMargins(0, dp(12), 0, 0);
        layout.addView(toField, toP);

        new AlertDialog.Builder(this).setTitle("Custom Date Range")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    String f = fromField.getText().toString().trim();
                    String t = toField.getText().toString().trim();
                    if (!f.isEmpty() && !t.isEmpty()) {
                        dateFrom = f; dateTo = t;
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
                            Date fDate = sdf.parse(f);
                            Date tDate = sdf.parse(t);
                            Calendar start = Calendar.getInstance();
                            start.setTime(fDate);
                            start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
                            rangeFromMs = start.getTimeInMillis();
                            Calendar end = Calendar.getInstance();
                            end.setTime(tDate);
                            end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59); end.set(Calendar.SECOND, 59); end.set(Calendar.MILLISECOND, 999);
                            rangeToMs = end.getTimeInMillis();
                        } catch (Exception ignored) {
                            rangeFromMs = 0;
                            rangeToMs = Long.MAX_VALUE;
                        }
                        selectedRangeLabel = "Custom";
                        showHome();
                    }
                }).show();
    }

    private void applyDateRange(String label) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        switch (label) {
            case "Today":
                dateFrom = dateTo = sdf.format(today);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                rangeFromMs = cal.getTimeInMillis();
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                rangeToMs = cal.getTimeInMillis();
                break;
            case "This Week":
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                dateFrom = sdf.format(cal.getTime());
                rangeFromMs = cal.getTimeInMillis();
                dateTo = sdf.format(today);
                Calendar endCalWeek = Calendar.getInstance();
                endCalWeek.set(Calendar.HOUR_OF_DAY, 23); endCalWeek.set(Calendar.MINUTE, 59); endCalWeek.set(Calendar.SECOND, 59); endCalWeek.set(Calendar.MILLISECOND, 999);
                rangeToMs = endCalWeek.getTimeInMillis();
                break;
            case "This Month":
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                dateFrom = sdf.format(cal.getTime());
                rangeFromMs = cal.getTimeInMillis();
                dateTo = sdf.format(today);
                Calendar endCalMonth = Calendar.getInstance();
                endCalMonth.set(Calendar.HOUR_OF_DAY, 23); endCalMonth.set(Calendar.MINUTE, 59); endCalMonth.set(Calendar.SECOND, 59); endCalMonth.set(Calendar.MILLISECOND, 999);
                rangeToMs = endCalMonth.getTimeInMillis();
                break;
            case "Last Month":
                cal.add(Calendar.MONTH, -1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                dateFrom = sdf.format(cal.getTime());
                rangeFromMs = cal.getTimeInMillis();
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                dateTo = sdf.format(cal.getTime());
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                rangeToMs = cal.getTimeInMillis();
                break;
        }
    }

    private void initDefaultDateRange() {
        if (!dateFrom.isEmpty()) return; // already set
        applyDateRange(selectedRangeLabel);
    }

    /** Builds the fixed bottom navigation bar (returns it, does not attach to content). */
    private LinearLayout buildBottomNavigation() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Design.surface(this));

        // Hairline divider — anchors the bar to the screen without a surrounding box.
        View divider = new View(this);
        divider.setBackgroundColor(Design.border(this));
        bar.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);

        navTab(nav, NavIcon.HOME, "Home", currentScreen == Screen.HOME, v -> showHome());
        navTab(nav, NavIcon.LEDGER, "Ledger", currentScreen == Screen.LEDGER, v -> showLedger());
        navTab(nav, NavIcon.SETTINGS, "Settings", currentScreen == Screen.SETTINGS, v -> showSettings());

        // Keep the bar clear of the gesture/navigation area (edge-to-edge)
        bar.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });

        bar.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    /**
     * Wraps the scrollable page and the fixed bottom navigation into a single screen:
     * the scroll area occupies the space above the navigation, which stays pinned
     * to the bottom of the window on every screen.
     */
    private View showScreen(LinearLayout page) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Design.background(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Design.background(this));

        // Content bottom inset so the last row never hides behind the fixed bar.
        scroll.setPadding(0, 0, 0, dp(76));
        scroll.setClipToPadding(false);

        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout navBar = buildBottomNavigation();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(navBar, navParams);
        return root;
    }

    private LinearLayout navTab(LinearLayout bar, int iconType, String label, boolean active, View.OnClickListener onClick) {
        int accent    = Design.primary(this);
        int idleColor = Design.muted(this);
        int color     = active ? accent : idleColor;

        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setMinimumHeight(dp(56));
        tab.setPadding(dp(8), dp(6), dp(8), dp(8));
        tab.setBackgroundColor(Color.TRANSPARENT);
        tab.setClickable(true);
        tab.setOnClickListener(onClick);
        Components.addPressAnimation(tab);

        NavIcon icon = new NavIcon(this, iconType, color);

        if (active) {
            // Soft accent pill behind the active icon.
            LinearLayout pill = new LinearLayout(this);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(Design.shape(this, Design.tint(accent, 34), 16, 0));
            LinearLayout.LayoutParams pillP = new LinearLayout.LayoutParams(dp(38), dp(38));
            pill.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            tab.addView(pill, pillP);
        } else {
            tab.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }

        TextView labelView = Components.text(this, label, 12, color, active ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, 0);
        tab.addView(labelView, lp);

        bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return tab;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEDGER
    // ─────────────────────────────────────────────────────────────────────────

    private void showLedger() {
        currentScreen = Screen.LEDGER;
        applyWindowTheme();

        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setPadding(0, 0, dp(8), 0);
        back.setContentDescription("Back to dashboard");
        back.setOnClickListener(v -> showHome());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Ledger", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button addBtn = Components.textButton(this, "+");
        addBtn.setTextSize(22);
        addBtn.setContentDescription("Add expense");
        addBtn.setOnClickListener(v -> startActivity(new Intent(this, QuickEntryActivity.class).setAction(ACTION_ADD_EXPENSE)));
        header.addView(addBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(header, match());

        // Search & Filter controls
        EditText searchInput = new EditText(this);
        searchInput.setHint("Search remarks, category, mode…");
        searchInput.setHintTextColor(Design.muted(this));
        searchInput.setTextColor(Design.text(this));
        searchInput.setTextSize(14);
        searchInput.setSingleLine(true);
        searchInput.setText(ledgerSearchQuery);
        searchInput.setBackground(Design.shape(this, Design.surfaceAlt(this), 12, Design.border(this)));
        searchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams sip = match();
        sip.setMargins(0, dp(12), 0, 0);
        page.addView(searchInput, sip);

        // Sort & Category filter row
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams frp = match();
        frp.setMargins(0, dp(8), 0, 0);

        String[] sortLabels = {"Newest", "Oldest", "Amount: High → Low", "Amount: Low → High"};
        Button sortBtn = Components.textButton(this, "⇅ " + sortLabels[ledgerSortIndex]);
        sortBtn.setTextSize(13);
        sortBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Sort By")
                    .setSingleChoiceItems(sortLabels, ledgerSortIndex, (d, which) -> {
                        ledgerSortIndex = which;
                        d.dismiss();
                        showLedger();
                    }).show();
        });
        filterRow.addView(sortBtn);

        Button catFilterBtn = Components.textButton(this, "🏷 " + ledgerCategoryFilter);
        catFilterBtn.setTextSize(13);
        catFilterBtn.setOnClickListener(v -> {
            List<String> catOptions = new ArrayList<>();
            catOptions.add("All");
            catOptions.addAll(store.categories());
            int sel = Math.max(0, catOptions.indexOf(ledgerCategoryFilter));
            new AlertDialog.Builder(this)
                    .setTitle("Filter Category")
                    .setSingleChoiceItems(catOptions.toArray(new String[0]), sel, (d, which) -> {
                        ledgerCategoryFilter = catOptions.get(which);
                        d.dismiss();
                        showLedger();
                    }).show();
        });
        LinearLayout.LayoutParams cfp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cfp.setMargins(dp(8), 0, 0, 0);
        filterRow.addView(catFilterBtn, cfp);

        Button payFilterBtn = Components.textButton(this, "💳 " + ledgerPaymentFilter);
        payFilterBtn.setTextSize(13);
        payFilterBtn.setOnClickListener(v -> {
            List<String> payOptions = new ArrayList<>();
            payOptions.add("All");
            payOptions.addAll(store.paymentModes());
            int sel = Math.max(0, payOptions.indexOf(ledgerPaymentFilter));
            new AlertDialog.Builder(this)
                    .setTitle("Filter Payment Mode")
                    .setSingleChoiceItems(payOptions.toArray(new String[0]), sel, (d, which) -> {
                        ledgerPaymentFilter = payOptions.get(which);
                        d.dismiss();
                        showLedger();
                    }).show();
        });
        LinearLayout.LayoutParams pfp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pfp.setMargins(dp(8), 0, 0, 0);
        filterRow.addView(payFilterBtn, pfp);

        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(filterRow);
        page.addView(filterScroll, frp);

        // Transaction list
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lcp.setMargins(0, dp(12), 0, 0);

        TextView loadingText = Components.text(this, "Loading transactions…", 14, Design.muted(this), Typeface.NORMAL);
        listContainer.addView(loadingText);
        page.addView(listContainer, lcp);

        setContentView(showScreen(page));

        // Load from DB
        executor.execute(() -> {
            List<TransactionEntity> txs = AppDatabase.get(this).transactionDao().getAll();
            runOnUiThread(() -> {
                renderLedgerItems(txs, listContainer);
                searchInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        ledgerSearchQuery = s.toString().trim();
                        renderLedgerItems(txs, listContainer);
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            });
        });
    }

    private void renderLedgerItems(List<TransactionEntity> allTxs, LinearLayout listContainer) {
        listContainer.removeAllViews();
        String currency = store.currencySymbol();
        String q = ledgerSearchQuery.toLowerCase(Locale.getDefault());

        List<TransactionEntity> filtered = new ArrayList<>();
        for (TransactionEntity tx : allTxs) {
            if (!"All".equals(ledgerCategoryFilter) && !tx.category.contains(store.name(ledgerCategoryFilter))) {
                continue;
            }
            if (!"All".equals(ledgerPaymentFilter) && !tx.paymentMode.contains(store.name(ledgerPaymentFilter))) {
                continue;
            }
            if (!q.isEmpty()) {
                String full = (tx.category + " " + tx.paymentMode + " " + tx.remarks + " " + tx.date + " " + tx.amount).toLowerCase(Locale.getDefault());
                if (!full.contains(q)) continue;
            }
            filtered.add(tx);
        }

        // Sort
        switch (ledgerSortIndex) {
            case 0: // Newest
                Collections.sort(filtered, (a, b) -> Long.compare(b.createdAt, a.createdAt));
                break;
            case 1: // Oldest
                Collections.sort(filtered, (a, b) -> Long.compare(a.createdAt, b.createdAt));
                break;
            case 2: // Amount High to Low
                Collections.sort(filtered, (a, b) -> Double.compare(b.amount, a.amount));
                break;
            case 3: // Amount Low to High
                Collections.sort(filtered, (a, b) -> Double.compare(a.amount, b.amount));
                break;
        }

        if (filtered.isEmpty()) {
            listContainer.addView(Components.text(this, allTxs.isEmpty() ? "No transactions yet. Tap + to add one." : "No matching transactions.", 14, Design.muted(this), Typeface.NORMAL));
            return;
        }

        for (TransactionEntity tx : filtered) {
            LinearLayout row = buildLedgerRow(tx, currency, listContainer, allTxs);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, dp(8));
            listContainer.addView(row, rp);
        }
    }

    private LinearLayout buildLedgerRow(TransactionEntity tx, String currency, LinearLayout container, List<TransactionEntity> allTxs) {
        LinearLayout card = Components.card(this);
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView cat = Components.text(this, tx.category, 15, Design.text(this), Typeface.BOLD);
        top.addView(cat, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView amt = Components.text(this, currency + String.format(Locale.US, "%.2f", tx.amount), 16, Design.primary(this), Typeface.BOLD);
        top.addView(amt);
        card.addView(top, match());

        LinearLayout sub = new LinearLayout(this);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, dp(4), 0, 0);

        String subText = tx.paymentMode + " · " + tx.date + " " + tx.time;
        if (!tx.remarks.isEmpty()) subText += "\n" + tx.remarks;
        TextView subView = Components.text(this, subText, 13, Design.muted(this), Typeface.NORMAL);
        sub.addView(subView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Sync status badge
        if (!TransactionEntity.SYNC_SYNCED.equals(tx.syncStatus)) {
            TextView badge = Components.badge(this, "Not synced", Design.warning(this));
            sub.addView(badge);
        }
        card.addView(sub, sp);

        // Long press → Edit / Delete
        card.setOnLongClickListener(v -> {
            showTransactionActions(tx, container);
            return true;
        });
        Components.addPressAnimation(card);
        return card;
    }

    private void showTransactionActions(TransactionEntity tx, LinearLayout container) {
        String currency = store.currencySymbol();
        String summary = currency + String.format(Locale.US, "%.2f", tx.amount) + " · " + tx.category;
        new AlertDialog.Builder(this)
                .setTitle(summary)
                .setItems(new String[]{"Edit", "Delete"}, (d, which) -> {
                    if (which == 0) showEditTransaction(tx, container);
                    else showDeleteConfirmation(tx, container);
                }).show();
    }

    private void showEditTransaction(TransactionEntity tx, LinearLayout container) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        EditText amtField = new EditText(this);
        amtField.setHint("Amount");
        amtField.setText(String.format(Locale.US, "%.2f", tx.amount));
        amtField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amtField);

        EditText remField = new EditText(this);
        remField.setHint("Note (optional)");
        remField.setText(tx.remarks);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(12), 0, 0);
        layout.addView(remField, rp);

        final String[] chosenCat = {tx.category};
        final String[] chosenPay = {tx.paymentMode};

        Button catBtn = Components.secondaryButton(this, "Category: " + (tx.category.isEmpty() ? "Choose" : tx.category));
        catBtn.setTextSize(14);
        catBtn.setOnClickListener(v -> {
            List<String> cats = store.categories();
            new AlertDialog.Builder(this)
                    .setTitle("Change Category")
                    .setSingleChoiceItems(cats.toArray(new String[0]), Math.max(0, cats.indexOf(chosenCat[0])), (d, which) -> {
                        chosenCat[0] = cats.get(which);
                        catBtn.setText("Category: " + chosenCat[0]);
                        d.dismiss();
                    }).show();
        });
        LinearLayout.LayoutParams cbp = match();
        cbp.setMargins(0, dp(10), 0, 0);
        layout.addView(catBtn, cbp);

        Button payBtn = Components.secondaryButton(this, "Mode: " + (tx.paymentMode.isEmpty() ? "Choose" : tx.paymentMode));
        payBtn.setTextSize(14);
        payBtn.setOnClickListener(v -> {
            List<String> pays = store.paymentModes();
            new AlertDialog.Builder(this)
                    .setTitle("Change Payment Mode")
                    .setSingleChoiceItems(pays.toArray(new String[0]), Math.max(0, pays.indexOf(chosenPay[0])), (d, which) -> {
                        chosenPay[0] = pays.get(which);
                        payBtn.setText("Mode: " + chosenPay[0]);
                        d.dismiss();
                    }).show();
        });
        LinearLayout.LayoutParams pbp = match();
        pbp.setMargins(0, dp(10), 0, 0);
        layout.addView(payBtn, pbp);

        new AlertDialog.Builder(this).setTitle("Edit Expense")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String normalized;
                    try { normalized = AmountUtil.normalize(amtField.getText().toString()); }
                    catch (Exception e) { Components.customToast(this, "Invalid amount.", true); return; }
                    tx.amount      = Double.parseDouble(normalized);
                    tx.category    = chosenCat[0];
                    tx.paymentMode = chosenPay[0];
                    tx.remarks     = remField.getText().toString().trim();
                    tx.updatedAt   = System.currentTimeMillis();
                    tx.syncStatus  = TransactionEntity.SYNC_PENDING;
                    executor.execute(() -> {
                        AppDatabase.get(this).transactionDao().update(tx);
                        runOnUiThread(() -> {
                            Components.customToast(this, "Expense updated.", false);
                            showLedger();
                        });
                    });
                }).show();
    }

    private void showDeleteConfirmation(TransactionEntity tx, LinearLayout container) {
        String currency = store.currencySymbol();
        new AlertDialog.Builder(this)
                .setTitle("Delete expense?")
                .setMessage(currency + String.format(Locale.US, "%.2f", tx.amount) + " · " + tx.category + "\n\nThis marks it deleted locally. It will be removed from Google Sheets on the next sync.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    executor.execute(() -> {
                        AppDatabase.get(this).transactionDao().softDelete(tx.transactionId, System.currentTimeMillis());
                        runOnUiThread(() -> {
                            Components.customToast(this, "Expense deleted.", false);
                            showLedger();
                        });
                    });
                }).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETTINGS
    // ─────────────────────────────────────────────────────────────────────────

    private void showSettings() {
        currentScreen = Screen.SETTINGS;
        applyWindowTheme();

        // Route to appropriate Settings section
        switch (currentSettingsSection) {
            case SYNC:
                showSyncSettings();
                return;
            case PREFERENCES:
                showPreferencesSettings();
                return;
            case CATEGORIES:
                showCategoriesSettings();
                return;
            case DATA_SUPPORT:
                showDataSupportSettings();
                return;
            default:
                showMainSettings();
        }
    }

    private void showMainSettings() {
        currentSettingsSection = SettingsSection.MAIN;

        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setContentDescription("Back to dashboard");
        back.setOnClickListener(v -> showHome());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Settings", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header, match());

        // Spacer
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        page.addView(new View(this), spacerParams);

        // Four main sections as large cards

        // 1. Sync & Google Sheets
        LinearLayout syncCard = settingsSectionCard(
            "☁️",
            "Sync & Google Sheets",
            "Manage synchronization with Google Sheets",
            () -> {
                currentSettingsSection = SettingsSection.SYNC;
                showSettings();
            }
        );
        page.addView(syncCard, match());

        // 2. Preferences
        LinearLayout prefsCard = settingsSectionCard(
            "⚙️",
            "Preferences",
            "Theme, currency, and default settings",
            () -> {
                currentSettingsSection = SettingsSection.PREFERENCES;
                showSettings();
            }
        );
        LinearLayout.LayoutParams prefsParams = match();
        prefsParams.setMargins(0, dp(16), 0, 0);
        page.addView(prefsCard, prefsParams);

        // 3. Categories & Payment Methods
        LinearLayout categoriesCard = settingsSectionCard(
            "🏷️",
            "Categories & Payment Methods",
            "Manage expense categories and payment modes",
            () -> {
                currentSettingsSection = SettingsSection.CATEGORIES;
                showSettings();
            }
        );
        LinearLayout.LayoutParams catParams = match();
        catParams.setMargins(0, dp(16), 0, 0);
        page.addView(categoriesCard, catParams);

        // 4. Data & Support
        LinearLayout dataCard = settingsSectionCard(
            "💾",
            "Data & Support",
            "Backup, restore, help, and app information",
            () -> {
                currentSettingsSection = SettingsSection.DATA_SUPPORT;
                showSettings();
            }
        );
        LinearLayout.LayoutParams dataParams = match();
        dataParams.setMargins(0, dp(16), 0, 0);
        page.addView(dataCard, dataParams);

        setContentView(showScreen(page));
    }

    private LinearLayout settingsSectionCard(String icon, String title, String description, Runnable action) {
        LinearLayout card = Components.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        Components.addPressAnimation(card);
        card.setOnClickListener(v -> action.run());

        // Icon
        TextView iconView = Components.text(this, icon, 32, Design.text(this), Typeface.NORMAL);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.setMarginEnd(dp(16));
        card.addView(iconView, iconParams);

        // Text column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = Components.text(this, title, 17, Design.text(this), Typeface.BOLD);
        textCol.addView(titleView);

        TextView descView = Components.text(this, description, 14, Design.muted(this), Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(4), 0, 0);
        textCol.addView(descView, descParams);

        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Chevron
        TextView chevron = Components.text(this, "›", 28, Design.muted(this), Typeface.NORMAL);
        card.addView(chevron, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETTINGS SUBSECTIONS
    // ─────────────────────────────────────────────────────────────────────────

    private void showSyncSettings() {
        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setContentDescription("Back to Settings");
        back.setOnClickListener(v -> {
            currentSettingsSection = SettingsSection.MAIN;
            showSettings();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Sync & Google Sheets", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header, match());

        // Google Apps Script URL
        section(page, "GOOGLE APPS SCRIPT");
        settingRow(page, "Web App URL", connectionStateText(), true, connectionStateColor(), this::editUrl);
        settingRow(page, "Test Connection", "Checks HTTPS reachability", true, 0, this::testConnection);

        // Sync Operations
        section(page, "SYNCHRONIZATION");
        settingRow(page, "Sync Now", "Push pending transactions to Sheet", true, 0, this::syncNow);
        settingRow(page, "Restore Data from Sheet", "Import Sheet rows into local DB", true, 0, this::restoreFromSheet);

        // Sync Status
        section(page, "STATUS");
        String lastSync = store.lastSyncAt() == 0 ? "Never" : new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(new Date(store.lastSyncAt()));
        settingRow(page, "Last Sync", lastSync, false, 0, null);
        settingRow(page, "Device Name", store.deviceName(), true, 0, this::editDevice);

        setContentView(showScreen(page));
    }

    private void showPreferencesSettings() {
        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setContentDescription("Back to Settings");
        back.setOnClickListener(v -> {
            currentSettingsSection = SettingsSection.MAIN;
            showSettings();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Preferences", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header, match());

        // Appearance
        section(page, "APPEARANCE");
        settingRow(page, "Theme", themeName(), true, 0, this::chooseTheme);

        // Regional
        section(page, "REGIONAL");
        settingRow(page, "Currency Symbol", store.currencySymbol(), true, 0, this::editCurrency);

        // Entry Defaults
        section(page, "ENTRY DEFAULTS");
        settingRow(page, "Default Category", displayDefault(store.defaultCategory()), true, 0, this::chooseDefaultCategory);
        settingRow(page, "Default Payment Mode", displayDefault(store.defaultPaymentMode()), true, 0, this::chooseDefaultPayment);

        setContentView(showScreen(page));
    }

    private void showCategoriesSettings() {
        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setContentDescription("Back to Settings");
        back.setOnClickListener(v -> {
            currentSettingsSection = SettingsSection.MAIN;
            showSettings();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Categories & Payment Methods", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header, match());

        // Categories
        section(page, "CATEGORIES");
        int catCount = store.categories().size();
        settingRow(page, "Manage Categories", catCount == 0 ? "Not set up yet" : catCount + " configured", true, 0, () -> manage("Categories", true));

        // Payment Modes
        section(page, "PAYMENT METHODS");
        int payCount = store.paymentModes().size();
        settingRow(page, "Manage Payment Methods", payCount == 0 ? "Not set up yet" : payCount + " configured", true, 0, () -> manage("Payment Modes", false));

        setContentView(showScreen(page));
    }

    private void showDataSupportSettings() {
        LinearLayout page = page();

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = Components.textButton(this, "←");
        back.setTextSize(22);
        back.setContentDescription("Back to Settings");
        back.setOnClickListener(v -> {
            currentSettingsSection = SettingsSection.MAIN;
            showSettings();
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = Components.text(this, "Data & Support", 24, Design.text(this), Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(header, match());

        // Data Management
        section(page, "DATA MANAGEMENT");
        settingRow(page, "Export Backup", "Save all data to a JSON backup file", true, 0, this::exportBackup);
        settingRow(page, "Import Backup", "Restore and merge from a backup file", true, 0, this::importBackup);
        settingRow(page, "Export as Text", "Copy all transactions to clipboard", true, 0, this::exportData);

        // Support
        section(page, "SUPPORT");
        settingRow(page, "Diagnostics", "Copy a safe report", true, 0, this::showDiagnostics);
        settingRow(page, "Contact Support", "Telegram @TechyBist", true, 0, this::contactSupport);

        // Information
        section(page, "INFORMATION");
        settingRow(page, "Privacy Policy", "How your data is used", true, 0, this::showPrivacyPolicy);

        TextView about = Components.text(this,
                "Pocket Ledger V2 · " + appVersion()
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
        if (url.equals(store.verifiedUrl())) return "Connected";
        return "Configured · not yet tested";
    }
    private int connectionStateColor() {
        String url = store.url();
        if (url.isEmpty()) return Design.warning(this);
        if (url.equals(store.verifiedUrl())) return Design.success(this);
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
                if (r.success) store.setVerifiedUrl(testedUrl);
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
                .setMessage("Your existing data will NOT be deleted.\n\nThe backup will be merged with your local transactions, preserving newer local changes.\n\nProceed to choose a backup file?")
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
                    String msg = "Backup exported successfully!\n\nTransactions exported: " + all.size();
                    new AlertDialog.Builder(this)
                            .setTitle("Backup Exported")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                    store.recordOperation("Export Backup", true, all.size() + " transactions exported");
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
                store.recordOperation("Import Backup", true, "Added: " + mr.added + ", Updated: " + mr.updated + ", Deleted: " + mr.deleted);

                runOnUiThread(() -> {
                    String msg = "Backup imported successfully!\n\n"
                            + "Total in backup: " + mr.total + "\n"
                            + "• Added: " + mr.added + "\n"
                            + "• Updated: " + mr.updated + "\n"
                            + "• Deleted (tombstoned): " + mr.deleted + "\n"
                            + "• Preserved (newer local): " + mr.preserved;

                    new AlertDialog.Builder(this)
                            .setTitle("Import Complete")
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
            StringBuilder sb = new StringBuilder("Pocket Ledger V2 Export\n\n");
            String cur = store.currencySymbol();
            for (TransactionEntity tx : txs) {
                sb.append(tx.date).append(" ").append(tx.time).append(" | ")
                  .append(cur).append(String.format(Locale.US, "%.2f", tx.amount)).append(" | ")
                  .append(tx.category).append(" | ").append(tx.paymentMode);
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
        String report = "Pocket Ledger V2\nVersion: " + appVersion()
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
                            .setPrimaryClip(ClipData.newPlainText("Pocket Ledger V2 diagnostics", report));
                    Components.customToast(this, "Copied to clipboard", false);
                }).show();
    }

    private void contactSupport() {
        Intent telegram = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?domain=TechyBist"));
        Intent web      = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/TechyBist"));
        startActivity(telegram.resolveActivity(getPackageManager()) != null ? telegram : web);
    }

    private void showPrivacyPolicy() {
        String policy = "Pocket Ledger V2 — Privacy Policy\n\n"
                + "LOCAL DATA:\n"
                + "All expense data is stored on your device in a local database. "
                + "Data is not sent anywhere unless you explicitly configure and trigger sync.\n\n"
                + "GOOGLE SHEETS (OPTIONAL):\n"
                + "If you configure a Google Apps Script URL and press Sync, your expense data "
                + "will be sent to that Google Sheet. You control when this happens.\n\n"
                + "ANONYMOUS USAGE STATISTICS:\n"
                + "A random anonymous installation ID may be used to track basic usage metrics "
                + "(app opens, active usage). No expense amounts, categories, remarks, or any "
                + "personal financial data are ever included.\n\n"
                + "BACKGROUND ACTIVITY:\n"
                + "Pocket Ledger V2 does NOT run any background services, location tracking, "
                + "sensor monitoring, or automatic syncing.\n\n"
                + "Support: Telegram @TechyBist";

        new AlertDialog.Builder(this).setTitle("Privacy Policy")
                .setMessage(policy)
                .setPositiveButton("Close", null).show();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(pad, pad, pad, pad);
        p.setBackgroundColor(Design.background(this));
        return p;
    }

    private void section(LinearLayout page, String text) {
        TextView v = Components.subtitle(this, text);
        v.setPadding(0, dp(28), 0, dp(8));
        page.addView(v);
    }

    private void settingRow(LinearLayout page, String title, String detail, boolean actionable, int statusColor, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        if (actionable && action != null) { Components.addPressAnimation(row); row.setOnClickListener(v -> action.run()); }

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Components.text(this, title, 16, Design.text(this), Typeface.BOLD);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actionable && action != null) {
            TextView chevron = Components.text(this, "›", 20, Design.muted(this), Typeface.NORMAL);
            top.addView(chevron);
        }
        row.addView(top);

        if (statusColor != 0) {
            TextView badge = Components.badge(this, detail, statusColor);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(0, dp(6), 0, 0);
            row.addView(badge, bp);
        } else if (detail != null && !detail.isEmpty()) {
            TextView d = Components.text(this, detail, 14, Design.muted(this), Typeface.NORMAL);
            d.setPadding(0, dp(2), 0, 0);
            row.addView(d);
        }
        page.addView(row, match());
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
