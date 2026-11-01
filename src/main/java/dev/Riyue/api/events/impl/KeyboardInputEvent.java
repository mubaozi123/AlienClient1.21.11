/*
 * Decompiled with CFR 0.152.
 */
package dev.Riyue.api.events.impl;

import dev.Riyue.api.events.Event;

public class KeyboardInputEvent
extends Event {
    private static final KeyboardInputEvent INSTANCE = new KeyboardInputEvent();

    private KeyboardInputEvent() {
    }

    public static KeyboardInputEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}

