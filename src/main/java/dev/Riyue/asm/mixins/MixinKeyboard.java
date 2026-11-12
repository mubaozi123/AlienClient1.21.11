package dev.Riyue.asm.mixins;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.Wrapper;
import dev.Riyue.core.impl.CommandManager;
import dev.Riyue.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Keyboard.class})
public class MixinKeyboard
        implements Wrapper {
    @Inject(method={"onKey"}, at={@At(value="HEAD")})
    private void onKey(long windowPointer, int action, KeyInput input, CallbackInfo ci) {
        block4: {
            try {
                int key = input.key();
                if (action == 1) {
                    RiyueClient.MODULE.onKeyPressed(key);
                }
                if (action == 0) {
                    RiyueClient.MODULE.onKeyReleased(key);
                }
            }
            catch (Throwable e) {
                // onKey 里除了事件总线还直接调用 ModuleManager.onKeyPressed/onKeyReleased，
                // 模块内部抛出的 LinkageError 同样不能被 catch(Exception) 漏掉。
                RiyueClient.rethrowFatal(e);
                e.printStackTrace();
                if (ClientSetting.INSTANCE == null || !ClientSetting.INSTANCE.debug.getValue()) break block4;
                CommandManager.sendMessage(String.valueOf(Formatting.DARK_RED) + "[ERROR] onKey " + e.getMessage());
            }
        }
    }

    @Redirect(method={"onKey"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/util/NarratorManager;isActive()Z"), require=0)
    public boolean hook(NarratorManager instance) {
        return false;
    }
}

