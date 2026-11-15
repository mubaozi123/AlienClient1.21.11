package dev.Riyue.mod.modules.impl.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Yarn port of zio's CrystalPlacementValidator. Kept as a separate class so the
 * builder flags (requireUpperAir, lowerHitbox, predictionTicks, ...) stay explicit.
 */
final class CrystalbotValidator {
    private boolean requireUpperAir;
    private boolean checkEntityCollisions = true;
    private boolean blockNearbyCrystals;
    private boolean allowFire = true;
    private boolean lowerHitbox;
    private boolean expandMovingPlayers;
    private boolean ignoreBaseBlock;
    private boolean ignoreYoungItems;
    private int predictionTicks;
    private float ignoredCrystalRangeSquared = 16.0f;

    boolean canPlace(BlockPos base) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || base == null) {
            return false;
        }
        if (!ignoreBaseBlock && !CrystalbotUtil.isBedrockOrObsidian(base)) {
            return false;
        }

        BlockPos crystalSpace = base.up();
        BlockState spaceState = mc.world.getBlockState(crystalSpace);
        BlockPos unconfirmed = Crystalbot.INSTANCE == null ? null : Crystalbot.INSTANCE.getUnconfirmedBreak();
        boolean clearSpace = spaceState.isAir() || crystalSpace.equals(unconfirmed);
        if (!spaceState.getFluidState().isEmpty()) {
            clearSpace = false;
        }
        if (allowFire) {
            if (!clearSpace && !spaceState.isOf(Blocks.FIRE)) {
                return false;
            }
        } else if (!clearSpace) {
            return false;
        }

        if (requireUpperAir && !mc.world.getBlockState(base.up(2)).isAir()) {
            return false;
        }

        int spaceY = crystalSpace.getY();
        double top = spaceY + (lowerHitbox ? 1 : 2);
        Box collisionBox = new Box(crystalSpace).withMaxY(top);
        int collisionCount = 0;

        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            Box entityBounds = entity.getBoundingBox();
            if (entity instanceof PlayerEntity player && entity != mc.player) {
                entityBounds = CrystalbotUtil.interpolationBounds((LivingEntity) entity);
                if (expandMovingPlayers) {
                    // zio PlayerState#zio$isNextToWall substitution: always expand slightly.
                    entityBounds = entityBounds.expand(0.0001, 0.0, 0.0001);
                }
                for (int tick = 1; tick <= predictionTicks; tick++) {
                    Box predictedBox = CrystalbotUtil.predictedBounds(player, tick);
                    Box clamped = predictedBox.withMaxY(entityBounds.maxY).withMinY(entityBounds.minY);
                    if (clamped.intersects(collisionBox)) {
                        collisionCount++;
                    }
                }
            } else {
                if (entity instanceof EndCrystalEntity && !blockNearbyCrystals) {
                    if (mc.player.getEyePos().squaredDistanceTo(entity.getEntityPos()) <= ignoredCrystalRangeSquared) {
                        continue;
                    }
                }
                if (entity instanceof ItemEntity && ignoreYoungItems && entity.age < 3) {
                    continue;
                }
            }
            if (entityBounds.intersects(collisionBox)) {
                collisionCount++;
                break;
            }
        }
        return !(checkEntityCollisions && collisionCount != 0);
    }

    CrystalbotValidator requireUpperAir(boolean enabled) {
        this.requireUpperAir = enabled;
        return this;
    }

    CrystalbotValidator checkEntityCollisions(boolean enabled) {
        this.checkEntityCollisions = enabled;
        return this;
    }

    CrystalbotValidator blockNearbyCrystals(boolean enabled) {
        this.blockNearbyCrystals = enabled;
        return this;
    }

    CrystalbotValidator allowFire(boolean enabled) {
        this.allowFire = enabled;
        return this;
    }

    CrystalbotValidator lowerHitbox(boolean enabled) {
        this.lowerHitbox = enabled;
        return this;
    }

    CrystalbotValidator expandMovingPlayers(boolean enabled) {
        this.expandMovingPlayers = enabled;
        return this;
    }

    CrystalbotValidator ignoreBaseBlock(boolean enabled) {
        this.ignoreBaseBlock = enabled;
        return this;
    }

    CrystalbotValidator ignoreYoungItems(boolean enabled) {
        this.ignoreYoungItems = enabled;
        return this;
    }

    CrystalbotValidator predictionTicks(int ticks) {
        this.predictionTicks = ticks;
        return this;
    }

    CrystalbotValidator ignoredCrystalRange(float range) {
        this.ignoredCrystalRangeSquared = range * range;
        return this;
    }
}
