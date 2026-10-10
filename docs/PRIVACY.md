# SMS Forward — Privacy Policy

_Last updated: 2026-10-10_

SMS Forward ("the app") forwards text messages you receive to destinations **you** configure. It has no servers and no accounts.

## Data the app accesses

| Data | Why | Where it goes |
|---|---|---|
| Incoming SMS (sender, text) | The app's only function: forwarding | Only to the destinations you set: phone numbers (by SMS), your Telegram bot/chat (via `api.telegram.org`), and/or your webhook URL (https) |
| Contacts (optional) | Show the sender's name in forwarded messages | Name is included in the forwarded message only; contacts are never uploaded |
| Settings (target numbers, Telegram chat ID and bot token, webhook URL, blocklist) | Needed to forward | Stored on your device only. Android may include them in your device backup if backup is on |

The app does not store a history of your messages. A forward that fails because you are offline is kept on the device until it is delivered or retries end (about a day), then deleted.

## What the app does not do
- No collection of SMS or contact data by the developer.
- SMS and contact data are never used for advertising, analytics or profiling, and never sold or shared with anyone other than the destinations you configure.

## Third parties you choose
Messages sent to Telegram or your webhook are handled under that service's own privacy policy. Messages forwarded by SMS are subject to carrier charges and policies.

## Deleting data
Clear the app's data or uninstall the app to delete all settings and any pending forwards.

## Contact
spinykiller@gmail.com · https://github.com/spinykiller/sms_forwarder/issues
