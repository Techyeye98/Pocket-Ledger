package com.pocketledger.v2;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * Home screen widget — launches QuickEntryActivity.
 * Migrated from V1, updated to V2 package and action string.
 *
 * The layout is chosen responsively from the widget's current width so the
 * widget stays clean at any size:
 *   < 160dp      → compact single-row layout
 *   160–279dp    → standard layout (original design)
 *   >= 280dp     → expanded layout
 * The same intent/click target is applied to every variant.
 */
public final class AddExpenseWidgetProvider extends AppWidgetProvider {

    private static final int STANDARD_MIN_WIDTH_DP = 160;
    private static final int EXPANDED_MIN_WIDTH_DP = 280;

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, manager, id));
        }
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        // Widget was resized → pick the layout that fits the new width.
        manager.updateAppWidget(id, buildViews(context, manager, id));
    }

    private static RemoteViews buildViews(Context context, AppWidgetManager manager, int id) {
        int minWidthDp = manager.getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int layoutId = pickLayout(minWidthDp);

        RemoteViews view = new RemoteViews(context.getPackageName(), layoutId);
        Intent intent = new Intent(context, QuickEntryActivity.class)
                .setAction(MainActivity.ACTION_ADD_EXPENSE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        view.setOnClickPendingIntent(R.id.widget_root, pending);
        return view;
    }

    private static int pickLayout(int minWidthDp) {
        if (minWidthDp < STANDARD_MIN_WIDTH_DP) return R.layout.widget_add_expense_compact;
        if (minWidthDp >= EXPANDED_MIN_WIDTH_DP) return R.layout.widget_add_expense_large;
        return R.layout.widget_add_expense;
    }
}