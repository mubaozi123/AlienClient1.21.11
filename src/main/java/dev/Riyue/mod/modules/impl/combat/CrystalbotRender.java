package dev.Riyue.mod.modules.impl.combat;

import dev.Riyue.api.utils.render.Render3DUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Replacement for zio's FeatureBoxRenderer / LineMeshRenderer / CrystalDamageRenderer.
 * Owns the fade-box history, the damage text and the prediction path.
 */
final class CrystalbotRender {
    private static final long BASE_BOX_LIFETIME_MILLIS = 1000L;

    private static final class FadeBox {
        final BlockPos pos;
        final long added;

        FadeBox(BlockPos pos, long added) {
            this.pos = pos;
            this.added = added;
        }
    }

    private final List<FadeBox> boxes = new ArrayList<>();
    private float damage;
    private long visibleUntilMillis;
    private BlockPos damagePos;

    void addBox(BlockPos base) {
        if (base == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!Crystalbot.INSTANCE.multiFade.getValue()) {
            boxes.clear();
        }
        boxes.add(new FadeBox(base.toImmutable(), now));
    }

    /** render() 已在上一步清理过期淡出框；为空时表示没有正在淡出的放置框。 */
    boolean isEmpty() {
        return boxes.isEmpty();
    }

    void showDamage(BlockPos base, double newDamage) {
        this.damagePos = base;
        this.visibleUntilMillis = System.currentTimeMillis() + 150L;
        this.damage = (float) newDamage;
    }

    void render(MatrixStack stack, Crystalbot module) {
        if (!module.render.getValue() || stack == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float lineWidth = module.lineWidth.getValueFloat();
        Color fill = module.fill.getValue();
        Color outline = module.outline.getValue();

        boolean fade = module.fade.getValue();
        float fadeSeconds = module.fadeTime.getValueFloat();
        long fadeMillis = fade ? (long) (1000.0f * fadeSeconds) : 1L;
        long lifetime = BASE_BOX_LIFETIME_MILLIS + fadeMillis;

        Iterator<FadeBox> iterator = boxes.iterator();
        while (iterator.hasNext()) {
            FadeBox box = iterator.next();
            long age = now - box.added;
            if (age > lifetime) {
                iterator.remove();
                continue;
            }
            float alpha;
            if (fade) {
                alpha = 1.0f - (float) (age - BASE_BOX_LIFETIME_MILLIS) / Math.max(1.0f, (float) fadeMillis);
                if (age <= BASE_BOX_LIFETIME_MILLIS) {
                    alpha = 1.0f;
                }
            } else {
                alpha = age <= BASE_BOX_LIFETIME_MILLIS ? 1.0f : 0.0f;
            }
            if (alpha <= 0.02f) {
                continue;
            }
            Color scaledFill = withAlpha(fill, alpha);
            Color scaledOutline = withAlpha(outline, alpha);
            Box bounds = new Box(box.pos);
            Render3DUtil.drawFill(stack, bounds, scaledFill);
            Render3DUtil.drawBox(stack, bounds, scaledOutline, lineWidth);
        }

        renderDamage(stack, module, now, fadeMillis, fadeSeconds);
        renderPredictionPath(stack, module);
    }

    private void renderDamage(MatrixStack stack, Crystalbot module, long now,
                              long fadeMillis, float fadeSeconds) {
        if (!module.renderDamage.getValue() || damagePos == null) {
            return;
        }
        if (now >= visibleUntilMillis + fadeMillis) {
            return;
        }
        float elapsed = (float) (now - visibleUntilMillis);
        float alpha = 1.0f - elapsed / (fadeSeconds * 1000.0f);
        if (alpha <= 0.02f) {
            return;
        }
        String text = String.format("%.1f", damage);
        Render3DUtil.drawText3D(text, damagePos.toCenterPos(),
                new Color(255, 255, 255, (int) (Math.min(1.0f, Math.max(0.0f, alpha)) * 255.0f)));
    }

    private void renderPredictionPath(MatrixStack stack, Crystalbot module) {
        if (!module.drawPrediction.getValue() || !module.prediction.getValue()
                || !(module.target instanceof PlayerEntity pathTarget)) {
            return;
        }
        int ticks = module.predictionTicks.getValueInt();
        float width = 1.5f;
        Color color = module.outline.getValue();
        Vec3d previous = CrystalbotUtil.bottomCenter(module.getTargetBounds(pathTarget));
        for (int tick = 1; tick < ticks; tick++) {
            Box predicted = CrystalbotUtil.predictedBounds(pathTarget, tick);
            Vec3d next = CrystalbotUtil.bottomCenter(predicted);
            Render3DUtil.drawLine(stack, previous, next, color, width);
            previous = next;
        }
        Vec3d start = CrystalbotUtil.bottomCenter(module.getTargetBounds(pathTarget));
        Vec3d end = CrystalbotUtil.topCenter(module.getTargetBounds(pathTarget));
        Render3DUtil.drawLine(stack, start, end, color, width);
    }

    private static Color withAlpha(Color color, float alpha) {
        int value = (int) (Math.min(1.0f, Math.max(0.0f, alpha)) * color.getAlpha());
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), value);
    }
}
