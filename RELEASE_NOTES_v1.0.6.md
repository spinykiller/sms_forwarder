# SMS Forwarder v1.0.6 - Critical Bug Fix Release

**Release Date:** October 17, 2025

## 🐛 Critical Bug Fix

This is a critical bug fix release that resolves an issue where enabling the blocklist feature would stop ALL SMS forwarding.

### What Was Fixed

**Blocklist Bug (Critical)**
- Fixed issue where enabling blocklist with no numbers entered would block ALL messages
- Fixed empty string matching causing all phone numbers to be blocked
- Fixed overly aggressive partial matching logic

### 🔧 Technical Details

The bug was in the `isSenderBlocked()` method where:
1. Empty strings in the blocked numbers list would match ANY phone number (because `string.endsWith("")` always returns `true`)
2. Partial matching didn't validate minimum phone number length

### ✨ Improvements

**Enhanced Blocklist Logic**
- Now properly skips empty blocked numbers
- Added validation to ensure sender number is not empty
- Partial matching now requires minimum 7-digit phone numbers
- More precise logging for exact vs partial matches

### 📋 Changes in v1.0.6

- ✅ Fixed blocklist blocking all messages when enabled with no numbers
- ✅ Added empty string validation in `isSenderBlocked()` method
- ✅ Improved partial matching to require minimum 7-digit numbers
- ✅ Enhanced logging to distinguish exact vs partial matches
- ✅ More robust error handling

### 🔄 Upgrade Instructions

**IMPORTANT: If you installed v1.0.5, please upgrade to v1.0.6 immediately**

1. Download `SMS-Forwarder-v1.0.6.apk`
2. Install over v1.0.5 (settings will be preserved)
3. Your blocklist settings will remain intact
4. SMS forwarding will now work correctly with blocklist enabled

### 📊 What Now Works

- ✅ Blocklist can be enabled without blocking all messages
- ✅ Forwarding works when blocklist is enabled with no numbers
- ✅ Forwarding works when blocklist is enabled with specific numbers
- ✅ Only messages from blocked numbers are prevented from forwarding
- ✅ All other forwarding functionality remains intact

### 🎯 Tested Scenarios

- ✅ Blocklist enabled with no numbers → Forwards all messages (correct)
- ✅ Blocklist enabled with specific numbers → Blocks only those numbers (correct)
- ✅ Blocklist disabled → Forwards all messages (correct)
- ✅ Blocklist with whitespace only → Forwards all messages (correct)

### 🔄 Compatibility

- **Android Version:** 7.0 (API 25) or higher
- **Upgrade from v1.0.5:** Safe upgrade - bug fix only
- **Storage:** Same size as v1.0.5 (~1.9 MB)
- **Performance:** Same as v1.0.5

### 📝 Full Feature List

All features from v1.0.5 remain available:
- Sender blocklist (now working correctly)
- Multiple forwarding methods (SMS, Telegram, Web)
- Multiple recipient support
- Loop prevention
- Auto-start after reboot
- Battery optimization handling

### 🙏 Apology & Thanks

We apologize for the inconvenience caused by the v1.0.5 bug. Thank you to the users who quickly reported this issue, allowing us to release a fix within hours.

---

**Download:** [SMS-Forwarder-v1.0.6.apk](releases/SMS-Forwarder-v1.0.6.apk)

**Repository:** [github.com/spinykiller/sms-forwarder](https://github.com/spinykiller/sms-forwarder)

**Issues/Support:** [GitHub Issues](https://github.com/spinykiller/sms-forwarder/issues)

