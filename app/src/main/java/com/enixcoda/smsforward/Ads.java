package com.enixcoda.smsforward;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AdMob banner + UMP consent.
 *
 * Placement rule (as in MemoryMedic): one anchored adaptive banner per screen, pinned below the
 * scrolling content so it never covers it, with a "Remove ads" link. Never on the Pro screen.
 * Ads only ever see AdMob's own signals: SMS and contact data never reach the ad SDK.
 */
final class Ads {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private Ads() {
    }

    /** Gathers consent (UMP), then initializes the SDK and runs onReady if ads are allowed. Safe to call on every start. */
    static void onStart(Activity activity, Runnable onReady) {
        ConsentInformation consent = UserMessagingPlatform.getConsentInformation(activity);
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> enableIfAllowed(activity, onReady)),
                error -> enableIfAllowed(activity, onReady));
        // Consent from a previous session allows ads right away
        enableIfAllowed(activity, onReady);
    }

    private static void enableIfAllowed(Activity activity, Runnable onReady) {
        if (!UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) return;
        if (initialized.compareAndSet(false, true)) MobileAds.initialize(activity.getApplicationContext(), status -> { });
        onReady.run();
    }

    /** GDPR/UMP: users in regions that need it must be able to revisit their ad-consent choice. */
    static boolean privacyOptionsRequired(Context context) {
        return UserMessagingPlatform.getConsentInformation(context).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    /** onReady runs either way: withdrawn consent must remove the banner, not just stop new ones. */
    static void showPrivacyOptions(Activity activity, Runnable onReady) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            enableIfAllowed(activity, () -> { });
            onReady.run();
        });
    }

    /** Shows the slot with a banner in its container, or hides it for Pro users and without ad consent. */
    static void bindBanner(Activity activity, View slot, ViewGroup container) {
        boolean show = !Billing.isPro(activity) && initialized.get()
                && UserMessagingPlatform.getConsentInformation(activity).canRequestAds();
        slot.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            destroyBanner(container);
            return;
        }
        if (container.getChildCount() > 0) return; // already loaded
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        AdView adView = new AdView(activity);
        // Anchored adaptive banner: full width, height chosen by AdMob for this device (~50–90dp).
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, (int) (metrics.widthPixels / metrics.density)));
        adView.setAdUnitId(BuildConfig.BANNER_UNIT);
        container.addView(adView);
        adView.loadAd(new AdRequest.Builder().build());
    }

    static void destroyBanner(ViewGroup container) {
        if (container.getChildCount() == 0) return;
        ((AdView) container.getChildAt(0)).destroy();
        container.removeAllViews();
    }
}
