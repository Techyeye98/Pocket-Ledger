package com.pocketledger.v2;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.List;
import java.util.function.IntConsumer;

/** The ledger uses the existing V6 palette and component geometry. */
final class LedgerUi {
    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c) {
            @Override public AlertDialog create() {
                AlertDialog dialog=super.create();
                dialog.setOnShowListener(d->{
                    dialog.getWindow().setBackgroundDrawable(Design.shape(Design.surface(c),20,c));
                    if(dialog.getListView()!=null)dialog.getListView().setBackgroundColor(Design.surface(c));
                    for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL})
                        if(dialog.getButton(which)!=null)dialog.getButton(which).setTextColor(Design.primary(c));
                });
                return dialog;
            }
            @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
        };
    }
    static String label(String value) {
        switch (value) {
            case "EXPENSE": return "Expense";
            case "INCOME": return "Income";
            case "TRANSFER": return "Transfer";
            case "LEND": return "Lend money";
            case "BORROW": return "Borrow money";
            case "FRIEND_PAID": return "Friend paid for me";
            case "ADJUSTMENT": return "Adjust balance";
            case "REPAYMENT_IN": return "Money received";
            case "REPAYMENT_OUT": return "Money repaid";
            case "WRITE_OFF_RECEIVABLE": return "Write off debt";
            case "FORGIVE_PAYABLE": return "Debt forgiven";
            case "REFUND": return "Refund";
            case "BANK": return "Bank account";
            case "CASH": return "Cash";
            case "WALLET": return "Wallet";
            case "CARD": return "Credit card";
            case "LENA/DENA": return "Lena / Dena";
            default: return value.replaceAll("\\s*🏷️?$", "");
        }
    }

    static AlertDialog choose(Context c, String title, List<String> options, int selected, IntConsumer action) {
        TextView heading = Components.text(c, title, 20, Design.text(c), Typeface.BOLD);
        heading.setPadding(Design.dp(c, 24), Design.dp(c, 24), Design.dp(c, 24), Design.dp(c, 16));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, options) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = Components.text(c, label(getItem(position)), 16,
                        position == selected ? Design.primary(c) : Design.text(c),
                        position == selected ? Typeface.BOLD : Typeface.NORMAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(Design.dp(c, 56));
                row.setPadding(Design.dp(c, 24), Design.dp(c, 14), Design.dp(c, 24), Design.dp(c, 14));
                row.setBackgroundColor(position == selected ? Design.primaryMuted(c) : Design.surface(c));
                return row;
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(c).setCustomTitle(heading)
                .setAdapter(adapter, (d, which) -> action.accept(which)).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getWindow().setBackgroundDrawable(Design.shape(Design.surface(c), 20, c));
            dialog.getListView().setBackgroundColor(Design.surface(c));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Design.primary(c));
        });
        dialog.show();
        return dialog;
    }

    static Button chip(Context c, String label, boolean active) {
        Button button = Components.textButton(c, label);
        button.setTextSize(14);
        button.setSelected(active);
        button.setTextColor(active ? Design.onPrimary(c) : Design.textSoft(c));
        button.setBackground(Design.shape(active ? Design.primary(c) : Design.surface(c), 12, c));
        button.setPadding(Design.dp(c, 16), Design.dp(c, 10), Design.dp(c, 16), Design.dp(c, 10));
        button.setMinHeight(Design.dp(c, 48));
        return button;
    }

    static LinearLayout fact(Context c, String label, String value, int ink) {
        LinearLayout column = new LinearLayout(c); column.setOrientation(LinearLayout.VERTICAL);
        TextView caption = Components.text(c, label, 12, Design.textSoft(c), Typeface.NORMAL);
        column.addView(caption);
        TextView amount = Components.text(c, value, 19, ink, Typeface.BOLD);
        amount.setPadding(0, Design.dp(c, 6), 0, 0);
        column.addView(amount);
        return column;
    }
}
