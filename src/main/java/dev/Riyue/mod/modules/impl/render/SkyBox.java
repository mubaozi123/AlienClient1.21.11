package dev.Riyue.mod.modules.impl.render;

import dev.Riyue.RiyueClient;
import dev.Riyue.core.impl.ShaderManager;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;

import java.awt.Color;

public class SkyBox extends Module {
    public static SkyBox INSTANCE;

    public final BooleanSetting applyAll = this.add(new BooleanSetting("ApplyAll", false));
    public final ColorSetting color = this.add(new ColorSetting("Color", new Color(255, 255, 255, 255)));
    public final ColorSetting backgroundColor = this.add(new ColorSetting("BackgroundColor", new Color(0, 0, 0, 255)));
    public final SliderSetting scale = this.add(new SliderSetting("Scale", 1.0, 0.1, 5.0, 0.05));
    public final SliderSetting speed = this.add(new SliderSetting("Speed", 4.0, 0.0, 20.0, 0.1));
    public final SliderSetting opacity = this.add(new SliderSetting("Opacity", 1.0, 0.0, 1.0, 0.01));

    public SkyBox() {
        super("Skybox", Category.Render);
        this.setChinese("天空盒");
        INSTANCE = this;
    }

    /**
     * 由 MixinGameRenderer.postRender3dHook 在 renderShaders() 之后调用。
     * 将当前主帧缓冲内容 + 程序化天空动画重新输出到屏幕。
     */
    public void renderSkybox() {
        if (this.isOn() && mc.player != null && mc.world != null) {
            ShaderManager sm = RiyueClient.SHADER;
            if (sm == null) return;
            sm.renderSkyboxFullscreen(
                    color.getValue(),
                    backgroundColor.getValue(),
                    scale.getValueFloat(),
                    speed.getValueFloat(),
                    opacity.getValueFloat(),
                    applyAll.getValue()
            );
        }
    }
}
