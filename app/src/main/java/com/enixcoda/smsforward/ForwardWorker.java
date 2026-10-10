package com.enixcoda.smsforward;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Retries a Telegram/Web forward that failed transiently, once the network is back. */
public class ForwardWorker extends Worker {
    static final String TELEGRAM = "telegram";
    static final String WEB = "web";
    static final String[] CHANNELS = {TELEGRAM, WEB};

    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_FROM = "from";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_TEXT = "text";
    // With exponential backoff from 30s (capped at 5h by WorkManager) this spans roughly a day.
    private static final int MAX_ATTEMPTS = 10;

    public ForwardWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void enqueue(Context context, String channel, String senderLabel, String message, String text) {
        WorkManager.getInstance(context).enqueue(new OneTimeWorkRequest.Builder(ForwardWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder()
                        .putString(KEY_CHANNEL, channel)
                        .putString(KEY_FROM, senderLabel)
                        .putString(KEY_MESSAGE, message)
                        .putString(KEY_TEXT, text)
                        .build())
                .build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Data in = getInputData();
        int result = Forwarder.send(getApplicationContext(), in.getString(KEY_CHANNEL), in.getString(KEY_FROM), in.getString(KEY_MESSAGE), in.getString(KEY_TEXT));
        if (result == Forwarder.RETRY && getRunAttemptCount() < MAX_ATTEMPTS) return Result.retry();
        return result == Forwarder.OK ? Result.success() : Result.failure();
    }
}
