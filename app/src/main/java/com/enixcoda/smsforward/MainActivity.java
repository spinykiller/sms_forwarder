package com.enixcoda.smsforward;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int PERMISSION_REQUEST_CODE = 100;
    // Battery optimization is explained once; afterwards it's under System Settings.
    private static final String KEY_BATTERY_PROMPTED = "battery_prompted";

    // App lock: unlocked for the rest of the process once authenticated, until the app has been in the background a minute
    private static final long RELOCK_AFTER_MS = 60_000;
    private static long unlockedUntil = 0; // SystemClock.elapsedRealtime(); 0 = locked
    private boolean unlocking;

    private TextView statusText;
    private View adSlot;
    private ViewGroup adContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before super: the chosen theme applies to every screen for the rest of the process.
        applyTheme(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_theme), "system"));
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
        HealthWorker.schedule(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && unlockedUntil == Long.MAX_VALUE)
            unlockedUntil = SystemClock.elapsedRealtime() + RELOCK_AFTER_MS;
    }

    static boolean canLock(Context context) {
        return BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private static final int LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    /** Hides the settings (they hold tokens and the log holds SMS text) until the user authenticates. */
    private void lockIfNeeded() {
        boolean enabled = Billing.isPro(this)
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_app_lock), false);
        // No screen lock any more (removed after enabling): don't lock the user out of their own settings
        if (!enabled || !canLock(this) || SystemClock.elapsedRealtime() <= unlockedUntil) return;
        View content = findViewById(android.R.id.content);
        content.setVisibility(View.INVISIBLE);
        if (unlocking) return;
        unlocking = true;
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                unlocking = false;
                unlockedUntil = Long.MAX_VALUE;
                content.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                unlocking = false;
                finish(); // cancelled or locked out: leave rather than show the settings
            }
        }).authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .build());
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
        lockIfNeeded();
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
        if (getString(R.string.key_paused).equals(key)) Pause.refresh(this);
        if (getString(R.string.key_heartbeat).equals(key) || getString(R.string.key_low_battery).equals(key) || Billing.KEY_PRO.equals(key))
            HealthWorker.schedule(this);
        if (Billing.KEY_PRO.equals(key)) {
            SettingsFragment settings = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.settings);
            if (settings != null && settings.isResumed()) settings.refresh();
        }
    }

    static void applyTheme(String value) {
        AppCompatDelegate.setDefaultNightMode("light".equals(value) ? AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(value) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
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
        if (Dispatcher.isPaused(this)) {
            statusText.setText(R.string.status_paused);
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        List<String> methods = new ArrayList<>();
        addMethod(methods, prefs, R.string.key_enable_sms, "SMS", R.string.key_target_sms);
        addMethod(methods, prefs, R.string.key_enable_telegram, "Telegram", R.string.key_target_telegram, R.string.key_telegram_apikey);
        addMethod(methods, prefs, R.string.key_enable_web, "Web", R.string.key_target_web);
        addMethod(methods, prefs, R.string.key_enable_email, "Email", R.string.key_email_to,
                R.string.key_smtp_host, R.string.key_smtp_port, R.string.key_smtp_user, R.string.key_smtp_password);
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
        private final ActivityResultLauncher<String> phonePermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) pickSim();
                    else Toast.makeText(requireContext(), R.string.sim_needs_permission, Toast.LENGTH_LONG).show();
                });
        private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), uri -> backup(uri, true));
        private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> backup(uri, false));

        private void backup(Uri uri, boolean export) {
            if (uri == null) return; // picker cancelled
            Context context = requireContext();
            try {
                if (export) SettingsBackup.export(context, uri);
                else SettingsBackup.restore(context, uri);
                Toast.makeText(context, export ? R.string.export_done : R.string.import_done, Toast.LENGTH_SHORT).show();
                if (!export) requireActivity().recreate(); // show the imported values
            } catch (IOException | JSONException | SecurityException e) {
                Toast.makeText(context, getString(R.string.backup_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Every target number must contain digits (entries without any would be silently dropped)
            bindText(R.string.key_target_sms, R.string.target_summary_sms, InputType.TYPE_CLASS_PHONE, false,
                    v -> PhoneNumberUtils.splitList(v).size() == PhoneNumberUtils.parsePhoneNumbers(v).size(), R.string.invalid_numbers);
            limitForFree(R.string.key_target_sms, v -> PhoneNumberUtils.parsePhoneNumbers(v).size() <= 1, R.string.free_one_number);
            bindText(R.string.key_target_telegram, R.string.target_summary_telegram, InputType.TYPE_CLASS_TEXT, false,
                    v -> v.matches("-?\\d+|@\\w+"), R.string.invalid_chat_id);
            bindText(R.string.key_telegram_apikey, R.string.summary_telegram_apikey,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, true,
                    v -> v.matches("\\d+:[\\w-]+"), R.string.invalid_token);
            bindText(R.string.key_target_web, R.string.target_summary_web,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false,
                    v -> "https".equalsIgnoreCase(Uri.parse(v).getScheme()) && Uri.parse(v).getHost() != null, R.string.invalid_url);
            bindText(R.string.key_email_to, R.string.summary_email_to,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false,
                    v -> allEmails(v), R.string.invalid_email);
            limitForFree(R.string.key_email_to, v -> PhoneNumberUtils.splitList(v).size() <= 1, R.string.free_one_email);
            bindText(R.string.key_smtp_user, R.string.summary_smtp_user,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false,
                    v -> allEmails(v) && PhoneNumberUtils.splitList(v).size() == 1, R.string.invalid_email);
            bindText(R.string.key_smtp_password, R.string.summary_smtp_password,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true, v -> true, 0);
            ((EditTextPreference) findPreference(getString(R.string.key_smtp_port))).setOnBindEditTextListener(e -> e.setInputType(InputType.TYPE_CLASS_NUMBER));
            findPreference(getString(R.string.key_smtp_port)).setOnPreferenceChangeListener((p, v) -> {
                if (((String) v).matches("\\d{1,5}")) return true;
                Toast.makeText(requireContext(), R.string.invalid_port, Toast.LENGTH_LONG).show();
                return false;
            });
            bindText(R.string.key_blocked_numbers, R.string.summary_blocked_numbers, InputType.TYPE_CLASS_TEXT, false,
                    v -> true, 0);
            bindText(R.string.key_filter_keywords, R.string.summary_filter_keywords, InputType.TYPE_CLASS_TEXT, false,
                    v -> MessageRules.invalidRegex(MessageRules.splitKeywords(v)) == null, R.string.invalid_regex_generic);
            bindText(R.string.key_filter_senders, R.string.summary_filter_senders, InputType.TYPE_CLASS_TEXT, false,
                    v -> true, 0);
            bindText(R.string.key_template, R.string.summary_template,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, false,
                    v -> v.contains("{message}"), R.string.invalid_template);

            for (int key : new int[]{R.string.key_quiet_start, R.string.key_quiet_end}) {
                findPreference(getString(key)).setOnPreferenceChangeListener((p, v) -> {
                    if (MessageRules.minutesOf((String) v) >= 0) return true;
                    Toast.makeText(requireContext(), R.string.invalid_time, Toast.LENGTH_LONG).show();
                    return false;
                });
            }
            findPreference(getString(R.string.key_app_lock)).setOnPreferenceChangeListener((p, on) -> {
                if (!(Boolean) on || canLock(requireContext())) return true;
                Toast.makeText(requireContext(), R.string.app_lock_needs_screen_lock, Toast.LENGTH_LONG).show();
                return false;
            });
            findPreference(getString(R.string.key_send_sim)).setOnPreferenceClickListener(p -> {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                    pickSim();
                else phonePermission.launch(Manifest.permission.READ_PHONE_STATE);
                return true;
            });
            findPreference(getString(R.string.key_rules)).setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), RulesActivity.class));
                return true;
            });
            bindNotificationApps();
            findPreference(getString(R.string.key_enable_notifications)).setOnPreferenceChangeListener((preference, on) -> {
                if ((Boolean) on && !NotificationForwarder.hasAccess(requireContext())) askNotificationAccess();
                return true;
            });
            findPreference(getString(R.string.key_enable_notifications)).setOnPreferenceClickListener(preference -> {
                // Tapping while on but without access (e.g. revoked later) asks again
                if (((SwitchPreferenceCompat) preference).isChecked() && !NotificationForwarder.hasAccess(requireContext())) askNotificationAccess();
                return false;
            });
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
            // Recreates open screens in the new theme
            findPreference(getString(R.string.key_theme)).setOnPreferenceChangeListener((preference, value) -> {
                applyTheme((String) value);
                return true;
            });
            findPreference(getString(R.string.key_export)).setOnPreferenceClickListener(preference -> {
                exportLauncher.launch("sms-forward-settings.json");
                return true;
            });
            findPreference(getString(R.string.key_import)).setOnPreferenceClickListener(preference -> {
                importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
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
            refresh();
        }

        void refresh() {
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
            for (int key : new int[]{R.string.key_forward_log, R.string.key_rules, R.string.key_rules_unmatched, R.string.key_otp_only,
                    R.string.key_enable_notifications, R.string.key_notification_apps,
                    R.string.key_enable_filter, R.string.key_filter_keywords, R.string.key_filter_senders, R.string.key_template,
                    R.string.key_quiet_hours, R.string.key_quiet_start, R.string.key_quiet_end, R.string.key_pause_roaming,
                    R.string.key_send_sim, R.string.key_heartbeat, R.string.key_low_battery, R.string.key_app_lock}) {
                findPreference(getString(key)).setEnabled(pro);
            }
            findPreference(getString(R.string.key_send_sim)).setSummary("same".equals(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(getString(R.string.key_send_sim), "same")) ? getString(R.string.sim_same) : getString(R.string.title_send_sim));
            findPreference(getString(R.string.key_rules)).setSummary(Rules.load(context).isEmpty()
                    ? getString(R.string.summary_rules) : getString(R.string.rules_count, Rules.load(context).size()));
            findPreference(getString(R.string.key_enable_notifications)).setSummary(NotificationForwarder.hasAccess(context)
                    ? R.string.summary_notifications_on : R.string.summary_notifications_access);
            findPreference(getString(R.string.key_privacy_choices)).setVisible(Ads.privacyOptionsRequired(context));
        }

        // ponytail: lists launchable apps on the main thread (~100 ms); move off-thread if it shows up as jank
        private void bindNotificationApps() {
            Context context = requireContext();
            PackageManager pm = context.getPackageManager();
            // The SMS app is excluded: its notifications duplicate the SMS forwarding
            String smsApp = Telephony.Sms.getDefaultSmsPackage(context);
            List<ResolveInfo> launchable = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            TreeMap<String, String> appsByLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (ResolveInfo info : launchable) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(context.getPackageName()) || pkg.equals(smsApp)) continue;
                appsByLabel.put(info.loadLabel(pm).toString(), pkg);
            }
            MultiSelectListPreference apps = findPreference(getString(R.string.key_notification_apps));
            apps.setEntries(appsByLabel.keySet().toArray(new CharSequence[0]));
            apps.setEntryValues(appsByLabel.values().toArray(new CharSequence[0]));
            apps.setSummaryProvider(p -> getString(R.string.summary_notification_apps, ((MultiSelectListPreference) p).getValues().size()));
        }

        @SuppressLint("MissingPermission") // checked by the caller
        private void pickSim() {
            Context context = requireContext();
            List<SubscriptionInfo> sims = context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            labels.add(getString(R.string.sim_same));
            values.add("same");
            if (sims != null) {
                for (SubscriptionInfo sim : sims) {
                    labels.add(getString(R.string.sim_entry, sim.getSimSlotIndex() + 1, sim.getDisplayName()));
                    values.add(String.valueOf(sim.getSubscriptionId()));
                }
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int checked = Math.max(0, values.indexOf(prefs.getString(getString(R.string.key_send_sim), "same")));
            new AlertDialog.Builder(context)
                    .setTitle(R.string.title_send_sim)
                    .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                        prefs.edit().putString(getString(R.string.key_send_sim), values.get(which)).apply();
                        findPreference(getString(R.string.key_send_sim)).setSummary(labels.get(which));
                        d.dismiss();
                    })
                    .show();
        }

        // Prominent disclosure before the system screen (Play policy for notification access)
        private void askNotificationAccess() {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.notification_access_title)
                    .setMessage(R.string.notification_access_message)
                    .setPositiveButton(R.string.open_settings, (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                    .setNegativeButton(R.string.not_now, null)
                    .show();
        }

        /** Adds a free-tier limit after the setting's format check: over the limit shows why and opens the Pro screen. */
        private void limitForFree(int keyRes, Predicate<String> freeAllows, int messageRes) {
            Preference preference = findPreference(getString(keyRes));
            Preference.OnPreferenceChangeListener formatCheck = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener((p, newValue) -> {
                if (formatCheck != null && !formatCheck.onPreferenceChange(p, newValue)) return false;
                if (Billing.isPro(requireContext()) || freeAllows.test((String) newValue)) return true;
                Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show();
                startActivity(new Intent(requireContext(), ProActivity.class));
                return false;
            });
        }

        private static boolean allEmails(String value) {
            List<String> addresses = PhoneNumberUtils.splitList(value);
            for (String address : addresses) {
                if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) return false;
            }
            return !addresses.isEmpty();
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
            boolean email = prefs.getBoolean(getString(R.string.key_enable_email), false);
            if (!sms && !telegram && !web && !email) {
                Toast.makeText(context, R.string.test_none_enabled, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, R.string.test_sending, Toast.LENGTH_SHORT).show();
            String sender = getString(R.string.test_sender);
            String message = getString(R.string.test_message);
            new Thread(() -> {
                List<String> results = new ArrayList<>();
                if (sms) {
                    List<String> targets = Dispatcher.forwardTargets(Billing.isPro(context),
                            PhoneNumberUtils.parsePhoneNumbers(prefs.getString(context.getString(R.string.key_target_sms), "")));
                    int sent = 0;
                    for (String target : targets) {
                        if (Forwarder.forwardViaSMS(context, SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                                MessageRules.format(MessageRules.DEFAULT_SMS_FORMAT, sender, sender, "", message), target, -1)) sent++;
                    }
                    results.add(targets.isEmpty() ? context.getString(R.string.test_sms_no_numbers)
                            : sent == targets.size() ? context.getString(R.string.test_sms_sent, sent)
                            : context.getString(R.string.test_sms_failed, targets.size() - sent));
                }
                String telegramText = MessageRules.format(MessageRules.DEFAULT_TELEGRAM_FORMAT, sender, sender, "", message);
                for (String channel : ForwardWorker.CHANNELS) {
                    String destination = Forwarder.destination(context, channel);
                    if (!(ForwardWorker.TELEGRAM.equals(channel) ? telegram : ForwardWorker.WEB.equals(channel) ? web : email)) continue;
                    results.add(describe(context, Forwarder.label(channel),
                            destination.isEmpty() ? Forwarder.FAIL : Forwarder.send(context, channel, destination, sender, message, telegramText)));
                }
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
