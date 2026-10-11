package com.enixcoda.smsforward;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * Pro app lock for every screen (settings hold tokens, the log holds SMS text): once unlocked, the app stays
 * unlocked until it has been fully in the background for a minute.
 */
public abstract class LockedActivity extends AppCompatActivity {
    private static final long RELOCK_AFTER_MS = 60_000;
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private static long unlockedUntil = 0; // SystemClock.elapsedRealtime(); 0 = locked; MAX = app in use
    private static int startedScreens = 0;
    private boolean unlocking;

    static boolean canLock(Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    @Override
    protected void onStart() {
        super.onStart();
        startedScreens++;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Moving between our own screens starts the next one before stopping this one, so only leaving the app hits 0
        if (--startedScreens == 0 && !isChangingConfigurations() && unlockedUntil == Long.MAX_VALUE)
            unlockedUntil = SystemClock.elapsedRealtime() + RELOCK_AFTER_MS;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean enabled = Billing.isPro(this)
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_app_lock), false);
        // No screen lock any more (removed after enabling): don't lock the user out of their own settings
        if (!enabled || !canLock(this)) return;
        if (SystemClock.elapsedRealtime() <= unlockedUntil) {
            unlockedUntil = Long.MAX_VALUE;
            return;
        }
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
                finishAffinity(); // cancelled or locked out: close the app rather than show it
            }
        }).authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build());
    }
}
