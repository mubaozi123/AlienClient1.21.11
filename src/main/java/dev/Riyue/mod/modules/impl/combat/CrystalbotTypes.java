package dev.Riyue.mod.modules.impl.combat;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.player.EntityUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zio AutoCrystal companion types, expressed with the Riyue/Yarn API.
 * All types are package private: only {@link Crystalbot} consumes them.
 */
enum CrystalbotPlacementKind {
    NONE,
    RAYTRACE,
    OBSIDIAN,
    NON_PLAYER
}

enum CrystalbotLethality {
    NONE(false),
    INTERSECTION(false),
    POP(true),
    KILL(true);

    final boolean lethalDamage;

    CrystalbotLethality(boolean lethalDamage) {
        this.lethalDamage = lethalDamage;
    }

    static CrystalbotLethality classifyLethality(LivingEntity target, double damage) {
        boolean lethal = damage >= CrystalbotUtil.healthWithAbsorption(target);
        boolean holdingTotem = target.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || target.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
        return lethal ? (holdingTotem ? POP : KILL) : NONE;
    }

    boolean isKill() {
        return this == KILL;
    }

    boolean isIntersection() {
        return this == INTERSECTION;
    }
}

/**
 * Damage acceptance policy. Mirrors zio's CrystalDamagePolicy, including the
 * static reference to the AutoCrystalModule singleton (here Crystalbot.INSTANCE).
 */
enum CrystalbotDamagePolicy {
    PLACE,
    HIT;

    double getMinimumDamage(LivingEntity target) {
        Crystalbot m = Crystalbot.INSTANCE;
        if (!m.obsidianDropNearTarget && !m.quiverThreatActive) {
            if (m.isFacePlaceEnabled()) {
                float health = CrystalbotUtil.healthWithAbsorption(target);
                if (health <= m.facePlaceHealth.getValueFloat()) {
                    return 0.6;
                }

                if (target instanceof PlayerEntity player) {
                    float armor = CrystalbotUtil.lowestArmorDurabilityPercent(player);
                    if (armor <= m.facePlaceArmorThreshold.getValueInt()) {
                        return 0.6;
                    }
                }

                if (target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD)
                        && m.facePlaceShieldBreaker.getValue()) {
                    return 0.6;
                }
            }

            if (Crystalbot.isWorldReady() && mcBlockAt(target).isOf(Blocks.NETHER_PORTAL)) {
                return 0.6;
            }

            double placeMinimum = m.minPlaceDamage.getValueFloat();
            double hitMinimum = m.minBreakDamage.getValueFloat();

            // zio NumberSetting.setBoundaryLabel("Lethal", MAX) emulation.
            if (m.minPlaceDamage.getValueFloat() >= m.minPlaceDamage.getMax() - 0.001) {
                placeMinimum = CrystalbotUtil.healthWithAbsorption(target);
            }

            if (m.shouldIgnoreMiningBlock()) {
                double miningMinimum = m.alwaysIgnoreMining.getValue() ? 0.6 : 5.0;
                placeMinimum = Math.min(placeMinimum, miningMinimum);
                hitMinimum = 3.0;
            }

            double selected = this == HIT ? hitMinimum : placeMinimum;
            return Math.min(selected, CrystalbotUtil.healthWithAbsorption(target));
        }
        return 0.1;
    }

    private static net.minecraft.block.BlockState mcBlockAt(LivingEntity target) {
        return net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(target.getBlockPos());
    }

    double getMaximumSelfDamage() {
        Crystalbot m = Crystalbot.INSTANCE;
        if (m.isSelfInvulnerable()) {
            return 999.0;
        }
        return this == HIT ? m.maxBreakSelfDamage.getValueFloat() : m.maxPlaceSelfDamage.getValueFloat();
    }

    boolean shouldRejectDamage(LivingEntity target, double selfDamage, double targetDamage,
                               CrystalbotLethality lethality) {
        Crystalbot m = Crystalbot.INSTANCE;
        boolean holdingTotem = net.minecraft.client.MinecraftClient.getInstance().player.isHolding(Items.TOTEM_OF_UNDYING);

        if (m.isSelfInvulnerable()) {
            selfDamage = 0.0;
        }
        if (m.forceSuicide.getValue()) {
            return false;
        }
        if (selfDamage >= CrystalbotUtil.selfHealth()
                && m.antiSuicide.getValue()
                && (!lethality.isKill() || !holdingTotem)) {
            return true;
        }
        if (this == HIT && lethality.isIntersection()) {
            return false;
        }
        if (lethality.isKill()) {
            return false;
        }
        if (this == PLACE && selfDamage > 0.0) {
            double ratio = targetDamage / selfDamage;
            if (ratio < m.placeDamageRatio.getValueFloat()) {
                return true;
            }
        }
        if (!(targetDamage >= CrystalbotUtil.healthWithAbsorption(target))) {
            if (targetDamage < getMinimumDamage(target)) {
                return true;
            }
            if (selfDamage > getMaximumSelfDamage()) {
                return true;
            }
        }
        return false;
    }
}

