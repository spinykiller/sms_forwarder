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
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean enableSMS = sharedPreferences.getBoolean(context.getString(R.string.key_enable_sms), false);
        final String targetNumbersString = sharedPreferences.getString(context.getString(R.string.key_target_sms), "");
        final List<String> targetNumbers = PhoneNumberUtils.parsePhoneNumbers(targetNumbersString);

        final boolean enableWeb = sharedPreferences.getBoolean(context.getString(R.string.key_enable_web), false);
        final boolean enableTelegram = sharedPreferences.getBoolean(context.getString(R.string.key_enable_telegram), false);

        // Get blocklist settings; entries stay raw so alphanumeric sender IDs (e.g. "HDFCBK") can be blocked
        final boolean enableBlocklist = sharedPreferences.getBoolean(context.getString(R.string.key_enable_blocklist), false);
        final String blockedNumbersString = sharedPreferences.getString(context.getString(R.string.key_blocked_numbers), "");
        final List<String> blockedEntries = PhoneNumberUtils.splitList(blockedNumbersString);

        // Pro: forward only matching messages, custom format. Ignored if Pro lapses (forward everything, default format).
        final boolean pro = Billing.isPro(context);
        final boolean enableFilter = pro && sharedPreferences.getBoolean(context.getString(R.string.key_enable_filter), false);
        final List<String> filterKeywords = PhoneNumberUtils.splitList(sharedPreferences.getString(context.getString(R.string.key_filter_keywords), ""));
        final List<String> filterSenders = PhoneNumberUtils.splitList(sharedPreferences.getString(context.getString(R.string.key_filter_senders), ""));
        final String template = pro ? sharedPreferences.getString(context.getString(R.string.key_template), "").trim() : "";

        if (!enableSMS && !enableTelegram && !enableWeb) return;

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

            // Check if sender is blocked
            if (enableBlocklist && PhoneNumberUtils.isSenderBlocked(senderNumber, blockedEntries)) {
                Log.d(TAG, "Message from blocked sender will not be forwarded");
                continue;
            }

            // A message from a target number is never forwarded (prevents loops); it may be a reverse message.
            if (PhoneNumberUtils.isSenderInTargetList(senderNumber, targetNumbers)) {
                Matcher reverse = REVERSE_FORMAT.matcher(rawMessageContent);
                // Prevent sending to the same number
                if (reverse.matches() && !PhoneNumberUtils.numbersMatch(reverse.group(1), senderNumber)) {
                    try {
                        Forwarder.sendSMS(subId, reverse.group(1), reverse.group(2));
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Error sending reverse message: " + e, e);
                    }
                }
                continue;
            }

            if (enableFilter && !MessageRules.matchesFilter(senderNumber, rawMessageContent, filterKeywords, filterSenders)) {
                Log.d(TAG, "Message doesn't match the filter, not forwarded");
                continue;
            }

            String senderNames = lookupContactName(context, senderNumber);
            String senderLabel = (senderNames.isEmpty() ? "" : senderNames + " ") + "(" + senderNumber + ")";
            String smsText = MessageRules.format(template.isEmpty() ? MessageRules.DEFAULT_SMS_FORMAT : template,
                    senderLabel, senderNumber, senderNames, rawMessageContent);
            String telegramText = MessageRules.format(template.isEmpty() ? MessageRules.DEFAULT_TELEGRAM_FORMAT : template,
                    senderLabel, senderNumber, senderNames, rawMessageContent);

            if (enableSMS) {
                for (String targetNumber : targetNumbers) {
                    Forwarder.forwardViaSMS(subId, smsText, targetNumber);
                }
            }
            // Try now; a transient failure (offline, server down) is queued and retried once online.
            for (String channel : ForwardWorker.CHANNELS) {
                if (Forwarder.send(context, channel, senderLabel, rawMessageContent, telegramText) == Forwarder.RETRY)
                    ForwardWorker.enqueue(context, channel, senderLabel, rawMessageContent, telegramText);
            }
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
