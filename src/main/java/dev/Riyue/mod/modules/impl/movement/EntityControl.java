package dev.Riyue.mod.modules.impl.movement;

import dev.Riyue.mod.modules.Module;

public class EntityControl extends Module {
    public static EntityControl INSTANCE;

    public EntityControl() {
        super("EntityControl", Category.Movement);
        setChinese("骑行控制");
        INSTANCE = this;
    }
}
