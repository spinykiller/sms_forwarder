package com.enixcoda.smsforward;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PhoneNumberUtilsTest {
    @Test
    public void splitList_trimsAndDropsEmpty() {
        assertEquals(Arrays.asList("+1 555", "HDFCBK"), PhoneNumberUtils.splitList(" +1 555 , ,HDFCBK,"));
        assertTrue(PhoneNumberUtils.splitList(null).isEmpty());
    }

    @Test
    public void parsePhoneNumbers_cleansAndDropsNonNumeric() {
        assertEquals(Arrays.asList("+8610000", "+15551234567"),
                PhoneNumberUtils.parsePhoneNumbers("+86 10000, 1 555 123 4567, HDFCBK"));
    }

    @Test
    public void numbersMatch_localVsInternational() {
        assertTrue(PhoneNumberUtils.numbersMatch("+919876543210", "98765 43210"));
        assertTrue(PhoneNumberUtils.numbersMatch("0091 98765 43210", "+919876543210"));
        assertFalse(PhoneNumberUtils.numbersMatch("+919876543210", "+919876543211"));
        // Short codes only match exactly
        assertFalse(PhoneNumberUtils.numbersMatch("12345", "2345"));
        assertFalse(PhoneNumberUtils.numbersMatch("HDFCBK", "HDFCBK"));
    }

    @Test
    public void isSenderInTargetList_matchesAcrossFormats() {
        assertTrue(PhoneNumberUtils.isSenderInTargetList("+15551234567", Collections.singletonList("5551234567")));
        assertFalse(PhoneNumberUtils.isSenderInTargetList("+15551234568", Collections.singletonList("5551234567")));
    }

    @Test
    public void isSenderBlocked_numbersAndSenderNames() {
        assertTrue(PhoneNumberUtils.isSenderBlocked("+919876543210", Collections.singletonList("9876543210")));
        assertTrue(PhoneNumberUtils.isSenderBlocked("AX-HDFCBK", Collections.singletonList("hdfcbk")));
        assertFalse(PhoneNumberUtils.isSenderBlocked("AX-ICICIB", Collections.singletonList("HDFCBK")));
        assertFalse(PhoneNumberUtils.isSenderBlocked("AX-HDFCBK", Collections.singletonList("12345")));
        assertFalse(PhoneNumberUtils.isSenderBlocked(null, Collections.singletonList("HDFCBK")));
    }
}
