# SMS Forwarder v1.0.5 - Release Notes

**Release Date:** October 17, 2025

## 🚫 New Feature: Sender Blocklist

This release introduces a highly requested feature - **Sender Blocklist** - allowing you to prevent messages from specific phone numbers from being forwarded.

### 🎯 Key Features

#### Sender Blocklist
- **Block Specific Senders** - Prevent messages from certain phone numbers from being forwarded
- **Easy Configuration** - Simple UI to add/remove blocked numbers via comma-separated list
- **Toggle Control** - Enable/disable blocklist feature with a single switch
- **Smart Matching** - Intelligent phone number matching that handles different formats
- **Multiple Numbers** - Block as many numbers as you need
- **Universal Blocking** - Works across all forwarding methods (SMS, Telegram, Web)

### ✨ What's New

1. **Blocklist Settings Section**
   - New dedicated settings category for blocklist configuration
   - Enable/disable toggle for quick control
   - Text field for entering blocked numbers

2. **Smart Phone Number Matching**
   - Handles various phone number formats (+1, 001, local, etc.)
   - Partial matching for numbers with/without country codes
   - Automatic number normalization

3. **Comprehensive Logging**
   - Logs when messages are blocked for debugging
   - Easy to verify blocklist is working correctly

### 🔧 How to Use

1. **Open SMS Forward App**
2. **Scroll to "Blocklist Settings"**
3. **Toggle "Enable Blocklist" ON**
4. **Enter Blocked Numbers**
   - Format: `+1 5551234567, +86 10000, 5559876543`
   - Separate multiple numbers with commas
   - Works with or without country codes
5. **Save and Done!**

Messages from blocked numbers will now be silently ignored and not forwarded to any of your configured destinations.

### 📋 Use Cases

- **Block Spam Senders** - Stop forwarding messages from known spam numbers
- **Block Marketing Messages** - Filter out promotional SMS
- **Block Specific Contacts** - Prevent certain contacts' messages from being forwarded
- **Privacy Control** - Keep sensitive senders' messages local only

### 🛡️ Technical Details

- **No Performance Impact** - Efficient blocklist checking with minimal overhead
- **Reliable** - Uses same phone number normalization as existing forwarding logic
- **Configurable** - Can be enabled/disabled without affecting other settings
- **Persistent** - Blocklist settings are saved and survive app restarts

### 📊 Changes Summary

- ✅ Added blocklist UI in preferences
- ✅ Implemented phone number blocking logic
- ✅ Added smart phone number matching
- ✅ Integrated with all forwarding methods
- ✅ Added comprehensive logging

### 🔄 Compatibility

- **Android Version:** 7.0 (API 25) or higher
- **Upgrade:** Safe upgrade from v1.0.4 - all existing settings preserved
- **Storage:** Minimal additional space required
- **Performance:** No impact on battery or CPU usage

### 📦 Installation

#### Upgrade from Previous Version
1. Download `SMS-Forwarder-v1.0.5.apk`
2. Install over existing app (settings will be preserved)
3. Configure blocklist in settings

#### Fresh Installation
1. Download `SMS-Forwarder-v1.0.5.apk`
2. Enable "Install from Unknown Sources"
3. Install the APK
4. Grant required permissions
5. Configure forwarding and blocklist settings

### 🐛 Known Issues

None reported for this release.

### 🙏 Acknowledgments

Thank you to all users who requested this feature! Your feedback helps make SMS Forwarder better.

### 📝 Full Changelog

See [CHANGELOG.md](CHANGELOG.md) for complete version history.

---

**Download:** [SMS-Forwarder-v1.0.5.apk](releases/SMS-Forwarder-v1.0.5.apk)

**Repository:** [github.com/spinykiller/sms-forwarder](https://github.com/spinykiller/sms-forwarder)

**Issues/Support:** [GitHub Issues](https://github.com/spinykiller/sms-forwarder/issues)

