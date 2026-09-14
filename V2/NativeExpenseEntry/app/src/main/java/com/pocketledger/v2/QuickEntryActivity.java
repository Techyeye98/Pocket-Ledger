package com.pocketledger.v2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quick Entry — V2 rewrite.
 *
 * V2 change: expense is saved to the LOCAL Room database immediately.
 * No Google Sheet URL is required. The Sheet is an optional secondary mirror;
 * after saving, ALL pending transactions are synced immediately in the
 * foreground, and the user can also press Sync in Settings.
 *
 * Flow:
 *   Validate → save to Room locally → update UI → finish
 *
 * UI is preserved from V1 — same layout, same polished bottom-sheet style.
 */
public final class QuickEntryActivity extends Activity {

    private static final String STATE_AMOUNT   = "amount";
    private static final String STATE_REMARK   = "remark";
    private static final String STATE_CATEGORY = "category";
    private static final String STATE_PAYMENT  = "payment";
    private static final String STATE_SAVING   = "saving";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    private EditText amount, remark;
    private TextView category, payment, error;
    private Button save;
    private String chosenCategory, chosenPayment;
    private boolean saving;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ConfigStore(this);
        NotificationHelper.prepare(this);

        saving = state != null && state.getBoolean(STATE_SAVING, false);
        chosenCategory = state == null
                ? initial(store.categories(), store.defaultCategory(), store.lastCategory())
                : state.getString(STATE_CATEGORY, "");
        chosenPayment = state == null
                ? initial(store.paymentModes(), store.defaultPaymentMode(), store.lastPaymentMode())
                : state.getString(STATE_PAYMENT, "");

