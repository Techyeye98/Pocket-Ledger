package com.pocketledger.v2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Reusable UI component factory — migrated from V1.
 * All component constructors read live design tokens so dark/light mode
 * is always correct without additional state.
 */
final class Components {
    private Components() {}

    static void addPressAnimation(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.8f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    static Button primaryButton(Context c, String title) {
        Button b = new Button(c);
        b.setText(title);
        b.setTextColor(Design.primaryOn(c));
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(Design.shape(c, Design.primary(c), 14, 0));
        b.setMinHeight(Design.dp(c, 52));
        addPressAnimation(b);
        return b;
    }

    static Button secondaryButton(Context c, String title) {
        Button b = new Button(c);
        b.setText(title);
        b.setTextColor(Design.text(c));
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(Design.shape(c, Design.surface(c), 14, Design.border(c)));
        b.setMinHeight(Design.dp(c, 52));
        addPressAnimation(b);
        return b;
    }

    static Button textButton(Context c, String title) {
        Button b = new Button(c);
        b.setText(title);
        b.setTextColor(Design.primary(c));
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        addPressAnimation(b);
        return b;
    }

    static TextView text(Context c, String text, float size, int color, int style) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        return t;
    }

    static TextView subtitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text.toUpperCase());
        t.setTextSize(12);
        t.setTextColor(Design.muted(c));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static TextView badge(Context c, String text, int colorToken) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(colorToken);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(Design.dp(c, 8), Design.dp(c, 4), Design.dp(c, 8), Design.dp(c, 4));
        t.setBackground(Design.shape(c, Design.tint(colorToken, 30), 8, 0));
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Design.card(c));
        l.setPadding(Design.dp(c, 16), Design.dp(c, 16), Design.dp(c, 16), Design.dp(c, 16));
        return l;
    }

    static void customToast(Activity a, String message, boolean isError) {
        a.runOnUiThread(() -> {
            Toast t = Toast.makeText(a, message, Toast.LENGTH_LONG);
            View view = t.getView();
            if (view != null) {
                view.setBackground(Design.shape(a, isError ? Design.error(a) : Design.success(a), 14, 0));
                TextView text = view.findViewById(android.R.id.message);
                if (text != null) {
                    text.setTextColor(Color.WHITE);
                    text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                }
            }
            t.show();
        });
    }
}
