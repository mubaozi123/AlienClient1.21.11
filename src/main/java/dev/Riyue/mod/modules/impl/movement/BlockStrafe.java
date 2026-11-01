package dev.Riyue.mod.modules.impl.movement;

import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.MoveEvent;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.player.MovementUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.combat.AutoAnchor;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;

public class BlockStrafe extends Module {
    public static BlockStrafe INSTANCE;
    private final SliderSetting speed =
            add(new SliderSetting("Speed", 10, 0, 20, 1).setSuffix("%"));
    private final SliderSetting aSpeed =
            add(new SliderSetting("AnchorSpeed", 3, 0, 20, 1).setSuffix("%"));

    public BlockStrafe() {
        super("BlockStrafe", Category.Movement);
        setChinese("方块灵活移动");
        INSTANCE = this;
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (!EntityUtil.isInsideBlock()) {
            return;
        }
        double speed = AutoAnchor.INSTANCE.currentPos == null ? this.speed.getValue() : this.aSpeed.getValue();
        double moveSpeed = 0.002873 * speed;
        double n = (double)MovementUtil.getMovementForward();
        double n2 = (double) MovementUtil.getMovementSideways();
        double n3 = BlockStrafe.mc.player.getYaw();
        if (n == 0.0 && n2 == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        }
        if (n != 0.0 && n2 != 0.0) {
            n *= Math.sin(0.7853981633974483);
            n2 *= Math.cos(0.7853981633974483);
        }
        event.setX(n * moveSpeed * -Math.sin(Math.toRadians(n3)) + n2 * moveSpeed * Math.cos(Math.toRadians(n3)));
        event.setZ(n * moveSpeed * Math.cos(Math.toRadians(n3)) - n2 * moveSpeed * -Math.sin(Math.toRadians(n3)));
    }
}
