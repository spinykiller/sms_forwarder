package com.enixcoda.smsforward;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public class BatteryOptimizationHelper {
    private static final String TAG = "BatteryOptimizationHelper";
    
    // MIUI/Poco specific package names
    private static final String MIUI_PACKAGE = "com.miui.powerkeeper";
    private static final String MIUI_SECURITY_CENTER = "com.miui.securitycenter";
    private static final String MIUI_AUTOSTART = "com.miui.securitycenter";
    
    // MIUI specific intents
    private static final String MIUI_AUTOSTART_INTENT = "miui.intent.action.OP_AUTO_START";
    private static final String MIUI_BATTERY_OPTIMIZATION_INTENT = "miui.intent.action.POWER_HIDE_MODE_APP_LIST";

    /**
     * Check if the app is being optimized for battery usage
     * @param context Application context
     * @return true if the app is being optimized (which could prevent SMS forwarding)
     */
    public static boolean isBatteryOptimized(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return !powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return false;
    }

    /**
     * Check if the device is running MIUI (Xiaomi/Poco)
     * @param context Application context
     * @return true if MIUI is detected
     */
    public static boolean isMIUIDevice(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(MIUI_PACKAGE, 0) != null ||
                   pm.getPackageInfo(MIUI_SECURITY_CENTER, 0) != null ||
                   Build.MANUFACTURER.toLowerCase().contains("xiaomi") ||
                   Build.MANUFACTURER.toLowerCase().contains("poco") ||
                   Build.MANUFACTURER.toLowerCase().contains("redmi");
        } catch (Exception e) {
            Log.d(TAG, "Error checking MIUI device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Request the user to disable battery optimization for this app
     * @param activity The activity to show the settings dialog from
     */
    public static void requestDisableBatteryOptimization(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isBatteryOptimized(activity)) {
                Log.d(TAG, "App is battery optimized, requesting exemption");
                
                // Try MIUI-specific approach first
                if (isMIUIDevice(activity)) {
                    openMIUIBatterySettings(activity);
                } else {
                    // Standard Android approach
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    
                    try {
                        activity.startActivityForResult(intent, 1001);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open battery optimization settings", e);
                        // Fallback to general battery optimization settings
                        Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        try {
                            activity.startActivity(fallbackIntent);
                        } catch (Exception fallbackException) {
                            Log.e(TAG, "Failed to open fallback battery optimization settings", fallbackException);
                        }
                    }
                }
            } else {
                Log.d(TAG, "App is not battery optimized, no action needed");
            }
        }
    }

    /**
     * Open MIUI-specific battery optimization settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openMIUIBatterySettings(Activity activity) {
        Log.d(TAG, "Opening MIUI battery optimization settings");
        
        // Try multiple MIUI-specific approaches
        Intent[] intents = {
            // MIUI Security Center
            new Intent().setClassName(MIUI_SECURITY_CENTER, "com.miui.securitycenter.MainActivity"),
            // MIUI Power Keeper
            new Intent().setClassName(MIUI_PACKAGE, "com.miui.powerkeeper.ui.PowerKeeperMainActivity"),
            // MIUI Battery Optimization
            new Intent(MIUI_BATTERY_OPTIMIZATION_INTENT),
            // Generic MIUI settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                Log.d(TAG, "Successfully opened MIUI settings with intent: " + intent.getAction());
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open MIUI settings with intent: " + intent.getAction() + ", error: " + e.getMessage());
            }
        }
        
        // Fallback to standard Android settings
        openBatteryOptimizationSettings(activity);
    }

    /**
     * Open MIUI auto-start settings
     * @param context Application context
     */
    public static void openMIUIAutoStartSettings(Context context) {
        Log.d(TAG, "Opening MIUI auto-start settings");
        
        Intent[] intents = {
            // MIUI Auto-start settings
            new Intent(MIUI_AUTOSTART_INTENT),
            // MIUI Security Center
            new Intent().setClassName(MIUI_SECURITY_CENTER, "com.miui.securitycenter.MainActivity"),
            // Generic app settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + context.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Successfully opened MIUI auto-start settings");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open MIUI auto-start settings: " + e.getMessage());
            }
        }
    }

    /**
     * Open the battery optimization settings page
     * @param context Application context
     */
    public static void openBatteryOptimizationSettings(Context context) {
        if (isMIUIDevice(context)) {
            openMIUIBatterySettingsForContext(context);
        } else {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open battery optimization settings", e);
            }
        }
    }

    /**
     * Open MIUI-specific battery optimization settings for Context
     * @param context Application context
     */
    private static void openMIUIBatterySettingsForContext(Context context) {
        Log.d(TAG, "Opening MIUI battery optimization settings");
        
        // Try multiple MIUI-specific approaches
        Intent[] intents = {
            // MIUI Security Center
            new Intent().setClassName(MIUI_SECURITY_CENTER, "com.miui.securitycenter.MainActivity"),
            // MIUI Power Keeper
            new Intent().setClassName(MIUI_PACKAGE, "com.miui.powerkeeper.ui.PowerKeeperMainActivity"),
            // MIUI Battery Optimization
            new Intent(MIUI_BATTERY_OPTIMIZATION_INTENT),
            // Generic MIUI settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + context.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Successfully opened MIUI settings with intent: " + intent.getAction());
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open MIUI settings with intent: " + intent.getAction() + ", error: " + e.getMessage());
            }
        }
        
        // Fallback to standard Android settings
        Intent fallbackIntent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(fallbackIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open fallback battery optimization settings", e);
        }
    }

    /**
     * Get device-specific instructions for battery optimization
     * @param context Application context
     * @return Instructions string
     */
    public static String getBatteryOptimizationInstructions(Context context) {
        if (isMIUIDevice(context)) {
            return "For Poco/Xiaomi devices:\n\n" +
                   "1. Go to Settings > Apps > Manage apps > SMS Forward\n" +
                   "2. Enable 'Autostart' permission\n" +
                   "3. Go to Settings > Battery & performance > Battery saver\n" +
                   "4. Add SMS Forward to 'No restrictions'\n" +
                   "5. Go to Settings > Apps > Permissions > SMS Forward\n" +
                   "6. Enable all SMS permissions\n\n" +
                   "This ensures the app stays running in background.";
        } else {
            return "To ensure SMS forwarding works reliably:\n\n" +
                   "1. Go to Settings > Apps > SMS Forward\n" +
                   "2. Disable battery optimization\n" +
                   "3. Enable background activity\n" +
                   "4. Grant all required permissions\n\n" +
                   "This prevents the system from killing the app.";
        }
    }
}
