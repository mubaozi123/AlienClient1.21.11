package dev.Riyue.mod.modules.impl.misc;

import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.DeathEvent;
import dev.Riyue.api.events.impl.UpdateEvent;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.StringSetting;

public class AutoKit extends Module {
    public static AutoKit INSTANCE;

    final StringSetting command = add(new StringSetting("Command", "kit 1"));

    public AutoKit() {
        super("AutoKit", Category.Misc);
        setChinese("自动配装命令");
        INSTANCE = this;
    }

    boolean kit = false;
    final Timer timer = new Timer();

    @Override
    public void onLogin() {
        kit = true;
        timer.reset();
    }

    @EventListener
    public void onDeath(DeathEvent event) {
        if (event.getPlayer() == mc.player) {
            kit = true;
            timer.reset();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (kit) {
            if (timer.passedS(2)) {
                kit = false;
                mc.player.networkHandler.sendChatCommand(command.getValue());
            }
        }
    }
}
