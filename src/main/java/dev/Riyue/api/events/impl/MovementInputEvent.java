package dev.Riyue.api.events.impl;

import dev.Riyue.api.events.Event;

public class MovementInputEvent
extends Event {
    private static final MovementInputEvent INSTANCE = new MovementInputEvent();

    private MovementInputEvent() {
    }

    public static MovementInputEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
