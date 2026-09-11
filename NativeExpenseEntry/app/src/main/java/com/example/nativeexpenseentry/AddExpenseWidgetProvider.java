package com.example.nativeexpenseentry;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public final class AddExpenseWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget_add_expense);
            Intent intent = new Intent(context, QuickEntryActivity.class).setAction(MainActivity.ACTION_ADD_EXPENSE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            view.setOnClickPendingIntent(R.id.widget_root, pending); manager.updateAppWidget(id, view);
        }
    }
}
