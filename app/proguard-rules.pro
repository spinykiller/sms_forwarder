# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/Enix/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Keep SMS Forward classes
-keep class com.enixcoda.smsforward.** { *; }

# Keep BroadcastReceiver classes
-keep class * extends android.content.BroadcastReceiver {
    public <methods>;
}

# Keep Service classes
-keep class * extends android.app.Service {
    public <methods>;
}

# Keep Activity classes
-keep class * extends android.app.Activity {
    public <methods>;
}

# Keep Preference classes
-keep class * extends androidx.preference.Preference {
    public <methods>;
}

# Remove debug information
-renamesourcefileattribute SourceFile
-dontwarn **

# Optimize aggressively
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove unused code
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Keep only necessary attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
