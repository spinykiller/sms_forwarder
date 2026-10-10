package com.enixcoda.smsforward;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    // Battery optimization is explained once; afterwards it's under System Settings.
    private static final String KEY_BATTERY_PROMPTED = "battery_prompted";

    private TextView statusText;
    private View adSlot;
    private ViewGroup adContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBars.apply(this, findViewById(R.id.header));
        statusText = findViewById(R.id.status_text);
        statusText.setOnClickListener(v -> {
            if (!hasSmsPermissions(this)) requestPermissions();
        });
        adSlot = findViewById(R.id.ad_slot);
        adContainer = findViewById(R.id.ad_container);
        findViewById(R.id.remove_ads).setOnClickListener(v -> startActivity(new Intent(this, ProActivity.class)));

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
            if (hasSmsPermissions(this)) maybePromptBatteryOptimization();
            else showPermissionExplanationDialog();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Billing.get(this).refresh();
        Ads.onStart(this, this::bindAd);
    }

    @Override
    protected void onDestroy() {
        Ads.destroyBanner(adContainer);
        super.onDestroy();
    }

    private void bindAd() {
        Ads.bindBanner(this, adSlot, adContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateStatus();
        bindAd(); // Pro status is cached in prefs
    }

    static boolean hasSmsPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        if (!hasSmsPermissions(this)) {
            statusText.setText(R.string.status_permission_missing);
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        List<String> methods = new ArrayList<>();
        addMethod(methods, prefs, R.string.key_enable_sms, "SMS", R.string.key_target_sms);
        addMethod(methods, prefs, R.string.key_enable_telegram, "Telegram", R.string.key_target_telegram, R.string.key_telegram_apikey);
        addMethod(methods, prefs, R.string.key_enable_web, "Web", R.string.key_target_web);
        statusText.setText(methods.isEmpty()
                ? getString(R.string.status_off)
                : getString(R.string.status_forwarding, String.join(", ", methods)));
    }

    private void addMethod(List<String> methods, SharedPreferences prefs, int enableKey, String name, int... requiredKeys) {
        if (!prefs.getBoolean(getString(enableKey), false)) return;
        for (int key : requiredKeys) {
            if (prefs.getString(getString(key), "").trim().isEmpty()) {
                methods.add(getString(R.string.status_not_set_up, name));
                return;
            }
        }
        methods.add(name);
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        }, PERMISSION_REQUEST_CODE);
    }

    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_message)
            .setPositiveButton(R.string.continue_, (dialog, which) -> requestPermissions())
            .setNegativeButton(R.string.not_now, null)
            .show();
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) return;
        updateStatus();
        if (hasSmsPermissions(this)) {
            maybePromptBatteryOptimization();
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECEIVE_SMS)) {
            // Denied with "don't ask again": the system dialog won't show anymore, only Settings can grant it.
            new AlertDialog.Builder(this)
                .setTitle(R.string.permission_denied_title)
                .setMessage(R.string.permission_denied_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> openAppSettings())
                .setNegativeButton(R.string.not_now, null)
                .show();
        }
    }

    private void maybePromptBatteryOptimization() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!BatteryOptimizationHelper.isBatteryOptimized(this) || prefs.getBoolean(KEY_BATTERY_PROMPTED, false)) return;
        prefs.edit().putBoolean(KEY_BATTERY_PROMPTED, true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Battery Optimization Detected")
            .setMessage(BatteryOptimizationHelper.getBatteryOptimizationInstructions(this))
            .setPositiveButton(R.string.open_settings, (dialog, which) -> BatteryOptimizationHelper.openBatteryOptimizationSettings(this))
            .setNegativeButton(R.string.later, null)
            .show();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Every target number must contain digits (entries without any would be silently dropped)
            bindText(R.string.key_target_sms, R.string.target_summary_sms, InputType.TYPE_CLASS_PHONE, false,
                    v -> PhoneNumberUtils.splitList(v).size() == PhoneNumberUtils.parsePhoneNumbers(v).size(), R.string.invalid_numbers);
            bindText(R.string.key_target_telegram, R.string.target_summary_telegram, InputType.TYPE_CLASS_TEXT, false,
                    v -> v.matches("-?\\d+|@\\w+"), R.string.invalid_chat_id);
            bindText(R.string.key_telegram_apikey, R.string.summary_telegram_apikey,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, true,
                    v -> v.matches("\\d+:[\\w-]+"), R.string.invalid_token);
            bindText(R.string.key_target_web, R.string.target_summary_web,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false,
                    v -> "https".equalsIgnoreCase(Uri.parse(v).getScheme()) && Uri.parse(v).getHost() != null, R.string.invalid_url);
            bindText(R.string.key_blocked_numbers, R.string.summary_blocked_numbers, InputType.TYPE_CLASS_TEXT, false,
                    v -> true, 0);
            bindText(R.string.key_filter_keywords, R.string.summary_filter_keywords, InputType.TYPE_CLASS_TEXT, false,
                    v -> true, 0);
            bindText(R.string.key_filter_senders, R.string.summary_filter_senders, InputType.TYPE_CLASS_TEXT, false,
                    v -> true, 0);
            bindText(R.string.key_template, R.string.summary_template,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, false,
                    v -> v.contains("{message}"), R.string.invalid_template);

            findPreference(getString(R.string.key_forward_log)).setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), LogActivity.class));
                return true;
            });
            findPreference(getString(R.string.key_pro_unlock)).setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), ProActivity.class));
                return true;
            });

            findPreference(getString(R.string.key_pro)).setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), ProActivity.class));
                return true;
            });
            findPreference(getString(R.string.key_privacy_choices)).setOnPreferenceClickListener(preference -> {
                MainActivity activity = (MainActivity) requireActivity();
                Ads.showPrivacyOptions(activity, activity::bindAd);
                return true;
            });
            findPreference(getString(R.string.key_send_test)).setOnPreferenceClickListener(preference -> {
                sendTest();
                return true;
            });
            findPreference(getString(R.string.key_permission_status)).setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null)));
                return true;
            });
            findPreference(getString(R.string.key_battery_optimization)).setOnPreferenceClickListener(preference -> {
                BatteryOptimizationHelper.openBatteryOptimizationSettings(requireContext());
                return true;
            });
            findPreference(getString(R.string.key_autostart_settings)).setOnPreferenceClickListener(preference -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.title_autostart_settings)
                    .setMessage(AutoStartHelper.getAutoStartInstructions(requireContext()))
                    .setPositiveButton(R.string.open_settings, (dialog, which) -> AutoStartHelper.requestAutoStartPermission(requireActivity()))
                    .setNegativeButton(R.string.later, null)
                    .show();
                return true;
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            // Permissions and battery settings change outside the app; refresh on return.
            Context context = requireContext();
            boolean contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            findPreference(getString(R.string.key_permission_status)).setSummary(!hasSmsPermissions(context)
                    ? R.string.permissions_sms_missing
                    : contacts ? R.string.permissions_ok : R.string.permissions_contacts_missing);
            findPreference(getString(R.string.key_battery_optimization)).setSummary(
                    BatteryOptimizationHelper.isBatteryOptimized(context) ? R.string.battery_optimized : R.string.battery_ok);
            boolean pro = Billing.isPro(context);
            findPreference(getString(R.string.key_pro)).setSummary(pro ? R.string.pro_active : R.string.summary_pro);
            findPreference(getString(R.string.key_pro_unlock)).setVisible(!pro);
            for (int key : new int[]{R.string.key_forward_log, R.string.key_enable_filter, R.string.key_filter_keywords, R.string.key_filter_senders, R.string.key_template}) {
                findPreference(getString(key)).setEnabled(pro);
            }
            findPreference(getString(R.string.key_privacy_choices)).setVisible(Ads.privacyOptionsRequired(context));
        }

        /** Shows the current value as the summary (help text when empty), sets the keyboard and rejects invalid input. */
        private void bindText(int keyRes, int helpRes, int inputType, boolean secret, Predicate<String> isValid, int errorRes) {
            EditTextPreference preference = findPreference(getString(keyRes));
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(inputType);
                editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
            });
            preference.setSummaryProvider(p -> {
                String value = ((EditTextPreference) p).getText();
                if (value == null || value.trim().isEmpty()) return getString(helpRes);
                return secret ? "••••" + value.substring(Math.max(0, value.length() - 4)) : value;
            });
            preference.setOnPreferenceChangeListener((p, newValue) -> {
                String value = ((String) newValue).trim();
                if (value.isEmpty() || isValid.test(value)) return true;
                Toast.makeText(requireContext(), errorRes, Toast.LENGTH_LONG).show();
                return false;
            });
        }

        private void sendTest() {
            Context context = requireContext().getApplicationContext();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean sms = prefs.getBoolean(getString(R.string.key_enable_sms), false);
            boolean telegram = prefs.getBoolean(getString(R.string.key_enable_telegram), false);
            boolean web = prefs.getBoolean(getString(R.string.key_enable_web), false);
            if (!sms && !telegram && !web) {
                Toast.makeText(context, R.string.test_none_enabled, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, R.string.test_sending, Toast.LENGTH_SHORT).show();
            String sender = getString(R.string.test_sender);
            String message = getString(R.string.test_message);
            new Thread(() -> {
                List<String> results = new ArrayList<>();
                if (sms) {
                    List<String> targets = PhoneNumberUtils.parsePhoneNumbers(prefs.getString(context.getString(R.string.key_target_sms), ""));
                    for (String target : targets) {
                        Forwarder.forwardViaSMS(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                                MessageRules.format(MessageRules.DEFAULT_SMS_FORMAT, sender, sender, "", message), target);
                    }
                    results.add(targets.isEmpty()
                            ? context.getString(R.string.test_sms_no_numbers)
                            : context.getString(R.string.test_sms_sent, targets.size()));
                }
                String telegramText = MessageRules.format(MessageRules.DEFAULT_TELEGRAM_FORMAT, sender, sender, "", message);
                if (telegram) results.add(describe(context, "Telegram", Forwarder.send(context, ForwardWorker.TELEGRAM, sender, message, telegramText)));
                if (web) results.add(describe(context, "Web", Forwarder.send(context, ForwardWorker.WEB, sender, message, telegramText)));
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.test_result_title)
                        .setMessage(String.join("\n", results))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                });
            }).start();
        }

        private static String describe(Context context, String name, int result) {
            int res = result == Forwarder.OK ? R.string.test_ok : result == Forwarder.RETRY ? R.string.test_retry : R.string.test_fail;
            return context.getString(res, name);
        }
    }
}
