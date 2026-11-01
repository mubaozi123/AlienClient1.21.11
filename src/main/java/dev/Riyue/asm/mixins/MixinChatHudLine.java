package dev.Riyue.asm.mixins;

import dev.Riyue.api.interfaces.IChatHudLineHook;
import dev.Riyue.api.utils.math.FadeUtils;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChatHudLine.class)
public class MixinChatHudLine implements IChatHudLineHook {
    @Unique
    private int id = 0;
    @Unique
    private boolean sync = false;
    @Unique
    private FadeUtils fade;

    @Override
    public int riyueClient$getMessageId() {
        return id;
    }

    @Override
    public void riyueClient$setMessageId(int id) {
        this.id = id;
    }

    @Override
    public boolean riyueClient$getSync() {
        return sync;
    }

    @Override
    public void riyueClient$setSync(boolean sync) {
        this.sync = sync;
    }

    @Override
    public FadeUtils riyueClient$getFade() {
        return fade;
    }

    @Override
    public void riyueClient$setFade(FadeUtils fade) {
        this.fade = fade;
    }
}
