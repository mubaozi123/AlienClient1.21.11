package dev.Riyue.mod.modules.impl.player;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.PacketEvent;
import dev.Riyue.api.events.impl.UpdateEvent;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

public class PacketEat extends Module {
    public static PacketEat INSTANCE;
    private final BooleanSetting deSync =
            add(new BooleanSetting("DeSync", false));
    private final BooleanSetting noRelease =
            add(new BooleanSetting("NoRelease", true));

    public PacketEat() {
        super("PacketEat", Category.Player);
        setChinese("发包进食");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (deSync.getValue() && mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getComponents().contains(DataComponentTypes.FOOD)) {
            Module.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(mc.player.getActiveHand(), id, RiyueClient.ROTATION.getLastYaw(), RiyueClient.ROTATION.getLastPitch()));
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (noRelease.getValue() && event.getPacket() instanceof PlayerActionC2SPacket packet && packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && mc.player.getActiveItem().getItem().getComponents().contains(DataComponentTypes.FOOD)) {
            event.cancel();
        }
    }
}