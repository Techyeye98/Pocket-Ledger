package com.example.nativeexpenseentry;

import android.content.Intent;
import android.app.PendingIntent;
import android.annotation.SuppressLint;
import android.os.Build;
import android.service.quicksettings.TileService;

public final class AddExpenseTileService extends TileService {
    @SuppressLint("StartActivityAndCollapseDeprecated") @Override public void onClick() {
        Intent intent = new Intent(this, QuickEntryActivity.class).setAction(MainActivity.ACTION_ADD_EXPENSE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
