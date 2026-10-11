package com.enixcoda.smsforward;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    /**
     * Replaces the matching settings. Only settings this version knows, with the type it reads them as, are
     * applied: a wrong type would crash every later read (and forwarding) until the app's data is cleared.
     */
    static void restore(Context context, Uri uri) throws IOException, JSONException {
        JSONObject json = new JSONObject(read(context, uri));
        Map<String, Class<?>> types = settingTypes(context);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        // The backup is the whole configuration: a setting it doesn't have goes back to its default
        for (String key : types.keySet()) {
            if (!json.has(key)) editor.remove(key);
        }
        for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object v = json.get(key);
            Class<?> type = types.get(key);
            if (type == Boolean.class && v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
            else if (type == String.class && v instanceof String) editor.putString(key, (String) v);
            else if (type == Set.class && v instanceof JSONArray) {
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ((JSONArray) v).length(); i++) set.add(((JSONArray) v).getString(i));
                editor.putStringSet(key, set);
            }
        }
        editor.apply();
    }

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Type of every setting, from the settings screen definition plus the ones stored outside it. */
    private static Map<String, Class<?>> settingTypes(Context context) throws IOException {
        Map<String, Class<?>> types = new HashMap<>();
        types.put(context.getString(R.string.key_rules), String.class);    // edited on the rules screen
        types.put(context.getString(R.string.key_send_sim), String.class); // set by the SIM picker
        try (XmlResourceParser xml = context.getResources().getXml(R.xml.root_preferences)) {
            for (int event = xml.getEventType(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                if (event != XmlPullParser.START_TAG) continue;
                int keyRes = xml.getAttributeResourceValue(ANDROID_NS, "key", 0);
                if (keyRes == 0) continue;
                String tag = xml.getName();
                Class<?> type = tag.contains("Switch") || tag.contains("CheckBox") ? Boolean.class
                        : tag.contains("MultiSelectList") ? Set.class
                        : tag.contains("EditText") || tag.contains("ListPreference") ? String.class : null;
                if (type != null) types.put(context.getString(keyRes), type);
            }
        } catch (XmlPullParserException e) {
            throw new IOException(e); // our own resource: can't happen unless the build is broken
        }
        return types;
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