record CrystalbotPlacementResult(
        BlockPos base,
        LivingEntity target,
        double damage,
        CrystalbotPlacementKind kind
) {
    CrystalbotPlacementResult selectBetterPlacement(CrystalbotPlacementResult other) {
        if (other == null) {
            return this;
        }
        int priority = this.kind.ordinal();
        if (priority < other.kind.ordinal()) {
            return this;
        }
        if (priority > other.kind.ordinal()) {
            return other;
        }
        return other.damage > this.damage ? other : this;
    }
}

record CrystalbotTargetDamage(
        LivingEntity target,
        double damage,
        CrystalbotPlacementKind kind,
        CrystalbotLethality lethality
) {
    static CrystalbotTargetDamage calculateForBase(Crystalbot module, BlockPos base,
                                                   boolean hasCrystalBase, List<LivingEntity> targets) {
        CrystalbotTargetDamage best = null;
        for (LivingEntity candidate : targets) {
            if (module.isInvalidTarget(candidate)) {
                continue;
            }
            if (base.getY() > candidate.getY() + 1.5D && !hasCrystalBase) {
                continue;
            }

            Box targetBounds = module.getTargetBounds(candidate);
            double minimumDamage = CrystalbotDamagePolicy.PLACE.getMinimumDamage(candidate);
            double damage = module.calculateExplosionDamage(base, candidate, targetBounds, true);
            CrystalbotLethality lethality = CrystalbotLethality.classifyLethality(candidate, damage);
            boolean holdingCrystal = candidate.isHolding(Items.END_CRYSTAL);

            if (module.ignoreNakeds.getValue() && !CrystalbotUtil.hasArmor(candidate) && !holdingCrystal) {
                continue;
            }

            double distance = CrystalbotUtil.bottomCenter(targetBounds)
                    .distanceTo(Vec3d.ofBottomCenter(base));
            if (distance > module.crystalRange.getValueFloat()
                    && !lethality.lethalDamage
                    && !module.obsidianDropNearTarget
                    && !module.quiverThreatActive) {
                continue;
            }

            boolean friend = candidate instanceof PlayerEntity
                    && RiyueClient.FRIEND != null
                    && RiyueClient.FRIEND.isFriend((PlayerEntity) candidate);
            if (friend && candidate != net.minecraft.client.MinecraftClient.getInstance().player) {
                if (module.antiFriendKill.getValue()
                        && damage > CrystalbotUtil.healthWithAbsorption(candidate)) {
                    return null;
                }
                continue;
            }

            if ((minimumDamage > damage || damage < 0.5D) && !lethality.lethalDamage) {
                continue;
            }
            if (best != null && !(damage > best.damage())) {
                continue;
            }
            best = new CrystalbotTargetDamage(candidate, damage, CrystalbotPlacementKind.NONE, lethality);
        }
        return best;
    }
}

/**
 * Block/face sampling modes. FAST is the center point, X8 the eight corners.
 */
enum CrystalbotBlockSampleMode {
    FAST(new Vec3d(0.5, 0.5, 0.5)),
    X2(new Vec3d(0.5, 0.05, 0.5), new Vec3d(0.5, 1.0, 0.5)),
    X4(new Vec3d(0.05, 0.05, 0.05), new Vec3d(0.05, 0.05, 0.95),
            new Vec3d(0.95, 0.05, 0.05), new Vec3d(0.95, 0.05, 0.95)),
    X8(new Vec3d(0.05, 0.05, 0.05), new Vec3d(0.05, 0.05, 0.95),
            new Vec3d(0.05, 0.95, 0.05), new Vec3d(0.05, 0.95, 0.95),
            new Vec3d(0.95, 0.05, 0.05), new Vec3d(0.95, 0.05, 0.95),
            new Vec3d(0.95, 0.95, 0.05), new Vec3d(0.95, 0.95, 0.95));

    private final List<Vec3d> offsets;

    CrystalbotBlockSampleMode(Vec3d... offsets) {
        this.offsets = Arrays.asList(offsets);
    }

    List<Vec3d> getOffsets() {
        return offsets;
    }

    List<Vec3d> sampleBlock(BlockPos position) {
        List<Vec3d> samples = new ArrayList<>();
        for (Vec3d offset : offsets) {
            Vec3d origin = new Vec3d(position.getX(), position.getY(), position.getZ());
            Vec3d point = offset.add(origin);
            if (this == X4 && net.minecraft.client.MinecraftClient.getInstance().player.getEyeY() > position.getY() + 1) {
                point = point.add(0.0, 0.9, 0.0);
            }
            samples.add(point);
        }
        return samples;
    }

    private static boolean isOppositeFaceCoordinate(int step, int rounded) {
        return step == 1 && rounded == 0 || step == -1 && rounded == 1;
    }

    List<Vec3d> sampleFace(BlockPos position, Direction face) {
        List<Vec3d> samples = new ArrayList<>();
        Vec3d origin = new Vec3d(position.getX(), position.getY(), position.getZ());
        for (Vec3d offset : offsets) {
            if (isOppositeFaceCoordinate(face.getOffsetX(), (int) Math.round(offset.x))) {
                continue;
            }
            if (isOppositeFaceCoordinate(face.getOffsetY(), (int) Math.round(offset.y))) {
                continue;
            }
            if (isOppositeFaceCoordinate(face.getOffsetZ(), (int) Math.round(offset.z))) {
                continue;
            }
            samples.add(offset.add(origin));
        }
        return samples;
    }
}
