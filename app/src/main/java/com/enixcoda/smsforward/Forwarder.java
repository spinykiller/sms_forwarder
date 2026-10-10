package com.enixcoda.smsforward;

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

public class Forwarder {
    private static final String TAG = "Forwarder";

    // Result of a network forward.
    static final int OK = 0;
    static final int RETRY = 1; // transient (offline, timeout, 408/429/5xx): queue and try again
    static final int FAIL = 2;  // disabled, misconfigured or rejected (4xx): retrying won't help

    /** Sends on the given SIM (the one that received the message), or the default SIM if unknown. */
    public static void sendSMS(int subId, String number, String content) {
        SmsManager smsManager = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ? SmsManager.getDefault()
                : SmsManager.getSmsManagerForSubscriptionId(subId);
        // Android splits long text into a linked multipart SMS; a single part is sent as a normal SMS.
        smsManager.sendMultipartTextMessage(number, null, smsManager.divideMessage(content), null, null);
    }

    public static void forwardViaSMS(int subId, String text, String forwardNumber) {
        try {
            sendSMS(subId, forwardNumber, text);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error forwarding SMS: " + e, e);
        }
    }

    /**
     * Forwards via Telegram (sends the formatted text) or Web (posts sender and raw message as JSON) per the
     * current settings. Blocking: call off the main thread.
     */
    static int send(Context context, String channel, String senderLabel, String message, String text) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (ForwardWorker.TELEGRAM.equals(channel)) {
            String chatId = prefs.getString(context.getString(R.string.key_target_telegram), "").trim();
            String token = prefs.getString(context.getString(R.string.key_telegram_apikey), "").trim();
            if (!prefs.getBoolean(context.getString(R.string.key_enable_telegram), false) || chatId.isEmpty() || token.isEmpty())
                return FAIL;
            return post("https://api.telegram.org/bot" + token + "/sendMessage",
                    "application/x-www-form-urlencoded",
                    "chat_id=" + encode(chatId) + "&text=" + encode(text));
        }
        String endpoint = prefs.getString(context.getString(R.string.key_target_web), "").trim();
        if (!prefs.getBoolean(context.getString(R.string.key_enable_web), false) || endpoint.isEmpty())
            return FAIL;
        try {
            JSONObject body = new JSONObject().put("from", senderLabel).put("message", message);
            return post(endpoint, "application/json; charset=utf-8", body.toString());
        } catch (JSONException e) {
            return FAIL;
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
