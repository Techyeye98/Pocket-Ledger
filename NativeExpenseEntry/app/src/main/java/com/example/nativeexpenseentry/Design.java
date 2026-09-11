package com.example.nativeexpenseentry;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** The app's single source of visual tokens for light and dark modes. */
final class Design {
    private Design() { }
    static boolean dark(Context c) {
        int theme = new ConfigStore(c).theme();
        if (theme == 1) return false;
        if (theme == 2) return true;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
    static int primary(Context c) { return Color.parseColor(dark(c) ? "#7FD6C4" : "#16414F"); }
    static int primaryOn(Context c) { return Color.parseColor(dark(c) ? "#0B1F26" : "#FFFFFF"); }
    static int secondary(Context c) { return Color.parseColor(dark(c) ? "#3FBE99" : "#2F9E7E"); }
    static int accent(Context c) { return Color.parseColor(dark(c) ? "#F08C72" : "#E8735A"); }
    static int background(Context c) { return Color.parseColor(dark(c) ? "#0E1416" : "#FAFBFA"); }
    static int surface(Context c) { return Color.parseColor(dark(c) ? "#161D20" : "#FFFFFF"); }
    static int surfaceAlt(Context c) { return Color.parseColor(dark(c) ? "#1E272B" : "#EEF3F1"); }
    static int text(Context c) { return Color.parseColor(dark(c) ? "#EDF3F1" : "#0E2A32"); }
    static int muted(Context c) { return Color.parseColor(dark(c) ? "#93A5A9" : "#5B7075"); }
    static int border(Context c) { return Color.parseColor(dark(c) ? "#2A3438" : "#DDE7E3"); }
    static int success(Context c) { return Color.parseColor(dark(c) ? "#4FBD86" : "#2E9563"); }
    static int warning(Context c) { return Color.parseColor(dark(c) ? "#E0A34A" : "#C9871F"); }
    static int error(Context c) { return Color.parseColor(dark(c) ? "#E37166" : "#C4453A"); }
    static GradientDrawable shape(Context c, int color, int radiusDp, int strokeColor) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) shape.setStroke(dp(c, 1), strokeColor); return shape;
    }
    static int dp(Context c, int value) { return (int) (value * c.getResources().getDisplayMetrics().density + .5f); }
}
