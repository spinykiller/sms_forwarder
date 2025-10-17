# SMS Forwarder v1.0.8 - Security & Telegram Fix

**Release Date:** October 17, 2025

## 🔒 Security & Bug Fixes

This release removes hardcoded credentials from examples and fixes Telegram @ username encoding issue.

### 🔒 Security Improvements

**Removed Hardcoded Credentials**
- Removed real bot tokens from documentation examples
- Replaced with placeholder tokens (e.g., `110201543:AAH4eZxxxxxxxxxxxxxxxxxxxxxP4U`)
- Replaced real group names with generic examples (`@mygroup`, `@mychannel`)
- All examples now use placeholders for security

### 🐛 Bug Fixes

**Telegram @ Username Encoding Fixed**
- Fixed issue where @username was being URL encoded incorrectly
- The `@` symbol in usernames (like `@mygroup`) is now preserved correctly
- Numeric chat IDs are still properly URL encoded
- Fixed logging to mask @ symbols for privacy

### 🔧 Technical Changes

**Telegram Implementation**
- Conditional URL encoding: only encode numeric chat IDs, preserve @ in usernames
- Improved chat ID parameter handling
- Better privacy in logs (masks @ symbols)
- More robust username format support

### 📋 Changes Summary

- ✅ Removed hardcoded bot tokens from all files
- ✅ Removed real group/channel names from examples
- ✅ Fixed @ symbol encoding in Telegram usernames
- ✅ Improved logging privacy
- ✅ Updated all documentation with placeholder examples

### 🎯 What Now Works

- ✅ Telegram @username format works correctly (was being over-encoded)
- ✅ Both numeric and @username formats supported
- ✅ No sensitive credentials in documentation
- ✅ Secure example placeholders throughout

### 🔄 Compatibility

- **Android Version:** 7.0 (API 25) or higher
- **Upgrade from v1.0.7:** Recommended upgrade for security and bug fixes
- **Storage:** Same size (~1.9 MB)

### ⚠️ Important

- **For v1.0.7 users**: Upgrade to v1.0.8 for proper @ username support
- **Telegram not working?** Make sure you're using the correct format:
  - Groups/Channels: `@username` (keep the @)
  - Personal chats: Numeric ID only (e.g., `123456789`)

### 💡 Correct Configuration Examples

**For Group Forwarding:**
```
Bot Token: [Your token from @BotFather]
Chat ID: @mygroup
```

**For Personal Chat:**
```
Bot Token: [Your token from @BotFather]
Chat ID: 123456789
```

All features from previous versions remain:
- ✅ Sender blocklist (fixed in v1.0.6)
- ✅ Telegram with proper @username support (fixed in v1.0.8)
- ✅ SMS forwarding to multiple numbers
- ✅ Web webhook forwarding
- ✅ Loop prevention
- ✅ Auto-start after reboot

---

**Download:** [SMS-Forwarder-v1.0.8.apk](releases/SMS-Forwarder-v1.0.8.apk)

**Repository:** [github.com/spinykiller/sms-forwarder](https://github.com/spinykiller/sms-forwarder)

**Issues/Support:** [GitHub Issues](https://github.com/spinykiller/sms-forwarder/issues)

