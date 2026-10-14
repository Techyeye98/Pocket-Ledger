package com.pocketledger.v2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public class Design {
    // Folio: cool paper and deep evergreen, with the website's mint/teal/amber vocabulary.
    public static int primary(Context c) { return dark(c) ? Color.parseColor("#65E2A4") : Color.parseColor("#087C4B"); }
    public static int onPrimary(Context c) { return dark(c) ? Color.parseColor("#062116") : Color.WHITE; }
    public static int primaryMuted(Context c) { return dark(c) ? Color.parseColor("#244C38") : Color.parseColor("#D9F1E3"); }
    public static int background(Context c) { return dark(c) ? Color.parseColor("#07130E") : Color.parseColor("#F2F8F4"); }
    public static int surface(Context c) { return dark(c) ? Color.parseColor("#10231A") : Color.WHITE; }
    public static int surfaceRaised(Context c) { return dark(c) ? Color.parseColor("#19382A") : Color.parseColor("#E5F1E9"); }
    public static int text(Context c) { return dark(c) ? Color.parseColor("#EFF9F2") : Color.parseColor("#10251B"); }
    public static int textSoft(Context c) { return dark(c) ? Color.parseColor("#B0CABA") : Color.parseColor("#526D5D"); }
    public static int muted(Context c) { return dark(c) ? Color.parseColor("#86A895") : Color.parseColor("#647D6D"); }
    public static int border(Context c) { return dark(c) ? Color.parseColor("#294638") : Color.parseColor("#D7E7DC"); }
    public static int success(Context c) { return dark(c) ? Color.parseColor("#77E7AF") : Color.parseColor("#087C4B"); }
    public static int warning(Context c) { return dark(c) ? Color.parseColor("#F2C879") : Color.parseColor("#966012"); }
    public static int error(Context c) { return dark(c) ? Color.parseColor("#FF998F") : Color.parseColor("#A9342B"); }

    public static boolean dark(Context c) {
        int theme = new ConfigStore(c).theme();
        if (theme == 1) return false;
        if (theme == 2) return true;
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static GradientDrawable shape(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable strokeShape(int color, int strokeColor, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static GradientDrawable borderTop(int color, int strokeColor, Context c) {
        // Since GradientDrawable doesn't support single-sided borders easily, 
        // we usually use LayerDrawable or a container view with padding.
        // We'll stick to full borders or rely on padding in layout.
        return strokeShape(color, strokeColor, 0, c);
    }

    public static int tint(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }
}
