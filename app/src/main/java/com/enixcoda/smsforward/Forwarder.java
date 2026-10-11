package com.enixcoda.smsforward;

import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Forwarder {
    private static final String TAG = "Forwarder";

    // Result of a network forward.
    static final int OK = 0;
    static final int RETRY = 1; // transient (offline, timeout, 408/429/5xx): queue and try again
    static final int FAIL = 2;  // disabled, misconfigured or rejected (4xx): retrying won't help

    /**
     * Sends on the given SIM (the one that received the message), or the default SIM if unknown.
     * logId: forward-log row that gets sent/delivered reports, or -1.
     */
    public static void sendSMS(Context context, int subId, String number, String content, long logId) {
        SmsManager smsManager = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ? SmsManager.getDefault()
                : SmsManager.getSmsManagerForSubscriptionId(subId);
        // Android splits long text into a linked multipart SMS; a single part is sent as a normal SMS.
        ArrayList<String> parts = smsManager.divideMessage(content);
        ArrayList<PendingIntent> sent = null;
        ArrayList<PendingIntent> delivered = null;
        if (logId >= 0) {
            // Reports for the last part stand for the whole message
            sent = new ArrayList<>(Collections.nCopies(parts.size(), (PendingIntent) null));
            delivered = new ArrayList<>(Collections.nCopies(parts.size(), (PendingIntent) null));
            sent.set(parts.size() - 1, SmsStatusReceiver.sent(context, logId));
            delivered.set(parts.size() - 1, SmsStatusReceiver.delivered(context, logId));
        }
        smsManager.sendMultipartTextMessage(number, null, parts, sent, delivered);
    }

    /** False if the SMS couldn't be handed to the carrier. */
    public static boolean forwardViaSMS(Context context, int subId, String text, String forwardNumber, long logId) {
        try {
            sendSMS(context, subId, forwardNumber, text, logId);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Error forwarding SMS: " + e, e);
            return false;
        }
    }

    /** Display name of a method. */
    static String label(String channel) {
        return ForwardLog.SMS.equals(channel) ? "SMS" : ForwardWorker.TELEGRAM.equals(channel) ? "Telegram"
                : ForwardWorker.WEB.equals(channel) ? "Web" : "Email";
    }

    /**
     * The configured Telegram chat ID, webhook URL or email address(es), or "" when the method is off or not set up.
     * Free users email one address.
     */
    static String destination(Context context, String channel) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int enableKey = ForwardWorker.TELEGRAM.equals(channel) ? R.string.key_enable_telegram
                : ForwardWorker.WEB.equals(channel) ? R.string.key_enable_web : R.string.key_enable_email;
        int targetKey = ForwardWorker.TELEGRAM.equals(channel) ? R.string.key_target_telegram
                : ForwardWorker.WEB.equals(channel) ? R.string.key_target_web : R.string.key_email_to;
        if (!prefs.getBoolean(context.getString(enableKey), false)) return "";
        String target = prefs.getString(context.getString(targetKey), "").trim();
        if (ForwardWorker.EMAIL.equals(channel) && !Billing.isPro(context)) {
            List<String> addresses = PhoneNumberUtils.splitList(target);
            return addresses.isEmpty() ? "" : addresses.get(0);
        }
        return target;
    }

    /**
     * Forwards to a destination captured when the SMS arrived (so a later settings change can't redirect it):
     * Telegram sends the formatted text, Web posts sender and raw message as JSON. Fails if the method has
     * since been turned off. Blocking: call off the main thread.
     */
    static int send(Context context, String channel, String destination, String senderLabel, String message, String text) {
        if (destination(context, channel).isEmpty()) return FAIL;
        if (ForwardWorker.EMAIL.equals(channel)) {
            return Mailer.send(context, destination, context.getString(R.string.email_subject, senderLabel), text);
        }
        if (ForwardWorker.TELEGRAM.equals(channel)) {
            String token = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(context.getString(R.string.key_telegram_apikey), "").trim();
            if (token.isEmpty()) return FAIL;
            return post("https://api.telegram.org/bot" + token + "/sendMessage",
                    "application/x-www-form-urlencoded",
                    "chat_id=" + encode(destination) + "&text=" + encode(text));
        }
        try {
            return post(destination, "application/json; charset=utf-8", webBody(context, senderLabel, message, text).toString());
        } catch (JSONException e) {
            return FAIL;
        }
    }

    // Discord rejects longer content
    private static final int DISCORD_MAX_CHARS = 2000;

    /** JSON per the chosen webhook format: plain {from, message}, or Slack/Teams {text} / Discord {content} with the formatted text. */
    private static JSONObject webBody(Context context, String senderLabel, String message, String text) throws JSONException {
        String format = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_web_format), "json");
        switch (format) {
            case "slack":
            case "teams":
                return new JSONObject().put("text", text);
            case "discord":
                return new JSONObject().put("content", text.length() > DISCORD_MAX_CHARS ? text.substring(0, DISCORD_MAX_CHARS - 1) + "…" : text);
            default:
                return new JSONObject().put("from", senderLabel).put("message", message);
        }
    }

    private static int post(String url, String contentType, String body) {
        // Android blocks cleartext http; it would fail on every retry.
        if (!url.regionMatches(true, 0, "https://", 0, 8)) return FAIL;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            Log.d(TAG, "Forward response: " + code);
            if (code >= 200 && code < 300) return OK;
            return code == 408 || code == 429 || code >= 500 ? RETRY : FAIL;
        } catch (MalformedURLException | IllegalArgumentException | ClassCastException e) {
            // Not a valid http(s) URL
            Log.e(TAG, "Invalid forward URL", e);
            return FAIL;
        } catch (IOException e) {
            Log.w(TAG, "Forward failed, will retry: " + e);
            return RETRY;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e); // UTF-8 is always supported
        }
    }
}
