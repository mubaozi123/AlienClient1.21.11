package dev.Riyue.asm.mixins;

import dev.Riyue.RiyueClient;
import dev.Riyue.mod.modules.impl.render.Ambience;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;

/**
 * 1.21.11 雾色覆盖。
 *
 * 背景：1.21.11 把雾改成了雾 UBO（{@code RenderSystem.setShaderFog(GpuBufferSlice)}），
 * 旧的 {@code RenderSystem.setShaderFogColor/Start/End} 全部被移除，
 * {@code BackgroundRenderer} 也被拆成 {@code SkyRendering} + {@code FogRenderer}。
 *
 * 雾色在 {@code FogRenderer.applyFog(Camera,...)} 内部由 {@code getFogColor(...)} 计算得到
 * （一个 {@link Vector4f}），随后被写入雾 UBO。因此在 {@code getFogColor} 返回处改写颜色，
 * 就能让仓库1 的 {@code Ambience.fog} / {@code Ambience.dimensionColor} 在 1.21.11 上重新生效。
 *
 * 注：雾距（fogStart/fogEnd）是内联写入 UBO 的 6 个 float，覆盖需动栈参数、风险高，此处不做。
 */
@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                               float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        try {
            if (Ambience.INSTANCE == null || !Ambience.INSTANCE.isOn()) {
                return;
            }
            // dimensionColor 优先：对应旧版 DimensionEffects.getFogColorOverride / clearColor
            if (Ambience.INSTANCE.dimensionColor.booleanValue) {
                Color c = Ambience.INSTANCE.dimensionColor.getValue();
                cir.setReturnValue(new Vector4f(
                        c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, 1.0f));
            } else if (Ambience.INSTANCE.fog.booleanValue) {
                Color c = Ambience.INSTANCE.fog.getValue();
                cir.setReturnValue(new Vector4f(
                        c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, c.getAlpha() / 255.0f));
            }
        } catch (Throwable t) {
            RiyueClient.rethrowFatal(t);
        }
    }
}
