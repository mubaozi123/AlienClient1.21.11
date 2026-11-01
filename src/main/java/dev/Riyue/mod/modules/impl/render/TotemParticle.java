package dev.Riyue.mod.modules.impl.render;

import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.TotemParticleEvent;
import dev.Riyue.api.utils.render.ColorUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;

import java.awt.*;
import java.util.Random;

public class TotemParticle extends Module {
    public static TotemParticle INSTANCE;
    public final SliderSetting velocityXZ =
            add(new SliderSetting("VelocityXZ", 100, 0, 500, 1).setSuffix("%"));
    public final SliderSetting velocityY =
            add(new SliderSetting("VelocityY", 100, 0, 500, 1).setSuffix("%"));
    final Random random = new Random();
    private final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255, 255)));
    private final ColorSetting color2 = add(new ColorSetting("Color2", new Color(0, 0, 0, 255)));

    public TotemParticle() {
        super("TotemParticle", Category.Render);
        setChinese("自定义图腾粒子");
        INSTANCE = this;
    }

    @EventListener
    public void idk(TotemParticleEvent event) {
        event.cancel();
        event.velocityZ *= velocityXZ.getValue() / 100;
        event.velocityX *= velocityXZ.getValue() / 100;

        event.velocityY *= velocityY.getValue() / 100;

        event.color = ColorUtil.fadeColor(color.getValue(), color2.getValue(), random.nextDouble());
    }
}
