# SMS Forwarder v1.0.7 - Improved Telegram Implementation

**Release Date:** October 17, 2025

## 📱 Telegram Improvements

This release improves the Telegram forwarding implementation with better error handling and support for group/channel usernames.

### ✨ What's New

**Enhanced Telegram Integration**
- **Group Username Support** - Now supports @username format for Telegram groups and channels
- **Improved Error Handling** - Better logging and error messages for Telegram API failures
- **GET Request Method** - Uses simple GET requests with URL encoding (like standard Telegram API usage)
- **Better Response Handling** - Reads and logs both success and error responses from Telegram API
- **Connection Timeouts** - Configurable timeouts to prevent hanging requests

**Improved UI Documentation**
- Updated Telegram Chat ID field with examples showing both numeric and @username formats
- Added helpful summary text explaining how to get bot token from @BotFather
- Better field labels ("Bot Token" instead of just "API KEY")

### 🔧 Technical Details

**Telegram Implementation Changes**
- Rewritten `ForwardTaskForTelegram` class for better reliability
- Uses `URLEncoder` for proper URL encoding (equivalent to Python's `urllib.parse.quote_plus`)
- Comprehensive logging with TAG for easier debugging
- Proper connection cleanup with try-finally blocks
- Reads full response body for both success and error cases

**Supported Chat ID Formats**
- Numeric chat ID: `123456789`
- Group username: `@mygroup`
- Channel username: `@mychannel`

### 📋 How to Use

**Setting up Telegram Forwarding:**

1. **Create a Telegram Bot**
   - Message @BotFather on Telegram
   - Use `/newbot` command
   - Get your bot token (e.g., `110201543:AAH4eZxxxxxxxxxxxxxxxxxxxxxP4U`)

2. **Get Your Chat ID**
   - For personal chat: Use @userinfobot to get your chat ID
   - For groups: Add your bot to the group and use the @username (e.g., `@mygroup`)
   - For channels: Use the @channelname (e.g., `@mychannel`)

3. **Configure in App**
   - Enable Telegram forwarding
   - Enter your bot token in "Telegram Bot Token" field
   - Enter chat ID or @username in "Target Telegram Chat ID" field

### 🎯 Benefits

- ✅ More reliable Telegram message delivery
- ✅ Better error messages for troubleshooting
- ✅ Support for group/channel names (no need to find numeric IDs)
- ✅ Consistent with standard Telegram API usage patterns
- ✅ Easier debugging with detailed logs

### 🔄 Compatibility

- **Android Version:** 7.0 (API 25) or higher
- **Upgrade from v1.0.6:** Safe upgrade - includes all previous fixes
- **Telegram API:** Compatible with latest Telegram Bot API
- **Storage:** Same size (~1.9 MB)

### 📊 Changes Summary

- ✅ Rewrote Telegram sending logic for better reliability
- ✅ Added support for @username format for groups/channels
- ✅ Improved error handling and logging
- ✅ Updated UI with helpful examples and documentation
- ✅ Better connection management with timeouts

### 🐛 Bug Fixes

All fixes from v1.0.6 are included:
- ✅ Blocklist working correctly
- ✅ Empty blocked numbers handled properly

All features from previous versions remain:
- ✅ Sender blocklist
- ✅ Multiple forwarding methods
- ✅ Loop prevention
- ✅ Auto-start functionality

### 💡 Example Configuration

**For Group Forwarding:**
```
Bot Token: 110201543:AAH4eZxxxxxxxxxxxxxxxxxxxxxP4U
Chat ID: @mygroup
```

**For Personal Chat:**
```
Bot Token: 110201543:AAH4eZxxxxxxxxxxxxxxxxxxxxxP4U
Chat ID: 123456789
```

**For Error Channel:**
```
Bot Token: 110201543:AAH4eZxxxxxxxxxxxxxxxxxxxxxP4U
Chat ID: @mychannel
```

### 🙏 Acknowledgments

Thanks for the feedback on improving the Telegram integration!

---

**Download:** [SMS-Forwarder-v1.0.7.apk](releases/SMS-Forwarder-v1.0.7.apk)

**Repository:** [github.com/spinykiller/sms-forwarder](https://github.com/spinykiller/sms-forwarder)

**Issues/Support:** [GitHub Issues](https://github.com/spinykiller/sms-forwarder/issues)

