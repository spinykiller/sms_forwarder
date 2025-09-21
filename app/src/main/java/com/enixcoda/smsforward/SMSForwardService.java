package com.enixcoda.smsforward;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

/**
 * A foreground service to keep the SMS forwarding functionality active
 * This helps prevent the system from killing the app due to battery optimization
 */
public class SMSForwardService extends Service {
    private static final String TAG = "SMSForwardService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "sms_forward_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SMS Forward Service created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "SMS Forward Service started");
        
        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Set up periodic restart using AlarmManager as additional backup
        setupServiceRestartAlarm();
        
        // Return START_STICKY to ensure the service is restarted if killed
        return START_STICKY;
    }

    private void setupServiceRestartAlarm() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, BootReceiver.class);
            intent.setAction("com.enixcoda.smsforward.PERIODIC_CHECK");
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 2, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Check every 60 minutes to reduce battery usage (was 10 minutes)
            long interval = 60 * 60 * 1000; // 60 minutes
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                SystemClock.elapsedRealtime() + interval, interval, pendingIntent);
            
            Log.d(TAG, "Set up service restart alarm every 60 minutes");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up service restart alarm: " + e.getMessage(), e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "SMS Forward Service destroyed");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarding",
                NotificationManager.IMPORTANCE_MIN // Changed to MIN to reduce battery usage
            );
            channel.setDescription("Notifications for SMS forwarding service");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET); // Hide from lock screen

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // Create intent to open the app when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Get current forwarding status
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableSMS = sharedPreferences.getBoolean(getString(R.string.key_enable_sms), false);
        boolean enableWeb = sharedPreferences.getBoolean(getString(R.string.key_enable_web), false);
        boolean enableTelegram = sharedPreferences.getBoolean(getString(R.string.key_enable_telegram), false);

        String statusText = "SMS Forwarding Active";
        if (enableSMS || enableWeb || enableTelegram) {
            StringBuilder methods = new StringBuilder();
            if (enableSMS) {
                String targetNumbersString = sharedPreferences.getString(getString(R.string.key_target_sms), "");
                List<String> targetNumbers = PhoneNumberUtils.parsePhoneNumbers(targetNumbersString);
                if (PhoneNumberUtils.hasValidNumbers(targetNumbers)) {
                    methods.append("SMS(").append(targetNumbers.size()).append(") ");
                } else {
                    methods.append("SMS ");
                }
            }
            if (enableWeb) methods.append("Web ");
            if (enableTelegram) methods.append("Telegram ");
            statusText = "Forwarding via: " + methods.toString().trim();
        } else {
            statusText = "SMS Forwarding Disabled";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forward")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Changed to MIN
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
            .setShowWhen(false) // Don't show timestamp
            .setLocalOnly(true) // Don't sync across devices
            .build();
    }
}
