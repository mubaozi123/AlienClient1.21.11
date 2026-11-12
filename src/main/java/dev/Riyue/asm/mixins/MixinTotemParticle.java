package dev.Riyue.asm.mixins;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.impl.TotemParticleEvent;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(TotemParticle.class)
public abstract class MixinTotemParticle extends MixinParticle {

    /** 只打印一次，避免极端情况下每个粒子刷一屏日志。 */
    @Unique
    private static boolean riyueHookWarned;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void hookInit(ClientWorld world, double x, double y, double z, double velocityX,
                          double velocityY, double velocityZ, SpriteProvider spriteProvider, CallbackInfo ci) {
        // 防御性兜底：粒子是在网络包处理线程里创建的，这里抛出的任何异常/错误都会让
        // ClientPlayNetworkHandler 触发 "Failed to handle packet ..., disconnecting"（直接掉线）。
        // 自定义颜色属于纯装饰功能，绝不能因为它的失败把玩家踢出服务器。
        // 捕获 Throwable 而非 Exception，才能同时兜住 AbstractMethodError / NoClassDefFoundError。
        try {
            TotemParticleEvent event = TotemParticleEvent.get(velocityX, velocityY, velocityZ);
            RiyueClient.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                this.velocityX = event.velocityX;
                this.velocityY = event.velocityY;
                this.velocityZ = event.velocityZ;
                Color color = event.color;
                if (color != null) {
                    // 1.21.11: setColor(FFF) 是 BillboardParticle 上的 public 方法，不在 Particle 基类。
                    // 直接调用真实方法即可；不能用 @Shadow，因为 Mixin 只在目标类自身查找 shadow 方法，
                    // 不会沿父类链找到 BillboardParticle（会抛 InvalidMixinException "No refMap loaded"）。
                    // setAlpha(float) 是 protected，无法从这里直接调用；旧实现里的 setAlpha 本来就是空方法，
                    // 从未生效，因此这里保持粒子默认 alpha。
                    ((BillboardParticle) (Object) this).setColor(
                            color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
                }
            }
        } catch (Throwable t) {
            // 降级为原版图腾粒子（默认颜色与速度），游戏继续正常运行。
            if (!riyueHookWarned) {
                riyueHookWarned = true;
                System.err.println("[RiyueClient] Totem particle hook ignored to avoid disconnect: " + t);
            }
        }
    }
}
