package dev.Riyue.mod.modules.impl.combat;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.entity.PlayerEntityPredict;
import dev.Riyue.api.utils.math.DamageUtils;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.impl.client.AntiCheat;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Geometry / raycast / explosion helpers for {@link Crystalbot}.
 *
 * The explosion routine is a faithful Yarn port of zio's DamageUtils#getExplosionDamage:
 * exposure sampling, ignored positions, an overriding solid position, low-resistance
 * blocks, difficulty scaling, armour/protection reduction, "assume best armour" and
 * shield blocking are all preserved. Riyue's DamageUtils#raycast is used for the
 * per-sample block raycast so that no static CombatUtil globals are touched (they
 * race with AutoCrystal.INSTANCE::onThread).
 */
final class CrystalbotUtil {
    private CrystalbotUtil() {
    }

    static final Direction[] PLACEMENT_DIRECTIONS = {
            Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
    };

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ── box helpers (zio BoundingBoxUtils / Vec3Utils) ──────────────────────

    static Vec3d bottomCenter(Box box) {
        return new Vec3d(MathHelper.lerp(0.5, box.minX, box.maxX), box.minY,
                MathHelper.lerp(0.5, box.minZ, box.maxZ));
    }

    static Vec3d topCenter(Box box) {
        return new Vec3d(MathHelper.lerp(0.5, box.minX, box.maxX), box.maxY,
                MathHelper.lerp(0.5, box.minZ, box.maxZ));
    }

    static Vec3d lowerQuarterCenter(Box box) {
        return bottomCenter(box).add(0.0, box.getLengthY() * 0.25, 0.0);
    }

