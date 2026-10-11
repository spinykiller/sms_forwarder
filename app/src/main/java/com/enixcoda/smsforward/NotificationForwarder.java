package com.enixcoda.smsforward;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telephony.SubscriptionManager;

import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pro: forwards notifications from the apps the user picked, through the same pipeline as SMS. */
public class NotificationForwarder extends NotificationListenerService {
    private static final int REMEMBERED = 100;
    private static final int SKIP_FLAGS = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_FOREGROUND_SERVICE;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Last title + text forwarded per notification: apps re-post the same notification on every update
    private final Map<String, String> lastTextByKey = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > REMEMBERED;
        }
    };

    static boolean hasAccess(Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> apps = prefs.getStringSet(getString(R.string.key_notification_apps), Collections.emptySet());
        if (!Billing.isPro(this) || !prefs.getBoolean(getString(R.string.key_enable_notifications), false)
                || !apps.contains(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if ((notification.flags & SKIP_FLAGS) != 0) return;

        Bundle extras = notification.extras;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text == null || text.toString().trim().isEmpty()) return;
        String body = text.toString();
        String seen = title + "\n" + body;
        synchronized (lastTextByKey) {
            if (seen.equals(lastTextByKey.put(sbn.getKey(), seen))) return;
        }
        // Our own forward showing up as a notification (e.g. Telegram picked here and as a destination): don't loop
        if (Dispatcher.wasRecentlySent(body)) return;

        String app = appLabel(sbn.getPackageName());
        String sender = title != null && !title.toString().trim().isEmpty() ? title.toString() : app;
        executor.execute(() -> Dispatcher.dispatch(this, sender, app, body, SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    // A new notification may reuse the key of a removed one: it must not count as a re-post
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (lastTextByKey) {
            lastTextByKey.remove(sbn.getKey());
        }
    }

    private String appLabel(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
