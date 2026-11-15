package dev.Riyue.mod.modules.impl.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Yarn port of zio's AutoCrystalTargeting. */
final class CrystalbotTargeting {
    private final List<LivingEntity> targets = new ArrayList<>();
    private final Crystalbot module;

    CrystalbotTargeting(Crystalbot module) {
        this.module = module;
    }

    synchronized void refresh() {
        targets.clear();
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (!(entity instanceof LivingEntity candidate) || module.isInvalidTarget(candidate)) {
                continue;
            }
            if (entity instanceof PlayerEntity player) {
                targets.add(player);
            }
            if (module.bosses.getValue()
                    && (candidate instanceof WitherEntity
                    || candidate instanceof EnderDragonEntity
                    || candidate instanceof WardenEntity)) {
                targets.add(candidate);
            }
        }
    }

    synchronized List<LivingEntity> snapshot() {
        return new ArrayList<>(targets);
    }

    LivingEntity findProximityTarget() {
        LivingEntity proximityTarget = module.target == null && !module.isInvalidTarget(module.previousTarget)
                ? module.previousTarget : module.target;
        if (proximityTarget == null) {
            double nearest = Double.MAX_VALUE;
            MinecraftClient mc = MinecraftClient.getInstance();
            for (LivingEntity candidate : snapshot()) {
                double distance = mc.player.distanceTo(candidate);
                if (!module.isInvalidTarget(candidate) && !(distance > nearest)) {
                    nearest = distance;
                    proximityTarget = candidate;
                }
            }
            if (proximityTarget == null) {
                return null;
            }
            module.target = proximityTarget;
        }
        return proximityTarget;
    }

    boolean hasObsidianDropNear(LivingEntity proximityTarget) {
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (!(entity instanceof ItemEntity item)) {
                continue;
            }
            ItemStack stack = item.getStack();
            if (!stack.isOf(Items.OBSIDIAN) || stack.getCount() < 8 || !entity.isAlive()) {
                continue;
            }
            Box targetBounds = proximityTarget.getBoundingBox();
            Vec3d center = targetBounds.getCenter();
            Box proximityBox = targetBounds.withMinY(center.y).expand(0.5, 0.0, 0.5);
            if (entity.getBoundingBox().intersects(proximityBox)) {
                return true;
            }
        }
        return false;
    }
}
