package com.enixcoda.smsforward;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Pro health check: a daily "still running" message and a low-battery alert, sent to Telegram/Web/Email (not SMS). */
public class HealthWorker extends Worker {
    private static final String WORK_NAME = "health";
    // State, not settings (no "key_" prefix): never exported
    private static final String LAST_HEARTBEAT = "health_last_heartbeat";
    private static final String LOW_ALERTED = "health_low_alerted";
    private static final long HEARTBEAT_EVERY_MS = TimeUnit.HOURS.toMillis(24) - TimeUnit.MINUTES.toMillis(30); // tolerate job jitter
    private static final int LOW_PERCENT = 15;
    private static final int RECOVERED_PERCENT = 25;

    public HealthWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Starts or stops the hourly check to match the settings and Pro status. */
    static void schedule(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean wanted = Billing.isPro(context) && (prefs.getBoolean(context.getString(R.string.key_heartbeat), false)
                || prefs.getBoolean(context.getString(R.string.key_low_battery), false));
        WorkManager workManager = WorkManager.getInstance(context);
        if (!wanted) {
            workManager.cancelUniqueWork(WORK_NAME);
            return;
        }
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(HealthWorker.class, 1, TimeUnit.HOURS)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!Billing.isPro(context)) return Result.success();
        BatteryManager battery = context.getSystemService(BatteryManager.class);
        int percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        String device = deviceName(context);
        long now = System.currentTimeMillis();

        if (prefs.getBoolean(context.getString(R.string.key_heartbeat), false) && now - prefs.getLong(LAST_HEARTBEAT, 0) >= HEARTBEAT_EVERY_MS) {
            if (sendToAll(context, context.getString(R.string.health_heartbeat, device, percent)))
                prefs.edit().putLong(LAST_HEARTBEAT, now).apply();
        }
        if (prefs.getBoolean(context.getString(R.string.key_low_battery), false)) {
            boolean alerted = prefs.getBoolean(LOW_ALERTED, false);
            if (!alerted && percent <= LOW_PERCENT && !battery.isCharging()) {
                if (sendToAll(context, context.getString(R.string.health_low_battery, device, percent)))
                    prefs.edit().putBoolean(LOW_ALERTED, true).apply();
            } else if (alerted && (percent >= RECOVERED_PERCENT || battery.isCharging())) {
                prefs.edit().putBoolean(LOW_ALERTED, false).apply(); // alert again next time it runs low
            }
        }
        return Result.success();
    }

    /** True if at least one method accepted it. */
    private static boolean sendToAll(Context context, String text) {
        boolean any = false;
        String sender = context.getString(R.string.app_name);
        for (String channel : ForwardWorker.CHANNELS) {
            String destination = Forwarder.destination(context, channel);
            if (!destination.isEmpty()) any |= Forwarder.send(context, channel, destination, sender, text, text) == Forwarder.OK;
        }
        return any;
    }

    private static String deviceName(Context context) {
        String name = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
        return name != null ? name : Build.MODEL;
    }
}
