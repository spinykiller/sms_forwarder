# Play Store release checklist

Package: `com.spinykiller.smsforward` · target API 36 · min API 25

## 1. Upload key

The existing upload key is `upload.keystore`; `keystore.properties` references it. Keep both files private and backed up outside the repo. Do not regenerate or overwrite the key. Enroll in Play App Signing when creating the app.

## 2. AdMob
AdMob app IDs are per app; MemoryMedic's can't be reused. In AdMob (account `pub-5969937375579820`): **Apps → Add app** (Android, `com.spinykiller.smsforward`) → **Ad units → Banner**. Put both IDs in `gradle.properties`:
```
admobAppId=ca-app-pub-5969937375579820~…
admobBannerUnit=ca-app-pub-5969937375579820/…
```
Release builds fail while these are unset (debug always uses Google's test IDs). Configure the GDPR message in **AdMob → Privacy & messaging** (the app shows it through UMP).

## 3. Build
```bash
./gradlew testDebugUnitTest lintDebug bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
```
Bump `versionCode` in `app/build.gradle` for every upload.

## 4. Play Console
- **Privacy policy URL:** https://github.com/spinykiller/sms_forwarder/blob/master/docs/PRIVACY.md
- **Permissions declaration (SMS):** required, or the app is rejected. Core functionality: *SMS forwarding to the user's other devices/services*. Closest permitted use: **cross-device synchronization or transfer of SMS** (forwarding to the user's own phone, Telegram or server). Explain: the user configures destinations; no data goes to the developer; SMS data is never used for ads. Provide a demo video showing setup → incoming SMS → forwarded copy.
  - Risk: Google decides SMS exceptions case by case. Rejection is possible; the GitHub APK remains the fallback.
- **Data safety:** Review `docs/store/console_answers.md` and the current Google SDK disclosures. SMS and optional contact names and selected notifications are sent to user-configured destinations for app functionality; determine collection and sharing under Play's current definitions. Webhooks use HTTPS and SMTP uses TLS; SMS is carrier-transmitted. AdMob may collect device IDs, approximate location, interactions and diagnostics. Declare ads and advertising ID use.
- **Notification access** (`NotificationListenerService`): no declaration form, but review checks the prominent disclosure shown before the system screen (in the app) and the privacy policy line; show it in the demo video.
- **READ_PHONE_STATE**: requested only from the Pro "Send SMS from" picker to list SIMs; mention it in the SMS declaration's description.
- **In-app products** (IDs must match `Billing.java`):
  - Subscription `pro` with base plans `annual` (+ offer `trial`, e.g. 7-day free trial, new customers) and `monthly`.
  - One-time product `pro_lifetime`.
  - Monthly: USD 1.99 / INR 99; annual: USD 9.99 / INR 499; lifetime: USD 19.99 / INR 999. Convert other regions from USD; enable new regions. Annual offer `trial`: 7 days free for customers who have never had `pro`.
  - Add license testers (Setup → License testing) and test purchases on the internal track; the emulator without Play Store shows "billing isn't available".
- **Content rating:** questionnaire (utility, no user-generated content shared publicly).
- **Target audience:** 18+.
- **Testing:** personal developer accounts created after Nov 2023 need a closed test with 12+ testers for 14 days before production.

## 5. Before each release
Emulator smoke test: grant permissions → enable a method → `adb emu sms send <number> "<long text>"` → forwarded once, joined; "Send test message" reports results.
