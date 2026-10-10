package com.enixcoda.smsforward;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MessageRulesTest {
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void matchesFilter_emptyListsForwardEverything() {
        assertTrue(MessageRules.matchesFilter("+15551234567", "hello", NONE, NONE));
    }

    @Test
    public void matchesFilter_keywordsAreCaseInsensitiveAny() {
        List<String> keywords = Arrays.asList("otp", "code");
        assertTrue(MessageRules.matchesFilter("x", "Your OTP is 1234", keywords, NONE));
        assertFalse(MessageRules.matchesFilter("x", "Lunch?", keywords, NONE));
    }

    @Test
    public void matchesFilter_requiresBothKeywordAndSender() {
        List<String> keywords = Collections.singletonList("otp");
        List<String> senders = Collections.singletonList("HDFCBK");
        assertTrue(MessageRules.matchesFilter("AX-HDFCBK", "OTP 1234", keywords, senders));
        assertFalse(MessageRules.matchesFilter("AX-ICICIB", "OTP 1234", keywords, senders));
        assertFalse(MessageRules.matchesFilter("AX-HDFCBK", "Statement ready", keywords, senders));
    }

    @Test
    public void format_defaultsMatchPreviousOutput() {
        assertEquals("From Mom (+1555):\nhi",
                MessageRules.format(MessageRules.DEFAULT_SMS_FORMAT, "Mom (+1555)", "+1555", "Mom", "hi"));
        assertEquals("Message from (+1555):\nhi",
                MessageRules.format(MessageRules.DEFAULT_TELEGRAM_FORMAT, "(+1555)", "+1555", "", "hi"));
    }

    @Test
    public void format_leavesPlaceholdersInsideMessage() {
        assertEquals("+1555: say {name}", MessageRules.format("{sender}: {message}", "(+1555)", "+1555", "Mom", "say {name}"));
    }
}
