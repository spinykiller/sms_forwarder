package com.enixcoda.smsforward;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Export/import of the user's settings as JSON. Only "key_*" settings: never Pro status or one-time prompts. */
final class SettingsBackup {
    private static final String PREFIX = "key_";
    private static final int MAX_BYTES = 1_000_000; // a settings file is a few KB; refuse anything absurd

    private SettingsBackup() {
    }

    static void export(Context context, Uri uri) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ?> e : PreferenceManager.getDefaultSharedPreferences(context).getAll().entrySet()) {
            if (!e.getKey().startsWith(PREFIX)) continue;
            Object v = e.getValue();
            json.put(e.getKey(), v instanceof Set ? new JSONArray((Set<?>) v) : v);
        }
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Can't write the file");
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Replaces the matching settings; unknown types and non-settings keys in the file are ignored. */
    static void restore(Context context, Uri uri) throws IOException, JSONException {
        JSONObject json = new JSONObject(read(context, uri));
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!key.startsWith(PREFIX)) continue;
            Object v = json.get(key);
            if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
            else if (v instanceof String) editor.putString(key, (String) v);
            else if (v instanceof JSONArray) {
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ((JSONArray) v).length(); i++) set.add(((JSONArray) v).getString(i));
                editor.putStringSet(key, set);
            }
        }
        editor.apply();
    }

    private static String read(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Can't read the file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("File too large");
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
