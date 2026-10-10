package com.enixcoda.smsforward;

import java.util.List;
import java.util.Locale;

/** Pro message rules: which messages to forward and how forwarded text is formatted. */
final class MessageRules {
    // Placeholders: {from} = "Name (number)" or "(number)", {sender} = number or sender ID, {name} = contact name, {message}
    static final String DEFAULT_SMS_FORMAT = "From {from}:\n{message}";
    static final String DEFAULT_TELEGRAM_FORMAT = "Message from {from}:\n{message}";

    private MessageRules() {
    }

    /** Forward only if the body contains any keyword (when set) AND the sender matches any listed sender (when set). */
    static boolean matchesFilter(String sender, String body, List<String> keywords, List<String> senders) {
        boolean keywordMatch = keywords.isEmpty();
        String lowerBody = body.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            keywordMatch |= lowerBody.contains(keyword.toLowerCase(Locale.ROOT));
        }
        return keywordMatch && (senders.isEmpty() || PhoneNumberUtils.senderMatchesAny(sender, senders));
    }

    static String format(String template, String from, String sender, String name, String message) {
        // {message} last, so placeholder-like text inside the SMS is left as is
        return template.replace("{from}", from).replace("{sender}", sender).replace("{name}", name)
                .replace("{message}", message);
    }
}
