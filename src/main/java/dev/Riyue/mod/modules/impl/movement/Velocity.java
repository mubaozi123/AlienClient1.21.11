package dev.Riyue.mod.modules.impl.movement;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.EntityVelocityUpdateEvent;
import dev.Riyue.api.events.impl.PacketEvent;
import dev.Riyue.api.events.impl.TickEvent;
import dev.Riyue.api.events.impl.UpdateRotateEvent;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.asm.accessors.IClientPlayerEntity;
import dev.Riyue.asm.accessors.IExplosionS2CPacket;
import dev.Riyue.asm.accessors.IPlayerMoveC2SPacket;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.EnumSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 由 open-zio (dev.zio / MC 1.21.11) 的 VelocityModule + Plain/Grim/Walls 三个
 * VelocityModeHandler 完整移植而来，替换仓库1 原有 Velocity。
 *
 * 框架映射：zio 事件 -> 仓库1 事件
 *  - ClientPacketListenerHandleSetEntityMotionEvent -> EntityVelocityUpdateEvent(explosion=false)
 *  - ClientPacketListenerHandleExplosionEvent       -> PacketEvent.Receive(ExplosionS2CPacket) 改包
 *  - LocalPlayerSendPositionEvent                   -> MixinClientPlayerEntity.sendMovementPackets HEAD hook
 *                                                      + PacketEvent.Send 改出站包（IPlayerMoveC2SPacket）
 *  - FishingHookPullEntityEvent                     -> EntityStatusS2CPacket(status=31)（沿用仓库1 原路径）
 *  - LocalPlayerPreTickEvent                        -> TickEvent(Pre)
 *  - ServerStateManager.hasElapsedSinceSetback      -> setbackTimer（PlayerPositionLookS2CPacket 触发 reset）
 *  - RotationManager.isMovementSyncEnabled          -> MovementSync.INSTANCE.isOn()
 *
 * 仓库1 兼容 API（外部 mixin/模块引用，必须保留）：
 *  - blockPush / entityPush / waterPush / noClimb（MixinClientPlayerEntity/MixinEntity/MixinFlowableFluid/MixinLivingEntity）
 *  - noRotation()（Aura/AutoCrystal/Surround/Phase 等 10 个模块调用）
 *  - pearlTimer（Phase 调用 reset()）
 *
 * 已知差异：爆炸击退改为在 PacketEvent.Receive 阶段直接改包（IExplosionS2CPacket），
 * 因为仓库1 的 velocityHook2(HEAD) 与 vanilla lambda 都会 addVelocityInternal，
 * 走事件改值会双重应用；改包后再 cancel 爆炸事件即可精确单次应用。
 */
public class Velocity extends Module {
    public static Velocity INSTANCE;

