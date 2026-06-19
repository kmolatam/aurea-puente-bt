package com.kmo.aureaprintbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = BridgeCore.prefs(context);
        if (!p.getBoolean("auto_start", false)) return;
        Intent svc = new Intent(context, BridgeService.class);
        svc.setAction(BridgeCore.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
        else context.startService(svc);
    }
}
