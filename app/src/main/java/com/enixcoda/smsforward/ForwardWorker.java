package com.enixcoda.smsforward;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
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
    static final String[] CHANNELS = {TELEGRAM, WEB};

    // The message lives in a file, not in WorkManager's Data (10 KB limit; a long SMS can exceed it).
    private static final String KEY_PAYLOAD = "payload";
    private static final String KEY_LOG_ID = "log_id";
    // With exponential backoff from 30s (capped at 5h by WorkManager) this spans roughly a day.
    private static final int MAX_ATTEMPTS = 10;

    public ForwardWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** destination: chat ID or URL captured now; logId: the forward-log row to update, or -1 when not logged. */
    static void enqueue(Context context, String channel, String destination, String senderLabel, String message, String text, long logId) {
        // No-backup storage: queued SMS text must not leave the phone through Android backups.
        File dir = new File(context.getNoBackupFilesDir(), "forward_queue");
        File payload = new File(dir, UUID.randomUUID() + ".json");
        try {
            String json = new JSONObject().put("channel", channel).put("destination", destination)
                    .put("from", senderLabel).put("message", message).put("text", text).toString();
            dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(payload)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Couldn't queue forward", e);
            ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
            return;
        }
        OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putString(KEY_PAYLOAD, payload.getPath()).putLong(KEY_LOG_ID, logId).build());
        // Expedited runs right away, even in Doze. Before Android 12 it would need a foreground service, so it's skipped there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        WorkManager.getInstance(context).enqueue(request.build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long logId = getInputData().getLong(KEY_LOG_ID, -1);
        File payload = new File(getInputData().getString(KEY_PAYLOAD));
        int result;
        try (DataInputStream in = new DataInputStream(new FileInputStream(payload))) {
            byte[] bytes = new byte[(int) payload.length()];
            in.readFully(bytes);
            JSONObject p = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            result = Forwarder.send(context, p.getString("channel"), p.getString("destination"),
                    p.getString("from"), p.getString("message"), p.getString("text"));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Unreadable queued forward", e);
            result = Forwarder.FAIL;
        }
        if (result == Forwarder.RETRY && getRunAttemptCount() < MAX_ATTEMPTS) return Result.retry();
        payload.delete();
        ForwardLog.setStatus(context, logId, result == Forwarder.OK ? ForwardLog.DELIVERED : ForwardLog.FAILED);
        return result == Forwarder.OK ? Result.success() : Result.failure();
    }
}
