package com.enixcoda.smsforward;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int SMS_PERMISSION_REQUEST_CODE = 101;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request all necessary permissions
        requestAllPermissions();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only check permissions and status if SMS permissions are not granted
        // This reduces unnecessary checks when app is already properly configured
        if (!hasSmsPermissions()) {
            checkPermissionsAndStatus();
        }
    }

    private void requestAllPermissions() {
        // Check and request SMS permissions first (most critical)
        if (!hasSmsPermissions()) {
            requestSmsPermissions();
        } else {
            // SMS permissions granted, check other permissions
            checkOtherPermissions();
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermissions() {
        String[] smsPermissions = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        };
        
        // Show explanation dialog first
        showPermissionExplanationDialog("SMS Permissions Required", 
            "This app needs SMS permissions to forward text messages. Without these permissions, the app cannot function.\n\n" +
            "• RECEIVE_SMS: To receive incoming messages\n" +
            "• SEND_SMS: To forward messages to target numbers",
            () -> ActivityCompat.requestPermissions(this, smsPermissions, SMS_PERMISSION_REQUEST_CODE));
    }

    private void checkOtherPermissions() {
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }

        // Request other permissions
        String[] otherPermissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_CONTACTS
        };
        
        boolean needsOtherPermissions = false;
        for (String permission : otherPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needsOtherPermissions = true;
                break;
            }
        }
        
        if (needsOtherPermissions) {
            ActivityCompat.requestPermissions(this, otherPermissions, PERMISSION_REQUEST_CODE);
        } else {
            // All permissions granted, proceed with app initialization
            initializeApp();
        }
    }

    private void initializeApp() {
        // Check battery optimization status
        checkBatteryOptimization();

        // Check auto-start permission
        checkAutoStartPermission();

        // Start the SMS forwarding service
        startSMSForwardService();
    }

    private void checkPermissionsAndStatus() {
        if (hasSmsPermissions()) {
            checkBatteryOptimization();
        } else {
            // SMS permissions not granted, show warning
            showPermissionWarningDialog();
        }
    }

    private void showPermissionExplanationDialog(String title, String message, Runnable onContinue) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant Permissions", (dialog, which) -> onContinue.run())
            .setNegativeButton("Cancel", (dialog, which) -> {
                showPermissionDeniedDialog();
            })
            .setCancelable(false)
            .show();
    }

    private void showPermissionWarningDialog() {
        new AlertDialog.Builder(this)
            .setTitle("SMS Permissions Required")
            .setMessage("SMS permissions are required for this app to function. Please grant SMS permissions in the next dialog or go to Settings to enable them manually.")
            .setPositiveButton("Grant Permissions", (dialog, which) -> requestSmsPermissions())
            .setNegativeButton("Open Settings", (dialog, which) -> openAppSettings())
            .setCancelable(false)
            .show();
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage("Without SMS permissions, this app cannot forward messages. You can grant permissions later in Settings.")
            .setPositiveButton("Open Settings", (dialog, which) -> openAppSettings())
            .setNegativeButton("Continue Anyway", (dialog, which) -> {
                // User chose to continue without permissions
                Log.w(TAG, "User chose to continue without SMS permissions");
            })
            .setCancelable(false)
            .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        switch (requestCode) {
            case SMS_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "SMS permissions granted");
                    checkOtherPermissions();
                } else {
                    Log.w(TAG, "SMS permissions denied");
                    showPermissionDeniedDialog();
                }
                break;
                
            case PERMISSION_REQUEST_CODE:
            case NOTIFICATION_PERMISSION_REQUEST_CODE:
                // Other permissions - not critical for basic functionality
                Log.d(TAG, "Other permissions result: " + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
                initializeApp();
                break;
        }
    }

    private void checkBatteryOptimization() {
        if (BatteryOptimizationHelper.isBatteryOptimized(this)) {
            Log.w(TAG, "App is being optimized for battery usage - this may prevent SMS forwarding");
            showBatteryOptimizationDialog();
        } else {
            Log.d(TAG, "App is not being optimized for battery usage");
        }
    }

    private void checkAutoStartPermission() {
        if (!AutoStartHelper.isAutoStartEnabled(this)) {
            Log.w(TAG, "Auto-start permission not enabled - this may prevent SMS forwarding after reboot");
            showAutoStartDialog();
        } else {
            Log.d(TAG, "Auto-start permission is enabled");
        }
    }

    private void showBatteryOptimizationDialog() {
        String title = "Battery Optimization Detected";
        String message = BatteryOptimizationHelper.getBatteryOptimizationInstructions(this);
        
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings", (dialog, which) -> {
                BatteryOptimizationHelper.requestDisableBatteryOptimization(this);
            })
            .setNegativeButton("Later", (dialog, which) -> {
                Log.d(TAG, "User chose to handle battery optimization later");
            })
            .setCancelable(false)
            .show();
    }

    private void showAutoStartDialog() {
        String title = "Auto-Start Permission Required";
        String message = AutoStartHelper.getAutoStartInstructions(this);
        
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings", (dialog, which) -> {
                AutoStartHelper.requestAutoStartPermission(this);
            })
            .setNegativeButton("Later", (dialog, which) -> {
                Log.d(TAG, "User chose to handle auto-start later");
            })
            .setCancelable(false)
            .show();
    }

    private void startSMSForwardService() {
        Intent serviceIntent = new Intent(this, SMSForwardService.class);
        startService(serviceIntent);
        Log.d(TAG, "Started SMS Forward Service");
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            
            // Add click listener for permission status preference
            Preference permissionStatusPref = findPreference(getString(R.string.key_permission_status));
            if (permissionStatusPref != null) {
                updatePermissionStatusSummary(permissionStatusPref);
                permissionStatusPref.setOnPreferenceClickListener(preference -> {
                    openAppSettings();
                    return true;
                });
            }
            
            // Add click listener for battery optimization preference
            Preference batteryOptimizationPref = findPreference(getString(R.string.key_battery_optimization));
            if (batteryOptimizationPref != null) {
                batteryOptimizationPref.setOnPreferenceClickListener(preference -> {
                    BatteryOptimizationHelper.openBatteryOptimizationSettings(getContext());
                    return true;
                });
            }
            
            // Add click listener for auto-start settings preference
            Preference autostartPref = findPreference(getString(R.string.key_autostart_settings));
            if (autostartPref != null) {
                autostartPref.setOnPreferenceClickListener(preference -> {
                    AutoStartHelper.requestAutoStartPermission(requireActivity());
                    return true;
                });
            }
            
            // Add click listener for auto-start status preference
            Preference autostartStatusPref = findPreference(getString(R.string.key_autostart_status));
            if (autostartStatusPref != null) {
                updateAutoStartStatusSummary(autostartStatusPref);
                autostartStatusPref.setOnPreferenceClickListener(preference -> {
                    AutoStartHelper.requestAutoStartPermission(requireActivity());
                    return true;
                });
            }
        }

        private void updatePermissionStatusSummary(Preference preference) {
            if (getContext() == null) return;
            
            boolean hasSmsPermissions = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                                       ContextCompat.checkSelfPermission(getContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
            
            boolean hasInternetPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
            boolean hasContactsPermission = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            
            if (hasSmsPermissions && hasInternetPermission && hasContactsPermission) {
                preference.setSummary("All permissions granted ✓");
            } else {
                StringBuilder missing = new StringBuilder("Missing: ");
                if (!hasSmsPermissions) missing.append("SMS ");
                if (!hasInternetPermission) missing.append("Internet ");
                if (!hasContactsPermission) missing.append("Contacts ");
                preference.setSummary(missing.toString().trim());
            }
        }

        private void openAppSettings() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }

        private void updateAutoStartStatusSummary(Preference preference) {
            if (getContext() == null) return;
            
            boolean autoStartEnabled = AutoStartHelper.isAutoStartEnabled(getContext());
            String deviceManufacturer = AutoStartHelper.getDeviceManufacturer(getContext());
            
            if (autoStartEnabled) {
                preference.setSummary("Auto-start enabled ✓");
            } else {
                preference.setSummary("Auto-start disabled - " + deviceManufacturer + " device");
            }
        }
    }
}
