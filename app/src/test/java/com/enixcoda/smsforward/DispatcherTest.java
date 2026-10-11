package com.enixcoda.smsforward;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DispatcherTest {
    @Test
    public void wasRecentlySent_echoesButNotRepeats() {
        Dispatcher.remember("Message from WhatsApp (Ann):\nSee you at 8 tonight");
        // Our forward as Telegram shows it: whole, truncated preview, or with extra text around it
        assertTrue(Dispatcher.wasRecentlySent("Message from WhatsApp (Ann):\nSee you at 8 tonight"));
        assertTrue(Dispatcher.wasRecentlySent("Message from WhatsApp (Ann):\nSee you"));
        assertTrue(Dispatcher.wasRecentlySent("Bot: Message from WhatsApp (Ann):\nSee you at 8 tonight"));
        // The same words sent again by a person are a new message
        assertFalse(Dispatcher.wasRecentlySent("See you at 8 tonight"));
        assertFalse(Dispatcher.wasRecentlySent("Message"));
    }
}
