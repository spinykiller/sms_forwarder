package com.enixcoda.smsforward;

import static android.provider.ContactsContract.CommonDataKinds.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION))
            return;

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean enableSMS = sharedPreferences.getBoolean(context.getString(R.string.key_enable_sms), false);
        final String targetNumbersString = sharedPreferences.getString(context.getString(R.string.key_target_sms), "");
        final List<String> targetNumbers = PhoneNumberUtils.parsePhoneNumbers(targetNumbersString);

        final boolean enableWeb = sharedPreferences.getBoolean(context.getString(R.string.key_enable_web), false);
        final String targetWeb = sharedPreferences.getString(context.getString(R.string.key_target_web), "");

        final boolean enableTelegram = sharedPreferences.getBoolean(context.getString(R.string.key_enable_telegram), false);
        final String targetTelegram = sharedPreferences.getString(context.getString(R.string.key_target_telegram), "");
        final String telegramToken = sharedPreferences.getString(context.getString(R.string.key_telegram_apikey), "");

        // Get blocklist settings
        final boolean enableBlocklist = sharedPreferences.getBoolean(context.getString(R.string.key_enable_blocklist), false);
        final String blockedNumbersString = sharedPreferences.getString(context.getString(R.string.key_blocked_numbers), "");
        final List<String> blockedNumbers = PhoneNumberUtils.parsePhoneNumbers(blockedNumbersString);

        if (!enableSMS && !enableTelegram && !enableWeb) return;

        final Bundle bundle = intent.getExtras();
        final Object[] pduObjects = (Object[]) bundle.get("pdus");
        if (pduObjects == null) return;

        for (Object messageObj : pduObjects) {
            SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) messageObj, (String) bundle.get("format"));
            String senderNumber = currentMessage.getDisplayOriginatingAddress();
            String senderNames = lookupContactName(context, senderNumber);
            String senderLabel = (senderNames.isEmpty() ? "" : senderNames + " ") + "(" + senderNumber + ")";
            String rawMessageContent = currentMessage.getDisplayMessageBody();

            // Check if sender is blocked
            if (enableBlocklist && PhoneNumberUtils.isSenderBlocked(senderNumber, blockedNumbers)) {
                Log.d(TAG, "Message from blocked sender " + senderNumber + " will not be forwarded");
                continue; // Skip this message, don't forward it
            }

            // Check if sender is in the target numbers list (for reverse messaging)
            if (PhoneNumberUtils.isSenderInTargetList(senderNumber, targetNumbers)) {
                // reverse message - sender is one of our target numbers
                String formatRegex = "To (\\+?\\d+?):\\n((.|\\n)*)";
                if (rawMessageContent.matches(formatRegex)) {
                    String forwardNumber = rawMessageContent.replaceFirst(formatRegex, "$1");
                    String forwardContent = rawMessageContent.replaceFirst(formatRegex, "$2");
                    
                    // Prevent sending to the same number
                    if (!PhoneNumberUtils.cleanPhoneNumber(forwardNumber).equals(PhoneNumberUtils.cleanPhoneNumber(senderNumber))) {
                        Log.d(TAG, "Sending reverse message from " + senderNumber + " to " + forwardNumber);
                        Forwarder.sendSMS(forwardNumber, forwardContent);
                    } else {
                        Log.d(TAG, "Prevented sending message to same number: " + senderNumber);
                    }
                }
            } else {
                // normal message forwarding
                // Check if sender is the same as any target number to prevent loops
                if (PhoneNumberUtils.isSenderInTargetList(senderNumber, targetNumbers)) {
                    Log.d(TAG, "Prevented forwarding message from target number to avoid loop: " + senderNumber);
                    continue;
                }

                // Forward to all configured methods
                if (enableSMS && PhoneNumberUtils.hasValidNumbers(targetNumbers)) {
                    for (String targetNumber : targetNumbers) {
                        // Double-check to prevent forwarding to sender
                        if (!PhoneNumberUtils.cleanPhoneNumber(senderNumber).equals(PhoneNumberUtils.cleanPhoneNumber(targetNumber))) {
                            Log.d(TAG, "Forwarding SMS from " + senderNumber + " to " + targetNumber);
                            Forwarder.forwardViaSMS(senderLabel, rawMessageContent, targetNumber);
                        } else {
                            Log.d(TAG, "Skipped forwarding to same number: " + targetNumber);
                        }
                    }
                }
                
                if (enableTelegram && !targetTelegram.equals("") && !telegramToken.equals(""))
                    Forwarder.forwardViaTelegram(senderLabel, rawMessageContent, targetTelegram, telegramToken);
                if (enableWeb && !targetWeb.equals(""))
                    Forwarder.forwardViaWeb(senderLabel, rawMessageContent, targetWeb);
            }
        }
    }

    private String lookupContactName(Context context, String phoneNumber) {
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
