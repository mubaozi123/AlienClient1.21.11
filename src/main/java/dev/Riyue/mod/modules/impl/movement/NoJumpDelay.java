package dev.Riyue.mod.modules.impl.movement;

import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.UpdateEvent;
import dev.Riyue.asm.accessors.ILivingEntity;
import dev.Riyue.mod.modules.Module;

public class NoJumpDelay
        extends Module {
    public static NoJumpDelay INSTANCE;

    public NoJumpDelay() {
        super("NoJumpDelay", Category.Movement);
        setChinese("无跳跃冷却");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        ((ILivingEntity) mc.player).setLastJumpCooldown(0);
    }
}
