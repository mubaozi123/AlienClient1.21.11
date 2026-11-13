package dev.Riyue.mod.modules.impl.combat;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.ClientTickEvent;
import dev.Riyue.api.utils.combat.CombatUtil;
import dev.Riyue.api.utils.math.ExplosionUtil;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.player.InventoryUtil;
import dev.Riyue.api.utils.render.Render3DUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.enums.SwingSide;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.EnumSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;

import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.List;

/**
 * Crystalbot —— 以 zio 的 AutoCrystal 算法思想重写的水晶模块：
 *   - 以玩家视点(eye)为中心的球扫描选位（替代以脚为中心的 getSphere）
 *   - 基于 lethality 的伤害策略（致死优先、自伤/保留血限制、最小伤害）
 *   - place / break 分离的延迟时序
 *   - base-place（在目标脚下无底座处先垫 obsidian 再放水晶）
 * 底层全部复用 Riyue 自有的工具（伤害/方块/实体/旋转/事件/设置/网络），不依赖 zio 任何类。
 */
public class Crystalbot extends Module {
    public static Crystalbot INSTANCE;

    private enum Page {
        General, Place, Break, Rotate, Render
    }

    private enum SwapMode {
        None, Normal, Silent, Inventory
    }

    private final EnumSetting<Page> page = this.add(new EnumSetting<Page>("Page", Page.General));

    // ── 通用 / 伤害策略 ──
    private final BooleanSetting thread = this.add(new BooleanSetting("Thread", false, () -> this.page.is(Page.General)));
    private final SliderSetting targetRange = this.add(new SliderSetting("TargetRange", 10.0, 0.0, 20.0, () -> this.page.is(Page.General)).setSuffix("m"));
    private final SliderSetting minDamage = this.add(new SliderSetting("MinDamage", 5.0, 0.0, 36.0, () -> this.page.is(Page.General)).setSuffix("dmg"));
    private final SliderSetting maxSelf = this.add(new SliderSetting("MaxSelf", 12.0, 0.0, 36.0, () -> this.page.is(Page.General)).setSuffix("dmg"));
    private final SliderSetting reserve = this.add(new SliderSetting("Reserve", 2.0, 0.0, 10.0, () -> this.page.is(Page.General)).setSuffix("hp"));

