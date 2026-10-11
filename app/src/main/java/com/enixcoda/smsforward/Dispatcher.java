package com.enixcoda.smsforward;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.work.Operation;

import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Forwards one incoming message to every configured destination, applying the user's filters and format. */
final class Dispatcher {
    private static final String TAG = "Dispatcher";
    private static final int REMEMBERED_TEXTS = 50;
    // Recently forwarded texts, so a forward that comes back as a notification isn't forwarded again
    private static final Map<String, Boolean> recentTexts = Collections.synchronizedMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > REMEMBERED_TEXTS;
        }
    });

    private Dispatcher() {
    }

    /** True if the text is (part of) something we forwarded recently; short texts only on an exact match. */
    static boolean wasRecentlySent(String text) {
        String t = text.trim();
        synchronized (recentTexts) {
            for (String sent : recentTexts.keySet()) {
                if (sent.equals(t) || (t.length() >= 10 && sent.contains(t))) return true;
            }
        }
        return false;
    }

    static boolean isPaused(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.key_paused), false);
    }

    /**
     * Blocking (contact lookup, waiting for the work queue): call off the main thread.
     * @param sender phone number or sender ID
     * @param name   contact name, or "" if unknown
     * @param subId  SIM that received it, or SubscriptionManager.INVALID_SUBSCRIPTION_ID
     */
    static void dispatch(Context context, String sender, String name, String body, int subId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (isPaused(context)) return;

        // Blocklist; entries stay raw so alphanumeric sender IDs (e.g. "HDFCBK") can be blocked
        if (prefs.getBoolean(context.getString(R.string.key_enable_blocklist), false)
                && PhoneNumberUtils.isSenderBlocked(sender, PhoneNumberUtils.splitList(prefs.getString(context.getString(R.string.key_blocked_numbers), "")))) {
            Log.d(TAG, "Message from blocked sender will not be forwarded");
            return;
        }

        // Pro: forward only matching messages, custom format. Ignored if Pro lapses (forward everything, default format).
        boolean pro = Billing.isPro(context);
        if (pro && prefs.getBoolean(context.getString(R.string.key_enable_filter), false)
                && !MessageRules.matchesFilter(sender, body,
                        MessageRules.splitKeywords(prefs.getString(context.getString(R.string.key_filter_keywords), "")),
                        PhoneNumberUtils.splitList(prefs.getString(context.getString(R.string.key_filter_senders), "")))) {
            Log.d(TAG, "Message doesn't match the filter, not forwarded");
            return;
        }

        if (pro && prefs.getBoolean(context.getString(R.string.key_otp_only), false) && !MessageRules.isOtp(body)) {
            Log.d(TAG, "Not a verification code, not forwarded");
            return;
        }
        // Pro routing rules pick the methods; null = every enabled method
        Set<String> routes = pro ? Rules.route(context, sender, body) : null;

        String template = pro ? prefs.getString(context.getString(R.string.key_template), "").trim() : "";
        String senderLabel = (name.isEmpty() ? "" : name + " ") + "(" + sender + ")";
        String smsText = MessageRules.format(template.isEmpty() ? MessageRules.DEFAULT_SMS_FORMAT : template, senderLabel, sender, name, body);
        String text = MessageRules.format(template.isEmpty() ? MessageRules.DEFAULT_TELEGRAM_FORMAT : template, senderLabel, sender, name, body);
        recentTexts.put(smsText.trim(), true);
        recentTexts.put(text.trim(), true);

        // Pro quiet hours: hold everything until they end
        Calendar now = Calendar.getInstance();
        long delayMs = pro && prefs.getBoolean(context.getString(R.string.key_quiet_hours), false)
                ? MessageRules.quietDelayMs(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                        prefs.getString(context.getString(R.string.key_quiet_start), ""), prefs.getString(context.getString(R.string.key_quiet_end), ""))
                : 0;

        if (prefs.getBoolean(context.getString(R.string.key_enable_sms), false) && (routes == null || routes.contains(ForwardLog.SMS))) {
            int smsSubId = pro ? chosenSim(context, subId) : subId;
            if (pro && prefs.getBoolean(context.getString(R.string.key_pause_roaming), false) && isRoaming(context, smsSubId)) {
                Log.d(TAG, "Roaming: SMS forwarding paused");
            } else {
                List<String> targets = PhoneNumberUtils.parsePhoneNumbers(prefs.getString(context.getString(R.string.key_target_sms), ""));
                // Free forwards to the first number
                for (String target : forwardTargets(pro, targets)) {
                    long logId = ForwardLog.add(context, ForwardLog.SMS, target, senderLabel, body, smsText, delayMs > 0 ? ForwardLog.QUEUED : ForwardLog.SENT);
                    if (delayMs > 0) {
                        await(context, ForwardWorker.enqueue(context, ForwardLog.SMS, target, senderLabel, body, smsText, logId, smsSubId, delayMs), logId);
                    } else if (!Forwarder.forwardViaSMS(context, smsSubId, smsText, target, logId)) {
                        ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
                    }
                }
            }
        }
        // Network sends run in a worker: the broadcast has a time limit, and the worker retries when offline.
        for (String channel : ForwardWorker.CHANNELS) {
            String destination = Forwarder.destination(context, channel);
            if (destination.isEmpty() || (routes != null && !routes.contains(channel))) continue;
            long logId = ForwardLog.add(context, channel, destination, senderLabel, body, text, ForwardLog.QUEUED);
            await(context, ForwardWorker.enqueue(context, channel, destination, senderLabel, body, text, logId,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID, delayMs), logId);
        }
    }

    // WorkManager persists asynchronously; finishing the broadcast first could let Android kill the process and drop it.
    private static void await(Context context, Operation queued, long logId) {
        try {
            if (queued != null) queued.getResult().get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Couldn't queue forward", e);
            ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
        }
    }

    /** Pro "Send from" SIM: the receiving SIM unless the user picked one. */
    private static int chosenSim(Context context, int receivedOn) {
        String choice = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_send_sim), "same");
        try {
            return "same".equals(choice) ? receivedOn : Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            return receivedOn;
        }
    }

    private static boolean isRoaming(Context context, int subId) {
        TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) telephony = telephony.createForSubscriptionId(subId);
        return telephony.isNetworkRoaming();
    }

    /** Free forwards to the first number only; Pro to all. */
    static List<String> forwardTargets(boolean pro, List<String> targetNumbers) {
        return pro ? targetNumbers : targetNumbers.subList(0, Math.min(1, targetNumbers.size()));
    }
}
