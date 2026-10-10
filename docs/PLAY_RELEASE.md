# Play Store release checklist

Package: `com.spinykiller.smsforward` · target API 36 · min API 25

## 1. Upload key (once)
```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
Create `keystore.properties` in the repo root (gitignored):
```
storeFile=upload-keystore.jks
storePassword=…
keyAlias=upload
keyPassword=…
```
Back up both files outside the repo. Enroll in Play App Signing when creating the app.

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
- **Data safety:** SMS/MMS messages and Contacts are *processed* to provide app functionality and *shared* only at the user's direction (to destinations they set); encrypted in transit (https; SMS is carrier-transmitted); deletable by clearing app data. Plus the AdMob SDK: *Device or other IDs*, *Approximate location*, *App interactions*, *Diagnostics* — collected and shared for Advertising/Analytics/Fraud prevention. Declare **Contains ads: yes** and the `AD_ID` permission use (advertising).
- **In-app products** (IDs must match `Billing.java`):
  - Subscription `pro` with base plans `annual` (+ offer `trial`, e.g. 7-day free trial, new customers) and `monthly`.
  - One-time product `pro_lifetime`.
  - Add license testers (Setup → License testing) and test purchases on the internal track; the emulator without Play Store shows "billing isn't available".
- **Content rating:** questionnaire (utility, no user-generated content shared publicly).
- **Target audience:** 18+.
- **Testing:** personal developer accounts created after Nov 2023 need a closed test with 12+ testers for 14 days before production.

## 5. Before each release
Emulator smoke test: grant permissions → enable a method → `adb emu sms send <number> "<long text>"` → forwarded once, joined; "Send test message" reports results.
