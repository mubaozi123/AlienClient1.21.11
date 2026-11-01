package dev.luminous.mod.modules.impl.combat;

import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;

public class AutoTK extends Module {
    private final BooleanSetting notify = add(new BooleanSetting("Notify", true));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));

    // 静态标志位，表示是否忽略好友判断
    private static boolean ignoringFriends = false;

    public static boolean isIgnoringFriends() {
        return ignoringFriends;
    }

    public AutoTK() {
        super("AutoTK", Category.Combat);
        this.setChinese("自动背刺");
    }

    @Override
    public void onEnable() {
        // 先置标志再通知，避免其它模块在通知回调中读到旧值
        ignoringFriends = true;
        super.onEnable();

        if (notify.getValue()) {
            CommandManager.sendMessage("§aNow ignoring friends for team killing!");
        }
    }

    @Override
    public void onDisable() {
        // 先恢复好友保护，保证后续逻辑（含通知回调）读到安全状态
        ignoringFriends = false;
        super.onDisable();

        if (notify.getValue()) {
            CommandManager.sendMessage("§aRestored friend protection!");
        }
    }

    @Override
    public void onLogout() {
        if (autoDisable.getValue()) {
            disable();
        }
    }
}
