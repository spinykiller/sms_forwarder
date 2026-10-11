package com.enixcoda.smsforward;

import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Sends a Telegram/Web forward off the SMS broadcast, retrying transient failures once the network is back. */
public class ForwardWorker extends Worker {
    private static final String TAG = "ForwardWorker";
    static final String TELEGRAM = "telegram";
    static final String WEB = "web";
    static final String EMAIL = "email";
    static final String[] CHANNELS = {TELEGRAM, WEB, EMAIL};

    // The message lives in a file, not in WorkManager's Data (10 KB limit; a long SMS can exceed it).
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_LOG_ID = "log_id";
    private static final String KEY_SMS = "sms";
    // While forwarding is paused, queued forwards wait and check again this often (fresh retry budget each time)
    private static final long PAUSED_RECHECK_MS = TimeUnit.MINUTES.toMillis(15);
    // With exponential backoff from 30s (capped at 5h by WorkManager) this spans roughly a day.
    private static final int MAX_ATTEMPTS = 10;

    public ForwardWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * destination: phone number, chat ID, URL or email captured now; logId: the forward-log row to update, or -1 when
     * not logged; subId: SIM for SMS; delayMs: hold it (quiet hours), 0 = now.
     * Returns the enqueue operation (await it before a broadcast finishes), or null if it couldn't be queued.
     */
    static Operation enqueue(Context context, String channel, String destination, String senderLabel, String message, String text,
                             long logId, int subId, long delayMs) {
        // No-backup storage: queued SMS text must not leave the phone through Android backups.
        File dir = new File(context.getNoBackupFilesDir(), "forward_queue");
        File payload = new File(dir, UUID.randomUUID() + ".json");
        try {
            String json = new JSONObject().put("channel", channel).put("destination", destination)
                    .put("from", senderLabel).put("message", message).put("text", text).put("subId", subId).toString();
            dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(payload)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Couldn't queue forward", e);
            ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
            return null;
        }
        return schedule(context, payload.getPath(), logId, ForwardLog.SMS.equals(channel), delayMs);
    }

    private static Operation schedule(Context context, String payloadPath, long logId, boolean sms, long delayMs) {
        OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(sms ? NetworkType.NOT_REQUIRED : NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putString(KEY_PAYLOAD, payloadPath).putLong(KEY_LOG_ID, logId)
                        .putBoolean(KEY_SMS, sms).build());
        if (delayMs > 0) request.setInitialDelay(delayMs, TimeUnit.MILLISECONDS);
        // Expedited runs right away, even in Doze. Before Android 12 it would need a foreground service, so it's skipped there.
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        return WorkManager.getInstance(context).enqueue(request.build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long logId = getInputData().getLong(KEY_LOG_ID, -1);
        String payloadPath = getInputData().getString(KEY_PAYLOAD);
        if (Dispatcher.isPaused(context)) {
            // "Nothing is forwarded while paused": hold it, and send once forwarding resumes
            schedule(context, payloadPath, logId, getInputData().getBoolean(KEY_SMS, false), PAUSED_RECHECK_MS);
            return Result.success();
        }
        File payload = new File(payloadPath);
        int result;
        boolean sms = false;
        try (DataInputStream in = new DataInputStream(new FileInputStream(payload))) {
            byte[] bytes = new byte[(int) payload.length()];
            in.readFully(bytes);
            JSONObject p = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            sms = ForwardLog.SMS.equals(p.getString("channel"));
            result = sms
                    ? (Forwarder.forwardViaSMS(context, p.optInt("subId", SubscriptionManager.INVALID_SUBSCRIPTION_ID),
                            p.getString("text"), p.getString("destination"), logId) ? Forwarder.OK : Forwarder.FAIL)
                    : Forwarder.send(context, p.getString("channel"), p.getString("destination"),
                            p.getString("from"), p.getString("message"), p.getString("text"));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Unreadable queued forward", e);
            result = Forwarder.FAIL;
        }
        if (result == Forwarder.RETRY && getRunAttemptCount() < MAX_ATTEMPTS) return Result.retry();
        payload.delete();
        // A sent SMS gets its status from the carrier's sent/delivered reports instead
        if (result != Forwarder.OK || !sms)
            ForwardLog.setStatus(context, logId, result == Forwarder.OK ? ForwardLog.DELIVERED : ForwardLog.FAILED);
        return result == Forwarder.OK ? Result.success() : Result.failure();
    }
}
