package com.pocketledger.v2;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Components {

    public static void addPressAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            if (!ValueAnimator.areAnimatorsEnabled()) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(110).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(170).start();
                    break;
            }
            return false;
        });
    }

    public static Button primaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Design.onPrimary(c));
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        b.setBackground(Design.shape(Design.primary(c), 14, c));
        b.setMinHeight(Design.dp(c, 52));
        b.setPadding(Design.dp(c, 22), Design.dp(c, 12), Design.dp(c, 22), Design.dp(c, 12));
        b.setStateListAnimator(null);
        addPressAnimation(b);
        return b;
    }

    public static Button secondaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Design.text(c));
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        b.setBackground(Design.strokeShape(Design.surface(c), Design.border(c), 14, c));
        b.setMinHeight(Design.dp(c, 52));
        b.setPadding(Design.dp(c, 16), Design.dp(c, 16), Design.dp(c, 16), Design.dp(c, 16));
        b.setStateListAnimator(null);
        addPressAnimation(b);
        return b;
    }

    public static Button textButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Design.text(c));
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinHeight(Design.dp(c, 48));
        b.setPadding(Design.dp(c, 12), Design.dp(c, 8), Design.dp(c, 12), Design.dp(c, 8));
        b.setStateListAnimator(null);
        addPressAnimation(b);
        return b;
    }

    public static TextView text(Context c, CharSequence text, float sp, int color, int style) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        
        if (sp >= 28) t.setLetterSpacing(-0.025f);
        
        return t;
    }

    public static TextView heroAmount(Context c, CharSequence text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        t.setTextColor(Design.text(c));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(-0.035f);
        return t;
    }

    public static TextView subtitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(Design.textSoft(c));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView badge(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        t.setTextColor(Design.background(c));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setBackground(Design.shape(color, 8, c));
        t.setPadding(Design.dp(c, 6), Design.dp(c, 4), Design.dp(c, 6), Design.dp(c, 4));
        return t;
    }

    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Design.border(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            Math.max(1, Design.dp(c, 1) / 2) // Hairline
        ));
        return v;
    }
    
    public static View vHairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Design.border(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(
            Math.max(1, Design.dp(c, 1) / 2), 
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        return v;
    }

    public static LinearLayout bentoBlock(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Design.shape(Design.surface(c), 18, c));
        return l;
    }

    public static void customToast(Activity activity, String message, boolean isError) {
        Toast toast = new Toast(activity);
        LinearLayout l = new LinearLayout(activity);
        l.setBackground(Design.shape(Design.surfaceRaised(activity), 12, activity));
        l.setPadding(Design.dp(activity, 16), Design.dp(activity, 12), Design.dp(activity, 16), Design.dp(activity, 12));
        
        TextView t = new TextView(activity);
        t.setText(message);
        t.setTextColor(Design.text(activity));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        l.addView(t);
        
        toast.setView(l);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, Design.dp(activity, 100));
        toast.show();
    }
}
