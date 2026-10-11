package com.enixcoda.smsforward;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/**
 * Pro forward log: marks a forwarded SMS as sent / delivered from the carrier's per-part reports. Any failed part
 * makes the message failed; it's delivered only when every part is.
 */
public class SmsStatusReceiver extends BroadcastReceiver {
    private static final String ACTION_SENT = "com.enixcoda.smsforward.SMS_SENT";
    private static final String ACTION_DELIVERED = "com.enixcoda.smsforward.SMS_DELIVERED";
    private static final String EXTRA_LOG_ID = "log_id";
    private static final String EXTRA_PARTS = "parts";
    // Delivered-part counts survive the process being killed between reports; not settings (no "key_" prefix)
    private static final String COUNTS = "sms_delivery_counts";
    private static final int MAX_PARTS = 64; // request-code space per message; longer SMS share codes (still correct, just coalesced)

    static PendingIntent sent(Context context, long logId, int part) {
        return pendingIntent(context, new Intent(context, SmsStatusReceiver.class).setAction(ACTION_SENT), logId, part, 0);
    }

    static PendingIntent delivered(Context context, long logId, int part, int parts) {
        return pendingIntent(context, new Intent(context, SmsStatusReceiver.class).setAction(ACTION_DELIVERED).putExtra(EXTRA_PARTS, parts),
                logId, part, 1);
    }

    private static PendingIntent pendingIntent(Context context, Intent intent, long logId, int part, int offset) {
        intent.putExtra(EXTRA_LOG_ID, logId);
        int code = (int) ((logId * MAX_PARTS + part % MAX_PARTS) * 2 + offset);
        // Mutable: Android adds the delivery-report PDU to it. The explicit component means only this receiver gets it.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(context, code, intent, flags);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long logId = intent.getLongExtra(EXTRA_LOG_ID, -1);
        if (ACTION_SENT.equals(intent.getAction())) {
            if (getResultCode() != Activity.RESULT_OK) ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
        } else if (ACTION_DELIVERED.equals(intent.getAction())) {
            byte[] pdu = intent.getByteArrayExtra("pdu");
            SmsMessage report = pdu == null ? null : SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"));
            if (report == null) return;
            // GSM report status: complete, still trying (pending), or a permanent failure (>= STATUS_FAILED)
            if (report.getStatus() >= Telephony.Sms.STATUS_FAILED) {
                ForwardLog.setStatus(context, logId, ForwardLog.FAILED);
                context.getSharedPreferences(COUNTS, Context.MODE_PRIVATE).edit().remove(String.valueOf(logId)).apply();
                return;
            }
            if (report.getStatus() != Telephony.Sms.STATUS_COMPLETE) return;
            SharedPreferences counts = context.getSharedPreferences(COUNTS, Context.MODE_PRIVATE);
            String key = String.valueOf(logId);
            int delivered = counts.getInt(key, 0) + 1;
            if (delivered < intent.getIntExtra(EXTRA_PARTS, 1)) {
                counts.edit().putInt(key, delivered).apply();
            } else {
                counts.edit().remove(key).apply();
                ForwardLog.setStatusUnlessFailed(context, logId, ForwardLog.DELIVERED);
            }
        }
    }
}
