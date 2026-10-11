package com.enixcoda.smsforward;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** Pro: recent forwards with their status; a failed Telegram/Web forward can be retried. Pro-only, so no ad. */
public class LogActivity extends AppCompatActivity {
    private ListView list;
    private View empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        SystemBars.apply(this, findViewById(R.id.header));
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        list.setOnItemClickListener((parent, view, position, id) -> confirmRetry((ForwardLog.Entry) parent.getItemAtPosition(position)));
        findViewById(R.id.clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.log_clear_confirm)
                .setPositiveButton(R.string.log_clear, (dialog, which) -> {
                    ForwardLog.clear(this);
                    load();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    // ponytail: reads ≤100 rows on the main thread; move off-thread if the log cap grows.
    private void load() {
        List<ForwardLog.Entry> entries = ForwardLog.recent(this);
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new ArrayAdapter<ForwardLog.Entry>(this, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                ForwardLog.Entry e = getItem(position);
                ((TextView) row.findViewById(android.R.id.text1)).setText(getString(R.string.log_row_title, statusLabel(e.status),
                        channelLabel(e), DateUtils.formatDateTime(getContext(), e.time, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE)));
                ((TextView) row.findViewById(android.R.id.text2)).setText(getString(R.string.log_row_body, e.sender, e.message));
                return row;
            }
        });
    }

    private String channelLabel(ForwardLog.Entry e) {
        return Forwarder.label(e.channel) + " → " + e.target;
    }

    private String statusLabel(String status) {
        switch (status) {
            case ForwardLog.DELIVERED: return getString(R.string.log_delivered);
            case ForwardLog.SENT: return getString(R.string.log_sent);
            case ForwardLog.QUEUED: return getString(R.string.log_queued);
            default: return getString(R.string.log_failed);
        }
    }

    private void confirmRetry(ForwardLog.Entry e) {
        // SMS can't be confirmed after handing it to the carrier; only failed network forwards are retried.
        if (ForwardLog.SMS.equals(e.channel) || !ForwardLog.FAILED.equals(e.status)) return;
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.log_retry_confirm, channelLabel(e)))
                .setPositiveButton(R.string.log_retry, (dialog, which) -> {
                    ForwardLog.setStatus(this, e.id, ForwardLog.QUEUED);
                    ForwardWorker.enqueue(this, e.channel, e.target, e.sender, e.message, e.text, e.id);
                    load();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
