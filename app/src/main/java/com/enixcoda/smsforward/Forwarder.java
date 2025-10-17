package com.enixcoda.smsforward;

import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;

public class Forwarder {
    // Standard SMS length is ~160 characters for GSM-7, ~70 for Unicode
    // We use a conservative limit to account for encoding variations
    static final int MAX_SMS_LENGTH = 140;

    public static void sendSMS(String number, String content) {
        SmsManager smsManager = SmsManager.getDefault();
        
        // Check if message needs to be split
        ArrayList<String> parts = smsManager.divideMessage(content);
        if (parts.size() > 1) {
            // Send as multi-part SMS (Android handles it properly)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null);
        } else {
            // Send as single SMS
            smsManager.sendTextMessage(number, null, content, null, null);
        }
    }
    
    /**
     * Ensures that the message content doesn't start with a number to prevent SMS delivery failures.
     * Some carriers reject or fail to deliver SMS messages that start with digits.
     */
    private static String sanitizeMessageContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Check if the message starts with a digit
        if (Character.isDigit(content.charAt(0))) {
            // Prepend text to prevent delivery issues
            return "Msg: " + content;
        }
        
        return content;
    }

    public static void forwardViaSMS(String senderNumber, String forwardContent, String forwardNumber) {
        String forwardPrefix = String.format("From %s:\n", senderNumber);
        
        // Sanitize the content to prevent issues with numbers at the start
        String sanitizedContent = sanitizeMessageContent(forwardContent);
        String fullMessage = forwardPrefix + sanitizedContent;

        try {
            // Use Android's built-in multi-part SMS handling which properly splits messages
            // and maintains the connection between parts
            sendSMS(forwardNumber, fullMessage);
        } catch (RuntimeException e) {
            Log.e(Forwarder.class.toString(), "Error forwarding SMS: " + e.toString(), e);
        }
    }

    public static void forwardViaTelegram(String senderNumber, String message, String targetTelegramID, String telegramToken) {
        new ForwardTaskForTelegram(senderNumber, message, targetTelegramID, telegramToken).execute();
    }
    public static void forwardViaWeb(String senderNumber, String message, String endpoint) {
        new ForwardTaskForWeb(senderNumber, message, endpoint).execute();
    }
}
