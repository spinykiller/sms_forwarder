# SMS Forward — Privacy Policy

_Last updated: 2026-10-10_

SMS Forward ("the app") forwards text messages you receive to destinations **you** configure. It has no servers and no accounts. The free version shows ads; SMS Forward Pro (a Google Play purchase) removes them.

## Data the app accesses

| Data | Why | Where it goes |
|---|---|---|
| Incoming SMS (sender, text) | The app's only function: forwarding | Only to the destinations you set: phone numbers (by SMS), your Telegram bot/chat (via `api.telegram.org`), and/or your webhook URL (https) |
| Contacts (optional) | Show the sender's name in forwarded messages | Name is included in the forwarded message only; contacts are never uploaded |
| Settings (target numbers, Telegram chat ID and bot token, webhook URL, blocklist) | Needed to forward | Stored on your device only. Android may include them in your device backup if backup is on |

The free version does not store a history of your messages. With Pro, the optional **forward log** keeps your last 100 forwards (sender, text, status) on this phone only; clear it anytime from the log screen. A forward that fails because you are offline is kept on the device until it is delivered or retries end (about a day), then deleted.

## What the app does not do
- No collection of SMS or contact data by the developer.
- SMS and contact data are never used for advertising, analytics or profiling, and never sold or shared with anyone other than the destinations you configure. The ad SDK has no access to them.

## Ads (free version)
The settings screen shows a banner from Google AdMob. AdMob may collect device identifiers (such as the advertising ID), IP-based approximate location and ad interaction data to serve and measure ads, and in some regions to personalize them. Where required (e.g. EEA/UK), the app asks for consent first, and you can change it anytime under **Settings → Privacy choices**. You can also reset or delete your advertising ID in Android settings. See [Google's privacy policy](https://policies.google.com/privacy) and [how Google uses data from partner apps](https://policies.google.com/technologies/partner-sites).

## Purchases
Pro purchases are handled by Google Play. The app only checks with Google Play whether you own Pro; it never sees your payment details.

## Third parties you choose
Messages sent to Telegram or your webhook are handled under that service's own privacy policy. Messages forwarded by SMS are subject to carrier charges and policies.

## Deleting data
Clear the app's data or uninstall the app to delete all settings and any pending forwards.

## Contact
spinykiller@gmail.com · https://github.com/spinykiller/sms_forwarder/issues
