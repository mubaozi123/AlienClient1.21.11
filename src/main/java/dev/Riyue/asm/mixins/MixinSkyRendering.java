package dev.Riyue.asm.mixins;

import dev.Riyue.RiyueClient;
import dev.Riyue.mod.modules.impl.render.Ambience;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

/**
 * 1.21.11 天空色覆盖。
 *
 * 背景：1.21.11 重写了渲染管线，旧的 {@code BackgroundRenderer}/{@code DimensionEffects}/
 * {@code ClientWorld.getSkyColor}/{@code BiomeEffects.getSkyColor} 全部被移除，
 * 天空色改由 {@link SkyRenderState#skyColor} 承载，每帧由 {@link SkyRendering#updateRenderState}
 * 构建、{@code WorldRenderer} 渲染时读取。
 *
 * 因此这里在 {@code updateRenderState} 尾部直接改写 {@code state.skyColor}，
 * 让仓库1 的 {@code Ambience.sky} / {@code Ambience.dimensionColor} 设置在 1.21.11 上重新生效。
 *
 * 注意：雾色 / 雾距在 1.21.11 已改为雾 UBO（{@code RenderSystem.setShaderFog(GpuBufferSlice)}），
 * 旧的 {@code setShaderFogColor/Start/End} 已不存在，无法在此处一并覆盖。
 */
@Mixin(SkyRendering.class)
public class MixinSkyRendering {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void onUpdateRenderState(ClientWorld world, float tickDelta, Camera camera,
                                     SkyRenderState state, CallbackInfo ci) {
        try {
            if (Ambience.INSTANCE == null || !Ambience.INSTANCE.isOn() || state == null) {
                return;
            }
            // 取用户设定的颜色：dimensionColor 优先（对应旧版 clearColor / 维度底色覆盖），
            // 否则用 sky（SkyColor）。二者都没开则不覆盖。
            Color c = null;
            if (Ambience.INSTANCE.dimensionColor.booleanValue) {
                c = Ambience.INSTANCE.dimensionColor.getValue();
            } else if (Ambience.INSTANCE.sky.booleanValue) {
                c = Ambience.INSTANCE.sky.getValue();
            }
            if (c == null) {
                return;
            }
            int rgb = c.getRGB();
            // 1.21.11 的可见天空由两层构成：
            //   renderTopSky(state.skyColor)                  —— 顶部天幕底色
            //   renderGlowingSky(state.sunriseAndSunsetColor) —— 地平线辉光/主渐变（占视觉主体）
            // 旧版只覆盖 skyColor 时，主天空渐变依旧由 sunriseAndSunsetColor 决定，几乎看不出变化。
            // 因此这里两个字段一起覆盖，整片天空才会真正变成用户设定的颜色。
            state.skyColor = rgb;
            state.sunriseAndSunsetColor = rgb;
        } catch (Throwable t) {
            RiyueClient.rethrowFatal(t);
        }
    }
}
