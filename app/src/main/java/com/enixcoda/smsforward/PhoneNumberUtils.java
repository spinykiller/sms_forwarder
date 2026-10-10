package com.enixcoda.smsforward;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Uses plain null/empty checks instead of TextUtils so it runs in JVM unit tests.
public class PhoneNumberUtils {
    private static final String TAG = "PhoneNumberUtils";
    // Minimum digits of the local number for suffix matching (e.g. "9876543210" vs "+919876543210");
    // shorter local numbers would match unrelated senders that merely end the same way
    private static final int MIN_SUFFIX_MATCH_DIGITS = 9;

    /**
     * Split a comma-separated string into trimmed, non-empty entries
     * @param listString Comma-separated values
     * @return List of raw entries
     */
    public static List<String> splitList(String listString) {
        List<String> entries = new ArrayList<>();
        if (isEmpty(listString)) {
            return entries;
        }
        for (String entry : listString.split(",")) {
            if (!entry.trim().isEmpty()) {
                entries.add(entry.trim());
            }
        }
        return entries;
    }

    /**
     * Parse a comma-separated string of phone numbers into a list
     * @param phoneNumbersString Comma-separated phone numbers
     * @return List of cleaned phone numbers
     */
    public static List<String> parsePhoneNumbers(String phoneNumbersString) {
        List<String> phoneNumbers = new ArrayList<>();
        for (String number : splitList(phoneNumbersString)) {
            String cleanedNumber = cleanPhoneNumber(number);
            if (!cleanedNumber.isEmpty()) {
                phoneNumbers.add(cleanedNumber);
            }
        }

        Log.d(TAG, "Parsed " + phoneNumbers.size() + " phone numbers");
        return phoneNumbers;
    }

    /**
     * Clean and normalize a phone number
     * @param phoneNumber Raw phone number
     * @return Cleaned phone number
     */
    public static String cleanPhoneNumber(String phoneNumber) {
        if (isEmpty(phoneNumber)) {
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
     * Check if two phone numbers refer to the same number: exact match after cleaning, or an international
     * number (+...) ending with a local number of enough digits (country code missing on one side)
     */
    public static boolean numbersMatch(String a, String b) {
        String cleanedA = cleanPhoneNumber(a);
        String cleanedB = cleanPhoneNumber(b);
        if (cleanedA.isEmpty() || cleanedB.isEmpty()) {
            return false;
        }
        if (cleanedA.equals(cleanedB)) {
            return true;
        }
        String international = cleanedA.length() > cleanedB.length() ? cleanedA : cleanedB;
        String local = international == cleanedA ? cleanedB : cleanedA;
        return international.startsWith("+") && !local.startsWith("+")
                && local.length() >= MIN_SUFFIX_MATCH_DIGITS && international.endsWith(local);
    }

    /**
     * Check if a phone number matches any number in the list
     * @param senderNumber The sender's phone number
     * @param targetNumbers List of target phone numbers
     * @return true if sender number matches any target number
     */
    public static boolean isSenderInTargetList(String senderNumber, List<String> targetNumbers) {
        for (String targetNumber : targetNumbers) {
            if (numbersMatch(senderNumber, targetNumber)) {
                Log.d(TAG, "Sender matches a target number");
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
     * Check if a sender is in the blocklist
     * @param senderNumber The sender's phone number or alphanumeric sender ID
     * @param blockedEntries Raw blocklist entries: phone numbers, or sender names such as "HDFCBK"
     *                       (a name matches any sender ID containing it, e.g. "AX-HDFCBK")
     * @return true if sender is blocked
     */
    public static boolean isSenderBlocked(String senderNumber, List<String> blockedEntries) {
        boolean blocked = senderMatchesAny(senderNumber, blockedEntries);
        if (blocked) Log.d(TAG, "Sender is blocked");
        return blocked;
    }

    /**
     * Check if a sender matches any entry: a phone number (see {@link #numbersMatch}) or a sender
     * name such as "HDFCBK", which matches any sender ID containing it (e.g. "AX-HDFCBK")
     */
    public static boolean senderMatchesAny(String senderNumber, List<String> entries) {
        if (isEmpty(senderNumber) || entries == null) {
            return false;
        }

        String sender = senderNumber.toLowerCase(Locale.ROOT);
        for (String entry : entries) {
            boolean isName = entry.matches(".*[A-Za-z].*");
            if (isName ? sender.contains(entry.toLowerCase(Locale.ROOT)) : numbersMatch(senderNumber, entry)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
