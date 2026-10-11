package com.enixcoda.smsforward;

import android.content.Context;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pro routing rules: a message goes to the methods of every rule it matches. Stored as JSON in settings. */
final class Rules {
    static final String[] METHODS = {ForwardLog.SMS, ForwardWorker.TELEGRAM, ForwardWorker.WEB, ForwardWorker.EMAIL};

    static final class Rule {
        String name = "";
        String senders = "";  // numbers or sender names, comma-separated; empty = any sender
        String keywords = ""; // words or /regex/, comma-separated; empty = any text
        final Set<String> methods = new HashSet<>();

        boolean matches(String sender, String body) {
            return MessageRules.matchesFilter(sender, body, MessageRules.splitKeywords(keywords), PhoneNumberUtils.splitList(senders));
        }
    }

    private Rules() {
    }

    static List<Rule> load(Context context) {
        List<Rule> rules = new ArrayList<>();
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_rules), "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Rule rule = new Rule();
                rule.name = o.optString("name");
                rule.senders = o.optString("senders");
                rule.keywords = o.optString("keywords");
                JSONArray methods = o.optJSONArray("methods");
                for (int m = 0; methods != null && m < methods.length(); m++) rule.methods.add(methods.getString(m));
                rules.add(rule);
            }
        } catch (JSONException e) {
            // Corrupt or hand-edited import: treat as no rules (forward to every method) rather than drop messages
        }
        return rules;
    }

    static void save(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            try {
                array.put(new JSONObject().put("name", rule.name).put("senders", rule.senders)
                        .put("keywords", rule.keywords).put("methods", new JSONArray(rule.methods)));
            } catch (JSONException e) {
                throw new AssertionError(e); // only strings: can't fail
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.key_rules), array.toString()).apply();
    }

    /** Methods to forward to, or null for every enabled method (no rules, or unmatched messages set to go everywhere). */
    static Set<String> route(Context context, String sender, String body) {
        List<Rule> rules = load(context);
        if (rules.isEmpty()) return null;
        Set<String> methods = new HashSet<>();
        boolean matchedAny = false;
        for (Rule rule : rules) {
            if (!rule.matches(sender, body)) continue;
            matchedAny = true;
            methods.addAll(rule.methods);
        }
        if (matchedAny) return methods;
        String unmatched = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.key_rules_unmatched), "all");
        return "all".equals(unmatched) ? null : methods; // empty: don't forward
    }
}
