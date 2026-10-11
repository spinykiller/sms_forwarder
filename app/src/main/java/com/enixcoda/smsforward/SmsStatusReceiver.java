package com.enixcoda.smsforward;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Pro forward log: marks a forwarded SMS as sent / delivered when the carrier reports it. */
public class SmsStatusReceiver extends BroadcastReceiver {
    private static final String ACTION_SENT = "com.enixcoda.smsforward.SMS_SENT";
    private static final String ACTION_DELIVERED = "com.enixcoda.smsforward.SMS_DELIVERED";
    private static final String EXTRA_LOG_ID = "log_id";

    static PendingIntent sent(Context context, long logId) {
        return pendingIntent(context, ACTION_SENT, logId, 0);
    }

    static PendingIntent delivered(Context context, long logId) {
        return pendingIntent(context, ACTION_DELIVERED, logId, 1);
    }

    private static PendingIntent pendingIntent(Context context, String action, long logId, int offset) {
        Intent intent = new Intent(context, SmsStatusReceiver.class).setAction(action).putExtra(EXTRA_LOG_ID, logId);
        // Mutable: Android adds the delivery-report PDU to it. The explicit component means only this receiver gets it.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(context, (int) (logId * 2 + offset), intent, flags);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long logId = intent.getLongExtra(EXTRA_LOG_ID, -1);
        if (ACTION_SENT.equals(intent.getAction())) {
            ForwardLog.setStatus(context, logId, getResultCode() == Activity.RESULT_OK ? ForwardLog.SENT : ForwardLog.FAILED);
        } else if (ACTION_DELIVERED.equals(intent.getAction())) {
            byte[] pdu = intent.getByteArrayExtra("pdu");
            SmsMessage report = pdu == null ? null : SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"));
            if (report != null && report.getStatus() == Telephony.Sms.STATUS_COMPLETE)
                ForwardLog.setStatus(context, logId, ForwardLog.DELIVERED);
        }
    }
}
