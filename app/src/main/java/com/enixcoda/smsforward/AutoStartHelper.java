package com.enixcoda.smsforward;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

public class AutoStartHelper {
    private static final String TAG = "AutoStartHelper";

    // MIUI/Poco specific package names and intents
    private static final String MIUI_SECURITY_CENTER = "com.miui.securitycenter";
    private static final String MIUI_AUTOSTART_INTENT = "miui.intent.action.OP_AUTO_START";
    private static final String MIUI_PERMISSION_INTENT = "miui.intent.action.APP_PERM_EDITOR";
    
    // Other manufacturer specific packages
    private static final String HUAWEI_MANAGER = "com.huawei.systemmanager";
    private static final String OPPO_MANAGER = "com.coloros.safecenter";
    private static final String VIVO_MANAGER = "com.iqoo.secure";
    private static final String SAMSUNG_MANAGER = "com.samsung.android.lool";
    private static final String ONEPLUS_MANAGER = "com.oneplus.security";

    /**
     * Check if the device is running MIUI (Xiaomi/Poco)
     * @param context Application context
     * @return true if MIUI is detected
     */
    public static boolean isMIUIDevice(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(MIUI_SECURITY_CENTER, 0) != null ||
                   Build.MANUFACTURER.toLowerCase().contains("xiaomi") ||
                   Build.MANUFACTURER.toLowerCase().contains("poco") ||
                   Build.MANUFACTURER.toLowerCase().contains("redmi");
        } catch (Exception e) {
            Log.d(TAG, "Error checking MIUI device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the device is running EMUI (Huawei)
     * @param context Application context
     * @return true if EMUI is detected
     */
    public static boolean isEMUIDevice(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(HUAWEI_MANAGER, 0) != null ||
                   Build.MANUFACTURER.toLowerCase().contains("huawei") ||
                   Build.MANUFACTURER.toLowerCase().contains("honor");
        } catch (Exception e) {
            Log.d(TAG, "Error checking EMUI device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the device is running ColorOS (OPPO)
     * @param context Application context
     * @return true if ColorOS is detected
     */
    public static boolean isColorOSDevice(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(OPPO_MANAGER, 0) != null ||
                   Build.MANUFACTURER.toLowerCase().contains("oppo") ||
                   Build.MANUFACTURER.toLowerCase().contains("oneplus");
        } catch (Exception e) {
            Log.d(TAG, "Error checking ColorOS device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the device is running FuntouchOS (Vivo)
     * @param context Application context
     * @return true if FuntouchOS is detected
     */
    public static boolean isFuntouchOSDevice(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageInfo(VIVO_MANAGER, 0) != null ||
                   Build.MANUFACTURER.toLowerCase().contains("vivo");
        } catch (Exception e) {
            Log.d(TAG, "Error checking FuntouchOS device: " + e.getMessage());
            return false;
        }
    }

    /**
     * Request auto-start permission for the app
     * @param activity The activity to show the settings dialog from
     */
    public static void requestAutoStartPermission(Activity activity) {
        Log.d(TAG, "Requesting auto-start permission");
        
        if (isMIUIDevice(activity)) {
            openMIUIAutoStartSettings(activity);
        } else if (isEMUIDevice(activity)) {
            openEMUIAutoStartSettings(activity);
        } else if (isColorOSDevice(activity)) {
            openColorOSAutoStartSettings(activity);
        } else if (isFuntouchOSDevice(activity)) {
            openFuntouchOSAutoStartSettings(activity);
        } else {
            openGenericAutoStartSettings(activity);
        }
    }

    /**
     * Open MIUI auto-start settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openMIUIAutoStartSettings(Activity activity) {
        Log.d(TAG, "Opening MIUI auto-start settings");
        
        Intent[] intents = {
            // MIUI Auto-start settings
            new Intent(MIUI_AUTOSTART_INTENT),
            // MIUI Permission settings
            new Intent(MIUI_PERMISSION_INTENT)
                .putExtra("extra_pkgname", activity.getPackageName()),
            // MIUI Security Center
            new Intent().setClassName(MIUI_SECURITY_CENTER, "com.miui.securitycenter.MainActivity"),
            // Generic app settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                Log.d(TAG, "Successfully opened MIUI auto-start settings");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open MIUI auto-start settings: " + e.getMessage());
            }
        }
        
        // Fallback to generic settings
        openGenericAutoStartSettings(activity);
    }

    /**
     * Open EMUI auto-start settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openEMUIAutoStartSettings(Activity activity) {
        Log.d(TAG, "Opening EMUI auto-start settings");
        
        Intent[] intents = {
            // Huawei System Manager
            new Intent().setClassName(HUAWEI_MANAGER, "com.huawei.systemmanager.startupmgr.StartupMgrActivity"),
            // Huawei Permission Manager
            new Intent().setClassName(HUAWEI_MANAGER, "com.huawei.permissionmanager.ui.MainActivity"),
            // Generic app settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                Log.d(TAG, "Successfully opened EMUI auto-start settings");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open EMUI auto-start settings: " + e.getMessage());
            }
        }
        
        openGenericAutoStartSettings(activity);
    }

    /**
     * Open ColorOS auto-start settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openColorOSAutoStartSettings(Activity activity) {
        Log.d(TAG, "Opening ColorOS auto-start settings");
        
        Intent[] intents = {
            // OPPO Safe Center
            new Intent().setClassName(OPPO_MANAGER, "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            // OPPO Permission Manager
            new Intent().setClassName(OPPO_MANAGER, "com.coloros.safecenter.permission.PermissionManagerActivity"),
            // Generic app settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                Log.d(TAG, "Successfully opened ColorOS auto-start settings");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open ColorOS auto-start settings: " + e.getMessage());
            }
        }
        
        openGenericAutoStartSettings(activity);
    }

    /**
     * Open FuntouchOS auto-start settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openFuntouchOSAutoStartSettings(Activity activity) {
        Log.d(TAG, "Opening FuntouchOS auto-start settings");
        
        Intent[] intents = {
            // Vivo Security Center
            new Intent().setClassName(VIVO_MANAGER, "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            // Vivo Permission Manager
            new Intent().setClassName(VIVO_MANAGER, "com.iqoo.secure.safeguard.SoftPermissionDetailActivity"),
            // Generic app settings
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName()))
        };
        
        for (Intent intent : intents) {
            try {
                activity.startActivity(intent);
                Log.d(TAG, "Successfully opened FuntouchOS auto-start settings");
                return;
            } catch (Exception e) {
                Log.d(TAG, "Failed to open FuntouchOS auto-start settings: " + e.getMessage());
            }
        }
        
        openGenericAutoStartSettings(activity);
    }

    /**
     * Open generic auto-start settings
     * @param activity The activity to show the settings dialog from
     */
    private static void openGenericAutoStartSettings(Activity activity) {
        Log.d(TAG, "Opening generic auto-start settings");
        
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open generic auto-start settings: " + e.getMessage());
        }
    }

    /**
     * Get device-specific instructions for enabling auto-start
     * @param context Application context
     * @return Instructions string
     */
    public static String getAutoStartInstructions(Context context) {
        int help = isMIUIDevice(context) ? R.string.autostart_help_miui
                : isEMUIDevice(context) ? R.string.autostart_help_emui
                : isColorOSDevice(context) ? R.string.autostart_help_coloros
                : isFuntouchOSDevice(context) ? R.string.autostart_help_vivo
                : R.string.autostart_help_generic;
        return context.getString(help);
    }
}
