package com.enixcoda.smsforward;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PhoneNumberUtils {
    private static final String TAG = "PhoneNumberUtils";

    /**
     * Parse a comma-separated string of phone numbers into a list
     * @param phoneNumbersString Comma-separated phone numbers
     * @return List of cleaned phone numbers
     */
    public static List<String> parsePhoneNumbers(String phoneNumbersString) {
        List<String> phoneNumbers = new ArrayList<>();
        
        if (TextUtils.isEmpty(phoneNumbersString)) {
            return phoneNumbers;
        }
        
        // Split by comma and clean each number
        String[] numbers = phoneNumbersString.split(",");
        for (String number : numbers) {
            String cleanedNumber = cleanPhoneNumber(number.trim());
            if (!cleanedNumber.isEmpty()) {
                phoneNumbers.add(cleanedNumber);
            }
        }
        
        Log.d(TAG, "Parsed " + phoneNumbers.size() + " phone numbers: " + phoneNumbers);
        return phoneNumbers;
    }

    /**
     * Clean and normalize a phone number
     * @param phoneNumber Raw phone number
     * @return Cleaned phone number
     */
    public static String cleanPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }
        
        // Remove all non-digit characters except +
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");
        
        // Remove leading zeros and normalize
        if (cleaned.startsWith("+")) {
            return cleaned;
        } else if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        } else if (cleaned.length() > 10) {
            return "+" + cleaned;
        } else {
            return cleaned;
        }
    }

    /**
     * Check if a phone number matches any number in the list
     * @param senderNumber The sender's phone number
     * @param targetNumbers List of target phone numbers
     * @return true if sender number matches any target number
     */
    public static boolean isSenderInTargetList(String senderNumber, List<String> targetNumbers) {
        if (TextUtils.isEmpty(senderNumber) || targetNumbers.isEmpty()) {
            return false;
        }
        
        String cleanedSender = cleanPhoneNumber(senderNumber);
        
        for (String targetNumber : targetNumbers) {
            String cleanedTarget = cleanPhoneNumber(targetNumber);
            if (cleanedSender.equals(cleanedTarget)) {
                Log.d(TAG, "Sender " + senderNumber + " matches target " + targetNumber);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Format phone numbers list for display
     * @param phoneNumbers List of phone numbers
     * @return Formatted string for display
     */
    public static String formatPhoneNumbersForDisplay(List<String> phoneNumbers) {
        if (phoneNumbers.isEmpty()) {
            return "No numbers configured";
        }
        
        if (phoneNumbers.size() == 1) {
            return phoneNumbers.get(0);
        }
        
        return phoneNumbers.size() + " numbers configured";
    }

    /**
     * Check if any forwarding numbers are configured
     * @param phoneNumbers List of phone numbers
     * @return true if at least one number is configured
     */
    public static boolean hasValidNumbers(List<String> phoneNumbers) {
        return phoneNumbers != null && !phoneNumbers.isEmpty();
    }

    /**
     * Check if a sender number is in the blocklist
     * @param senderNumber The sender's phone number
     * @param blockedNumbers List of blocked phone numbers
     * @return true if sender number is blocked
     */
    public static boolean isSenderBlocked(String senderNumber, List<String> blockedNumbers) {
        if (TextUtils.isEmpty(senderNumber) || blockedNumbers == null || blockedNumbers.isEmpty()) {
            return false;
        }
        
        String cleanedSender = cleanPhoneNumber(senderNumber);
        if (TextUtils.isEmpty(cleanedSender)) {
            return false;
        }
        
        for (String blockedNumber : blockedNumbers) {
            String cleanedBlocked = cleanPhoneNumber(blockedNumber);
            
            // Skip empty blocked numbers
            if (TextUtils.isEmpty(cleanedBlocked)) {
                continue;
            }
            
            // Exact match
            if (cleanedSender.equals(cleanedBlocked)) {
                Log.d(TAG, "Sender " + senderNumber + " is blocked (exact match with " + blockedNumber + ")");
                return true;
            }
            
            // Partial match: check if numbers match at the end (for cases where country code might be missing)
            // Only do partial matching if both numbers are at least 7 digits (minimum valid phone number)
            if (cleanedBlocked.length() >= 7 && cleanedSender.length() >= 7) {
                if (cleanedSender.endsWith(cleanedBlocked) || cleanedBlocked.endsWith(cleanedSender)) {
                    Log.d(TAG, "Sender " + senderNumber + " is blocked (partial match with " + blockedNumber + ")");
                    return true;
                }
            }
        }
        
        return false;
    }
}
