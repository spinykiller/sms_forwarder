package com.enixcoda.smsforward;

import static android.provider.ContactsContract.CommonDataKinds.*;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    // Reverse message from a target number: "To <number>:\n<text>" sends <text> to <number> from this phone.
    private static final Pattern REVERSE_FORMAT = Pattern.compile("To (\\+?\\d+?):\\n(.*)", Pattern.DOTALL);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()))
            return;

        // Contact lookup and network calls must not run on the main thread.
        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                handle(context, intent);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private void handle(Context context, Intent intent) {
        if (Dispatcher.isPaused(context)) return;
        // Everything off: don't act on reverse commands either (they send chargeable SMS)
        boolean anyEnabled = false;
        for (int key : new int[]{R.string.key_enable_sms, R.string.key_enable_telegram, R.string.key_enable_web, R.string.key_enable_email})
            anyEnabled |= PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(key), false);
        if (!anyEnabled) return;
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final List<String> targetNumbers = PhoneNumberUtils.parsePhoneNumbers(
                sharedPreferences.getString(context.getString(R.string.key_target_sms), ""));

        // Reply/forward on the SIM that received the message (matters on dual-SIM phones).
        final int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        // A long SMS arrives as several parts; join them per sender so it's forwarded as one message.
        final Map<String, StringBuilder> messagesBySender = new LinkedHashMap<>();
        for (SmsMessage part : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            messagesBySender.computeIfAbsent(part.getDisplayOriginatingAddress(), k -> new StringBuilder())
                    .append(part.getDisplayMessageBody());
        }

        for (Map.Entry<String, StringBuilder> entry : messagesBySender.entrySet()) {
            String senderNumber = entry.getKey();
            String rawMessageContent = entry.getValue().toString();

            // Blocked senders can't forward or send reverse messages
            if (sharedPreferences.getBoolean(context.getString(R.string.key_enable_blocklist), false)
                    && PhoneNumberUtils.isSenderBlocked(senderNumber,
                            PhoneNumberUtils.splitList(sharedPreferences.getString(context.getString(R.string.key_blocked_numbers), "")))) continue;

            // A message from a target number is never forwarded (prevents loops); it may be a reverse message.
            if (PhoneNumberUtils.isSenderInTargetList(senderNumber, targetNumbers)) {
                Matcher reverse = REVERSE_FORMAT.matcher(rawMessageContent);
                // Prevent sending to the same number
                if (reverse.matches() && !PhoneNumberUtils.numbersMatch(reverse.group(1), senderNumber)) {
                    try {
                        Forwarder.sendSMS(context, subId, reverse.group(1), reverse.group(2), -1);
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Error sending reverse message: " + e, e);
                    }
                }
                continue;
            }

            Dispatcher.dispatch(context, senderNumber, lookupContactName(context, senderNumber), rawMessageContent, subId);
        }
    }

    private String lookupContactName(Context context, String phoneNumber) {
        // Contacts is optional: without it, forward with the number only.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return "";
        Uri filterUri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{Phone.DISPLAY_NAME};
        String[] senderContactNames = {};
        try (Cursor cur = context.getContentResolver().query(filterUri, projection, null, null, null)) {
            if (cur != null) {
                senderContactNames = new String[cur.getCount()];
                int i = 0;
                while (cur.moveToNext()) {
                    senderContactNames[i] = cur.getString(0);
                    i++;
                }
            }
        }
        return String.join(", ", senderContactNames);
    }
}
