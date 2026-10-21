package com.pocketledger.v2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** The same local-first entry workflow, presented as a focused expense slip. */
public class QuickEntryActivity extends Activity {
    private ConfigStore store;
    private EditText amountField;
    private EditText noteField;
    private SpendingTypePicker spendingTypePicker;
    private LinearLayout categoryContainer;
    private LinearLayout paymentContainer;
    private Button saveButton;
    private java.util.List<PaymentChoice> ledgerPayments = new java.util.ArrayList<>();
    private boolean paymentLoaded;
    private String selectedCategory = "";
    private String selectedPayment = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        NotificationHelper.prepare(this);
        boolean widgetEntry = getIntent().getBooleanExtra(AddExpenseWidgetProvider.EXTRA_FROM_WIDGET, false);
        setTheme(Design.dark(this)
                ? (widgetEntry ? R.style.Theme_PocketLedger_WidgetEntry_Dark : R.style.Theme_PocketLedger_QuickEntry_Dark)
                : (widgetEntry ? R.style.Theme_PocketLedger_WidgetEntry : R.style.Theme_PocketLedger_QuickEntry));
        getWindow().setStatusBarColor(widgetEntry ? Color.TRANSPARENT : Design.background(this));
        getWindow().setNavigationBarColor(Design.background(this));
        if (widgetEntry) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            getWindow().setDimAmount(Design.dark(this) ? 0.50f : 0.42f);
            getWindow().setGravity(Gravity.BOTTOM);
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            setFinishOnTouchOutside(false);
        }
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (Design.dark(this)) flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        if (widgetEntry) {
            GradientDrawable sheet = Design.shape(Design.background(this), 26, this);
            root.setBackground(sheet);
            View handle = new View(this);
            handle.setBackground(Design.shape(Design.border(this), 3, this));
            LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(38), dp(4));
            handleParams.gravity = Gravity.CENTER_HORIZONTAL;
            handleParams.setMargins(0, dp(11), 0, dp(3));
            root.addView(handle, handleParams);
        } else root.setBackgroundColor(Design.background(this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(12), dp(12), dp(5));
        TextView title = Components.text(this, "Add expense", 24, Design.text(this), Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button close = Components.textButton(this, "×");
        close.setTextSize(26);
        close.setContentDescription("Close without saving");
        close.setOnClickListener(v -> closeEntry());
        top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(15), dp(20), dp(26));

        TextView amountLabel = Components.text(this, "Amount", 14, Design.textSoft(this), Typeface.NORMAL);
        content.addView(amountLabel);
        LinearLayout amountLine = new LinearLayout(this);
        amountLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView currency = Components.text(this, store.currencySymbol(), 36, Design.primary(this), Typeface.BOLD);
        amountLine.addView(currency);
        amountField = new EditText(this);
        amountField.setHint("0.00");
        amountField.setHintTextColor(Design.muted(this));
        amountField.setTextColor(Design.text(this));
        amountField.setTextSize(48);
        amountField.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amountField.setSingleLine(true);
        amountField.setBackground(null);
        amountField.setSelectAllOnFocus(true);
        amountField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        amountField.setPadding(dp(8), 0, 0, 0);
        amountLine.addView(amountField, new LinearLayout.LayoutParams(0, dp(88), 1));
        content.addView(amountLine);
        content.addView(Components.hairline(this));

        TextView categoryLabel = sectionHeading("Category");
        LinearLayout.LayoutParams sectionParams = match();
        sectionParams.setMargins(0, dp(30), 0, dp(10));
        content.addView(categoryLabel, sectionParams);
        categoryContainer = new LinearLayout(this);
        categoryContainer.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView categories = new HorizontalScrollView(this);
        categories.setHorizontalScrollBarEnabled(false);
        categories.addView(categoryContainer);
        content.addView(categories);

        TextView paymentLabel = sectionHeading("Payment method");
        LinearLayout.LayoutParams paymentLabelParams = match();
        paymentLabelParams.setMargins(0, dp(27), 0, dp(10));
        content.addView(paymentLabel, paymentLabelParams);
        paymentContainer = new LinearLayout(this);
        paymentContainer.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView payments = new HorizontalScrollView(this);
        payments.setHorizontalScrollBarEnabled(false);
        payments.addView(paymentContainer);
        content.addView(payments);

        TextView typeLabel = sectionHeading("Need or want? (optional)");
        LinearLayout.LayoutParams typeParams = match();
        typeParams.setMargins(0, dp(27), 0, dp(10));
        content.addView(typeLabel, typeParams);
        spendingTypePicker = new SpendingTypePicker(this, SpendingType.UNTAGGED);
        content.addView(spendingTypePicker, match());
        TextView typeHint = Components.text(this, "Skip if unsure. Tap a selected option to clear it.", 12, Design.textSoft(this), Typeface.NORMAL);
        typeHint.setPadding(0, dp(7), 0, 0);
        content.addView(typeHint);

        TextView noteLabel = sectionHeading("Note (optional)");
        LinearLayout.LayoutParams noteLabelParams = match();
        noteLabelParams.setMargins(0, dp(27), 0, dp(10));
        content.addView(noteLabel, noteLabelParams);
        noteField = new EditText(this);
        noteField.setHint("What was this for?");
        noteField.setHintTextColor(Design.muted(this));
        noteField.setTextColor(Design.text(this));
        noteField.setTextSize(15);
        noteField.setSingleLine(true);
        noteField.setBackground(Design.shape(Design.surface(this), 13, this));
        noteField.setPadding(dp(16), 0, dp(16), 0);
        content.addView(noteField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(20), dp(12), dp(20), dp(18));
        footer.setBackgroundColor(Design.background(this));
        TextView localNote = Components.text(this, "Saved locally first", 12, Design.textSoft(this), Typeface.NORMAL);
        localNote.setGravity(Gravity.CENTER);
        footer.addView(localNote);
        saveButton = Components.primaryButton(this, "Save expense");
        LinearLayout.LayoutParams saveParams = match();
        saveParams.setMargins(0, dp(8), 0, 0);
        footer.addView(saveButton, saveParams);
        saveButton.setOnClickListener(v -> submit());
        root.addView(footer);

        if (!widgetEntry) {
            LinearLayout types = new LinearLayout(this);
            Button expense = LedgerUi.chip(this, "Expense", true);
            Button income = LedgerUi.chip(this, "Income", false);
            Button more = LedgerUi.chip(this, "More", false);
            for(Button button:new Button[]{expense,income,more}){
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMarginEnd(dp(6));types.addView(button,p);
            }
            LinearLayout.LayoutParams modeParams=match();modeParams.bottomMargin=dp(20);content.addView(types,0,modeParams);
            income.setOnClickListener(v -> openLedger("entry","INCOME"));
            more.setOnClickListener(v -> LedgerUi.choose(this,"More actions",java.util.Arrays.asList("Split payment / Change date","Transfer / Pay card","Lend / Borrow","Accounts"),-1,which -> {
                openLedger(which==3?"accounts":"entry",which==1?"TRANSFER":which==2?"LEND":"EXPENSE");
            }));
        }
        loadSelections();
        Intent intent = getIntent();
        if (intent != null) {
            String category = intent.getStringExtra("category");
            if (category != null && !category.isEmpty()) selectedCategory = category;
            String amount = intent.getStringExtra("amount");
            if (amount != null && !amount.isEmpty()) amountField.setText(amount);
            renderChips();
        }
        if (savedInstanceState != null) {
            amountField.setText(savedInstanceState.getString("amount", ""));
            noteField.setText(savedInstanceState.getString("note", ""));
            selectedCategory = savedInstanceState.getString("category", selectedCategory);
            selectedPayment = savedInstanceState.getString("payment", selectedPayment);
            spendingTypePicker.setValue(savedInstanceState.getString("spendingType", SpendingType.UNTAGGED));
            renderChips();
        }
        setContentView(root);
        if (widgetEntry) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(560));
            int height = Math.min(Math.round(getResources().getDisplayMetrics().heightPixels * 0.84f), dp(740));
            getWindow().setLayout(width, height);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::closeEntry);
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { closeEntry(); }

    private void closeEntry() {
        if (getIntent().getBooleanExtra(AddExpenseWidgetProvider.EXTRA_FROM_WIDGET, false) && isTaskRoot())
            finishAndRemoveTask();
        else finish();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("amount", amountField.getText().toString());
        outState.putString("note", noteField.getText().toString());
        outState.putString("category", selectedCategory);
        outState.putString("payment", selectedPayment);
        outState.putString("spendingType", spendingTypePicker.getValue());
        super.onSaveInstanceState(outState);
    }

    private TextView sectionHeading(String title) {
        return Components.text(this, title, 15, Design.text(this), Typeface.BOLD);
    }

    private void loadSelections() {
        selectedCategory = store.defaultCategory();
        selectedPayment = store.defaultPaymentMode();
        List<String> categories = store.categories();
        if (!categories.contains(selectedCategory)) selectedCategory = categories.contains(store.lastCategory())
                ? store.lastCategory() : (categories.isEmpty() ? "" : categories.get(0));
        List<String> payments = store.paymentModes();
        if (!payments.contains(selectedPayment)) selectedPayment = payments.contains(store.lastPaymentMode())
                ? store.lastPaymentMode() : (payments.isEmpty() ? "" : payments.get(0));
        renderChips();
    }

    private void renderChips() {
        categoryContainer.removeAllViews();
        for (String category : store.categories())
            categoryContainer.addView(buildChip(category, category.equals(selectedCategory), true));
        paymentContainer.removeAllViews();
        if (ledgerPayments.isEmpty()) {
            for (String payment : store.paymentModes())
                paymentContainer.addView(buildChip(payment, payment.equals(selectedPayment), false));
        } else for (PaymentChoice choice : ledgerPayments) {
            View chip = buildChip(choice.id, choice.id.equals(selectedPayment), false);
            ((TextView) chip).setText(choice.label);
            paymentContainer.addView(chip);
        }
    }

    private View buildChip(String value, boolean active, boolean category) {
        TextView chip = Components.text(this, value, 14,
                active ? Design.onPrimary(this) : Design.text(this), Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setMinHeight(dp(48));
        chip.setBackground(Design.shape(active ? Design.primary(this) : Design.surface(this), 12, this));
        chip.setOnClickListener(v -> {
            if (category) selectedCategory = value;
            else selectedPayment = value;
            renderChips();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        params.setMarginEnd(dp(8));
        chip.setLayoutParams(params);
        return chip;
    }

    private void openLedger(String page,String kind){
        startActivity(LedgerActivity.intent(this,page).putExtra("kind",kind).putExtra("amount",amountField.getText().toString()).putExtra("note",noteField.getText().toString()).putExtra("paymentChoice",selectedPayment));
        finish();
    }

    private void loadPayments() {
        paymentLoaded = false;
        new Thread(() -> {
            try {
                Ledger snapshot = Ledger.load(AppDatabase.get(this));
                List<PaymentChoice> choices = PaymentChoice.list(snapshot, true);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    ledgerPayments = choices;
                    if (!choices.isEmpty()) {
                        String preferred = getSharedPreferences("ledger_ui", 0).getString("defaultPayment", "");
                        if (preferred.isEmpty()) preferred = getSharedPreferences("ledger_ui", 0).getString("lastPayment", "");
                        boolean exists = false;
                        for (PaymentChoice c : choices) if (c.id.equals(selectedPayment)) exists = true;
                        if (!exists) {
                            selectedPayment = choices.get(0).id;
                            for (PaymentChoice c : choices) if (c.id.equals(preferred)) selectedPayment = c.id;
                        }
                    }
                    paymentLoaded = true;
                    renderChips();
                });
            } catch (Exception e) { runOnUiThread(() -> Components.customToast(this, "Could not load payment accounts. Reopen entry to retry.", true)); }
        }).start();
    }

    @Override protected void onResume() { super.onResume(); if (store != null && paymentContainer != null) loadPayments(); }

    private void submit() {
        if (!paymentLoaded) { Components.customToast(this, "Loading payment accounts…", true); return; }
        double amount;
        try { amount = Double.parseDouble(AmountUtil.normalize(amountField.getText().toString())); }
        catch (Exception error) { Components.customToast(this, "Enter a valid amount", true); return; }
        if (!Double.isFinite(amount) || amount <= 0) { Components.customToast(this, "Amount must be greater than zero", true); return; }
        if (selectedCategory == null || selectedCategory.isEmpty()) { Components.customToast(this, "Choose a category", true); return; }
        if (selectedPayment == null || selectedPayment.isEmpty()) { Components.customToast(this, "Choose a payment method", true); return; }
        saveButton.setEnabled(false);
        saveButton.setText("Saving…");

        long now = System.currentTimeMillis();
        TransactionEntity tx = new TransactionEntity();
        tx.transactionId = java.util.UUID.randomUUID().toString();
        tx.date = new java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new java.util.Date(now));
        tx.time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date(now));
        try { tx.amountMinor = Money.parse(amountField.getText().toString()); }
        catch (Exception e) { saveButton.setEnabled(true); saveButton.setText("Save expense"); Components.customToast(this, "Use at most two decimal places", true); return; }
        tx.personalMinor = tx.amountMinor;
        tx.effectiveAt = now;
        tx.amount = tx.amountMinor / 100.0;
        for (PaymentChoice c : ledgerPayments) if (c.id.equals(selectedPayment)) {
            tx.accountId = c.accountId; tx.routeId = c.routeId; tx.paymentMode = c.label;
        }
        tx.category = store.backendValue(selectedCategory);
        if (tx.accountId.isEmpty()) tx.paymentMode = store.backendValue(selectedPayment);
        tx.remarks = noteField.getText().toString().trim();
        tx.spendingType = spendingTypePicker.getValue();
        tx.deviceName = store.deviceName();
        tx.createdAt = now;
        tx.updatedAt = now;
        tx.syncStatus = TransactionEntity.SYNC_LOCAL_ONLY;
        tx.deleted = false;

        new Thread(() -> {
            try {
                LedgerStore.save(this, tx);
                boolean belowZero = false;
                try { Ledger after = Ledger.load(AppDatabase.get(this)); AccountEntity savedAccount = after.account(tx.accountId);
                    belowZero = savedAccount != null && !savedAccount.isCard() && after.balance(savedAccount) < 0;
                } catch (Exception ignored) { /* A warning must not turn a committed save into a failure. */ }
                final boolean negative = belowZero;
                runOnUiThread(() -> {
                    store.recordSelection(selectedCategory, tx.paymentMode);
                    if (!tx.accountId.isEmpty()) getSharedPreferences("ledger_ui", 0).edit().putString("lastPayment", selectedPayment).apply();
                    store.recordOperation("Expense saved", true, "Saved locally · " + tx.date + " " + tx.time);
                    NotificationHelper.saved(this);
                    Components.customToast(this, negative ? "Expense saved · account balance is negative" : "Expense saved locally", false);
                    sendBroadcast(new Intent(getPackageName() + ".DATA_UPDATED").setPackage(getPackageName()));
                    closeEntry();
                });
                try {
                    SyncEngine.SyncResult result = SyncEngine.syncAllPending(this, store);
                    if (result.synced > 0) store.recordSync(System.currentTimeMillis());
                    if (result.synced > 0 || result.failed > 0)
                        store.recordOperation("Auto sync", result.failed == 0,
                                result.synced + " synced" + (result.failed > 0 ? ", " + result.failed + " failed: " + result.lastError : result.lastError.isEmpty()?"":", Dashboard: "+result.lastError));
                } catch (Exception ignored) {
                    // A failed mirror must never turn a successful local save into an error.
                }
                MainActivity.notifyPendingSyncCompleted();
            } catch (Exception error) {
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText("Save expense");
                    Components.customToast(this, "Could not save. Please try again.", true);
                });
            }
        }).start();
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) { return Design.dp(this, value); }
}