    static double horizontalDistance(Vec3d first, Vec3d second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    static float healthWithAbsorption(LivingEntity entity) {
        if (entity == null) {
            return 0.0f;
        }
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    static float selfHealth() {
        return mc().player == null ? 0.0f : mc().player.getHealth() + mc().player.getAbsorptionAmount();
    }

    /**
     * zio BoundingBoxUtils#getInterpolationBounds: a box centred on the entity's
     * interpolated (server) position with minY at the feet and the vanilla height.
     */
    static Box interpolationBounds(LivingEntity entity) {
        Box bounds = entity.getBoundingBox();
        Vec3d position = entity.getLerpedPos(1.0f);
        double halfX = bounds.getLengthX() * 0.5;
        double halfZ = bounds.getLengthZ() * 0.5;
        return new Box(-halfX, 0.0, -halfZ, halfX, bounds.getLengthY(), halfZ).offset(position);
    }

    static Box predictedBounds(PlayerEntity player, int ticks) {
        if (AntiCheat.INSTANCE == null) {
            return player.getBoundingBox();
        }
        PlayerEntityPredict prediction = new PlayerEntityPredict(
                player,
                AntiCheat.INSTANCE.maxMotionY.getValue(),
                ticks,
                AntiCheat.INSTANCE.simulation.getValueInt(),
                AntiCheat.INSTANCE.step.getValue(),
                AntiCheat.INSTANCE.doubleStep.getValue(),
                AntiCheat.INSTANCE.jump.getValue(),
                AntiCheat.INSTANCE.inBlockPause.getValue());
        return prediction.predict.getBoundingBox();
    }

    // ── world helpers (zio WorldUtils / BlockInteractionUtils) ──────────────

    static boolean isBedrockOrObsidian(BlockPos pos) {
        if (pos == null || mc().world == null) {
            return false;
        }
        BlockState state = mc().world.getBlockState(pos);
        return state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN);
    }

    static boolean canMineBlock(BlockPos pos) {
        if (pos == null || mc().world == null || mc().player == null) {
            return false;
        }
        BlockState state = mc().world.getBlockState(pos);
        if (!mc().player.isCreative() && state.getBlock().getHardness() < 0.0f) {
            return false;
        }
        if (state.isAir()) {
            return false;
        }
        return BlockUtil.canCollide(new Box(pos));
    }

    static boolean canPlaceAt(BlockPos pos, boolean playersOnly) {
        if (pos == null || mc().world == null) {
            return false;
        }
        if (!mc().world.isInBuildLimit(pos) || !mc().world.getWorldBorder().contains(pos)) {
            return false;
        }
        if (BlockUtil.placedPos.contains(pos)) {
            return false;
        }
        Box box = new Box(pos);
        for (net.minecraft.entity.Entity entity : safeEntities()) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            if (playersOnly && !(entity instanceof PlayerEntity)) {
                continue;
            }
            if (entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }
        return true;
    }

    static Iterable<net.minecraft.entity.Entity> safeEntities() {
        if (RiyueClient.THREAD != null) {
            Iterable<net.minecraft.entity.Entity> snapshot = RiyueClient.THREAD.getEntities();
            if (snapshot != null) {
                return snapshot;
            }
        }
        return mc().world == null ? Collections.emptyList() : mc().world.getEntities();
    }

    /** zio BlockInteractionUtils#getStrictDirections, using repo BlockUtil strict direction checks. */
    static List<Direction> getStrictDirections(BlockPos pos) {
        List<Direction> directions = new ArrayList<>(Arrays.asList(PLACEMENT_DIRECTIONS));
        if (pos == null || mc().world == null || mc().player == null) {
            return directions;
        }
        if (pos.getY() + 1 > mc().world.getTopYInclusive()) {
            directions.remove(Direction.UP);
        }

        Vec3d eye = mc().player.getEyePos();
        Vec3d center = pos.toCenterPos();
        double deltaX = eye.x - center.x;
        double deltaZ = eye.z - center.z;
        if (deltaX > 0.5) {
            directions.remove(Direction.WEST);
        } else if (deltaX < -0.5) {
            directions.remove(Direction.EAST);
        } else {
            directions.remove(Direction.WEST);
            directions.remove(Direction.EAST);
        }
        if (deltaZ > 0.5) {
            directions.remove(Direction.NORTH);
        } else if (deltaZ < -0.5) {
            directions.remove(Direction.SOUTH);
        } else {
            directions.remove(Direction.NORTH);
            directions.remove(Direction.SOUTH);
        }
        // zio's eye-height based UP/DOWN filtering (non-GRIM branch).
        if (eye.y < pos.getY()) {
            directions.remove(Direction.UP);
        }
        if (eye.y > pos.getY()) {
            directions.remove(Direction.DOWN);
        }
        // NCP solid neighbour / Legit visibility / Grim body-sample checks come
        // from repo1's BlockUtil.isStrictDirection for non-Vanilla placement modes.
        if (AntiCheat.INSTANCE != null
                && AntiCheat.INSTANCE.placement.getValue() != dev.Riyue.mod.modules.settings.enums.Placement.Vanilla) {
            directions.removeIf(direction -> !BlockUtil.isStrictDirection(pos, direction));
        }
        return directions;
    }

    static Direction findSupportDirection(BlockPos pos, boolean strictDirection) {
        return findSupportDirection(pos, strictDirection, false);
    }

    static Direction findSupportDirection(BlockPos pos, boolean strictDirection, boolean checkRaytrace) {
        if (pos == null || mc().world == null || mc().player == null) {
            return null;
        }
        for (Direction direction : PLACEMENT_DIRECTIONS) {
            BlockPos neighbor = pos.offset(direction);
            if (neighbor.getY() >= mc().world.getTopYInclusive()) {
                continue;
            }
            if (!mc().world.getWorldBorder().contains(neighbor)) {
                continue;
            }
            if (checkRaytrace) {
                Direction clickedFace = direction.getOpposite();
                if (!canSeeAny(CrystalbotBlockSampleMode.X8.sampleFace(neighbor, clickedFace))) {
                    continue;
                }
            }
            // zio worldStateManager.isPlacementOccupied(neighbor) -> occupied, clickable block.
            boolean occupied = !mc().world.getBlockState(neighbor).isAir()
                    || BlockUtil.placedPos.contains(neighbor);
            if (!occupied) {
                continue;
            }
            if (!strictDirection) {
                return direction;
            }
            if (getStrictDirections(neighbor).contains(direction.getOpposite())) {
                return direction;
            }
        }
        return null;
    }

    // ── visibility (zio WorldUtils#canSee / canSeeAny) ───────────────────────

    static boolean canSee(Vec3d origin, Vec3d target) {
        return canSee(origin, target, BlockPos.ofFloored(target), null);
    }

    static boolean canSee(Vec3d origin, Vec3d target, BlockPos targetBlock, Direction targetFace) {
        if (origin == null || target == null || mc().world == null || mc().player == null) {
            return false;
        }
        if (target.distanceTo(origin) > 128.0) {
            return false;
        }
        BlockHitResult hit = mc().world.raycast(new RaycastContext(
                origin, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc().player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit.getPos().equals(target)) {
            return true;
        }
        if (!hit.getBlockPos().equals(targetBlock)) {
            return false;
        }
        return hit.getSide() == targetFace || targetFace == null;
    }

    static boolean canSeeAny(Vec3d origin, Iterable<Vec3d> points) {
        for (Vec3d point : points) {
            if (canSee(origin, point)) {
                return true;
            }
        }
        return false;
    }

    static boolean canSeeAny(Iterable<Vec3d> points) {
        return mc().player != null && canSeeAny(mc().player.getEyePos(), points);
    }

    // ── face corners (zio MathUtils#faceCorners) ─────────────────────────────

    private static Vec3d faceCorner(double x, double y, double z, Direction.Axis firstAxis,
                                    Direction.Axis secondAxis, int firstOffset, int secondOffset) {
        return new Vec3d(
                x + (firstAxis == Direction.Axis.X ? firstOffset : 0) + (secondAxis == Direction.Axis.X ? secondOffset : 0),
                y + (firstAxis == Direction.Axis.Y ? firstOffset : 0) + (secondAxis == Direction.Axis.Y ? secondOffset : 0),
                z + (firstAxis == Direction.Axis.Z ? firstOffset : 0) + (secondAxis == Direction.Axis.Z ? secondOffset : 0));
    }

    static List<Vec3d> faceCorners(BlockPos pos, Direction direction) {
        Direction.Axis faceAxis = direction.getAxis();
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double faceOffset = direction.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        if (faceAxis == Direction.Axis.X) {
            x += faceOffset;
        }
        if (faceAxis == Direction.Axis.Y) {
            y += faceOffset;
        }
        if (faceAxis == Direction.Axis.Z) {
            z += faceOffset;
        }
        Direction.Axis firstAxis = faceAxis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis secondAxis = faceAxis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        return List.of(
                faceCorner(x, y, z, firstAxis, secondAxis, 0, 0),
                faceCorner(x, y, z, firstAxis, secondAxis, 1, 0),
                faceCorner(x, y, z, firstAxis, secondAxis, 1, 1),
                faceCorner(x, y, z, firstAxis, secondAxis, 0, 1));
    }

    // ── inventory helpers ────────────────────────────────────────────────────

    static boolean hasArmor(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (!player.getEquippedStack(slot).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    static float lowestArmorDurabilityPercent(PlayerEntity player) {
        float lowest = 100.0f;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            lowest = Math.min(lowest, durabilityPercent(player.getEquippedStack(slot)));
        }
        return lowest;
    }

    private static float durabilityPercent(ItemStack stack) {
        if (!stack.isDamageable() || stack.getMaxDamage() == 0) {
            return 100.0f;
        }
        return EntityUtil.getDamagePercent(stack);
    }

    // ── explosion damage (zio DamageUtils) ──────────────────────────────────

    static double getCrystalDamage(Vec3d explosionPos, LivingEntity target) {
        return getCrystalDamage(explosionPos, target, target.getBoundingBox());
    }

    static double getCrystalDamage(Vec3d explosionPos, LivingEntity target, Box bounds) {
        return getExplosionDamage(explosionPos, target, bounds, 6.0, true,
                Collections.emptyList(), null);
    }

    static double getExplosionDamage(Vec3d explosionPos, LivingEntity target, Box bounds,
                                     double power, boolean ignoreLowResistance,
                                     Collection<BlockPos> ignoredPositions, BlockPos solidPosition) {
        if (target == null || mc().world == null) {
            return 0.0;
        }
        if (target == mc().player && mc().player.isCreative()) {
            return 0.0;
        }
        Vec3d bottomCenter = bottomCenter(bounds);
        double diameter = power * 2.0;
        double normalizedDistance = bottomCenter.distanceTo(explosionPos) / diameter;
        if (normalizedDistance > 1.0) {
            return 0.0;
        }

        DamageUtils.RaycastFactory factory = (context, blockPos) -> {
            if (ignoredPositions != null && !ignoredPositions.isEmpty() && ignoredPositions.contains(blockPos)) {
                return null;
            }
            BlockState state;
            if (solidPosition != null && blockPos.equals(solidPosition)) {
                state = Blocks.OBSIDIAN.getDefaultState();
            } else {
                state = mc().world.getBlockState(blockPos);
                if (ignoreLowResistance && state.getBlock().getBlastResistance() < 600) {
                    return null;
                }
            }
            return state.getCollisionShape(mc().world, blockPos)
                    .raycast(context.start(), context.end(), blockPos);
        };

        float exposure = getExposure(explosionPos, bounds, factory);
        double impact = (1.0 - normalizedDistance) * exposure;
        double damage = ((impact * impact + impact) / 2.0) * 7.0 * diameter + 1.0;
        float scaled = scaleForDifficulty((float) damage);
        scaled = applyDamageReduction(target, scaled);
        scaled = resolveBlockingDamage(target, explosionPos, scaled);
        return Math.max(scaled, 0.0);
    }

    private static float getExposure(Vec3d explosionPos, Box bounds, DamageUtils.RaycastFactory factory) {
        double xDiff = bounds.maxX - bounds.minX;
        double yDiff = bounds.maxY - bounds.minY;
        double zDiff = bounds.maxZ - bounds.minZ;
        double stepX = 1.0 / (xDiff * 2.0 + 1.0);
        double stepY = 1.0 / (yDiff * 2.0 + 1.0);
        double stepZ = 1.0 / (zDiff * 2.0 + 1.0);
        if (stepX <= 0.0 || stepY <= 0.0 || stepZ <= 0.0) {
            return 0.0f;
        }
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        int visible = 0;
        int total = 0;
        for (double fx = 0.0; fx <= 1.0; fx += stepX) {
            for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                    double sampleX = MathHelper.lerp(fx, bounds.minX, bounds.maxX) + offsetX;
                    double sampleY = MathHelper.lerp(fy, bounds.minY, bounds.maxY);
                    double sampleZ = MathHelper.lerp(fz, bounds.minZ, bounds.maxZ) + offsetZ;
                    DamageUtils.ExposureRaycastContext context = new DamageUtils.ExposureRaycastContext(
                            new Vec3d(sampleX, sampleY, sampleZ), explosionPos);
                    if (DamageUtils.raycast(context, factory) == null) {
                        visible++;
                    }
                    total++;
                }
            }
        }
        return total == 0 ? 0.0f : (float) visible / total;
    }

    private static float scaleForDifficulty(float damage) {
        Difficulty difficulty = mc().world.getDifficulty();
        return switch (difficulty) {
            case PEACEFUL -> 0.0f;
            case EASY -> Math.min(damage / 2.0f + 1.0f, damage);
            case HARD -> damage * 1.5f;
            default -> damage;
        };
    }

    private static float applyDamageReduction(LivingEntity target, float damage) {
        Crystalbot module = Crystalbot.INSTANCE;
        boolean assumeBestArmor = module != null && module.isOn() && module.assumeBestArmor.getValue();

        DamageSource source = mc().world.getDamageSources().explosion(null);
        float armor = target.getArmor();
        float toughness = (float) target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        damage = DamageUtil.getDamageLeft(target, damage, source, armor, toughness);

        if (target.hasStatusEffect(StatusEffects.RESISTANCE) || assumeBestArmor) {
            StatusEffectInstance resistance = target.getStatusEffect(StatusEffects.RESISTANCE);
            float amplifier = resistance == null ? 1.0f : resistance.getAmplifier();
            float resistanceLevel = amplifier + 1.0f;
            damage = damage * (25.0f - resistanceLevel * 5.0f) / 25.0f;
        }

        int protectionScore = 0;
        int assumedArmorUnits = 1;
        if (target instanceof PlayerEntity player) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = player.getEquippedStack(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.isIn(ItemTags.HEAD_ARMOR)) {
                    assumedArmorUnits--;
                }
                int blast = DamageUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
                int protection = DamageUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
                protectionScore += blast * 2 + protection;
                assumedArmorUnits++;
            }
            if (assumeBestArmor) {
                protectionScore = (int) ((assumedArmorUnits / 4.0f) * 20.0f);
            }
        }
        protectionScore = Math.min(protectionScore, 20);
        damage *= 1.0f - protectionScore / 25.0f;
        return Math.max(damage, 0.0f);
    }

    private static float resolveBlockingDamage(LivingEntity target, Vec3d explosionPos, float damage) {
        if (!target.isUsingItem()) {
            return damage;
        }
        BlocksAttacksComponent blocksAttacks = target.getActiveItem().get(DataComponentTypes.BLOCKS_ATTACKS);
        if (blocksAttacks == null) {
            return damage;
        }
        DamageSource source = mc().world.getDamageSources().explosion(null);
        Vec3d viewVector = target.getRotationVector(0.0f, target.getHeadYaw());
        Vec3d attackDirection = explosionPos.subtract(target.getEntityPos());
        Vec3d horizontal = new Vec3d(attackDirection.x, 0.0, attackDirection.z).normalize();
        double dot = MathHelper.clamp(horizontal.dotProduct(viewVector), -1.0, 1.0);
        double angle = Math.acos(dot);
        float reduced = blocksAttacks.getDamageReductionAmount(source, damage, angle);
        return Math.max(damage - reduced, 0.0f);
    }
}
