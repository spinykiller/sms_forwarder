# Play Console declarations — draft for verification

## SMS permissions declaration

**Core functionality:** Forward incoming SMS messages to destinations configured by the user. The app receives SMS using RECEIVE_SMS and can send a copy to another phone using SEND_SMS. The user may instead enable forwarding to their Telegram chat, HTTPS webhook, or email through their own SMTP account. The user controls which destinations are enabled. No message is sent to a developer-operated server. Without SMS access the app cannot perform its core forwarding function. The app is not a default SMS handler; request review under the cross-device synchronization or transfer of SMS exception. Google decides whether the exception applies.

The optional Pro SIM picker requests READ_PHONE_STATE to list available SIMs for outgoing SMS. Optional Pro notification forwarding requests notification access after an in-app disclosure; it forwards content only from apps selected by the user.

Demo video: show destination setup, an incoming SMS and the forwarded copy, then the notification-access disclosure and system access screen. Video still required.

## Data safety

Review the final app and SDK disclosures in Play Console before submitting; these are working answers, not verified Console selections.

- SMS sender and message content: used for app functionality; sent to user-configured destinations. Optional Pro notification forwarding similarly handles selected notification title and content.
- Contacts: optional lookup of sender names; the name may be included in a forward.
- Settings and credentials: stored locally; Android backup and user-selected settings exports may contain them. Pro forward logs and pending retries are stored locally; app data can be deleted by clearing storage or uninstalling.
- AdMob/UMP: check Google's current SDK data safety guidance for device or other IDs, approximate location, interactions and diagnostics, including collection, sharing, purposes and required/optional status. Google Play handles purchases.
- Transit: HTTPS is required for webhooks; SMTP uses TLS; SMS is carrier-transmitted. Confirm the Console's encryption answer against every data flow and SDK; do not claim SMS is end-to-end encrypted.
- Contains ads: yes. Advertising ID: advertising.

Sources: https://support.google.com/googleplay/android-developer/answer/10208820 and https://support.google.com/googleplay/android-developer/answer/10787469
