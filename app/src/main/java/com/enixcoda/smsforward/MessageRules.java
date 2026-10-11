package com.enixcoda.smsforward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Pro message rules: which messages to forward and how forwarded text is formatted. */
final class MessageRules {
    // Placeholders: {from} = "Name (number)" or "(number)", {sender} = number or sender ID, {name} = contact name, {message}
    static final String DEFAULT_SMS_FORMAT = "From {from}:\n{message}";
    static final String DEFAULT_TELEGRAM_FORMAT = "Message from {from}:\n{message}";

    private MessageRules() {
    }

    // A code of 4–8 digits next to a word that marks it as one (otp, code, pin, …)
    private static final Pattern OTP_CODE = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");
    private static final Pattern OTP_WORD = Pattern.compile(
            "\\b(otp|code|verification|verify|pin|passcode|password|one[ -]?time|2fa|login|auth)", Pattern.CASE_INSENSITIVE);

    /**
     * Forward only if the body contains any keyword (when set) AND the sender matches any listed sender (when set).
     * A keyword written as /regex/ is matched as a case-insensitive regular expression.
     */
    static boolean matchesFilter(String sender, String body, List<String> keywords, List<String> senders) {
        boolean keywordMatch = keywords.isEmpty();
        String lowerBody = body.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            keywordMatch |= isRegex(keyword) ? findRegex(keyword, body) : lowerBody.contains(keyword.toLowerCase(Locale.ROOT));
        }
        return keywordMatch && (senders.isEmpty() || PhoneNumberUtils.senderMatchesAny(sender, senders));
    }

    static boolean isOtp(String body) {
        return OTP_CODE.matcher(body).find() && OTP_WORD.matcher(body).find();
    }

    static boolean isRegex(String keyword) {
        return keyword.length() > 2 && keyword.startsWith("/") && keyword.endsWith("/");
    }

    /** null if every /regex/ keyword compiles, else the first invalid one (for settings validation). */
    static String invalidRegex(List<String> keywords) {
        for (String keyword : keywords) {
            if (!isRegex(keyword)) continue;
            try {
                Pattern.compile(keyword.substring(1, keyword.length() - 1));
            } catch (PatternSyntaxException e) {
                return keyword;
            }
        }
        return null;
    }

    private static boolean findRegex(String keyword, String body) {
        try {
            return Pattern.compile(keyword.substring(1, keyword.length() - 1), Pattern.CASE_INSENSITIVE).matcher(body).find();
        } catch (PatternSyntaxException e) {
            return false; // rejected in settings; a stale invalid one just never matches
        }
    }

    /** Comma-separated keywords; commas inside /regex/ don't split (an escaped \/ doesn't close it). */
    static List<String> splitKeywords(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inRegex = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean escaped = i > 0 && s.charAt(i - 1) == '\\';
            if (c == '/' && !escaped) {
                if (current.toString().trim().isEmpty()) inRegex = true;
                else if (inRegex) inRegex = false;
            }
            if (c == ',' && !inRegex) {
                if (!current.toString().trim().isEmpty()) out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().trim().isEmpty()) out.add(current.toString().trim());
        return out;
    }

    static String format(String template, String from, String sender, String name, String message) {
        // {message} last, so placeholder-like text inside the SMS is left as is
        return template.replace("{from}", from).replace("{sender}", sender).replace("{name}", name)
                .replace("{message}", message);
    }
}