    // ── 放置 ──
    private final BooleanSetting place = this.add(new BooleanSetting("Place", true, () -> this.page.is(Page.Place)).setParent());
    private final SliderSetting placeRange = this.add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0, 0.01, () -> this.page.is(Page.Place) && this.place.isOpen()).setSuffix("m"));
    private final SliderSetting placeDelay = this.add(new SliderSetting("PlaceDelay", 50, 0, 1000, () -> this.page.is(Page.Place) && this.place.isOpen()).setSuffix("ms"));
    private final SliderSetting wallRange = this.add(new SliderSetting("WallRange", 6.0, 0.0, 6.0, 0.01, () -> this.page.is(Page.Place) && this.place.isOpen()).setSuffix("m"));
    private final BooleanSetting basePlace = this.add(new BooleanSetting("BasePlace", true, () -> this.page.is(Page.Place) && this.place.isOpen()));
    private final SliderSetting baseMin = this.add(new SliderSetting("BaseMin", 6.0, 0.0, 36.0, 0.1, () -> this.page.is(Page.Place) && this.place.isOpen()).setSuffix("hp"));
    private final SliderSetting baseMax = this.add(new SliderSetting("BaseMax", 12.0, 0.0, 36.0, 0.1, () -> this.page.is(Page.Place) && this.place.isOpen()).setSuffix("hp"));
    private final EnumSetting<SwapMode> autoSwap = this.add(new EnumSetting<SwapMode>("AutoSwap", SwapMode.None, () -> this.page.is(Page.Place) && this.place.isOpen()));

    // ── 破坏 ──
    private final BooleanSetting breakSetting = this.add(new BooleanSetting("Break", true, () -> this.page.is(Page.Break)).setParent());
    private final SliderSetting breakRange = this.add(new SliderSetting("BreakRange", 4.0, 0.0, 6.0, 0.01, () -> this.page.is(Page.Break) && this.breakSetting.isOpen()).setSuffix("m"));
    private final SliderSetting breakDelay = this.add(new SliderSetting("BreakDelay", 50, 0, 1000, () -> this.page.is(Page.Break) && this.breakSetting.isOpen()).setSuffix("ms"));
    private final SliderSetting minAge = this.add(new SliderSetting("MinAge", 0, 0, 20, () -> this.page.is(Page.Break) && this.breakSetting.isOpen()).setSuffix("tick"));

    // ── 旋转 ──
    private final BooleanSetting rotate = this.add(new BooleanSetting("Rotate", true, () -> this.page.is(Page.Rotate)).setParent());
    private final BooleanSetting onPlace = this.add(new BooleanSetting("OnPlace", false, () -> this.rotate.isOpen() && this.page.is(Page.Rotate)));
    private final BooleanSetting onBreak = this.add(new BooleanSetting("OnBreak", false, () -> this.rotate.isOpen() && this.page.is(Page.Rotate)));

    // ── 渲染 ──
    private final BooleanSetting render = this.add(new BooleanSetting("Render", true, () -> this.page.is(Page.Render)));
    private final ColorSetting boxColor = this.add(new ColorSetting("Box", new Color(255, 255, 255, 255), () -> this.page.is(Page.Render) && this.render.getValue()).injectBoolean(true));
    private final SliderSetting lineWidth = this.add(new SliderSetting("LineWidth", 1.5, 0.01, 3.0, 0.01, () -> this.page.is(Page.Render) && this.render.getValue()));

    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private final Timer baseTimer = new Timer();

    // 后台线程(onThread)写入、主线程(onTick)读取的共享结果，必须 volatile 防止指令重排/缓存可见性问题
    public volatile BlockPos placePos;
    public volatile BlockPos baseBlockPos;
    public volatile EndCrystalEntity breakCrystal;
    private volatile Vec3d eyePosition;
    private volatile BlockPos syncPos;
    private int lastSlot;
    public volatile PlayerEntity target;

    public Crystalbot() {
        super("Crystalbot", Module.Category.Combat);
        this.setChinese("水晶机器人");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.placePos = null;
        this.baseBlockPos = null;
        this.breakCrystal = null;
        this.target = null;
        this.eyePosition = null;
    }

    @Override
    public void onDisable() {
        this.placePos = null;
        this.baseBlockPos = null;
        this.breakCrystal = null;
        this.target = null;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (event.isPost()) {
            return;
        }
        if (!this.thread.getValue()) {
            // 单线程模式：主线程既计算又发包（保持旧行为，便于排查/兼容）
            this.runCalc();
        }
        // 主线程只负责执行（发包）：单线程模式 runCalc 已算好，多线程模式由 onThread 后台算好
        PlayerEntity target = this.target;
        if (target == null) {
            this.placePos = null;
            this.baseBlockPos = null;
            this.breakCrystal = null;
            return;
        }
        if (this.place.getValue() && this.placePos != null) {
            this.handlePlacement(this.placePos, target);
        }
        if (this.breakSetting.getValue() && this.breakCrystal != null && this.breakCrystal.isAlive()) {
            this.attackCrystal(this.breakCrystal);
        }
    }

    /**
     * 后台线程入口（由 ThreadManager.ClientService 并行调度）。
     * 仅当 Thread 开启时在后台执行重量级计算（选位 / 伤害扫描），不发包。
     */
    public void onThread() {
        if (this.isOff()) {
            return;
        }
        if (this.thread.getValue()) {
            this.runCalc();
        }
    }

    /**
     * 纯计算（不发包）：更新视点、选目标、计算最佳放置点与最佳爆裂水晶。
     * 单线程模式由主线程 onTick 调用，多线程模式由后台 onThread 调用。
     */
    private void runCalc() {
        this.updateEyePosition();
        PlayerEntity target = this.getTarget();
        this.target = target;
        if (target == null) {
            this.placePos = null;
            this.baseBlockPos = null;
            this.breakCrystal = null;
            return;
        }
        if (this.place.getValue()) {
            BlockPos best = this.calculatePlacement(target);
            this.placePos = best;
            this.baseBlockPos = best;
        } else {
            this.placePos = null;
        }
        if (this.breakSetting.getValue()) {
            this.runBreakCycle(target);
        } else {
            this.breakCrystal = null;
        }
    }

    private void updateEyePosition() {
        if (mc.world != null && mc.player != null) {
            this.eyePosition = mc.player.getEyePos();
        }
    }

    private PlayerEntity getTarget() {
        PlayerEntity best = null;
        double bestDist = this.targetRange.getValue();
        // 后台线程安全：读取 ThreadManager 拷贝的玩家快照，避免与客户端主线程的世界 tick 迭代冲突
        for (AbstractClientPlayerEntity p : this.safePlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) {
                continue;
            }
            double d = mc.player.distanceTo(p);
            if (d > this.targetRange.getValue()) {
                continue;
            }
            if (best == null || d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /**
     * 取线程安全的玩家快照；未就绪时回退到实时世界（仅主线程单线程模式会走到这里）。
     */
    private java.util.List<AbstractClientPlayerEntity> safePlayers() {
        if (RiyueClient.THREAD != null && RiyueClient.THREAD.threadSafePlayersList != null) {
            return RiyueClient.THREAD.threadSafePlayersList;
        }
        return mc.world.getPlayers();
    }

    /**
     * 取线程安全的实体快照；未就绪时回退到实时世界（仅主线程单线程模式会走到这里）。
     */
    private Iterable<Entity> safeEntities() {
        if (RiyueClient.THREAD != null && RiyueClient.THREAD.threadSafeEntityList != null) {
            return RiyueClient.THREAD.threadSafeEntityList;
        }
        return mc.world.getEntities();
    }

    /**
     * zio 风格选位：以 eye 为中心的立方体扫描，对每个底座（obsidian/bedrock 或 base-place 可放方块处）
     * 计算对目标的伤害与自伤，按 lethality 策略筛选并选最优。
     */
    private BlockPos calculatePlacement(PlayerEntity target) {
        if (this.eyePosition == null) {
            return null;
        }
        double range = this.placeRange.getValue();
        int r = (int) Math.ceil(range);
        BlockPos best = null;
        float bestScore = -1.0f;
        double selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = new BlockPos((int) Math.floor(this.eyePosition.x) + x, (int) Math.floor(this.eyePosition.y) + y, (int) Math.floor(this.eyePosition.z) + z);
                    boolean hasBase = this.isBedrockOrObsidian(pos);
                    if (!hasBase) {
                        if (!this.basePlace.getValue()) {
                            continue;
                        }
                        if (!BlockUtil.canPlace(pos)) {
                            continue;
                        }
                    }
                    double dist = this.eyePosition.distanceTo(pos.toCenterPos());
                    if (dist > range) {
                        continue;
                    }
                    // 水晶位置 = 底座上方
                    BlockPos boost = pos.up();
                    if (!mc.world.isAir(boost) && !BlockUtil.hasCrystal(boost)) {
                        if (!this.basePlace.getValue()) {
                            continue;
                        }
                    }
                    Vec3d explosionPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    float selfDamage = this.calcDamage(explosionPos, mc.player, mc.player);
                    // 自伤限制：超过 MaxSelf 且会跌破保留血则拒绝
                    if (selfDamage > this.maxSelf.getValue() && selfDamage >= selfHealth - this.reserve.getValue()) {
                        continue;
                    }
                    float targetDamage = this.calcDamage(explosionPos, target, target);
                    float lethal = EntityUtil.getHealth(target);
                    float score;
                    if (targetDamage >= lethal) {
                        // 致死：加权优先
                        score = targetDamage + 100.0f;
                    } else {
                        if (targetDamage < this.minDamage.getValue()) {
                            continue;
                        }
                        score = targetDamage;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private void handlePlacement(BlockPos base, PlayerEntity target) {
        boolean hasBase = this.isBedrockOrObsidian(base);
        // base-place：先垫 obsidian
        if (!hasBase && this.basePlace.getValue() && BlockUtil.canPlace(base)) {
            if (this.baseTimer.passed(50L) && !this.hasCrystalAt(base.up())) {
                int block = this.getBlock();
                if (block != -1) {
                    int old = mc.player.getInventory().selectedSlot;
                    this.doSwap(block);
                    BlockUtil.placeBlock(base, this.rotate.getValue());
                    if (this.autoSwap.getValue() == SwapMode.Inventory) {
                        this.doSwap(block);
                        EntityUtil.syncInventory();
                    } else {
                        this.doSwap(old);
                    }
                    this.baseTimer.reset();
                }
            }
        }
        BlockPos crystalPos = base.up();
        this.doPlaceCrystal(crystalPos);
    }

    private void runBreakCycle(PlayerEntity target) {
        if (!this.breakTimer.passed((long) this.breakDelay.getValue())) {
            return;
        }
        double range = this.breakRange.getValue();
        double selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        EndCrystalEntity best = null;
        float bestScore = -1.0f;
        // 后台线程安全：读取 ThreadManager 拷贝的实体快照
        for (Entity e : this.safeEntities()) {
            if (!(e instanceof EndCrystalEntity crystal) || !crystal.isAlive()) {
                continue;
            }
            if (crystal.age < this.minAge.getValueInt()) {
                continue;
            }
            double dist = mc.player.getEyePos().distanceTo(crystal.getEntityPos());
            if (dist > range) {
                continue;
            }
            Vec3d explosionPos = crystal.getEntityPos();
            float selfDamage = this.calcDamage(explosionPos, mc.player, mc.player);
            if (selfDamage > this.maxSelf.getValue() && selfDamage >= selfHealth - this.reserve.getValue()) {
                continue;
            }
            float targetDamage = this.calcDamage(explosionPos, target, target);
            float lethal = EntityUtil.getHealth(target);
            float score;
            if (targetDamage >= lethal) {
                score = targetDamage + 100.0f;
            } else {
                if (targetDamage < this.minDamage.getValue()) {
                    continue;
                }
                score = targetDamage;
            }
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        // 只计算最优爆裂目标，实际发包交给主线程 onTick（attackCrystal），保证网络/世界操作在主线程执行
        this.breakCrystal = best;
    }

    private void doPlaceCrystal(BlockPos crystalPos) {
        if (!this.place.getValue()) {
            return;
        }
        if (!this.placeTimer.passed((long) this.placeDelay.getValue())) {
            return;
        }
        BlockPos obsPos = crystalPos.down();
        if (!this.isBedrockOrObsidian(obsPos)) {
            return;
        }
        if (!mc.world.isAir(crystalPos) && !BlockUtil.hasCrystal(crystalPos)) {
            return;
        }
        Direction facing = BlockUtil.getClickSide(obsPos);
        if (facing == null) {
            return;
        }
        boolean mainhand = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        if (!mainhand && !offhand) {
            int crystal = this.getCrystal();
            if (crystal == -1) {
                return;
            }
            int old = mc.player.getInventory().selectedSlot;
            this.doSwap(crystal);
            this.placeCrystal(crystalPos, facing);
            if (this.autoSwap.getValue() == SwapMode.Silent) {
                this.doSwap(old);
            } else if (this.autoSwap.getValue() == SwapMode.Inventory) {
                this.doSwap(crystal);
                EntityUtil.syncInventory();
            }
        } else {
            this.placeCrystal(crystalPos, facing);
        }
        this.placeTimer.reset();
        this.syncPos = crystalPos;
    }

    private void placeCrystal(BlockPos crystalPos, Direction facing) {
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        BlockPos obsPos = crystalPos.down();
        Vec3d vec = obsPos.toCenterPos().add(
                facing.getVector().getX() * 0.5,
                facing.getVector().getY() * 0.5,
                facing.getVector().getZ() * 0.5);
        if (this.rotate.getValue() && this.onPlace.getValue()) {
            RiyueClient.ROTATION.lookAt(vec);
        }
        BlockUtil.clickBlock(obsPos, facing, this.rotate.getValue(), hand, SwingSide.All);
        if (this.rotate.getValue() && !this.onPlace.getValue()) {
            RiyueClient.ROTATION.snapBack();
        } else if (this.rotate.getValue() && this.onPlace.getValue()) {
            RiyueClient.ROTATION.snapBack();
        }
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (!this.breakTimer.passed((long) this.breakDelay.getValue())) {
            return;
        }
        this.breakTimer.reset();
        if (this.rotate.getValue() && this.onBreak.getValue()) {
            RiyueClient.ROTATION.lookAt(crystal.getEntityPos());
        }
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);
        EntityUtil.resetLastAttackedTicks();
        this.syncPos = crystal.getBlockPos();
        if (this.rotate.getValue()) {
            RiyueClient.ROTATION.snapBack();
        }
    }

    private void doSwap(int slot) {
        if (this.autoSwap.getValue() == SwapMode.Silent || this.autoSwap.getValue() == SwapMode.Normal) {
            InventoryUtil.switchToSlot(slot);
        } else if (this.autoSwap.getValue() == SwapMode.Inventory) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        }
    }

    private int getBlock() {
        if (this.autoSwap.getValue() == SwapMode.Inventory) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        }
        return InventoryUtil.findBlock(Blocks.OBSIDIAN);
    }

    private int getCrystal() {
        if (this.autoSwap.getValue() == SwapMode.Silent || this.autoSwap.getValue() == SwapMode.Normal) {
            return InventoryUtil.findItem(Items.END_CRYSTAL);
        }
        if (this.autoSwap.getValue() == SwapMode.Inventory) {
            return InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        return -1;
    }

    private boolean hasCrystalAt(BlockPos pos) {
        return BlockUtil.hasCrystal(pos);
    }

    private boolean isBedrockOrObsidian(BlockPos pos) {
        net.minecraft.block.Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN;
    }

    private float calcDamage(Vec3d pos, PlayerEntity player, PlayerEntity predict) {
        CombatUtil.invalidateChunkCache();
        CombatUtil.terrainIgnore = true;
        float damage = ExplosionUtil.calculateDamage(pos, player, predict, 6.0f);
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (!this.render.getValue()) {
            return;
        }
        Color c = this.boxColor.getValue();
        if (c.getAlpha() <= 0) {
            return;
        }
        if (this.placePos != null) {
            Box bb = new Box(this.placePos);
            Render3DUtil.drawBox(matrixStack, bb, c, this.lineWidth.getValueFloat());
        }
        if (this.breakCrystal != null && this.breakCrystal.isAlive()) {
            Box bb = this.breakCrystal.getBoundingBox();
            Render3DUtil.drawBox(matrixStack, bb, c, this.lineWidth.getValueFloat());
        }
    }
}