    /** zio AntiCheatModule.ROTATION_JITTER_STEP = 0.2^3 * 8 * 0.15 - 0.001 */
    private static final float ROTATION_JITTER_STEP = 0.0086F;
    private static final double BLOCK_CORNER_INSET = 0.0625;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Plain));
    private final SliderSetting horizontal = add(new SliderSetting("Horizontal", 0, 0, 100, 1, () -> mode.is(Mode.Plain)));
    private final SliderSetting vertical = add(new SliderSetting("Vertical", 0, 0, 100, 1, () -> mode.is(Mode.Plain)));
    private final BooleanSetting inverse = add(new BooleanSetting("Inverse", false, () -> mode.is(Mode.Plain)));
    public final BooleanSetting reduce = add(new BooleanSetting("Reduce", false,
            () -> (MovementSync.INSTANCE != null && MovementSync.INSTANCE.isOn()) || mode.is(Mode.Walls)));
    public final BooleanSetting staticSetting = add(new BooleanSetting("Static", false,
            () -> mode.is(Mode.Walls) && reduce.isOpen()));
    public final BooleanSetting sources = add(new BooleanSetting("Sources", false).setParent());
    private final BooleanSetting explosions = add(new BooleanSetting("Explosions", true, sources::isOpen));
    public final BooleanSetting blockPush = add(new BooleanSetting("NoBlockPush", true, sources::isOpen));
    public final BooleanSetting waterPush = add(new BooleanSetting("NoWaterPush", false, sources::isOpen));
    private final BooleanSetting fishingBobber = add(new BooleanSetting("FishingBobber", true, sources::isOpen));
    public final BooleanSetting entityPush = add(new BooleanSetting("NoEntityPush", true));
    public final BooleanSetting noClimb = add(new BooleanSetting("NoClimb", false));
    public final BooleanSetting noRotation = add(new BooleanSetting("NoRotation", false,
            () -> mode.is(Mode.Grim) || mode.is(Mode.Walls)));

    /** LUNGE 长矛突击：STAB 动作后的动量包放行标记（zio lungeMotionPending） */
    private boolean lungeMotionPending;

    /** 服务器 setback 状态（zio ServerStateManager.setbackTimer 等价实现） */
    private final Timer setbackTimer = new Timer();
    private final Timer teleportAckTimer = new Timer();

    /** 仓库1 兼容：Phase 模块调用 Velocity.INSTANCE.pearlTimer.reset() */
    public final Timer pearlTimer = new Timer();

    private final PlainVelocityHandler plainHandler = new PlainVelocityHandler();
    private final GrimVelocityHandler grimHandler = new GrimVelocityHandler();
    private final WallsVelocityHandler wallsHandler = new WallsVelocityHandler();

    public Velocity() {
        super("Velocity", Category.Movement);
        setChinese("反击退");
        INSTANCE = this;
    }

    private VelocityModeHandler getHandler() {
        return switch (mode.getValue()) {
            case Plain -> plainHandler;
            case Grim -> grimHandler;
            case Walls -> wallsHandler;
        };
    }

    public boolean isLungeMotionPending() {
        return this.lungeMotionPending;
    }

    public void setLungeMotionPending(boolean pending) {
        this.lungeMotionPending = pending;
    }

    @Override
    public String getInfo() {
        if (mode.is(Mode.Plain)) {
            return "H" + horizontal.getValueInt() + "% V" + vertical.getValueInt() + "%";
        }
        return mode.getValue().name();
    }

    @Override
    public void onDisable() {
        this.lungeMotionPending = false;
        this.wallsHandler.resetRotationOverride();
    }

    /**
     * 模块对外暴露的「是否不旋转」判定，签名沿用仓库1 原版：
     * Aura / AutoAnchor / AutoCrystal / AutoWeb / SelfTrap / Surround / Phase /
     * Scaffold / AutoPearl / SpeedMine 都调用 Velocity.INSTANCE.noRotation()。
     */
    public boolean noRotation() {
        return isOn() && (mode.is(Mode.Grim) || mode.is(Mode.Walls)) && EntityUtil.isInsideBlock() && noRotation.getValue();
    }

    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (noRotation()) {
            event.setRotation(RiyueClient.ROTATION.rotationYaw, 89);
        }
    }

    @EventListener
    public void onVelocity(EntityVelocityUpdateEvent event) {
        if (nullCheck() || !isOn()) {
            return;
        }
        if (event.isExplosion()) {
            // 爆炸击退已在 PacketEvent.Receive 阶段改包；这里取消事件，
            // 防止 velocityHook2(HEAD) 再叠加一次（vanilla lambda 仍会应用改包后的值）。
            event.cancel();
            return;
        }
        if (event.getEntity() != mc.player) {
            return;
        }
        getHandler().onEntityMotion(event);
    }

    @EventListener
    public void onReceivePacket(PacketEvent.Receive event) {
        if (nullCheck()) {
            return;
        }
        // setback 检测（zio ServerStateManager：位置包 -> ack>50ms -> setbackTimer.reset）
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            if (teleportAckTimer.passed(50L)) {
                setbackTimer.reset();
            }
            teleportAckTimer.reset();
            if (isOn() && mode.is(Mode.Walls)) {
                wallsHandler.onPositionCorrection();
            }
            return;
        }
        if (!isOn()) {
            return;
        }
        // 钓鱼钩拉扯取消（zio FishingHookPullEntityEvent 语义，沿用仓库1 status=31 路径）
        if (fishingBobber.getValue()
                && event.getPacket() instanceof EntityStatusS2CPacket packet
                && packet.getStatus() == 31
                && packet.getEntity(mc.world) instanceof FishingBobberEntity fishHook
                && fishHook.getHookedEntity() == mc.player) {
            event.setCancelled(true);
            getHandler().onFishingPull();
            return;
        }
        // 爆炸击退改包（vanilla onExplosion 的 lambda 会 addVelocityInternal 原始击退，
        // 仓库1 velocityHook2 又会加一次 -> 必须在 Receive 阶段直接改包）
        if (event.getPacket() instanceof ExplosionS2CPacket explosion
                && !explosion.playerKnockback().isEmpty()) {
            Vec3d knockback = explosion.playerKnockback().get();
            Vec3d modified = getHandler().modifyExplosionKnockback(knockback);
            if (modified != null) {
                ((IExplosionS2CPacket) (Object) explosion).setPlayerKnockback(Optional.of(modified));
            }
        }
    }

    @EventListener
    public void onSendPacket(PacketEvent.Send event) {
        if (nullCheck() || !isOn()) {
            return;
        }
        // LUNGE 检测（zio: STAB 动作 + 主手长矛附魔 -> 放行下一次动量包）
        if (event.getPacket() instanceof PlayerActionC2SPacket actionPacket
                && actionPacket.getAction() == PlayerActionC2SPacket.Action.STAB
                && hasEnchantment(Enchantments.LUNGE, mc.player.getMainHandStack())) {
            this.lungeMotionPending = true;
        }
        if (event.getPacket() instanceof PlayerMoveC2SPacket move && move.changesLook()) {
            // Grim: 记录最后发送的 yaw/pitch（补偿包要用）
            if (mode.is(Mode.Grim)) {
                grimHandler.onSendPacket(move);
            }
            // Walls: 出站旋转覆盖（zio LocalPlayerSendPositionEvent 语义）
            if (mode.is(Mode.Walls)) {
                wallsHandler.applyRotationOverride(move);
            }
        }
    }

    @EventListener
    public void onUpdate(TickEvent event) {
        if (nullCheck() || !isOn() || event.isPost()) {
            return;
        }
        if (mode.is(Mode.Grim)) {
            grimHandler.onTick();
        } else if (mode.is(Mode.Walls)) {
            wallsHandler.onTick();
        }
    }

    /**
     * sendMovementPackets HEAD hook（由 MixinClientPlayerEntity 调用）。
     * zio 的 LocalPlayerSendPositionEvent(Pre) 语义：在 vanilla 构建移动包之前
     * 操纵 ticksSinceLastPositionPacketSent / lastYawClient / lastPitchClient。
     */
    public static void onSendMovementPacketsHead() {
        Velocity instance = INSTANCE;
        if (instance == null || !instance.isOn() || nullCheck()) {
            return;
        }
        if (instance.mode.is(Mode.Walls)) {
            instance.wallsHandler.onSendPositionHead();
        } else {
            instance.wallsHandler.resetRotationOverride();
        }
    }

    static boolean movementSyncOn() {
        return MovementSync.INSTANCE != null && MovementSync.INSTANCE.isOn();
    }

    static boolean hasElapsedSinceSetback(long ms) {
        return INSTANCE != null && INSTANCE.setbackTimer.passed(ms);
    }

    static boolean hasEnchantment(RegistryKey<Enchantment> enchantment, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(enchantment) && entry.getIntValue() > 0) {
                return true;
            }
        }
        return false;
    }

    /** zio PlayerStateUtils.isInCobweb */
    static boolean isInCobweb(ClientPlayerEntity player) {
        Box bounds = player.getBoundingBox();
        return BlockPos.stream(streamMin(bounds), streamMax(bounds))
                .anyMatch(pos -> mc.world.getBlockState(pos).isOf(Blocks.COBWEB));
    }

    static BlockPos streamMin(Box bounds) {
        return BlockPos.ofFloored(bounds.minX, bounds.minY, bounds.minZ);
    }

    static BlockPos streamMax(Box bounds) {
        return BlockPos.ofFloored(bounds.maxX, bounds.maxY, bounds.maxZ);
    }

    // ==================================================================
    // zio VelocityModeHandler 抽象
    // ==================================================================
    private abstract static class VelocityModeHandler {
        void onEntityMotion(EntityVelocityUpdateEvent event) {
        }

        /** 返回 null = 不改动爆炸击退；返回 Vec3d = 替换击退向量 */
        Vec3d modifyExplosionKnockback(Vec3d knockback) {
            return null;
        }

        void onFishingPull() {
        }

        void onTick() {
        }
    }

    // ==================================================================
    // Plain 模式（zio PlainVelocityHandler）
    // ==================================================================
    private final class PlainVelocityHandler extends VelocityModeHandler {
        @Override
        void onEntityMotion(EntityVelocityUpdateEvent event) {
            int horizontalPercent = horizontal.getValueInt();
            int verticalPercent = vertical.getValueInt();
            int sign = inverse.getValue() ? -1 : 1;
            Vec3d velocity = new Vec3d(event.getX(), event.getY(), event.getZ());
            if (shouldLeaveMotionUnchanged((float) velocity.length())) {
                return;
            }
            if (movementSyncOn() && velocity.x == 0.0 && velocity.z == 0.0 && Math.abs(velocity.y) < 0.0788) {
                return;
            }
            event.setX(event.getX() * horizontalPercent * 0.01 * sign);
            event.setZ(event.getZ() * horizontalPercent * 0.01 * sign);
            event.setY(event.getY() * verticalPercent * 0.01);
            if (horizontalPercent == 0 && verticalPercent == 0) {
                event.cancel();
            }
        }

        @Override
        Vec3d modifyExplosionKnockback(Vec3d knockback) {
            if (!explosions.getValue()) {
                return null;
            }
            int horizontalPercent = horizontal.getValueInt();
            int verticalPercent = vertical.getValueInt();
            int sign = inverse.getValue() ? -1 : 1;
            return knockback.multiply(horizontalPercent * 0.01 * sign, verticalPercent * 0.01, horizontalPercent * 0.01 * sign);
        }

        private boolean shouldLeaveMotionUnchanged(float motionLength) {
            if (isLungeMotionPending() && motionLength > 0.458F) {
                setLungeMotionPending(false);
                return true;
            }
            long reductionWindow = motionLength <= 0.3 ? 200L : 100L;
            return movementSyncOn()
                    && reduce.getValue()
                    && !hasElapsedSinceSetback(reductionWindow);
        }
    }

    // ==================================================================
    // Grim 模式（zio GrimVelocityHandler）
    // ==================================================================
    private final class GrimVelocityHandler extends VelocityModeHandler {
        private boolean intersectsNonEmptyBlock;
        private boolean compensationPending;
        private float lastSentYaw;
        private float lastSentPitch;

        private float getPlayerIntersectionSize(BlockPos blockPos) {
            Box blockBounds = new Box(blockPos);
            return (float) mc.player.getBoundingBox().intersection(blockBounds).getAverageSideLength();
        }

        private void sendBlockCompensation() {
            BlockPos target = findCompensationBlock();
            if (target == null || (!this.compensationPending && reduce.getValue())) {
                return;
            }
            this.compensationPending = false;
            ClientPlayerEntity player = mc.player;
            try (PendingUpdateManager pendingUpdateManager = mc.world.getPendingUpdateManager().incrementSequence()) {
                int sequence = pendingUpdateManager.getSequence();
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                        player.getX(), player.getY(), player.getZ(),
                        this.lastSentYaw, this.lastSentPitch, player.isOnGround(), player.horizontalCollision));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, Direction.DOWN, sequence));
            } catch (Exception ignoredFailure) {
            }
        }

        private BlockPos findCompensationBlock() {
            Box bounds = mc.player.getBoundingBox();
            return BlockPos.stream(streamMin(bounds), streamMax(bounds))
                    .filter(position -> mc.world.getBlockState(position).isAir()
                            || (reduce.getValue() && !mc.world.getFluidState(position).isEmpty()))
                    .max(Comparator.comparingDouble(this::getPlayerIntersectionSize))
                    .orElse(null);
        }

        private boolean shouldLeaveMotionUnchanged() {
            boolean noCompensationBlock = this.findCompensationBlock() == null && !this.intersectsNonEmptyBlock;
            ClientPlayerEntity player = mc.player;
            return player.isGliding() || isInCobweb(player) || noCompensationBlock
                    || !hasElapsedSinceSetback(100L);
        }

        @Override
        void onEntityMotion(EntityVelocityUpdateEvent event) {
            if (!this.shouldLeaveMotionUnchanged()) {
                event.cancel();
                this.compensationPending = true;
            }
        }

        @Override
        void onFishingPull() {
            if (fishingBobber.getValue()) {
                this.compensationPending = true;
            }
        }

        void onSendPacket(PlayerMoveC2SPacket movementPacket) {
            if (movementPacket.changesLook()) {
                this.lastSentYaw = movementPacket.getYaw(0.0F);
                this.lastSentPitch = movementPacket.getPitch(0.0F);
            }
        }

        @Override
        Vec3d modifyExplosionKnockback(Vec3d knockback) {
            boolean hasMotion = knockback.x != 0.0 || knockback.y != 0.0 || knockback.z != 0.0;
            if (hasMotion && explosions.getValue() && !this.shouldLeaveMotionUnchanged()) {
                return Vec3d.ZERO;
            }
            return null;
        }

        @Override
        void onTick() {
            Box bounds = mc.player.getBoundingBox();
            this.intersectsNonEmptyBlock = BlockPos.stream(streamMin(bounds), streamMax(bounds))
                    .anyMatch(position -> !mc.world.getBlockState(position).isAir());
            if (mc.player.age > 20 && !this.shouldLeaveMotionUnchanged()) {
                this.sendBlockCompensation();
            }
        }
    }

    // ==================================================================
    // Walls 模式（zio WallsVelocityHandler）
    // ==================================================================
    private final class WallsVelocityHandler extends VelocityModeHandler {
        private final List<Long> recentPositionCorrections = Collections.synchronizedList(new ArrayList<>());
        private final Timer correctionCooldown = new Timer();
        private boolean groundCollisionPresent;

        // 出站旋转覆盖状态（HEAD hook 计算，PacketEvent.Send 消费）
        private boolean rotationOverrideActive;
        private boolean yawOverrideSet;
        private float yawOverride;
        private boolean pitchOverrideSet;
        private float pitchOverride;
        private boolean jitterPitch;

        private float getAbsolutePositionDeltaSum() {
            double deltaSum = mc.player.getX() - mc.player.lastX;
            deltaSum += mc.player.getY() - mc.player.lastY;
            deltaSum += mc.player.getZ() - mc.player.lastZ;
            return (float) Math.abs(deltaSum);
        }

        static boolean hasRaisedPlayerCollision() {
            return hasPlayerCollisionAtHeight(mc.player.getY() + 0.1);
        }

        /** zio PlayerStateUtils.getSurroundPositions(BlockerTargetOffsets.SURROUND) */
        private static boolean allOccupiedBlocksHaveCollision() {
            BlockPos center = mc.player.getBlockPos();
            BlockPos[] positions = new BlockPos[]{
                    center.add(1, 0, 0), center.add(0, 0, 1),
                    center.add(-1, 0, 0), center.add(0, 0, -1)
            };
            for (BlockPos position : positions) {
                if (mc.world.getBlockState(position).getCollisionShape(mc.world, position).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private boolean canApplyWallVelocity() {
            return this.isStationaryStaticMode() || this.groundCollisionPresent;
        }

        static boolean hasUsableGroundCollision() {
            return !mc.player.isGliding() && hasRaisedPlayerCollision();
        }

        private boolean isStationaryStaticMode() {
            return staticSetting.getValue() && !mc.player.isGliding()
                    && this.getAbsolutePositionDeltaSum() < 0.01;
        }

        private boolean shouldCancelVelocity() {
            if (FastWeb.INSTANCE.isOn() && FastWeb.INSTANCE.grim.isOpen() && isInCobweb(mc.player)) {
                return true;
            }
            return this.correctionCooldown.passed(150L) && this.canApplyWallVelocity();
        }

        static boolean hasPlayerCollisionAtHeight(double height) {
            Box bounds = mc.player.getBoundingBox();
            bounds = bounds.shrink(BLOCK_CORNER_INSET, 0.0, BLOCK_CORNER_INSET);
            bounds = bounds.withMinY(height);
            Box collisionBounds = bounds.withMaxY(height + 0.1);
            return BlockPos.stream(streamMin(collisionBounds), streamMax(collisionBounds))
                    .anyMatch(position -> intersectsCollision(collisionBounds, position));
        }

        private static boolean intersectsCollision(Box bounds, BlockPos position) {
            BlockState state = mc.world.getBlockState(position);
            VoxelShape shape = state.getCollisionShape(mc.world, position);
            return !shape.isEmpty() && bounds.intersects(shape.getBoundingBox().offset(position));
        }

        // ---------- 旋转覆盖（zio applyYawAndPitch / applyRandomizedPitch） ----------

        private void onSendPositionHead() {
            this.resetRotationOverride();
            if (mc.currentScreen instanceof HandledScreen
                    || !this.canApplyWallVelocity()
                    || !reduce.getValue()) {
                return;
            }
            this.rotationOverrideActive = true;
            if (movementSyncOn()) {
                this.applyRandomizedPitch();
            } else {
                this.applyYawAndPitch();
            }
        }

        private void applyYawAndPitch() {
            IClientPlayerEntity access = (IClientPlayerEntity) mc.player;
            float previousYaw = access.getLastYaw();
            boolean moving = isMoving();
            boolean yawWithinThreshold = Math.abs(MathHelper.subtractAngles(mc.player.getYaw(), previousYaw)) <= 2.0F;
            if (Sprint.INSTANCE != null && Sprint.INSTANCE.isOn() && mc.player.isSprinting()
                    && !allOccupiedBlocksHaveCollision() && yawWithinThreshold) {
                moving = false;
            }
            if (!moving) {
                if (this.isStationaryStaticMode()) {
                    access.setTicksSinceLastPositionPacketSent(Math.min(access.getTicksSinceLastPositionPacketSent(), 18));
                }
                this.yawOverrideSet = true;
                this.yawOverride = previousYaw;
            }
            this.pitchOverrideSet = true;
            this.pitchOverride = 89.0F;
        }

        private void applyRandomizedPitch() {
            IClientPlayerEntity access = (IClientPlayerEntity) mc.player;
            access.setTicksSinceLastPositionPacketSent(20);
            access.setLastYaw((float) Math.random());
            access.setLastPitch((float) Math.random());
            float jitter = ROTATION_JITTER_STEP * (mc.player.age % 6 - 3);
            this.jitterPitch = true;
            this.pitchOverride = jitter;
        }

        private void applyRotationOverride(PlayerMoveC2SPacket movementPacket) {
            if (!this.rotationOverrideActive) {
                return;
            }
            IPlayerMoveC2SPacket access = (IPlayerMoveC2SPacket) (Object) movementPacket;
            if (this.yawOverrideSet) {
                access.setYaw(this.yawOverride);
            }
            if (this.jitterPitch) {
                access.setPitch(MathHelper.clamp(movementPacket.getPitch(0.0F) + this.pitchOverride, -90.0F, 90.0F));
            } else if (this.pitchOverrideSet) {
                access.setPitch(this.pitchOverride);
            }
            this.resetRotationOverride();
        }

        private void resetRotationOverride() {
            this.rotationOverrideActive = false;
            this.yawOverrideSet = false;
            this.pitchOverrideSet = false;
            this.jitterPitch = false;
            this.yawOverride = 0.0F;
            this.pitchOverride = 0.0F;
        }

        // ---------- 动量/爆炸/校正 ----------

        @Override
        void onEntityMotion(EntityVelocityUpdateEvent event) {
            if (this.shouldCancelVelocity()) {
                Vec3d incomingVelocity = new Vec3d(event.getX(), event.getY(), event.getZ());
                if (Math.abs(incomingVelocity.y) < 0.0788 && incomingVelocity.x == 0.0 && incomingVelocity.z == 0.0) {
                    return;
                }
                event.cancel();
            }
        }

        @Override
        Vec3d modifyExplosionKnockback(Vec3d knockback) {
            if (this.shouldCancelVelocity()) {
                return Vec3d.ZERO;
            }
            return null;
        }

        void onPositionCorrection() {
            this.recentPositionCorrections.add(System.currentTimeMillis());
        }

        @Override
        void onTick() {
            this.groundCollisionPresent = hasUsableGroundCollision();
            this.recentPositionCorrections.removeIf(receivedAt -> System.currentTimeMillis() > receivedAt + 500L);
            if (this.recentPositionCorrections.size() > 2) {
                this.correctionCooldown.reset();
            }
        }
    }

    private static boolean isMoving() {
        Vec2f movement = mc.player.input.getMovementInput();
        return movement.x != 0.0F || movement.y != 0.0F;
    }

    public enum Mode {
        Plain, Grim, Walls
    }
}
