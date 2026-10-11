# SMS Forward — Privacy Policy

_Last updated: 2026-10-10_

SMS Forward ("the app") forwards text messages you receive to destinations **you** configure. It has no servers and no accounts. The free version shows ads; SMS Forward Pro (a Google Play purchase) removes them.

## Data the app accesses

| Data | Why | Where it goes |
|---|---|---|
| Incoming SMS (sender, text) | The app's main function: forwarding | Only to the destinations you set: phone numbers (by SMS), your Telegram bot/chat (via `api.telegram.org`), your webhook URL (https; plain, Slack, Discord or Teams format), and/or email addresses (sent through **your own** SMTP account) |
| Notifications of apps you pick (Pro, optional; needs notification access you grant) | Forward app notifications | Title and text only, to the same destinations you set. Apps you don't pick are never read |
| Contacts (optional) | Show the sender's name in forwarded messages | Name is included in the forwarded message only; contacts are never uploaded |
| SIM list (Pro, optional; phone permission asked only when you open "Send SMS from") | Let you choose which SIM sends forwards | Stays on the phone |
| Battery level and device name (Pro, only if you turn on the health check) | Daily "still running" message and low-battery alert | Sent only to your Telegram/Web/Email destinations |
| Settings (target numbers, Telegram chat ID and bot token, webhook URL, email address and SMTP password, rules, filters) | Needed to forward | Stored on your device only. Android may include them in your device backup if backup is on. "Export settings" writes them to a file you choose |

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
