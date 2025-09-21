package com.enixcoda.smsforward;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "BootReceiver received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_REBOOT.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) { // Xiaomi quick boot
            
            Log.d(TAG, "Device boot completed or app updated, checking if SMS forwarding should be enabled");
            
            // Add delay for MIUI devices to ensure system is fully ready
            if (BatteryOptimizationHelper.isMIUIDevice(context)) {
                Log.d(TAG, "MIUI device detected, adding delay before starting service");
                scheduleDelayedStart(context, 30000); // 30 seconds delay
            } else {
                startSMSForwardingService(context);
            }
        } else if ("com.enixcoda.smsforward.DELAYED_START".equals(action)) {
            Log.d(TAG, "Delayed start triggered");
            startSMSForwardingService(context);
        } else if ("com.enixcoda.smsforward.PERIODIC_CHECK".equals(action)) {
            Log.d(TAG, "Periodic check triggered");
            checkAndRestartService(context);
        }
    }

    private void startSMSForwardingService(Context context) {
        try {
            // Check if any forwarding method is enabled
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            
            final boolean enableSMS = sharedPreferences.getBoolean(context.getString(R.string.key_enable_sms), false);
            final String targetNumbersString = sharedPreferences.getString(context.getString(R.string.key_target_sms), "");
            final List<String> targetNumbers = PhoneNumberUtils.parsePhoneNumbers(targetNumbersString);
            
            final boolean enableWeb = sharedPreferences.getBoolean(context.getString(R.string.key_enable_web), false);
            final boolean enableTelegram = sharedPreferences.getBoolean(context.getString(R.string.key_enable_telegram), false);
            
            if (enableSMS || enableWeb || enableTelegram) {
                Log.d(TAG, "SMS forwarding is enabled, starting service");
                
                // Check if SMS is enabled but no numbers configured
                if (enableSMS && !PhoneNumberUtils.hasValidNumbers(targetNumbers)) {
                    Log.w(TAG, "SMS forwarding enabled but no valid numbers configured");
                }
                
                // Start the service to keep SMS forwarding active
                Intent serviceIntent = new Intent(context, SMSForwardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                // Set up periodic restart using AlarmManager as backup
                setupPeriodicRestart(context);
                
                Log.d(TAG, "SMS forwarding service started successfully");
            } else {
                Log.d(TAG, "SMS forwarding is disabled, not starting app");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting SMS forwarding service: " + e.getMessage(), e);
        }
    }

    private void scheduleDelayedStart(Context context, long delayMs) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, BootReceiver.class);
            intent.setAction("com.enixcoda.smsforward.DELAYED_START");
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            long triggerTime = SystemClock.elapsedRealtime() + delayMs;
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            
            Log.d(TAG, "Scheduled delayed start in " + delayMs + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling delayed start: " + e.getMessage(), e);
            // Fallback to immediate start
            startSMSForwardingService(context);
        }
    }

    private void setupPeriodicRestart(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, BootReceiver.class);
            intent.setAction("com.enixcoda.smsforward.PERIODIC_CHECK");
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 1, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Check every 30 minutes to reduce battery usage (was 5 minutes)
            long interval = 30 * 60 * 1000; // 30 minutes
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                SystemClock.elapsedRealtime() + interval, interval, pendingIntent);
            
            Log.d(TAG, "Set up periodic restart check every 30 minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up periodic restart: " + e.getMessage(), e);
        }
    }

    private void checkAndRestartService(Context context) {
        try {
            // Check if service should be running
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean enableSMS = sharedPreferences.getBoolean(context.getString(R.string.key_enable_sms), false);
            final boolean enableWeb = sharedPreferences.getBoolean(context.getString(R.string.key_enable_web), false);
            final boolean enableTelegram = sharedPreferences.getBoolean(context.getString(R.string.key_enable_telegram), false);
            
            if (enableSMS || enableWeb || enableTelegram) {
                // Check if service is running by trying to start it
                Intent serviceIntent = new Intent(context, SMSForwardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                Log.d(TAG, "Periodic check: Service restarted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in periodic check: " + e.getMessage(), e);
        }
    }
}
