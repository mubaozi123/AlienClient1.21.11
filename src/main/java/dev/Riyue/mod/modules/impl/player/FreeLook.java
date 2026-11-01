package dev.Riyue.mod.modules.impl.player;

import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.LookDirectionEvent;
import dev.Riyue.api.utils.math.MathUtil;
import dev.Riyue.mod.modules.Module;
import net.minecraft.client.util.math.MatrixStack;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;

    public FreeLook() {
        super("FreeLook", Category.Player);
        setChinese("自由视角");
        INSTANCE = this;
    }

    private float fakeYaw;
    private float fakePitch;
    private float prevFakeYaw;
    private float prevFakePitch;

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        fakePitch = mc.player.getPitch();
        fakeYaw = mc.player.getYaw();

        prevFakePitch = fakePitch;
        prevFakeYaw = fakeYaw;
    }

    @EventListener
    public void onLookDirection(LookDirectionEvent event) {
        fakeYaw += (float) event.getCursorDeltaX() * 0.15f;
        fakePitch += (float) event.getCursorDeltaY() * 0.15f;
        event.cancel();
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        prevFakeYaw = fakeYaw;
        prevFakePitch = fakePitch;
    }

    public float getFakeYaw() {
        return MathUtil.interpolate(prevFakeYaw, fakeYaw, mc.getRenderTickCounter().getDynamicDeltaTicks());
    }

    public float getFakePitch() {
        return MathUtil.interpolate(prevFakePitch, fakePitch, mc.getRenderTickCounter().getDynamicDeltaTicks());
    }
}
