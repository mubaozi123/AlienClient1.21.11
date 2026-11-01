package dev.Riyue.asm.mixins;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.impl.TimerEvent;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={RenderTickCounter.Dynamic.class})
public class MixinRenderTickCounter {
    @Shadow
    private float dynamicDeltaTicks;
    @Shadow
    private float tickProgress;
    @Shadow
    private long lastTimeMillis;
    @Final
    @Shadow
    private float tickTime;

    @Inject(method={"beginRenderTick(J)I"}, at={@At(value="HEAD")}, cancellable=true)
    private void beginRenderTickHook(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        TimerEvent event = TimerEvent.getEvent();
        RiyueClient.EVENT_BUS.post(event);
        if (!event.isCancelled()) {
            float timer = event.isModified() ? event.get() : (RiyueClient.TIMER != null ? RiyueClient.TIMER.get() : 1.0f);
            if (timer == 1.0f) {
                return;
            }
            this.dynamicDeltaTicks = (float)(timeMillis - this.lastTimeMillis) / this.tickTime * timer;
            this.lastTimeMillis = timeMillis;
            this.tickProgress += this.dynamicDeltaTicks;
            int i = (int)this.tickProgress;
            this.tickProgress -= (float)i;
            cir.setReturnValue(i);
        }
    }
}


