package com.pocketledger.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/** Notification helper — migrated from V1, updated package. */
final class NotificationHelper {
    private static final String CHANNEL = "pocket_ledger_saved";

    static void prepare(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Expense saved", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shown when an expense is saved locally");
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    /** Show a brief "Expense saved" notification. */
    static void saved(Context c) {
        try {
            Notification.Builder b = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_input_add)
                    .setContentTitle("Expense saved")
                    .setContentText("Saved locally · sync when ready")
                    .setAutoCancel(true);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(1, b.build());
        } catch (Exception ignored) { }
    }
}
