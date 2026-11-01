# Play Console declarations — draft for verification

## SMS permissions declaration

**Core functionality:** Forward incoming SMS messages to destinations configured by the user. The app receives SMS using RECEIVE_SMS and can send a copy to another phone using SEND_SMS. The user may instead enable forwarding to their Telegram chat, HTTPS webhook, or email through their own SMTP account. The user controls which destinations are enabled. No message is sent to a developer-operated server. Without SMS access the app cannot perform its core forwarding function. The app is not a default SMS handler; request review under the cross-device synchronization or transfer of SMS exception. Google decides whether the exception applies.

The optional Pro SIM picker requests READ_PHONE_STATE to list available SIMs for outgoing SMS. Optional Pro notification forwarding requests notification access after an in-app disclosure; it forwards content only from apps selected by the user.

Demo video: the local `demo.mp4` is a draft only. Replace it with a recording showing installation, permission request, destination setup, the same incoming SMS and forwarded copy, and the notification-access disclosure before submitting.

## Data safety

Review the final app and SDK disclosures in Play Console before submitting; these are working answers, not verified Console selections.

- SMS sender and message content: used for app functionality; sent to user-configured destinations. Optional Pro notification forwarding similarly handles selected notification title and content.
- Contacts: optional lookup of sender names; the name may be included in a forward.
- Settings and credentials: stored locally; Android backup and user-selected settings exports may contain them. Pro forward logs and pending retries are stored locally; app data can be deleted by clearing storage or uninstalling.
- AdMob/UMP: check Google's current SDK data safety guidance for device or other IDs, approximate location, interactions and diagnostics, including collection, sharing, purposes and required/optional status. Google Play handles purchases.
- Transit: HTTPS is required for webhooks; SMTP uses TLS; SMS is carrier-transmitted. Confirm the Console's encryption answer against every data flow and SDK; do not claim SMS is end-to-end encrypted.
- Contains ads: yes. Advertising ID: advertising.

Sources: https://support.google.com/googleplay/android-developer/answer/10208820 and https://support.google.com/googleplay/android-developer/answer/10787469

## Other Console forms

- App category: Tools. Suggested tags: select only applicable SMS and utility tags available in Console.
- App access: All functionality is accessible without an account; paid Pro features use Google Play purchases.
- Ads: Yes. Advertising ID: Yes, used for advertising.
- Target audience: 18 and over only.
- Government apps: No. Financial features: None. Health features: None.
- Content rating: Answer the questionnaire for a utility app with no app-provided public user-generated content, violence, sexual content, gambling or controlled-substance content. Messages and notification content are user-supplied; answer any questions about user interaction and unrestricted access according to the exact wording in Console.
- Privacy policy: https://github.com/spinykiller/sms_forwarder/blob/master/docs/PRIVACY.md
- SMS declaration video URL: [Paste the unlisted YouTube URL after recording and upload.]

Google's data safety definitions distinguish collection from sharing and exclude certain user-directed transfers from the definition of sharing. Confirm the exact form answers for each configured destination and every integrated SDK; do not assume all user-directed transfers are automatically exempt. See https://support.google.com/googleplay/android-developer/answer/10787469.

### Data safety form: working selections

| Question | Working answer |
|---|---|
| Does the app collect or share required user data? | Yes; include the advertising SDK. |
| SMS or MMS messages | Sender and SMS text leave the device for configured destinations, for app functionality. Collection/sharing classifications require the Console definition review above. |
| Contacts | Optional sender-name lookup; a name may leave the device as part of a forward. App functionality. |
| Other in-app messages | Optional selected notification titles and text leave the device for configured destinations. App functionality. Confirm the Console data-type mapping. |
| Device or other IDs | AdMob: collected and shared, for advertising, analytics and fraud prevention; check SDK version and options. |
| Approximate location | AdMob may derive it from IP address; review Google SDK disclosures and configuration. |
| App interactions and diagnostics | AdMob: collected and shared, for advertising, analytics and fraud prevention; check SDK version and options. |
| Encrypted in transit | HTTPS webhooks and TLS SMTP; AdMob uses TLS per Google's current SDK disclosure. SMS uses the carrier. Review Play's user-directed-transfer guidance before selecting the form answer. |
| Can users request data deletion? | Clear app storage or uninstall for locally stored settings and logs; recipients and third-party services have their own deletion procedures. No developer account exists. |

Google's [Mobile Ads disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure) describes the latest SDK; this app uses an older SDK (23.5.0), so verify its applicable disclosures before submitting.
