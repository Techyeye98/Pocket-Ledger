package com.example.nativeexpenseentry;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationHelper {
    private static final String CHANNEL = "saved_expenses";
    private NotificationHelper() { }
    static void prepare(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26) ((NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL, "Saved expenses", NotificationManager.IMPORTANCE_DEFAULT));
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 8);
    }
    static void saved(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1001, builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Expense saved").setContentText("Expense Saved Successfully").setAutoCancel(true).build());
    }
}