        build(state);
        configureWindow();
        if (saving && save != null) { save.setEnabled(false); save.setText("Saving…"); }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_AMOUNT,   amount   == null ? "" : amount.getText().toString());
        out.putString(STATE_REMARK,   remark   == null ? "" : remark.getText().toString());
        out.putString(STATE_CATEGORY, chosenCategory);
        out.putString(STATE_PAYMENT,  chosenPayment);
        out.putBoolean(STATE_SAVING,  saving);
    }

    // NOTE: deliberately do NOT shut down `executor` here. The auto-sync task runs
    // on this same single-thread executor right after the local insert, and must be
    // allowed to finish pushing pending transactions (and marking them SYNCED) even
    // though the activity has been destroyed. Shutting it down mid-flight would
    // interrupt the HTTP call — the row could reach the Sheet yet never be marked
    // SYNCED locally, leaving the Ledger showing "Not synced" forever.
    @Override protected void onDestroy() { super.onDestroy(); }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String initial(List<String> list, String preferred, String recent) {
        return list.contains(preferred) ? preferred
                : (list.contains(recent) ? recent
                : (list.isEmpty() ? "" : list.get(0)));
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setBackgroundDrawable(Design.shape(this, Design.surface(this), 24, 0));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.gravity = Gravity.BOTTOM;
        lp.dimAmount = .40f;
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setAttributes(lp);
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void build(Bundle state) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(20));
        page.setBackground(Design.shape(this, Design.surface(this), 24, 0));
        scroll.addView(page);

        // Drag handle
        TextView handle = Components.text(this, "", 0, Design.muted(this), Typeface.NORMAL);
        handle.setBackground(Design.shape(this, Design.border(this), 3, 0));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, dp(2), 0, dp(18));
        page.addView(handle, hp);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Components.text(this, "Add an expense", 24, Design.text(this), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button cancel = Components.textButton(this, "Cancel");
        cancel.setContentDescription("Cancel expense entry");
        cancel.setOnClickListener(v -> finish());
        header.addView(cancel, new LinearLayout.LayoutParams(dp(72), dp(48)));
        page.addView(header);

        // Amount
        TextView amtLabel = Components.subtitle(this, "AMOUNT");
        amtLabel.setPadding(0, dp(20), 0, dp(8));
        page.addView(amtLabel);
        LinearLayout amountBox = new LinearLayout(this);
        amountBox.setGravity(Gravity.CENTER_VERTICAL);
        amountBox.setPadding(dp(16), 0, dp(16), 0);
        amountBox.setBackground(Design.shape(this, Design.surfaceAlt(this), 14, Design.border(this)));
        String currency = store.currencySymbol();
        TextView rupee = Components.text(this, currency, 26, Design.text(this), Typeface.BOLD);
        amountBox.addView(rupee);
        amount = new EditText(this);
        amount.setHint("0.00");
        amount.setHintTextColor(Design.muted(this));
        amount.setTextColor(Design.text(this));
        amount.setTextSize(40);
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        amount.setPadding(dp(10), 0, 0, 0);
        amount.setContentDescription("Amount");
        if (state != null) amount.setText(state.getString(STATE_AMOUNT, ""));
        amountBox.addView(amount, new LinearLayout.LayoutParams(0, dp(64), 1));
        page.addView(amountBox, wide(ViewGroup.LayoutParams.WRAP_CONTENT));

        // Category
        TextView catLabel = Components.subtitle(this, "CATEGORY");
        catLabel.setPadding(0, dp(20), 0, dp(8));
        page.addView(catLabel);
        category = select(chosenCategory, "Choose category");
        category.setOnClickListener(v -> pick("Choose category", store.categories(), true));
        page.addView(category, wide(dp(52)));

        // Payment mode
        TextView payLabel = Components.subtitle(this, "PAYMENT MODE");
        payLabel.setPadding(0, dp(16), 0, dp(8));
        page.addView(payLabel);
        payment = select(chosenPayment, "Choose payment mode");
        payment.setOnClickListener(v -> pick("Choose payment mode", store.paymentModes(), false));
        page.addView(payment, wide(dp(52)));

        // Note
        TextView noteLabel = Components.subtitle(this, "NOTE · OPTIONAL");
        noteLabel.setPadding(0, dp(16), 0, dp(8));
        page.addView(noteLabel);
        remark = new EditText(this);
        remark.setHint("What was this for?");
        remark.setHintTextColor(Design.muted(this));
        remark.setTextColor(Design.text(this));
        remark.setTextSize(16);
        remark.setMinLines(2);
        remark.setMaxLines(3);
        remark.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        remark.setPadding(dp(16), dp(12), dp(16), dp(12));
        remark.setBackground(Design.shape(this, Design.surfaceAlt(this), 14, Design.border(this)));
        if (state != null) remark.setText(state.getString(STATE_REMARK, ""));
        page.addView(remark, wide(ViewGroup.LayoutParams.WRAP_CONTENT));

        // Error + info
        error = Components.text(this, "", 13, Design.error(this), Typeface.NORMAL);
        error.setVisibility(View.GONE);
        error.setPadding(0, dp(10), 0, 0);
        page.addView(error);

        // V2: note says "Saved locally" not "Google Sheet"
        TextView note = Components.text(this,
                "Saved to your device. Sync to Google Sheets when ready.",
                13, Design.muted(this), Typeface.NORMAL);
        note.setPadding(0, dp(14), 0, dp(14));
        page.addView(note);

        // Save button
        save = Components.primaryButton(this, "Review & save");
        save.setOnClickListener(v -> review());
        page.addView(save, wide(dp(52)));

        setContentView(scroll);
        amount.requestFocus();
        amount.postDelayed(() ->
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(amount, InputMethodManager.SHOW_IMPLICIT), 180);
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void pick(String title, List<String> options, boolean isCategory) {
        if (options.isEmpty()) { showError("Add an option in Settings first."); return; }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options.toArray(new String[0]),
                        options.indexOf(isCategory ? chosenCategory : chosenPayment),
                        (d, which) -> {
                            if (isCategory) { chosenCategory = options.get(which); category.setText(chosenCategory); }
                            else            { chosenPayment  = options.get(which); payment.setText(chosenPayment); }
                            d.dismiss();
                        })
                .setNegativeButton("Cancel", null).show();
    }

    private void review() {
        String normalized;
        try { normalized = AmountUtil.normalize(amount.getText().toString()); }
        catch (Exception ex) { showError("Enter an amount greater than zero."); amount.requestFocus(); return; }
        if (!store.categories().contains(chosenCategory) || !store.paymentModes().contains(chosenPayment)) {
            showError("Choose a valid category and payment mode."); return;
        }
        hideError();
        String summary = store.currencySymbol() + normalized + "\n"
                + chosenCategory + " · " + chosenPayment
                + (remark.getText().toString().trim().isEmpty() ? ""
                : "\n\u201C" + remark.getText().toString().trim() + "\u201D");
        new AlertDialog.Builder(this)
                .setTitle("Save this expense?")
                .setMessage(summary)
                .setNegativeButton("Edit", null)
                .setPositiveButton("Save", (d, w) -> submit(normalized))
                .show();
    }

    /**
     * V2 submit: write to Room local database, no network call required.
     *
     * Transaction flow:
     *   1. Build TransactionEntity with a fresh UUID + current timestamp
     *   2. Insert via Room on a background thread
     *   3. On success: show notification + toast, record last selection, finish
     *
     * Sync to Google Sheets is done separately via explicit user action in Settings.
     */
    private void submit(String normalized) {
        if (saving) return;
        saving = true;
        save.setEnabled(false);
        save.setText("Saving…");
        save.performHapticFeedback(HapticFeedbackConstants.CONFIRM);

        Date now        = new Date();
        String date     = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(now);
        String time     = new SimpleDateFormat("HH:mm:ss",  Locale.US).format(now);
        long   epochMs  = now.getTime();

        TransactionEntity tx = new TransactionEntity();
        tx.transactionId = UUID.randomUUID().toString();
        tx.amount        = Double.parseDouble(normalized);
        tx.category      = store.backendValue(chosenCategory);
        tx.paymentMode   = store.backendValue(chosenPayment);
        tx.remarks       = remark.getText().toString().trim();
        tx.date          = date;
        tx.time          = time;
        tx.deviceName    = store.deviceName();
        tx.createdAt     = epochMs;
        tx.updatedAt     = epochMs;
        tx.syncStatus    = TransactionEntity.SYNC_LOCAL_ONLY;
        tx.deleted       = false;

        executor.execute(() -> {
            try {
                AppDatabase.get(this).transactionDao().insert(tx);
                runOnUiThread(() -> {
                    saving = false; save.setEnabled(true); save.setText("Review & save");
                    store.recordSelection(chosenCategory, chosenPayment);
                    store.recordOperation("Expense saved", true, "Saved locally · " + date + " " + time);
                    NotificationHelper.saved(this);
                    Components.customToast(this, "Expense saved locally", false);
                    finish();
                });
                // Background, same single-thread executor: immediately attempt to sync
                // ALL pending transactions (the new one + any previously failed).
                // Silent no-op when Sheets URL is not configured yet.
                autoSyncPending();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false; save.setEnabled(true); save.setText("Review & save");
                    showError("Could not save. Please try again.");
                });
            }
        });
    }

    private void showError(String message) { error.setText(message); error.setVisibility(View.VISIBLE); }
    private void hideError()               { error.setVisibility(View.GONE); }

    /**
     * Immediately (foreground, on the background executor) attempt to sync ALL
     * pending/non-SYNCED transactions to Google Sheets — the newly saved one plus
     * any older transactions that failed previously. Concurrent with the UI
     * toast/finish above. Silent no-op when Sheets is not configured. Failed
     * transactions stay pending in Room for the next sync.
     */
    private void autoSyncPending() {
        try {
            SyncEngine.SyncResult r = SyncEngine.syncAllPending(this, store);
            if (r.synced > 0) {
                store.recordSync(System.currentTimeMillis());
            }
            if (r.synced > 0 || r.failed > 0) {
                store.recordOperation("Auto sync", r.failed == 0,
                        r.synced + " synced" + (r.failed > 0 ? ", " + r.failed + " failed: " + r.lastError : ""));
            }
        } catch (Exception ignored) {
            // The local save already succeeded. A sync attempt must never be
            // reported as a save failure, regardless of what happened above.
        }
        // Sync finished (or was skipped). MainActivity may show a Ledger row that
        // was rendered BEFORE the sync completed — re-render it so the transaction
        // stops showing "Not synced". Re-queries Room, so it's a no-op effect if
        // nothing changed.
        MainActivity.notifyPendingSyncCompleted();
    }

    private TextView select(String value, String description) {
        TextView v = Components.text(this, value.isEmpty() ? description : value,
                16, Design.text(this), Typeface.NORMAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(16), 0, dp(16), 0);
        v.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        v.setBackground(Design.shape(this, Design.surfaceAlt(this), 14, Design.border(this)));
        v.setContentDescription(description);
        Components.addPressAnimation(v);
        return v;
    }

    private LinearLayout.LayoutParams wide(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }
    private int dp(int value) { return Design.dp(this, value); }
}
