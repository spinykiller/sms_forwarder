# SMS Forwarder

A lightweight Android application that automatically forwards SMS messages to multiple recipients via SMS, Telegram, or webhooks. Optimized for minimal battery usage and designed to work reliably across different Android manufacturers.

## 🚀 Features

### 📱 Core Functionality
- **Multi-Channel Forwarding**: Forward SMS via SMS, Telegram, or webhooks
- **Multiple Recipients**: Support for multiple phone numbers (comma-separated, Pro)
- **Reverse Messaging**: Send messages back to original sender
- **Loop Prevention**: Prevents forwarding messages to the same number
- **Contact Integration**: Shows sender names from contacts (optional permission)
- **Long Messages**: Multipart SMS are forwarded as one message
- **Offline Retry**: Telegram/webhook forwards that fail while offline are retried once the network is back
- **Dual SIM**: Forwards and replies on the SIM that received the message

### 💎 Free with ads, or Pro
- Free: SMS (one number), Telegram, webhook (plain/Slack/Discord/Teams) and email (one address) forwarding, blocklist, reverse messaging, pause switch with Quick Settings tile and widget, settings export/import, light/dark theme, 6 languages — with one banner on the settings screen
- Pro (yearly with free trial, monthly, or lifetime): no ads, app notification forwarding, routing rules, OTP-only mode, keyword (incl. /regex/) and sender filters, custom message format, multiple numbers and email addresses, forward log with delivery reports and retry, quiet hours, roaming pause, SIM choice, daily health check and low-battery alert, app lock

### 🔋 Battery & Performance Optimized
- **No Background Service**: Android wakes the app only when an SMS arrives — no persistent notification, no polling

### 🛡️ Reliability Features
- **Battery Optimization Handling**: Works with aggressive battery managers
- **Device-Specific Support**: Optimized for MIUI, EMUI, ColorOS, FuntouchOS

### 🎨 User Experience
- **Dark Theme**: Modern dark UI with excellent visibility
- **Permission Management**: Automatic permission requests with user guidance
- **Device-Specific Instructions**: Tailored setup guides for different manufacturers
- **Status Monitoring**: Real-time status display in settings

## 📋 Requirements

- **Android Version**: 7.0 (API 25) or higher
- **Permissions**: SMS (Send/Receive), Internet, Contacts (optional)
- **Storage**: ~2MB APK size

## 🛠️ Installation

### Option 1: Download APK
1. Download the latest APK from [Releases](https://github.com/spinykiller/sms_forwarder/releases)
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK

### Option 2: Build from Source
```bash
git clone https://github.com/spinykiller/sms_forwarder.git
cd sms_forwarder/SMS-Forward
./gradlew assembleRelease
```

## ⚙️ Configuration

### 1. Grant Permissions
The app will automatically request necessary permissions:
- **SMS Permissions**: Required for forwarding messages
- **Internet Permission**: Required for Telegram/webhook forwarding
- **Contacts Permission**: Optional, shows sender names

### 2. Configure Forwarding Methods

#### SMS Forwarding
- Enable "Forward via SMS"
- Enter target phone numbers (comma-separated)
- Example: `+1234567890, +9876543210`

#### Telegram Forwarding
- Enable "Forward via Telegram"
- Enter your Telegram Chat ID
- Enter your Telegram Bot Token

#### Webhook Forwarding
- Enable "Forward via Web"
- Enter your webhook URL
- Messages will be sent as POST requests

### 3. Device-Specific Setup

#### For Poco/Xiaomi Devices
1. Go to Settings > Apps > Manage apps > SMS Forward
2. Enable 'Autostart' permission
3. Go to Settings > Battery & performance > Battery saver
4. Add SMS Forward to 'No restrictions'
5. Enable all SMS permissions

#### For Huawei/Honor Devices
1. Go to Settings > Apps > SMS Forward
2. Enable 'Autostart' permission
3. Go to Settings > Battery > App launch
4. Set SMS Forward to 'Manual'
5. Enable all required permissions

#### For OPPO/OnePlus Devices
1. Go to Settings > Apps > SMS Forward
2. Enable 'Autostart' permission
3. Go to Settings > Battery > Background app management
4. Set SMS Forward to 'Allow'
5. Enable all required permissions

#### For Vivo Devices
1. Go to Settings > Apps > SMS Forward
2. Enable 'Autostart' permission
3. Go to Settings > Battery > Background app management
4. Set SMS Forward to 'Allow'
5. Enable all required permissions

## 🔧 Technical Details

### Architecture
- **MainActivity**: Permission management and UI
- **SMSReceiver**: BroadcastReceiver for incoming SMS
- **Forwarder**: Sends via SMS, Telegram and webhooks
- **ForwardWorker**: WorkManager job that retries failed Telegram/webhook forwards
- **AutoStartHelper**: Device-specific auto-start management
- **BatteryOptimizationHelper**: Battery optimization handling

### Performance Optimizations
- **Event-Driven**: Runs only when an SMS arrives; retries use WorkManager with a network constraint
- **Reduced Checks**: Eliminated redundant permission checks
- **Simplified Logic**: Streamlined auto-start detection

### Security
- **No External Communication**: Only communicates with configured endpoints
- **Local Processing**: All SMS processing happens locally
- **Permission-Based**: Only requests necessary permissions

## 📱 Supported Devices

### Tested Manufacturers
- **Xiaomi/Poco/Redmi**: Full support with MIUI optimizations
- **Huawei/Honor**: EMUI-specific optimizations
- **OPPO/OnePlus**: ColorOS-specific optimizations
- **Vivo**: FuntouchOS-specific optimizations
- **Samsung**: Generic Android support
- **Google Pixel**: Generic Android support

### Android Versions
- **Android 7.0+**: Full feature support
- **Android 10+**: Scoped storage compatibility

## 🐛 Troubleshooting

### Not Forwarding After Reboot
1. Check auto-start permissions in device settings
2. Disable battery optimization for the app
3. Enable background activity permissions
4. For MIUI devices, check Security Center settings

### SMS Not Being Forwarded
1. Verify SMS permissions are granted
2. Check if forwarding is enabled in app settings
3. Ensure target numbers are correctly formatted
4. Check device-specific battery settings

### High Battery Usage
1. Ensure you're using the latest optimized version
2. Check for conflicting apps

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/spinykiller/sms_forwarder/issues)
- **Discussions**: [GitHub Discussions](https://github.com/spinykiller/sms_forwarder/discussions)

## 🔄 Changelog

### Version 1.0.2 (Latest)
- ✅ Dark theme implementation
- ✅ Performance optimizations (80% CPU reduction)
- ✅ Battery usage optimization
- ✅ Auto-start permission management
- ✅ Device-specific optimizations
- ✅ Multiple recipient support
- ✅ Loop prevention
- ✅ Enhanced reliability features

### Version 1.0.1
- ✅ Basic SMS forwarding
- ✅ Telegram integration
- ✅ Webhook support
- ✅ Battery optimization handling

## 🙏 Acknowledgments

- Original SMS Forward project by EnixCoda
- Android community for best practices
- Contributors and testers

---

**Note**: This app requires SMS permissions to function. It does not collect or transmit personal data except to your configured forwarding destinations.